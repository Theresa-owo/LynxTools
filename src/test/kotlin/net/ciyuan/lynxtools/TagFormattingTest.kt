package net.ciyuan.lynxtools

import net.ciyuan.lynxtools.tags.TagFormatting
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TagFormattingTest {
    @Test fun `context gradient endpoints color the whole label for all configured palettes`() {
        val palettes=listOf("#A3ADB8" to "#F5F7FA", "#66D6B5" to "#C7F3D7", "#68B8F5" to "#C4E7FF",
            "#AA8AE8" to "#E1CEFF", "#FFB45E" to "#FFE7A3", "#FFB7D5" to "#B8DFFF")
        for((start,end) in palettes) {
            val spans=TagFormatting.format("[特蕾西娅]","Alex","[❤]",TagFormatting.template(start,end,"<name>"),128)
            assertEquals("[特蕾西娅] Alex [❤]",spans.joinToString("") { it.text })
            assertEquals(start.substring(1).toInt(16),spans.first().rgb)
            assertEquals(end.substring(1).toInt(16),spans.last().rgb)
        }
    }
    @Test fun `missing or invalid metadata falls back without interpreting markup`() {
        val fallback="<gradient:#000000:#ffffff><label></gradient>"
        for((start,end) in listOf(null to null, "#112233" to null, null to "#112233", "red" to "#112233",
            "#12345" to "#123456", "#112233" to "#GGGGGG", "#112233><rainbow>" to "#123456", " #112233" to "#123456")) {
            assertEquals(fallback,TagFormatting.template(start,end,fallback))
        }
        assertEquals("<gradient:#aabbcc:#DDEEFF><label></gradient>",TagFormatting.template("#aabbcc","#DDEEFF",fallback))
    }
    @Test fun `full label removes legacy affix colors but preserves text spacing and safe name`() {
        val spans=TagFormatting.format("&#A3ADB8[旅人]&r ","<red>Alex"," <blue>守 护</blue> &#f5f7fa[❤]&r",
            TagFormatting.template("#A3ADB8","#F5F7FA","<name>"),128)
        assertEquals("[旅人] <red>Alex 守 护 [❤]",spans.joinToString("") { it.text })
        assertEquals("[VIP] Alex [❤]",TagFormatting.format("&x&1&2&3&4&5&6[VIP]&r","Alex","§f[❤]§r","<label>",128).joinToString("") { it.text })
        val bounded=TagFormatting.format("&#A3ADB8"+"😀".repeat(20),"Alex","&r","<label>",16)
        assertEquals(16,bounded.sumOf { it.text.codePointCount(0,it.text.length) })
    }
    @Test fun `complete label has one gradient and separated prefix name suffix`() {
        val spans=TagFormatting.format("<red>[VIP]", "Alex", "<blue>[❤]", "<gradient:#42D9E8:#B58AFF><label></gradient>",128)
        assertEquals("[VIP] Alex [❤]",spans.joinToString("") { it.text })
        assertEquals(0x42D9E8,spans.first().rgb)
        assertEquals(0xB58AFF,spans.last().rgb)
        assertTrue(spans.map { it.rgb }.distinct().size>8)
    }
    @Test fun `complete label omits empty separators and escapes player text`() {
        val spans=TagFormatting.format(" ","<red>Alex","", "<label>",128)
        assertEquals("<red>Alex",spans.joinToString("") { it.text })
    }
    @Test fun `reset and unclosed LP prefix preserve text and semantic style`() {
        val spans=TagFormatting.format("<red>[VIP]<reset> ","Alex","<green> +","<gradient:#00ffff:#ff00ff><name></gradient>",128)
        assertEquals("[VIP] Alex +",spans.joinToString("") { it.text })
        assertEquals(0xFF5555,spans.first().rgb)
        assertEquals(0x55FF55,spans.last().rgb)
        assertTrue(spans.map { it.rgb }.distinct().size>3)
    }
    @Test fun `placeholder input is literal and output is bounded by Unicode code points`() {
        val spans=TagFormatting.format("","<red>X</red>","","<name>",128)
        assertEquals("<red>X</red>",spans.joinToString("") { it.text })
        val bounded=TagFormatting.format("😀".repeat(200),"Alex","","<name>",16)
        val text=bounded.joinToString("") { it.text }
        assertEquals(16,text.codePointCount(0,text.length))
    }
    @Test fun `interactive markup is not executable`() {
        val spans=TagFormatting.format("<click:run_command:'/op x'>","Alex","</click>","<name>",128)
        // The wire model has only literal text, RGB and four decoration bits, no interaction fields.
        assertTrue(spans.all { it.flags in 0..15 })
        assertTrue(spans.joinToString("") { it.text }.contains("Alex"))
    }
}
