package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.component.*
import io.github.composefluent.winrt.ide.ui.WinRTChoice

@Composable
internal fun DesignerStoryboardPanel(project: Project, source: String, key: String, revision: Long, position: Int) {
    var refresh by remember { mutableIntStateOf(0) }
    val tracks by produceState(emptyList<DesignerAnimationTrack>(), source, key, revision, refresh) {
        value = withContext(Dispatchers.IO) { ReadAction.compute<List<DesignerAnimationTrack>, RuntimeException> {
            DesignerStoryboardEditing.tracks(project, source, key)
        } }
    }
    var trackIndex by remember(source, key) { mutableIntStateOf(0) }
    var frameIndex by remember(source, key, trackIndex) { mutableIntStateOf(-1) }
    val track = tracks.getOrNull(trackIndex)
    val frame = track?.frames?.getOrNull(frameIndex)
    val value = remember(source, key, trackIndex, frameIndex, frame?.value) { TextFieldState(frame?.value.orEmpty()) }
    var interpolation by remember(track?.kind) { mutableStateOf(if (track?.kind == "Object") "Discrete" else "Linear") }
    var feedback by remember(source, key) { mutableStateOf<String?>(null) }
    fun edit(remove: Boolean) {
        val selected = track ?: return
        val text = value.text.toString()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching {
                DesignerStoryboardEditing.write(project, selected, frame, position, text, interpolation, remove)
            }.onSuccess { feedback = "${if (remove) "Deleted" else "Saved"} keyframe · Ctrl+Z to undo"; refresh++; frameIndex = -1 }
                .onFailure { feedback = it.message ?: "Could not edit keyframe" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tracks.isEmpty()) Text("No editable keyframe tracks in this source storyboard.")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WinRTChoice("Track", tracks.mapIndexed { index, item -> index.toString() to item.label }, trackIndex.toString()) { trackIndex = it.toInt() } }
                Box(Modifier.weight(1f)) { WinRTChoice("Keyframe", listOf("-1" to "New at playhead") + track?.frames.orEmpty().mapIndexed { index, item -> index.toString() to "${item.time} · ${item.value ?: "Object value"}" }, frameIndex.toString()) { frameIndex = it.toInt() } }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Value")
                TextField(value, enabled = frame?.complex != true, modifier = Modifier.width(160.dp))
                if (frame == null) Box(Modifier.width(150.dp)) { WinRTChoice("Interpolation", (if (track?.kind == "Object") listOf("Discrete") else listOf("Linear", "Discrete")).map { it to it }, interpolation) { interpolation = it } }
                else Text(frame.type.removeSuffix("${track?.kind}KeyFrame"))
                OutlinedButton(enabled = track != null && frame?.complex != true && value.text.isNotBlank(), onClick = { edit(false) }) { Text(if (frame == null) "Add at playhead" else "Save at playhead") }
                OutlinedButton(enabled = frame != null && !frame.complex, onClick = { edit(true) }) { Text("Delete keyframe") }
            }
            Text("Edits source XAML at $position ms. Existing easing and spline settings are preserved.")
        }
        feedback?.let { Text(it) }
    }
}

@Composable
internal fun DesignerStoryboardCreatePanel(project: Project, selection: DesignerSelection?, position: Int, selectedKey: String, onCreated: (String) -> Unit) {
    val key = remember(selectedKey) { TextFieldState(selectedKey.ifEmpty { "Animation" }) }
    val properties = selection?.properties.orEmpty().filter { it.writable && it.dependencyProperty && !it.elementValue }
    var property by remember(selection?.pointer) { mutableStateOf("Opacity") }
    val initial = remember(selection?.pointer, property) { TextFieldState(properties.singleOrNull { it.name == property }?.value.orEmpty()) }
    var dependent by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Create a track for the selected named element")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Storyboard key")
            TextField(key, modifier = Modifier.width(180.dp))
            Box(Modifier.weight(1f)) { WinRTChoice("Property", properties.map { it.name to it.name }, property) { property = it } }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("First value")
            TextField(initial, modifier = Modifier.width(150.dp))
            CheckboxRow("Allow dependent animations", dependent, { dependent = it })
            OutlinedButton(enabled = selection != null && properties.any { it.name == property } && key.text.isNotBlank() && initial.text.isNotBlank(), onClick = {
                val target = selection ?: return@OutlinedButton
                val resourceKey = key.text.toString().trim()
                val propertyName = property
                val text = initial.text.toString()
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) runCatching { DesignerStoryboardEditing.createTrack(project, target, resourceKey, propertyName, position, text, dependent) }
                        .onSuccess { feedback = "Created $resourceKey · $propertyName at $position ms · Ctrl+Z to undo"; onCreated(resourceKey) }
                        .onFailure { feedback = it.message ?: "Could not create animation track" }
                }
            }) { Text("Create track") }
        }
        feedback?.let { Text(it) }
    }
}
