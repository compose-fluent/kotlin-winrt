// Copyright (c) Microsoft Corporation. Licensed under the MIT License.
package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.runtime.*
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.UserControl

@WinRTXamlContentProperty("ExampleContent")
internal class ColorPageExample : UserControl() {
    var Description: String
        get() = getValue(DescriptionProperty) as? String ?: ""
        set(value) { setValue(DescriptionProperty, value) }
    var Title: String
        get() = getValue(TitleProperty) as? String ?: ""
        set(value) { setValue(TitleProperty, value) }
    var ExampleContent: UIElement?
        get() = getValue(ExampleContentProperty)?.asWinRT<UIElement>()
        set(value) { setValue(ExampleContentProperty, value) }
    companion object {
        val DescriptionProperty: DependencyProperty = DependencyProperty.register("Description", String::class, ColorPageExample::class, PropertyMetadata(""))
        val TitleProperty: DependencyProperty = DependencyProperty.register("Title", String::class, ColorPageExample::class, PropertyMetadata(""))
        val ExampleContentProperty: DependencyProperty = DependencyProperty.register("ExampleContent", UIElement::class, ColorPageExample::class, PropertyMetadata(null))
    }
}
