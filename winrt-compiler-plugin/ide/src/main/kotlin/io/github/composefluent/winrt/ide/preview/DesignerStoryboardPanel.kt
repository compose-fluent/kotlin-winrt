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
internal fun DesignerStoryboardPanel(project: Project, source: String, key: String, revision: Long, position: Int, owner: String?, onSeek: (Int) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    val tracks by produceState(emptyList<DesignerAnimationTrack>(), source, key, owner, revision, refresh) {
        value = withContext(Dispatchers.IO) { ReadAction.compute<List<DesignerAnimationTrack>, RuntimeException> {
            DesignerStoryboardEditing.tracks(project, source, key, owner)
        } }
    }
    var trackIndex by remember(source, key, owner) { mutableIntStateOf(0) }
    var frameIndex by remember(source, key, owner, trackIndex) { mutableIntStateOf(-1) }
    val track = tracks.getOrNull(trackIndex)
    val frame = track?.frames?.getOrNull(frameIndex)
    val frameTime = remember(source, key, owner, trackIndex, frameIndex, frame?.time) {
        TextFieldState(frame?.time?.let(DesignerStoryboardEditing::milliseconds)?.toString().orEmpty())
    }
    val validFrameTime = frameTime.text.toString().toIntOrNull()?.takeIf { it in 0..86_400_000 }
    val value = remember(source, key, owner, trackIndex, frameIndex, frame?.value) { TextFieldState(frame?.value.orEmpty()) }
    var interpolation by remember(track?.kind) { mutableStateOf(if (track?.kind == "Object") "Discrete" else "Linear") }
    var feedback by remember(source, key, owner) { mutableStateOf<String?>(null) }
    fun edit(remove: Boolean) {
        val selected = track ?: return
        val text = value.text.toString()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) runCatching {
                DesignerStoryboardEditing.write(project, selected, frame, if (frame == null || remove) position else validFrameTime ?: return@invokeLater, text, interpolation, remove)
            }.onSuccess { feedback = "${if (remove) "Deleted" else "Saved"} keyframe · Ctrl+Z to undo"; refresh++; if (remove) frameIndex = -1 }
                .onFailure { feedback = it.message ?: "Could not edit keyframe" }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (tracks.isEmpty()) Text("No editable keyframe tracks in this source storyboard.")
        else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { WinRTChoice("Track", tracks.mapIndexed { index, item -> index.toString() to item.label }, trackIndex.toString()) { trackIndex = it.toInt() } }
                Box(Modifier.weight(1f)) { WinRTChoice("Keyframe", listOf("-1" to "New at playhead") + track?.frames.orEmpty().mapIndexed { index, item -> index.toString() to "${item.time} · ${item.value ?: "Object value"}" }, frameIndex.toString()) { frameIndex = it.toInt()
                    track?.frames?.getOrNull(frameIndex)?.time?.let(DesignerStoryboardEditing::milliseconds)?.let(onSeek)
                } }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (frame?.complex == true) "Object XAML" else "Value")
                if (frame?.complex == true || value.text.trimStart().startsWith("<")) TextArea(value, modifier = Modifier.width(280.dp).heightIn(min = 90.dp, max = 160.dp))
                else TextField(value, modifier = Modifier.width(160.dp))
                if (frame == null) Box(Modifier.width(150.dp)) { WinRTChoice("Interpolation", (if (track?.kind == "Object") listOf("Discrete") else listOf("Linear", "Discrete")).map { it to it }, interpolation) { interpolation = it } }
                else Text(frame.type.removeSuffix("${track?.kind}KeyFrame"))
                if (frame != null) {
                    Text("Time (ms)")
                    TextField(frameTime, modifier = Modifier.width(100.dp))
                    OutlinedButton(onClick = { frameTime.setTextAndPlaceCursorAtEnd(position.toString()) }) { Text("Use playhead") }
                }
                OutlinedButton(enabled = track != null && value.text.isNotBlank() && (frame == null || validFrameTime != null), onClick = { edit(false) }) { Text(if (frame == null) "Add at playhead" else "Save keyframe") }
                OutlinedButton(enabled = frame != null, onClick = { edit(true) }) { Text("Delete keyframe") }
            }
            if (frame != null && validFrameTime == null) Text("Enter a whole-millisecond time from 0 to 86400000; the source KeyTime is ${frame.time}.")
            Text(if (frame == null) "Adds a source keyframe at $position ms." else "Edits this frame at its specified time. Existing easing and spline settings are preserved.")
            if (frame != null && track != null && track.kind != "Object") key(trackIndex, frameIndex, frame.spline) {
                DesignerSplinePanel(frame.spline) { curve ->
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) runCatching { DesignerStoryboardEditing.writeSpline(project, track, frame, curve) }
                            .onSuccess { feedback = "Saved timing curve · Ctrl+Z to undo"; refresh++ }
                            .onFailure { feedback = it.message ?: "Could not edit timing curve" }
                    }
                }
            }
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
