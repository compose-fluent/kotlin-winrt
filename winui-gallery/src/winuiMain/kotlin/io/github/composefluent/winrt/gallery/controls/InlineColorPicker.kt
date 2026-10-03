package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.gallery.brush
import io.github.composefluent.winrt.gallery.rgb
import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.SolidColorBrush
import windows.ui.Color

internal class InlineColorPicker : UserControl() {
    private var ready = false
    private val handlers: MutableList<windows.foundation.EventHandler<Color>> = mutableListOf()
    var Header: String
        get() = getValue(HeaderProperty) as? String ?: ""
        set(value) { setValue(HeaderProperty, value) }
    var Color: Color
        get() = getValue(ColorProperty) as Color
        set(value) { ColorBrush = SolidColorBrush(value); setValue(ColorProperty, value) }
    var ColorBrush: SolidColorBrush
        get() = checkNotNull(getValue(ColorBrushProperty)).asWinRT<SolidColorBrush>()
        set(value) { setValue(ColorBrushProperty, value) }
    fun addColorChanged(handler: windows.foundation.EventHandler<Color>) { handlers.add(handler) }
    fun removeColorChanged(handler: windows.foundation.EventHandler<Color>) { handlers.remove(handler) }
    override fun initializeComponent() {
        super.initializeComponent(); ready = true
        loaded.add { _, _ -> ColorHex.text = format(Color) }
    }
    private fun format(value: Color): String = "#" + (if (value.a == 255.toUByte()) listOf(value.r, value.g, value.b) else listOf(value.a, value.r, value.g, value.b)).joinToString("") { it.toInt().toString(16).padStart(2, '0').uppercase() }
    fun GetSolidColorBrush(hex: String): SolidColorBrush = SolidColorBrush(
        microsoft.ui.xaml.markup.XamlBindingHelper.convertValue(Color::class, hex) as Color)
    private fun Picker_ColorChanged(sender: ColorPicker, args: ColorChangedEventArgs) {
        if (!ready) return
        Color = args.newColor; ColorPreview.fill = ColorBrush; ColorHex.text = format(Color)
        handlers.toList().forEach { it(this, Color) }
    }
    private fun PickerFlyout_Opened(sender: Any?, args: Any?) { Picker.color = checkNotNull(ColorPreview.fill).asWinRT<SolidColorBrush>().color }
    private fun ColorHex_TextChanged(sender: Any?, args: TextChangedEventArgs) {
        if (!ready) return
        val text = ColorHex.text.removePrefix("#")
        if (text.length !in listOf(6, 8) || text.toUIntOrNull(16) == null) return
        Color = GetSolidColorBrush("#$text").color; ColorPreview.fill = ColorBrush; handlers.toList().forEach { it(this, Color) }
    }
    companion object {
        val HeaderProperty: DependencyProperty = DependencyProperty.register("Header", String::class, InlineColorPicker::class, PropertyMetadata(""))
        val ColorProperty: DependencyProperty = DependencyProperty.register("Color", Color::class, InlineColorPicker::class, PropertyMetadata(rgb(0xFFFFFFu)))
        val ColorBrushProperty: DependencyProperty = DependencyProperty.register("ColorBrush", SolidColorBrush::class, InlineColorPicker::class, PropertyMetadata(brush(0xFFFFFFu)))
    }
}
