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
internal class DesignerThicknessDraft(value: String?, private var inherited: String? = null) {
    val draft = DesignerPropertyDraft(value)
    private var baseline = expanded(value ?: inherited)
    val fields = baseline.map(::TextFieldState)
    var inheritedChanged by mutableStateOf(false)
        private set
    private fun expanded(value: String?): List<String> =
        (value?.let(WinUIXamlDesignerEditing::thickness) ?: DoubleArray(4)).map {
            java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()
        }
    fun sync() {
        val values = fields.map { it.text.toString() }
        draft.buffer.setTextAndPlaceCursorAtEnd(if (values == baseline) draft.expected.orEmpty() else values.joinToString(","))
    }
    fun reload(value: String?, effective: String? = inherited) {
        inherited = effective
        inheritedChanged = false
        draft.reload(value)
        baseline = expanded(value ?: effective)
        baseline.forEachIndexed { index, text -> fields[index].setTextAndPlaceCursorAtEnd(text) }
    }
    fun accept(value: String?, effective: String? = inherited) {
        sync()
        val clean = draft.buffer.text.toString() == draft.expected.orEmpty()
        val oldExpected = draft.expected
        if (!clean && oldExpected == null && expanded(effective) != expanded(inherited)) inheritedChanged = true
        draft.accept(value)
        if (draft.expected == value && draft.buffer.text.toString() == value.orEmpty()) reload(value, effective)
        else if (draft.expected != oldExpected) {
            inherited = effective
            baseline = expanded(value ?: effective)
        }
    }
    fun submission(property: DesignerProperty): String? {
        sync()
        return if (inheritedChanged) null else draft.submission(property)
    }
}

@Composable
internal fun DesignerThicknessFields(property: DesignerProperty, inherited: String?, isCurrent: () -> Boolean,
    apply: (Map<String, String?>, String) -> Unit, modifier: Modifier = Modifier) {
    val state = remember { DesignerThicknessDraft(property.value, inherited) }
    val currentProperty by rememberUpdatedState(property)
    val currentGuard by rememberUpdatedState(isCurrent)
    val currentApply by rememberUpdatedState(apply)
    LaunchedEffect(property.value, inherited) {
        // Missing effective values while an inspection is pending are not zeroes.
        if (inherited == null) state.accept(property.value) else state.accept(property.value, inherited)
    }
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
                                else if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { if (inherited == null) state.reload(currentProperty.value) else state.reload(currentProperty.value, inherited); true }
                                else false
                            })
                    }
                }
            }
        }
        val literal = state.fields.joinToString(",") { it.text.toString() }
        WinUIXamlDesignerEditing.validate(property, literal)?.let { Text(it) }
        if (state.inheritedChanged || state.draft.changed(property.value)) {
            Text("Changed in XAML. Press Escape to reload.")
        }
    }
}
