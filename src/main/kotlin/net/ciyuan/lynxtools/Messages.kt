package net.ciyuan.lynxtools

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer

class Messages(private val settings: () -> Settings) {
    fun component(key: String, tone: String = "info", args: Map<String,String> = emptyMap()): MutableComponent {
        val lang = settings().language
        val points = lang.values.getValue("prefix").codePoints().toArray()
        val start = color(lang.values.getValue("prefix_start"))
        val end = color(lang.values.getValue("prefix_end"))
        val result = Component.empty()
        points.forEachIndexed { index, code ->
            val t = if(points.size <= 1) 0.0 else index.toDouble() / (points.size - 1)
            var rgb = 0
            for (shift in listOf(16,8,0)) rgb = rgb or (((((start shr shift) and 255)*(1-t) + ((end shr shift) and 255)*t).toInt()) shl shift)
            result.append(Component.literal(String(Character.toChars(code))).withStyle { it.withColor(rgb) })
        }
        return result.append(Component.literal(lang.text(key,args)).withStyle { it.withColor(color(lang.values.getValue("${tone}_color"))) })
    }
    fun send(player: ServerPlayer, key: String, tone: String = "info", vararg args: Pair<String,String>) = player.sendSystemMessage(component(key,tone,args.toMap()))
    fun send(source: CommandSourceStack, key: String, tone: String = "info", vararg args: Pair<String,String>) { source.sendSystemMessage(component(key,tone,args.toMap())) }
    private fun color(s: String) = s.removePrefix("#").toInt(16)
}
