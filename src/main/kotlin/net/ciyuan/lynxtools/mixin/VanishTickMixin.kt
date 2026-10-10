package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.ciyuan.lynxtools.VanishEffects
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin

@Mixin(ServerPlayer::class)
abstract class VanishTickMixin {
    @WrapMethod(method=["tick", "doTick"])
    private fun lynxTick(original: Operation<Void>) {
        val previous=VanishEffects.enter(this)
        try { original.call() } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["checkFallDamage"])
    private fun lynxFall(y: Double, ground: Boolean, state: BlockState, pos: BlockPos, original: Operation<Void>) {
        val previous=VanishEffects.enter(this)
        try { original.call(y,ground,state,pos) } finally { VanishEffects.exit(previous) }
    }
}
