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

internal class AppWindowTitleBarWindow : Window() {
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this); checkNotNull(appWindow).apply { setPresenter(OverlappedPresenter.create().apply { isAlwaysOnTop = true; isResizable = false }); resize(SizeInt32(600,400)) } }
    fun Configure(colors: List<Color>) { checkNotNull(checkNotNull(appWindow).titleBar).apply {
        backgroundColor = colors[0]; foregroundColor = colors[1]; buttonBackgroundColor = colors[2]; buttonForegroundColor = colors[3]
        buttonHoverBackgroundColor = colors[4]; buttonHoverForegroundColor = colors[5]; buttonInactiveBackgroundColor = colors[6]; buttonInactiveForegroundColor = colors[7]
        inactiveBackgroundColor = colors[8]; inactiveForegroundColor = colors[9]; buttonPressedBackgroundColor = colors[10]; buttonPressedForegroundColor = colors[11]
    } }

}
