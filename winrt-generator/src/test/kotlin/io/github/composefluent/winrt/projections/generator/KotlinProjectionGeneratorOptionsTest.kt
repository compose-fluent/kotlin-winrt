package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.runtime.XamlFramework
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinProjectionGeneratorOptionsTest {
    @Test
    fun defaults_to_winui_for_existing_cli_invocations() {
        assertEquals(XamlFramework.WinUi, parse().xamlFramework)
    }

    @Test
    fun accepts_both_xaml_frameworks_using_the_shared_parser() {
        // CsWinRT Projections.cs selects WUX/MUX mappings by framework, including
        // UWP applications that consume Microsoft.UI.Xaml WinUI 2 control types.
        assertEquals(XamlFramework.Uwp, parse("--xaml-framework", "uwp").xamlFramework)
        assertEquals(XamlFramework.WinUi, parse("--xaml-framework", "winui").xamlFramework)
        assertEquals(XamlFramework.Uwp, parse("--xaml-framework", "Uwp").xamlFramework)
    }

    @Test
    fun rejects_an_unknown_xaml_framework() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parse("--xaml-framework", "desktop")
        }
        assertTrue(error.message.orEmpty().contains("expected winui or uwp"))
    }

    @Test
    fun requires_a_xaml_framework_value() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parse("--xaml-framework")
        }
        assertEquals("--xaml-framework requires a value.", error.message)
    }

    private fun parse(vararg options: String): KotlinProjectionGeneratorOptions =
        KotlinProjectionGeneratorOptions.parse(listOf("--output", "generated") + options)
}
