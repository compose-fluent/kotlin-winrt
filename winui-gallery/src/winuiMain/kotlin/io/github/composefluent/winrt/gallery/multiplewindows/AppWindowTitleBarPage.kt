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

@GalleryPage(route = "AppWindowTitleBar", title = "AppWindowTitleBar", group = "MultipleWindows", order = 1)
internal class AppWindowTitleBarPage : Page() {
    private var sampleWindow: AppWindowTitleBarWindow? = null
    private var extendWindow: AppWindowTitleBarExtendWindow? = null
    private var themeHeightWindow: AppWindowTitleBarThemeWindow? = null
    val titleBarThemes: List<TitleBarTheme> = listOf(TitleBarTheme.UseDefaultAppMode,TitleBarTheme.Light,TitleBarTheme.Dark)
    val titleBarHeightOptions: List<TitleBarHeightOption> = listOf(TitleBarHeightOption.Standard,TitleBarHeightOption.Tall,TitleBarHeightOption.Collapsed)
    fun ColorToArgbString(color: Color): String = "${color.a}, ${color.r}, ${color.g}, ${color.b}"
    fun BoolToLowerString(value: Boolean?): String = (value ?: false).toString()
    private fun Hyperlink_Click(sender: microsoft.ui.xaml.documents.Hyperlink, args: microsoft.ui.xaml.documents.HyperlinkClickEventArgs) { GalleryNavigationHost.navigate("TitleBar") }
    private fun ShowWindowButton_Click(sender: Any?, args: RoutedEventArgs) {
        ShowWindowButton.isEnabled = false
        sampleWindow = AppWindowTitleBarWindow().apply {
            Configure(listOf(Background.Color,Foreground.Color,ButtonBackground.Color,ButtonForeground.Color,ButtonHoverBackground.Color,ButtonHoverForeground.Color,ButtonInactiveBackground.Color,ButtonInactiveForeground.Color,InactiveBackground.Color,InactiveForeground.Color,ButtonPressedBackground.Color,ButtonPressedForeground.Color))
            closed.add { _, _ -> ShowWindowButton.isEnabled = true; sampleWindow = null }; activate()
        }
    }
    private fun ShowExtendButton_Click(sender: Any?, args: RoutedEventArgs) {
        ShowExtendButton.isEnabled = false
        extendWindow = AppWindowTitleBarExtendWindow().apply {
            Configure(ExtendContentCheckBox.isChecked == true,HeightComboBox.selectedItem as? TitleBarHeightOption ?: TitleBarHeightOption.Standard)
            closed.add { _, _ -> ShowExtendButton.isEnabled = true; extendWindow = null }; activate()
        }
    }
    private fun ExtendContentCheckBox_Checked(sender: Any?, args: RoutedEventArgs) { extendWindow?.appWindow?.titleBar?.extendsContentIntoTitleBar = true }
    private fun ExtendContentCheckBox_Unchecked(sender: Any?, args: RoutedEventArgs) { extendWindow?.appWindow?.titleBar?.extendsContentIntoTitleBar = false }
    private fun HeightComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { extendWindow?.appWindow?.titleBar?.let { if (it.extendsContentIntoTitleBar) it.preferredHeightOption = HeightComboBox.selectedItem as? TitleBarHeightOption ?: TitleBarHeightOption.Standard } }
    private fun ShowThemeButton_Click(sender: Any?, args: RoutedEventArgs) {
        ShowThemeHeightButton.isEnabled = false
        themeHeightWindow = AppWindowTitleBarThemeWindow().apply {
            Configure(ThemeComboBox.selectedItem as? TitleBarTheme ?: TitleBarTheme.UseDefaultAppMode)
            closed.add { _, _ -> ShowThemeHeightButton.isEnabled = true; themeHeightWindow = null }; activate()
        }
    }
    private fun ThemeComboBox_SelectionChanged(sender: Any?, args: SelectionChangedEventArgs) { themeHeightWindow?.appWindow?.titleBar?.preferredTheme = ThemeComboBox.selectedItem as? TitleBarTheme ?: TitleBarTheme.UseDefaultAppMode }
    private fun Background_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.backgroundColor = color }
    private fun Foreground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.foregroundColor = color }
    private fun ButtonBackground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonBackgroundColor = color }
    private fun ButtonForeground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonForegroundColor = color }
    private fun ButtonHoverBackground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonHoverBackgroundColor = color }
    private fun ButtonHoverForeground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonHoverForegroundColor = color }
    private fun ButtonInactiveBackground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonInactiveBackgroundColor = color }
    private fun ButtonInactiveForeground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonInactiveForegroundColor = color }
    private fun InactiveBackground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.inactiveBackgroundColor = color }
    private fun InactiveForeground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.inactiveForegroundColor = color }
    private fun ButtonPressedBackground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonPressedBackgroundColor = color }
    private fun ButtonPressedForeground_ColorChanged(sender: Any?, color: Color) { sampleWindow?.appWindow?.titleBar?.buttonPressedForegroundColor = color }

}
