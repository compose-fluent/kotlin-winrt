// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.multiplewindows

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.gallery.controls.ColorSelector
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.windowing.*
import windows.ui.Color

@GalleryPage(route = "TitleBar", title = "TitleBar", group = "MultipleWindows", order = 3)
internal class TitleBarPage : Page() {
    private fun CreateTitleBarWindowClick(sender: Any?, args: RoutedEventArgs) { TitleBarWindow().activate() }
    private fun TitleBar_LayoutUpdated(sender: Any?, args: Any?) { GalleryWindows.forElement(this)?.let { TitleBarHelper.ApplySystemThemeToCaptionButtons(it,actualTheme) } }
    private fun Hyperlink_Click(sender: microsoft.ui.xaml.documents.Hyperlink, args: microsoft.ui.xaml.documents.HyperlinkClickEventArgs) { GalleryNavigationHost.navigate("AppWindowTitleBar") }

}
