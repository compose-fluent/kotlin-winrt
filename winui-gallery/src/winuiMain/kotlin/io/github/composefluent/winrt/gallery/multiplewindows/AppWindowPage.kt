// Ported from WinUI Gallery v2.9.3 (MIT).
package io.github.composefluent.winrt.gallery.multiplewindows

import io.github.composefluent.winrt.gallery.*
import io.github.composefluent.winrt.gallery.samplepages.*
import io.github.composefluent.winrt.gallery.controls.ColorSelector
import io.github.composefluent.winrt.gallery.helpers.TitleBarHelper
import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.windowing.*
import windows.ui.Color

@GalleryPage(route = "AppWindow", title = "AppWindow", group = "MultipleWindows", order = 0)
internal class AppWindowPage : Page() {
    private var ready = false
    override fun initializeComponent() { super.initializeComponent(); ready = true; UpdateInitialSizeDescription() }
    private fun ShowSampleWindow1(sender: Any?, args: RoutedEventArgs) { SampleWindow1().apply { Configure(WindowTitle.text,WindowWidth.value.toInt(),WindowHeight.value.toInt(),XPoint.value.toInt(),YPoint.value.toInt()); activate() } }
    private fun ShowSampleWindow2(sender: Any?, args: RoutedEventArgs) { SampleWindow2().activate() }
    private fun ShowSampleWindow3(sender: Any?, args: RoutedEventArgs) { SampleWindow3().apply { Configure(IsAlwaysOnTop.isOn,IsMaximizable.isOn,IsMinimizable.isOn,IsResizable.isOn,HasBorder.isOn,HasTitleBar.isOn); activate() } }
    private fun ShowSampleWindow4(sender: Any?, args: RoutedEventArgs) { SampleWindow4().apply { Configure(MinWidthBox.value.toInt(),MinHeightBox.value.toInt(),MaxWidthBox.value.toInt(),MaxHeightBox.value.toInt()); activate() } }
    private fun ShowSampleWindow5(sender: Any?, args: RoutedEventArgs) { ModalWindow().Show(this) }
    private fun ShowSampleWindow6(sender: Any?, args: RoutedEventArgs) { SampleWindow6().activate() }
    private fun ShowSampleWindow7(sender: Any?, args: RoutedEventArgs) { SampleWindow7().apply { Configure(InitialSize.selectedItem.toString()); activate() } }
    private fun HasBorder_Toggled(sender: Any?, args: RoutedEventArgs) { if (ready && !HasBorder.isOn) HasTitleBar.isOn = false }
    private fun HasTitleBar_Toggled(sender: Any?, args: RoutedEventArgs) { if (ready && HasTitleBar.isOn) HasBorder.isOn = true }
    fun BoolToLowerString(value: Boolean): String = value.toString()
    private fun InitialSize_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { if (ready) UpdateInitialSizeDescription() }
    private fun UpdateInitialSizeDescription() {
        val size = InitialSize.selectedItem?.toString() ?: "Small"
        val percentage = when (size) { "Small" -> "5%"; "Medium" -> "15%"; "Large" -> "25%"; else -> "Unknown" }
        InitialSizeDescription.text = "$size: Window size is approximately $percentage of the display's work area."
    }

}
