package net.ciyuan.lynxtools

import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.server.level.ServerPlayer

class Messages(private val settings: () -> Settings) {
    fun presence(joining:Boolean,name:Component):MutableComponent {
        val lang=settings().language
        val kind=if(joining) "join" else "quit"
        val template=lang.text("${kind}_message")
        val marker=legacyMarker(lang.text("${kind}_marker"))
        val result=Component.empty()
        var from=0
        for(match in Language.PLACEHOLDER.findAll(template)) {
            result.append(Component.literal(template.substring(from,match.range.first)))
            result.append(if(match.groupValues[1]=="name") name else marker)
            from=match.range.last+1
        }
        return result.append(Component.literal(template.substring(from)))
    }
    /** Only configured connection markers accept legacy codes; dynamic names remain literal Components. */
    private fun legacyMarker(text:String):MutableComponent {
        val result=Component.empty(); val buffer=StringBuilder(); var style=Style.EMPTY
        val palette=intArrayOf(0x000000,0x0000AA,0x00AA00,0x00AAAA,0xAA0000,0xAA00AA,0xFFAA00,0xAAAAAA,0x555555,0x5555FF,0x55FF55,0x55FFFF,0xFF5555,0xFF55FF,0xFFFF55,0xFFFFFF)
        fun flush() { if(buffer.isNotEmpty()) { result.append(Component.literal(buffer.toString()).withStyle(style)); buffer.setLength(0) } }
        var i=0
        while(i<text.length) {
            if(text[i] in "&§" && i+1<text.length) {
                val code=text[i+1].lowercaseChar(); val color="0123456789abcdef".indexOf(code)
                if(color>=0 || code in "klmnor") {
                    flush()
                    style=if(color>=0) Style.EMPTY.withColor(palette[color]) else when(code) {
                        'k' -> style.withObfuscated(true); 'l' -> style.withBold(true); 'm' -> style.withStrikethrough(true)
                        'n' -> style.withUnderlined(true); 'o' -> style.withItalic(true); else -> Style.EMPTY
                    }
                    i+=2; continue
                }
            }
            buffer.append(text[i++])
        }
        flush(); return result
    }
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
