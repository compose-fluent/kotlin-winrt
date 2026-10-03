// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.layouts

import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventArgs
import windows.foundation.*
import kotlin.math.*

internal class VariedImageSizeLayout : VirtualizingLayout() {
    var Width: Double = 150.0
    private val bounds = mutableListOf<Rect>()
    private var offsets = floatArrayOf(0f)
    private var lastWidth = -1f
    private var first = 0
    private var last = -1
    override fun onItemsChangedCore(context: VirtualizingLayoutContext, source: Any?, args: NotifyCollectionChangedEventArgs) { bounds.clear(); offsets = floatArrayOf(); first = 0; last = -1; lastWidth = -1f; invalidateMeasure() }
    override fun measureOverride(context: VirtualizingLayoutContext, availableSize: Size): Size {
        val width = availableSize.width
        val itemWidth = Width.toFloat()
        if (width != lastWidth || offsets.isEmpty()) {
            offsets = FloatArray(max(1, (width / itemWidth).toInt()))
            bounds.indices.forEach { index -> val column = offsets.indices.minBy { offsets[it] }; val height = bounds[index].height; bounds[index] = Rect(column * itemWidth, offsets[column], itemWidth, height); offsets[column] += height }
            lastWidth = width
        }
        val viewport = context.realizationRect; val bottom = viewport.y + viewport.height
        first = bounds.indexOfFirst { it.y < bottom && it.y + it.height > viewport.y }.takeIf { it >= 0 } ?: 0
        last = first - 1
        var nextOffset = -1f
        var index = first
        while (index < context.itemCount && nextOffset < bottom) {
            val child = context.getOrCreateElementAt(index); child.measure(Size(itemWidth, availableSize.height))
            if (index >= bounds.size) { val column = offsets.indices.minBy { offsets[it] }; nextOffset = offsets[column]; bounds.add(Rect(column * itemWidth, nextOffset, itemWidth, child.desiredSize.height)); offsets[column] += child.desiredSize.height }
            else nextOffset = bounds.getOrNull(index + 1)?.y ?: offsets.min()
            last = index++
        }
        return Size(width, offsets.max())
    }
    override fun arrangeOverride(context: VirtualizingLayoutContext, finalSize: Size): Size { for (index in first..last) context.getOrCreateElementAt(index).arrange(bounds[index]); return finalSize }
}
