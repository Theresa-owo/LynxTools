package net.ciyuan.lynxtools

import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.LuckPerms
import net.luckperms.api.event.EventSubscription
import net.luckperms.api.event.user.UserDataRecalculateEvent
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.slf4j.LoggerFactory
import net.neoforged.fml.ModList
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentLinkedQueue

class Runtime(val server: MinecraftServer) {
    var config=Settings.load(Path.of(".")); private set
    val messages=Messages { config }
    private val api: LuckPerms=LuckPermsProvider.get()
    val permissions=Permissions(api) { config }
    val store=Store(Path.of(config.string("storage.database")),config)
    val teleports=Teleports(this)
    val flights=Flights(this)
    val requests=Requests(this)
    val names=net.ciyuan.lynxtools.tags.NameSync(this)
    val warpNames=sortedSetOf<String>()
    val failedDeathWrites=mutableSetOf<UUID>()
    private val deathGeneration=mutableMapOf<UUID,Long>()
    var tick=0L; private set
    @Volatile var stopping=false; private set
    private val configWorker=Executors.newSingleThreadExecutor { r -> Thread(r,"LynxTools-Config").apply { isDaemon=true } }
    private var reloading=false
    private val deferred=ConcurrentLinkedQueue<() -> Unit>()
    private val visibility=mutableMapOf<UUID,List<Boolean>>()
    private val subscription: EventSubscription<UserDataRecalculateEvent> = api.eventBus.subscribe(ModList.get().getModContainerById("lynxtools").orElseThrow(),UserDataRecalculateEvent::class.java) { e ->
        defer { server.playerList.getPlayer(e.user.uniqueId)?.let { refresh(it); flights.reload(); names.dirty(it.uuid) } }
    }
    init {
        try {
            store.open(server.usesAuthentication()).join()
            warpNames.addAll(store.names().join())
        } catch(e: Exception) {
            subscription.close(); configWorker.shutdown()
            try { store.close() } catch(close: Exception) { e.addSuppressed(close) }
            LOG.error("LynxTools startup failed; existing database preserved",e)
            throw IllegalStateException("LynxTools database initialization failed; see server log",e)
        }
        if(config.bool("logging.startup_banner")) LOG.info("\n" + """
             _
            | |   _   _ _ __ __  __
            | |  | | | | '_ \\ \/ /
            | |__| |_| | | | |>  <
            |_____\__, |_| |_/_/\_\
                  |___/
        """.trimIndent())
        LOG.info("LynxTools ready: Minecraft 26.1.2 / NeoForge 26.1.2.115, online-mode={}, schema=1",server.usesAuthentication())
    }
    fun place(p: ServerPlayer)=Place(p.level().dimension().identifier().toString(),p.x,p.y,p.z,p.yRot,p.xRot)
    fun online(p: ServerPlayer)=!stopping && server.playerList.getPlayer(p.uuid)===p && !p.hasDisconnected()
    fun defer(work: () -> Unit) { if(!stopping) deferred.add(work) }
    fun <T> complete(future: CompletableFuture<T>, context: String, action: (T?,Throwable?) -> Unit) {
        future.whenComplete { value,error ->
            if(error!=null) LOG.error("LynxTools operation failed: {}",context,error)
            defer { action(value,error) }
        }
    }
    fun tick() {
        tick++
        // Snapshot the queue: actions queued by a game-mode event run on the next tick.
        val count=deferred.size.coerceAtMost(1024)
        repeat(count) { deferred.poll()?.let { work -> try { work() } catch(e: Exception) { LOG.error("LynxTools server callback failed",e) } } }
        try { teleports.tick() } catch(e: Exception) { LOG.error("Teleport preparation failed",e); teleports.cancelAll("internal") }
        flights.tick()
        requests.tick()
        names.tick()
        if(tick % config.int("fly.recheck_ticks")==0L) server.playerList.players.forEach { refresh(it) }
    }
    fun refresh(p: ServerPlayer) {
        val state=Commands.nodes.map { (cmd,node) -> (cmd=="lynxtools" || config.module(cmd)) && (permissions.command(p,node) || cmd=="fly" && permissions.command(p,"fly_others")) }
        if(visibility.put(p.uuid,state)!=state) server.commands.sendCommands(p)
    }
    fun login(p: ServerPlayer) { flights.clear(p); if(p.isAlive) Suicides.clear(p); refresh(p); names.login(p) }
    fun logout(p: ServerPlayer) { requests.cancelFor(p.uuid); teleports.clear(p.uuid); flights.clear(p); visibility.remove(p.uuid); flights.reload(); names.logout(p) }
    fun respawn(p: ServerPlayer) { requests.cancelFor(p.uuid); flights.clear(p); teleports.cancel(p.uuid); refresh(p) }
    fun death(p: ServerPlayer) {
        requests.cancelFor(p.uuid); teleports.cancel(p.uuid); flights.clear(p)
        if(config.module("back")) {
            val place=place(p); val uuid=p.uuid; val name=p.gameProfile.name()
            val generation=(deathGeneration[uuid] ?: 0L)+1
            deathGeneration[uuid]=generation
            complete(store.save("death",uuid.toString(),place,true,name),"save death uuid=$uuid dimension=${place.dimension}") { _,error ->
                // A newer write can fail immediately when the queue is full, before this older write completes.
                if(deathGeneration[uuid]!=generation) return@complete
                if(error!=null) {
                    failedDeathWrites.add(uuid)
                    server.playerList.getPlayer(uuid)?.let { messages.send(it,"storage","error") }
                } else failedDeathWrites.remove(uuid)
            }
        }
    }
    fun reload(source: CommandSourceStack) {
        if(reloading) { messages.send(source,"busy","warning"); return }
        reloading=true
        complete(CompletableFuture.supplyAsync({ Settings.load(Path.of(".")) },configWorker),"reload configuration") { fresh,error ->
            reloading=false
            if(error!=null || fresh==null) { messages.send(source,"reload_failed","error"); return@complete }
            val restart=Settings.restartKeys.filter { config.values[it]!=fresh.values[it] }
            config=fresh.copy(values=fresh.values + restart.associateWith { config.values.getValue(it) })
            teleports.cancelAll("cancel_reload"); flights.reload()
            requests.clear(true)
            names.reset()
            visibility.clear(); server.playerList.players.forEach { refresh(it) }
            messages.send(source,"reload_ok","success")
            if(restart.isNotEmpty()) messages.send(source,"restart_needed","warning","keys" to restart.joinToString(", "))
        }
    }
    fun stop() {
        stopping=true; subscription.close(); requests.clear(false); teleports.cancelAll(); flights.stop(); deferred.clear()
        configWorker.shutdown()
        try { store.close() } catch(e: Exception) { LOG.error("LynxTools database shutdown failed; inspect database/WAL before backup",e) }
    }
    companion object { val LOG=LoggerFactory.getLogger("LynxTools") }
}
