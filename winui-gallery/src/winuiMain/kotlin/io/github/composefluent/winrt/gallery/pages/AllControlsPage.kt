// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.pages
import io.github.composefluent.winrt.gallery.GalleryPageInfo
import io.github.composefluent.winrt.gallery.models.ControlInfoDataItem
internal class AllControlsPage(val Heading: String, pages: List<GalleryPageInfo>) : ItemsPageBase() {
    init { Items = pages.map(::ControlInfoDataItem) }
}
