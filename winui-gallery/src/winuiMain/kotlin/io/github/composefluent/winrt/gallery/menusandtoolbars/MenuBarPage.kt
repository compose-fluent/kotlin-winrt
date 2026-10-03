package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.MenuFlyoutItem
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "MenuBar", title = "MenuBar", group = "MenusAndToolbars", order = 5)
internal class MenuBarPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun OnElementClicked(sender: Any?, args: RoutedEventArgs) {
        val item = checkNotNull(sender).asWinRT<MenuFlyoutItem>()
        val output = when (item.name.firstOrNull()) {
            'o' -> SelectedOptionText
            't' -> SelectedOptionText1
            'z' -> SelectedOptionText2
            else -> return
        }
        output.text = "You clicked: " + item.text
    }
}
