package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Slider", title = "Slider", group = "BasicInput", order = 12)
internal class SliderPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; applySnapsTo() }
    private fun applySnapsTo() { Slider3.snapsTo = if (SnapsToRadioButtons.selectedItem == "StepValues") SliderSnapsTo.StepValues else SliderSnapsTo.Ticks }
    private fun SnapsToRadioButtons_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) applySnapsTo() }
}
