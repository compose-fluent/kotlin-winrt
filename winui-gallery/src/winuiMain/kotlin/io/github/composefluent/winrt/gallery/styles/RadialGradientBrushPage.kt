package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "RadialGradientBrush", title = "RadialGradientBrush", group = "Styles", order = 6)
internal class RadialGradientBrushPage : Page() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        loaded.add { _, _ -> initializeSliders() }
        MappingModeComboBox.selectionChanged.add { _, _ ->
            RadialGradientBrushExample.mappingMode = if (MappingModeComboBox.selectedValue == "Absolute") BrushMappingMode.Absolute else BrushMappingMode.RelativeToBoundingBox
            initializeSliders()
        }
        SpreadMethodComboBox.selectionChanged.add { _, _ ->
            RadialGradientBrushExample.spreadMethod = when (SpreadMethodComboBox.selectedValue?.toString()) { "Reflect" -> GradientSpreadMethod.Reflect; "Repeat" -> GradientSpreadMethod.Repeat; else -> GradientSpreadMethod.Pad }
        }
    }
    private fun initializeSliders() {
        val absolute = RadialGradientBrushExample.mappingMode == BrushMappingMode.Absolute
        val width = Rect.actualWidth.coerceAtLeast(1.0); val height = Rect.actualHeight.coerceAtLeast(1.0)
        listOf(CenterXSlider, RadiusXSlider, OriginXSlider).forEach { slider ->
            slider.maximum = if (absolute) width else 1.0; slider.value = slider.maximum / 2.0
            slider.stepFrequency = if (absolute) width / 50.0 else 0.02; slider.smallChange = if (absolute) 10.0 else 0.05
        }
        listOf(CenterYSlider, RadiusYSlider, OriginYSlider).forEach { slider ->
            slider.maximum = if (absolute) width else 1.0; slider.value = slider.maximum / 2.0
            slider.stepFrequency = if (absolute) height / 50.0 else 0.02; slider.smallChange = if (absolute) 10.0 else 0.05
        }
    }
    private fun OnSliderValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) {
        if (!ready) return
        RadialGradientBrushExample.center = windows.foundation.Point(CenterXSlider.value.toFloat(), CenterYSlider.value.toFloat())
        RadialGradientBrushExample.radiusX = RadiusXSlider.value; RadialGradientBrushExample.radiusY = RadiusYSlider.value
        RadialGradientBrushExample.gradientOrigin = windows.foundation.Point(OriginXSlider.value.toFloat(), OriginYSlider.value.toFloat())
    }
}
