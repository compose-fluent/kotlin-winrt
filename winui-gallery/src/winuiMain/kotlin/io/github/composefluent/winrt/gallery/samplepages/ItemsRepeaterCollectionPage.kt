// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.samplepages

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.collections.CustomDataObject
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*
import microsoft.ui.xaml.navigation.*

internal class ItemsRepeaterCollectionPage : Page() {
    private var storedItem: CustomDataObject? = null
    private var persistedScrollPosition = 0.0
    private val tappedHandler: TappedEventHandler = TappedEventHandler(::Item_Tapped)
    private val keyHandler: KeyEventHandler = KeyEventHandler(::Item_KeyDown)
    override fun initializeComponent() {
        super.initializeComponent(); navigationCacheMode = NavigationCacheMode.Enabled
        repeater.itemsSource = CustomDataObject.GetDataObjects(true); repeater.elementPrepared.add(::Repeater_ElementPrepared)
    }
    private fun Repeater_ElementPrepared(sender: ItemsRepeater, args: ItemsRepeaterElementPreparedEventArgs) {
        checkNotNull(args.element).tapped.remove(tappedHandler); checkNotNull(args.element).tapped.add(tappedHandler)
        checkNotNull(args.element).keyDown.remove(keyHandler); checkNotNull(args.element).keyDown.add(keyHandler)
    }
    private fun Item_Tapped(sender: Any?, args: TappedRoutedEventArgs) { sender?.asWinRT<FrameworkElement>()?.let(::NavigateToItem) }
    private fun Item_KeyDown(sender: Any?, args: KeyRoutedEventArgs) {
        if (args.key == windows.system.VirtualKey.Enter || args.key == windows.system.VirtualKey.Space) { sender?.asWinRT<FrameworkElement>()?.let(::NavigateToItem); args.handled = true }
    }
    private fun NavigateToItem(element: FrameworkElement) {
        storedItem = checkNotNull(repeater.itemsSourceView).getAt(repeater.getElementIndex(element)) as? CustomDataObject
        FindChildByName(element, "connectedElement")?.let { ConnectedAnimationService.getForCurrentView().prepareToAnimate("ForwardConnectedAnimation", it) }
        persistedScrollPosition = scrollViewer.verticalOffset
        checkNotNull(frame).navigate(DetailedInfoPage::class, storedItem, SuppressNavigationTransitionInfo())
    }
    override fun onNavigatedTo(args: NavigationEventArgs) {
        super.onNavigatedTo(args); val item = storedItem ?: return
        scrollViewer.changeView(null, persistedScrollPosition, null, true); updateLayout()
        val index = checkNotNull(repeater.itemsSourceView).indexOf(item)
        val container = repeater.tryGetElement(index)?.asWinRT<FrameworkElement>()
        ConnectedAnimationService.getForCurrentView().getAnimation("BackConnectedAnimation")?.let { animation ->
            animation.configuration = DirectConnectedAnimationConfiguration()
            container?.let { FindChildByName(it, "connectedElement") }?.let { animation.tryStart(it) }
        }
        (container ?: repeater).focus(FocusState.Programmatic)
    }
    private fun FindChildByName(parent: DependencyObject, name: String): UIElement? {
        for (index in 0 until VisualTreeHelper.getChildrenCount(parent)) {
            val child = VisualTreeHelper.getChild(parent, index)
            val element = runCatching { child.asWinRT<FrameworkElement>() }.getOrNull()
            if (element?.name == name) return element
            FindChildByName(child, name)?.let { return it }
        }
        return null
    }
}
