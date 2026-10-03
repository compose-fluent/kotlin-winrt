package io.github.composefluent.winrt.gallery.layout

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Border", title = "Border", group = "Layout", order = 0)
internal class BorderPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun ThicknessSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) Control1.borderThickness = inset(args.newValue) }
    private fun BGRadioButton_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        Control1.background = SolidColorBrush(when (checkNotNull(sender).asWinRT<RadioButton>().content?.toString()) {
            "Yellow" -> rgb(0xFFFF00u); "Green" -> rgb(0x008000u); "Blue" -> rgb(0x0000FFu); else -> rgb(0xFFFFFFu)
        })
    }
    private fun RadioButton_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        Control1.borderBrush = SolidColorBrush(when (checkNotNull(sender).asWinRT<RadioButton>().content?.toString()) {
            "Yellow" -> rgb(0xFFD700u); "Green" -> rgb(0x006400u); "Blue" -> rgb(0x00008Bu); else -> rgb(0xFFFFFFu)
        })
    }
}
