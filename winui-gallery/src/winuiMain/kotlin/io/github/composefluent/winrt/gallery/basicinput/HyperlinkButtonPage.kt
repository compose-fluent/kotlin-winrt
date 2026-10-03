package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.GalleryNavigationHost
import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "HyperlinkButton", title = "HyperlinkButton", group = "BasicInput", order = 2)
internal class HyperlinkButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun DisableControl1_Click(sender: Any?, args: RoutedEventArgs) {
        Control1.isEnabled = DisableControl1.isChecked != true
    }

    private fun GoToHyperlinkButton_Click(sender: Any?, args: RoutedEventArgs) {
        GalleryNavigationHost.navigate("ToggleButton")
    }
}
