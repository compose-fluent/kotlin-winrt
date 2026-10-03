package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.AppBarToggleButton
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.TextBlock

@GalleryPage(route = "AppBarToggleButton", title = "AppBarToggleButton", group = "MenusAndToolbars", order = 2)
internal class AppBarToggleButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun AppBarButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<AppBarToggleButton>()
        val output: TextBlock = when (button.name) {
            "Button1" -> Control1Output
            "Button2" -> Control2Output
            "Button3" -> Control3Output
            "Button4" -> Control4Output
            else -> return
        }
        val checked = when (button.isChecked) {
            true -> "True"
            false -> "False"
            null -> ""
        }
        output.text = "IsChecked = $checked"
    }
}