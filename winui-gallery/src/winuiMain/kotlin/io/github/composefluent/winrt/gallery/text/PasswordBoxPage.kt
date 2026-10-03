package io.github.composefluent.winrt.gallery.text

import io.github.composefluent.winrt.gallery.GalleryPage
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.PasswordBox
import microsoft.ui.xaml.controls.PasswordRevealMode

@GalleryPage(route = "PasswordBox", title = "PasswordBox", group = "Text", order = 2)
internal class PasswordBoxPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun PasswordBox_PasswordChanged(sender: Any?, args: RoutedEventArgs) {
        val passwordBox = checkNotNull(sender).asWinRT<PasswordBox>()
        val invalid = passwordBox.password.isEmpty() || passwordBox.password == "Password"
        Control1Output.text = if (invalid) "'Password' is not allowed." else ""
        Control1Output.visibility = if (invalid) Visibility.Visible else Visibility.Collapsed
        if (passwordBox.password == "Password") passwordBox.password = ""
    }

    private fun RevealModeCheckbox_Changed(sender: Any?, args: RoutedEventArgs) {
        passworBoxWithRevealmode.passwordRevealMode =
            if (revealModeCheckBox.isChecked == true) PasswordRevealMode.Visible else PasswordRevealMode.Hidden
    }
}
