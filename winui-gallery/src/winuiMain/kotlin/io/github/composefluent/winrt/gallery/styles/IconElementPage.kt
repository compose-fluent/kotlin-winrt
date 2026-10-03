package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "IconElement", title = "IconElement", group = "Styles", order = 3)
internal class IconElementPage : Page() {
    private fun MonochromeButton_CheckedChanged(sender: Any?, args: RoutedEventArgs) {
        val icon = SlicesIcon
        icon.showAsMonochrome = MonochromeButton.isChecked == true
        icon.uriSource = windows.foundation.Uri("ms-appx:///Assets/SampleMedia/Slices.png")
    }
}
