// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.collections

import io.github.composefluent.winrt.runtime.WinRTObservableList
import microsoft.ui.xaml.DataTemplate
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.interop.INotifyCollectionChanged
import microsoft.ui.xaml.interop.NotifyCollectionChangedEventHandler

internal class Bar(val Length: Double, val MaxLength: Int) {
    val Height: Double = Length / 4
    val MaxHeight: Double = MaxLength / 4.0
    val Diameter: Double = Length / 6
    val MaxDiameter: Double = MaxLength / 6.0
}
internal class NestedCategory(val CategoryName: String, items: List<String>) {
    val CategoryItems: MutableList<String> = WinRTObservableList(items)
}
internal class MyItemsSource(items: List<Recipe>) : AbstractMutableList<Recipe>(), IKeyIndexMapping, INotifyCollectionChanged {
    private val inner: WinRTObservableList<Recipe> = WinRTObservableList(items)
    override val size: Int get() = inner.size
    override fun get(index: Int): Recipe = inner[index]
    override fun set(index: Int, element: Recipe): Recipe = inner.set(index, element)
    override fun add(index: Int, element: Recipe) { inner.add(index, element) }
    override fun removeAt(index: Int): Recipe = inner.removeAt(index)
    override fun keyFromIndex(index: Int): String = inner[index].Num.toString()
    override fun indexFromKey(key: String): Int = inner.indexOfFirst { it.Num.toString() == key }
    override fun addCollectionChanged(handler: NotifyCollectionChangedEventHandler) { inner.addCollectionChanged(handler) }
    override fun removeCollectionChanged(handler: NotifyCollectionChangedEventHandler) { inner.removeCollectionChanged(handler) }
    fun InitializeCollection(items: List<Recipe>) { inner.clear(); inner.addAll(items) }
}
internal class MyDataTemplateSelector : DataTemplateSelector() {
    var Normal: DataTemplate? = null
    var Accent: DataTemplate? = null
    override fun selectTemplateCore(item: Any?): DataTemplate? = if ((item as Int) % 2 == 0) Normal else Accent
}
internal class StringOrIntTemplateSelector : DataTemplateSelector() {
    var StringTemplate: DataTemplate? = null
    var IntTemplate: DataTemplate? = null
    override fun selectTemplateCore(item: Any?): DataTemplate? = when (item) { is String -> StringTemplate; is Int -> IntTemplate; else -> null }
}
