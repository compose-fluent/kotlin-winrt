package io.github.composefluent.winrt.gallery.scrolling

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "PipsPager", title = "PipsPager", group = "Scrolling", order = 1)
internal class PipsPagerPage : Page() {
    val Pictures: List<String> = (1..8).map { "ms-appx:///Assets/SampleMedia/LandscapeImage$it.jpg" }
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true }
    private fun TestPipsPager2_SelectedIndexChanged(sender: PipsPager, args: PipsPagerSelectedIndexChangedEventArgs) {
        announce(sender, "Page ${sender.selectedPageIndex + 1} of ${sender.numberOfPages} selected", "PipsPagerPageChangeNotificationId")
    }
    private fun OrientationComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) TestPipsPager2.orientation = if (args.addedItems.firstOrNull()?.toString() == "Vertical") Orientation.Vertical else Orientation.Horizontal }
    private fun visibility(args: SelectionChangedEventArgs): PipsPagerButtonVisibility = when (args.addedItems.firstOrNull()?.toString()) { "Visible" -> PipsPagerButtonVisibility.Visible; "VisibleOnPointerOver" -> PipsPagerButtonVisibility.VisibleOnPointerOver; else -> PipsPagerButtonVisibility.Collapsed }
    private fun PrevButtonComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) TestPipsPager2.previousButtonVisibility = visibility(args) }
    private fun NextButtonComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) TestPipsPager2.nextButtonVisibility = visibility(args) }
    private fun WrapModeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) TestPipsPager2.wrapMode = if (args.addedItems.firstOrNull()?.toString() == "Wrap") PipsPagerWrapMode.Wrap else PipsPagerWrapMode.None }
}
