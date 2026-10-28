@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

// Matches the XAML designer's documented 12.5%–800% navigation range.
internal val previewZoomLevels = listOf(.125f, .25f, .5f, .75f, 1f, 1.5f, 2f, 3f, 4f, 6f, 8f)

internal fun previewZoomStep(current: Float, increase: Boolean): Float =
    if (increase) previewZoomLevels.firstOrNull { it > current + .001f } ?: 8f
    else previewZoomLevels.lastOrNull { it < current - .001f } ?: .125f

internal fun previewZoomLabel(zoom: Float): String {
    val percent = kotlin.math.round(zoom * 1000) / 10
    return (if (percent == percent.toInt().toFloat()) percent.toInt().toString() else percent.toString()) + "%"
}
internal fun parsePreviewZoom(text: String): Float? = text.trim().removeSuffix("%").trim().toFloatOrNull()
    ?.takeIf { it.isFinite() && it in 12.5f..800f }?.div(100f)

internal fun previewOrigin(width: Float, height: Float,
    target: io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds, factor: Float) =
    androidx.compose.ui.geometry.Offset(
        (width - target.width.toFloat() * factor) / 2 - target.x.toFloat() * factor,
        (height - target.height.toFloat() * factor) / 2 - target.y.toFloat() * factor,
    )