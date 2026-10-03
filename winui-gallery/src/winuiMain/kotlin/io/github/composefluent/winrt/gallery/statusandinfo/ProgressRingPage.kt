package io.github.composefluent.winrt.gallery.statusandinfo

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ProgressRing", title = "ProgressRing", group = "StatusAndInfo", order = 3)
internal class ProgressRingPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; applyBackground(BackgroundComboBox1); applyBackground(BackgroundComboBox2) }
    private fun ProgressValue_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (!ready) return
        if (sender.value.isNaN()) sender.value = 0.0 else ProgressRing2.value = sender.value
    }
    private fun applyBackground(box: ComboBox) {
        val first = box == BackgroundComboBox1
        val gray = box.selectedItem == "LightGray"
        (if (first) ProgressRing1 else ProgressRing2).background = SolidColorBrush(if (gray) rgb(0xD3D3D3u) else windows.ui.Color(0u, 0u, 0u, 0u))
        (if (first) RevealBackgroundProperty1 else RevealBackgroundProperty2).IsEnabled = gray
    }
    private fun Background_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) applyBackground(checkNotNull(sender).asWinRT<ComboBox>()) }
}
