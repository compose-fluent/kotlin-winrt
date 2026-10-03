package io.github.composefluent.winrt.gallery.design

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Geometry", title = "Geometry", group = "DesignItem", order = 1, glyph = "\uE743")
internal class GeometryPage : Page() {
    private fun ShowGeometryButtonClick1(sender: Any?, args: RoutedEventArgs) { ShowGeometryInfoTooltip1.isOpen = !ShowGeometryInfoTooltip1.isOpen }
    private fun ShowGeometryButtonClick2(sender: Any?, args: RoutedEventArgs) { ShowGeometryInfoTooltip2.isOpen = !ShowGeometryInfoTooltip2.isOpen }
    private fun ShowGeometryButtonClick3(sender: Any?, args: RoutedEventArgs) { ShowGeometryInfoTooltip3.isOpen = !ShowGeometryInfoTooltip3.isOpen }
    private fun copyResource(name: String) {
        windows.applicationmodel.datatransfer.Clipboard.setContent(windows.applicationmodel.datatransfer.DataPackage().apply { setText(name) })
    }
    private fun CopyControlResourceToClipboardButton_Click(sender: Any?, args: RoutedEventArgs) = copyResource("ControlCornerRadius")
    private fun CopyOverlayResourceToClipboardButton_Click(sender: Any?, args: RoutedEventArgs) = copyResource("OverlayCornerRadius")
}
