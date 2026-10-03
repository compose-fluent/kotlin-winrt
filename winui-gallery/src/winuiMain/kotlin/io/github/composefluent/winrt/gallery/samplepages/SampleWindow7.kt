// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*
import microsoft.ui.windowing.*
import windows.graphics.*
import windows.ui.Color
import kotlinx.coroutines.delay

internal class SampleWindow7 : Window() {
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this) }
    fun Configure(size: String) { checkNotNull(appWindow).apply {
        setIcon("Assets/Tiles/GalleryIcon.ico"); titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode
        setPresenter(CompactOverlayPresenter.create().apply { initialSize = when (size) { "Small" -> CompactOverlaySize.Small; "Large" -> CompactOverlaySize.Large; else -> CompactOverlaySize.Medium } })
    } }

}
