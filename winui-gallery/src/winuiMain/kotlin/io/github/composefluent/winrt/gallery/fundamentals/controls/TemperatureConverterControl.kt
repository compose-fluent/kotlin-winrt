// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.fundamentals.controls

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.documents.*
import microsoft.ui.xaml.automation.AutomationProperties
import microsoft.ui.xaml.automation.peers.*

internal class TemperatureConverterControl : UserControl() {
    private fun HasText(text: String): Boolean = text.isNotBlank()
    private fun Button_Click(sender: Any?, args: RoutedEventArgs) {
        val formatter = windows.globalization.numberformatting.DecimalFormatter().apply { fractionDigits = 2; integerDigits = 1 }
        ResultTextBlock.text = formatter.parseDouble(InputTextBox.text)?.let { "Fahrenheit: ${formatter.formatDouble(it * 9 / 5 + 32)}°F" } ?: "Invalid input!"
    }
}
