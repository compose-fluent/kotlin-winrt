package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "Button", title = "Button", group = "BasicInput", order = 0)
internal class ButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun onStandardClick(sender: Any?, args: RoutedEventArgs) {
        textOutput.text = "You clicked: Standard XAML button"
    }

    private fun onImageClick(sender: Any?, args: RoutedEventArgs) {
        imageOutput.text = "You clicked: Image button"
    }

    private fun onDisableClick(sender: Any?, args: RoutedEventArgs) {
        standardButton.isEnabled = disableButton.isChecked != true
    }
}
