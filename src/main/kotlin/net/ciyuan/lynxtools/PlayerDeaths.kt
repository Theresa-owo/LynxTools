package net.ciyuan.lynxtools

import net.minecraft.server.level.ServerPlayer

/** Called synchronously only after every cancellable NeoForge death listener has returned. */
object PlayerDeaths {
    var confirmed: ((ServerPlayer) -> Unit)? = null
}
