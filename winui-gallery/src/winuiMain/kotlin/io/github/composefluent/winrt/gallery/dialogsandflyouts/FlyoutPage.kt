package io.github.composefluent.winrt.gallery.dialogsandflyouts

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Flyout
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "Flyout", title = "Flyout", group = "DialogsAndFlyouts", order = 1)
internal class FlyoutPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun DeleteConfirmation_Click(sender: Any?, args: RoutedEventArgs) {
        (Control1.flyout as? Flyout)?.hide()
    }
}
