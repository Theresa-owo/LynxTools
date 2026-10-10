package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.ciyuan.lynxtools.PresenceHooks
import net.minecraft.network.chat.Component
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.server.players.PlayerList
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(ServerGamePacketListenerImpl::class)
abstract class QuitMessageMixin {
    @WrapOperation(method=["removePlayerFromWorld"],at=[At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")])
    private fun lynxQuit(list:PlayerList,message:Component,overlay:Boolean,original:Operation<Void>) {
        val player=((this as Any) as ServerGamePacketListenerImpl).player
        if(!PresenceHooks.replace(player,false)) original.call(list,message,overlay)
    }
}
