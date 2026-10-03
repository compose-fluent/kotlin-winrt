package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WinRTXamlDeclarationsTest {
    private fun fixture() = javaClass.getResource("/xaml/declarations-v1.json")!!.readText().removePrefix("\uFEFF")

    @Test
    fun reads_real_fork_harvester_output_without_guessing_kotlin_projection_names() {
        val index = WinRTXamlDeclarations.parse(fixture())
        val page = index.pages.single()
        assertEquals("probe.MainPage", page.className)
        assertEquals("MainPage.xaml", page.resourcePath)
        assertEquals("Microsoft.UI.Xaml.Controls.Page", page.baseTypeName)
        val button = page.connections.single { it.fieldName == "myButton" }
        assertEquals(2, button.id)
        assertEquals("Microsoft.UI.Xaml.Controls.Button", button.typeName)
        assertEquals("onClick", button.events.single().handlerName)
        assertEquals("Microsoft.UI.Xaml.RoutedEventHandler", button.events.single().delegateTypeName)
        assertEquals(WinRTXamlSourceLocation(4, 45), button.events.single().location)
        val canonical = WinRTXamlDeclarations.canonicalText(index)
        assertEquals(canonical, WinRTXamlDeclarations.canonicalText(WinRTXamlDeclarations.parse(canonical)))
    }

    @Test
    fun fingerprint_ignores_order_but_tracks_semantic_changes() {
        val index = WinRTXamlDeclarations.parse(fixture())
        val page = index.pages.single()
        assertEquals(WinRTXamlDeclarations.fingerprint(index), WinRTXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(features = page.features.reversed(), connections = page.connections.reversed())),
        )))
        assertNotEquals(WinRTXamlDeclarations.fingerprint(index), WinRTXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(resourcePath = "Renamed.xaml")),
        )))
    }

    @Test
    fun rejects_incompatible_or_ambiguous_declarations() {
        for (invalid in listOf(
            fixture().replace("\"SchemaVersion\": 1", "\"SchemaVersion\": ${WinRTXamlDeclarations.SCHEMA_VERSION + 1}"),
            fixture().replace("MainPage.xaml", "../MainPage.xaml"),
            fixture().replace("named-elements", "unsupported-feature"),
            fixture().replace("\"Id\": 2", "\"Id\": 1"),
        )) assertThrows(IllegalArgumentException::class.java) { WinRTXamlDeclarations.parse(invalid) }
        val index = WinRTXamlDeclarations.parse(fixture())
        assertThrows(IllegalArgumentException::class.java) {
            WinRTXamlDeclarations.canonicalText(index.copy(pages = index.pages + index.pages))
        }
    }

    @Test
    fun failed_compiler_output_cannot_expose_stale_declarations() {
        val file = Files.createTempFile("xaml-output", ".json")
        try {
            Files.writeString(file, """{"KotlinDeclarations":${fixture()},"MSBuildLogEntries":[{"Type":2,"Message":"bad XAML"}]}""")
            assertThrows(IllegalArgumentException::class.java) { WinRTXamlDeclarations.readCompilerOutput(file) }
            Files.writeString(file, """{"KotlinDeclarations":${fixture()},"MSBuildLogEntries":[]}""")
            assertEquals("probe.MainPage", WinRTXamlDeclarations.readCompilerOutput(file).pages.single().className)
            Files.writeString(file, """{"KotlinDeclarations":{"SchemaVersion":1,"Pages":[],"Resources":[]},"MSBuildLogEntries":[]}""")
            assertTrue(WinRTXamlDeclarations.readCompilerOutput(file).pages.isEmpty())
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
