package io.github.composefluent.winrt.samples

import microsoft.ui.xaml.Application
import microsoft.ui.xaml.DependencyProperty
import microsoft.ui.xaml.UIElement
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.Page
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
        assertEquals(false, shouldRunWinUiSmoke())
    }
}
