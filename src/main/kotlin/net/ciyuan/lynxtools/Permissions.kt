package net.ciyuan.lynxtools

import net.luckperms.api.LuckPerms
import net.luckperms.api.util.Tristate
import net.minecraft.server.level.ServerPlayer

class Permissions(private val api: LuckPerms, private val settings: () -> Settings) {
    fun command(player: ServerPlayer, command: String) = check(player, "lynxtools.command.${command.replace('_','.')}", settings().bool("permissions.$command"))
    fun warp(player: ServerPlayer, name: String) = !settings().bool("warp.per_warp_permissions") || check(player,"lynxtools.warp.$name",false)
    private fun check(player: ServerPlayer, node: String, default: Boolean): Boolean {
        val user = api.userManager.getUser(player.uuid) ?: return false
        // ServerPlayer is LuckPerms' NeoForge platform player type. Never cache QueryOptions across calls.
        val options = api.contextManager.getQueryOptions(player)
        return when(user.cachedData.getPermissionData(options).checkPermission(node)) {
            Tristate.TRUE -> true
            Tristate.FALSE -> false
            Tristate.UNDEFINED -> default
        }
    }
}
