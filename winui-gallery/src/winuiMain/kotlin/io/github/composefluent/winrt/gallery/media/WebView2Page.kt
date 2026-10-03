package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.controls.Page

@GalleryPage(route = "WebView2", title = "WebView2", group = "Media", order = 7)
internal class WebView2Page : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        unloaded.add { _, _ -> MyWebView2.close() }
    }
}