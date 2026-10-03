package io.github.composefluent.winrt.gallery.controls

import microsoft.ui.xaml.DependencyObject
import microsoft.ui.xaml.media.SolidColorBrush

/** WinUI Gallery's independent substitution object; values are filled by compiled bindings. */
internal class ControlExampleSubstitution : DependencyObject() {
    private val listeners = mutableListOf<() -> Unit>()
    var Key: String = ""
    var Value: Any? = null
        set(value) { field = value; listeners.toList().forEach { it() } }
    var IsEnabled: Boolean = true
        set(value) { field = value; listeners.toList().forEach { it() } }

    fun ValueAsString(): String {
        if (!IsEnabled) return ""
        val value = Value ?: return ""
        (value as? SolidColorBrush)?.let { brush ->
            val color = brush.color
            return "#" + listOf(color.a, color.r, color.g, color.b).joinToString("") {
                it.toString(16).padStart(2, '0').uppercase()
            }
        }
        return when (value) {
            is Double -> if (value.isFinite() && value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            is Float -> if (value.isFinite() && value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
            is Boolean -> if (value) "True" else "False"
            else -> value.toString()
        }
    }

    internal fun addValueChanged(listener: () -> Unit) { listeners += listener }
    internal fun removeValueChanged(listener: () -> Unit) { listeners -= listener }
}
