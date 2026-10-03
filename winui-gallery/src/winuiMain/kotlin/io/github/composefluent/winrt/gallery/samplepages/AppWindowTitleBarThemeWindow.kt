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

internal class AppWindowTitleBarThemeWindow : Window() {
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this); checkNotNull(appWindow).apply { setPresenter(OverlappedPresenter.create().apply { isAlwaysOnTop = true; isResizable = false }); resize(SizeInt32(600,400)) } }
    fun Configure(theme: TitleBarTheme) { appWindow?.titleBar?.preferredTheme = theme }

}
