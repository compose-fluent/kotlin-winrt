package io.github.composefluent.winrt.gallery.dialogsandflyouts

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "TeachingTip", title = "TeachingTip", group = "DialogsAndFlyouts", order = 3)
internal class TeachingTipPage : Page() {
    private fun TestButton1Click(sender: Any?, args: RoutedEventArgs) {
        TestButton1TeachingTip.isOpen = true
    }

    private fun TestButton2Click(sender: Any?, args: RoutedEventArgs) {
        TestButton2TeachingTip.isOpen = true
    }

    private fun TestButton3Click(sender: Any?, args: RoutedEventArgs) {
        TestButton3TeachingTip.isOpen = true
    }
}