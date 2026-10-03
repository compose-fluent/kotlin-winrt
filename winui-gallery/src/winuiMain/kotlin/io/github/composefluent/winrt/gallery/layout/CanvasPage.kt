package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Canvas", title = "Canvas", group = "Layout", order = 1)
internal class CanvasPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; updateAutomationName() }
    private fun updateAutomationName() { microsoft.ui.xaml.automation.AutomationProperties.setName(ZSlider,
        "Canvas.ZIndex value ${ZSlider.value.toInt()} of range ${ZSlider.minimum.toInt()} to ${ZSlider.maximum.toInt()}") }
    private fun ZSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) updateAutomationName() }
}
