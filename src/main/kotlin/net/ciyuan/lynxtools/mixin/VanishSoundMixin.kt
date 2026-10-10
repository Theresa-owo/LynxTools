package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.ciyuan.lynxtools.VanishEffects
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.entity.Entity
import org.spongepowered.asm.mixin.Mixin

@Mixin(Entity::class)
abstract class VanishSoundMixin {
    @WrapMethod(method=["playSound(Lnet/minecraft/sounds/SoundEvent;FF)V"])
    private fun lynxSound(sound: SoundEvent,volume: Float,pitch: Float,original: Operation<Void>) {
        val previous=VanishEffects.enter(this)
        try { original.call(sound,volume,pitch) } finally { VanishEffects.exit(previous) }
    }
    @WrapMethod(method=["playSound(Lnet/minecraft/sounds/SoundEvent;)V"])
    private fun lynxSoundDefault(sound: SoundEvent,original: Operation<Void>) {
        val previous=VanishEffects.enter(this)
        try { original.call(sound) } finally { VanishEffects.exit(previous) }
    }
}
