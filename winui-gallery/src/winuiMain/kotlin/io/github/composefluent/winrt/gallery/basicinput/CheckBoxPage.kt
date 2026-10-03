package io.github.composefluent.winrt.gallery.basicinput

import io.github.composefluent.winrt.gallery.*
import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.controls.*

@GalleryPage(route = "CheckBox", title = "CheckBox", group = "BasicInput", order = 7)
internal class CheckBoxPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun onTwoStateClick(sender: Any?, args: RoutedEventArgs) {
        twoStateOutput.text = if (twoStateCheckBox.isChecked == true) "Checked" else "Unchecked"
    }

    private fun onThreeStateClick(sender: Any?, args: RoutedEventArgs) {
        threeStateOutput.text = when (threeStateCheckBox.isChecked) {
            true -> "Checked"
            false -> "Unchecked"
            null -> "Indeterminate"
        }
    }

    private fun onSelectAllClick(sender: Any?, args: RoutedEventArgs) {
        listOf(option1, option2, option3).forEach { it.isChecked = allOptions.isChecked != false }
    }

    private fun onOptionClick(sender: Any?, args: RoutedEventArgs) {
        val options = listOf(option1, option2, option3)
        allOptions.isChecked = when {
            options.all { it.isChecked == true } -> true
            options.none { it.isChecked == true } -> false
            else -> null
        }
    }
}
