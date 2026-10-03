package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ColorPicker", title = "ColorPicker", group = "BasicInput", order = 8)
internal class ColorPickerPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; applySpectrumShape() }
    private fun applySpectrumShape() { colorPicker.colorSpectrumShape = if (ColorSpectrumShapeRadioButtons.selectedItem == "Box") ColorSpectrumShape.Box else ColorSpectrumShape.Ring }
    private fun ColorSpectrumShapeRadioButtons_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) applySpectrumShape() }
}
