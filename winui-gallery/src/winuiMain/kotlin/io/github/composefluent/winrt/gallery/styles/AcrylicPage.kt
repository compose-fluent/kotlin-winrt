package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "Acrylic", title = "AcrylicBrush", group = "Styles", order = 0)
internal class AcrylicPage : Page() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        loaded.add { _, _ -> ColorSelectorInApp.selectedIndex = 0; FallbackColorSelectorInApp.selectedIndex = 0; OpacitySliderInApp.value = 0.8; OpacitySliderLumin.value = 0.8; LuminositySlider.value = 0.8 }
    }
    private fun Slider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) {
        if (!ready) return
        val shape = if (sender == OpacitySliderLumin) CustomAcrylicShapeLumin else CustomAcrylicShapeInApp
        checkNotNull(shape.fill).asWinRT<AcrylicBrush>().tintOpacity = args.newValue
    }
    private fun ColorSelector_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) args.addedItems.firstOrNull()?.asWinRT<SolidColorBrush>()?.let { checkNotNull(CustomAcrylicShapeInApp.fill).asWinRT<AcrylicBrush>().tintColor = it.color }
    }
    private fun FallbackColorSelector_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (ready) args.addedItems.firstOrNull()?.asWinRT<SolidColorBrush>()?.let { checkNotNull(CustomAcrylicShapeInApp.fill).asWinRT<AcrylicBrush>().fallbackColor = it.color }
    }
    private fun LuminositySlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) checkNotNull(CustomAcrylicShapeLumin.fill).asWinRT<AcrylicBrush>().tintLuminosityOpacity = args.newValue }
    private fun SystemBackdropLink_Click(sender: microsoft.ui.xaml.documents.Hyperlink, args: microsoft.ui.xaml.documents.HyperlinkClickEventArgs) { GalleryNavigationHost.navigate("SystemBackdrops") }
}
