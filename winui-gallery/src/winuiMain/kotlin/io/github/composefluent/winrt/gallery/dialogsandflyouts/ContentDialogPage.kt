package io.github.composefluent.winrt.gallery.dialogsandflyouts

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.gallery.GalleryPageTasks
import io.github.composefluent.winrt.gallery.controlStyle
import io.github.composefluent.winrt.runtime.asWinRT
import io.github.composefluent.winrt.runtime.await
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.automation.peers.AutomationEvents
import microsoft.ui.xaml.automation.peers.FrameworkElementAutomationPeer
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ContentDialogButton
import microsoft.ui.xaml.controls.ContentDialogResult
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.TextBlock

@GalleryPage(route = "ContentDialog", title = "ContentDialog", group = "DialogsAndFlyouts", order = 0)
internal class ContentDialogPage : Page() {
    private val tasks = GalleryPageTasks(this)

    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun ShowDialog_Click(sender: Any?, args: RoutedEventArgs) {
        showDialog(checkNotNull(sender).asWinRT<Button>(), false, DialogResult)
    }

    private fun ShowDialogNoDefault_Click(sender: Any?, args: RoutedEventArgs) {
        showDialog(checkNotNull(sender).asWinRT<Button>(), true, DialogResultNoDefault)
    }

    private fun showDialog(button: Button, noDefault: Boolean, output: TextBlock) = tasks.launch {
        val dialog = ContentDialogExample().apply {
            xamlRoot = button.xamlRoot
            requestedTheme = button.actualTheme
            style = controlStyle("DefaultContentDialogStyle")
            title = if (noDefault) "Replace file?" else "Save your work?"
            primaryButtonText = if (noDefault) "Replace" else "Save"
            secondaryButtonText = if (noDefault) "Keep" else "Don't Save"
            closeButtonText = "Cancel"
            defaultButton = if (noDefault) ContentDialogButton.None else ContentDialogButton.Primary
            content = ContentDialogContent()
        }
        try {
            val result = dialog.showAsync().await()
            output.text = when (result) {
                ContentDialogResult.Primary -> if (noDefault) "User replaced the file" else "User saved their work"
                ContentDialogResult.Secondary -> if (noDefault) "User kept the file" else "User did not save their work"
                else -> "User cancelled the dialog"
            }
            val peer = FrameworkElementAutomationPeer.fromElement(output)
                ?: FrameworkElementAutomationPeer.createPeerForElement(output)
            peer?.raiseAutomationEvent(AutomationEvents.LiveRegionChanged)
        } finally {
            dialog.hide()
        }
    }
}
