// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.gallery.controls.ColorSelector
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.windowing.*
import windows.ui.Color

@GalleryPage(route = "SystemBackdrops", title = "System Backdrops (Mica/Acrylic)", group = "Styles", order = 7)
internal class SystemBackdropsPage : Page() {
    private fun createBuiltInWindow_Click(sender: Any?, args: RoutedEventArgs) { SampleBuiltInSystemBackdropsWindow().apply { SetBackdrop(GalleryBackdropType.Mica); activate() } }
    private fun createCustomMicaWindow_Click(sender: Any?, args: RoutedEventArgs) { SampleSystemBackdropsWindow().apply { AllowedBackdrops = listOf(GalleryBackdropType.Mica,GalleryBackdropType.MicaAlt,GalleryBackdropType.None); activate() } }
    private fun createCustomDesktopAcrylicWindow_Click(sender: Any?, args: RoutedEventArgs) { SampleSystemBackdropsWindow().apply { AllowedBackdrops = listOf(GalleryBackdropType.Acrylic,GalleryBackdropType.AcrylicThin,GalleryBackdropType.None); activate() } }

}
