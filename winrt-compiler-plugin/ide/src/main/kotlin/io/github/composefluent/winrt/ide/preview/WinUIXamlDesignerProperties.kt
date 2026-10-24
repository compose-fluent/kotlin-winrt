@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.*

@Composable
internal fun DesignerProperties(selection: DesignerSelection?, filter: String, apply: (Map<String, String?>, String) -> Unit) {
    val expansion = remember { mutableStateMapOf<String, Boolean>() }
    if (selection == null) { Text("Select a source element to edit its XAML properties."); return }
    var editing by remember(selection.pointer, selection.state?.pointer) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(if (selection.state == null) "XAML properties" else "Visual state setters")
        selection.properties.filter { it.writable && it.name.contains(filter, true) }
            .groupBy(::designerPropertyCategory).toSortedMap().forEach { (category, properties) ->
            val expanded = expansion[category] ?: (category == "Layout" || category == "Common")
            Link("${if (expanded || filter.isNotEmpty()) "▾" else "▸"} $category (${properties.size})", onClick = { expansion[category] = !expanded })
            if (expanded || filter.isNotEmpty()) properties.forEach { property ->
            key(property.name) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(property.name, Modifier.weight(1f), maxLines = 1)
                        Tooltip(tooltip = { Text(property.value ?: "Inherited or default; click to set a local value") }) {
                            Link((property.value ?: "Set value…").take(42), modifier = Modifier.widthIn(max = 160.dp), enabled = !property.elementValue,
                                onClick = { editing = if (editing == property.name) null else property.name })
                        }
                    }
                    if (property.elementValue) Text("Property element · edit in XAML")
                    if (editing == property.name) {
                        val input = remember(selection.text, selection.state?.sourceText, property.name) { TextFieldState(property.value.orEmpty()) }
                        val error = WinUIXamlDesignerEditing.validate(property, input.text.toString())
                        val latestApply by rememberUpdatedState(apply)
                        val commit = {
                            val value = input.text.toString()
                            if (WinUIXamlDesignerEditing.validate(property, value) == null)
                                latestApply(mapOf(property.name to value), "Set ${property.name}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreviewAction("Apply ${property.name}", "apply", enabled = error == null) { commit() }
                            PreviewAction("Reset ${property.name}", "reset", enabled = property.value != null) {
                                latestApply(mapOf(property.name to null), "Reset ${property.name}"); editing = null
                            }
                            PreviewAction("Cancel ${property.name}", "close") { editing = null }
                        }
                        if (property.choices.isNotEmpty()) Dropdown(menuContent = {
                            property.choices.forEach { value -> selectableItem(selected = input.text.toString() == value,
                                onClick = { input.setTextAndPlaceCursorAtEnd(value) }) { Text(value) } }
                        }) { Text(input.text.toString().ifEmpty { "Choose value" }) }
                        var expressionMode by remember(property.name) { mutableStateOf<String?>(null) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Link("Resource…", modifier = Modifier.heightIn(min = 28.dp), onClick = { expressionMode = "Resource" })
                            Link("Binding…", modifier = Modifier.heightIn(min = 28.dp), onClick = { expressionMode = "Binding" })
                            if (property.type.substringAfterLast('.') in setOf("Brush", "Color")) Link("Color…", modifier = Modifier.heightIn(min = 28.dp), onClick = {
                                javax.swing.SwingUtilities.invokeLater {
                                    val hex = input.text.toString().removePrefix("#")
                                    val initial = runCatching {
                                        require(hex.length == 6 || hex.length == 8)
                                        java.awt.Color(hex.toLong(16).toInt(), hex.length == 8)
                                    }.getOrDefault(java.awt.Color.BLACK)
                                    val chosen = javax.swing.JColorChooser.showDialog(null, "Choose ${property.name}", initial)
                                    if (chosen != null) input.setTextAndPlaceCursorAtEnd(
                                        if (chosen.alpha == 255) "#%02X%02X%02X".format(chosen.red, chosen.green, chosen.blue)
                                        else "#%02X%02X%02X%02X".format(chosen.alpha, chosen.red, chosen.green, chosen.blue))
                                }
                            })
                        }
                        if (expressionMode == "Resource") {
                            val search = remember { TextFieldState() }
                            var themeResource by remember { mutableStateOf(false) }
                            TextField(search, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search resource keys" })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Link(if (themeResource) "ThemeResource ✓" else "StaticResource ✓", modifier = Modifier.heightIn(min = 28.dp), onClick = { themeResource = !themeResource })
                                Link("Close", modifier = Modifier.heightIn(min = 28.dp), onClick = { expressionMode = null })
                            }
                            val keys = selection.resourceKeys.filter { it.contains(search.text, true) }
                            if (keys.isEmpty()) Text("No resource keys found in scope.")
                            keys.take(20).forEach { resource -> Link(resource, modifier = Modifier.heightIn(min = 28.dp), onClick = {
                                input.setTextAndPlaceCursorAtEnd("{${if (themeResource) "ThemeResource" else "StaticResource"} $resource}")
                                expressionMode = null
                            }) }
                            if (keys.size > 20) Text("Refine the search to see more keys.")
                        }
                        if (expressionMode == "Binding") {
                            val path = remember { TextFieldState() }
                            var mode by remember { mutableStateOf("OneWay") }
                            Text("Binding path")
                            TextField(path, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Binding path" })
                            Dropdown(menuContent = { listOf("OneWay", "TwoWay", "OneTime").forEach { value ->
                                selectableItem(selected = mode == value, onClick = { mode = value }) { Text(value) }
                            } }) { Text(mode) }
                            Link("Use binding", modifier = Modifier.heightIn(min = 28.dp), enabled = path.text.isNotBlank() && path.text.none { it in "{}," }, onClick = {
                                input.setTextAndPlaceCursorAtEnd("{Binding Path=${path.text}, Mode=$mode}"); expressionMode = null
                            })
                            Link("Cancel binding", modifier = Modifier.heightIn(min = 28.dp), onClick = { expressionMode = null })
                        }
                        val focus = remember(property.name) { FocusRequester() }
                        LaunchedEffect(property.name) { focus.requestFocus() }
                        TextField(input, modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Edit ${property.name}" }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { commit(); true }
                                else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { editing = null; true }
                                else false
                            })
                        if (error != null) Text(error)

                    }
                }
            }
        }
    }
    }
}

/** Presentation grouping only; writable types and members still come from WinMD. */
internal fun designerPropertyCategory(property: DesignerProperty): String = when {
    property.name in setOf("Width", "Height", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight", "Margin", "Padding",
        "HorizontalAlignment", "VerticalAlignment", "Orientation", "Spacing") || property.name.startsWith("Grid.") || property.name.startsWith("Canvas.") -> "Layout"
    property.name in setOf("Content", "Text", "Header", "PlaceholderText", "IsEnabled", "Visibility", "Name", "Tag") -> "Common"
    property.name.contains("Brush") || property.name in setOf("Background", "Foreground", "Opacity", "BorderThickness", "CornerRadius") -> "Appearance"
    property.name.startsWith("Font") || property.name.startsWith("Text") || property.name.startsWith("Character") -> "Typography"
    property.name.contains("Template") || property.name in setOf("Style", "DataContext", "Resources") -> "Resources and templates"
    else -> "Other"
}
