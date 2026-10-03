// Ported from WinUI Gallery Converters/MenuItemTemplateSelector.cs (MIT).
package io.github.composefluent.winrt.gallery.converters

import io.github.composefluent.winrt.gallery.models.*
import io.github.composefluent.winrt.runtime.WinRTXamlContentProperty
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.DataTemplateSelector
import io.github.composefluent.winrt.runtime.asWinRT
import windows.foundation.Uri

@WinRTXamlContentProperty("ItemTemplate")
internal class MenuItemTemplateSelector : DataTemplateSelector() {
    var ItemTemplate: DataTemplate? = null
    private val templates = ResourceDictionary().apply {
        source = Uri("ms-appx:///io/github/composefluent/winrt/gallery/converters/MenuItemTemplates.xaml")
    }
    private val HeaderTemplate: DataTemplate get() = checkNotNull(templates["HeaderTemplate"]).asWinRT<DataTemplate>()
    private val SeparatorTemplate: DataTemplate get() = checkNotNull(templates["SeparatorTemplate"]).asWinRT<DataTemplate>()
    override fun selectTemplateCore(item: Any?): DataTemplate? = when (item) { is Separator -> SeparatorTemplate; is Header -> HeaderTemplate; else -> ItemTemplate }
    override fun selectTemplateCore(item: Any?, container: DependencyObject): DataTemplate? = selectTemplateCore(item)
}
