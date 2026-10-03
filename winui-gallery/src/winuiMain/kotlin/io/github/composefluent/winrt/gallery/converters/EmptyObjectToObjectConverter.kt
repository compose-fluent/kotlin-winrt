package io.github.composefluent.winrt.gallery.converters
import microsoft.ui.xaml.data.IValueConverter
import kotlin.reflect.KClass
internal class EmptyObjectToObjectConverter : IValueConverter {
    var EmptyValue: Any? = null
    var NotEmptyValue: Any? = null
    override fun convert(value: Any?,targetType: KClass<*>?,parameter: Any?,language: String): Any? = if (value == null) EmptyValue else NotEmptyValue
    override fun convertBack(value: Any?,targetType: KClass<*>?,parameter: Any?,language: String): Any? = error("Empty-object conversion is one-way")
}
