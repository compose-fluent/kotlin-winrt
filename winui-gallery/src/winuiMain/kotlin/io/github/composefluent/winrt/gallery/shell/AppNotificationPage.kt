package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*
import microsoft.ui.xaml.media.animation.*

@GalleryPage(route = "AppNotification", title = "App notifications", group = "Shell", order = 0)
internal class AppNotificationPage : Page() {
    private val appNotificationSoundEvents: List<microsoft.windows.appnotifications.builder.AppNotificationSoundEvent> = listOf(
        microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.Default, microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.IM,
        microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.Reminder, microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.SMS,
        microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.Alarm, microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.Call)
    private var selectedAppNotificationSoundEvent: microsoft.windows.appnotifications.builder.AppNotificationSoundEvent = microsoft.windows.appnotifications.builder.AppNotificationSoundEvent.Default
    private fun ShowInformationalNotificationWithLogoButton_Click(sender: Any?, args: RoutedEventArgs) {
        checkNotNull(microsoft.windows.appnotifications.AppNotificationManager.default).show(microsoft.windows.appnotifications.builder.AppNotificationBuilder()
            .addText("Control Highlight: PersonPicture").addText("Use the PersonPicture control to display user avatars with initials or images.")
            .setAppLogoOverride(windows.foundation.Uri("ms-appx:///Assets/ControlImages/PersonPicture.png"), microsoft.windows.appnotifications.builder.AppNotificationImageCrop.Circle)
            .setAudioEvent(selectedAppNotificationSoundEvent).setTimeStamp(kotlin.time.Clock.System.now()).buildNotification())
    }
    private fun ShowVisualNotificationWithHeroImageButton_Click(sender: Any?, args: RoutedEventArgs) {
        checkNotNull(microsoft.windows.appnotifications.AppNotificationManager.default).show(microsoft.windows.appnotifications.builder.AppNotificationBuilder()
            .addText("Harbor Scene with Boats").addText("A quiet harbor with boats gently anchored in view.")
            .setHeroImage(windows.foundation.Uri("ms-appx:///Assets/SampleMedia/LandscapeImage5.jpg")).setAttributionText("WinUI gallery assets").buildNotification())
    }
    private fun ShowNotificationButton_Click(sender: Any?, args: RoutedEventArgs) {
        checkNotNull(microsoft.windows.appnotifications.AppNotificationManager.default).show(microsoft.windows.appnotifications.builder.AppNotificationBuilder().addText("Welcome to WinUI 3 Gallery").addText("Explore interactive samples and discover the power of modern Windows UI.").buildNotification())
    }
    private fun ShowNotificationWithControlsButton_Click(sender: Any?, args: RoutedEventArgs) {
        checkNotNull(microsoft.windows.appnotifications.AppNotificationManager.default).show(microsoft.windows.appnotifications.builder.AppNotificationBuilder()
            .addText("Survey").addText("Please select your satisfaction level and leave a comment.")
            .addComboBox(microsoft.windows.appnotifications.builder.AppNotificationComboBox("satisfaction").addItem("1", "Very Bad").addItem("2", "Bad").addItem("3", "Neutral").addItem("4", "Good").addItem("5", "Excellent").setSelectedItem("3"))
            .addTextBox("comment", "Leave a comment here...", "").addButton(microsoft.windows.appnotifications.builder.AppNotificationButton("Submit").addArgument("action", "submit_survey")).buildNotification())
    }
    private fun ShowNotificationWithProgressBarButton_Click(sender: Any?, args: RoutedEventArgs) {
        checkNotNull(microsoft.windows.appnotifications.AppNotificationManager.default).show(microsoft.windows.appnotifications.builder.AppNotificationBuilder().addText("Progress Bar Example").addText("This is a sample notification showing how to use a progress bar.")
            .addProgressBar(microsoft.windows.appnotifications.builder.AppNotificationProgressBar().apply { title = "Demo Progress"; value = 0.6; valueStringOverride = "60%"; status = "In progress..." }).buildNotification())
    }
}
