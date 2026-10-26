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
          fun unbound(target: FrameworkElement, property: DependencyProperty?, name: String, protectedProperties: Set<String>): Boolean =
            name !in protectedProperties && property != null && target.getBindingExpression(property) == null
          return WinUIXamlStateAdapter(capture = capture@ { value, protectedProperties ->
            val control = runCatching { value.asWinRT<Control>() }.getOrNull() ?: return@capture null
            val textBox = runCatching { value.asWinRT<TextBox>() }.getOrNull()
            val toggleSwitch = runCatching { value.asWinRT<ToggleSwitch>() }.getOrNull()
            val toggleButton = runCatching { value.asWinRT<ToggleButton>() }.getOrNull()
            val selector = runCatching { value.asWinRT<Selector>() }.getOrNull()
            val range = runCatching { value.asWinRT<RangeBase>() }.getOrNull()
            val scroll = runCatching { value.asWinRT<ScrollViewer>() }.getOrNull()
            var type = "Control"
            val values = buildList {
              fun put(name: String, data: Any?) { add(WinUIXamlVisualProperty(name, data?.toString() ?: "null")) }
              when {
                textBox != null -> {
                  type = "TextBox"
                  if (unbound(textBox, TextBox.textProperty, "Text", protectedProperties)) put("Text", textBox.text)
                  put("SelectionStart", textBox.selectionStart); put("SelectionLength", textBox.selectionLength)
                }
                toggleSwitch != null -> {
                  type = "ToggleSwitch"
                  if (unbound(toggleSwitch, ToggleSwitch.isOnProperty, "IsOn", protectedProperties)) put("IsOn", toggleSwitch.isOn)
                }
                toggleButton != null -> {
                  type = "ToggleButton"
                  if (unbound(toggleButton, ToggleButton.isCheckedProperty, "IsChecked", protectedProperties)) put("IsChecked", toggleButton.isChecked)
                }
                selector != null -> {
                  type = "Selector"
                  if (unbound(selector, Selector.selectedIndexProperty, "SelectedIndex", protectedProperties) && unbound(selector, Selector.selectedItemProperty, "SelectedItem", protectedProperties)) {
                  put("SelectedIndex", selector.selectedIndex); put("ItemCount", selector.items?.size ?: 0)
                }
                }
                range != null -> {
                  type = "RangeBase"
                  if (unbound(range, RangeBase.valueProperty, "Value", protectedProperties)) put("Value", range.value)
                }
                scroll != null -> {
                  type = "ScrollViewer"
                  put("HorizontalOffset", scroll.horizontalOffset); put("VerticalOffset", scroll.verticalOffset)
                  put("ZoomFactor", scroll.zoomFactor)
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
          }, restore = { value, state, protectedProperties ->
            val values = state.values.associate { it.name to it.value }
            fun finite(name: String): Double? = values[name]?.toDouble()?.also { require(it.isFinite()) }
            when (state.kind) {
              "TextBox" -> {
                val target = value.asWinRT<TextBox>()
                if (unbound(target, TextBox.textProperty, "Text", protectedProperties)) values["Text"]?.let { target.text = it }
                val start = (values["SelectionStart"]?.toInt() ?: 0).coerceIn(0, target.text.length)
                val length = (values["SelectionLength"]?.toInt() ?: 0).coerceIn(0, target.text.length - start)
                target.select(start, length)
              }
              "ToggleSwitch" -> {
                val target = value.asWinRT<ToggleSwitch>()
                if (unbound(target, ToggleSwitch.isOnProperty, "IsOn", protectedProperties))
                values["IsOn"]?.let { target.isOn = it.toBooleanStrict() }
              }
              "ToggleButton" -> {
                val target = value.asWinRT<ToggleButton>()
                if (unbound(target, ToggleButton.isCheckedProperty, "IsChecked", protectedProperties))
                values["IsChecked"]?.let { target.isChecked = if (it == "null") null else it.toBooleanStrict() }
              }
              "Selector" -> {
                val target = value.asWinRT<Selector>()
                if (unbound(target, Selector.selectedIndexProperty, "SelectedIndex", protectedProperties) && unbound(target, Selector.selectedItemProperty, "SelectedItem", protectedProperties) &&
                values["ItemCount"]?.toInt() == (target.items?.size ?: 0)) {
                values["SelectedIndex"]?.toInt()?.let { index ->
                  if (index >= -1 && index < (target.items?.size ?: 0)) target.selectedIndex = index
                }
              }
              }
              "RangeBase" -> {
                val target = value.asWinRT<RangeBase>()
                if (unbound(target, RangeBase.valueProperty, "Value", protectedProperties))
                finite("Value")?.let { target.value = it.coerceIn(target.minimum, target.maximum) }
              }
              "ScrollViewer" -> {
                val target = value.asWinRT<ScrollViewer>()
                target.updateLayout()
                val zoom = finite("ZoomFactor")?.toFloat()?.coerceIn(target.minZoomFactor, target.maxZoomFactor)
                target.changeView(finite("HorizontalOffset"), finite("VerticalOffset"), zoom, true)
              }
            }
            values["Focus"]?.let { focus -> value.asWinRT<Control>().focus(when (focus) {
              "Keyboard" -> FocusState.Keyboard
              "Pointer" -> FocusState.Pointer
              "Programmatic" -> FocusState.Programmatic
              else -> error("Invalid saved focus state.")
            }) }
          })
        }
    """.trimIndent())
}
