// Licensed to the .NET Foundation under the MIT license.
package io.github.composefluent.winrt.gallery.converters

import microsoft.ui.xaml.*
import microsoft.ui.xaml.data.IValueConverter
import microsoft.ui.xaml.markup.XamlBindingHelper
import kotlin.reflect.KClass

/** CommunityToolkit EmptyObjectToObjectConverter + EmptyCollectionToObjectConverter. */
internal class EmptyCollectionToObjectConverter : DependencyObject(), IValueConverter {
    var EmptyValue: Any?
        get() = getValue(EmptyValueProperty)
        set(value) { setValue(EmptyValueProperty, value) }
    var NotEmptyValue: Any?
        get() = getValue(NotEmptyValueProperty)
        set(value) { setValue(NotEmptyValueProperty, value) }
    override fun convert(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? {
        var empty = (value as? Iterable<*>)?.iterator()?.hasNext() != true
        if (parameter == true || parameter?.toString().equals("true", true)) empty = !empty
        val result = if (empty) EmptyValue else NotEmptyValue
        return if (targetType == null || result == null) result else XamlBindingHelper.convertValue(targetType, result)
    }
    override fun convertBack(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? = throw UnsupportedOperationException()
    companion object {
        val EmptyValueProperty: DependencyProperty = DependencyProperty.register("EmptyValue", Any::class, EmptyCollectionToObjectConverter::class, PropertyMetadata(null))
        val NotEmptyValueProperty: DependencyProperty = DependencyProperty.register("NotEmptyValue", Any::class, EmptyCollectionToObjectConverter::class, PropertyMetadata(null))
    }
}
