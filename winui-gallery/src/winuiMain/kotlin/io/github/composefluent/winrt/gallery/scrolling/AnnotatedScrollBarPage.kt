package io.github.composefluent.winrt.gallery.scrolling

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "AnnotatedScrollBar", title = "AnnotatedScrollBar", group = "Scrolling", order = 0)
internal class AnnotatedScrollBarPage : Page() {
    private var ready = false
    val ColorCollection: MutableList<SolidColorBrush> = WinRTObservableList(buildList {
        listOf(rgb(0xF0FFFFu) to 32, rgb(0xDC143Cu) to 50, rgb(0x00FFFFu) to 8, rgb(0xFF00FFu) to 70, rgb(0xFFD700u) to 90).forEach { (color, count) -> val brush = SolidColorBrush(color); repeat(count) { add(brush) } }
    })
    override fun initializeComponent() { super.initializeComponent(); ready = true; dataContext = this; loaded.add { _, _ -> checkNotNull(scrollView.scrollPresenter).verticalScrollController = annotatedScrollBar.scrollController; PopulateLabelCollection() } }
    private fun GetItemsPerRow(): Int = maxOf((itemsRepeater.actualWidth / 120.0).toInt(), 1)
    private fun GetOffsetOfItem(index: Int): Double = 90.0 * (index / GetItemsPerRow())
    private fun GetItemColor(index: Int): String = when { index < 32 -> "Azure"; index < 82 -> "Crimson"; index < 90 -> "Cyan"; index < 160 -> "Fuchsia"; else -> "Gold" }
    private fun GetOffsetLabel(offset: Double): String = GetItemColor(listOf(31, 81, 89, 159).firstOrNull { offset <= GetOffsetOfItem(it) } ?: 160)
    private fun PopulateLabelCollection() { if (!ready) return; annotatedScrollBar.labels.clear(); listOf("Azure" to 0, "Crimson" to 32, "Cyan" to 82, "Fuchsia" to 90, "Gold" to 160).forEach { (name, index) -> annotatedScrollBar.labels.add(AnnotatedScrollBarLabel(name, GetOffsetOfItem(index))) } }
    private fun AnnotatedScrollBar_DetailLabelRequested(sender: AnnotatedScrollBar, args: AnnotatedScrollBarDetailLabelRequestedEventArgs) { args.content = GetOffsetLabel(args.scrollOffset) }
    private fun ItemsRepeater_SizeChanged(sender: Any?, args: SizeChangedEventArgs) { PopulateLabelCollection() }
    private fun AnnotatedScrollBarMaxHeightSlider_ValueChanged(sender: Any?, args: RangeBaseValueChangedEventArgs) { if (ready) annotatedScrollBar.maxHeight = args.newValue }
}
