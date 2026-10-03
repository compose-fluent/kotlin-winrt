// Licensed to the .NET Foundation under the MIT license.
package io.github.composefluent.winrt.gallery.toolkit

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.interop.*
import windows.foundation.*
import kotlin.math.*

/** Port of CommunityToolkit/Windows components/Primitives/src/WrapLayout. */
internal class WrapLayout : VirtualizingLayout() {
    var HorizontalSpacing: Double
        get() = getValue(HorizontalSpacingProperty) as Double
        set(value) { setValue(HorizontalSpacingProperty, value) }
    var VerticalSpacing: Double
        get() = getValue(VerticalSpacingProperty) as Double
        set(value) { setValue(VerticalSpacingProperty, value) }
    var Orientation: microsoft.ui.xaml.controls.Orientation
        get() = getValue(OrientationProperty) as microsoft.ui.xaml.controls.Orientation
        set(value) { setValue(OrientationProperty, value) }
    private data class Uv(val u: Double = 0.0, val v: Double = 0.0)
    private data class Item(val index: Int, var element: UIElement? = null, var measure: Uv? = null, var position: Uv? = null)
    private class State {
        val items = mutableListOf<Item>()
        var orientation = microsoft.ui.xaml.controls.Orientation.Horizontal
        var spacing = Uv()
        var available = 0.0
        fun item(index: Int): Item { while (items.size <= index) items.add(Item(items.size)); return items[index] }
        fun removeFrom(index: Int) { if (index in 0 until items.size) items.subList(index, items.size).clear() }
        fun height(): Double {
            var last: Uv? = null; var height = 0.0
            for (item in items.asReversed()) {
                val position = item.position ?: continue; val measure = item.measure ?: continue
                if (last != null && last.v > position.v) break
                last = position; height = max(height, measure.v)
            }
            return (last?.v ?: 0.0) + height
        }
    }
    // Private managed cache is owned by its layout context and removed on teardown;
    // it is not an authored public LayoutState WinRT type.
    private val states = mutableMapOf<VirtualizingLayoutContext, State>()
    private fun uv(size: Size) = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) Uv(size.width.toDouble(), size.height.toDouble()) else Uv(size.height.toDouble(), size.width.toDouble())
    private fun size(value: Uv) = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) Size(value.u.toFloat(), value.v.toFloat()) else Size(value.v.toFloat(), value.u.toFloat())
    private fun rect(position: Uv, measure: Uv) = if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) Rect(position.u.toFloat(), position.v.toFloat(), measure.u.toFloat(), measure.v.toFloat()) else Rect(position.v.toFloat(), position.u.toFloat(), measure.v.toFloat(), measure.u.toFloat())
    private fun bounds(context: VirtualizingLayoutContext): Pair<Double, Double> = context.realizationRect.let { if (Orientation == microsoft.ui.xaml.controls.Orientation.Horizontal) it.y.toDouble() to (it.y + it.height).toDouble() else it.x.toDouble() to (it.x + it.width).toDouble() }
    override fun initializeForContextCore(context: VirtualizingLayoutContext) { states[context] = State(); super.initializeForContextCore(context) }
    override fun uninitializeForContextCore(context: VirtualizingLayoutContext) { states.remove(context); super.uninitializeForContextCore(context) }
    override fun onItemsChangedCore(context: VirtualizingLayoutContext, source: Any?, args: NotifyCollectionChangedEventArgs) {
        val state = states.getOrPut(context, ::State)
        when (args.action) {
            NotifyCollectionChangedAction.Add -> state.removeFrom(args.newStartingIndex)
            NotifyCollectionChangedAction.Remove -> state.removeFrom(args.oldStartingIndex)
            NotifyCollectionChangedAction.Replace -> { state.removeFrom(args.newStartingIndex); context.recycleElement(context.getOrCreateElementAt(args.newStartingIndex)) }
            NotifyCollectionChangedAction.Move -> { state.removeFrom(min(args.newStartingIndex, args.oldStartingIndex)); context.recycleElement(context.getOrCreateElementAt(args.oldStartingIndex)); context.recycleElement(context.getOrCreateElementAt(args.newStartingIndex)) }
            NotifyCollectionChangedAction.Reset -> state.items.clear()
        }
        super.onItemsChangedCore(context, source, args)
    }
    override fun measureOverride(context: VirtualizingLayoutContext, availableSize: Size): Size {
        val state = states.getOrPut(context, ::State)
        val parent = uv(availableSize); val spacing = uv(Size(HorizontalSpacing.toFloat(), VerticalSpacing.toFloat()))
        if (state.orientation != Orientation) {
            state.items.forEach { it.measure = it.measure?.let { m -> Uv(m.v, m.u) }; it.position = null }
            state.orientation = Orientation; state.available = 0.0
        }
        if (state.spacing != spacing || state.available != parent.u) {
            state.items.forEach { it.position = null }; state.spacing = spacing; state.available = parent.u
        }
        var currentV = 0.0; var position = Uv()
        val (minimum, maximum) = bounds(context)
        for (index in 0 until context.itemCount) {
            val item = state.item(index); var measured = false
            if (item.measure == null) {
                item.element = context.getOrCreateElementAt(index).also { it.measure(availableSize) }
                item.measure = uv(checkNotNull(item.element).desiredSize); measured = true
            }
            var measure = checkNotNull(item.measure)
            if (item.position == null) {
                if (parent.u < position.u + measure.u) { position = Uv(0.0, position.v + currentV + spacing.v); currentV = 0.0 }
                item.position = position
            }
            position = checkNotNull(item.position)
            if (position.v + measure.v < minimum || position.v > maximum) {
                item.element?.let(context::recycleElement); item.element = null
                if (position.v > maximum) break
                continue
            }
            if (!measured) {
                item.element = context.getOrCreateElementAt(index).also { it.measure(availableSize) }
                measure = uv(checkNotNull(item.element).desiredSize)
                if (measure != item.measure) {
                    state.removeFrom(index + 1); item.measure = measure
                    if (parent.u < position.u + measure.u) { position = Uv(0.0, position.v + currentV + spacing.v); currentV = 0.0 }
                    item.position = position
                }
            }
            position = Uv(position.u + measure.u + spacing.u, position.v); currentV = max(measure.v, currentV)
        }
        return size(Uv(if (parent.u.isInfinite()) 0.0 else ceil(parent.u), state.height()))
    }
    override fun arrangeOverride(context: VirtualizingLayoutContext, finalSize: Size): Size {
        val state = states[context] ?: return finalSize
        val (minimum, maximum) = bounds(context)
        for (index in 0 until context.itemCount) {
            val item = state.item(index); val position = item.position ?: break; val measure = item.measure ?: break
            if (minimum <= position.v + measure.v && position.v <= maximum) context.getOrCreateElementAt(index).arrange(rect(position, measure))
            else if (position.v > maximum) break
        }
        return finalSize
    }
    companion object {
        private fun Changed(owner: DependencyObject, args: DependencyPropertyChangedEventArgs) { owner.asWinRT<WrapLayout>().apply { invalidateMeasure(); invalidateArrange() } }
        val HorizontalSpacingProperty: DependencyProperty = DependencyProperty.register("HorizontalSpacing", Double::class, WrapLayout::class, PropertyMetadata(0.0, ::Changed))
        val VerticalSpacingProperty: DependencyProperty = DependencyProperty.register("VerticalSpacing", Double::class, WrapLayout::class, PropertyMetadata(0.0, ::Changed))
        val OrientationProperty: DependencyProperty = DependencyProperty.register("Orientation", microsoft.ui.xaml.controls.Orientation::class, WrapLayout::class, PropertyMetadata(microsoft.ui.xaml.controls.Orientation.Horizontal, ::Changed))
    }
}
