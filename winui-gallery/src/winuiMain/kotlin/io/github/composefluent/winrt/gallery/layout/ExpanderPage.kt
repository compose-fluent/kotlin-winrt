package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Expander", title = "Expander", group = "Layout", order = 2)
internal class ExpanderPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ExpandDirectionComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val up = checkNotNull(sender).asWinRT<ComboBox>().selectedItem == "Up"
        Expander1.expandDirection = if (up) ExpandDirection.Up else ExpandDirection.Down
        Expander1.verticalAlignment = if (up) VerticalAlignment.Bottom else VerticalAlignment.Top
    }
}
