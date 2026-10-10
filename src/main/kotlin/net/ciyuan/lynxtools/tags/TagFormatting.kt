package net.ciyuan.lynxtools.tags

import net.ciyuan.lynxtools.protocol.TagSpan
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags

object TagFormatting {
    private val rgb=Regex("#[0-9a-fA-F]{6}")
    private val legacyColor=Regex("(?i)(?:[&§]#[0-9a-f]{6}|[&§]x(?:[&§][0-9a-f]){6}|[&§][0-9a-fk-or])")
    fun template(start:String?,end:String?,fallback:String):String =
        if(start!=null && end!=null && rgb.matches(start) && rgb.matches(end))
            "<gradient:$start:$end><label></gradient>" else fallback
    private val parser = MiniMessage.builder().tags(TagResolver.resolver(
        StandardTags.color(),StandardTags.decorations(),StandardTags.gradient(),StandardTags.rainbow(),StandardTags.reset()
    )).build()
    fun format(prefix:String,name:String,suffix:String,template:String,limit:Int):List<TagSpan> {
        require(prefix.length<=2048 && suffix.length<=2048 && template.length<=2048)
        fun plain(component:Component):String=buildString {
            if(component is TextComponent) append(component.content())
            component.children().forEach { append(plain(it)) }
        }
        val root=if(template.contains("<label>")) {
            // Parse LP formatting first, then insert the complete label as literal text into ONE gradient.
            val text=listOf(plain(parser.deserialize(prefix.replace(legacyColor,""))).trim(),name,plain(parser.deserialize(suffix.replace(legacyColor,""))).trim())
                .filter { it.isNotEmpty() }.joinToString(" ")
            parser.deserialize(template,Placeholder.unparsed("label",text),Placeholder.unparsed("name",name))
        } else Component.empty().append(parser.deserialize(prefix))
            .append(parser.deserialize(template,Placeholder.unparsed("name",name)))
            .append(parser.deserialize(suffix))
        val output=mutableListOf<TagSpan>()
        var left=limit
        fun walk(c:Component,parent:Style) {
            if(left<=0) return
            val style=c.style().merge(parent,Style.Merge.Strategy.IF_ABSENT_ON_TARGET)
            if(c is TextComponent) {
                val points=c.content().codePoints().filter { !Character.isISOControl(it) }.limit(left.toLong()).toArray()
                if(points.isNotEmpty()) {
                    left-=points.size
                    val text=String(points,0,points.size)
                    var flags=0
                    listOf(TextDecoration.BOLD,TextDecoration.ITALIC,TextDecoration.UNDERLINED,TextDecoration.STRIKETHROUGH).forEachIndexed { i,d -> if(style.decoration(d)==TextDecoration.State.TRUE) flags=flags or (1 shl i) }
                    val color=style.color()?.value() ?: 0xFFFFFF
                    val prev=output.lastOrNull()
                    if(prev!=null && prev.rgb==color && prev.flags==flags) output[output.lastIndex]=prev.copy(text=prev.text+text)
                    else output.add(TagSpan(text,color,flags))
                }
            }
            c.children().forEach { walk(it,style) }
        }
        walk(root,Style.empty())
        return output
    }
}
