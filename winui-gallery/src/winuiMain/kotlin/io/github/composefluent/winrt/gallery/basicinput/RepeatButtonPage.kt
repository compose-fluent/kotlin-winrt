package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "RepeatButton", title = "RepeatButton", group = "BasicInput", order = 3)
internal class RepeatButtonPage : Page() {
    private var clicks = 0

    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun onRepeatClick(sender: Any?, args: RoutedEventArgs) {
        output.text = "Number of clicks: ${++clicks}"
    }

    private fun onDisableClick(sender: Any?, args: RoutedEventArgs) {
        repeatButton.isEnabled = disableRepeat.isChecked != true
    }
}
