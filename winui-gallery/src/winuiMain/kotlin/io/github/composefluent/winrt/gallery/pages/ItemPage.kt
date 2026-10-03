// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.pages
import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.ControlInfoDataItem
import io.github.composefluent.winrt.gallery.helpers.PageScrollBehaviorHelper
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
internal class ItemPage(page: GalleryPageInfo,private val setFavorite: (Boolean) -> Unit) : Page() {
    val Item: ControlInfoDataItem = ControlInfoDataItem(page)
    private val sample = GallerySamples.create(page.id)
    override fun initializeComponent() {
        super.initializeComponent()
        pageHeader.ConfigureActions(sample::toggleTheme,setFavorite)
        contentFrame.content = sample.element
        if (PageScrollBehaviorHelper.GetSuppressHostScrolling(sample.element)) {
            svPanel.verticalScrollBarVisibility = ScrollBarVisibility.Disabled
            svPanel.verticalScrollMode = ScrollMode.Disabled
        }
    }
}
