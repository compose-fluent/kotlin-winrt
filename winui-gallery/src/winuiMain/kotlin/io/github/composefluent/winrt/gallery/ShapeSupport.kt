// Ported from WinUI Gallery Samples/{Shape,Line,RadialGradientBrush} (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.shapes.*
import windows.foundation.Point
import windows.foundation.Rect

internal fun geometryRange(title: String, initial: Double, minimum: Double, maximum: Double, changed: (Double) -> Unit) =
    range(title, initial, minimum, maximum, changed).apply { width = 220.0; smallChange = 1.0; stepFrequency = 0.5; isFocusEngagementEnabled = false }

internal fun pointLabels(canvas: Canvas, values: List<Triple<String, Double, Double>>): ToggleSwitch {
    val labels = values.map { (text, x, y) -> label(text).apply {
        Canvas.setLeft(this, x); Canvas.setTop(this, y); Canvas.setZIndex(this, 1)
        visibility = Visibility.Collapsed; canvas.children!!.add(this)
    } }
    return ToggleSwitch().apply {
        header = "Show points"
        toggled.add { _, _ -> labels.forEach { it.visibility = if (isOn) Visibility.Visible else Visibility.Collapsed } }
    }
}
