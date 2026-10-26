package io.github.composefluent.winrt.compiler.xaml

import io.github.composefluent.winrt.runtime.XamlFramework
import io.github.composefluent.winrt.metadata.xamlKotlinNamespace
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/** SDK UI-state adaptation, separate from projection classification. Binding-owned
 * values and password contents are not overwritten by development state recovery. */
internal fun writeXamlStateAdapterSource(root: Path, name: String, framework: XamlFramework) {
    val sdk = framework.xamlKotlinNamespace
    val file = root.resolve("io/github/composefluent/winrt/generated/xaml/${name}State.kt")
    Files.createDirectories(file.parent)
    file.writeText("""
        package io.github.composefluent.winrt.generated.xaml
        import io.github.composefluent.winrt.runtime.*
        import $sdk.*
        import $sdk.controls.*
        import $sdk.controls.primitives.*

        internal fun ${name}State(): WinUIXamlStateAdapter {
          fun unbound(target: FrameworkElement, property: DependencyProperty?): Boolean =
            property != null && target.getBindingExpression(property) == null
          fun kind(value: Any): String? = when (value) {
            is TextBox -> "TextBox"
            is ToggleSwitch -> "ToggleSwitch"
            is ToggleButton -> "ToggleButton"
            is Selector -> "Selector"
            is RangeBase -> "RangeBase"
            is ScrollViewer -> "ScrollViewer"
            is Control -> "Control"
            else -> null
          }
          return WinUIXamlStateAdapter(capture = capture@ { value ->
            val type = kind(value) ?: return@capture null
            val control = value as Control
            val values = buildList {
              fun put(name: String, data: Any?) { add(WinUIXamlVisualProperty(name, data?.toString() ?: "null")) }
              when (value) {
                is TextBox -> {
                  if (unbound(value, TextBox.textProperty)) put("Text", value.text)
                  put("SelectionStart", value.selectionStart); put("SelectionLength", value.selectionLength)
                }
                is ToggleSwitch -> if (unbound(value, ToggleSwitch.isOnProperty)) put("IsOn", value.isOn)
                is ToggleButton -> if (unbound(value, ToggleButton.isCheckedProperty)) put("IsChecked", value.isChecked)
                is Selector -> if (unbound(value, Selector.selectedIndexProperty) && unbound(value, Selector.selectedItemProperty)) {
                  put("SelectedIndex", value.selectedIndex); put("ItemCount", value.items?.size ?: 0)
                }
                is RangeBase -> if (unbound(value, RangeBase.valueProperty)) put("Value", value.value)
                is ScrollViewer -> {
                  put("HorizontalOffset", value.horizontalOffset); put("VerticalOffset", value.verticalOffset)
                  put("ZoomFactor", value.zoomFactor)
                }
              }
              if (control.focusState != FocusState.Unfocused) put("Focus", when (control.focusState) {
                FocusState.Keyboard -> "Keyboard"
                FocusState.Pointer -> "Pointer"
                else -> "Programmatic"
              })
            }
            // Keep the type even for unfocused controls so focus can be restored
            // into a newly created instance. The registry omits empty snapshots.
            WinUIXamlSavedElement("", type, values)
          }, restore = { value, state ->
            require(kind(value) == state.kind) { "The saved control type no longer matches." }
            val values = state.values.associate { it.name to it.value }
            fun finite(name: String): Double? = values[name]?.toDouble()?.also { require(it.isFinite()) }
            when (value) {
              is TextBox -> {
                if (unbound(value, TextBox.textProperty)) values["Text"]?.let { value.text = it }
                val start = (values["SelectionStart"]?.toInt() ?: 0).coerceIn(0, value.text.length)
                val length = (values["SelectionLength"]?.toInt() ?: 0).coerceIn(0, value.text.length - start)
                value.select(start, length)
              }
              is ToggleSwitch -> if (unbound(value, ToggleSwitch.isOnProperty))
                values["IsOn"]?.let { value.isOn = it.toBooleanStrict() }
              is ToggleButton -> if (unbound(value, ToggleButton.isCheckedProperty))
                values["IsChecked"]?.let { value.isChecked = if (it == "null") null else it.toBooleanStrict() }
              is Selector -> if (unbound(value, Selector.selectedIndexProperty) && unbound(value, Selector.selectedItemProperty) &&
                values["ItemCount"]?.toInt() == (value.items?.size ?: 0)) {
                values["SelectedIndex"]?.toInt()?.let { index ->
                  if (index >= -1 && index < (value.items?.size ?: 0)) value.selectedIndex = index
                }
              }
              is RangeBase -> if (unbound(value, RangeBase.valueProperty))
                finite("Value")?.let { value.value = it.coerceIn(value.minimum, value.maximum) }
              is ScrollViewer -> {
                value.updateLayout()
                val zoom = finite("ZoomFactor")?.toFloat()?.coerceIn(value.minZoomFactor, value.maxZoomFactor)
                value.changeView(finite("HorizontalOffset"), finite("VerticalOffset"), zoom, true)
              }
            }
            values["Focus"]?.let { focus -> (value as Control).focus(when (focus) {
              "Keyboard" -> FocusState.Keyboard
              "Pointer" -> FocusState.Pointer
              "Programmatic" -> FocusState.Programmatic
              else -> error("Invalid saved focus state.")
            }) }
          })
        }
    """.trimIndent())
}
