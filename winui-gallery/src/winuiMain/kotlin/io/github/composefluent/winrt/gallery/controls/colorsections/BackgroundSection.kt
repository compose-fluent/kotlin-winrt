package io.github.composefluent.winrt.gallery.controls.colorsections

import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.documents.Hyperlink
import microsoft.ui.xaml.documents.HyperlinkClickEventArgs
import io.github.composefluent.winrt.gallery.GalleryNavigationHost

internal class BackgroundSection : Page() {
    private fun SystemBackdropLink_Click(sender: Hyperlink, args: HyperlinkClickEventArgs) { GalleryNavigationHost.navigate("SystemBackdrops") }
    private fun SystemBackdropElementLink_Click(sender: Hyperlink, args: HyperlinkClickEventArgs) { GalleryNavigationHost.navigate("SystemBackdropElement") }
}
