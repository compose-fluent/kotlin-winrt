@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.composefluent.winrt.ide.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.IntSize
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import io.github.composefluent.winrt.runtime.*

/** IDE-only navigation contract; WinUI runtime drawing and XAML ownership are unchanged. */
class WinUIXamlPreviewCanvasTest : BasePlatformTestCase() {
    fun testMarqueeExcludesTemplateNodesPartialHitsAndNestedDuplicates() {
        val nodes = listOf(
            WinUIXamlVisualNode(listOf(0), "Border", "", WinUIXamlVisualBounds(20.0, 20.0, 40.0, 40.0)),
            WinUIXamlVisualNode(listOf(0, 0), "TextBlock", "", WinUIXamlVisualBounds(25.0, 25.0, 10.0, 10.0)),
            WinUIXamlVisualNode(listOf(1), "Button", "", WinUIXamlVisualBounds(80.0, 20.0, 40.0, 40.0)),
            WinUIXamlVisualNode(listOf(2), "TemplateChild", "", WinUIXamlVisualBounds(30.0, 30.0, 10.0, 10.0)))
        val authored = setOf(listOf(0), listOf(0, 0), listOf(1))
        assertEquals(setOf(listOf(0)), designerMarquee(nodes, authored, Offset(100f, 70f), Offset(10f, 10f)))
        assertEquals(setOf(listOf(0), listOf(1)), designerMarquee(nodes, authored, Offset(10f,10f), Offset(70f,70f), setOf(listOf(1))))
        assertEquals(setOf(listOf(0, 0)), designerMarquee(nodes, authored, Offset(24f,24f), Offset(36f,36f)))
    }

    fun testZoomKeepsDocumentPointUnderPointerWhenLeavingFitSelection() {
        val root = WinUIXamlVisualBounds(0.0, 0.0, 800.0, 600.0)
        val selected = WinUIXamlVisualBounds(100.0, 50.0, 200.0, 100.0)
        val viewport = IntSize(600, 400)
        val pan = Offset(27f, -18f)
        val pointer = Offset(240f, 130f)
        val oldOrigin = previewOrigin(600f, 400f, selected, 2f) + pan
        val result = previewAnchoredPan(viewport, selected, root, 2f, 1.5f, pan, pointer)
        val newOrigin = previewOrigin(600f, 400f, root, 1.5f) + result
        assertEquals((pointer.x - oldOrigin.x) / 2f, (pointer.x - newOrigin.x) / 1.5f, .001f)
        assertEquals((pointer.y - oldOrigin.y) / 2f, (pointer.y - newOrigin.y) / 1.5f, .001f)
    }

    fun testMouseSelectionHandPanAndPointerZoomShareCoordinates() {
        val scene = ImageComposeScene(400, 400)
        var reset by mutableIntStateOf(0)
        var hand by mutableStateOf(false)
        var zoom by mutableStateOf<Float?>(null)
        var selected by mutableStateOf<List<Int>>(emptyList())
        val view = WinUIXamlVisualSnapshot(listOf(
            WinUIXamlVisualNode(emptyList(), "Grid", "", WinUIXamlVisualBounds(0.0, 0.0, 200.0, 200.0)),
            WinUIXamlVisualNode(listOf(0), "Button", "Test", WinUIXamlVisualBounds(20.0, 20.0, 40.0, 40.0))),
            emptyList(), WinUIXamlVisualImage(1, 1, byteArrayOf(-1,-1,-1,-1)))
        var time = System.nanoTime()
        fun settle() = repeat(15) { Thread.sleep(10); time += 20_000_000; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); scene.render(time).close(); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
        fun click(point: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, point, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Release, point, buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }
        fun drag() {
            scene.sendPointerEvent(PointerEventType.Press, Offset(60f,60f), buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.sendPointerEvent(PointerEventType.Move, Offset(160f,60f), buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, Offset(160f,60f), buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }
        try {
            scene.setContent { PreviewImage(view, selected, { selected = it }, {}, zoom, false, hand, {}, { zoom = it }, { zoom = null; reset++ }, Modifier.fillMaxSize(), reset) }
            settle()
            click(Offset(60f,60f)); assertEquals(listOf(0), selected)
            drag(); selected = emptyList(); settle()
            click(Offset(60f,60f)); assertEquals("Selection drag must not move the canvas", listOf(0), selected)
            reset++; settle() // Fit must not detach the active gesture coroutine from pan state.
            hand = true; settle(); drag()
            hand = false; selected = emptyList(); settle()
            click(Offset(160f,60f)); assertEquals("Hand drag moves the hit-test coordinates with the canvas", listOf(0), selected)
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(160f,60f), scrollDelta = Offset(0f,-1f),
                keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true))
            settle(); assertEquals(3f, zoom)
            selected = emptyList(); settle(); click(Offset(160f,60f))
            assertEquals("Pointer-anchored zoom retains the same selected element", listOf(0), selected)
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(160f,60f), scrollDelta = Offset(0f,-1f),
                keyboardModifiers = PointerKeyboardModifiers(isCtrlPressed = true))
            settle(); assertEquals("Consecutive wheel events use the updated zoom", 4f, zoom)
        } finally { scene.close() }
    }
    fun testLayoutGestureCommitsOnceInDipAndHandleResizes() {
        val scene = ImageComposeScene(400, 400)
        val changes = mutableListOf<DesignerTransform>()
        val view = WinUIXamlVisualSnapshot(listOf(
            WinUIXamlVisualNode(emptyList(), "Grid", "", WinUIXamlVisualBounds(0.0, 0.0, 200.0, 200.0)),
            WinUIXamlVisualNode(listOf(0), "Button", "", WinUIXamlVisualBounds(20.0, 20.0, 40.0, 40.0))),
            emptyList(), WinUIXamlVisualImage(1, 1, byteArrayOf(-1, -1, -1, -1)))
        var time = System.nanoTime()
        fun settle() = repeat(15) { Thread.sleep(10); time += 20_000_000; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications(); scene.render(time).close(); PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
        fun drag(from: Offset, to: Offset) {
            scene.sendPointerEvent(PointerEventType.Press, from, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            settle()
            scene.sendPointerEvent(PointerEventType.Move, to, buttons = PointerButtons(isPrimaryPressed = true))
            settle()
            assertEquals("No source writes while pointer is held", 0, changes.size)
            scene.sendPointerEvent(PointerEventType.Release, to, buttons = PointerButtons(), button = PointerButton.Primary)
            settle()
        }
        try {
            scene.setContent { PreviewImage(view, listOf(0), {}, {}, null, false, false, {}, {}, {}, Modifier.fillMaxSize(), editable = true, changeLayout = changes::add) }
            settle(); drag(Offset(80f, 80f), Offset(100f, 110f))
            assertEquals(listOf(DesignerTransform(10.0, 15.0)), changes)
            changes.clear(); drag(Offset(120f, 120f), Offset(160f, 140f))
            assertEquals(listOf(DesignerTransform(0.0, 0.0, 20.0, 10.0, true)), changes)
        } finally { scene.close() }
    }
}
