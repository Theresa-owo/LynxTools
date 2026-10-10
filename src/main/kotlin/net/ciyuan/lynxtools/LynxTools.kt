package net.ciyuan.lynxtools

import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.Mod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.CommandEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.ciyuan.lynxtools.protocol.TagUpdate

@Mod(value="lynxtools", dist=[Dist.DEDICATED_SERVER])
class LynxTools(modBus:IEventBus) {
    private var runtime: Runtime? = null
    private val commands=Commands { runtime }
    init { NeoForge.EVENT_BUS.register(this); modBus.addListener<RegisterPayloadHandlersEvent> { e -> e.registrar("1").optional().playToClient(TagUpdate.TYPE,TagUpdate.CODEC) } }
    @SubscribeEvent fun starting(e: ServerStartingEvent) { runtime=Runtime(e.server); PlayerDeaths.confirmed={ p -> runtime?.death(p) } }
    @SubscribeEvent fun stopping(e: ServerStoppingEvent) { PlayerDeaths.confirmed=null; runtime?.stop(); runtime=null }
    @SubscribeEvent fun commands(e: RegisterCommandsEvent) { commands.register(e.dispatcher) }
    @SubscribeEvent fun command(e: CommandEvent) { commands.intercept(e) }
    @SubscribeEvent fun tick(e: ServerTickEvent.Post) { runtime?.tick() }
    @SubscribeEvent fun damage(e: LivingDamageEvent.Post) {
        val p=e.entity as? ServerPlayer ?: return
        if(e.inflictedDamage>0) runtime?.teleports?.cancel(p.uuid,"cancel_damage")
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) fun protect(e: LivingDamageEvent.Pre) { runtime?.flights?.damage(e) }
    @SubscribeEvent fun login(e: PlayerEvent.PlayerLoggedInEvent) { (e.entity as? ServerPlayer)?.let { runtime?.login(it) } }
    @SubscribeEvent fun logout(e: PlayerEvent.PlayerLoggedOutEvent) { (e.entity as? ServerPlayer)?.let { runtime?.logout(it) } }
    @SubscribeEvent fun respawn(e: PlayerEvent.PlayerRespawnEvent) { (e.entity as? ServerPlayer)?.let { runtime?.respawn(it) } }
    @SubscribeEvent fun dimension(e: PlayerEvent.PlayerChangedDimensionEvent) { (e.entity as? ServerPlayer)?.let { runtime?.flights?.reconcile(it) } }
    @SubscribeEvent fun mode(e: PlayerEvent.PlayerChangeGameModeEvent) {
        val p=e.entity as? ServerPlayer ?: return
        // Game mode event is PRE-change; reconcile after vanilla has written/synchronised abilities.
        runtime?.defer { runtime?.flights?.reconcile(p) }
    }
}
