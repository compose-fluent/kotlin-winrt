package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "VariableSizedWrapGrid", title = "VariableSizedWrapGrid", group = "Layout", order = 7)
internal class VariableSizedWrapGridPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun OrientationGroup_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) Control1.orientation = if (checkNotNull(checkNotNull(sender).asWinRT<RadioButtons>().selectedItem).asWinRT<RadioButton>().tag == "Horizontal") Orientation.Horizontal else Orientation.Vertical
    }
}
