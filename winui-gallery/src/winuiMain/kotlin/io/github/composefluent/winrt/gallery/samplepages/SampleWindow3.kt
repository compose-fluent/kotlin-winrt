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

internal class SampleWindow3 : Window() {
    private val tasks by lazy { GalleryPageTasks(checkNotNull(content).asWinRT<FrameworkElement>()) }
    override fun initializeComponent() { super.initializeComponent(); GalleryWindows.track(this); sizeChanged.add(::SampleWindow3_SizeChanged) }
    fun Configure(alwaysOnTop: Boolean, maximizable: Boolean, minimizable: Boolean, resizable: Boolean, border: Boolean, titleBar: Boolean) {
        checkNotNull(appWindow).apply {
            setPresenter(OverlappedPresenter.create().apply { isAlwaysOnTop = alwaysOnTop; isMaximizable = maximizable; isMinimizable = minimizable; isResizable = resizable; setBorderAndTitleBar(border,titleBar) })
            setIcon("Assets/Tiles/GalleryIcon.ico"); this.titleBar?.preferredTheme = TitleBarTheme.UseDefaultAppMode
        }
    }
    private fun presenter(): OverlappedPresenter = checkNotNull(checkNotNull(appWindow).presenter).asWinRT<OverlappedPresenter>()
    private fun MaximizeRestoreBtn_Click(sender: Any?, args: RoutedEventArgs) { presenter().let { if (it.state == OverlappedPresenterState.Maximized) it.restore() else it.maximize() } }
    private fun SampleWindow3_SizeChanged(sender: Any?, args: WindowSizeChangedEventArgs) { MaximizeRestoreBtn.content = if (presenter().state == OverlappedPresenterState.Maximized) "Restore" else "Maximize" }
    private fun MinimizeBtn_Click(sender: Any?, args: RoutedEventArgs) { presenter().minimize() }
    private fun RestoreBtn_Click(sender: Any?, args: RoutedEventArgs) { tasks.launch { presenter().minimize(); delay(3000); presenter().restore() } }
    private fun CloseBtn_Click(sender: Any?, args: RoutedEventArgs) { close() }

}
