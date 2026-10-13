package io.github.composefluent.winrt.samples.uwp

import windows.ui.xaml.RoutedEventArgs
import windows.ui.xaml.controls.Page

class MainPage : Page() {
    val greeting: String = "Kotlin/WinRT UWP"
    private var clicks = 0

    override fun initializeComponent() {
        super.initializeComponent()
        sampleLog("XAML loaded; named button=${incrementButton.content}")
    }

    private fun onIncrement(sender: Any?, args: RoutedEventArgs?) {
        clicks++
        resultText.text = "Clicked $clicks times"
        sampleLog("Click $clicks; text=${resultText.text}")
    }
}
