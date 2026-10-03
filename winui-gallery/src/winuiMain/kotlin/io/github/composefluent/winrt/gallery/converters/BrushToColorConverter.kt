// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.converters

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.data.IValueConverter
import microsoft.ui.xaml.media.SolidColorBrush
import windows.ui.Color
import kotlin.reflect.KClass

internal class BrushToColorConverter : IValueConverter {
    override fun convert(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? =
        value?.let { runCatching { it.asWinRT<SolidColorBrush>().color }.getOrNull() } ?: Color(0u, 0u, 0u, 0u)
    override fun convertBack(value: Any?, targetType: KClass<*>?, parameter: Any?, language: String): Any? =
        throw UnsupportedOperationException("BrushToColorConverter only supports conversion to Color")
}
