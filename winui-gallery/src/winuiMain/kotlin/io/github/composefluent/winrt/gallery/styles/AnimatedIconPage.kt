package io.github.composefluent.winrt.gallery.styles

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "AnimatedIcon", title = "AnimatedIcon", group = "Styles", order = 1)
internal class AnimatedIconPage : Page() {
    private fun Button_PointerEntered(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) { AnimatedIcon.setState(SearchAnimatedIcon, "PointerOver") }
    private fun Button_PointerExited(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) { AnimatedIcon.setState(SearchAnimatedIcon, "Normal") }
    companion object {
        fun GetAnimationSourceFromString(selection: Any?): IAnimatedVisualSource2 = when (selection?.toString()) {
            "AnimatedBackVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedBackVisualSource()
            "AnimatedChevronDownSmallVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedChevronDownSmallVisualSource()
            "AnimatedChevronRightDownSmallVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedChevronRightDownSmallVisualSource()
            "AnimatedChevronUpDownSmallVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedChevronUpDownSmallVisualSource()
            "AnimatedFindVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedFindVisualSource()
            "AnimatedGlobalNavigationButtonVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedGlobalNavigationButtonVisualSource()
            "AnimatedSettingsVisualSource" -> microsoft.ui.xaml.controls.animatedvisuals.AnimatedSettingsVisualSource()
            else -> error("$selection is not a valid animated visual")
        }
    }
}
