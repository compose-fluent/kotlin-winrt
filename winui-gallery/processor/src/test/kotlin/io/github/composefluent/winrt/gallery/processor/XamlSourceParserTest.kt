package io.github.composefluent.winrt.gallery.processor

import io.github.composefluent.winrt.gallery.code.KotlinCodeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XamlSourceParserTest {
    @Test
    fun preserves_actual_markup_and_utf16_offsets_for_display_and_copy() {
        val source = """<?xml version="1.0"?>
            |<Page xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
            |  <!-- Keep the sample comment -->
            |  <Button x:Name='button' Content="A &amp; B 😀" Click="onClick" />
            |  <TextBlock><![CDATA[<literal>]]></TextBlock>
            |</Page>
        """.trimMargin()
        val document = XamlSourceParser().parse("Page.xaml", source)
        assertEquals(source, document.source)
        var start = 0
        val tokens = document.spans.map { span ->
            (document.source.substring(start, span.end) to span.kind).also { start = span.end }
        }
        assertEquals(source, tokens.joinToString("") { it.first })
        assertTrue(tokens.contains("Button" to KotlinCodeKind.Type))
        assertTrue(tokens.contains("x:Name" to KotlinCodeKind.Property))
        assertTrue(tokens.contains("&amp;" to KotlinCodeKind.Escape))
        assertTrue(tokens.any { it.second == KotlinCodeKind.Comment && "sample comment" in it.first })
        assertTrue(tokens.contains("<![CDATA[<literal>]]>" to KotlinCodeKind.String))
        assertEquals(source.length, start)
    }

    @Test
    fun empty_or_incomplete_editor_text_does_not_lose_characters() {
        listOf("", "<Button Content=\"unfinished", "<!-- unfinished", "<Page>A & broken</Page>").forEach { source ->
            val document = XamlSourceParser().parse("Sample.xaml", source)
            assertEquals(source, document.source)
            assertEquals(source.length, document.spans.lastOrNull()?.end ?: 0)
        }
    }

    @Test
    fun supplementary_characters_survive_generated_constant_boundaries() {
        val source = "x".repeat(1999) + "😀"
        val generated = generateCodeDocument("Preview", XamlSourceParser().parse("Sample.xaml", source))
        assertTrue(generated.contains("\\ud83d"))
        assertTrue(generated.contains("\\ude00"))
        assertEquals(generated, generated.toByteArray(Charsets.UTF_8).toString(Charsets.UTF_8))
    }
}
