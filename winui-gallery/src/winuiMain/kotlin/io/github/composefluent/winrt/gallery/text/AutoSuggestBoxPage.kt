package io.github.composefluent.winrt.gallery.text

import io.github.composefluent.winrt.gallery.GalleryCatalog
import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageInfo
import io.github.composefluent.winrt.gallery.galleryCats
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.controls.AutoSuggestBox
import microsoft.ui.xaml.controls.AutoSuggestBoxQuerySubmittedEventArgs
import microsoft.ui.xaml.controls.AutoSuggestBoxSuggestionChosenEventArgs
import microsoft.ui.xaml.controls.AutoSuggestBoxTextChangedEventArgs
import microsoft.ui.xaml.controls.AutoSuggestionBoxTextChangeReason
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.media.imaging.BitmapImage
import windows.foundation.Uri

@GalleryPage(route = "AutoSuggestBox", title = "AutoSuggestBox", group = "Text", order = 0)
internal class AutoSuggestBoxPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun AutoSuggestBox_TextChanged(sender: AutoSuggestBox, args: AutoSuggestBoxTextChangedEventArgs) {
        if (args.reason != AutoSuggestionBoxTextChangeReason.UserInput) return
        val words = sender.text.split(' ')
        sender.itemsSource = galleryCats.filter { cat -> words.all { cat.contains(it, ignoreCase = true) } }
            .ifEmpty { listOf("No results found") }
    }

    private fun AutoSuggestBox_SuggestionChosen(sender: AutoSuggestBox, args: AutoSuggestBoxSuggestionChosenEventArgs) {
        SuggestionOutput.text = args.selectedItem?.toString().orEmpty()
    }

    private fun Control2_TextChanged(sender: AutoSuggestBox, args: AutoSuggestBoxTextChangedEventArgs) {
        if (args.reason != AutoSuggestionBoxTextChangeReason.UserInput) return
        sender.itemsSource = searchControls(sender.text).map { it.title }.ifEmpty { listOf("No results found") }
    }

    private fun Control2_SuggestionChosen(sender: AutoSuggestBox, args: AutoSuggestBoxSuggestionChosenEventArgs) {
        val title = args.selectedItem?.toString() ?: return
        if (GalleryCatalog.pages.any { it.title == title }) sender.text = title
    }

    private fun Control2_QuerySubmitted(sender: AutoSuggestBox, args: AutoSuggestBoxQuerySubmittedEventArgs) {
        val chosen = args.chosenSuggestion?.toString()
        val page = GalleryCatalog.pages.firstOrNull { it.title == chosen }
            ?: if (args.queryText.isNotEmpty()) searchControls(args.queryText).firstOrNull() else null
        if (page != null) selectControl(page)
    }

    private fun selectControl(page: GalleryPageInfo) {
        ControlDetails.visibility = Visibility.Visible
        ControlImage.source = if (page.image.isNotBlank()) BitmapImage(Uri(page.image)) else null
        ControlTitle.text = page.title
        ControlSubtitle.text = page.subtitle
    }

    private fun searchControls(query: String): List<GalleryPageInfo> {
        val words = query.split(' ')
        return GalleryCatalog.pages.filter { page -> words.all { page.title.contains(it, ignoreCase = true) } }
            .sortedWith(compareBy({ !it.title.startsWith(query, ignoreCase = true) }, { it.title }))
    }
}