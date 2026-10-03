package io.github.composefluent.winrt.gallery.menusandtoolbars

import io.github.composefluent.winrt.gallery.GalleryPage
import microsoft.ui.xaml.automation.peers.AutomationNotificationKind
import microsoft.ui.xaml.automation.peers.AutomationNotificationProcessing
import microsoft.ui.xaml.automation.peers.FrameworkElementAutomationPeer
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.input.ExecuteRequestedEventArgs
import microsoft.ui.xaml.input.XamlUICommand

@GalleryPage(route = "XamlUICommand", title = "XamlUICommand", group = "MenusAndToolbars", order = 9)
internal class XamlUICommandPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun CustomXamlUICommand_ExecuteRequested(sender: XamlUICommand, args: ExecuteRequestedEventArgs) {
        XamlUICommandOutput.text = "You fired the custom command"
        FrameworkElementAutomationPeer.fromElement(CustomButton)?.raiseNotificationEvent(
            AutomationNotificationKind.ActionCompleted,
            AutomationNotificationProcessing.ImportantMostRecent,
            "Activated custom XAML UI Command",
            "CustomXamlUICommandNotificationActivityId",
        )
    }
}
