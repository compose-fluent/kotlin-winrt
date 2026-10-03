package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "SystemBackdropElement", title = "SystemBackdropElement", group = "Styles", order = 8)
internal class SystemBackdropElementPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; updateBackdrop() }
    private fun BackdropTypeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) updateBackdrop() }
    private fun updateBackdrop() {
        val type = BackdropTypeComboBox.selectedItem?.asWinRT<ComboBoxItem>()?.tag?.toString() ?: "Acrylic"
        DynamicBackdropHost.systemBackdrop = when (type) {
            "Mica" -> MicaBackdrop().apply { kind = microsoft.ui.composition.systembackdrops.MicaKind.Base }
            "MicaAlt" -> MicaBackdrop().apply { kind = microsoft.ui.composition.systembackdrops.MicaKind.BaseAlt }
            else -> DesktopAcrylicBackdrop()
        }
        Example1.XamlSource = "SystemBackdropElement/SystemBackdropElement${type}_xaml.txt"
    }
    private fun CornerRadiusSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) DynamicBackdropHost.cornerRadius = CornerRadius(args.newValue, args.newValue, args.newValue, args.newValue) }
}
