package io.github.composefluent.winrt.gallery.media

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "PersonPicture", title = "PersonPicture", group = "Media", order = 5)
internal class PersonPicturePage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; applyProfile() }
    private fun applyProfile() {
        personPicture.profilePicture = if (ProfileImageRadio.isChecked == true) microsoft.ui.xaml.media.imaging.BitmapImage(windows.foundation.Uri("https://learn.microsoft.com/windows/uwp/contacts-and-calendar/images/shoulder-tap-static-payload.png")) else null
        personPicture.displayName = if (DisplayNameRadio.isChecked == true) "Jane Doe" else ""
        personPicture.initials = if (InitialsRadio.isChecked == true) "SB" else ""
    }
    private fun RadioButtons_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) applyProfile() }
}
