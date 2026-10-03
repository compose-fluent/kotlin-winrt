package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "StackPanel", title = "StackPanel", group = "Layout", order = 6)
internal class StackPanelPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun OrientationGroup_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) Control1.orientation = if (checkNotNull(checkNotNull(sender).asWinRT<RadioButtons>().selectedItem).asWinRT<RadioButton>().tag == "Horizontal") Orientation.Horizontal else Orientation.Vertical
    }
}
