package io.github.composefluent.winrt.gallery.multiplewindows

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "CreateMultipleWindows", title = "Multiple windows", group = "MultipleWindows", order = 2)
internal class CreateMultipleWindowsPage : Page() {
// #if WINUI2
    private val tasks = GalleryPageTasks(this)
    private fun CreateWindow_Click(sender: Any?, args: RoutedEventArgs?) { tasks.launch {
        val window = checkNotNull(windows.ui.windowmanagement.AppWindow.tryCreateAsync()).await()
            ?: error("This device cannot create an additional app window.")
        val content = Page().apply {
            requestedTheme = this@CreateMultipleWindowsPage.actualTheme
            background = GalleryTheme.resource("SolidBackgroundFillColorBaseBrush", requestedTheme).asWinRT<Brush>()
            this.content = StackPanel().apply {
                margin = inset(24.0)
                spacing = 16.0
                children!!.add(TextBlock().apply { text = "UWP secondary window"; fontSize = 24.0 })
                children!!.add(TextBox().apply { header = "Independent window content"; placeholderText = "Type here" })
                children!!.add(Button().apply {
                    this.content = "Close this window"
                    click.add { _, _ -> window.closeAsync() }
                })
            }
        }
        try {
            window.title = "Kotlin UWP — secondary window"
            windows.ui.xaml.hosting.ElementCompositionPreview.setAppWindowContent(window, content)
            window.requestSize(windows.foundation.Size(640f, 480f))
            GalleryWindows.track(window, content)
            check(checkNotNull(window.tryShowAsync()).await()) { "The secondary window could not be shown." }
            galleryLog("Window: secondary window shown")
        } catch (error: Throwable) {
            window.closeAsync()
            throw error
        }
    } }
// #else
    private fun CreateWindow_Click(sender: Any?, args: RoutedEventArgs?) {
        val window = MultipleWindowsSampleWindow()
        checkNotNull(window.content).asWinRT<Page>().requestedTheme = actualTheme
        GalleryWindows.track(window); window.activate()
    }
// #endif
}
