package io.github.composefluent.winrt.metadata

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import io.github.composefluent.winrt.runtime.XamlFramework

class WinUiXamlDeclarationsTest {
    private fun fixture() = javaClass.getResource("/xaml/declarations-v1.json")!!.readText().removePrefix("\uFEFF")

    @Test
    fun framework_is_part_of_the_semantic_fingerprint_and_legacy_indexes_default_to_winui() {
        val legacy = WinUiXamlDeclarations.parse(fixture())
        assertEquals(XamlFramework.WinUi, legacy.framework)
        val uwp = legacy.copy(framework = XamlFramework.Uwp)
        assertNotEquals(WinUiXamlDeclarations.fingerprint(legacy), WinUiXamlDeclarations.fingerprint(uwp))
        assertEquals(XamlFramework.Uwp, WinUiXamlDeclarations.parse(WinUiXamlDeclarations.canonicalText(uwp)).framework)
        assertThrows(IllegalArgumentException::class.java) { parseXamlFramework("automatic") }
    }

    @Test
    fun source_fingerprint_survives_document_normalization_and_index_round_trips() {
        val plain = "<Page>\n  <TextBlock Text=\"Hello\"/>\n</Page>"
        val hash = WinUiXamlDeclarations.sourceFingerprint(plain)
        assertEquals(hash, WinUiXamlDeclarations.sourceFingerprint("\uFEFF" + plain.replace("\n", "\r\n")))
        assertNotEquals(hash, WinUiXamlDeclarations.sourceFingerprint(plain.replace("Hello", "Changed")))
        val original = WinUiXamlDeclarations.parse(fixture())
        val updated = original.copy(pages = original.pages.map { it.copy(sourceHash = hash) })
        assertEquals(hash, WinUiXamlDeclarations.parse(WinUiXamlDeclarations.canonicalText(updated)).pages.single().sourceHash)
        assertEquals("", original.pages.single().sourceHash)
        assertThrows(IllegalArgumentException::class.java) {
            WinUiXamlDeclarations.canonicalText(updated.copy(pages = updated.pages.map { it.copy(sourceHash = "invalid") }))
        }
    }

    @Test
    fun reads_real_fork_harvester_output_without_guessing_kotlin_projection_names() {
        val index = WinUiXamlDeclarations.parse(fixture())
        val page = index.pages.single()
        assertEquals("probe.MainPage", page.className)
        assertEquals("MainPage.xaml", page.resourcePath)
        assertEquals("Microsoft.UI.Xaml.Controls.Page", page.baseTypeName)
        val button = page.connections.single { it.fieldName == "myButton" }
        assertEquals(2, button.id)
        assertEquals("Microsoft.UI.Xaml.Controls.Button", button.typeName)
        assertEquals("onClick", button.events.single().handlerName)
        assertEquals("Microsoft.UI.Xaml.RoutedEventHandler", button.events.single().delegateTypeName)
        assertEquals(WinUiXamlSourceLocation(4, 45), button.events.single().location)
        val canonical = WinUiXamlDeclarations.canonicalText(index)
        assertEquals(canonical, WinUiXamlDeclarations.canonicalText(WinUiXamlDeclarations.parse(canonical)))
    }

    @Test
    fun fingerprint_ignores_order_but_tracks_semantic_changes() {
        val index = WinUiXamlDeclarations.parse(fixture())
        val page = index.pages.single()
        assertEquals(WinUiXamlDeclarations.fingerprint(index), WinUiXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(features = page.features.reversed(), connections = page.connections.reversed())),
        )))
        assertNotEquals(WinUiXamlDeclarations.fingerprint(index), WinUiXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(resourcePath = "Renamed.xaml")),
        )))
    }

    @Test
    fun property_protocol_tracks_defaults_and_rejects_ambiguous_members() {
        val original = WinUiXamlDeclarations.parse(fixture())
        val first = WinUiXamlPropertyDeclaration("Count", "Int32", false, true, "CountChanged", "2", null,
            WinUiXamlSourceLocation(3, 5))
        val second = first.copy(name = "Title", typeName = "String", isValueType = false,
            changedHandler = "TitleChanged", defaultValue = "escaped \"text\"\nline")
        val page = original.pages.single().copy(features = original.pages.single().features + "properties",
            properties = listOf(first, second))
        val index = original.copy(schemaVersion = 3, pages = listOf(page))
        val canonical = WinUiXamlDeclarations.canonicalText(index)
        assertEquals(second.defaultValue, WinUiXamlDeclarations.parse(canonical).pages.single().properties.last().defaultValue)
        assertEquals(WinUiXamlDeclarations.fingerprint(index), WinUiXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(properties = page.properties.reversed())))))
        assertNotEquals(WinUiXamlDeclarations.fingerprint(index), WinUiXamlDeclarations.fingerprint(index.copy(
            pages = listOf(page.copy(properties = listOf(first.copy(defaultValue = "3"), second))))))
        for (invalid in listOf(
            index.copy(schemaVersion = 2),
            index.copy(pages = listOf(page.copy(properties = listOf(first, first)))),
            index.copy(pages = listOf(page.copy(properties = listOf(first.copy(name = "myButton"))))),
            index.copy(pages = listOf(page.copy(properties = listOf(first.copy(isReadOnly = true))))),
            index.copy(pages = listOf(page.copy(properties = listOf(first.copy(defaultValueMarkup = "<Button/>"))))),
            index.copy(pages = listOf(page.copy(properties = listOf(first, second.copy(changedHandler = first.changedHandler))))),
        )) assertThrows(IllegalArgumentException::class.java) { WinUiXamlDeclarations.canonicalText(invalid) }
    }

    @Test
    fun rejects_incompatible_or_ambiguous_declarations() {
        for (invalid in listOf(
            fixture().replace("\"SchemaVersion\": 1", "\"SchemaVersion\": ${WinUiXamlDeclarations.SCHEMA_VERSION + 1}"),
            fixture().replace("MainPage.xaml", "../MainPage.xaml"),
            fixture().replace("named-elements", "unsupported-feature"),
            fixture().replace("\"Id\": 2", "\"Id\": 1"),
        )) assertThrows(IllegalArgumentException::class.java) { WinUiXamlDeclarations.parse(invalid) }
        val index = WinUiXamlDeclarations.parse(fixture())
        assertThrows(IllegalArgumentException::class.java) {
            WinUiXamlDeclarations.canonicalText(index.copy(pages = index.pages + index.pages))
        }
    }

    @Test
    fun failed_compiler_output_cannot_expose_stale_declarations() {
        val file = Files.createTempFile("xaml-output", ".json")
        try {
            Files.writeString(file, """{"KotlinDeclarations":${fixture()},"MSBuildLogEntries":[{"Type":2,"Message":"bad XAML"}]}""")
            assertThrows(IllegalArgumentException::class.java) { WinUiXamlDeclarations.readCompilerOutput(file) }
            Files.writeString(file, """{"KotlinDeclarations":${fixture()},"MSBuildLogEntries":[]}""")
            assertEquals("probe.MainPage", WinUiXamlDeclarations.readCompilerOutput(file).pages.single().className)
            Files.writeString(file, """{"KotlinDeclarations":{"SchemaVersion":1,"Pages":[],"Resources":[]},"MSBuildLogEntries":[]}""")
            assertTrue(WinUiXamlDeclarations.readCompilerOutput(file).pages.isEmpty())
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
