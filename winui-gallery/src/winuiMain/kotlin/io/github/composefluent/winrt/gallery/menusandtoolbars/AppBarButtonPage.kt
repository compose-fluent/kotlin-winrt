package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.automation.peers.AutomationNotificationKind
import microsoft.ui.xaml.automation.peers.AutomationNotificationProcessing
import microsoft.ui.xaml.automation.peers.FrameworkElementAutomationPeer
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.TextBlock

@GalleryPage(route = "AppBarButton", title = "AppBarButton", group = "MenusAndToolbars", order = 0)
internal class AppBarButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun AppBarButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>()
        val name = button.name
        val output: TextBlock = when (name) {
            "Button1" -> Control1Output
            "Button2" -> Control2Output
            "Button3" -> Control3Output
            "Button4" -> Control4Output
            "Button5" -> Control5Output
            else -> return
        }
        output.text = "You clicked: $name"
        FrameworkElementAutomationPeer.fromElement(button)?.raiseNotificationEvent(
            AutomationNotificationKind.ActionCompleted,
            AutomationNotificationProcessing.ImportantMostRecent,
            output.text,
            "AppBarButtonSuccessNotificationId",
        )
    }
}