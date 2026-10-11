package net.ciyuan.lynxtools

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.BlockCollisions
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.math.floor
import kotlin.math.ceil

class Teleports(private val app: Runtime) {
    private data class Request(val player: ServerPlayer, val command: String, val name: String, val start: Place,
        val begin: Long, val token: UUID = UUID.randomUUID(), var destination: Death? = null,
        var candidates: Iterator<Place>? = null, var candidate: Place? = null, var checked: Int = 0, var search: Boolean = false,
        var level: ServerLevel? = null, val chunks: MutableMap<ChunkPos,CompletableFuture<*>> = mutableMapOf(),
        var anchor: ServerPlayer? = null, var anchorStart: Place? = null)
    private val pending=linkedMapOf<UUID,Request>()
    private val cooldown=mutableMapOf<UUID,Long>()
    private val consuming=mutableSetOf<UUID>()
    private fun begin(player: ServerPlayer, command: String, name: String):Request? {
        cancel(player.uuid,"cancel_repeat")
        if(command=="back" && player.uuid in app.failedDeathWrites) { app.messages.send(player,"storage","error"); return null }
        if(consuming.contains(player.uuid)) { app.messages.send(player,"busy","warning"); return null }
        if(!state(player)) { app.messages.send(player,"state","error"); return null }
        val remaining=(cooldown[player.uuid] ?: 0)-app.tick
        if(remaining>0) { app.messages.send(player,"cooldown","warning","seconds" to ceil(remaining/20.0).toInt().toString()); return null }
        if(pending.size>=app.config.int("teleport.max_pending")) { app.messages.send(player,"busy","warning"); return null }
        val r=Request(player,command,name,app.place(player),app.tick)
        pending[player.uuid]=r
        if(app.config.int("teleport.wait_seconds")>0) app.messages.send(player,"waiting","info","seconds" to app.config.int("teleport.wait_seconds").toString())
        return r
    }
    fun request(player: ServerPlayer, command: String, name: String) {
        val r=begin(player,command,name) ?: return
        val kind=if(command=="back") "death" else command
        val owner=if(command=="warp") name else player.uuid.toString()
        app.complete(app.store.get(kind,owner),"read $kind uuid=${player.uuid}") { death,error ->
            if(pending[player.uuid] !== r) return@complete
            if(command=="back" && player.uuid in app.failedDeathWrites) { cancel(player.uuid,"storage"); return@complete }
            if(error!=null) { cancel(player.uuid,"storage"); return@complete }
            if(death==null) { cancel(player.uuid,"missing_$command", "name" to name); return@complete }
            r.destination=death
        }
    }
    fun requestTo(player:ServerPlayer,target:ServerPlayer):Boolean {
        val r=begin(player,"tpr",target.gameProfile.name()) ?: return false
        val destination=app.place(target)
        r.destination=Death(destination,r.token.toString())
        r.anchor=target; r.anchorStart=destination
        return true
    }
    fun cancel(uuid: UUID, message: String? = null, vararg args: Pair<String,String>) {
        val r=pending.remove(uuid) ?: return
        // SPAWN_SEARCH tickets expire naturally like vanilla spawn search; never remove another caller's ticket.
        if(message!=null && app.online(r.player)) app.messages.send(r.player,message,"warning",*args)
        r.anchor?.takeIf { app.online(it) }?.let { app.messages.send(it,"tpr_transfer_cancelled","warning","name" to r.player.gameProfile.name()) }
    }
    fun clear(uuid: UUID) { cancel(uuid); cooldown.remove(uuid); consuming.remove(uuid) }
    fun cancelAll(message: String? = null) { pending.keys.toList().forEach { cancel(it,message) } }
    fun tick() {
        var budget=app.config.int("teleport.checks_per_tick")
        var loadBudget=app.config.int("teleport.chunk_loads_per_tick")
        // One pass visits every request; rotate order so bounded searches cannot starve newer requests.
        val requests=pending.values.toList()
        if(requests.isNotEmpty()) { val r=requests.first(); pending.remove(r.player.uuid); pending[r.player.uuid]=r }
        for(r in requests) {
            val p=r.player
            if(!app.online(p)) { cancel(p.uuid); continue }
            if(!state(p) || app.place(p).dimension!=r.start.dimension) { cancel(p.uuid,"cancel_state"); continue }
            if(!app.config.module(r.command) || !app.permissions.command(p,r.command) || (r.command=="warp" && !app.permissions.warp(p,r.name))) { cancel(p.uuid,"denied"); continue }
            val tolerance=app.config.double("teleport.move_tolerance")
            if(r.anchor!=null) {
                val target=r.anchor!!; val start=r.anchorStart!!
                if(!app.online(target) || !state(target) || target.level().dimension().identifier().toString()!=start.dimension ||
                    target.position().distanceToSqr(Vec3(start.x,start.y,start.z))>tolerance*tolerance) { cancel(p.uuid,"cancel_state"); continue }
            }
            if(p.position().distanceToSqr(Vec3(r.start.x,r.start.y,r.start.z))>tolerance*tolerance) { cancel(p.uuid,"cancel_move"); continue }
            val wait=app.config.int("teleport.wait_seconds")*20L
            if(app.tick-r.begin > wait + app.config.int("teleport.chunk_timeout_seconds")*20L) { cancel(p.uuid,"timeout"); continue }
            if(app.tick-r.begin<wait) continue
            val death=r.destination ?: continue
            if(r.level==null) {
                if(!death.place.valid()) { cancel(p.uuid,"invalid_position"); continue }
                val level=app.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(death.place.dimension)))
                if(level==null || r.start.dimension in app.config.strings("teleport.blocked_source_dimensions") || death.place.dimension in app.config.strings("teleport.blocked_target_dimensions")) { cancel(p.uuid,"dimension"); continue }
                if(!bounds(level,death.place)) { cancel(p.uuid,"invalid_position"); continue }
                r.level=level
                r.search=r.command!="warp" && (app.config.string("teleport.strategy")=="safe" || r.command=="back" && app.config.bool("back.always_safe"))
                r.candidates=candidates(death.place,if(r.search) app.config.int("teleport.search_radius") else 0,r.search).iterator()
            }
            val level=r.level!!
            r.chunks.keys.forEach { level.chunkSource.addTicketWithRadius(TicketType.SPAWN_SEARCH,it,1) }
            while(budget>0 && pending[p.uuid]===r) {
                val candidate=r.candidate ?: run {
                    if(r.checked>=app.config.int("teleport.max_candidates") || !r.candidates!!.hasNext()) { cancel(p.uuid,"unsafe"); break }
                    r.candidates!!.next().also { r.candidate=it; r.checked++ }
                }
                budget--
                if(!bounds(level,candidate)) { r.candidate=null; continue }
                val block=BlockPos.containing(candidate.x,candidate.y,candidate.z)
                val pos=ChunkPos(block.x shr 4,block.z shr 4)
                if(pos !in r.chunks) {
                    if(loadBudget<=0) break
                    if(r.chunks.size>=app.config.int("teleport.max_chunk_loads")) { cancel(p.uuid,"unsafe"); break }
                    loadBudget--
                    r.chunks[pos]=level.chunkSource.addTicketAndLoadWithRadius(TicketType.SPAWN_SEARCH,pos,1)
                }
                val future=r.chunks.getValue(pos)
                if(!future.isDone) break
                if(future.isCompletedExceptionally) { cancel(p.uuid,"timeout"); break }
                // A completed future does not guarantee success; never synchronously load a missing neighbour.
                if((-1..1).any { dx -> (-1..1).any { dz -> level.chunkSource.getChunkNow(pos.x+dx,pos.z+dz)==null } }) { cancel(p.uuid,"timeout"); break }
                // Warp is an administrator-defined exact destination. Never alter its stored coordinates.
                if(r.command=="warp") { finish(r,candidate); break }
                // First check the saved position unchanged; subsequent lattice anchors resolve real collision tops.
                val landing=if(r.search && r.checked>1) surface(level,p,candidate) else candidate
                if(bounds(level,landing) && safe(level,p,landing,r.command=="back" && app.config.bool("back.always_safe") || app.config.bool("teleport.reject_danger"))) {
                    finish(r,landing); break
                }
                r.candidate=null
            }
        }
    }
    private fun finish(r: Request, place: Place) {
        val p=r.player
        pending.remove(p.uuid)
        if(app.config.bool("teleport.dismount")) p.stopRiding()
        val ok=p.teleportTo(r.level!!,place.x,place.y,place.z,emptySet(),place.yaw,place.pitch,true)
        if(!ok || p.level()!==r.level || p.position().distanceToSqr(Vec3(place.x,place.y,place.z))>0.01) { app.messages.send(p,"internal","error"); return }
        p.fallDistance=0.0
        cooldown[p.uuid]=app.tick+app.config.int("teleport.cooldown_seconds")*20L
        app.messages.send(p,"teleported","success","name" to (if(r.command=="warp" || r.command=="tpr") r.name else app.config.language.text("${r.command}_label")))
        r.anchor?.takeIf { app.online(it) }?.let { app.messages.send(it,"tpr_arrived","success","name" to p.gameProfile.name()) }
        if(r.command=="back" && app.config.bool("back.consume")) {
            consuming.add(p.uuid)
            app.complete(app.store.consumeDeath(p.uuid.toString(),r.destination!!.revision),"consume death uuid=${p.uuid}") { _,error ->
                consuming.remove(p.uuid)
                if(error!=null && app.online(p)) app.messages.send(p,"back_consume_failed","error")
            }
        }
    }
    fun state(p: ServerPlayer) = p.isAlive && !p.isRemoved && !p.isSleeping && !p.isChangingDimension && p.connection.hasClientLoaded() && !p.isVehicle && (!p.isPassenger || app.config.bool("teleport.dismount"))
    fun bounds(level: ServerLevel, place: Place) = place.valid() && place.y>=level.minY && place.y+1.8<=level.maxY+1.0 && level.worldBorder.isWithinBounds(AABB(place.x-0.3,place.y,place.z-0.3,place.x+0.3,place.y+1.8,place.z+0.3)) && kotlin.math.abs(place.x)<29999984 && kotlin.math.abs(place.z)<29999984
    private fun safe(level: ServerLevel, p: ServerPlayer, v: Place, danger: Boolean): Boolean {
        val box=AABB(v.x-0.3,v.y,v.z-0.3,v.x+0.3,v.y+1.8,v.z+0.3)
        if(!level.worldBorder.isWithinBounds(box) || collisions(level,p,box,v.y).hasNext() || !level.noEntityCollision(p,box)) return false
        val hazards=app.config.strings("teleport.dangerous_blocks")
        fun hazardous(pos:BlockPos):Boolean {
            val state=level.getBlockState(pos)
            return danger && (!state.fluidState.isEmpty || BuiltInRegistries.BLOCK.getKey(state.block).toString() in hazards)
        }
        // A tiny downward sweep must hit an actual block shape, including narrow/extended shapes.
        var supported=false
        for((pos,_) in collisions(level,p,box.move(0.0,-1.0e-5,0.0),v.y)) {
            supported=true
            if(hazardous(pos)) return false
        }
        if(!supported) return false
        for(pos in BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY-0.01,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))) {
            if(hazardous(pos)) return false
        }
        return true
    }
    private fun collisions(level:ServerLevel,p:ServerPlayer,box:AABB,feetY:Double):BlockCollisions<Pair<BlockPos,VoxelShape>> =
        BlockCollisions(level,CollisionContext.withPosition(p,feetY),box,false) { pos,shape -> pos.immutable() to shape }

    /** One bounded vertical sweep per lattice anchor, no integer-height or full-face assumptions. */
    private fun surface(level:ServerLevel,p:ServerPlayer,anchor:Place):Place {
        val top=anchor.y+0.5+1.0e-5
        val foot=AABB(anchor.x-0.3,top,anchor.z-0.3,anchor.x+0.3,top+1.0e-5,anchor.z+0.3)
        val distance=-1.0-2.0e-5
        val shapes=Iterable { collisions(level,p,foot.expandTowards(0.0,distance,0.0),top).asSequence().map { it.second }.iterator() }
        return anchor.copy(y=top+Shapes.collide(Direction.Axis.Y,foot,shapes,distance))
    }
    companion object {
        /** Best-first lattice search, bounded by caller; nearest Euclidean offset first, no cube allocation. */
        internal fun candidates(place: Place, radius: Int, search: Boolean): Sequence<Place> = sequence {
            yield(place)
            if(!search) return@sequence
            data class Offset(val x:Int,val y:Int,val z:Int)
            val baseX=floor(place.x)+0.5
            val baseY=kotlin.math.round(place.y)
            val baseZ=floor(place.z)+0.5
            fun distance(o: Offset): Double {
                val dx=baseX+o.x-place.x; val dy=baseY+o.y-place.y; val dz=baseZ+o.z-place.z
                return dx*dx+dy*dy+dz*dz
            }
            val queue=java.util.PriorityQueue(compareBy<Offset> { distance(it) }.thenBy { it.y }.thenBy { it.x }.thenBy { it.z })
            val seen=hashSetOf<Offset>()
            val zero=Offset(0,0,0); queue.add(zero); seen.add(zero)
            while(queue.isNotEmpty()) {
                val o=queue.remove()
                yield(place.copy(x=baseX+o.x,y=baseY+o.y,z=baseZ+o.z))
                for(d in listOf(Offset(1,0,0),Offset(-1,0,0),Offset(0,1,0),Offset(0,-1,0),Offset(0,0,1),Offset(0,0,-1))) {
                    val n=Offset(o.x+d.x,o.y+d.y,o.z+d.z)
                    if(kotlin.math.abs(n.x)<=radius && kotlin.math.abs(n.y)<=radius && kotlin.math.abs(n.z)<=radius && seen.add(n)) queue.add(n)
                }
            }
        }
    }
}
