// HomePageHeader and OpacityMaskView composition adapted from WinUI Gallery (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.composition.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.hosting.ElementCompositionPreview
import microsoft.ui.xaml.media.LinearGradientBrush
import microsoft.ui.xaml.media.GradientStop
import microsoft.ui.xaml.media.Stretch
import microsoft.ui.xaml.media.imaging.BitmapImage
import windows.foundation.Uri
import windows.foundation.Point
import windows.foundation.numerics.Vector2
import windows.ui.Color
import windows.ui.viewmanagement.AccessibilitySettings

internal fun galleryHero() = Grid().apply {
    height = 400.0; verticalAlignment = VerticalAlignment.Top; isHitTestVisible = false
    val image = Image().apply {
        margin = Thickness(0.0, -100.0, 0.0, 0.0)
        source = BitmapImage(Uri("ms-appx:///Assets/GalleryHeaderImage.png")); stretch = Stretch.UniformToFill
    }
    val source = Grid().apply { children!!.add(image) }
    children!!.add(source)
    val accessibility = AccessibilitySettings()
    var surface: CompositionVisualSurface? = null
    var surfaceBrush: CompositionSurfaceBrush? = null
    var opacityMask: CompositionLinearGradientBrush? = null
    var maskedBrush: CompositionMaskBrush? = null
    var sprite: SpriteVisual? = null
    // The element owns this empty host across unload/reload. Child resources
    // are removed before closing, so no closed visual remains in the tree.
    var host: ContainerVisual? = null
    fun refresh() {
        val dark = actualTheme == ElementTheme.Dark
        source.background = if (accessibility.highContrast) brush(0x000000u) else if (dark) brush(0x020B20u) else LinearGradientBrush().apply {
            startPoint = Point(0.5f, 0f); endPoint = Point(0.5f, 1f)
            gradientStops!!.add(GradientStop().apply { offset = 0.0; color = rgb(0xCED8E4u) })
            gradientStops!!.add(GradientStop().apply { offset = 1.0; color = rgb(0xD5DBE3u) })
        }
        image.opacity = if (dark || accessibility.highContrast) 0.8 else 0.9
        opacityMask?.let { gradient ->
            val compositor = checkNotNull(gradient.compositor)
            val stops = checkNotNull(gradient.colorStops)
            stops.clear()
            listOf(0f to 255u, (if (accessibility.highContrast) 0.55f else 0.75f) to 255u,
                (if (accessibility.highContrast) 0.95f else 0.85f) to 0u, 1f to 0u).forEach { (offset, alpha) ->
                stops.add(compositor.createColorGradientStop(offset, Color(alpha.toUByte(), 255u, 255u, 255u)))
            }
        }
    }
    fun release() {
        host?.children?.removeAll()
        ElementCompositionPreview.getElementVisual(source).opacity = 1f
        sprite?.close(); sprite = null
        maskedBrush?.close(); maskedBrush = null
        opacityMask?.close(); opacityMask = null
        surfaceBrush?.close(); surfaceBrush = null
        surface?.close(); surface = null
    }
    loaded.add { _, _ ->
        release()
        val visual = ElementCompositionPreview.getElementVisual(source)
        val compositor = checkNotNull(visual.compositor)
        val visualHost = host ?: compositor.createContainerVisual().also {
            it.relativeSizeAdjustment = Vector2(1f, 1f)
            ElementCompositionPreview.setElementChildVisual(this, it)
            host = it
        }
        val createdSurface = compositor.createVisualSurface().apply { sourceVisual = visual; sourceSize = source.actualSize }
        surface = createdSurface
        surfaceBrush = compositor.createSurfaceBrush(createdSurface)
        opacityMask = compositor.createLinearGradientBrush().apply { startPoint = Vector2(0.5f, 0f); endPoint = Vector2(0.5f, 1f) }
        maskedBrush = compositor.createMaskBrush().apply { this.source = surfaceBrush; mask = opacityMask }
        val createdSprite = compositor.createSpriteVisual().apply { relativeSizeAdjustment = Vector2(1f, 1f); brush = maskedBrush }
        sprite = createdSprite
        visual.opacity = 0f
        checkNotNull(visualHost.children).insertAtTop(createdSprite)
        refresh()
    }
    source.sizeChanged.add { _, _ -> surface?.sourceSize = source.actualSize }
    actualThemeChanged.add { _, _ -> refresh() }
    GalleryTheme.observe(this, ::refresh)
    unloaded.add { _, _ -> release() }
    refresh()
}
