package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.announce
import io.github.composefluent.winrt.gallery.brush
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ListViewItem
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.shapes.Rectangle

@GalleryPage(route = "ThemeTransition", title = "Theme Transitions", group = "Motion", order = 5)
internal class ThemeTransitionPage : Page() {
    private var itemCount = 10

    override fun initializeComponent() {
        super.initializeComponent()
        repeat(itemCount) { AddRemoveListView.items.add(ListViewItem().apply { content = "Item $it" }) }
        AddItemsToContentListView()
        unloaded.add { _, _ -> ExamplePopup.isOpen = false }
    }

    private fun ShowPopupButton_Click(sender: Any?, args: RoutedEventArgs) {
        ExamplePopup.isOpen = true
        ClosePopupButton.focus(FocusState.Programmatic)
    }

    private fun ClosePopupButton_Click(sender: Any?, args: RoutedEventArgs) {
        ExamplePopup.isOpen = false
        ShowPopupButton.focus(FocusState.Programmatic)
    }

    private fun ContentRefreshButton_Click(sender: Any?, args: RoutedEventArgs) {
        AddItemsToContentListView(true)
        announce(checkNotNull(sender).asWinRT<UIElement>(), "Data refreshed.", "ContentRefreshNotificationId")
    }

    private fun AddItemsToContentListView(different: Boolean = false) {
        ContentList.itemsSource = List(5) { if (different) "Updated content $it" else "Item $it" }
    }

    private fun AddButton_Click(sender: Any?, args: RoutedEventArgs) {
        AddRemoveListView.items.add(ListViewItem().apply { content = "New Item ${itemCount++}" })
        announce(checkNotNull(sender).asWinRT<UIElement>(), "Item added.", "AddDeleteItemAddedNotificationId")
    }

    private fun DeleteButton_Click(sender: Any?, args: RoutedEventArgs) {
        if (AddRemoveListView.items.isNotEmpty()) {
            AddRemoveListView.items.removeAt(0)
            announce(checkNotNull(sender).asWinRT<UIElement>(), "Item deleted.", "AddDeleteItemDeletedNotificationId")
        }
    }

    private fun RepositionButton_Click(sender: Any?, args: RoutedEventArgs) {
        MiddleElement.visibility = if (MiddleElement.visibility == Visibility.Visible) Visibility.Collapsed else Visibility.Visible
        val announcement = if (MiddleElement.visibility == Visibility.Visible) "Element restored." else "Element repositioned."
        announce(checkNotNull(sender).asWinRT<UIElement>(), announcement, "RepositionNotificationId")
    }

    private fun EntranceAddButton_Click(sender: Any?, args: RoutedEventArgs) {
        val count = checkNotNull(sender).asWinRT<Button>().tag?.toString()?.toIntOrNull() ?: return
        repeat(count) {
            EntranceStackPanel.children.add(Rectangle().apply {
                width = 50.0; height = 50.0
                margin = Thickness(5.0, 5.0, 5.0, 5.0)
                fill = brush(0xADD8E6u)
            })
        }
        val announcement = if (count == 1) "Added 1 rectangle." else "Added $count rectangles."
        announce(checkNotNull(sender).asWinRT<UIElement>(), announcement, "EntranceAddNotificationId")
    }

    private fun EntranceClearButton_Click(sender: Any?, args: RoutedEventArgs) {
        EntranceStackPanel.children.clear()
        announce(checkNotNull(sender).asWinRT<UIElement>(), "All rectangles cleared.", "EntranceClearNotificationId")
    }

    private fun AddDeleteButton_Click(sender: Any?, args: RoutedEventArgs) {
        AddButton_Click(sender, args)
        DeleteButton_Click(sender, args)
        announce(checkNotNull(sender).asWinRT<UIElement>(), "Item added and item deleted.", "AddDeleteBothNotificationId")
    }
}
