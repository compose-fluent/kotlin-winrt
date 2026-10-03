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

internal class TitleBarWindow : Window() {
    private var ready = false
    override fun initializeComponent() {
        super.initializeComponent(); GalleryWindows.track(this); ready = true; extendsContentIntoTitleBar = true
        appWindow?.titleBar?.preferredHeightOption = TitleBarHeightOption.Tall; setTitleBar(titleBar); appWindow?.setIcon("Assets/Tiles/GalleryIcon.ico")
        navView.selectedItem = navView.menuItems.firstOrNull()
    }
    private fun TitleBar_PaneToggleRequested(sender: TitleBar, args: Any?) { navView.isPaneOpen = !navView.isPaneOpen }
    private fun TitleBar_BackRequested(sender: TitleBar, args: Any?) { if (navFrame.canGoBack) navFrame.goBack() }
    private fun navView_SelectionChanged(sender: NavigationView, args: NavigationViewSelectionChangedEventArgs) {
        if (!ready) return
        val tag = args.selectedItem?.asWinRT<NavigationViewItem>()?.tag?.toString() ?: return
        val page = when (tag) { "SamplePage1" -> SamplePage1::class; "SamplePage2" -> SamplePage2::class; "SamplePage3" -> SamplePage3::class; else -> SamplePage4::class }
        sender.header = "Sample Page ${tag.last()}"; navFrame.navigate(page)
    }

}
