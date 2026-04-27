package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Application
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.DependencyProperty
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.UIElement
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.Window
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.Button
import io.github.kitectlab.winrt.projections.microsoft.ui.xaml.controls.Page
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
