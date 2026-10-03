package io.github.composefluent.winrt.gallery.accessibility

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.brush
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "AccessibilityKeyboard", title = "Keyboard Navigation", group = "AccessibilityItem", order = 1)
internal class AccessibilityKeyboardPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun MakeRedButton_Click(sender: Any?, args: RoutedEventArgs) {
        ColorRectangle.fill = brush(0xFF0000u)
    }

    private fun MakeBlueButton_Click(sender: Any?, args: RoutedEventArgs) {
        ColorRectangle.fill = brush(0x0000FFu)
    }

    private fun MakeChartreuseButton_Click(sender: Any?, args: RoutedEventArgs) {
        ColorRectangle.fill = brush(0x7FFF00u)
    }
}
