package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Viewbox", title = "Viewbox", group = "Layout", order = 8)
internal class ViewboxPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun Stretch_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        Control1.stretch = when (checkNotNull(checkNotNull(sender).asWinRT<RadioButtons>().selectedItem).asWinRT<RadioButton>().tag?.toString()) {
            "None" -> Stretch.None; "Fill" -> Stretch.Fill; "UniformToFill" -> Stretch.UniformToFill; else -> Stretch.Uniform
        }
    }
    private fun StretchDirection_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        Control1.stretchDirection = when (checkNotNull(checkNotNull(sender).asWinRT<RadioButtons>().selectedItem).asWinRT<RadioButton>().tag?.toString()) {
            "UpOnly" -> StretchDirection.UpOnly; "DownOnly" -> StretchDirection.DownOnly; else -> StretchDirection.Both
        }
    }
}
