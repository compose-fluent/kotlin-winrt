package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.UserControl
import windows.applicationmodel.datatransfer.Clipboard
import windows.applicationmodel.datatransfer.DataPackage

internal class TypographyControl : UserControl() {
    var Example: String
        get() = (getValue(ExampleProperty) as? String).orEmpty()
        set(value) { setValue(ExampleProperty, value) }
    var Weight: String
        get() = (getValue(WeightProperty) as? String).orEmpty()
        set(value) { setValue(WeightProperty, value) }
    var VariableFont: String
        get() = (getValue(VariableFontProperty) as? String).orEmpty()
        set(value) { setValue(VariableFontProperty, value) }
    var SizeLineHeight: String
        get() = (getValue(SizeLineHeightProperty) as? String).orEmpty()
        set(value) { setValue(SizeLineHeightProperty, value) }
    var ResourceName: String
        get() = (getValue(ResourceNameProperty) as? String).orEmpty()
        set(value) { setValue(ResourceNameProperty, value) }
    var ExampleStyle: Style?
        get() = getValue(ExampleStyleProperty)?.asWinRT<Style>()
        set(value) { setValue(ExampleStyleProperty, value) }
    private fun CopyToClipboardButton_Click(sender: Any?, args: RoutedEventArgs) { Clipboard.setContent(DataPackage().apply { setText(ResourceName) }) }
    companion object {
        val ExampleProperty = DependencyProperty.register("Example", String::class, TypographyControl::class, PropertyMetadata(""))
        val WeightProperty = DependencyProperty.register("Weight", String::class, TypographyControl::class, PropertyMetadata(""))
        val VariableFontProperty = DependencyProperty.register("VariableFont", String::class, TypographyControl::class, PropertyMetadata(""))
        val SizeLineHeightProperty = DependencyProperty.register("SizeLineHeight", String::class, TypographyControl::class, PropertyMetadata(""))
        val ResourceNameProperty = DependencyProperty.register("ResourceName", String::class, TypographyControl::class, PropertyMetadata(""))
        val ExampleStyleProperty = DependencyProperty.register("ExampleStyle", Style::class, TypographyControl::class, PropertyMetadata(null))
    }
}
