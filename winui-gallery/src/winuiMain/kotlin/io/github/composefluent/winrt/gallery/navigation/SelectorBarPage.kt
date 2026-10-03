package io.github.composefluent.winrt.gallery.navigation

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "SelectorBar", title = "SelectorBar", group = "Navigation", order = 3)
internal class SelectorBarPage : Page() {
    private var previousSelectedIndex = 0
    private var ready = false
    val PinkColorCollection: MutableList<SolidColorBrush> = WinRTObservableList(List(5) { SolidColorBrush(rgb(0xFFC0CBu)) })
    val PlumColorCollection: MutableList<SolidColorBrush> = WinRTObservableList(List(7) { SolidColorBrush(rgb(0xDDA0DDu)) })
    val PowderBlueColorCollection: MutableList<SolidColorBrush> = WinRTObservableList(List(4) { SolidColorBrush(rgb(0xB0E0E6u)) })
    override fun initializeComponent() { super.initializeComponent(); ready = true; ContentFrame.navigate(SamplePage1::class); ItemsView3.itemsSource = PinkColorCollection }
    private fun SelectorBar2_SelectionChanged(sender: SelectorBar, args: SelectorBarSelectionChangedEventArgs) {
        if (!ready) return
        val index = sender.items.indexOf(sender.selectedItem)
        val page = listOf(SamplePage1::class, SamplePage2::class, SamplePage3::class, SamplePage4::class, SamplePage5::class)[index.coerceIn(0, 4)]
        ContentFrame.navigate(page, null, SlideNavigationTransitionInfo().apply { effect = if (index > previousSelectedIndex) SlideNavigationTransitionEffect.FromRight else SlideNavigationTransitionEffect.FromLeft })
        previousSelectedIndex = index
    }
    private fun SelectorBar3_SelectionChanged(sender: SelectorBar, args: SelectorBarSelectionChangedEventArgs) {
        if (ready) ItemsView3.itemsSource = when (sender.selectedItem) { SelectorBarItemPink -> PinkColorCollection; SelectorBarItemPlum -> PlumColorCollection; else -> PowderBlueColorCollection }
    }
}
