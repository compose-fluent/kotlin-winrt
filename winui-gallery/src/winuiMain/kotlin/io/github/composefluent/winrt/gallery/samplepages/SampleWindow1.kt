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

internal class SampleWindow1 : Window() {
    private val tasks by lazy { GalleryPageTasks(checkNotNull(content).asWinRT<FrameworkElement>()) }
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this) }
    fun Configure(windowTitle: String, width: Int, height: Int, x: Int, y: Int) { checkNotNull(appWindow).apply {
        title = windowTitle; resize(SizeInt32(width,height)); move(PointInt32(x,y)); setTaskbarIcon("Assets/Tiles/GalleryIcon.ico"); setTitleBarIcon("Assets/Tiles/GalleryIcon.ico"); titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode
    } }
    private fun Show_Click(sender: Any?, args: RoutedEventArgs) { tasks.launch { checkNotNull(appWindow).hide(); delay(3000); checkNotNull(appWindow).show() } }
    private fun Hide_Click(sender: Any?, args: RoutedEventArgs) { appWindow?.hide() }
    private fun Close_Click(sender: Any?, args: RoutedEventArgs) { close() }

}
