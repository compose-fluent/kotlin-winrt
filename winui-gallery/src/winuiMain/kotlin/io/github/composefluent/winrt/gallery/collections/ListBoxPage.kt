package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "ListBox", title = "ListBox", group = "Collections", order = 4)
internal class ListBoxPage : Page() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent()
        ready = true
        UpdateFont()
    }
    private fun ColorChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val selected = ColorsList.selectedItem?.toString() ?: return
        colorOutput.fill = brush(when (selected) { "Blue" -> 0x0000FFu; "Green" -> 0x008000u; "Red" -> 0xFF0000u; else -> 0xFFFF00u })
    }
    private fun FontChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) UpdateFont() }
    private fun UpdateFont() { FontsList.selectedItem?.toString()?.let { fontOutput.fontFamily = FontFamily(it) } }
}
