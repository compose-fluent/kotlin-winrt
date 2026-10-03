package io.github.composefluent.winrt.gallery.motion

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "ParallaxView", title = "ParallaxView", group = "Motion", order = 6)
internal class ParallaxViewPage : io.github.composefluent.winrt.gallery.pages.ItemsPageBase() {
    override fun initializeComponent() { Items = io.github.composefluent.winrt.gallery.models.ControlInfoDataSource.Items.sortedBy { it.Title }; super.initializeComponent() }
}
