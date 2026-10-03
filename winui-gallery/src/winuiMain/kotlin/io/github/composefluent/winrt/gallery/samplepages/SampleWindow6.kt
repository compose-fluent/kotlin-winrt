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

internal class SampleWindow6 : Window() {
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this); checkNotNull(appWindow).apply { setIcon("Assets/Tiles/GalleryIcon.ico"); titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode; setPresenter(AppWindowPresenterKind.FullScreen) } }
    private fun Close_Click(sender: Any?, args: RoutedEventArgs) { close() }

}
