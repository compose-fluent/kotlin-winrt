package io.github.composefluent.winrt.ide.nuget

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import java.nio.file.Files
import java.nio.file.Path

/** Offscreen interactions with the production Jewel package list. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
class WinRTNuGetPackageListTest : BasePlatformTestCase() {
    fun testReadmeUsesTheIdeMarkdownRenderer() {
        val scene = ImageComposeScene(720, 600, Density(1f))
        try {
            scene.setContent { SwingBridgeTheme {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    WinRTNuGetReadme(project, "# Package guide\n\nUse **native controls**.\n\n- Install the package\n- Add a reference", false, null)
                }
            } }
            kotlinx.coroutines.runBlocking {
                repeat(30) { scene.render().close(); kotlinx.coroutines.delay(20) }
            }
            assertNotNull(scene.textNode("Package guide"))
            assertFalse(scene.nodes().any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text.contains("**") } })
            screenshot(scene, "nuget-readme.png")
        } finally { scene.close() }
    }

    fun testWholeRowsSelectPackagesButGroupHeadersDoNot() {
        var selected by mutableStateOf("Direct.Package")
        val packages = listOf(
            WinRTNuGetListPackage("Direct.Package", "2.0", group = "Top-level packages"),
            WinRTNuGetListPackage("Dependency.Package", "1.0", group = "Transitive packages"),
        )
        val scene = ImageComposeScene(600, 400, Density(1f))
        try {
            scene.setContent {
                SwingBridgeTheme {
                    Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                        WinRTNuGetPackageList(packages, selected, Modifier.fillMaxSize()) { selected = it.id }
                    }
                }
            }
            scene.render().close()
            scene.click(scene.textNode("Dependency.Package").boundsInWindow.center)
            assertEquals("Dependency.Package", selected)
            scene.click(scene.textNode("Top-level packages (1)").boundsInWindow.center)
            assertEquals("Headers must not act like package entries", "Dependency.Package", selected)
            scene.click(scene.textNode("Direct.Package").boundsInWindow.center)
            assertEquals("Direct.Package", selected)
            assertTrue("Native selected-row semantics must follow the selected package", scene.nodes().any {
                it.config.getOrNull(SemanticsProperties.Selected) == true
            })
            screenshot(scene, "nuget-package-list.png")
        } finally { scene.close() }
    }

    fun testManagerChangesViewsThroughNativeTabs() {
        val scene = ImageComposeScene(1100, 700, Density(1f))
        try {
            scene.setContent { SwingBridgeTheme {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) { WinRTNuGetPanel(project) }
            } }
            scene.render().close()
            scene.click(scene.textNode("Installed").boundsInWindow.center)
            assertNotNull(scene.textNode("No installed packages match this filter."))
            scene.click(scene.textNode("Updates").boundsInWindow.center)
            assertNotNull(scene.textNode("No updates available from this source."))
            screenshot(scene, "nuget-manager.png")
        } finally { scene.close() }
    }

    private fun screenshot(scene: ImageComposeScene, name: String) {
        System.getProperty("winrt.ide.uiScreenshotDirectory")?.let { directory ->
            val target = Files.createDirectories(Path.of(directory)).resolve(name)
            scene.render().use { image -> image.encodeToData()!!.use { Files.write(target, it.bytes) } }
        }
    }

    private fun ImageComposeScene.click(position: Offset) {
        sendPointerEvent(PointerEventType.Press, position, button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, button = PointerButton.Primary)
        render().close()
        render().close()
    }

    private fun ImageComposeScene.textNode(text: String) = nodes().single { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    }

    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun children(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::children)
        return semanticsOwners.flatMap { children(it.unmergedRootSemanticsNode) }
    }
}
