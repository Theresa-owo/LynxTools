package net.ciyuan.lynxtools

import net.ciyuan.lynxtools.tags.TagFormatting
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TagFormattingTest {
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
