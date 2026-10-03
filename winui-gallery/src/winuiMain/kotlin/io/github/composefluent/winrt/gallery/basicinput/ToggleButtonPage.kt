package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "ToggleButton", title = "ToggleButton", group = "BasicInput", order = 4)
internal class ToggleButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun onToggleClick(sender: Any?, args: RoutedEventArgs) {
        output.text = if (toggleButton.isChecked == true) "On" else "Off"
    }

    private fun onDisableClick(sender: Any?, args: RoutedEventArgs) {
        toggleButton.isEnabled = disableToggle.isChecked != true
    }
}
