@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only navigation changes here. Selection never edits the user's XAML geometry. */
@Composable
internal fun PreviewImage(view: WinUIXamlVisualSnapshot, selected: List<Int>, select: (List<Int>) -> Unit,
    openSource: (List<Int>) -> Unit, zoom: Float?, fitSelection: Boolean, panTool: Boolean,
    onZoom: (Float) -> Unit, setZoom: (Float) -> Unit, fitAll: () -> Unit, modifier: Modifier, resetViewport: Int = 0) {
    val image = view.image ?: return
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, image) {
        value = withContext(Dispatchers.IO) { bgraImage(image).toComposeImageBitmap() }
    }
    val root = view.nodes.firstOrNull()?.bounds ?: return
    if (root.width <= 0 || root.height <= 0) return
    val target = if (fitSelection) view.nodes.firstOrNull { it.path == selected }?.bounds
        ?.takeIf { it.width > 0 && it.height > 0 } ?: root else root
    val density = LocalDensity.current.density
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val effectiveZoom by remember(target, density, zoom, fitSelection) { derivedStateOf {
        previewScale(viewport.width, viewport.height, target.width, target.height, density, zoom)
            .let { if (fitSelection) it.coerceAtMost(8f) else it }
    } }
    val reportedZoom = effectiveZoom
    SideEffect { onZoom(reportedZoom) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(resetViewport) { pan = Offset.Zero }
    var space by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val factor by remember(density, target, zoom, fitSelection) { derivedStateOf { effectiveZoom * density } }
    fun zoomAt(value: Float, point: Offset) {
        pan = previewAnchoredPan(viewport, target, root, factor, value * density, pan, point)
        setZoom(value)
    }
    // Pointer coroutines outlive recomposition; read current transform inputs,
    // never a memoized local function reference containing an earlier zoom.
    val currentRoot by rememberUpdatedState(root)
    val currentTarget by rememberUpdatedState(target)
    val currentZoom by rememberUpdatedState(zoom)
    val currentFitSelection by rememberUpdatedState(fitSelection)
    val currentNodes by rememberUpdatedState(view.nodes)
    val latestHit by rememberUpdatedState<(Offset) -> WinUIXamlVisualNode?>({ point ->
        val currentFactor = previewScale(viewport.width, viewport.height, currentTarget.width, currentTarget.height,
            density, currentZoom).let { if (currentFitSelection) it.coerceAtMost(8f) else it } * density
        val origin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), currentTarget, currentFactor) + pan
        WinUIXamlVisualInspectionSelection.hit(currentNodes, (point.x - origin.x) / currentFactor.toDouble(),
            (point.y - origin.y) / currentFactor.toDouble())
    })
    val latestSelect by rememberUpdatedState(select)
    val latestOpen by rememberUpdatedState(openSource)
    val latestSetZoom by rememberUpdatedState(setZoom)
    val latestZoom by rememberUpdatedState<(Boolean, Offset) -> Unit>({ increase, point ->
        val active = previewScale(viewport.width, viewport.height, currentTarget.width, currentTarget.height,
            density, currentZoom).let { if (currentFitSelection) it.coerceAtMost(8f) else it }
        val next = previewZoomStep(active, increase)
        pan = previewAnchoredPan(viewport, currentTarget, currentRoot, active * density, next * density, pan, point)
        latestSetZoom(next)
    })
    val latestPanTool by rememberUpdatedState(panTool)
    Canvas(modifier.onSizeChanged { viewport = it }.clipToBounds()
        .focusRequester(focus).onFocusChanged { focused = it.isFocused; if (!focused) space = false }
        .onKeyEvent { event ->
            if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) false
            else if (event.key == Key.Spacebar) { space = event.type == KeyEventType.KeyDown; true }
            else if (event.type != KeyEventType.KeyDown) false
            else when (event.key) {
                Key.Equals, Key.Plus, Key.NumPadAdd -> { zoomAt(previewZoomStep(effectiveZoom, true), Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.Minus, Key.NumPadSubtract -> { zoomAt(previewZoomStep(effectiveZoom, false), Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.Zero, Key.NumPad0 -> { zoomAt(1f, Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.F, Key.MoveHome -> { fitAll(); true }
                Key.DirectionLeft -> { pan += Offset(40f * density, 0f); true }
                Key.DirectionRight -> { pan -= Offset(40f * density, 0f); true }
                Key.DirectionUp -> { pan += Offset(0f, 40f * density); true }
                Key.DirectionDown -> { pan -= Offset(0f, 40f * density); true }
                Key.Escape -> { select(selected.dropLast(1)); true }
                Key.Enter, Key.NumPadEnter -> { openSource(selected); true }
                else -> false
            }
        }.focusable()
        .pointerHoverIcon(if (panTool || space) PointerIcon.Hand else PointerIcon.Default)
        .semantics { contentDescription = "WinUI XAML preview. Click to select; double-click or Enter to open XAML. Ctrl+wheel to zoom, wheel to pan. Space+drag or middle drag to pan. Plus/minus zoom; 0 actual size; F fit; Escape parent." }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var down: Offset? = null
                var dragged = false
                var panning = false
                var lastTap = 0L
                var lastPoint = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    when (event.type) {
                        PointerEventType.Scroll -> {
                            val delta = change.scrollDelta
                            if (event.keyboardModifiers.isCtrlPressed && delta.y != 0f) latestZoom(delta.y < 0, change.position)
                            else pan -= if (event.keyboardModifiers.isShiftPressed) Offset(delta.y * 40f, delta.x * 40f) else delta * 40f
                            change.consume()
                        }
                        PointerEventType.Press -> {
                            if (event.buttons.isPrimaryPressed || event.buttons.isTertiaryPressed) {
                                focus.requestFocus()
                                down = change.position; dragged = false
                                panning = latestPanTool || space || event.buttons.isTertiaryPressed
                                if (panning) change.consume()
                            }
                        }
                        PointerEventType.Move -> if (down != null && change.pressed) {
                            if ((change.position - down!!).getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (panning) { pan += change.position - change.previousPosition; change.consume() }
                        }
                        PointerEventType.Release -> if (down != null && !change.pressed) {
                            if (!panning && !dragged) latestHit(change.position)?.let { node ->
                                latestSelect(node.path)
                                if (change.uptimeMillis - lastTap in 1..viewConfiguration.doubleTapTimeoutMillis &&
                                    (change.position - lastPoint).getDistance() < viewConfiguration.touchSlop) {
                                    latestOpen(node.path); lastTap = 0
                                } else { lastTap = change.uptimeMillis; lastPoint = change.position }
                            }
                            down = null; change.consume()
                        }
                    }
                }
            }
        }) {
        val origin = previewOrigin(size.width, size.height, target, factor) + pan
        translate(origin.x, origin.y) { scale(factor, factor, Offset.Zero) {
            bitmap?.let { drawImage(it, dstSize = IntSize(root.width.toInt(), root.height.toInt())) }
            view.nodes.firstOrNull { it.path == selected }?.bounds?.let { bounds ->
                drawRect(Color(0xFF4BA3FF), Offset(bounds.x.toFloat(), bounds.y.toFloat()),
                    Size(bounds.width.toFloat(), bounds.height.toFloat()), style = Stroke(2.dp.toPx() / factor))
            }
        } }
        if (focused) drawRect(Color(0x554BA3FF), style = Stroke(1.dp.toPx()))
    }
}

/** Keep the same document point under the pointer, including leaving Fit selection. */
internal fun previewAnchoredPan(viewport: IntSize, oldTarget: WinUIXamlVisualBounds, newTarget: WinUIXamlVisualBounds,
    oldFactor: Float, newFactor: Float, pan: Offset, anchor: Offset): Offset {
    val oldOrigin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), oldTarget, oldFactor) + pan
    val newOrigin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), newTarget, newFactor)
    return anchor - (anchor - oldOrigin) * (newFactor / oldFactor) - newOrigin
}
