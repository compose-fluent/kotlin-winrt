package io.github.composefluent.winrt.ide.templates

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme

@OptIn(ExperimentalComposeUiApi::class)
class WinRTNewXamlFormTest : BasePlatformTestCase() {
    fun testTypingANameAndShowingAnErrorKeepsTheFooterAtFullHeight() {
        val name = TextFieldState()
        val scene = ImageComposeScene(460, 340, Density(1f))
        try {
            scene.setContent { SwingBridgeTheme {
                WinRTNewXamlForm(WinRTXamlFileKind.Page, "E:/very/long/project/directory/".repeat(5), name, TextFieldState("sample.views"),
                    "A file with this name already exists. ".repeat(5).takeIf { name.text.isNotEmpty() }, false, name.text.isNotEmpty(), {}, {})
            } }
            scene.render().close()
            val initial = scene.label("Create").boundsInWindow
            name.edit { append("DetailsPageWithALongName") }
            scene.render().close(); scene.render().close()
            val after = scene.label("Create").boundsInWindow
            assertEquals(initial.top, after.top, 1f)
            assertEquals(initial.height, after.height, 1f)
            assertTrue("Create must remain fully visible", after.bottom < 340f)
            assertTrue("Cancel must remain fully visible", scene.label("Cancel").boundsInWindow.bottom < 340f)
        } finally { scene.close() }
    }
    private fun ImageComposeScene.label(text: String): SemanticsNode {
        fun nodes(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::nodes)
        return semanticsOwners.flatMap { nodes(it.unmergedRootSemanticsNode) }.single {
            it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { value -> value.text == text }
        }
    }
}
