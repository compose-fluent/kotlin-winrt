package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "MenuFlyout", title = "MenuFlyout", group = "MenusAndToolbars", order = 6)
internal class MenuFlyoutPage : Page() {
    private fun MenuFlyoutItem_Click(sender: Any?, args: RoutedEventArgs) {
        Control1Output.text = "Sort by: ${checkNotNull(sender).asWinRT<MenuFlyoutItem>().tag}"
    }
    private fun SplitMenuFlyoutItem_Click(sender: Any?, args: RoutedEventArgs) {
        Control3bOutput.text = "Clicked: ${checkNotNull(sender).asWinRT<MenuFlyoutItem>().text}"
    }
    private fun Example5_Loaded(sender: Any?, args: RoutedEventArgs) {}
}
