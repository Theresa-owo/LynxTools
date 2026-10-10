package net.ciyuan.lynxtools

import net.minecraft.server.level.ServerPlayer

/** A per-entity, persisted one-death marker; never changes a world game rule. */
object Suicides {
    private const val KEY="lynxtools:suicide_keep_inventory"
    @JvmStatic fun keeps(player: ServerPlayer): Boolean = player.persistentData.getBooleanOr(KEY,false)
    @JvmStatic fun clear(player: ServerPlayer) { player.persistentData.remove(KEY) }
    fun execute(app: Runtime, player: ServerPlayer, keep: Boolean) {
        if(!app.teleports.state(player)) { app.messages.send(player,"state","error"); return }
        if(keep) player.persistentData.putBoolean(KEY,true)
        try {
            // Exactly the native /kill path, including generic_kill damage and NeoForge death hooks.
            player.kill(player.level())
            // Native ServerPlayer.die marks the client unloaded only after the cancellable death hook.
            // Health can already be zero even when another mod cancels that hook.
            val died=!player.isAlive && !player.connection.hasClientLoaded()
            Runtime.LOG.info("Suicide actor={} keepInventory={} died={}",player.uuid,keep,died)
            app.messages.send(player,if(!died) "state" else if(keep) "suicide_kept" else "suicide_done",if(died) "success" else "error")
        } finally {
            // A cancelled death must not leak KeepInventory into an unrelated subsequent death.
            if(player.isAlive || player.connection.hasClientLoaded()) clear(player)
        }
    }
}
