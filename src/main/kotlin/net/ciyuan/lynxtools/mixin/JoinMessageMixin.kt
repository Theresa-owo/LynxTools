package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.ciyuan.lynxtools.PresenceHooks
import net.minecraft.network.Connection
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.CommonListenerCookie
import net.minecraft.server.players.PlayerList
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(PlayerList::class)
abstract class JoinMessageMixin {
    @WrapOperation(method=["placeNewPlayer"],at=[At(value="INVOKE",target="Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")])
    private fun lynxJoin(list:PlayerList,message:Component,overlay:Boolean,original:Operation<Void>,connection:Connection,player:ServerPlayer,cookie:CommonListenerCookie) {
        if(!PresenceHooks.replace(player,true)) original.call(list,message,overlay)
    }
}
