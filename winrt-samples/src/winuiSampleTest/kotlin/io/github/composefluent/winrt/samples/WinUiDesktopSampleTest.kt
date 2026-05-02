package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.Application
import microsoft.ui.xaml.DependencyProperty
import microsoft.ui.xaml.ResourceDictionary
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.ComboBox
import microsoft.ui.xaml.controls.ListView
import microsoft.ui.xaml.controls.Page
import microsoft.ui.xaml.controls.Slider
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBox
import microsoft.ui.xaml.controls.ToggleSwitch
import microsoft.ui.xaml.controls.XamlControlsResources
import microsoft.ui.xaml.markup.XamlReader
import microsoft.ui.xaml.media.MicaBackdrop
import org.junit.Assert.assertEquals
import org.junit.Test

class WinUiDesktopSampleTest {
    @Test
    fun winui_desktop_sample_uses_generated_winui_projection_surface() {
        assertEquals("Microsoft.UI.Xaml.Application", Application.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Window", Window.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.Button", Button.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.Page", Page.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.UIElement", UIElement.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.DependencyProperty", DependencyProperty.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.ResourceDictionary", ResourceDictionary.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.XamlControlsResources", XamlControlsResources.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Media.MicaBackdrop", MicaBackdrop.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.StackPanel", StackPanel.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.TextBox", TextBox.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.ToggleSwitch", ToggleSwitch.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.Slider", Slider.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.ComboBox", ComboBox.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Controls.ListView", ListView.Metadata.TYPE_NAME)
        assertEquals("Microsoft.UI.Xaml.Markup.XamlReader", XamlReader.Metadata.TYPE_NAME)
        assertEquals(false, shouldRunWinUiSmoke())
    }
}
