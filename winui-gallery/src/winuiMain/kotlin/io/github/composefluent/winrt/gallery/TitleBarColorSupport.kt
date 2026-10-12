// Ported from WinUI Gallery Samples/AppWindowTitleBar (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.windowing.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.MicaBackdrop
import windows.graphics.SizeInt32
import windows.ui.Color

internal fun colorSelector(initial: Color, changed: (Color) -> Unit): Button {
    val swatch = Border().apply { width = 40.0; height = 20.0; background = microsoft.ui.xaml.media.SolidColorBrush(initial) }
    val picker = ColorPicker().apply {
        color = initial
        isAlphaEnabled = true
        colorChanged.add { _, args ->
            swatch.background = microsoft.ui.xaml.media.SolidColorBrush(args!!.newColor)
            changed(args.newColor)
        }
    }
    val popup = Flyout().apply { content = picker }
    return Button().apply { content = swatch; flyout = popup }
}
