package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import org.jetbrains.jewel.ui.component.*
import io.github.composefluent.winrt.ide.ui.WinRTChoice

@Composable
internal fun DesignerSplinePanel(initial: DesignerSpline?, onApply: (DesignerSpline) -> Unit) {
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val coordinates = remember(initial) { listOf(initial?.x1 ?: 0f, initial?.y1 ?: 0f, initial?.x2 ?: 1f, initial?.y2 ?: 1f).map { TextFieldState(it.toString()) } }
    val curve = DesignerSpline.parse(coordinates.joinToString(",") { it.text.toString() })
    val currentCurve by rememberUpdatedState(curve)
    fun update(value: DesignerSpline) {
        listOf(value.x1, value.y1, value.x2, value.y2).forEachIndexed { index, number -> coordinates[index].setTextAndPlaceCursorAtEnd(number.toString()) }
    }
    var preset by remember(initial) { mutableStateOf("Custom") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Timing curve")
        WinRTChoice("Curve preset", listOf("Custom" to "Custom") + DesignerSpline.presets.keys.map { it to it }, preset) {
            preset = it; DesignerSpline.presets[it]?.let(::update)
        }
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = "Timing curve. Drag either control point, or edit its coordinates below." }
            .pointerInput(inset) {
                var point = 0
                detectDragGestures(onDragStart = { position ->
                    val value = currentCurve ?: return@detectDragGestures
                    fun distance(x: Float, y: Float) = (position - Offset(inset + x * (size.width - 2 * inset), size.height - inset - y * (size.height - 2 * inset))).getDistanceSquared()
                    point = if (distance(value.x1, value.y1) <= distance(value.x2, value.y2)) 0 else 1
                }, onDrag = { change, _ ->
                    val value = currentCurve
                    if (value != null) {
                        val x = ((change.position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        val y = ((size.height - inset - change.position.y) / (size.height - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        update(if (point == 0) value.copy(x1 = x, y1 = y) else value.copy(x2 = x, y2 = y))
                        preset = "Custom"; change.consume()
                    }
                })
            }) {
            fun at(x: Float, y: Float) = Offset(inset + x * (size.width - 2 * inset), size.height - inset - y * (size.height - 2 * inset))
            val ink = Color(0xff397dcc)
            for (i in 0..4) {
                val t = i / 4f
                drawLine(Color.Gray.copy(alpha = .35f), at(t, 0f), at(t, 1f))
                drawLine(Color.Gray.copy(alpha = .35f), at(0f, t), at(1f, t))
            }
            curve?.let { value ->
                val start = at(0f, 0f); val end = at(1f, 1f)
                val first = at(value.x1, value.y1); val second = at(value.x2, value.y2)
                drawLine(ink.copy(alpha = .6f), start, first, 2.dp.toPx())
                drawLine(ink.copy(alpha = .6f), end, second, 2.dp.toPx())
                drawPath(Path().apply { moveTo(start.x, start.y); cubicTo(first.x, first.y, second.x, second.y, end.x, end.y) }, ink, style = Stroke(3.dp.toPx()))
                drawCircle(ink, 6.dp.toPx(), first); drawCircle(ink, 6.dp.toPx(), second)
            }
        }
        listOf("First control point" to 0, "Second control point" to 2).forEach { (label, offset) ->
            Text(label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("X"); TextField(coordinates[offset], modifier = Modifier.weight(1f).semantics { contentDescription = "$label X" })
                Text("Y"); TextField(coordinates[offset + 1], modifier = Modifier.weight(1f).semantics { contentDescription = "$label Y" })
            }
        }
        if (curve == null) Text("Enter four coordinates between 0 and 1.")
        OutlinedButton(enabled = curve != null, onClick = { curve?.let(onApply) }) { Text("Apply timing curve") }
        Text("Uses spline interpolation. Value and time stay unchanged; Ctrl+Z undoes the curve edit.")
    }
}
