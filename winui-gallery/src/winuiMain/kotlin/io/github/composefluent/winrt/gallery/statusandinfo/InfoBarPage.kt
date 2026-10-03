package io.github.composefluent.winrt.gallery.statusandinfo

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.controls.primitives.*
import microsoft.ui.xaml.media.*

@GalleryPage(route = "InfoBar", title = "InfoBar", group = "StatusAndInfo", order = 1)
internal class InfoBarPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; DisplayMessage.Value = "A long essential app message..."; DisplayButton.Value = "" }
    private fun SeverityComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        TestInfoBar1.severity = when (SeverityComboBox.selectedItem?.toString()) {
            "Error" -> InfoBarSeverity.Error; "Warning" -> InfoBarSeverity.Warning; "Success" -> InfoBarSeverity.Success; else -> InfoBarSeverity.Informational
        }
    }
    private fun MessageComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        val short = MessageComboBox.selectedIndex == 0
        TestInfoBar2.message = if (short) "A short essential app message." else
            "A long essential app message for your users to be informed of, acknowledge, or take action on. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Proin dapibus dolor vitae justo rutrum, ut lobortis nibh mattis. Aenean id elit commodo, semper felis nec."
        DisplayMessage.Value = if (short) TestInfoBar2.message else "A long essential app message..."
    }
    private fun ActionButtonComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) {
        if (!ready) return
        when (ActionButtonComboBox.selectedIndex) {
            1 -> { TestInfoBar2.actionButton = Button().apply { content = "Action" }; DisplayButton.Value = "<InfoBar.ActionButton>\n    <Button Content=\"Action\" />\n</InfoBar.ActionButton>" }
            2 -> { TestInfoBar2.actionButton = HyperlinkButton().apply { content = "Informational link"; navigateUri = windows.foundation.Uri("https://www.microsoft.com/") }; DisplayButton.Value = "<InfoBar.ActionButton>\n    <HyperlinkButton Content=\"Informational link\" NavigateUri=\"https://www.microsoft.com/\" />\n</InfoBar.ActionButton>" }
            else -> { TestInfoBar2.actionButton = null; DisplayButton.Value = "" }
        }
    }
}
