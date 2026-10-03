package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "SwipeControl", title = "SwipeControl", group = "MenusAndToolbars", order = 7)
internal class SwipeControlPage : Page() {
    private var isArchived = false
    private var isFlagged = false
    private var isAccepted = false
    private val items: MutableList<Any?> = WinRTObservableList(listOf("Swipe Item 1", "Swipe Item 2", "Swipe Item 3", "Swipe Item 4"))
    override fun initializeComponent() { super.initializeComponent(); lv.itemsSource = items }
    private fun DeleteOne_ItemInvoked(sender: SwipeItem, args: SwipeItemInvokedEventArgs) { isArchived = !isArchived; checkNotNull(checkNotNull(args.swipeControl).content).asWinRT<TextBlock>().text = if (isArchived) "Archived - Swipe Left" else "Swipe Left" }
    private fun DeleteItem_ItemInvoked(sender: SwipeItem, args: SwipeItemInvokedEventArgs) { items.remove(args.swipeControl?.dataContext) }
    private fun Accept_ItemInvoked(sender: SwipeItem, args: SwipeItemInvokedEventArgs) {
        isAccepted = !isAccepted; CheckAcceptFlagBool(checkNotNull(args.swipeControl))
        sender.iconSource = FontIconSource().apply { glyph = if (isAccepted) "\uE711" else "\uE10B" }; sender.text = if (isAccepted) "Cancel" else "Accept"
    }
    private fun Flag_ItemInvoked(sender: SwipeItem, args: SwipeItemInvokedEventArgs) {
        isFlagged = !isFlagged; CheckAcceptFlagBool(checkNotNull(args.swipeControl))
        sender.iconSource = FontIconSource().apply { glyph = if (isFlagged) "\uEB4B" else "\uE129" }; sender.text = if (isFlagged) "Unmark" else "Flag"
    }
    private fun CheckAcceptFlagBool(swipe: SwipeControl) {
        checkNotNull(swipe.content).asWinRT<TextBlock>().text = when { isAccepted && isFlagged -> "Swipe Right - Accepted & Flagged"; isAccepted -> "Swipe Right - Accepted"; isFlagged -> "Swipe Right - Flagged"; else -> "Swipe Right" }
    }
}
