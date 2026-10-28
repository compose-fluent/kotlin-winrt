package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import io.github.composefluent.winrt.ide.ui.WinRTChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.*

@Composable
internal fun DesignerObjectPropertyPanel(selection: DesignerSelection, property: DesignerProperty, active: Boolean) {
    var refresh by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val snapshot by produceState<DesignerObjectValue?>(null, selection.text, selection.style?.sourceText, selection.state?.sourceText, property.name, refresh) {
        value = withContext(Dispatchers.IO) { ReadAction.compute<DesignerObjectValue?, RuntimeException> {
            runCatching { DesignerObjectEditing.inspect(selection, property) }.onFailure { feedback = it.message ?: "Cannot read object members" }.getOrNull()
        } }
    }
    val model = snapshot
    val draft = remember(selection.pointer, property.name) { TextFieldState() }
    var expected by remember(selection.pointer, property.name) { mutableStateOf("") }
    LaunchedEffect(model?.xml) { model?.let { if (draft.text.toString() == expected) draft.setTextAndPlaceCursorAtEnd(it.xml); expected = it.xml } }
    var objectIndex by remember { mutableStateOf("0") }
    var type by remember(property.name) { mutableStateOf("") }
    var showXaml by remember(selection.pointer, property.name) { mutableStateOf(false) }
    fun write(value: String?) {
        ApplicationManager.getApplication().invokeLater {
            if (active) runCatching { DesignerObjectEditing.replace(selection, property, value) }
                .onSuccess { feedback = "${property.name} saved · Ctrl+Z to undo"; refresh++ }
                .onFailure { feedback = it.message ?: "Could not edit object value" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${property.name} · object value")
        if (model == null && feedback == null) Text("Reading object members…")
        else if (model != null) {
            if (model.nodes.isNotEmpty()) {
                WinRTChoice("Object", model.nodes.mapIndexed { i, node -> i.toString() to node.first }, objectIndex) { objectIndex = it }
                val nested = model.nodes.getOrNull(objectIndex.toIntOrNull() ?: 0)?.second
                if (nested != null) DesignerProperties(nested, "", active = active, expandedByDefault = true) { changes, title ->
                    ApplicationManager.getApplication().invokeLater {
                        if (active) runCatching { WinUIXamlDesignerEditing.apply(requireNotNull(nested.pointer.element).project, nested, changes, title) }
                            .onSuccess { feedback = "$title · Ctrl+Z to undo"; refresh++ }
                            .onFailure { feedback = it.message ?: "Could not edit object member" }
                    }
                }
            }
            if (model.types.isNotEmpty()) {
                WinRTChoice("New object type", listOf("" to "Choose an assignable type") + model.types, type) { type = it }
                OutlinedButton(enabled = active && type.isNotEmpty(), onClick = {
                    val ns = type.substringBeforeLast('.')
                    val uri = if (io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.namespaces(io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.PRESENTATION).contains(ns))
                        io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.PRESENTATION else "using:$ns"
                    draft.setTextAndPlaceCursorAtEnd("<${type.substringAfterLast('.')} xmlns=\"$uri\"/>")
                    showXaml = true
                }) { Text("Create value draft") }
            }
            CheckboxRow("Edit object XAML", showXaml, { showXaml = it })
            if (showXaml || model.nodes.isEmpty()) {
                Text("XAML value · nested objects and collections")
                TextArea(draft, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 220.dp).semantics { contentDescription = "${property.name} object XAML" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = active && draft.text.isNotBlank(), onClick = { write(draft.text.toString()) }) { Text("Apply object") }
                    OutlinedButton(enabled = active && (property.elementValue || property.value != null), onClick = { write(null) }) { Text("Reset value") }
                    Link("Reload source", onClick = { draft.setTextAndPlaceCursorAtEnd(model.xml); expected = model.xml })
                }
            }
        }
        feedback?.let { Text(it) }
    }
}
