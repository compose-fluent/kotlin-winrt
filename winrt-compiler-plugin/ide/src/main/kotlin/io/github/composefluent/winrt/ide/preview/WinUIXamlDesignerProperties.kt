@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import org.jetbrains.jewel.ui.component.*

internal data class DesignerEditRejection(val revision: Long, val edits: List<Pair<DesignerSelection, Set<String>>>) {
    fun revisionFor(selection: DesignerSelection, name: String): Long = if (edits.any { (target, properties) ->
        target.pointer == selection.pointer && target.state?.pointer == selection.state?.pointer &&
            target.style?.pointer == selection.style?.pointer && name in properties
    }) revision else 0
}

@Composable
internal fun DesignerProperties(selection: DesignerSelection?, filter: String, active: Boolean = true, effectiveValues: Map<String, String> = emptyMap(), rejection: DesignerEditRejection? = null, expandedByDefault: Boolean = false, apply: (Map<String, String?>, String) -> Unit) {
    val expansion = remember { mutableStateMapOf<String, Boolean>() }
    val currentSelection by rememberUpdatedState(selection)
    val currentActive by rememberUpdatedState(active)
    if (selection == null) { Text("Select a source element to edit its XAML properties."); return }
    var editing by remember(selection.pointer, selection.state?.pointer, selection.style?.pointer) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(selection.style?.let { "Style setters · ${it.label}" } ?: if (selection.state == null) "XAML properties" else "Visual state setters")
        selection.sharedTemplate?.let { Text("Shared template: $it. Changes affect all instances.") }
        selection.properties.filter { it.writable && it.name.contains(filter, true) }
            .groupBy(::designerPropertyCategory).toSortedMap().forEach { (category, properties) ->
            val expanded = expansion[category] ?: (expandedByDefault || category == "Layout" || category == "Common")
            Link("${if (expanded || filter.isNotEmpty()) "▾" else "▸"} $category (${properties.size})", onClick = { expansion[category] = !expanded })
            if (expanded || filter.isNotEmpty()) properties.sortedWith(compareBy<DesignerProperty> {
                if (category == "Layout") designerLayoutOrder.indexOf(it.name).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE else 0
            }.thenBy { it.name }).forEach { property ->
            key(selection.pointer, selection.state?.pointer, selection.style?.pointer, property.name) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DesignerPropertyRow(property, selection.bounds, effectiveValues[property.name], rejection?.revisionFor(selection, property.name) ?: 0, apply,
                        isCurrent = { currentActive && currentSelection?.pointer == selection.pointer &&
                            currentSelection?.state?.pointer == selection.state?.pointer &&
                            currentSelection?.style?.pointer == selection.style?.pointer },
                        advanced = { editing = if (editing == property.name) null else property.name })
                    if (editing == property.name) {
                        if (property.elementValue) DesignerObjectPropertyPanel(selection, property, active)
                        else {
                        val input = remember(selection.text, selection.state?.sourceText, selection.style?.sourceText, property.name) { TextFieldState(property.value.orEmpty()) }
                        val error = WinUIXamlDesignerEditing.validate(property, input.text.toString())
                        val latestApply by rememberUpdatedState(apply)
                        val commit = {
                            val value = input.text.toString()
                            if (currentActive && WinUIXamlDesignerEditing.validate(property, value) == null)
                                latestApply(mapOf(property.name to value), "Set ${property.name}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PreviewAction("Apply ${property.name}", "apply", enabled = active && error == null) { commit() }
                            PreviewAction("Reset ${property.name}", "reset", enabled = active && property.value != null) {
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
                            Link("Object…", modifier = Modifier.heightIn(min = 28.dp), onClick = { expressionMode = if (expressionMode == "Object") null else "Object" })
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
                        if (expressionMode == "Object") DesignerObjectPropertyPanel(selection, property, active)
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
}

/** A local draft never overwrites a concurrent source edit or submits twice on Enter + blur. */
internal class DesignerPropertyDraft(value: String?) {
    val buffer = TextFieldState(value.orEmpty())
    var expected by mutableStateOf(value)
        private set
    var submitted by mutableStateOf<String?>(null)
        private set
    fun accept(value: String?) {
        if (submitted != null && value == submitted) {
            // Acknowledgement must not erase typing that began after Enter.
            expected = value
            submitted = null
        } else if (buffer.text.toString() == expected.orEmpty()) reload(value)
    }
    fun reload(value: String?) {
        buffer.setTextAndPlaceCursorAtEnd(value.orEmpty())
        expected = value
        submitted = null
    }
    fun reject() { submitted = null }
    fun changed(source: String?) = source != expected && (submitted == null || source != submitted)
    fun submission(property: DesignerProperty): String? {
        val value = buffer.text.toString()
        if (changed(property.value) || submitted != null || value == expected.orEmpty() ||
            WinUIXamlDesignerEditing.validate(property, value) != null) return null
        submitted = value
        return value
    }
}

@Composable
private fun DesignerPropertyRow(property: DesignerProperty,
    bounds: io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds, inherited: String?, rejectedRevision: Long,
    apply: (Map<String, String?>, String) -> Unit, isCurrent: () -> Boolean, advanced: () -> Unit) {
    val draft = remember { DesignerPropertyDraft(property.value) }
    val currentApply by rememberUpdatedState(apply)
    val currentProperty by rememberUpdatedState(property)
    val currentGuard by rememberUpdatedState(isCurrent)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(property.value) { draft.accept(property.value) }
    LaunchedEffect(rejectedRevision) { if (rejectedRevision != 0L) draft.reject() }
    val commit = {
        if (currentGuard()) draft.submission(currentProperty)?.let { value ->
            currentApply(mapOf(currentProperty.name to value), "Set ${currentProperty.name}")
        }
    }
    val currentCommit by rememberUpdatedState(commit)
    DisposableEffect(Unit) { onDispose { currentCommit() } }
    val effectiveChoice = designerEffectiveChoice(property, inherited)
    val dirty = draft.buffer.text.toString() != draft.expected.orEmpty()
    val error = if (dirty) WinUIXamlDesignerEditing.validate(property, draft.buffer.text.toString()) else null
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Layout(modifier = Modifier.fillMaxWidth(), content = {
            Tooltip(tooltip = { Text(property.name) }) { Text(when (property.name) { "HorizontalAlignment" -> "H. alignment"; "VerticalAlignment" -> "V. alignment"; else -> property.name }, Modifier, maxLines = 1) }
            if (property.elementValue) Link("Object value…", onClick = advanced, modifier = Modifier.fillMaxWidth())
            else if (property.name in setOf("HorizontalAlignment", "VerticalAlignment") && property.choices.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
                val horizontal = property.name == "HorizontalAlignment"
                val values = if (horizontal) listOf("Left", "Center", "Right", "Stretch") else listOf("Top", "Center", "Bottom", "Stretch")
                values.filter { it in property.choices }.forEach { value ->
                    PreviewAction("${property.name}: $value", "align-${if (horizontal) "h" else "v"}-${value.lowercase()}",
                        selected = effectiveChoice == value) {
                        if (currentGuard() && value != currentProperty.value)
                            currentApply(mapOf(property.name to value), "Set ${property.name}")
                    }
                }
            }
            else if (property.choices.isNotEmpty()) Dropdown(modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = "Edit ${property.name}"
            }, menuContent = {
                property.choices.forEach { value -> selectableItem(selected = effectiveChoice == value, onClick = {
                    if (currentGuard() && value != currentProperty.value)
                        currentApply(mapOf(property.name to value), "Set ${property.name}")
                }) { Text(value) } }
            }) { Text(property.value ?: effectiveChoice?.let { "$it (inherited)" } ?: "Default", maxLines = 1) }
            else if (property.type.substringAfterLast('.') == "Thickness" &&
                (property.value == null || WinUIXamlDesignerEditing.thickness(property.value) != null)) {
                DesignerThicknessFields(property, inherited, currentGuard, currentApply, Modifier.fillMaxWidth(), rejectedRevision)
            }
            else TextField(draft.buffer, placeholder = {
                Text(designerPropertyPlaceholder(property, inherited, bounds), maxLines = 1)
            }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Edit ${property.name}" }
                .onFocusChanged {
                    val leaving = focused && !it.hasFocus
                    focused = it.hasFocus
                    if (leaving) currentCommit()
                }.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) { currentCommit(); true }
                    else if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) { draft.reload(currentProperty.value); true }
                    else false
                })
            PreviewAction("Reset ${property.name}", "reset", enabled = property.value != null && !property.elementValue) {
                draft.reload(currentProperty.value)
                if (currentGuard()) currentApply(mapOf(property.name to null), "Reset ${property.name}")
            }
            PreviewAction("More ${property.name} options", "more", action = advanced)
        }) { children, constraints ->
            // Reflow during measurement, keeping the same editor composition so
            // resizing the inspector preserves its draft, selection and focus.
            val width = constraints.maxWidth
            val gap = 4.dp.roundToPx()
            val reset = children[2].measure(Constraints())
            val more = children[3].measure(Constraints())
            val actionsWidth = reset.width + more.width + gap
            val stacked = width < 320.dp.roundToPx()
            val labelWidth = if (stacked) (width - actionsWidth - gap).coerceAtLeast(0) else 104.dp.roundToPx()
            val label = children[0].measure(Constraints.fixedWidth(labelWidth))
            val editorWidth = if (stacked) width else (width - labelWidth - actionsWidth - 2 * gap).coerceAtLeast(0)
            val editor = children[1].measure(Constraints.fixedWidth(editorWidth))
            val headingHeight = maxOf(label.height, reset.height, more.height, if (stacked) 0 else editor.height)
            val height = if (stacked) headingHeight + gap + editor.height else headingHeight
            layout(width, height) {
                label.placeRelative(0, (headingHeight - label.height) / 2)
                reset.placeRelative(width - actionsWidth, (headingHeight - reset.height) / 2)
                more.placeRelative(width - more.width, (headingHeight - more.height) / 2)
                editor.placeRelative(if (stacked) 0 else labelWidth + gap,
                    if (stacked) headingHeight + gap else (headingHeight - editor.height) / 2)
            }
        }
        if (draft.changed(property.value)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Changed in XAML.")
            Link("Reload value", onClick = { draft.reload(property.value) })
        } else if (error != null) Text(error)
    }
}

// Keep the common sizing/alignment controls adjacent, as in the XAML designer;
// alphabetic ordering otherwise separates Width from Height and the two alignments.
private val designerLayoutOrder = listOf("Width", "Height", "HorizontalAlignment", "VerticalAlignment", "Margin", "Padding",
    "MinWidth", "MinHeight", "MaxWidth", "MaxHeight", "Orientation", "Spacing")

internal fun designerPropertyPlaceholder(property: DesignerProperty, inherited: String?,
    bounds: io.github.composefluent.winrt.runtime.WinUIXamlVisualBounds): String {
    val effective = inherited?.takeUnless { it.isEmpty() || it.startsWith("Unavailable:") }
    if (property.name in setOf("Width", "Height") && effective == "NaN") {
        val actual = if (property.name == "Width") bounds.width else bounds.height
        return "Auto (${java.math.BigDecimal.valueOf(actual).stripTrailingZeros().toPlainString()})"
    }
    return effective?.let { "$it (inherited)" } ?: "Default"
}

internal fun designerEffectiveChoice(property: DesignerProperty, inherited: String?): String? =
    property.value ?: inherited?.substringAfterLast('.')?.let { value ->
        property.choices.firstOrNull { it.equals(value, ignoreCase = true) }
    }

/** Presentation grouping only; writable types and members still come from WinMD. */
internal fun designerPropertyCategory(property: DesignerProperty): String = when {
    property.name in setOf("Width", "Height", "MinWidth", "MaxWidth", "MinHeight", "MaxHeight", "Margin", "Padding",
        "HorizontalAlignment", "VerticalAlignment", "Orientation", "Spacing") || property.name.startsWith("Grid.") || property.name.startsWith("Canvas.") -> "Layout"
    property.name in setOf("Content", "Text", "Header", "PlaceholderText", "IsEnabled", "Visibility", "Name", "Tag") -> "Common"
    property.name.contains("Brush") || property.name in setOf("Color", "Background", "Foreground", "Opacity", "BorderThickness", "CornerRadius") -> "Appearance"
    property.name.startsWith("Font") || property.name.startsWith("Text") || property.name.startsWith("Character") -> "Typography"
    property.name.contains("Template") || property.name in setOf("Style", "DataContext", "Resources") -> "Resources and templates"
    else -> "Other"
}
