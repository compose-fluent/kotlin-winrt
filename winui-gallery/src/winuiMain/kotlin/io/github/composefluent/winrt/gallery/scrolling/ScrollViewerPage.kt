package io.github.composefluent.winrt.gallery.scrolling

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "ScrollViewer", title = "ScrollViewer", group = "Scrolling", order = 3)
internal class ScrollViewerPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; ScrollViewerControl.changeView(null, null, 4f) }
    private fun ZoomModeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val index = checkNotNull(sender).asWinRT<ComboBox>().selectedIndex
        ScrollViewerControl.zoomMode = ZoomMode.fromAbi(index)
        ZoomSlider.isEnabled = index == 1
        if (index != 1) ScrollViewerControl.changeView(null, null, 2f)
    }
    private fun ZoomSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) ScrollViewerControl.changeView(null, null, args.newValue.toFloat()) }
    private fun hsmCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) ScrollViewerControl.horizontalScrollMode = ScrollMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun vsmCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) ScrollViewerControl.verticalScrollMode = ScrollMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun hsbvCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) ScrollViewerControl.horizontalScrollBarVisibility = ScrollBarVisibility.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun vsbvCombo_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) ScrollViewerControl.verticalScrollBarVisibility = ScrollBarVisibility.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun ScrollViewerControl_ManipulationCompleted(sender: Any?, args: microsoft.ui.xaml.input.ManipulationCompletedRoutedEventArgs) { ZoomSlider.value = ScrollViewerControl.zoomFactor.toDouble() }
    private fun ScrollViewerControl_ViewChanged(sender: Any?, args: ScrollViewerViewChangedEventArgs) { if (ready && !args.isIntermediate) ZoomSlider.value = ScrollViewerControl.zoomFactor.toDouble() }
}
