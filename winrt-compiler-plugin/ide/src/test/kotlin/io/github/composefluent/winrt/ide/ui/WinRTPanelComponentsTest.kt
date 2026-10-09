package io.github.composefluent.winrt.ide.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme

/** Exercise the shared selection control through Jewel's actual popup and
 * Compose pointer dispatch. These are offscreen plugin components, not inputs
 * to a user's desktop. Studio owns the native gallery cell styling. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
class WinRTPanelComponentsTest : BasePlatformTestCase() {
    fun testChoiceDismissesOutsideAndCanReopenWithoutChangingSelection() {
        var selected by mutableStateOf("first")
        val scene = ImageComposeScene(600, 400, Density(1f))
        try {
            scene.setContent {
                SwingBridgeTheme {
                    Column(Modifier.fillMaxSize()) {
                        Column(Modifier.width(300.dp)) {
                            WinRTChoice("SDK version", listOf("first" to "Stable", "second" to "Preview"), selected) {
                                selected = it
                            }
                        }
                    }
                }
            }
            scene.render().close()
            val anchor = scene.nodes().single {
                it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("SDK version") == true
            }.boundsInWindow.center
            scene.click(anchor)
            assertTrue(scene.nodes().toString(), scene.nodes().any { it.hasText("Preview") })

            scene.click(Offset(500f, 300f))
            assertFalse("Clicking empty form space must dismiss the popup", scene.nodes().any { it.hasText("Preview") })
            assertEquals("first", selected)

            scene.click(anchor)
            assertTrue("The selector must reopen after an outside click", scene.nodes().any { it.hasText("Preview") })
            val second = scene.nodes().single { it.hasText("Preview") }.boundsInWindow.center
            scene.click(second)
            assertEquals("Selection must return the item key rather than its displayed title", "second", selected)
            assertFalse("Selecting a value must close the popup", scene.nodes().any { it.hasText("Stable") })
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.click(position: Offset) {
        sendPointerEvent(PointerEventType.Press, position, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, button = PointerButton.Primary)
        render().close()
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
    }

    private fun SemanticsNode.hasText(text: String): Boolean =
        config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
}
