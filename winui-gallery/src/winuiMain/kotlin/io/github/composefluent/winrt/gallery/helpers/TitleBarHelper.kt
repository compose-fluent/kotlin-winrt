// Ported from WinUI Gallery Helpers/TitleBarHelper.cs (MIT).
package io.github.composefluent.winrt.gallery.helpers

import io.github.composefluent.winrt.gallery.rgb
import microsoft.ui.xaml.*
import windows.ui.Color

internal object TitleBarHelper {
    fun ApplySystemThemeToCaptionButtons(window: Window, currentTheme: ElementTheme) {
        val dark = currentTheme == ElementTheme.Dark
        window.appWindow?.titleBar?.apply {
            buttonForegroundColor = if (dark) rgb(0xFFFFFFu) else rgb(0u)
            buttonHoverForegroundColor = if (dark) rgb(0xFFFFFFu) else rgb(0u)
            buttonHoverBackgroundColor = if (dark) Color(24u,255u,255u,255u) else Color(24u,0u,0u,0u)
        }
    }
}
