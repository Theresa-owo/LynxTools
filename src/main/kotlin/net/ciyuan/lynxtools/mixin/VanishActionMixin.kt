package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.ciyuan.lynxtools.VanishEffects
import net.minecraft.network.protocol.game.*
import net.minecraft.server.network.ServerGamePacketListenerImpl
import org.spongepowered.asm.mixin.Mixin

@Mixin(ServerGamePacketListenerImpl::class)
abstract class VanishActionMixin {
    @WrapMethod(method=["handleAttack"])
    private fun lynxAttack(packet: ServerboundAttackPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleMovePlayer"])
    private fun lynxhandleMovePlayer(packet: ServerboundMovePlayerPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handlePlayerAction"])
    private fun lynxhandlePlayerAction(packet: ServerboundPlayerActionPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleUseItemOn"])
    private fun lynxhandleUseItemOn(packet: ServerboundUseItemOnPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleUseItem"])
    private fun lynxhandleUseItem(packet: ServerboundUseItemPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleInteract"])
    private fun lynxhandleInteract(packet: ServerboundInteractPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleAnimate"])
    private fun lynxhandleAnimate(packet: ServerboundSwingPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleContainerClose"])
    private fun lynxhandleContainerClose(packet: ServerboundContainerClosePacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleContainerClick"])
    private fun lynxhandleContainerClick(packet: ServerboundContainerClickPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["handleContainerButtonClick"])
    private fun lynxhandleContainerButtonClick(packet: ServerboundContainerButtonClickPacket, original: Operation<Void>) {
        val previous=VanishEffects.enter(((this as Any) as ServerGamePacketListenerImpl).player)
        try { original.call(packet) } finally { VanishEffects.exit(previous) }
    }
}
