package io.github.composefluent.winrt.gallery.fundamentals

import io.github.composefluent.winrt.gallery.GalleryNavigationHost
import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.documents.Hyperlink
import microsoft.ui.xaml.documents.HyperlinkClickEventArgs

@GalleryPage(route = "XamlResources", title = "Resources", group = "FundamentalsItem", order = 0)
internal class XamlResourcesPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun Hyperlink_Click(sender: Hyperlink, args: HyperlinkClickEventArgs) {
        GalleryNavigationHost.navigate("Color")
    }
}
