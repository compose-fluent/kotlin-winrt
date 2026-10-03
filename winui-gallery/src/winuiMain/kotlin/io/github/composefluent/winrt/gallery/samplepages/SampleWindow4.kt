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

internal class SampleWindow4 : Window() {
    private val tasks by lazy { GalleryPageTasks(checkNotNull(content).asWinRT<FrameworkElement>()) }
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this) }
    fun Configure(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) { checkNotNull(appWindow).apply {
        resize(SizeInt32(800,500)); setIcon("Assets/Tiles/GalleryIcon.ico"); titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode
        setPresenter(OverlappedPresenter.create().apply { preferredMinimumWidth = minWidth; preferredMinimumHeight = minHeight; preferredMaximumWidth = maxWidth; preferredMaximumHeight = maxHeight; isMaximizable = false })
    } }
    private fun presenter(): OverlappedPresenter = checkNotNull(checkNotNull(appWindow).presenter).asWinRT<OverlappedPresenter>()
    private fun MinimizeBtn_Click(sender: Any?, args: RoutedEventArgs) { presenter().minimize() }
    private fun RestoreBtn_Click(sender: Any?, args: RoutedEventArgs) { tasks.launch { presenter().minimize(); delay(3000); presenter().restore() } }
    private fun CloseBtn_Click(sender: Any?, args: RoutedEventArgs) { close() }

}
