package io.github.composefluent.winrt.ide.preview

import io.github.composefluent.winrt.runtime.WinUIXamlVisualNode

/** RenderTargetBitmap's SDK limitation applies to realized visuals, not unused
 * templates in the source document. Keep layout/editing available and explain
 * missing pixels instead of presenting an apparently complete capture.
 * https://learn.microsoft.com/windows/windows-app-sdk/api/winrt/microsoft.ui.xaml.media.imaging.rendertargetbitmap.renderasync
 */
internal fun previewCaptureLimitations(nodes: List<WinUIXamlVisualNode>): List<String> =
    nodes.mapNotNull { node ->
        if (node.bounds.width <= 0 || node.bounds.height <= 0) return@mapNotNull null
        when (node.typeName) {
            "Microsoft.UI.Xaml.Controls.MediaPlayerElement", "Windows.UI.Xaml.Controls.MediaPlayerElement" ->
                "Video pixels are not included by the XAML capture API. Run the application to inspect video playback; layout and property editing remain available."
            "Microsoft.UI.Xaml.Controls.SwapChainPanel", "Windows.UI.Xaml.Controls.SwapChainPanel",
            "Windows.UI.Xaml.Controls.SwapChainBackgroundPanel" ->
                "DirectX swap-chain pixels are not included by the XAML capture API. Run the application to inspect this content; layout and property editing remain available."
            else -> null
        }
    }.distinct()
