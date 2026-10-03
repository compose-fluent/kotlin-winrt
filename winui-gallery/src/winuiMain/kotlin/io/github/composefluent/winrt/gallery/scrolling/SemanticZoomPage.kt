package io.github.composefluent.winrt.gallery.scrolling

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "SemanticZoom", title = "SemanticZoom", group = "Scrolling", order = 4)
internal class SemanticZoomPage : Page() {
    val Groups: List<ControlInfoDataGroup> = ControlInfoDataSource.Groups
    private fun List_GotFocus(sender: Any?, args: RoutedEventArgs) { Control1.startBringIntoView() }
}
