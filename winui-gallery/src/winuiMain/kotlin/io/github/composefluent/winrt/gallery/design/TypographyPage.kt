package io.github.composefluent.winrt.gallery.design

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Typography", title = "Typography", group = "DesignItem", order = 4, glyph = "\uE8D2")
internal class TypographyPage : Page() {
    private fun ShowTypographyButtonClick1(sender: Any?, args: RoutedEventArgs) { ShowTypographyInfoTooltip1.isOpen = !ShowTypographyInfoTooltip1.isOpen }
    private fun ShowTypographyButtonClick2(sender: Any?, args: RoutedEventArgs) { ShowTypographyInfoTooltip2.isOpen = !ShowTypographyInfoTooltip2.isOpen }
    private fun ShowTypographyButtonClick3(sender: Any?, args: RoutedEventArgs) { ShowTypographyInfoTooltip3.isOpen = !ShowTypographyInfoTooltip3.isOpen }
    private fun ShowTypographyButtonClick4(sender: Any?, args: RoutedEventArgs) { ShowTypographyInfoTooltip4.isOpen = !ShowTypographyInfoTooltip4.isOpen }
    private fun ShowTypographyButtonClick5(sender: Any?, args: RoutedEventArgs) { ShowTypographyInfoTooltip5.isOpen = !ShowTypographyInfoTooltip5.isOpen }
}
