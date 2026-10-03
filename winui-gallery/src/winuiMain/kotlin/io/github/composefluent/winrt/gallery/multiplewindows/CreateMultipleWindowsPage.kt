package io.github.composefluent.winrt.gallery.multiplewindows

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "CreateMultipleWindows", title = "Multiple windows", group = "MultipleWindows", order = 2)
internal class CreateMultipleWindowsPage : Page() {
    private fun CreateWindow_Click(sender: Any?, args: RoutedEventArgs) {
        val window = MultipleWindowsSampleWindow()
        checkNotNull(window.content).asWinRT<Page>().requestedTheme = actualTheme
        GalleryWindows.track(window); window.activate()
    }
}
