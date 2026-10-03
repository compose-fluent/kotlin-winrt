package io.github.composefluent.winrt.gallery.accessibility

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*
import kotlin.math.pow

@GalleryPage(route = "AccessibilityColorContrast", title = "Color Contrast", group = "AccessibilityItem", order = 0)
internal class AccessibilityColorContrastPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; RecalculateContrastRatio() }
    private fun RecalculateContrastRatio() {
        if (!ready) return
        val ratio = CalculateContrastRatio(TextColorPicker.Color, BackgroundColorPicker.Color)
        ContrastRatioPresenter.text = "${kotlin.math.round(ratio * 100.0) / 100.0}:1"
        SetCheckState(NormalTextCheckEllipse, NormalTextCheckIcon, NormalTextCheckResult, ratio >= 4.5)
        SetCheckState(LargeTextCheckEllipse, LargeTextCheckIcon, LargeTextCheckResult, ratio >= 3.0)
        SetCheckState(ComponentsCheckEllipse, ComponentsCheckIcon, ComponentsCheckResult, ratio >= 3.0)
    }
    private fun SetCheckState(background: microsoft.ui.xaml.shapes.Ellipse, icon: FontIcon, text: TextBlock, passed: Boolean) {
        background.fill = brush(if (passed) 0x006400u else 0x8B0000u); icon.glyph = if (passed) "\uE73E" else "\uE711"; text.text = if (passed) "Pass" else "Fail"
    }
    private fun BackgroundColorPicker_ColorChanged(sender: Any?, color: windows.ui.Color) { RecalculateContrastRatio() }
    private fun TextColorPicker_ColorChanged(sender: Any?, color: windows.ui.Color) { RecalculateContrastRatio() }
    companion object {
        fun GetSolidColorBrush(hex: String): SolidColorBrush = brush(hex.removePrefix("#").toUInt(16))
        fun CalculateContrastRatio(first: windows.ui.Color, second: windows.ui.Color): Double {
            val a = GetRelativeLuminance(first); val b = GetRelativeLuminance(second)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }
        fun GetRelativeLuminance(color: windows.ui.Color): Double {
            fun linear(value: UByte): Double { val v = value.toInt() / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
            return 0.2126 * linear(color.r) + 0.7152 * linear(color.g) + 0.0722 * linear(color.b)
        }
    }
}
