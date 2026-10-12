// Ported from WinUI Gallery SampleSupport/SamplePages/SamplePage1 through SamplePage6 (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.shapes.Ellipse

internal const val sampleLorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

internal fun sampleContent(number: Int, connected: (UIElement) -> Unit = {}): UIElement {
    fun tile(color: UInt, row: Int, column: Int, span: Int = 1, margin: Double = 5.0) = Grid().apply {
        minHeight = 150.0; this.margin = inset(margin); background = brush(color)
        Grid.setRow(this, row); Grid.setColumn(this, column); Grid.setRowSpan(this, span)
    }
    fun grid(weights: List<Double>, rows: Int) = Grid().apply {
        weights.forEach { columnDefinitions!!.add(column(it, GridUnitType.Star)) }; repeat(rows) { rowDefinitions!!.add(autoRow()) }
    }
    fun prose(row: Int, margin: Thickness = inset(5.0)) = label(sampleLorem).apply { Grid.setRow(this, row); Grid.setColumnSpan(this, 3); this.margin = margin }
    val panel = when (number) {
        2 -> Grid().apply {
            columnDefinitions!!.add(column(1.0, GridUnitType.Auto)); columnDefinitions!!.add(column(1.0, GridUnitType.Star))
            repeat(2) { rowDefinitions!!.add(autoRow()) }
            children!!.add(Grid().apply {
                Grid.setRow(this, 1); width = 150.0; height = 200.0; minHeight = 150.0; margin = inset(12.0)
                verticalAlignment = VerticalAlignment.Top; background = GalleryTheme.brush("AccentFillColorDefaultBrush")
                connected(this)
            })
            children!!.add(stack(0.0) {
                Grid.setRow(this, 1); Grid.setColumn(this, 1); minHeight = 200.0; margin = inset(12.0)
                children!!.add(label("Lorem ipsum dolor sit amet, consectetur adipiscing elit", 28.0).apply { margin = Thickness(0.0, 0.0, 0.0, 12.0) })
                children!!.add(label(sampleLorem))
            })
        }
        3 -> grid(listOf(2.0, 1.0, 1.0), 4).apply {
            children!!.add(tile(0xD3D3D3u, 1, 0, 2)); children!!.add(tile(0xA9A9A9u, 1, 1)); children!!.add(tile(0x808080u, 2, 1))
            children!!.add(tile(0xD3D3D3u, 1, 2)); children!!.add(tile(0xA9A9A9u, 2, 2)); children!!.add(prose(3))
        }
        4 -> stack(0.0) {
            children!!.add(grid(listOf(2.0, 1.0, 1.0), 1).apply {
                children!!.add(tile(0xE9967Au, 0, 0)); children!!.add(tile(0x8B0000u, 0, 1)); children!!.add(tile(0xF08080u, 0, 2))
            })
            children!!.add(grid(listOf(1.0, 1.0, 2.0), 3).apply {
                children!!.add(tile(0xF08080u, 1, 0)); children!!.add(tile(0x8B0000u, 1, 1)); children!!.add(tile(0xCD5C5Cu, 1, 2)); children!!.add(prose(2))
            })
        }
        5 -> grid(listOf(1.0, 1.0, 4.0), 4).apply {
            children!!.add(tile(0xF0E68Cu, 0, 0)); children!!.add(tile(0xBDB76Bu, 0, 1))
            children!!.add(Ellipse().apply { width = 150.0; height = 150.0; fill = brush(0x8FBC8Fu); Grid.setColumn(this, 2) })
            children!!.add(Ellipse().apply { width = 75.0; height = 75.0; fill = brush(0x3CB371u); Grid.setRow(this, 1); Grid.setColumnSpan(this, 2) })
            children!!.add(tile(0x556B2Fu, 1, 2)); children!!.add(prose(3))
        }
        6 -> grid(listOf(1.0, 1.0, 4.0), 4).apply {
            listOf(Triple(0, 0, 20.0 to 0x48D1CCu), Triple(0, 1, 50.0 to 0x4682B4u),
                Triple(1, 0, 50.0 to 0xB0E0E6u), Triple(1, 1, 20.0 to 0x48D1CCu)).forEach { (row, column, style) ->
                children!!.add(Ellipse().apply {
                    width = style.first; height = width; fill = brush(style.second)
                    Grid.setRow(this, row); Grid.setColumn(this, column)
                })
            }
            children!!.add(tile(0x87CEEBu, 0, 2)); children!!.add(tile(0x4682B4u, 1, 2)); children!!.add(prose(3))
        }
        else -> Grid().apply {
            columnDefinitions!!.add(column(1.0, GridUnitType.Auto)); repeat(2) { columnDefinitions!!.add(column(1.0, GridUnitType.Star)) }
            repeat(3) { rowDefinitions!!.add(autoRow()) }; rowDefinitions!!.add(starRow())
            children!!.add(tile(0xA9A9A9u, 1, 1, margin = 6.0)); children!!.add(tile(0xD3D3D3u, 1, 2, margin = 6.0))
            children!!.add(tile(0xD3D3D3u, 2, 1, margin = 6.0)); children!!.add(tile(0xA9A9A9u, 2, 2, margin = 6.0))
            children!!.add(Grid().apply { Grid.setRow(this, 1); Grid.setRowSpan(this, 2); minWidth = 250.0; minHeight = 150.0; margin = inset(5.0); background = GalleryTheme.brush("AccentFillColorDefaultBrush"); connected(this) })
            children!!.add(prose(3, Thickness(6.0, 12.0, 6.0, 12.0)))
        }
    }
    return scroll(panel)
}
