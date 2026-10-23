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
    if (selection == null) { Text("Select a source element to edit its XAML properties."); return }
    var editing by remember(selection.pointer) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("XAML properties")
        selection.properties.filter { it.writable && it.name.contains(filter, true) }.forEach { property ->
            key(property.name) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(property.name, Modifier.weight(1f), maxLines = 1)
                        Tooltip(tooltip = { Text(property.value ?: "Inherited or default; click to set a local value") }) {
                            Link((property.value ?: "Set value…").take(42), enabled = !property.elementValue,
                                onClick = { editing = if (editing == property.name) null else property.name })
                        }
                    }
                    if (property.elementValue) Text("Property element · edit in XAML")
                    if (editing == property.name) {
                        val input = remember(selection.text, property.name) { TextFieldState(property.value.orEmpty()) }
                        val error = WinUIXamlDesignerEditing.validate(property, input.text.toString())
                        fun commit() { if (error == null) apply(mapOf(property.name to input.text.toString()), "Set ${property.name}") }
                        if (property.choices.isNotEmpty()) Dropdown(menuContent = {
                            property.choices.forEach { value -> selectableItem(selected = input.text.toString() == value,
                                onClick = { input.setTextAndPlaceCursorAtEnd(value) }) { Text(value) } }
                        }) { Text(input.text.toString().ifEmpty { "Choose value" }) }
                        val focus = remember(property.name) { FocusRequester() }
                        LaunchedEffect(property.name) { focus.requestFocus() }
                        TextField(input, modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = "Edit ${property.name}" }
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { commit(); true }
                                else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { editing = null; true }
                                else false
                            })
                        if (error != null) Text(error)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DefaultButton(enabled = error == null, onClick = ::commit) { Text("Apply") }
                            OutlinedButton(enabled = property.value != null, onClick = { apply(mapOf(property.name to null), "Reset ${property.name}"); editing = null }) { Text("Reset") }
                            Link("Cancel", onClick = { editing = null })
                        }
                    }
                }
            }
        }
    }
}
