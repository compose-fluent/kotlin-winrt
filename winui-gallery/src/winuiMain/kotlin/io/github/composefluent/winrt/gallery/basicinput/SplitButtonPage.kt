package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.gallery.rgb
import io.github.composefluent.winrt.runtime.asWinRT
import kotlinx.coroutines.delay
import microsoft.ui.text.TextSetOptions
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.ItemClickEventArgs
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.SplitButton
import microsoft.ui.xaml.controls.SplitButtonClickEventArgs
import microsoft.ui.xaml.media.SolidColorBrush
import microsoft.ui.xaml.shapes.Rectangle

@GalleryPage(route = "SplitButton", title = "SplitButton", group = "BasicInput", order = 5)
internal class SplitButtonPage : Page() {
    private var currentColor = rgb(0x008000u)
    private val tasks = GalleryPageTasks(this)

    override fun initializeComponent() {
        super.initializeComponent()
        val selection = checkNotNull(checkNotNull(myRichEditBox.document).selection)
        selectedTextFormat().foregroundColor = currentColor
        selection.setText(TextSetOptions.None,
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
                "sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Tempor commodo ullamcorper a lacus.")
    }

    private fun GridView_ItemClick(sender: Any?, args: ItemClickEventArgs) {
        val rectangle = checkNotNull(args.clickedItem).asWinRT<Rectangle>()
        currentColor = checkNotNull(rectangle.fill).asWinRT<SolidColorBrush>().color
        selectedTextFormat().foregroundColor = currentColor
        CurrentColor.background = SolidColorBrush(currentColor)
        myRichEditBox.focus(FocusState.Keyboard)
        // The original Gallery delays closing for microsoft-ui-xaml issue #6350.
        tasks.launch {
            delay(10)
            myColorButton.flyout?.hide()
        }
    }

    private fun RevealColorButton_Click(sender: Any?, args: RoutedEventArgs) {
        myColorButtonReveal.flyout?.hide()
    }

    private fun myColorButton_Click(sender: SplitButton, args: SplitButtonClickEventArgs) {
        currentColor = checkNotNull(CurrentColor.background).asWinRT<SolidColorBrush>().color
        selectedTextFormat().foregroundColor = currentColor
    }

    private fun MyRichEditBox_TextChanged(sender: Any?, args: RoutedEventArgs) {
        val format = selectedTextFormat()
        if (format.foregroundColor != currentColor) format.foregroundColor = currentColor
    }

    private fun selectedTextFormat() = checkNotNull(
        checkNotNull(checkNotNull(myRichEditBox.document).selection).characterFormat
    )
}
