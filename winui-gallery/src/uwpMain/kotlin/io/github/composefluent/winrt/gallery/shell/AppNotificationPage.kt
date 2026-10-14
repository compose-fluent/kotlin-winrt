package io.github.composefluent.winrt.gallery.shell

import io.github.composefluent.winrt.gallery.*
import windows.data.xml.dom.XmlDocument
import windows.ui.notifications.ToastNotification
import windows.ui.notifications.ToastNotificationManager
import windows.ui.xaml.RoutedEventArgs
import windows.ui.xaml.controls.Page

@GalleryPage(route = "AppNotification", title = "App notifications", group = "Shell", order = 0)
internal class AppNotificationPage : Page() {
    private val tasks = GalleryPageTasks(this)
    private val appNotificationSoundEvents = listOf("Default", "IM", "Reminder", "SMS", "Alarm", "Call")
    private var selectedAppNotificationSoundEvent = "Default"

    // UWP consumes the documented adaptive-toast XML schema directly.
    // Activation is delivered to Application.OnActivated, not a WASDK manager.
    private fun show(binding: String, actions: String = "", audio: String = "") { tasks.launch {
        val xml = XmlDocument().apply {
            loadXml("""<toast launch="AppNotification"><visual><binding template="ToastGeneric">$binding</binding></visual>$actions$audio</toast>""")
        }
        val notification = ToastNotification(xml).apply { tag = "gallery"; group = "samples" }
        notification.failed.add { _, args -> galleryLog("Toast failed: ${args?.errorCode}") }
        val notifier = checkNotNull(ToastNotificationManager.createToastNotifier())
        notifier.show(notification)
        galleryLog("Toast submitted: setting=${notifier.setting}")
    } }
    private fun ShowNotificationButton_Click(sender: Any?, args: RoutedEventArgs?) = show(
        "<text>Welcome to Kotlin WinUI Gallery</text><text>Explore interactive UWP samples.</text>")
    private fun ShowInformationalNotificationWithLogoButton_Click(sender: Any?, args: RoutedEventArgs?) {
        val sound = when (selectedAppNotificationSoundEvent) {
            "Alarm" -> "Looping.Alarm"
            "Call" -> "Looping.Call"
            else -> selectedAppNotificationSoundEvent
        }
        show("""<text>Control Highlight: PersonPicture</text><text>Display user avatars with initials or images.</text><image placement="appLogoOverride" hint-crop="circle" src="ms-appx:///Assets/ControlImages/PersonPicture.png"/>""",
            audio = """<audio src="ms-winsoundevent:Notification.$sound"/>""")
    }
    private fun ShowVisualNotificationWithHeroImageButton_Click(sender: Any?, args: RoutedEventArgs?) = show(
        """<text>Harbor Scene with Boats</text><text>A quiet harbor with boats gently anchored in view.</text><image placement="hero" src="ms-appx:///Assets/SampleMedia/LandscapeImage5.jpg"/><text placement="attribution">WinUI gallery assets</text>""")
    private fun ShowNotificationWithControlsButton_Click(sender: Any?, args: RoutedEventArgs?) = show(
        "<text>Survey</text><text>Please select your satisfaction level and leave a comment.</text>",
        """<actions><input id="satisfaction" type="selection" defaultInput="3"><selection id="1" content="Very Bad"/><selection id="2" content="Bad"/><selection id="3" content="Neutral"/><selection id="4" content="Good"/><selection id="5" content="Excellent"/></input><input id="comment" type="text" placeHolderContent="Leave a comment here..."/><action content="Submit" arguments="submit_survey" activationType="foreground"/></actions>""")
    private fun ShowNotificationWithProgressBarButton_Click(sender: Any?, args: RoutedEventArgs?) = show(
        """<text>Progress Bar Example</text><progress title="Demo Progress" value="0.6" valueStringOverride="60%" status="In progress..."/>""")
}
