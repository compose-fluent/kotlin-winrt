package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "CompactSizing", title = "Compact Sizing", group = "Styles", order = 2)
internal class CompactSizingPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun Example1_Loaded(sender: Any?, args: RoutedEventArgs) { ContentFrame.navigate(io.github.composefluent.winrt.gallery.samplepages.SampleStandardSizingPage::class, null, SuppressNavigationTransitionInfo()) }
    private fun Standard_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val previous = ContentFrame.content as? io.github.composefluent.winrt.gallery.samplepages.SampleCompactSizingPage
        ContentFrame.navigate(io.github.composefluent.winrt.gallery.samplepages.SampleStandardSizingPage::class, null, SuppressNavigationTransitionInfo())
        previous?.let { (ContentFrame.content as io.github.composefluent.winrt.gallery.samplepages.SampleStandardSizingPage).CopyState(it) }
    }
    private fun Compact_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val previous = ContentFrame.content as? io.github.composefluent.winrt.gallery.samplepages.SampleStandardSizingPage
        ContentFrame.navigate(io.github.composefluent.winrt.gallery.samplepages.SampleCompactSizingPage::class, null, SuppressNavigationTransitionInfo())
        previous?.let { (ContentFrame.content as io.github.composefluent.winrt.gallery.samplepages.SampleCompactSizingPage).CopyState(it) }
    }
}
