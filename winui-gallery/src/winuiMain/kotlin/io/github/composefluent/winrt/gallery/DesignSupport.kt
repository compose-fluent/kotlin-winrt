// Ported from WinUI Gallery Samples/{Geometry,Spacing,Typography} (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.imaging.BitmapImage
import windows.applicationmodel.datatransfer.Clipboard
import windows.applicationmodel.datatransfer.DataPackage
import windows.foundation.Uri

internal fun copyButton(value: String) = Button("Copy to clipboard") { Clipboard.setContent(DataPackage().apply { setText(value) }) }.apply {
    content = glyph("\uE8C8"); named(this, "Copy $value to clipboard"); ToolTipService.setToolTip(this, "Copy to clipboard")
}

internal fun themedDesignImage(name: String, height: Double = Double.NaN) = Image().apply {
    this.height = height
    fun update() { source = BitmapImage(Uri("ms-appx:///Assets/Design/$name.${if (actualTheme == ElementTheme.Light) "light" else "dark"}.png")) }
    loaded.add { _, _ -> update() }; actualThemeChanged.add { _, _ -> update() }
}

internal fun horizontalScroll(child: UIElement) = ScrollViewer().apply {
    content = child; horizontalScrollMode = ScrollMode.Auto; horizontalScrollBarVisibility = ScrollBarVisibility.Auto; verticalScrollBarVisibility = ScrollBarVisibility.Hidden
}

internal fun designRow(widths: List<Double>, shaded: Boolean, vararg cells: FrameworkElement): Grid = Grid().apply {
    minHeight = 48.0; cornerRadius = corners(4.0)
    if (shaded) background = GalleryTheme.brush("CardBackgroundFillColorDefaultBrush")
    widths.forEach { value -> columnDefinitions!!.add(column(if (value < 0) 1.0 else value, if (value < 0) GridUnitType.Star else GridUnitType.Pixel)) }
    columnDefinitions!!.add(column(1.0, GridUnitType.Auto))
    cells.forEachIndexed { index, cell -> Grid.setColumn(cell, index); children!!.add(cell) }
}

internal fun annotation(canvas: Canvas, x: Double, y: Double, title: String, subtitle: String = "") {
    val info = Button().apply { content = glyph("\uE946"); padding = inset(4.0); Canvas.setLeft(this, x); Canvas.setTop(this, y); Canvas.setZIndex(this, 1); named(this, "Show $title"); ToolTipService.setToolTip(this, title) }
    val tip = TeachingTip().apply { this.title = title; this.subtitle = subtitle; target = info }
    info.click.add { _, _ -> tip.isOpen = !tip.isOpen }
    canvas.children!!.add(info); canvas.children!!.add(tip)
}
