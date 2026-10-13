package io.github.composefluent.winrt.gallery.design

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "Color", title = "Color", group = "DesignItem", order = 0, glyph = "\uE790")
internal class ColorPage : Page() {
    private var previousSelectedIndex = 0
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun PageSelector_Loaded(sender: Any?, args: RoutedEventArgs?) { PageSelector.selectedItem = checkNotNull(PageSelector.items)[0] }
// #if WINUI3
    private fun PageSelector_SelectionChanged(sender: SelectorBar?, args: SelectorBarSelectionChangedEventArgs?) { requireNotNull(sender);
// #else
    private fun PageSelector_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs?) { requireNotNull(sender);
// #endif
        if (!ready) return
        val index = PageSelector.items!!.indexOf(PageSelector.selectedItem)
        val types = listOf(io.github.composefluent.winrt.gallery.controls.colorsections.TextSection::class,
            io.github.composefluent.winrt.gallery.controls.colorsections.FillSection::class, io.github.composefluent.winrt.gallery.controls.colorsections.StrokeSection::class,
            io.github.composefluent.winrt.gallery.controls.colorsections.BackgroundSection::class, io.github.composefluent.winrt.gallery.controls.colorsections.SignalSection::class,
            io.github.composefluent.winrt.gallery.controls.colorsections.HighContrastSection::class)
        NavigationFrame.navigate(types[index.coerceIn(0, 5)], null, SlideNavigationTransitionInfo().apply { effect = if (index > previousSelectedIndex) SlideNavigationTransitionEffect.FromRight else SlideNavigationTransitionEffect.FromLeft })
        previousSelectedIndex = index
    }
}
