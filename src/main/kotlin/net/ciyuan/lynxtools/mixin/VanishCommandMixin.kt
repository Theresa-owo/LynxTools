package net.ciyuan.lynxtools.mixin

import com.mojang.brigadier.ParseResults
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod
import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import net.ciyuan.lynxtools.VanishEffects
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import org.spongepowered.asm.mixin.Mixin

@Mixin(Commands::class)
abstract class VanishCommandMixin {
    @WrapMethod(method=["performCommand"])
    private fun lynxCommand(parse: ParseResults<CommandSourceStack>,text: String,original: Operation<Void>) {
        val previous=VanishEffects.enter(parse.context.source.entity ?: this)
        try { original.call(parse,text) } finally { VanishEffects.exit(previous) }
    }
}
