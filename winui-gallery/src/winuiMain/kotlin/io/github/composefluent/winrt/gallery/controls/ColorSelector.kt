// Ported from WinUI Gallery Controls/ColorSelector.cs (MIT).
package io.github.composefluent.winrt.gallery.controls

import io.github.composefluent.winrt.runtime.asWinRT
import microsoft.ui.xaml.*
import microsoft.ui.xaml.controls.*
import microsoft.ui.xaml.media.SolidColorBrush
import windows.ui.Color
import windows.foundation.EventHandler

internal class ColorSelector : UserControl() {
    private var ready = false
    private val handlers = mutableListOf<EventHandler<Color>>()
    var Color: Color
        get() = getValue(ColorProperty) as Color
        set(value) { setValue(ColorProperty,value) }
    fun addColorChanged(handler: EventHandler<Color>) { handlers.add(handler) }
    fun removeColorChanged(handler: EventHandler<Color>) { handlers.remove(handler) }
    override fun initializeComponent() { super.initializeComponent(); ready = true; UpdateVisual(Color) }
    private fun ColorPicker_ColorChanged(sender: ColorPicker, args: ColorChangedEventArgs) { if (ready && Color != args.newColor) Color = args.newColor }
    private fun UpdateVisual(color: Color) { if (!ready) return; CurrentColor.background = SolidColorBrush(color); if (ColorPicker.color != color) ColorPicker.color = color }
    companion object {
        val ColorProperty: DependencyProperty = DependencyProperty.register("Color",Color::class,ColorSelector::class,PropertyMetadata(Color(0u,0u,0u,0u),PropertyChangedCallback { value,args ->
            val selector = checkNotNull(value).asWinRT<ColorSelector>(); val color = args.newValue as Color
            selector.UpdateVisual(color); selector.handlers.toList().forEach { it(selector,color) }
        }))
    }
}
