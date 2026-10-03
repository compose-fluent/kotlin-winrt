package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.pages.ItemsPageBase
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "ItemsView", title = "ItemsView", group = "Collections", order = 3)
internal class ItemsViewPage : ItemsPageBase() {
    private var ready = false
    private var initialized = false
    private var linedFlowLayout: LinedFlowLayout? = null
    private var stackLayout: StackLayout? = null
    private var uniformGridLayout: UniformGridLayout? = null
    private var linedFlowLayoutItemTemplate: DataTemplate? = null
    private var applyLineHeight = false
    private var applyOptions = false
    override fun initializeComponent() {
        super.initializeComponent(); ready = true; dataContext = this
        loaded.add { _, _ ->
            if (!initialized) {
                initialized = true
                linedFlowLayout = SwappableLayoutsItemsView.layout?.asWinRT<LinedFlowLayout>()
                linedFlowLayoutItemTemplate = SwappableLayoutsItemsView.itemTemplate?.asWinRT<DataTemplate>()
                SwappableLayoutsItemsView.scrollView?.viewChanged?.add(::SwappableLayoutsItemsViewScrollView_ViewChanged)
                val items = WinRTObservableList(CustomDataObject.GetDataObjects(true))
                BasicItemsView.itemsSource = items; SwappableSelectionModesItemsView.itemsSource = items
                checkNotNull(dispatcherQueue).tryEnqueue(microsoft.ui.dispatching.DispatcherQueuePriority.Low) { SwappableLayoutsItemsView.itemsSource = items }
            }
        }
    }
    private fun BasicItemsView_ItemInvoked(sender: ItemsView, args: ItemsViewItemInvokedEventArgs) { (args.invokedItem as? CustomDataObject)?.let { tblBasicInvokeOutput.text = "You invoked ${it.Title}." } }
    private fun ApplyLinedFlowLayoutLineHeight() { if (ready) linedFlowLayout?.lineHeight = if (rbSmallLineHeight.isChecked == true) 80.0 else 160.0 }
    private fun ApplyLinedFlowLayoutOptions() { if (ready) linedFlowLayout?.let { it.lineSpacing = nbLineSpacing.value; it.minItemSpacing = nbMinItemSpacing.value } }
    private fun RbLayout_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val name = checkNotNull(sender).asWinRT<RadioButton>().content.toString()
        when (name) {
            "LinedFlowLayout" -> { if (linedFlowLayout == null) linedFlowLayout = SwappableLayoutsItemsView.layout?.asWinRT<LinedFlowLayout>(); if (linedFlowLayoutItemTemplate == null) linedFlowLayoutItemTemplate = SwappableLayoutsItemsView.itemTemplate?.asWinRT<DataTemplate>(); SwappableLayoutsItemsView.layout = linedFlowLayout; SwappableLayoutsItemsView.itemTemplate = linedFlowLayoutItemTemplate }
            "StackLayout" -> { if (stackLayout == null) stackLayout = StackLayout().apply { spacing = 5.0 }; SwappableLayoutsItemsView.layout = stackLayout; SwappableLayoutsItemsView.itemTemplate = checkNotNull(resources["StackLayoutItemTemplate"]).asWinRT<DataTemplate>() }
            "UniformGridLayout" -> { if (uniformGridLayout == null) uniformGridLayout = UniformGridLayout().apply { minColumnSpacing = 5.0; minRowSpacing = 5.0; maximumRowsOrColumns = 3 }; SwappableLayoutsItemsView.layout = uniformGridLayout; SwappableLayoutsItemsView.itemTemplate = checkNotNull(resources["UniformGridLayoutItemTemplate"]).asWinRT<DataTemplate>() }
        }
        spLinedFlowLayoutOptions.visibility = if (name == "LinedFlowLayout") Visibility.Visible else Visibility.Collapsed
        spStackLayoutOptions.visibility = if (name == "StackLayout") Visibility.Visible else Visibility.Collapsed
        spUniformGridLayoutOptions.visibility = if (name == "UniformGridLayout") Visibility.Visible else Visibility.Collapsed
    }
    private fun RbLineHeight_Checked(sender: Any?, args: RoutedEventArgs) {
        if (!ready) return
        val view = SwappableLayoutsItemsView.scrollView
        if (view != null && view.verticalOffset != 0.0) { applyLineHeight = true; view.scrollTo(0.0, 0.0, ScrollingScrollOptions(ScrollingAnimationMode.Disabled, ScrollingSnapPointsMode.Ignore)) } else ApplyLinedFlowLayoutLineHeight()
    }
    private fun NbLinedFlowLayoutOptions_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) {
        if (!ready) return
        val view = SwappableLayoutsItemsView.scrollView
        if (view != null && view.verticalOffset != 0.0) { applyOptions = true; view.scrollTo(0.0, 0.0, ScrollingScrollOptions(ScrollingAnimationMode.Disabled, ScrollingSnapPointsMode.Ignore)) } else ApplyLinedFlowLayoutOptions()
    }
    private fun NbStackLayoutOptions_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) { if (ready) stackLayout?.spacing = nbSpacing.value }
    private fun NbUniformGridLayoutOptions_ValueChanged(sender: NumberBox, args: NumberBoxValueChangedEventArgs) { if (ready) uniformGridLayout?.let { it.minColumnSpacing = nbMinColumnSpacing.value; it.minRowSpacing = nbMinRowSpacing.value; it.maximumRowsOrColumns = nbMaximumRowsOrColumns.value.toInt() } }
    private fun SwappableLayoutsItemsViewScrollView_ViewChanged(sender: ScrollView, args: Any?) {
        if (sender.verticalOffset != 0.0) return
        if (applyOptions) { applyOptions = false; ApplyLinedFlowLayoutOptions() }
        if (applyLineHeight) { applyLineHeight = false; ApplyLinedFlowLayoutLineHeight() }
    }
    private fun SwappableSelectionModesItemsView_ItemInvoked(sender: ItemsView, args: ItemsViewItemInvokedEventArgs) { (args.invokedItem as? CustomDataObject)?.let { tblInvocationOutput.text = "You invoked ${it.Title}." } }
    private fun SwappableSelectionModesItemsView_SelectionChanged(sender: ItemsView, args: ItemsViewSelectionChangedEventArgs) { if (ready) tblSelectionOutput.text = "You have selected ${sender.selectedItems.size} item(s)." }
    private fun CmbSelectionMode_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) SwappableSelectionModesItemsView.selectionMode = ItemsViewSelectionMode.fromAbi(checkNotNull(sender).asWinRT<ComboBox>().selectedIndex) }
    private fun ChkIsItemInvokedEnabled_IsCheckedChanged(sender: Any?, args: RoutedEventArgs) { if (ready) { tblInvocationOutput.text = ""; SwappableSelectionModesItemsView.isItemInvokedEnabled = chkIsItemInvokedEnabled.isChecked == true } }
}
