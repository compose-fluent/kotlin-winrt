// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.hosting.ElementCompositionPreview
import microsoft.ui.xaml.media.CompositionTarget
import microsoft.ui.composition.*
import windows.foundation.numerics.Vector2

/** Same visual-surface mask as Gallery Controls/OpacityMaskView.xaml.cs. */
internal class OpacityMaskView : ContentControl() {
    var OpacityMask: UIElement?
        get() = getValue(OpacityMaskProperty) as? UIElement
        set(value) { setValue(OpacityMaskProperty,value) }
    private var maskBrush: CompositionMaskBrush? = null
    private var captureBrush: CompositionSurfaceBrush? = null
    private var templateRoot: Grid? = null
    private val objects = mutableListOf<CompositionObject>()
    init { defaultStyleKey = OpacityMaskView::class }
    override fun onApplyTemplate() {
        super.onApplyTemplate()
        templateRoot?.let { ElementCompositionPreview.setElementChildVisual(it,null) }
        objects.asReversed().forEach { it.close() }; objects.clear()
        val root = checkNotNull(getTemplateChild("PART_RootGrid")).asWinRT<Grid>()
        val mask = checkNotNull(getTemplateChild("PART_MaskContainer")).asWinRT<Border>()
        val presenter = checkNotNull(getTemplateChild("PART_ContentPresenter")).asWinRT<ContentPresenter>()
        val compositor = CompositionTarget.getCompositorForCurrentThread()
        val brush = compositor.createMaskBrush().also { objects.add(it) }
        brush.source = visualBrush(presenter)
        captureBrush = visualBrush(mask)
        brush.mask = if (OpacityMask == null) null else captureBrush
        maskBrush = brush
        val sprite = compositor.createSpriteVisual().also { objects.add(it) }
        sprite.relativeSizeAdjustment = Vector2(1f,1f)
        sprite.brush = brush
        templateRoot = root
        ElementCompositionPreview.setElementChildVisual(root,sprite)
    }
    private fun visualBrush(element: UIElement): CompositionSurfaceBrush {
        val visual = ElementCompositionPreview.getElementVisual(element)
        val compositor = checkNotNull(visual.compositor)
        val surface = compositor.createVisualSurface().also { objects.add(it) }
        surface.sourceVisual = visual
        val size = compositor.createExpressionAnimation("visual.Size").also { objects.add(it) }
        size.setReferenceParameter("visual",visual)
        surface.startAnimation("SourceSize",size)
        visual.opacity = 0f
        return compositor.createSurfaceBrush(surface).also { objects.add(it) }
    }
    companion object {
        val OpacityMaskProperty: DependencyProperty = DependencyProperty.register("OpacityMask",UIElement::class,OpacityMaskView::class,
            PropertyMetadata(null,PropertyChangedCallback { sender,_ ->
                checkNotNull(sender).asWinRT<OpacityMaskView>().apply { maskBrush?.mask = if (OpacityMask == null) null else captureBrush }
            }))
    }
}
