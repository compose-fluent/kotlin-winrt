package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds
import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode
import org.junit.Assert.*
import org.junit.Test

class PreviewCaptureLimitationsTest {
    @Test fun only_realized_sdk_surfaces_report_missing_pixels() {
        fun node(type: String, width: Double = 200.0) = WinUIXamlVisualNode(emptyList(), type, "",
            WinUIXamlVisualBounds(0.0, 0.0, width, 100.0))
        assertTrue(previewCaptureLimitations(listOf(node("sample.MediaPlayerElement"),
            node("Microsoft.UI.Xaml.Controls.MediaPlayerElement", 0.0))).isEmpty())
        val diagnostics = previewCaptureLimitations(listOf(
            node("Microsoft.UI.Xaml.Controls.MediaPlayerElement"),
            node("Microsoft.UI.Xaml.Controls.MediaPlayerElement"),
            node("Microsoft.UI.Xaml.Controls.SwapChainPanel")))
        val video = listOf(node("Microsoft.UI.Xaml.Controls.MediaPlayerElement"))
        assertTrue(previewUsesComposition(video))
        assertTrue(previewCaptureLimitations(video, "Composition").isEmpty())
        assertFalse(previewUsesComposition(listOf(node("sample.MediaPlayerElement"))))
        val popup = listOf(video.single().copy(layer = 1))
        assertFalse(previewUsesComposition(popup))
        assertEquals(1, previewCaptureLimitations(popup, "Composition").size)
        assertEquals(1, diagnostics.size)
        assertTrue(diagnostics.any { "Video pixels" in it })
        assertTrue(diagnostics.any { "DirectX swap-chain pixels" in it })
    }
}
