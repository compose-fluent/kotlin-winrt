package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode
import io.github.composefluent.winrt.runtime.winUIXamlNeedsCompositionCapture

/** RenderTargetBitmap's SDK limitation applies to realized visuals, not unused
 * templates in the source document. Keep layout/editing available and explain
 * missing pixels instead of presenting an apparently complete capture.
 * https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.xaml.media.imaging.rendertargetbitmap.renderasync
 */
internal fun previewUsesComposition(nodes: List<WinUIXamlVisualNode>): Boolean =
    nodes.any { it.layer == 0 && it.bounds.width > 0 && it.bounds.height > 0 && winUIXamlNeedsCompositionCapture(it.typeName) }

internal fun previewCaptureLimitations(nodes: List<WinUIXamlVisualNode>, backend: String = "Xaml"): List<String> =
    nodes.mapNotNull { node ->
        if (node.bounds.width <= 0 || node.bounds.height <= 0 || (backend == "Composition" && node.layer == 0)) return@mapNotNull null
        if (node.layer > 0 && winUIXamlNeedsCompositionCapture(node.typeName)) return@mapNotNull "Video and DirectX pixels inside Popup/Flyout layers are unavailable in this capture; layout and property editing remain available."
        if (winUIXamlNeedsCompositionCapture(node.typeName))
            "Video pixels and DirectX swap-chain pixels require composition capture. This frame contains XAML only; layout and property editing remain available. The isolated designer can capture composed content."
        else null
    }.distinct()
