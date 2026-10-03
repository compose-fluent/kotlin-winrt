// Licensed to the .NET Foundation under the MIT license.
package io.github.composefluent.winrt.gallery.toolkit

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import windows.foundation.*
import kotlin.math.max

internal enum class StretchChild { None, Last }

/** Port of CommunityToolkit/Windows components/Primitives/src/WrapPanel/WrapPanel.cs. */
internal class WrapPanel : Panel() {
    var HorizontalSpacing: Double
        get() = getValue(HorizontalSpacingProperty) as Double
        set(value) { setValue(HorizontalSpacingProperty,value) }
    var VerticalSpacing: Double
        get() = getValue(VerticalSpacingProperty) as Double
        set(value) { setValue(VerticalSpacingProperty,value) }
    var Orientation: microsoft.ui.xaml.controls.Orientation
        get() = getValue(OrientationProperty) as microsoft.ui.xaml.controls.Orientation
        set(value) { setValue(OrientationProperty,value) }
    var Padding: Thickness
        get() = getValue(PaddingProperty) as Thickness
        set(value) { setValue(PaddingProperty,value) }
    var StretchChild: io.github.composefluent.winrt.gallery.toolkit.StretchChild
        get() = getValue(StretchChildProperty) as io.github.composefluent.winrt.gallery.toolkit.StretchChild
        set(value) { setValue(StretchChildProperty,value) }
    private data class Placement(val child: UIElement, val u: Double, val v: Double, val width: Double)
    private class Row { val children = mutableListOf<Placement>(); var height = 0.0 }
    private val rows = mutableListOf<Row>()
    private fun uv(width: Double,height: Double): Pair<Double,Double> = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) width to height else height to width
    private fun size(u: Double,v: Double): Size = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) Size(u.toFloat(),v.toFloat()) else Size(v.toFloat(),u.toFloat())
    override fun measureOverride(availableSize: Size): Size {
        val available = Size(max(0.0,availableSize.width-Padding.left-Padding.right).toFloat(),max(0.0,availableSize.height-Padding.top-Padding.bottom).toFloat())
        children.forEach { it.measure(available) }
        return UpdateRows(availableSize)
    }
    override fun arrangeOverride(finalSize: Size): Size {
        if ((Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal && finalSize.width < desiredSize.width) ||
            (Orientation == microsoft.ui.xaml.controls.Orientation.Vertical && finalSize.height < desiredSize.height)) UpdateRows(finalSize)
        rows.forEach { row -> row.children.forEach { placement ->
            val bounds = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal)
                Rect(placement.u.toFloat(),placement.v.toFloat(),placement.width.toFloat(),row.height.toFloat())
            else Rect(placement.v.toFloat(),placement.u.toFloat(),row.height.toFloat(),placement.width.toFloat())
            placement.child.arrange(bounds)
        } }
        return finalSize
    }
    private fun UpdateRows(available: Size): Size {
        rows.clear()
        val start = uv(Padding.left,Padding.top); val end = uv(Padding.right,Padding.bottom)
        if (children.isEmpty()) return size(start.first+end.first,start.second+end.second)
        val parent = uv(available.width.toDouble(),available.height.toDouble()); val spacing = uv(HorizontalSpacing,VerticalSpacing)
        var u = start.first; var v = start.second; var row = Row(); var requiredU = 0.0
        children.forEachIndexed { index,child ->
            if (child.visibility != Visibility.Collapsed) {
                val desired = uv(child.desiredSize.width.toDouble(),child.desiredSize.height.toDouble())
                if (desired.first+u+end.first > parent.first || u >= parent.first) {
                    u = start.first; v += row.height+spacing.second; rows.add(row); row = Row()
                }
                val width = if (index == children.lastIndex && StretchChild == io.github.composefluent.winrt.gallery.toolkit.StretchChild.Last && parent.first.isFinite()) parent.first-u else desired.first
                row.children.add(Placement(child,u,v,width)); row.height = max(row.height,desired.second)
                u += width+spacing.first; requiredU = max(requiredU,u)
            }
        }
        if (row.children.isNotEmpty()) rows.add(row)
        if (rows.isEmpty()) return size(start.first+end.first,start.second+end.second)
        val last = rows.last()
        val requiredV = last.children.firstOrNull()?.v ?: v
        return size(requiredU+end.first,requiredV+last.height+end.second)
    }
    companion object {
        private val changed = PropertyChangedCallback { value,_ -> checkNotNull(value).asWinRT<WrapPanel>().apply { invalidateMeasure(); invalidateArrange() } }
        val HorizontalSpacingProperty: DependencyProperty = DependencyProperty.register("HorizontalSpacing",Double::class,WrapPanel::class,PropertyMetadata(0.0,changed))
        val VerticalSpacingProperty: DependencyProperty = DependencyProperty.register("VerticalSpacing",Double::class,WrapPanel::class,PropertyMetadata(0.0,changed))
        val OrientationProperty: DependencyProperty = DependencyProperty.register("Orientation",microsoft.ui.xaml.controls.Orientation::class,WrapPanel::class,PropertyMetadata(microsoft.ui.xaml.controls.Orientation.Horizontal,changed))
        val PaddingProperty: DependencyProperty = DependencyProperty.register("Padding",Thickness::class,WrapPanel::class,PropertyMetadata(Thickness(0.0,0.0,0.0,0.0),changed))
        val StretchChildProperty: DependencyProperty = DependencyProperty.register("StretchChild",io.github.composefluent.winrt.gallery.toolkit.StretchChild::class,WrapPanel::class,PropertyMetadata(io.github.composefluent.winrt.gallery.toolkit.StretchChild.None,changed))
    }
}
