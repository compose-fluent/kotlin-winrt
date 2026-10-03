package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.pages.ItemsPageBase
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "GridView", title = "GridView", group = "Collections", order = 1)
internal class GridViewPage : ItemsPageBase() {
    private var ready = false
    private var StyledGridIWG: ItemsWrapGrid? = null
    override fun initializeComponent() {
        super.initializeComponent(); ready = true; dataContext = this
        val items = WinRTObservableList(CustomDataObject.GetDataObjects())
        BasicGridView.itemsSource = WinRTObservableList(items); ContentGridView.itemsSource = items; StyledGrid.itemsSource = items
        DisplayDT.Value = GalleryCodeCatalog.sourceDocument("GridView/ImageTemplate.txt")?.source ?: ""
    }
    private fun ItemTemplate_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val key = checkNotNull(sender).asWinRT<FrameworkElement>().tag?.toString() ?: return
        ContentGridView.itemTemplate = checkNotNull(resources[key]).asWinRT<DataTemplate>(); itemTemplate.Value = key
        DisplayDT.Value = GalleryCodeCatalog.sourceDocument("GridView/$key.txt")?.source ?: ""
    }
    private fun ContentGridView_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) SelectionOutput.text = "You have selected ${ContentGridView.selectedItems.size} item(s)." }
    private fun ContentGridView_ItemClick(sender: Any?, args: ItemClickEventArgs) { (args.clickedItem as? CustomDataObject)?.let { ClickOutput.text = "You clicked ${it.Title}." } }
    private fun BasicGridView_ItemClick(sender: Any?, args: ItemClickEventArgs) { (args.clickedItem as? CustomDataObject)?.let { ClickOutput0.text = "You clicked ${it.Title}." } }
    private fun ItemClickCheckBox_Click(sender: Any?, args: RoutedEventArgs) { if (ready) ClickOutput.text = "" }
    private fun FlowDirectionCheckBox_Click(sender: Any?, args: RoutedEventArgs) { if (ready) ContentGridView.flowDirection = if (ContentGridView.flowDirection == FlowDirection.LeftToRight) FlowDirection.RightToLeft else FlowDirection.LeftToRight }
    private fun SelectionModeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) ContentGridView.selectionMode = ListViewSelectionMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun StyledGrid_InitWrapGrid(sender: Any?, args: RoutedEventArgs) { StyledGridIWG = checkNotNull(sender).asWinRT<ItemsWrapGrid>().apply { maximumRowsOrColumns = 3 } }
    private fun NumberBox_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (!ready) return
        val panel = StyledGridIWG ?: return
        if (sender == WrapItemCount) { panel.maximumRowsOrColumns = WrapItemCount.value.toInt(); return }
        for (index in StyledGrid.items.indices) StyledGrid.containerFromIndex(index)?.asWinRT<GridViewItem>()?.let { it.margin = Thickness(ColumnSpace.value, RowSpace.value, ColumnSpace.value, RowSpace.value) }
    }
}
