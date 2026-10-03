package io.github.composefluent.winrt.gallery.fundamentals

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "Templates", title = "Templates", group = "FundamentalsItem", order = 3)
internal class TemplatesPage : Page() {
    private fun LayoutSelector_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        val tag = (args.addedItems.firstOrNull() as? RadioButton)?.tag?.toString() ?: return
        MyListView.itemsPanel = checkNotNull(resources["${tag}Template"]).asWinRT<ItemsPanelTemplate>()
        val snippet = GalleryCodeCatalog.sampleDefinition("Templates/TemplatesCustomizeItemscontrolItemspaneltemplate.txt")?.xaml?.source.orEmpty()
        Example3.Xaml = if (tag == "StackPanel") snippet.replace("<WrapGrid Orientation=\"Horizontal\" />", "<StackPanel Orientation=\"Vertical\" />") else snippet
    }
}
