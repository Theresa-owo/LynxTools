package net.ciyuan.lynxtools.mixin

import net.ciyuan.lynxtools.VanishVisibility
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerPlayer
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable

@Mixin(CommandSourceStack::class)
abstract class VanishSuggestionsMixin {
    @Inject(method=["getOnlinePlayerNames"],at=[At("RETURN")],cancellable=true)
    private fun lynxNames(ci: CallbackInfoReturnable<Collection<String>>) {
        val source=(this as Any) as CommandSourceStack
        val viewer=source.entity as? ServerPlayer ?: return
        val hidden=source.server.playerList.players.filter { VanishVisibility.hidden(viewer.uuid,it.uuid) }.map { it.gameProfile.name() }.toSet()
        ci.returnValue=ci.returnValue.filterNot { it in hidden }
    }
}
