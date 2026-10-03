// Port of CommunityToolkit/Windows components/Primitives/src/SwitchPresenter/Case.cs (MIT).
package io.github.composefluent.winrt.gallery.toolkit
import io.github.composefluent.winrt.runtime.WinRTXamlContentProperty
import microsoft.ui.xaml.*
@WinRTXamlContentProperty("Content")
internal class Case : DependencyObject() {
    var Content: Any?
        get() = getValue(ContentProperty) as Any?
        set(value) { setValue(ContentProperty, value) }
    var Value: Any?
        get() = getValue(ValueProperty) as Any?
        set(value) { setValue(ValueProperty, value) }
    var IsDefault: Boolean
        get() = getValue(IsDefaultProperty) as Boolean
        set(value) { setValue(IsDefaultProperty, value) }

    companion object {
        val ContentProperty: DependencyProperty = DependencyProperty.register("Content", Any::class, Case::class, PropertyMetadata(null))
        val ValueProperty: DependencyProperty = DependencyProperty.register("Value", Any::class, Case::class, PropertyMetadata(null))
        val IsDefaultProperty: DependencyProperty = DependencyProperty.register("IsDefault", Boolean::class, Case::class, PropertyMetadata(false))
    }
}
