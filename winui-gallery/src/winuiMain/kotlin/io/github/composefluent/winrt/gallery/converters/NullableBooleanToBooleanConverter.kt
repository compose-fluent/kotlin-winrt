package io.github.composefluent.winrt.gallery.converters

import microsoft.ui.xaml.data.IValueConverter
import kotlin.reflect.KClass

internal class NullableBooleanToBooleanConverter : IValueConverter {
    override fun convert(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? = value == true
    override fun convertBack(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? = value == true
}
