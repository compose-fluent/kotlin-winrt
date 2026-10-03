package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "ToggleSwitch", title = "ToggleSwitch", group = "BasicInput", order = 13)
internal class ToggleSwitchPage : Page() {
    private var initialized = false

    override fun initializeComponent() {
        super.initializeComponent()
        initialized = true
        progress.isActive = workToggle.isOn
    }

    private fun onWorkToggled(sender: Any?, args: RoutedEventArgs) {
        // IsOn can raise Toggled while LoadComponent is still connecting later siblings.
        if (initialized) progress.isActive = workToggle.isOn
    }
}
