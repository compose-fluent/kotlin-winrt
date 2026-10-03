package io.github.composefluent.winrt.gallery.converters

import kotlin.reflect.KClass
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.`data`.IValueConverter

internal class DoubleToThicknessConverter : IValueConverter {
    override fun convert(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any =
        if (value is Double) Thickness(value, value, value, value) else false
    override fun convertBack(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? =
        throw UnsupportedOperationException("The original converter is one way")
}
