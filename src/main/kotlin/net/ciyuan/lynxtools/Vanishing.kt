package net.ciyuan.lynxtools

import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket
import java.util.UUID

/** Ephemeral server state. Packet hooks only read immutable snapshots, including off-thread sends. */
class Vanishing(private val app: Runtime) {
    private val active=mutableSetOf<UUID>()
    private var previous=emptyMap<UUID,Set<UUID>>()
    fun active(p: ServerPlayer)=p.uuid in active
    fun visible(viewer: ServerPlayer, target: ServerPlayer)=!VanishVisibility.hidden(viewer.uuid,target.uuid)
    fun toggle(p: ServerPlayer) {
        if(!active(p) && !p.isCreative) { app.messages.send(p,"vanish_creative_only","error"); return }
        if(!app.teleports.state(p) || p.passengers.isNotEmpty()) { app.messages.send(p,"state","error"); return }
        if(p.containerMenu!==p.inventoryMenu) p.closeContainer()
        if(!active.remove(p.uuid)) active.add(p.uuid)
        app.requests.cancelFor(p.uuid)
        refresh()
        app.messages.send(p,if(active(p)) "vanish_enabled" else "vanish_disabled","success")
        Runtime.LOG.info("Vanish actor={} enabled={}",p.uuid,active(p))
    }
    fun refresh() {
        val players=app.server.playerList.players.toList()
        if(active.isEmpty() && previous.values.all { it.isEmpty() }) {
            previous=players.associate { it.uuid to emptySet<UUID>() }
            VanishVisibility.active=emptySet(); VanishVisibility.hidden=previous
            return
        }
        val allowed=players.filter { app.config.module("vanish") && app.permissions.command(it,"vanish") }.map { it.uuid }.toSet()
        active.toList().forEach { id -> if(id !in allowed || players.none { it.uuid==id && it.isCreative }) {
            players.find { it.uuid==id }?.let { if(it.containerMenu!==it.inventoryMenu) it.closeContainer() }
            active.remove(id)
            players.find { it.uuid==id }?.let { app.messages.send(it,"vanish_revoked","warning") }
        } }
        val next=players.associate { viewer -> viewer.uuid to if(viewer.uuid in allowed) emptySet() else active.filter { it!=viewer.uuid }.toSet() }
        VanishVisibility.active=active.toSet()
        VanishVisibility.hidden=next
        for(viewer in players) {
            val before=previous[viewer.uuid] ?: (active-viewer.uuid); val after=next[viewer.uuid].orEmpty()
            val removed=after-before; val restored=before-after
            if(removed.isNotEmpty()) viewer.connection.send(ClientboundPlayerInfoRemovePacket(removed.toList()))
            val restoredPlayers=players.filter { it.uuid in restored }
            if(restoredPlayers.isNotEmpty()) viewer.connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(restoredPlayers))
            if(before!=after) {
                // Native method updates both this viewer's tracked entities and viewers of this player.
                viewer.level().chunkSource.chunkMap.move(viewer)
                app.names.login(viewer)
            }
        }
        previous=next
    }
    fun logout(p: ServerPlayer) { if(p.containerMenu!==p.inventoryMenu) p.closeContainer(); active.remove(p.uuid); previous=previous-p.uuid }
    fun end(p: ServerPlayer) {
        if(!active(p)) return
        if(p.containerMenu!==p.inventoryMenu) p.closeContainer()
        active.remove(p.uuid); refresh()
    }
    fun stop() { active.clear(); VanishVisibility.active=emptySet(); VanishVisibility.hidden=emptyMap() }
}

object VanishVisibility {
    @Volatile var active: Set<UUID> = emptySet()
    @Volatile var hidden: Map<UUID,Set<UUID>> = emptyMap()
    fun active(entity: Any?)=entity is ServerPlayer && entity.uuid in active
    fun hidden(viewer: UUID, target: UUID)=viewer!=target && target in active && (hidden[viewer]?.contains(target) ?: true)
}
