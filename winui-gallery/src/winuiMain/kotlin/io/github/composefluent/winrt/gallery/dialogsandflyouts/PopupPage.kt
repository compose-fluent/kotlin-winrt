package io.github.composefluent.winrt.gallery.dialogsandflyouts

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Popup", title = "Popup", group = "DialogsAndFlyouts", order = 2)
internal class PopupPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ShowPopupOffsetClicked(sender: Any?, args: RoutedEventArgs) { StandardPopup.isOpen = true; IsLightDismissEnabledToggleSwitch.isEnabled = false }
    private fun ClosePopupClicked(sender: Any?, args: RoutedEventArgs) { StandardPopup.isOpen = false; IsLightDismissEnabledToggleSwitch.isEnabled = true }
    private fun PopupClosed(sender: Any?, args: Any?) { if (ready) IsLightDismissEnabledToggleSwitch.isEnabled = true }
}
