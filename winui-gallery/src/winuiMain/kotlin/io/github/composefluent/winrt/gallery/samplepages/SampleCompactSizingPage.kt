package io.github.composefluent.winrt.gallery.samplepages

import microsoft.ui.xaml.controls.*

internal class SampleCompactSizingPage : Page() {
    val FirstName: TextBox get() = firstName
    val LastName: TextBox get() = lastName
    val Password: PasswordBox get() = password
    val ConfirmPassword: PasswordBox get() = confirmPassword
    val ChosenDate: DatePicker get() = chosenDate
    fun CopyState(page: SampleStandardSizingPage) {
        FirstName.text = page.FirstName.text; LastName.text = page.LastName.text
        Password.password = page.Password.password; ConfirmPassword.password = page.ConfirmPassword.password
        ChosenDate.date = page.ChosenDate.date
    }
}
