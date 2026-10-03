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

internal class CardPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private var storedItem: CustomDataObject? = null
    override fun initializeComponent() { super.initializeComponent(); collection.itemsSource = CustomDataObject.GetDataObjects(true) }
    private fun BackButton_Click(sender: Any?, args: RoutedEventArgs) {
        val item = storedItem ?: return
        val animation = ConnectedAnimationService.getForCurrentView().prepareToAnimate("backwardsAnimation", destinationElement)
        SmokeGrid.children.remove(destinationElement)
        animation.completed.add(::Animation_Completed)
        collection.scrollIntoView(item, ScrollIntoViewAlignment.Default); collection.updateLayout()
        animation.configuration = DirectConnectedAnimationConfiguration()
        tasks.launch { if (!collection.tryStartConnectedAnimationAsync(animation, item, "connectedElement").await()) Animation_Completed(animation, null) }
    }
    private fun Animation_Completed(sender: ConnectedAnimation, args: Any?) { SmokeGrid.visibility = Visibility.Collapsed; if (!SmokeGrid.children.contains(destinationElement)) SmokeGrid.children.add(destinationElement) }
    private fun TipsGrid_ItemClick(sender: Any?, args: ItemClickEventArgs) {
        val item = args.clickedItem as? CustomDataObject ?: return
        storedItem = item
        val animation = collection.prepareConnectedAnimation("forwardAnimation", item, "connectedElement")
        detailImage.source = microsoft.ui.xaml.media.imaging.BitmapImage(windows.foundation.Uri(item.ImageLocation))
        detailTitle.text = item.Title; detailDescription.text = item.Description
        SmokeGrid.visibility = Visibility.Visible; animation?.tryStart(destinationElement)
    }
}
