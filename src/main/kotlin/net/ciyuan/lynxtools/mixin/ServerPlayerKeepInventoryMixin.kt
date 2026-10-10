package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.ciyuan.lynxtools.Suicides
import net.ciyuan.lynxtools.PlayerDeaths
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRules
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ServerPlayer::class)
abstract class ServerPlayerKeepInventoryMixin {
    @WrapOperation(method=["die"],at=[At(value="INVOKE",target="Lnet/neoforged/neoforge/common/CommonHooks;onLivingDeath(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z")])
    private fun lynxConfirmedDeath(entity:LivingEntity,source:DamageSource,original:Operation<Boolean>):Boolean {
        val canceled=original.call(entity,source)
        if(!canceled) PlayerDeaths.confirmed?.invoke((this as Any) as ServerPlayer)
        return canceled
    }
    @WrapOperation(method=["restoreFrom"], at=[At(value="INVOKE", target="Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")])
    private fun lynxRestoreInventory(rules: GameRules, rule: GameRule<*>, original: Operation<Any>, oldPlayer: ServerPlayer, restoreAll: Boolean): Any =
        if(rule===GameRules.KEEP_INVENTORY && Suicides.keeps(oldPlayer)) true else original.call(rules,rule)

    @Inject(method=["restoreFrom"], at=[At("RETURN")])
    private fun lynxClearDeathMarker(oldPlayer: ServerPlayer, restoreAll: Boolean, ci: CallbackInfo) {
        Suicides.clear(oldPlayer)
        Suicides.clear((this as Any) as ServerPlayer)
    }
}
