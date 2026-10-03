package io.github.composefluent.winrt.gallery.processor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GallerySampleDefinitionTest {
    @Test fun upstream_sections_keep_code_independent_and_allow_xaml_only_examples() {
        val source = "--- header\r\nA button\r\n--- xaml\r\n<Button Content=\"A &amp; B\" />\r\n"
        val sample = GallerySampleDefinition.parse(source)
        assertEquals("A button", sample.header)
        assertEquals("<Button Content=\"A &amp; B\" />", sample.xaml)
        assertEquals("", sample.kotlin)
    }

    @Test fun kotlin_section_preserves_indentation_and_literal_section_text() {
        val sample = GallerySampleDefinition.parse("--- Header\nTest\n--- Kotlin\nfun click() {\n    println(\"--- xaml\")\n}\n")
        assertEquals("fun click() {\n    println(\"--- xaml\")\n}", sample.kotlin)
    }

    @Test fun malformed_definitions_fail_instead_of_showing_another_examples_code() {
        listOf("--- header\nTitle", "--- xaml\n<Button/>",
            "--- header\nTitle\n--- Header\nOther\n--- xaml\n<Button/>",
            "--- header\nTitle\n--- c#\nvoid Click() {}").forEach {
            assertFailsWith<IllegalArgumentException> { GallerySampleDefinition.parse(it) }
        }
    }
}
