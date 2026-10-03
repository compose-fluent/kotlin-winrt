package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "StandardUICommand", title = "StandardUICommand", group = "MenusAndToolbars", order = 8)
internal class StandardUICommandPage : Page() {
    private val collection: MutableList<ListItemData> = WinRTObservableList()
    private fun DeleteCommand_ExecuteRequested(sender: microsoft.ui.xaml.input.XamlUICommand, args: microsoft.ui.xaml.input.ExecuteRequestedEventArgs) {
        val item = collection.firstOrNull { it.Text == args.parameter as? String }
        if (item != null) collection.remove(item) else if (ListViewRight.selectedIndex in collection.indices) collection.removeAt(ListViewRight.selectedIndex)
    }
    private fun ListView_Loaded(sender: Any?, args: RoutedEventArgs) { checkNotNull(sender).asWinRT<ListView>().itemsSource = collection }
    private fun ListViewSwipeContainer_PointerEntered(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) {
        val pointer = args.pointer ?: return
        if (pointer.pointerDeviceType == microsoft.ui.input.PointerDeviceType.Mouse || pointer.pointerDeviceType == microsoft.ui.input.PointerDeviceType.Pen) VisualStateManager.goToState(checkNotNull(sender).asWinRT<Control>(), "HoverButtonsShown", true)
    }
    private fun ListViewSwipeContainer_PointerExited(sender: Any?, args: microsoft.ui.xaml.input.PointerRoutedEventArgs) { VisualStateManager.goToState(checkNotNull(sender).asWinRT<Control>(), "HoverButtonsHidden", true) }
    private fun ControlExample_Loaded(sender: Any?, args: RoutedEventArgs) {
        // StandardUICommand is available on every Windows App SDK version targeted here.
        val command = microsoft.ui.xaml.input.StandardUICommand(microsoft.ui.xaml.input.StandardUICommandKind.Delete)
            .also { it.executeRequested.add(::DeleteCommand_ExecuteRequested) }.asWinRT<microsoft.ui.xaml.input.ICommand>()
        DeleteFlyoutItem.command = command
        collection.clear(); (0 until 15).forEach { collection.add(ListItemData("List item $it", command)) }
    }
    private fun ListViewRight_ContainerContentChanging(sender: ListViewBase, args: ContainerContentChangingEventArgs) {
        val data = args.item as? ListItemData ?: return
        val flyout = MenuFlyout().apply {
            items.add(MenuFlyoutItem().apply { command = data.Command })
            opened.add { _, _ -> target?.asWinRT<ListViewItem>()?.isSelected = true }
        }
        args.itemContainer?.contextFlyout = flyout
    }
}
