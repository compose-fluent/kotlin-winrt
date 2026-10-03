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

internal class CollectionPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var storedItem: CustomDataObject? = null
    override fun initializeComponent() {
        super.initializeComponent(); navigationCacheMode = NavigationCacheMode.Enabled
        collection.itemsSource = CustomDataObject.GetDataObjects()
    }
    private fun collection_Loaded(sender: Any?, args: RoutedEventArgs) {
        val item = storedItem ?: return
        collection.scrollIntoView(item, ScrollIntoViewAlignment.Default); collection.updateLayout()
        ConnectedAnimationService.getForCurrentView().getAnimation("BackConnectedAnimation")?.let { animation ->
            animation.configuration = DirectConnectedAnimationConfiguration()
            tasks.launch { collection.tryStartConnectedAnimationAsync(animation, item, "connectedElement").await() }
        }
        collection.focus(FocusState.Programmatic)
    }
    private fun collection_ItemClick(sender: Any?, args: ItemClickEventArgs) {
        storedItem = args.clickedItem as? CustomDataObject
        storedItem?.let { collection.prepareConnectedAnimation("ForwardConnectedAnimation", it, "connectedElement") }
        checkNotNull(frame).navigate(DetailedInfoPage::class, storedItem, SuppressNavigationTransitionInfo())
    }
    private fun TextBlock_IsTextTrimmedChanged(sender: TextBlock, args: IsTextTrimmedChangedEventArgs) { ToolTipService.setToolTip(sender, if (sender.isTextTrimmed) sender.text else "") }
}
