package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ContainerOpenersCounter
import net.minecraft.world.level.block.state.BlockState
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(ContainerOpenersCounter::class)
abstract class VanishContainerMixin {
    @Inject(method=["incrementOpeners"],at=[At("HEAD")],cancellable=true)
    private fun lynxOpen(entity: LivingEntity,level: Level,pos: BlockPos,state: BlockState,range: Double,ci: CallbackInfo) {
        if(VanishVisibility.active(entity)) ci.cancel()
    }
    @Inject(method=["decrementOpeners"],at=[At("HEAD")],cancellable=true)
    private fun lynxClose(entity: LivingEntity,level: Level,pos: BlockPos,state: BlockState,ci: CallbackInfo) {
        if(VanishVisibility.active(entity)) ci.cancel()
    }
    @Inject(method=["hasContainerOpen"],at=[At("HEAD")],cancellable=true)
    private fun lynxRecheck(entity: Entity,pos: BlockPos,ci: CallbackInfoReturnable<Boolean>) {
        if(VanishVisibility.active(entity)) ci.returnValue=false
    }
}
