package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.brush
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.RadioButton

@GalleryPage(route = "RadioButton", title = "RadioButton", group = "BasicInput", order = 10)
internal class RadioButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun RadioButton_Checked(sender: Any?, args: RoutedEventArgs) {
        val selected = checkNotNull(sender).asWinRT<RadioButton>().content
        Control1Output.text = "You selected $selected"
    }

    private fun BackgroundColor_SelectionChanged(sender: Any?, args: microsoft.ui.xaml.controls.SelectionChangedEventArgs) {
        val colors = listOf(0x008000u, 0xFFFF00u, 0xFFFFFFu)
        val index = BackgroundRadioButtons.selectedIndex
        if (index in colors.indices) ControlOutput.background = brush(colors[index])
    }

    private fun BorderBrush_SelectionChanged(sender: Any?, args: microsoft.ui.xaml.controls.SelectionChangedEventArgs) {
        val colors = listOf(0x006400u, 0xFFD700u, 0xFFFFFFu)
        val index = BorderRadioButtons.selectedIndex
        if (index in colors.indices) ControlOutput.borderBrush = brush(colors[index])
    }
}
