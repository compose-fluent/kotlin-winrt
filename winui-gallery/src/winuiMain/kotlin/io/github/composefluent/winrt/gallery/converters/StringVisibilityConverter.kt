package io.github.composefluent.winrt.gallery.converters
import microsoft.ui.xaml.Visibility
import microsoft.ui.xaml.data.IValueConverter
import kotlin.reflect.KClass
internal class StringVisibilityConverter : IValueConverter {
    var EmptyValue: Visibility = Visibility.Collapsed
    var NotEmptyValue: Visibility = Visibility.Visible
    override fun convert(value: Any?,targetType: KClass<*>?,parameter: Any?,language: String): Any? = if (value == null || value.toString().isEmpty()) EmptyValue else NotEmptyValue
    override fun convertBack(value: Any?,targetType: KClass<*>?,parameter: Any?,language: String): Any? = error("Visibility conversion is one-way")
}
