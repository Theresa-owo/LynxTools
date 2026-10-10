package net.ciyuan.lynxtools.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.ciyuan.lynxtools.Suicides
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.gamerules.GameRule
import net.minecraft.world.level.gamerules.GameRules
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

@Mixin(Player::class)
abstract class PlayerKeepInventoryMixin {
    @WrapOperation(method=["dropEquipment","getBaseExperienceReward"], at=[At(value="INVOKE", target="Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")])
    private fun lynxKeepInventory(rules: GameRules, rule: GameRule<*>, original: Operation<Any>): Any {
        val player=(this as Any) as? ServerPlayer
        return if(rule===GameRules.KEEP_INVENTORY && player!=null && Suicides.keeps(player)) true else original.call(rules,rule)
    }
}
