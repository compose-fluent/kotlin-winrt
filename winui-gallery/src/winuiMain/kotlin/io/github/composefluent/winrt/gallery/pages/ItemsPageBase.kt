package io.github.composefluent.winrt.gallery.pages

import io.github.composefluent.winrt.gallery.GalleryNavigationHost
import io.github.composefluent.winrt.gallery.models.ControlInfoDataItem
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.data.*

/** Shared business base; its final pages keep one composed SDK Page identity. */
internal abstract class ItemsPageBase : Page(), INotifyPropertyChanged {
    private val handlers = mutableListOf<PropertyChangedEventHandler>()
    var FocusItemId: String? = null
    var Items: List<ControlInfoDataItem>? = null
        protected set(value) { if (field !== value) { field = value; handlers.toList().forEach { it(this, PropertyChangedEventArgs("Items")) } } }
    override fun addPropertyChanged(handler: PropertyChangedEventHandler) { handlers.add(handler) }
    override fun removePropertyChanged(handler: PropertyChangedEventHandler) { handlers.remove(handler) }
    protected fun OnItemClick(sender: Any?, args: ItemClickEventArgs) { (args.clickedItem as? ControlInfoDataItem)?.let { GalleryNavigationHost.navigate(it.UniqueId) } }
    protected fun OnItemContainerChanged(sender: ListViewBase, args: ContainerContentChangingEventArgs) {
        val item = args.item as? ControlInfoDataItem ?: return
        args.itemContainer?.isEnabled = item.IncludedInBuild
    }
    protected fun OnItemGridViewItemClick(sender: Any?, args: ItemClickEventArgs) {
        val item = args.clickedItem as? ControlInfoDataItem ?: return
        FocusItemId = item.UniqueId
        GalleryNavigationHost.navigate(item.UniqueId)
    }
    protected fun OnItemGridViewContainerContentChanging(sender: ListViewBase, args: ContainerContentChangingEventArgs) {
        OnItemContainerChanged(sender,args)
        sender.items.lastOrNull()?.let { item ->
            sender.containerFromItem(item)?.asWinRT<GridViewItem>()?.let { it.xYFocusDown = it }
        }
    }
    protected fun OnItemGridViewLoaded(sender: Any?, args: RoutedEventArgs) {
        val view = checkNotNull(sender).asWinRT<GridView>()
        val item = (view.itemsSource as? List<*>)?.filterIsInstance<ControlInfoDataItem>()?.firstOrNull { it.UniqueId == FocusItemId } ?: return
        view.scrollIntoView(item)
        view.containerFromItem(item)?.asWinRT<GridViewItem>()?.focus(FocusState.Programmatic)
    }
}
