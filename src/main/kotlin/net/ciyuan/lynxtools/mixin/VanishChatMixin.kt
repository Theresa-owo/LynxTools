package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.ciyuan.lynxtools.VanishEffects
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.minecraft.network.chat.ChatDecorator
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.ChatType
import net.minecraft.network.chat.PlayerChatMessage
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerGamePacketListenerImpl::class)
abstract class VanishChatMixin {
    // NeoForge fires ServerChatEvent from this decorator. Include synchronous chat-mod broadcasts.
    @WrapOperation(method=["*"],at=[At(value="INVOKE",target="Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;")])
    private fun lynxDecorate(decorator:ChatDecorator,player:ServerPlayer?,message:Component,original:Operation<Component?>):Component? {
        val previous=VanishEffects.enter(player ?: this)
        try { return original.call(decorator,player,message) } finally { VanishEffects.exit(previous) }
    }
    @Inject(method=["sendPlayerChatMessage"],at=[At("HEAD")],cancellable=true)
    private fun lynxChat(message: PlayerChatMessage, type: ChatType.Bound, ci: CallbackInfo) {
        val listener=(this as Any) as ServerGamePacketListenerImpl
        if(!VanishVisibility.hidden(listener.player.uuid,message.sender())) return
        // Cancel before native chat index/signature accounting. Self/authorised viewers retain normal signed chat.
        ci.cancel()
    }
}
