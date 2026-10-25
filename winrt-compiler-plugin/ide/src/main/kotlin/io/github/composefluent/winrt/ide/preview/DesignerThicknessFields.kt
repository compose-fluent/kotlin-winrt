@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/** XAML Thickness shorthand is expanded by the existing source-editing parser. */
internal class DesignerThicknessDraft(value: String?) {
    val draft = DesignerPropertyDraft(value)
    val fields = expanded(value).map(::TextFieldState)
    private fun expanded(value: String?): List<String> =
        (value?.let(WinUIXamlDesignerEditing::thickness) ?: DoubleArray(4)).map {
            java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
        }
    fun sync() {
        val values = fields.map { it.text.toString() }
        draft.buffer.setTextAndPlaceCursorAtEnd(if (values == expanded(draft.expected)) draft.expected.orEmpty() else values.joinToString(","))
    }
    fun reload(value: String?) {
        draft.reload(value)
        expanded(value).forEachIndexed { index, text -> fields[index].setTextAndPlaceCursorAtEnd(text) }
    }
    fun accept(value: String?) {
        sync()
        draft.accept(value)
        if (draft.expected == value && draft.buffer.text.toString() == value.orEmpty()) reload(value)
    }
    fun submission(property: DesignerProperty): String? { sync(); return draft.submission(property) }
}

@Composable
internal fun DesignerThicknessFields(property: DesignerProperty, isCurrent: () -> Boolean,
    apply: (Map<String, String?>, String) -> Unit, modifier: Modifier = Modifier) {
    val state = remember { DesignerThicknessDraft(property.value) }
    val currentProperty by rememberUpdatedState(property)
    val currentGuard by rememberUpdatedState(isCurrent)
    val currentApply by rememberUpdatedState(apply)
    LaunchedEffect(property.value) { state.accept(property.value) }
    fun commit() {
        if (currentGuard()) state.submission(currentProperty)?.let {
            currentApply(mapOf(currentProperty.name to it), "Set ${currentProperty.name}")
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(listOf(0 to "Left", 1 to "Top"), listOf(2 to "Right", 3 to "Bottom")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { (index, label) ->
                    var focused by remember { mutableStateOf(false) }
                    Column(Modifier.weight(1f)) {
                        Text(label)
                        TextField(state.fields[index], modifier = Modifier.fillMaxWidth()
                            .semantics { contentDescription = "${property.name} $label" }
                            .onFocusChanged { val leaving = focused && !it.hasFocus; focused = it.hasFocus; if (leaving) commit() }
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Enter) { commit(); true }
                                else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { state.reload(currentProperty.value); true }
                                else false
                            })
                    }
                }
            }
        }
        val literal = state.fields.joinToString(",") { it.text.toString() }
        WinUIXamlDesignerEditing.validate(property, literal)?.let { Text(it) }
        if (state.draft.changed(property.value)) {
            Text("Changed in XAML. Press Escape to reload.")
        }
    }
}
