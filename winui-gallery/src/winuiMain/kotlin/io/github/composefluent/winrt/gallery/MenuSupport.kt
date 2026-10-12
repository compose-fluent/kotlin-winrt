// Ported from WinUI Gallery Samples/{AppBarButton,AppBarToggleButton,AppBarSeparator,MenuBar,MenuFlyout} (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.input.KeyboardAccelerator
import microsoft.ui.xaml.media.*
import windows.foundation.Point
import windows.system.VirtualKey
import windows.system.VirtualKeyModifiers

internal fun menuItem(title: String, action: () -> Unit = {}) = MenuFlyoutItem().apply { text = title; click.add { _, _ -> action() } }
internal fun shortcut(key: VirtualKey, modifiers: VirtualKeyModifiers = VirtualKeyModifiers.Control) = KeyboardAccelerator().apply { this.key = key; this.modifiers = modifiers }
internal fun appCommand(title: String, symbol: Symbol, action: () -> Unit = {}) = AppBarButton().apply { label = title; icon = SymbolIcon(symbol); click.add { _, _ -> action() } }
internal fun samplePath() = PathIcon().apply {
    data = PathGeometry().apply {
        fillRule = FillRule.Nonzero
        figures!!.add(PathFigure().apply {
            startPoint = Point(20f, 20f)
            listOf(Point(24f, 10f), Point(24f, 24f), Point(5f, 24f)).forEach { next -> segments!!.add(LineSegment().apply { point = next }) }
        })
    }
}
