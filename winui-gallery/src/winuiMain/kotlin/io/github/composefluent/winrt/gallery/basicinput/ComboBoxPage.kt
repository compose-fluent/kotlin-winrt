package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ComboBox", title = "ComboBox", group = "BasicInput", order = 9)
internal class ComboBoxPage : Page() {
    val FontSizes: List<Double> = listOf(8.0, 9.0, 10.0, 11.0, 12.0, 14.0, 16.0, 18.0, 20.0, 24.0, 28.0, 36.0, 48.0, 72.0)
    private val tasks = GalleryPageTasks(this)
    private fun ColorComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        val color = when (args.addedItems.firstOrNull()?.toString()) {
            "Yellow" -> rgb(0xFFFF00u); "Green" -> rgb(0x008000u)
            "Blue" -> rgb(0x0000FFu); "Red" -> rgb(0xFF0000u); else -> return
        }
        Control1Output.fill = SolidColorBrush(color)
    }
    private fun Combo3_Loaded(sender: Any?, args: RoutedEventArgs) {
        Combo3.selectedIndex = 2
        Combo3.textSubmitted.add(::Combo3_TextSubmitted)
    }
    private fun Combo3_TextSubmitted(sender: ComboBox, args: ComboBoxTextSubmittedEventArgs) {
        val value = sender.text.toDoubleOrNull()
        if (value != null && (value in FontSizes || value > 8.0 && value < 100.0)) {
            sender.selectedItem = value
        } else {
            sender.text = sender.selectedValue?.toString().orEmpty()
            tasks.launch { ContentDialog().apply {
                content = "The font size must be a number between 8 and 100."
                closeButtonText = "Close"; defaultButton = ContentDialogButton.Close; xamlRoot = sender.xamlRoot
            }.showAsync().await() }
        }
        args.handled = true
    }
}
