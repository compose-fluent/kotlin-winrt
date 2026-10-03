package io.github.composefluent.winrt.gallery.statusandinfo

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ProgressBar", title = "ProgressBar", group = "StatusAndInfo", order = 2)
internal class ProgressBarPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ProgressValue_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (!ready) return
        if (sender.value.isNaN()) sender.value = 0.0 else ProgressBar2.value = sender.value
    }
}
