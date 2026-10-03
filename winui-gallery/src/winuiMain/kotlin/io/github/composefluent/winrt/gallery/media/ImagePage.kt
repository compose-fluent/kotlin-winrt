package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Image", title = "Image", group = "Media", order = 2)
internal class ImagePage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ImageStretch_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        StretchImage.stretch = when (checkNotNull(sender).asWinRT<RadioButton>().content?.toString()) {
            "None" -> Stretch.None; "Fill" -> Stretch.Fill; "UniformToFill" -> Stretch.UniformToFill; else -> Stretch.Uniform
        }
    }
    private fun ClickToPlaySource_ImageOpened(sender: Any?, args: RoutedEventArgs) {
        if (ClickToPlaySource.isAnimatedBitmap) PlaybackButtons.visibility = Visibility.Visible
    }
}
