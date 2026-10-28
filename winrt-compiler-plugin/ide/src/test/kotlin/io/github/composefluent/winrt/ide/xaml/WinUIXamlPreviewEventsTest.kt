package io.github.composefluent.winrt.ide.xaml

import io.github.composefluent.winrt.metadata.*
import io.github.composefluent.winrt.ide.preview.WinUIXamlDesignDocument
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class WinUIXamlPreviewEventsTest {
    @Test fun cached_events_prepare_new_documents_without_the_full_catalog() {
        // Reuse normalized metadata member ownership, including inherited project events.
        val ns = "Microsoft.UI.Xaml.Controls"
        val catalog = WinUIXamlCatalog(WinRTMetadataModel(listOf(
            WinRTNamespace(ns, listOf(WinRTTypeDefinition(ns, "Button", WinRTTypeKind.RuntimeClass,
                events = listOf(WinRTEventDefinition("Click", "System.Object"))))),
            WinRTNamespace("Sample", listOf(WinRTTypeDefinition("Sample", "Action", WinRTTypeKind.RuntimeClass,
                baseTypeName = "$ns.Button")))
        )))
        val directory = Files.createTempDirectory("xaml-preview-events")
        val key = "a".repeat(64)
        try {
            WinUIXamlPreviewEvents.from(catalog).save(directory, key)
            val events = requireNotNull(WinUIXamlPreviewEvents.read(directory, key))
            assertTrue(events.isEvent("using:Sample", "Action", "Click"))
            assertFalse(events.isEvent("using:Sample", "Action", "Content"))
            val document = WinUIXamlDesignDocument.prepare(
                """<Button xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" Content="New document" Click="OnClick"/>""",
                event = events::isEvent)
            assertFalse(document.markup.contains("OnClick"))
            assertTrue(document.markup.contains("New document"))
            assertNull(WinUIXamlPreviewEvents.read(directory, "b".repeat(64)))
            Files.writeString(directory.resolve("preview-events-v1-$key.json"), "broken")
            assertNull(WinUIXamlPreviewEvents.read(directory, key))
        } finally { directory.toFile().deleteRecursively() }
    }
}
