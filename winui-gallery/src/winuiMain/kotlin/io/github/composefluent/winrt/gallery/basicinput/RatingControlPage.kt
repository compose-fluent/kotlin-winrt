package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "RatingControl", title = "RatingControl", group = "BasicInput", order = 11)
internal class RatingControlPage : Page() {
    private fun RatingControl1_ValueChanged(sender: RatingControl, args: Any?) { sender.caption = "Your rating" }
}
