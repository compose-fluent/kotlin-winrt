package io.github.composefluent.winrt.gallery.controls
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.UserControl
internal class Tile : UserControl() {
    var Title: String
        get() = getValue(TitleProperty) as? String ?: ""
        set(value) { setValue(TitleProperty, value) }
    var Description: String
        get() = getValue(DescriptionProperty) as? String ?: ""
        set(value) { setValue(DescriptionProperty, value) }
    var Link: String
        get() = getValue(LinkProperty) as? String ?: ""
        set(value) { setValue(LinkProperty, value) }
    var Source: Any?
        get() = getValue(SourceProperty) as Any?
        set(value) { setValue(SourceProperty, value) }
    companion object {
        val TitleProperty: DependencyProperty = DependencyProperty.register("Title", String::class, Tile::class, PropertyMetadata(""))
        val DescriptionProperty: DependencyProperty = DependencyProperty.register("Description", String::class, Tile::class, PropertyMetadata(""))
        val LinkProperty: DependencyProperty = DependencyProperty.register("Link", String::class, Tile::class, PropertyMetadata(""))
        val SourceProperty: DependencyProperty = DependencyProperty.register("Source", Any::class, Tile::class, PropertyMetadata(null))
    }
}
