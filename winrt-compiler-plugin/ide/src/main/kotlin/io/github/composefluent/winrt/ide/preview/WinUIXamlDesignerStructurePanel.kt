package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.*

@Composable
internal fun DesignerStructurePanel(selection: DesignerSelection?, definition: (String, Int, String?) -> Unit,
    reorder: (Boolean) -> Unit) {
    val expansion = remember { mutableStateMapOf<String, Boolean>() }
    val structure = selection?.structure ?: return
    if (structure.previous || structure.next) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Link("Move earlier", modifier = Modifier.heightIn(min = 28.dp), enabled = structure.previous, onClick = { reorder(false) })
        Link("Move later", modifier = Modifier.heightIn(min = 28.dp), enabled = structure.next, onClick = { reorder(true) })
    }
    listOf("Row" to structure.rows, "Column" to structure.columns).forEach { (axis, sizes) ->
        if (sizes != null) {
            val expanded = expansion[axis] == true
            Link("${if (expanded) "▾" else "▸"} Grid ${axis.lowercase()}s (${sizes.size})",
                modifier = Modifier.heightIn(min = 28.dp), onClick = { expansion[axis] = !expanded })
            if (expanded) {
                Text("Auto, pixels or * (for example 2*)")
                sizes.forEachIndexed { index, value ->
                    val input = remember(selection.text, axis, index) { TextFieldState(value) }
                    val valid = DesignerStructureEditing.validLength(input.text.toString())
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$axis $index")
                            PreviewAction("Apply $axis $index", "apply", enabled = valid && input.text.toString() != value) {
                                definition(axis, index, input.text.toString())
                            }
                        }
                        TextField(input, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$axis $index size" })
                        if (!valid) Text("Enter Auto, a non-negative size, or a star size.")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Link("Add ${axis.lowercase()}", modifier = Modifier.heightIn(min = 28.dp), onClick = { definition(axis, sizes.size, "*") })
                    Link("Remove last", modifier = Modifier.heightIn(min = 28.dp), enabled = sizes.isNotEmpty(), onClick = { definition(axis, sizes.lastIndex, null) })
                }
            }
        }
    }
}
