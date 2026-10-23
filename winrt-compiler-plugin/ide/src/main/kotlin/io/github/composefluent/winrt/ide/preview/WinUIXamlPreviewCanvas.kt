@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Canvas gestures report DIP transforms; the IDE source editor owns XAML writes. */
@Composable
internal fun PreviewImage(view: WinUIXamlVisualSnapshot, selected: List<Int>, select: (List<Int>) -> Unit,
    openSource: (List<Int>) -> Unit, zoom: Float?, fitSelection: Boolean, panTool: Boolean,
    onZoom: (Float) -> Unit, setZoom: (Float) -> Unit, fitAll: () -> Unit, modifier: Modifier, resetViewport: Int = 0, editable: Boolean = false, changeLayout: (DesignerTransform) -> Unit = {},
    selectedPaths: Set<List<Int>> = setOf(selected), toggleSelection: (List<Int>) -> Unit = select,
    snapEnabled: Boolean = true, armedTool: String? = null, toolDrop: DesignerToolDrop? = null,
    finishDrop: () -> Unit = {}, insert: (String, List<Int>, Double, Double) -> Unit = { _, _, _, _ -> },
    cancelTool: () -> Unit = {}, selectSiblings: () -> Unit = {}, sourcePaths: Set<List<Int>>? = null, contextActions: List<Pair<String, () -> Unit>> = emptyList(), selectArea: (Set<List<Int>>) -> Unit = {}, clipboardAction: (String) -> Unit = {}, panState: MutableState<Offset>? = null) {
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
    var canvasOrigin by remember { mutableStateOf(Offset.Zero) }
    val effectiveZoom by remember(target, density, zoom, fitSelection) { derivedStateOf {
        previewScale(viewport.width, viewport.height, target.width, target.height, density, zoom)
            .let { if (fitSelection) it.coerceAtMost(8f) else it }
    } }
    val reportedZoom = effectiveZoom
    SideEffect { onZoom(reportedZoom) }
    val localPan = remember { mutableStateOf(Offset.Zero) }
    val activePan: MutableState<Offset> = panState ?: localPan
    var pan by activePan
    if (panState == null) LaunchedEffect(resetViewport) { pan = Offset.Zero }
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
        val hit = WinUIXamlVisualInspectionSelection.hit(currentNodes, (point.x - origin.x) / currentFactor.toDouble(),
            (point.y - origin.y) / currentFactor.toDouble())
        if (hit == null || sourcePaths == null) hit else currentNodes.filter { it.path in sourcePaths && hit.path.take(it.path.size) == it.path }.maxByOrNull { it.path.size }
    })
    val latestSourcePaths by rememberUpdatedState(sourcePaths)
    val latestSelectArea by rememberUpdatedState(selectArea)
    var marquee by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
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
    val latestEditable by rememberUpdatedState(editable)
    val latestSelected by rememberUpdatedState(selected)
    val latestLayout by rememberUpdatedState(changeLayout)
    val latestSelectedPaths by rememberUpdatedState(selectedPaths)
    val latestToggle by rememberUpdatedState(toggleSelection)
    val latestSnap by rememberUpdatedState(snapEnabled)
    val latestTool by rememberUpdatedState(armedTool)
    val latestInsert by rememberUpdatedState(insert)
    fun dropTool(type: String, point: Offset) {
        val active = previewScale(viewport.width, viewport.height, currentTarget.width, currentTarget.height, density, currentZoom)
            .let { if (currentFitSelection) it.coerceAtMost(8f) else it } * density
        val origin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), currentTarget, active) + pan
        latestHit(point)?.let { node -> latestInsert(type, node.path, ((point.x - origin.x) / active).toDouble(), ((point.y - origin.y) / active).toDouble()) }
    }
    val latestDrop by rememberUpdatedState<(String, Offset) -> Unit>(::dropTool)
    LaunchedEffect(toolDrop) {
        toolDrop?.let { drop ->
            val point = drop.point - canvasOrigin
            if (point.x in 0f..viewport.width.toFloat() && point.y in 0f..viewport.height.toFloat()) latestDrop(drop.type, point)
            finishDrop()
        }
    }
    var guides by remember { mutableStateOf<DesignerSnap?>(null) }
    var manipulation by remember { mutableStateOf<DesignerTransform?>(null) }
    var cancelDrag by remember { mutableStateOf(false) }
    var hoverPoint by remember { mutableStateOf<Offset?>(null) }
    val hoveredHandle = if (!editable || selectedPaths.size != 1 || panTool || space) null else view.nodes.firstOrNull { it.path == selected }?.bounds?.let { bounds ->
        val origin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), target, factor) + pan
        hoverPoint?.let { point -> designerHandles.firstOrNull { (hx, hy) ->
            (origin + Offset((bounds.x + bounds.width * hx / 2).toFloat(), (bounds.y + bounds.height * hy / 2).toFloat()) * factor - point).getDistance() <= 7 * density
        } }
    }
    val cursor = when {
        armedTool != null -> PointerIcon.Crosshair
        panTool || space -> PointerIcon.Hand
        hoveredHandle != null -> PointerIcon(java.awt.Cursor.getPredefinedCursor(when {
            hoveredHandle.first == 1 -> java.awt.Cursor.N_RESIZE_CURSOR
            hoveredHandle.second == 1 -> java.awt.Cursor.E_RESIZE_CURSOR
            hoveredHandle.first == hoveredHandle.second -> java.awt.Cursor.NW_RESIZE_CURSOR
            else -> java.awt.Cursor.NE_RESIZE_CURSOR
        }))
        else -> PointerIcon.Default
    }
    ContextMenuArea(items = { contextActions.map { (label, action) -> ContextMenuItem(label, action) } }) {
    Canvas(modifier.onGloballyPositioned { canvasOrigin = it.positionInRoot() }.onSizeChanged { viewport = it }.clipToBounds()
        .focusRequester(focus).onFocusChanged { focused = it.isFocused; if (!focused) space = false }
        .onKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && editable && event.isCtrlPressed && event.key in listOf(Key.C, Key.X, Key.V)) {
                clipboardAction(if (event.key == Key.C) "copy" else if (event.key == Key.X) "cut" else "paste"); true
            } else if (event.type == KeyEventType.KeyDown && editable && event.key == Key.Delete) { clipboardAction("delete"); true }
            else if (event.isCtrlPressed && event.key == Key.A && event.type == KeyEventType.KeyDown) { selectSiblings(); true }
            else if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) false
            else if (event.key == Key.Spacebar) { space = event.type == KeyEventType.KeyDown; true }
            else if (event.type != KeyEventType.KeyDown) false
            else if (editable && !panTool && event.key in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
                val step = if (event.isShiftPressed) 10.0 else 1.0
                changeLayout(DesignerTransform(if (event.key == Key.DirectionLeft) -step else if (event.key == Key.DirectionRight) step else 0.0,
                    if (event.key == Key.DirectionUp) -step else if (event.key == Key.DirectionDown) step else 0.0)); true
            }
            else when (event.key) {
                Key.Equals, Key.Plus, Key.NumPadAdd -> { zoomAt(previewZoomStep(effectiveZoom, true), Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.Minus, Key.NumPadSubtract -> { zoomAt(previewZoomStep(effectiveZoom, false), Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.Zero, Key.NumPad0 -> { zoomAt(1f, Offset(viewport.width / 2f, viewport.height / 2f)); true }
                Key.F, Key.MoveHome -> { fitAll(); true }
                Key.DirectionLeft -> { pan += Offset(40f * density, 0f); true }
                Key.DirectionRight -> { pan -= Offset(40f * density, 0f); true }
                Key.DirectionUp -> { pan += Offset(0f, 40f * density); true }
                Key.DirectionDown -> { pan -= Offset(0f, 40f * density); true }
                Key.Escape -> { if (armedTool != null) cancelTool() else if (manipulation != null || marquee != null) { cancelDrag = true; manipulation = null; marquee = null } else select(selected.dropLast(1)); true }
                Key.Enter, Key.NumPadEnter -> { openSource(selected); true }
                else -> false
            }
        }.focusable()
        .pointerHoverIcon(cursor)
        .semantics { contentDescription = "WinUI XAML preview. Click to select; Ctrl+click or Shift+click toggles multiple elements; Alt disables snapping; double-click or Enter to open XAML. Ctrl+wheel to zoom, wheel to pan. Space+drag or middle drag to pan. Plus/minus zoom; 0 actual size; F fit; Drag the selected element or its resize handles to edit layout. Arrows nudge; Shift+arrows nudge ten DIP. Escape cancels a drag or selects the parent." }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var down: Offset? = null
                var dragged = false
                var panning = false
                var layoutDrag = false
                var areaDrag = false
                var areaOrigin = Offset.Zero
                var areaSelection = emptySet<List<Int>>()
                var multiple = false
                var handle: Pair<Int, Int>? = null
                var dragFactor = 1f
                var dragBounds: WinUIXamlVisualBounds? = null
                var lastTap = 0L
                var lastPoint = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: continue
                    hoverPoint = if (event.type == PointerEventType.Exit) null else change.position
                    when (event.type) {
                        PointerEventType.Scroll -> {
                            val delta = change.scrollDelta
                            if (event.keyboardModifiers.isCtrlPressed && delta.y != 0f) latestZoom(delta.y < 0, change.position)
                            else pan -= if (event.keyboardModifiers.isShiftPressed) Offset(delta.y * 40f, delta.x * 40f) else delta * 40f
                            change.consume()
                        }
                        PointerEventType.Press -> {
                            if (event.buttons.isSecondaryPressed) latestHit(change.position)?.let { hit ->
                                if (hit.path !in latestSelectedPaths) latestSelect(hit.path)
                            }
                            if (event.buttons.isPrimaryPressed || event.buttons.isTertiaryPressed) {
                                focus.requestFocus()
                                down = change.position; dragged = false
                                panning = latestPanTool || space || event.buttons.isTertiaryPressed
                                cancelDrag = false; manipulation = null; guides = null; layoutDrag = false; handle = null; areaDrag = false; marquee = null
                                multiple = event.keyboardModifiers.isCtrlPressed || event.keyboardModifiers.isShiftPressed
                                if (!panning) {
                                    val active = previewScale(viewport.width, viewport.height, currentTarget.width, currentTarget.height, density, currentZoom)
                                        .let { if (currentFitSelection) it.coerceAtMost(8f) else it }
                                    dragFactor = active * density
                                    val bounds = currentNodes.firstOrNull { it.path == latestSelected }?.bounds
                                    val origin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), currentTarget, dragFactor) + pan
                                    if (latestEditable && latestSelectedPaths.size == 1 && bounds != null) {
                                        handle = designerHandles.firstOrNull { (hx, hy) ->
                                            val position = origin + Offset((bounds.x + bounds.width * hx / 2).toFloat(), (bounds.y + bounds.height * hy / 2).toFloat()) * dragFactor
                                            (position - change.position).getDistance() <= 7 * density
                                        }
                                    }
                                    val hit = latestHit(change.position)
                                    areaDrag = latestSourcePaths != null && handle == null && latestTool == null &&
                                        (hit == null || (hit.path !in latestSelectedPaths || multiple) && currentNodes.any { it.path in latestSourcePaths.orEmpty() && it.path.size > hit.path.size && it.path.take(hit.path.size) == hit.path })
                                    areaOrigin = origin
                                    areaSelection = if (multiple) latestSelectedPaths else emptySet()
                                    if (latestTool != null || areaDrag) { /* Defer insertion or marquee selection until release. */ }
                                    else if (multiple && hit != null) latestToggle(hit.path)
                                    else if (handle != null || hit?.path in latestSelectedPaths) {
                                        layoutDrag = latestEditable; dragBounds = bounds
                                    } else if (hit != null) latestSelect(hit.path)
                                }
                                if (panning || layoutDrag) change.consume()
                            }
                        }
                        // Re-entering the canvas while held continues the same gesture.
                        PointerEventType.Move, PointerEventType.Enter -> if (down != null && change.pressed) {
                            if ((change.position - down!!).getDistance() > viewConfiguration.touchSlop) dragged = true
                            if (panning) { pan += change.position - change.previousPosition; change.consume() }
                            else if (areaDrag && dragged && !cancelDrag) { marquee = down!! to change.position; change.consume() }
                            else if (layoutDrag && dragged && !cancelDrag) {
                                val delta = (change.position - down!!) / dragFactor
                                val bounds = dragBounds!!
                                val raw = designerTransform(handle, delta.x.toDouble(), delta.y.toDouble(), bounds)
                                guides = if (latestSnap && !event.keyboardModifiers.isAltPressed) designerSnap(bounds, raw,
                                    currentNodes.filter { it.path !in latestSelectedPaths && it.path.dropLast(1) == latestSelected.dropLast(1) }.map { it.bounds },
                                    6.0 * density / dragFactor) else DesignerSnap(raw)
                                manipulation = guides!!.change
                                change.consume()
                            }
                        }
                        PointerEventType.Release -> if (down != null && !change.pressed) {
                            if (areaDrag && dragged && !cancelDrag) {
                                val a = (down!! - areaOrigin) / dragFactor
                                val b = (change.position - areaOrigin) / dragFactor
                                latestSelectArea(designerMarquee(currentNodes, latestSourcePaths.orEmpty(), a, b, areaSelection))
                            } else if (layoutDrag && dragged && !cancelDrag) manipulation?.let(latestLayout)
                            manipulation = null; guides = null; marquee = null
                            if (!panning && !dragged && latestTool != null) latestDrop(latestTool!!, change.position)
                            else if (!panning && !dragged && !multiple) latestHit(change.position)?.let { node ->
                                if (latestSelectedPaths.size != 1 || node.path !in latestSelectedPaths) latestSelect(node.path)
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
            guides?.let { snap ->
                snap.vertical?.let { x -> drawLine(Color(0xFFDC64CA), Offset(x.toFloat(), 0f), Offset(x.toFloat(), root.height.toFloat()), 1.dp.toPx() / factor) }
                snap.horizontal?.let { y -> drawLine(Color(0xFFDC64CA), Offset(0f, y.toFloat()), Offset(root.width.toFloat(), y.toFloat()), 1.dp.toPx() / factor) }
            }
            view.nodes.filter { it.path in selectedPaths }.forEach { node ->
                val original = node.bounds
                val edit = manipulation
                val bounds = if (edit == null) original else original.copy(x = original.x + edit.x, y = original.y + edit.y,
                    width = original.width + edit.width, height = original.height + edit.height)
                drawRect(Color(0xFF4BA3FF), Offset(bounds.x.toFloat(), bounds.y.toFloat()),
                    Size(bounds.width.toFloat(), bounds.height.toFloat()), style = Stroke(2.dp.toPx() / factor))
                if (editable && selectedPaths.size == 1) designerHandles.forEach { (hx, hy) ->
                    val center = Offset((bounds.x + bounds.width * hx / 2).toFloat(), (bounds.y + bounds.height * hy / 2).toFloat())
                    val side = 7.dp.toPx() / factor
                    drawRect(Color.White, center - Offset(side / 2, side / 2), Size(side, side))
                    drawRect(Color(0xFF4BA3FF), center - Offset(side / 2, side / 2), Size(side, side), style = Stroke(1.dp.toPx() / factor))
                }
            }
        } }
        marquee?.let { (a, b) ->
            val topLeft = Offset(minOf(a.x, b.x), minOf(a.y, b.y))
            val extent = Size(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.y - a.y))
            drawRect(Color(0x224BA3FF), topLeft, extent)
            drawRect(Color(0xFF4BA3FF), topLeft, extent, style = Stroke(1.dp.toPx()))
        }
        if (focused) drawRect(Color(0x554BA3FF), style = Stroke(1.dp.toPx()))
    }
    }
}

/** Keep the same document point under the pointer, including leaving Fit selection. */
internal fun previewAnchoredPan(viewport: IntSize, oldTarget: WinUIXamlVisualBounds, newTarget: WinUIXamlVisualBounds,
    oldFactor: Float, newFactor: Float, pan: Offset, anchor: Offset): Offset {
    val oldOrigin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), oldTarget, oldFactor) + pan
    val newOrigin = previewOrigin(viewport.width.toFloat(), viewport.height.toFloat(), newTarget, newFactor)
    return anchor - (anchor - oldOrigin) * (newFactor / oldFactor) - newOrigin
}

internal val designerHandles = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2)
internal fun designerTransform(handle: Pair<Int, Int>?, dx: Double, dy: Double, bounds: WinUIXamlVisualBounds): DesignerTransform {
    if (handle == null) return DesignerTransform(dx, dy)
    val x = if (handle.first == 0) dx.coerceAtMost(bounds.width - 1) else 0.0
    val y = if (handle.second == 0) dy.coerceAtMost(bounds.height - 1) else 0.0
    val width = if (handle.first == 0) -x else if (handle.first == 2) dx.coerceAtLeast(1 - bounds.width) else 0.0
    val height = if (handle.second == 0) -y else if (handle.second == 2) dy.coerceAtLeast(1 - bounds.height) else 0.0
    return DesignerTransform(x, y, width, height, true)
}
/** Fully enclosed authored elements; never select both an ancestor and its descendants. */
internal fun designerMarquee(nodes: List<WinUIXamlVisualNode>, authored: Set<List<Int>>, a: Offset, b: Offset,
    previous: Set<List<Int>> = emptySet()): Set<List<Int>> {
    val left = minOf(a.x, b.x); val right = maxOf(a.x, b.x)
    val top = minOf(a.y, b.y); val bottom = maxOf(a.y, b.y)
    val hits = nodes.filter { node -> node.path in authored && node.bounds.let {
        it.width > 0 && it.height > 0 && it.x >= left && it.y >= top && it.x + it.width <= right && it.y + it.height <= bottom
    } }.map { it.path }.toSet()
    val combined = previous + hits
    return combined.filter { path -> combined.none { parent -> parent.size < path.size && path.take(parent.size) == parent } }.toSet()
}
