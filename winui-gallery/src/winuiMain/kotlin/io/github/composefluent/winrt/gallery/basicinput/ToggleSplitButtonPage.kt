package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.text.MarkerType
import microsoft.ui.xaml.FocusState
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.Symbol
import microsoft.ui.xaml.controls.SymbolIcon
import microsoft.ui.xaml.controls.ToggleSplitButton
import microsoft.ui.xaml.controls.ToggleSplitButtonIsCheckedChangedEventArgs

@GalleryPage(route = "ToggleSplitButton", title = "ToggleSplitButton", group = "BasicInput", order = 6)
internal class ToggleSplitButtonPage : Page() {
    private var marker = MarkerType.Bullet

    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun BulletButton_Click(sender: Any?, args: RoutedEventArgs) {
        val button = checkNotNull(sender).asWinRT<Button>()
        val symbol = checkNotNull(button.content).asWinRT<SymbolIcon>().symbol
        marker = if (symbol == Symbol.List) MarkerType.Bullet else MarkerType.UppercaseRoman
        mySymbolIcon.symbol = symbol
        AutomationProperties.setName(myListButton, if (symbol == Symbol.List) "Bullets" else "Roman Numerals")
        myRichEditBox.document!!.selection!!.paragraphFormat!!.listType = marker
        myListButton.isChecked = true
        myListButton.flyout?.hide()
        myRichEditBox.focus(FocusState.Keyboard)
    }

    private fun MyListButton_IsCheckedChanged(sender: ToggleSplitButton, args: ToggleSplitButtonIsCheckedChangedEventArgs) {
        myRichEditBox.document!!.selection!!.paragraphFormat!!.listType =
            if (sender.isChecked) marker else MarkerType.None
    }
}
