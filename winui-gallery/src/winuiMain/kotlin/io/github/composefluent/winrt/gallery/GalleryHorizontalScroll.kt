// Ported from WinUI Gallery Controls/HorizontalScrollContainer (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*

internal fun galleryHorizontalScroll(content: UIElement): Grid = Grid().apply {
    val scroller = ScrollViewer().apply {
        horizontalScrollMode = ScrollMode.Enabled
        verticalScrollMode = ScrollMode.Disabled
        horizontalScrollBarVisibility = ScrollBarVisibility.Hidden
        verticalScrollBarVisibility = ScrollBarVisibility.Hidden
        this.content = Grid().apply {
            margin = Thickness(36.0, 0.0, 36.0, 0.0)
            children!!.add(content)
        }
    }
    fun arrow(left: Boolean) = Button().apply {
        width = 16.0; minWidth = 0.0; height = 38.0; padding = inset(0.0)
        margin = if (left) Thickness(8.0, -16.0, 0.0, 0.0) else Thickness(0.0, -16.0, 8.0, 0.0)
        horizontalAlignment = if (left) HorizontalAlignment.Left else HorizontalAlignment.Right
        verticalAlignment = VerticalAlignment.Center
        cornerRadius = corners(4.0); borderThickness = inset(1.0)
        this.content = glyph(if (left) "\uEDD9" else "\uEDDA", 8.0)
        val name = if (left) "Scroll left" else "Scroll right"
        named(this, name); ToolTipService.setToolTip(this, name)
        // Keep the native Button's keyboard, pointer and accessibility behavior,
        // supplying the same state brushes as the Gallery's scroll template.
        listOf("", "PointerOver", "Pressed").forEach { state ->
            resources!!["ButtonBackground$state"] = GalleryTheme.brush("FlipViewNextPreviousButtonBackground$state")
            resources!!["ButtonBorderBrush$state"] = GalleryTheme.brush("FlipViewNextPreviousButtonBorderBrush$state")
            resources!!["ButtonForeground$state"] = GalleryTheme.brush("FlipViewNextPreviousArrowForeground$state")
        }
    }
    val back = arrow(true)
    val forward = arrow(false)
    fun update(offset: Double = scroller.horizontalOffset) {
        back.visibility = if (offset >= 1.0) Visibility.Visible else Visibility.Collapsed
        forward.visibility = if (offset < scroller.scrollableWidth - 1.0) Visibility.Visible else Visibility.Collapsed
    }
    back.click.add { _, _ ->
        scroller.changeView(scroller.horizontalOffset - scroller.viewportWidth, null, null)
        forward.focus(FocusState.Programmatic)
    }
    forward.click.add { _, _ ->
        scroller.changeView(scroller.horizontalOffset + scroller.viewportWidth, null, null)
        back.focus(FocusState.Programmatic)
    }
    scroller.viewChanging.add { _, args -> update(checkNotNull(args.finalView).horizontalOffset) }
    scroller.sizeChanged.add { _, _ -> update() }
    scroller.loaded.add { _, _ -> update() }
    children!!.add(scroller); children!!.add(back); children!!.add(forward)
    update()
}
