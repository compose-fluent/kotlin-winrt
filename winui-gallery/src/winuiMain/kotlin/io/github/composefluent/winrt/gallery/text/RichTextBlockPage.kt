package io.github.composefluent.winrt.gallery.text

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.rgb
import microsoft.ui.xaml.controls.ComboBox
import microsoft.ui.xaml.controls.ComboBoxItem
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.SelectionChangedEventArgs
import microsoft.ui.xaml.documents.TextHighlighter
import microsoft.ui.xaml.documents.TextRange
import microsoft.ui.xaml.media.SolidColorBrush

@GalleryPage(route = "RichTextBlock", title = "RichTextBlock", group = "Text", order = 4)
internal class RichTextBlockPage : Page() {
    private fun HighlightColorCombobox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        val selected = (sender as? ComboBox)?.selectedItem as? ComboBoxItem
        val color = when (selected?.content as? String) {
            "Red" -> rgb(0xFF0000u)
            "Blue" -> rgb(0x0000FFu)
            else -> rgb(0xFFFF00u)
        }
        val highlighter = TextHighlighter().apply {
            background = SolidColorBrush(color)
            ranges.add(TextRange(28, 11))
        }
        TextHighlightingRichTextBlock.textHighlighters.clear()
        TextHighlightingRichTextBlock.textHighlighters.add(highlighter)
    }
}
