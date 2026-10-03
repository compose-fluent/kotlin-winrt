// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.design

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.IconData
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*

@GalleryPage(route = "Iconography", title = "Iconography", group = "DesignItem", order = 2, glyph = "\uED58")
internal class IconographyPage : Page() {
    val FontSizes: List<Double> = listOf(16.0, 24.0, 32.0, 48.0)
    private val tasks = GalleryPageTasks(this)
    private val icons: List<IconData> by lazy { galleryIcons.map(::IconData) }
    private var filtered: List<IconData> = emptyList()
    private var searchJob: Job? = null
    private var searchGeneration = 0
    private var loadedIcons = false
    var SelectedItem: IconData?
        get() = getValue(SelectedItemProperty) as? IconData
        set(value) { setValue(SelectedItemProperty, value); value?.let(::SetSampleCodePresenterCode) }
    override fun initializeComponent() {
        super.initializeComponent()
        IconsItemsView.loaded.add(::IconsItemsView_Loaded)
    }
    private fun IconsItemsView_Loaded(sender: Any?, args: RoutedEventArgs) {
        if (loadedIcons) return
        loadedIcons = true
        tasks.launch {
            filtered = withContext(Dispatchers.Default) { icons }
            IconsItemsView.itemsSource = filtered
            SelectedItem = filtered.firstOrNull()
            if (filtered.isNotEmpty()) IconsItemsView.select(0)
            SidePanel.visibility = Visibility.Visible
        }
    }
    private fun SetSampleCodePresenterCode(value: IconData) {
        XAMLCodePresenterFont.Code = "<FontIcon Glyph=\"${value.TextGlyph}\" />"
        CSharpCodePresenterFont.Code = "val icon = FontIcon().apply {\n    glyph = \"${value.CodeGlyph}\"\n}"
        XAMLCodePresenterSymbol.Code = value.SymbolName?.let { "<SymbolIcon Symbol=\"$it\" />" }.orEmpty()
        CSharpCodePresenterSymbol.Code = value.SymbolName?.let { "val icon = SymbolIcon(Symbol.$it)" }.orEmpty()
    }
    private fun SearchTextBox_TextChanged(sender: AutoSuggestBox, args: AutoSuggestBoxTextChangedEventArgs) = Filter(sender.text)
    fun Filter(search: String) {
        IconsItemsView.itemsSource = null
        val generation = ++searchGeneration
        searchJob?.cancel()
        searchJob = tasks.launch {
            val terms = search.split(' ').filter(String::isNotEmpty)
            val result = withContext(Dispatchers.Default) {
                icons.filter { icon ->
                    ensureActive()
                    terms.all { term -> icon.Code.contains(term, true) || icon.Name.contains(term, true) || icon.Tags.any { it.contains(term, true) } }
                }
            }
            if (generation != searchGeneration) return@launch
            filtered = result
            IconsItemsView.itemsSource = result
            if (result.isNotEmpty()) { SelectedItem = result.first(); IconsItemsView.select(0) }
            announce(IconsAutoSuggestBox, when (result.size) { 0 -> "No icons found."; 1 -> "1 icon found."; else -> "${result.size} icons found." }, "AutoSuggestBoxNumberIconsFoundId")
        }
    }
    private fun IconsItemsView_SelectionChanged(sender: ItemsView, args: ItemsViewSelectionChangedEventArgs) {
        filtered.getOrNull(sender.currentItemIndex)?.let { SelectedItem = it }
    }
    private fun TagsItemsView_ItemInvoked(sender: ItemsView, args: ItemsViewItemInvokedEventArgs) {
        (args.invokedItem as? String)?.let { IconsAutoSuggestBox.text = it }
    }
    companion object {
        val SelectedItemProperty: DependencyProperty = DependencyProperty.register("SelectedItem", IconData::class, IconographyPage::class, PropertyMetadata(null))
    }
}
