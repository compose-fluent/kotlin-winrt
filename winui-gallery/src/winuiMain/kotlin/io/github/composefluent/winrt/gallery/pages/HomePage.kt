// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.pages
import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.models.*
import microsoft.ui.xaml.VisualStateManager
@GalleryPage(route="Home",title="Home",group="",order=-1,glyph="\uE80F")
internal class HomePage : ItemsPageBase() {
    val RecentlyVisitedSamplesList: List<ControlInfoDataItem> = valid("Recent")
    val RecentlyAddedOrUpdatedSamplesList: List<ControlInfoDataItem> = ControlInfoDataSource.Items.filter { it.IsNew || it.IsUpdated }
    val FavoriteSamplesList: List<ControlInfoDataItem> = valid("Favorites")
    private fun valid(key: String): List<ControlInfoDataItem> {
        val items = ControlInfoDataSource.Items.associateBy { it.UniqueId }
        val routes = GalleryPreferences.routes(key)
        val valid = routes.mapNotNull(items::get)
        if (valid.size != routes.size) GalleryPreferences.putRoutes(key,valid.map { it.UniqueId })
        return valid
    }
    override fun initializeComponent() {
        super.initializeComponent()
        Items = ControlInfoDataSource.Items.sortedBy { it.Title }
        // Upstream HomePage.OnNavigatedTo applies these states before the first layout.
        // Waiting for Loaded leaves an empty nested GridView visible during measurement.
        VisualStateManager.goToState(this,if (RecentlyVisitedSamplesList.isEmpty()) "NoRecent" else "Recent",false)
        VisualStateManager.goToState(this,if (FavoriteSamplesList.isEmpty()) "NoFavorites" else "Favorites",false)
    }
}
