package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "FlipView", title = "FlipView", group = "Collections", order = 0)
internal class FlipViewPage : io.github.composefluent.winrt.gallery.pages.ItemsPageBase() {
    override fun initializeComponent() { super.initializeComponent(); Items = io.github.composefluent.winrt.gallery.models.ControlInfoDataSource.Groups.take(3).flatMap { it.Items } }
}
