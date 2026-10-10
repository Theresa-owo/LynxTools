package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ShulkerBoxBlockEntity::class)
abstract class VanishShulkerMixin {
    @Inject(method=["startOpen", "stopOpen"],at=[At("HEAD")],cancellable=true)
    private fun lynxOpen(user: ContainerUser,ci: CallbackInfo) { if(VanishVisibility.active(user.livingEntity)) ci.cancel() }
}
