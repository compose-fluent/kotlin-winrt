// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.layouts

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import windows.foundation.*
import kotlin.math.*

internal class ActivityFeedLayout : VirtualizingLayout() {
    var RowSpacing: Double
        get() = getValue(RowSpacingProperty) as Double
        set(value) { setValue(RowSpacingProperty, value) }
    var ColumnSpacing: Double
        get() = getValue(ColumnSpacingProperty) as Double
        set(value) { setValue(ColumnSpacingProperty, value) }
    var MinItemSize: Size
        get() = getValue(MinItemSizeProperty) as Size
        set(value) { setValue(MinItemSizeProperty, value) }
    private val contextBounds = mutableMapOf<VirtualizingLayoutContext, MutableMap<Int, Rect>>()
    override fun initializeForContextCore(context: VirtualizingLayoutContext) { super.initializeForContextCore(context); contextBounds[context] = linkedMapOf() }
    override fun uninitializeForContextCore(context: VirtualizingLayoutContext) { contextBounds.remove(context); super.uninitializeForContextCore(context) }
    override fun measureOverride(context: VirtualizingLayoutContext, availableSize: Size): Size {
        val bounds = contextBounds.getOrPut(context) { linkedMapOf() }; bounds.clear()
        if (context.itemCount == 0) return Size(availableSize.width, 0f)
        val minimum = MinItemSize.let { if (it.width == 0f && it.height == 0f) context.getOrCreateElementAt(0).let { element -> element.measure(Size(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)); element.desiredSize } else it }
        val height = minimum.height; val step = height + RowSpacing.toFloat()
        val rows = (context.itemCount + 2) / 3
        val first = max(0, (context.realizationRect.y / step).toInt() - 1)
        val last = min(rows, ((context.realizationRect.y + context.realizationRect.height) / step).toInt() + 1)
        val width = max(minimum.width, (availableSize.width - ColumnSpacing.toFloat() * 3) / 4)
        for (row in first until last) {
            var x = 0f
            for (column in 0..2) {
                val index = row * 3 + column; if (index >= context.itemCount) break
                val wide = if (row % 2 == 0) column == 2 else column == 0
                val itemWidth = if (wide) width * 2 + ColumnSpacing.toFloat() else width
                val rect = Rect(x, row * step, itemWidth, height); bounds[index] = rect
                context.getOrCreateElementAt(index).measure(Size(itemWidth, height)); x += itemWidth + ColumnSpacing.toFloat()
            }
        }
        return Size(width * 4 + ColumnSpacing.toFloat() * 2, (rows - 1) * step + height)
    }
    override fun arrangeOverride(context: VirtualizingLayoutContext, finalSize: Size): Size { contextBounds[context]?.forEach { (index, rect) -> context.getOrCreateElementAt(index).arrange(rect) }; return finalSize }
    companion object {
        private fun Changed(owner: DependencyObject, args: DependencyPropertyChangedEventArgs) { owner.asWinRT<ActivityFeedLayout>().invalidateMeasure() }
        val RowSpacingProperty: DependencyProperty = DependencyProperty.register("RowSpacing", Double::class, ActivityFeedLayout::class, PropertyMetadata(0.0, ::Changed))
        val ColumnSpacingProperty: DependencyProperty = DependencyProperty.register("ColumnSpacing", Double::class, ActivityFeedLayout::class, PropertyMetadata(0.0, ::Changed))
        val MinItemSizeProperty: DependencyProperty = DependencyProperty.register("MinItemSize", Size::class, ActivityFeedLayout::class, PropertyMetadata(Size(0f, 0f), ::Changed))
    }
}
