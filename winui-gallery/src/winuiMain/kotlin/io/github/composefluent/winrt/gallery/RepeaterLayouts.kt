// Adapted from WinUI Gallery Layouts/ActivityFeedLayout.cs and VariedImageSizeLayout.cs (MIT).
package io.github.composefluent.winrt.gallery

import microsoft.ui.xaml.controls.VirtualizingLayout
import microsoft.ui.xaml.controls.VirtualizingLayoutContext
import windows.foundation.Rect
import windows.foundation.Size
import kotlin.math.max
import kotlin.math.min

/** One instance per repeater: only the rows intersecting its realization rectangle are created. */
class GalleryActivityFeedLayout : VirtualizingLayout() {
    private val bounds = linkedMapOf<Int, Rect>()

    override fun measureOverride(context: VirtualizingLayoutContext?, availableSize: Size): Size { requireNotNull(context);
        bounds.clear()
        val width = if (availableSize.width.isFinite()) availableSize.width else 520f
        val tileWidth = max(80f, (width - 36f) / 4f)
        val rows = (context.itemCount + 2) / 3
        val viewport = context.realizationRect
        val first = max(0, (viewport.y / 120f).toInt() - 1)
        val last = min(rows, ((viewport.y + viewport.height) / 120f).toInt() + 2)
        for (row in first until last) {
            var x = 0f
            for (column in 0..2) {
                val index = row * 3 + column
                if (index >= context.itemCount) break
                val wide = if (row % 2 == 0) column == 2 else column == 0
                val itemWidth = if (wide) tileWidth * 2f + 12f else tileWidth
                val rect = Rect(x, row * 120f, itemWidth, 108f)
                context.getOrCreateElementAt(index)!!.measure(Size(itemWidth, 108f))
                bounds[index] = rect
                x += itemWidth + 12f
            }
        }
        return Size(tileWidth * 4f + 36f, max(0f, rows * 120f - 12f))
    }

    override fun arrangeOverride(context: VirtualizingLayoutContext?, finalSize: Size): Size { requireNotNull(context);
        bounds.forEach { (index, rect) -> context.getOrCreateElementAt(index)!!.arrange(rect) }
        return finalSize
    }
}

/** Masonry layout with measured bounds cached as scrolling discovers additional recipes. */
class GalleryRecipeLayout : VirtualizingLayout() {
    private val bounds = mutableListOf<Rect>()
    private var offsets = floatArrayOf(0f)
    private var lastWidth = -1f
    private var first = 0
    private var last = -1

    fun reset() {
        bounds.clear()
        offsets.fill(0f)
        first = 0; last = -1
        invalidateMeasure()
    }

    override fun measureOverride(context: VirtualizingLayoutContext?, availableSize: Size): Size { requireNotNull(context);
        val width = if (availableSize.width.isFinite()) availableSize.width else 400f
        if (width != lastWidth) {
            offsets = FloatArray(max(1, (width / 200f).toInt()))
            bounds.indices.forEach { index ->
                val column = offsets.indices.minBy { offsets[it] }
                val height = bounds[index].height
                bounds[index] = Rect(column * 200f, offsets[column], 200f, height)
                offsets[column] += height
            }
            lastWidth = width
        }
        val viewport = context.realizationRect
        val bottom = viewport.y + viewport.height
        first = bounds.indexOfFirst { it.y < bottom && it.y + it.height > viewport.y }
            .takeIf { it >= 0 } ?: bounds.size
        last = first - 1
        var index = first
        while (index < context.itemCount) {
            val nextY = bounds.getOrNull(index)?.y ?: offsets.min()
            if (nextY >= bottom && index > first) break
            val child = context.getOrCreateElementAt(index)
            child!!.measure(Size(200f, Float.POSITIVE_INFINITY))
            if (index >= bounds.size) {
                val column = offsets.indices.minBy { offsets[it] }
                bounds.add(Rect(column * 200f, offsets[column], 200f, child.desiredSize.height))
                offsets[column] += child.desiredSize.height
            }
            last = index++
        }
        return Size(width, offsets.max())
    }

    override fun arrangeOverride(context: VirtualizingLayoutContext?, finalSize: Size): Size { requireNotNull(context);
        for (index in first..last) context.getOrCreateElementAt(index)!!.arrange(bounds[index])
        return finalSize
    }
}
