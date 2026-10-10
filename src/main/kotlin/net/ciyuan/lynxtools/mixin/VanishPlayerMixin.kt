package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(Player::class)
abstract class VanishPlayerMixin {
    @Inject(method=["getMovementEmission"],at=[At("HEAD")],cancellable=true)
    private fun lynxNoSteps(ci: CallbackInfoReturnable<Entity.MovementEmission>) {
        if(VanishVisibility.active(this)) ci.returnValue=Entity.MovementEmission.NONE
    }
}
