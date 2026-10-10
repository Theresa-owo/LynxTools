package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ServerPlayer::class)
abstract class ServerPlayerVanishMixin {
    @Inject(method=["broadcastToPlayer"],at=[At("HEAD")],cancellable=true)
    private fun lynxVisible(viewer: ServerPlayer, ci: CallbackInfoReturnable<Boolean>) {
        val player=(this as Any) as ServerPlayer
        if(VanishVisibility.hidden(viewer.uuid,player.uuid)) ci.returnValue=false
    }
}
