package net.ciyuan.lynxtools.verification

import com.mojang.authlib.GameProfile
import io.netty.channel.ChannelFutureListener
import io.netty.channel.embedded.EmbeddedChannel
import net.ciyuan.lynxtools.*
import net.ciyuan.lynxtools.Runtime as LynxRuntime
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.Node
import net.minecraft.core.BlockPos
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.PacketFlow
import net.minecraft.network.protocol.game.*
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ClientInformation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.PlayerList
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.GameType
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.SlabType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.NeoForgeMod
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.event.server.ServerStartedEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor

/** Runs only in the isolated probe mod. Native world/commands/events; no real network client. */
class LifecycleProbe {
    private val log=LoggerFactory.getLogger("LynxToolsLifecycle")
    private val app get()=ProbeState.app
    private data class Step(val name:String,val test:()->Boolean,val limit:Int=500,var ticks:Int=0)
    private val steps=ArrayDeque<Step>()
    private lateinit var p: ServerPlayer
    private lateinit var actor: ServerPlayer
    private lateinit var net: Sink
    private lateinit var baseline: Settings
    private val configPath=Path.of("plugins/LynxTools/Config.toml")
    private var configBytes: ByteArray?=null
    private var running=false
    private var ending=false
    private var completed=0
    private var future: CompletableFuture<*>?=null
    private val marker=Identifier.fromNamespaceAndPath("lynxtools","flight")
    private var death: Death?=null
    private var disconnected: ServerPlayer?=null
    private var third:ServerPlayer?=null
    private var oldAccept=""
    private var deadline=0L
    private var cancelDeath=false
    private val databaseGate=CountDownLatch(1)
    private lateinit var databaseExecutor:ThreadPoolExecutor
    private val uuid=UUID.fromString("53969817-1e97-427a-b591-65544f04c61b")
    private val restart=System.getProperty("lynxtools.verification.restart")=="true"
    private val messages get()=net.packets.filterIsInstance<ClientboundSystemChatPacket>().map { it.content().string }
    private class FrozenPlayer(level:ServerLevel,profile:GameProfile):ServerPlayer(level.server,level,profile,ClientInformation.createDefault()) {
        override fun doTick() {} // Physics input is controlled by the probe; native commands/death/teleport are not overridden.
    }
    internal class Sink(p:ServerPlayer):ServerGamePacketListenerImpl(p.level().server,testConnection(),p,CommonListenerCookie.createInitial(p.gameProfile,false)) {
        val packets=mutableListOf<Packet<*>>()
        override fun send(packet:Packet<*>, listener:ChannelFutureListener?) { packets.add(packet) }
        override fun tick() {}
        fun acknowledge() {
            packets.filterIsInstance<ClientboundPlayerPositionPacket>().lastOrNull()?.let { handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket(it.id())) }
            packets.removeAll { it is ClientboundPlayerPositionPacket }
            if(player.isAlive) markClientLoaded()
        }
    }
    companion object {
        private fun testConnection()=Connection(PacketFlow.SERVERBOUND).also { c ->
            Connection::class.java.getDeclaredField("channel").apply { isAccessible=true }.set(c,EmbeddedChannel())
            Connection::class.java.getDeclaredField("address").apply { isAccessible=true }.set(c,java.net.InetSocketAddress("127.0.0.1",0))
        }
    }
    init {
        NeoForge.EVENT_BUS.addListener<LivingDeathEvent>(EventPriority.LOWEST) { e ->
            if(cancelDeath && e.entity.uuid==uuid) { e.isCanceled=true; e.entity.health=20f }
        }
        NeoForge.EVENT_BUS.addListener<ServerStartedEvent> { setup() }
        NeoForge.EVENT_BUS.addListener<ServerTickEvent.Post> { tick() }
    }
    @Suppress("UNCHECKED_CAST") private fun players()=PlayerList::class.java.getDeclaredField("players").apply { isAccessible=true }.get(app.server.playerList) as MutableList<ServerPlayer>
    @Suppress("UNCHECKED_CAST") private fun byId()=PlayerList::class.java.getDeclaredField("playersByUUID").apply { isAccessible=true }.get(app.server.playerList) as MutableMap<UUID,ServerPlayer>
    private fun register(id:UUID,name:String):ServerPlayer {
        val player=FrozenPlayer(app.server.overworld(),GameProfile(id,name))
        Sink(player).markClientLoaded()
        player.setNoGravity(true); player.snapTo(8.5,200.0,8.5,0f,0f)
        players().add(player); byId()[id]=player; player.level().addNewPlayer(player)
        LuckPermsProvider.get().userManager.loadUser(id).join()
        NeoForge.EVENT_BUS.post(PlayerEvent.PlayerLoggedInEvent(player))
        return player
    }
    private fun grant(player:ServerPlayer,node:String,allow:Boolean) {
        val user=LuckPermsProvider.get().userManager.getUser(player.uuid)!!
        user.data().clear(); user.data().add(Node.builder(node).value(allow).build())
    }
    private fun cmd(s:String,source:ServerPlayer=p) { app.server.commands.performPrefixedCommand(source.createCommandSourceStack(),s) }
    private fun action(name:String,block:()->Unit) { steps.add(Step(name,{ block(); true })) }
    private fun waitFor(name:String,limit:Int=500,block:()->Boolean) { steps.add(Step(name,block,limit)) }
    private fun save(kind:String,owner:String,v:Place) {
        action("save $kind $owner") { future=app.store.save(kind,owner,v,true) }
        waitFor("commit $kind") { future!!.isDone.also { if(it) future!!.join() } }
    }
    private fun config(vararg changes:Pair<String,Any>) {
        action("configure ${changes.map { it.first }}") {
            // Test-only snapshot injection; production reload is separately exercised by InventoryProbe.
            val field=LynxRuntime::class.java.getDeclaredField("config").apply { isAccessible=true }
            field.set(app,baseline.copy(values=baseline.values+changes.toMap()))
            app.flights.reload()
        }
    }
    private fun move(level:ServerLevel,x:Double=8.5,y:Double=200.0,z:Double=8.5) {
        check(p.teleportTo(level,x,y,z,emptySet(),0f,0f,true)); net.acknowledge(); p.hasChangedDimension(); net.packets.clear()
    }
    private fun request(command:String,expect:ServerLevel,x:Double,y:Double,z:Double) {
        action("request $command") { net.packets.clear(); cmd(command) }
        waitFor("complete $command") {
            val success=messages.any { it.contains("已传送至") }
            if(success) check(p.level()===expect && p.position().distanceToSqr(x,y,z)<0.01) { "wrong destination ${app.place(p)}" }
            else check(messages.none { it.contains("失败") || it.contains("超时") || it.contains("拒绝") || it.contains("取消") }) { messages.toString() }
            success
        }
    }
    private fun rejected(command:String,text:String) {
        action("request rejection $command") { net.packets.clear(); cmd(command) }
        waitFor("reject $command: $text") { messages.any { it.contains(text) }.also { check(messages.none { s -> s.contains("已传送至") }) } }
    }
    private fun platform(level:ServerLevel) {
        for(x in 4..12) for(z in 4..12) {
            level.setBlock(BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),3)
            for(y in 200..203) level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
        }
    }
    private fun setup() {
        baseline=app.config; configBytes=Files.readAllBytes(configPath)
        running=true
        val overworld=app.server.overworld(); val nether=app.server.getLevel(Level.NETHER)!!
        action("prepare two real dimensions") { future=CompletableFuture.allOf(overworld.chunkSource.addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN,ChunkPos(0,0),1),nether.chunkSource.addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN,ChunkPos(0,0),1)) }
        waitFor("chunks loaded") { future!!.isDone.also { if(it) future!!.join() } }
        action("players online via test registration; no authenticated client") {
            platform(overworld); platform(nether)
            p=register(uuid,"LynxProbe"); net=p.connection as Sink
            actor=register(UUID.randomUUID(),"LynxActor"); actor.snapTo(12.5,200.0,12.5,0f,0f)
            grant(p,"lynxtools.command.*",true)
        }
        if(System.getProperty("lynxtools.verification.terrain")=="true") {
            terrainCases(overworld,nether)
            action("finish terrain") { finish(true) }
            return
        }
        if(restart) {
            action("read persisted death in second JVM") { future=app.store.get("death",uuid.toString()) }
            waitFor("death recovered after JVM restart") { if(!future!!.isDone) false else { death=future!!.join() as Death; check(death!!.place.dimension=="minecraft:the_nether"); true } }
            request("back",nether,8.5,200.0,8.5)
            action("no flight after new JVM login") { check(!p.mayFly()) }
        } else {
            save("warp","probe_nether",Place("minecraft:the_nether",8.5,200.0,8.5,37f,-14f))
            request("warp probe_nether",nether,8.5,200.0,8.5)
            action("yaw and pitch preserved") { check(p.yRot==37f && p.xRot == -14f) }
            action("save native home command") { net.packets.clear(); cmd("sethome") }
            waitFor("home acknowledged after commit") { messages.any { it.contains("home") && (it.contains("保存") || it.contains("更新")) } }
            action("leave home dimension") { move(overworld) }
            request("home",nether,8.5,200.0,8.5)
            save("warp","probe_missing",Place("lynxtools:removed",8.5,200.0,8.5,0f,0f))
            rejected("warp probe_missing","维度")
            save("warp","probe_height",Place("minecraft:overworld",8.5,9999.0,8.5,0f,0f))
            rejected("warp probe_height","坐标无效")
            config("teleport.blocked_target_dimensions" to listOf("minecraft:the_nether"))
            rejected("warp probe_nether","维度")
            config("teleport.strategy" to "exact")
            action("danger floor") { nether.setBlock(BlockPos(8,199,8),Blocks.MAGMA_BLOCK.defaultBlockState(),3); move(overworld) }
            request("warp probe_nether",nether,8.5,200.0,8.5)
            config()
            request("warp probe_nether",nether,8.5,200.0,8.5)
            action("restore floor and native death") {
                nether.setBlock(BlockPos(8,199,8),Blocks.STONE.defaultBlockState(),3); move(nether)
                p.invulnerableTime=0; p.kill(p.level()); check(!p.isAlive)
                net.packets.clear(); cmd("back"); check(messages.any { it.contains("当前状态") })
                future=app.store.get("death",uuid.toString())
            }
            waitFor("death event persisted") {
                if(!future!!.isDone) false else { death=future!!.join() as Death; check(death!!.place==Place("minecraft:the_nether",8.5,200.0,8.5,0f,0f)); true }
            }
            action("native respawn packet path") {
                net.handleClientCommand(ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN))
                p=net.player; p.setNoGravity(true); net.acknowledge(); p.hasChangedDimension(); move(overworld)
                check(p.isAlive && app.server.playerList.getPlayer(uuid)===p)
            }
            request("back",nether,8.5,200.0,8.5)
            action("leave and reuse death") { move(overworld) }
            request("back",nether,8.5,200.0,8.5)
            action("death point now above lava") { nether.setBlock(BlockPos(8,199,8),Blocks.LAVA.defaultBlockState(),3); move(overworld) }
            request("back",nether,7.5,200.0,8.5)
            action("death record unchanged by search") { future=app.store.get("death",uuid.toString()) }
            waitFor("same revision after back") { future!!.isDone.also { if(it) check(future!!.join()==death) } }
            action("late death cancellation leaves flight active") {
                cmd("fly"); cancelDeath=true
                try { p.invulnerableTime=0; p.kill(p.level()) } finally { cancelDeath=false }
                check(p.isAlive && p.mayFly()); cmd("fly"); future=app.store.get("death",uuid.toString())
            }
            waitFor("late cancellation did not replace death record") { future!!.isDone.also { if(it) check(future!!.join()==death) } }
            action("restore death point floor") { nether.setBlock(BlockPos(8,199,8),Blocks.STONE.defaultBlockState(),3); move(overworld) }
            config("back.consume" to true)
            request("back",nether,8.5,200.0,8.5)
            action("read consumed death") { future=app.store.get("death",uuid.toString()) }
            waitFor("successful back consumed only selected death") { future!!.isDone.also { if(it) check(future!!.join()==null) } }
            rejected("back","没有可用的死亡记录")
            config()
            action("fault injection: older death queued, newer rejected") {
                databaseExecutor=Store::class.java.getDeclaredField("executor").apply { isAccessible=true }.get(app.store) as ThreadPoolExecutor
                databaseExecutor.execute { databaseGate.await() }
                app.death(p)
                while(databaseExecutor.queue.remainingCapacity()>0) databaseExecutor.execute { }
                p.snapTo(9.5,200.0,8.5,0f,0f)
                app.death(p)
            }
            waitFor("newer rejection marks back unavailable") { uuid in app.failedDeathWrites }
            action("allow older write to commit") { databaseGate.countDown(); deadline=app.tick+5 }
            waitFor("older success must not clear newer failure") {
                if(app.tick<deadline || databaseExecutor.activeCount!=0 || databaseExecutor.queue.isNotEmpty()) false
                else { check(uuid in app.failedDeathWrites); true }
            }
            action("new successful death recovers availability") { move(nether); app.death(p) }
            waitFor("latest success clears failure") { uuid !in app.failedDeathWrites }
            requestCases(overworld)
            flightCases(overworld,nether)
            config("teleport.wait_seconds" to 2)
            action("movement cancels waiting") { move(overworld); cmd("warp probe_nether"); p.snapTo(9.5,200.0,8.5,0f,0f) }
            waitFor("movement cancellation") { messages.any { it.contains("你移动了") } }
            action("damage cancels waiting") { move(overworld); cmd("warp probe_nether"); p.invulnerableTime=0; p.hurtServer(overworld,p.damageSources().generic(),1f) }
            waitFor("damage cancellation") { messages.any { it.contains("你受伤了") } }
            action("logout cancels pending and active flight") { move(overworld); cmd("fly"); check(p.mayFly()); cmd("warp probe_nether"); app.server.playerList.remove(p); disconnected=p; check(!p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!.hasModifier(marker)) }
            waitFor("pending offline request expires safely",100) { app.tick%40L==0L }
            action("login resets flight and pending work") { p=register(uuid,"LynxProbe"); net=p.connection as Sink; check(!p.mayFly()) }
            config()
            request("back",nether,8.5,200.0,8.5)
        }
        action("finish") { finish(true) }
    }
    private fun terrainCases(overworld:ServerLevel,nether:ServerLevel) {
        val end=app.server.getLevel(Level.END)!!
        val actual=Place("minecraft:the_end",309.76705445397545,1.0625,0.6511966766345503,-89.8472900390625f,-0.954871416091919f)
        config("teleport.checks_per_tick" to 1,"teleport.search_radius" to 2,"teleport.max_candidates" to 128)
        action("load actual carpet column in end") { future=end.chunkSource.addTicketAndLoadWithRadius(TicketType.PLAYER_SPAWN,ChunkPos(19,0),1) }
        waitFor("end chunks ready") { future!!.isDone.also { if(it) future!!.join() } }
        action("build real carpet warp surface") {
            for(x in 307..312) for(z in -2..3) {
                end.setBlock(BlockPos(x,0,z),Blocks.STONE.defaultBlockState(),3)
                for(y in 1..5) end.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
            }
            end.setBlock(BlockPos(309,1,0),Blocks.WHITE_CARPET.defaultBlockState(),3)
            move(overworld)
        }
        save("warp","terrain_carpet",actual)
        request("warp terrain_carpet",end,actual.x,actual.y,actual.z)
        action("warp preserves every saved component exactly") { check(app.place(p)==actual); move(overworld) }
        // Intentional solid obstruction: warp still executes its saved destination, home must not.
        action("obstruct warp body") { end.setBlock(BlockPos(309,2,0),Blocks.STONE.defaultBlockState(),3) }
        request("warp terrain_carpet",end,actual.x,actual.y,actual.z)
        action("warp still exact inside obstruction") { check(app.place(p)==actual); move(overworld); end.setBlock(BlockPos(309,2,0),Blocks.AIR.defaultBlockState(),3) }
        save("warp","terrain_invalid_height",actual.copy(y=9999.0))
        rejected("warp terrain_invalid_height","坐标无效")
        save("warp","terrain_removed_dimension",actual.copy(dimension="lynxtools:removed"))
        rejected("warp terrain_removed_dimension","维度")
        action("warp bounds still include entire body") {
            val edge=end.worldBorder.maxX
            check(!app.teleports.bounds(end,actual.copy(x=edge-0.1)))
        }
        save("death",uuid.toString(),actual)
        request("back",end,actual.x,actual.y,actual.z)
        action("back retains exact carpet feet") { check(app.place(p)==actual); move(overworld) }
        fun clear() {
            move(overworld)
            for(x in 4..12) for(z in 4..12) for(y in 195..206) nether.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
        }
        val shapes=listOf(
            Triple("stone",Blocks.STONE.defaultBlockState(),200.0),
            Triple("carpet",Blocks.WHITE_CARPET.defaultBlockState(),199.0625),
            Triple("fence",Blocks.OAK_FENCE.defaultBlockState(),200.5),
            Triple("pane",Blocks.GLASS_PANE.defaultBlockState(),200.0),
            Triple("wall",Blocks.COBBLESTONE_WALL.defaultBlockState(),200.5),
            Triple("bottom slab",Blocks.STONE_SLAB.defaultBlockState(),199.5),
            Triple("top slab",Blocks.STONE_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,SlabType.TOP),200.0),
            Triple("stairs",Blocks.OAK_STAIRS.defaultBlockState(),200.0),
            Triple("snow layers",Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS,4),199.375)
        )
        val rough=Place("minecraft:the_nether",8.5,199.9,8.5,21f,-11f)
        for((name,block,height) in shapes) {
            action("prepare $name collision surface") { clear(); nether.setBlock(BlockPos(8,198,8),Blocks.STONE.defaultBlockState(),3); nether.setBlock(BlockPos(8,199,8),block,3) }
            save("death",uuid.toString(),rough)
            request("back",nether,8.5,height,8.5)
            action("verify $name exact collision top and original death retained") {
                check(kotlin.math.abs(p.y-height)<1.0e-7 && p.yRot==21f && p.xRot == -11f)
                future=app.store.get("death",uuid.toString())
            }
            waitFor("$name death unchanged") { future!!.isDone.also { if(it) check((future!!.join() as Death).place==rough) } }
        }
        action("prepare void") { clear() }
        save("death",uuid.toString(),rough.copy(y=200.0))
        rejected("back","未找到")
        action("prepare lava") { clear(); for(x in 4..12) for(z in 4..12) nether.setBlock(BlockPos(x,199,z),Blocks.LAVA.defaultBlockState(),3) }
        rejected("back","未找到")
        save("home",uuid.toString(),rough.copy(y=200.0))
        rejected("home","未找到")
        save("warp","terrain_lava",rough.copy(y=200.0))
        request("warp terrain_lava",nether,8.5,200.0,8.5)
        action("prepare blocked headroom across bounded search") {
            clear(); nether.setBlock(BlockPos(8,199,8),Blocks.STONE.defaultBlockState(),3)
            for(x in 4..12) for(z in 4..12) for(y in 201..206) nether.setBlock(BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3)
        }
        rejected("back","未找到")
        action("terrain result") { log.info("TERRAIN_PROBES_PASSED: exact end carpet warp including yaw/pitch and obstruction/lava bypass; back carpet/fence/pane/wall/slabs/stairs/snow/stone; void/lava/headroom and home rejection; budget=1; saved death unchanged") }
    }
    private fun requestCases(overworld:ServerLevel) {
        config("requests.cooldown_seconds" to 0)
        fun chats(player:ServerPlayer)=(player.connection as Sink).packets.filterIsInstance<ClientboundSystemChatPacket>().map { it.content().string }
        fun clear() { net.packets.clear(); (actor.connection as Sink).packets.clear() }
        fun clickCommands():List<String> {
            val card=(actor.connection as Sink).packets.filterIsInstance<ClientboundSystemChatPacket>().last { it.content().string.contains("希望传送") }.content()
            fun walk(c:Component):List<String> = listOfNotNull((c.style.clickEvent as? ClickEvent.RunCommand)?.command())+c.siblings.flatMap { walk(it) }
            return walk(card)
        }
        action("all command roots and aliases available to granted player") {
            val source=p.createCommandSourceStack(); val dispatcher=app.server.commands.dispatcher
            (Commands.nodes.keys+Commands.aliases.keys).forEach { check(dispatcher.root.getChild(it).canUse(source)) { "missing permitted root $it" } }
            fun tab(input:String)=dispatcher.getCompletionSuggestions(dispatcher.parse(input,source)).join().list.map { it.text }
            check(tab("gm ").containsAll(listOf("0","1","2","3","s","c","a","p")))
            check("LynxActor" in tab("fly "))
            check("LynxActor" in tab("tpr ") && "LynxActor" in tab("tprequest "))
            check("reload" in tab("lynxtools "))
            check(tab("setwarp new_name ").isNotEmpty())
        }
        action("send alias emits three native click buttons") {
            clear(); cmd("tprequest LynxActor")
            val clicks=clickCommands(); check(clicks.size==3 && clicks[0].startsWith("/tpa ") && clicks[1].startsWith("/tpd ") && clicks[2].endsWith(" ignore"))
            oldAccept=clicks[0]
            val source=actor.createCommandSourceStack(); val dispatcher=app.server.commands.dispatcher
            for(command in listOf("tpa ","tpaccept ","tpd ","tpdecline ")) check(dispatcher.getCompletionSuggestions(dispatcher.parse(command,source)).join().list.any { it.text=="LynxProbe" })
        }
        action("accept alias notifies both") {
            cmd("tpaccept",actor)
            check(chats(actor).any { it.contains("已接受") } && messages.any { it.contains("已接受") })
        }
        waitFor("accepted request teleports across dimensions") { messages.any { it.contains("已传送至") }.also { if(it) check(p.level()===overworld && p.position().distanceToSqr(actor.position())<4.0) } }
        action("stale click cannot accept a newer request") {
            clear(); cmd("tpr LynxActor"); cmd(oldAccept.removePrefix("/"),actor)
            check(chats(actor).any { it.contains("已失效") })
            check(app.requests.suggestions(actor)==listOf("LynxProbe"))
            cmd("tpdecline",actor)
            check(chats(actor).any { it.contains("已拒绝") } && messages.any { it.contains("拒绝了") })
        }
        action("ignore click only notifies receiver") {
            clear(); cmd("tpr LynxActor"); val ignore=clickCommands()[2]; net.packets.clear(); cmd(ignore.removePrefix("/"),actor)
            check(chats(actor).any { it.contains("已忽略") } && messages.isEmpty())
        }
        action("accept permission denial preserves request") {
            clear(); cmd("tpr LynxActor"); grant(actor,"lynxtools.command.tpa",false); cmd("tpa",actor)
            check(chats(actor).any { it.contains("没有权限") } && app.requests.suggestions(actor).isNotEmpty())
            grant(actor,"lynxtools.command.*",true); cmd("tpd",actor)
        }
        action("temporary recipient state keeps request") {
            clear(); cmd("tpr LynxActor"); actor.startSleeping(actor.blockPosition()); cmd("tpa",actor)
            check(chats(actor).any { it.contains("请求仍然有效") } && app.requests.suggestions(actor).isNotEmpty())
            actor.stopSleepInBed(true,true); actor.snapTo(12.5,200.0,12.5,0f,0f); cmd("tpa",actor)
            check(chats(actor).any { it.contains("已接受") })
        }
        waitFor("same request accepted after recipient recovers") { messages.any { it.contains("已传送至") } }
        action("multiple incoming requests require explicit selection") {
            clear(); third=register(UUID.randomUUID(),"LynxThird"); third!!.snapTo(5.5,200.0,5.5,0f,0f)
            cmd("tpr LynxActor"); cmd("tpr LynxActor",third!!); cmd("tpa",actor)
            check(chats(actor).any { it.contains("多条请求") }); cmd("tpd LynxProbe",actor)
            app.server.playerList.remove(third!!); third=null
        }
        config("requests.cooldown_seconds" to 0,"requests.timeout_seconds" to 5)
        action("request expiry setup") { clear(); cmd("tpr LynxActor") }
        waitFor("request timeout notifies both",160) { messages.any { it.contains("超时") } && chats(actor).any { it.contains("超时") } }
        config("requests.cooldown_seconds" to 0,"teleport.wait_seconds" to 2)
        action("recipient movement after acceptance cancels") { clear(); cmd("tpr LynxActor"); cmd("tpa",actor); actor.snapTo(11.5,200.0,12.5,0f,0f) }
        waitFor("moving recipient cancellation notifies both") { messages.any { it.contains("取消") } && chats(actor).any { it.contains("取消") } }
        config()
    }
    private fun flightCases(overworld:ServerLevel,nether:ServerLevel) {
        for(mode in listOf("s","a","c","p")) action("fly toggles mode $mode") {
            cmd("gm $mode"); net.packets.clear(); cmd("fly"); check(p.mayFly())
            check(!p.abilities.flying || mode=="p")
            cmd("fly"); check(p.mayFly()==(mode=="c" || mode=="p"))
            check(net.packets.filterIsInstance<ClientboundPlayerAbilitiesPacket>().last().canFly()==p.mayFly())
        }
        action("flight survives mode transitions") { cmd("gm s"); cmd("fly"); cmd("gm c"); cmd("gm a"); check(p.mayFly()) }
        action("flight across dimensions") { move(overworld); move(nether); check(p.mayFly()); cmd("fly"); check(!p.mayFly()) }
        action("other modifier survives toggle") {
            val id=Identifier.fromNamespaceAndPath("verification","external_flight")
            val attr=p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!
            attr.addTransientModifier(AttributeModifier(id,1.0,AttributeModifier.Operation.ADD_VALUE))
            cmd("fly"); cmd("fly"); check(attr.hasModifier(id) && p.mayFly()); attr.removeModifier(id)
        }
        action("airborne disable grants one fall protection") {
            p.setOnGround(false); cmd("fly"); p.abilities.flying=true; cmd("fly"); check(!p.mayFly() && !p.abilities.flying)
            val health=p.health; p.invulnerableTime=0; p.hurtServer(p.level(),p.damageSources().fall(),4f); check(p.health==health)
            p.invulnerableTime=0; p.hurtServer(p.level(),p.damageSources().fall(),4f); check(p.health<health); p.health=20f
        }
        config("fly.fall_protection_ticks" to 2)
        action("protection expiry setup") { p.setOnGround(false); cmd("fly"); cmd("fly"); deadline=app.tick+app.config.int("fly.fall_protection_ticks")+1 }
        waitFor("protection time elapsed",10) { app.tick>=deadline }
        action("expired protection does not absorb damage") { val health=p.health; p.invulnerableTime=0; p.hurtServer(p.level(),p.damageSources().fall(),4f); check(p.health<health); p.health=20f }
        config()
        action("external removal of own modifier") { cmd("fly"); p.abilities.flying=true; net.packets.clear(); p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!.removeModifier(marker) }
        waitFor("external removal resynchronizes client",10) { !p.abilities.flying && net.packets.filterIsInstance<ClientboundPlayerAbilitiesPacket>().lastOrNull()?.canFly()==false }
        action("permission revoked") { cmd("fly"); grant(p,"lynxtools.command.fly",false) }
        waitFor("revocation removes ability",50) { !p.mayFly() }
        action("restore admin grant") { grant(p,"lynxtools.command.*",true); cmd("fly") }
        config("modules.fly" to false)
        action("disabled module removed flight") { check(!p.mayFly()) }
        config()
        action("self-only fly cannot modify others") {
            grant(p,"lynxtools.command.fly",true); cmd("fly LynxActor"); check(!actor.mayFly())
            val source=p.createCommandSourceStack(); val dispatcher=app.server.commands.dispatcher
            val options=dispatcher.getCompletionSuggestions(dispatcher.parse("fly ",source)).join().list.map { it.text }
            check("LynxProbe" in options && "LynxActor" !in options)
            cmd("fly"); check(p.mayFly()); cmd("fly LynxProbe"); check(!p.mayFly()); grant(p,"lynxtools.command.*",true)
        }
        action("others-only fly cannot target self by name") {
            grant(actor,"lynxtools.command.fly.others",true); cmd("fly",actor); check(!actor.mayFly()); cmd("fly LynxActor",actor); check(!actor.mayFly())
            val source=actor.createCommandSourceStack(); val dispatcher=app.server.commands.dispatcher
            val options=dispatcher.getCompletionSuggestions(dispatcher.parse("fly ",source)).join().list.map { it.text }
            check("LynxProbe" in options && "LynxActor" !in options)
            cmd("fly LynxProbe",actor); check(p.mayFly())
        }
        action("issuer logs out") { app.server.playerList.remove(actor) }
        waitFor("issuer-offline revokes",50) { !p.mayFly() }
        action("death clears flight") { cmd("fly"); p.invulnerableTime=0; p.kill(p.level()); check(!p.isAlive && !p.getAttribute(NeoForgeMod.CREATIVE_FLIGHT)!!.hasModifier(marker)) }
        action("respawn clears grant") { net.handleClientCommand(ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN)); p=net.player; p.setNoGravity(true); net.acknowledge(); p.hasChangedDimension(); check(!p.mayFly()); move(nether) }
    }
    private fun tick() {
        if(!running || ending) return
        try {
            // Explicit synthetic acknowledgements, never claimed as actual client packet receipt.
            if(::net.isInitialized) net.acknowledge()
            val s=steps.firstOrNull() ?: return
            check(++s.ticks<=s.limit) { "Timeout: ${s.name}; messages=${if(::net.isInitialized) messages else emptyList<String>()}" }
            if(s.test()) { completed++; log.info("PASS {}",s.name); steps.removeFirst() }
        } catch(e:Throwable) { log.error("LIFECYCLE_PROBES_FAILED step={}",steps.firstOrNull()?.name,e); finish(false) }
    }
    private fun finish(success:Boolean) {
        if(ending) return
        ending=true
        databaseGate.countDown()
        if(::p.isInitialized && app.server.playerList.getPlayer(p.uuid)===p) app.server.playerList.remove(p)
        if(::actor.isInitialized && app.server.playerList.getPlayer(actor.uuid)===actor) app.server.playerList.remove(actor)
        third?.let { if(app.server.playerList.getPlayer(it.uuid)===it) app.server.playerList.remove(it) }
        LynxRuntime::class.java.getDeclaredField("config").apply { isAccessible=true }.set(app,baseline)
        configBytes?.let { Files.write(configPath,it) }
        log.info("LIFECYCLE_RESULT success={} restart={} completed={}",success,restart,completed)
        app.server.halt(false)
    }
}
