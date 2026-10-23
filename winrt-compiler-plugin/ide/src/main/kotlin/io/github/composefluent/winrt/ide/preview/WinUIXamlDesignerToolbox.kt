package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.*

internal data class DesignerToolDrop(val type: String, val point: Offset, val serial: Long = System.nanoTime())

@Composable
internal fun DesignerToolbox(types: List<String>, armed: String?, choose: (String?) -> Unit,
    drop: (DesignerToolDrop) -> Unit, close: () -> Unit) {
    val search = remember { TextFieldState() }
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Toolbox"); Link("Close", onClick = close)
        }
        TextField(search, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search toolbox" })
        Text("Drag onto the canvas, or select a control then click its container. Escape cancels.")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            types.filter { it.contains(search.text, true) }.forEach { type ->
                var origin by remember { mutableStateOf(Offset.Zero) }
                var point by remember { mutableStateOf(Offset.Zero) }
                val latestDrop by rememberUpdatedState(drop)
                val latestChoose by rememberUpdatedState(choose)
                Box(Modifier.fillMaxWidth().background(if (type == armed) Color(0x224BA3FF) else Color.Transparent)
                    .onGloballyPositioned { origin = it.positionInRoot() }
                    .clickable { choose(if (armed == type) null else type) }
                    .pointerInput(type) { detectDragGestures(
                        onDragStart = { point = origin + it; latestChoose(type) },
                        onDrag = { change, amount -> point += amount; change.consume() },
                        onDragEnd = { latestDrop(DesignerToolDrop(type, point)); latestChoose(null) },
                        onDragCancel = { latestChoose(null) }) }
                    .semantics { contentDescription = "Insert $type; drag to canvas" }.padding(8.dp)) { Text(type) }
            }
        }
    }
}
