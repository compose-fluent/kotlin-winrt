package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.psi.xml.XmlFile
import io.github.composefluent.winrt.ide.templates.*
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import java.nio.file.Files
import java.nio.file.Path

/** Native component interaction, backed by the same PSI model as the file editor. */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalJewelApi::class)
class WinRTManifestDesignerTest : BasePlatformTestCase() {
    fun testTabsCommitPendingEditsAndCapabilitiesPreserveSource() {
        val original = WinRTTemplates.module(WinRTTemplateOptions("app", "sample.app", WinRTTemplateKind.WinUIApplication))
            .getValue("src/main/appxResources/AppxManifest.xml").toString(Charsets.UTF_8)
            .replace("</Package>", "<!-- keep --><custom:Unknown xmlns:custom=\"urn:custom\"/>\n</Package>")
        val xml = myFixture.addFileToProject("AppxManifest.xml", original) as XmlFile
        var snapshot by mutableStateOf(WinRTXmlForms.snapshot(xml))
        val scene = ImageComposeScene(1100, 740, Density(1f))
        try {
            scene.setContent { SwingBridgeTheme {
                Box(Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground)) {
                    WinRTManifestDesigner(snapshot, emptyList(), emptyList(), false,
                        onEdit = { field, text -> WinRTXmlForms.set(project, xml.virtualFile, field, text); snapshot = WinRTXmlForms.snapshot(xml) },
                        onRemove = { field -> WinRTXmlForms.removeEntry(project, xml.virtualFile, field); snapshot = WinRTXmlForms.snapshot(xml) },
                        onCapability = { name, restricted, device -> WinRTXmlForms.addCapability(project, xml.virtualFile, name, restricted, device); snapshot = WinRTXmlForms.snapshot(xml) },
                        onExtension = { _, _, _ -> }, onContentUri = { _, _, _ -> }, onGradle = {}, onBrowse = {}, assetPath = { null })
                }
            } }
            scene.render().close()
            assertFalse(scene.nodes().any { it.hasText("Apply") })
            assertFalse("Packaging identity must not fill the application page", scene.nodes().any { it.hasText("Publisher:") })
            screenshot(scene, "manifest-application.png")
            val input = scene.nodes().single { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains("Display Name") == true }
            assertTrue(input.config[SemanticsActions.SetText].action!!(AnnotatedString("Preview application")))
            scene.render().close()
            scene.click(scene.textNode("Visual Assets").boundsInWindow.center)
            assertEquals("Preview application", snapshot.fields.first { it.attribute == "DisplayName" }.value)
            assertNotNull(scene.textNode("Square 150 × 150 logo:"))
            screenshot(scene, "manifest-assets.png")
            scene.click(scene.textNode("Capabilities").boundsInWindow.center)
            scene.click(scene.textNode("Internet client").boundsInWindow.center)
            assertTrue(snapshot.fields.any { it.value == "internetClient" })
            scene.click(scene.textNode("Internet client").boundsInWindow.center)
            assertFalse(snapshot.fields.any { it.value == "internetClient" })
            assertTrue(xml.text.contains("<!-- keep --><custom:Unknown"))
            scene.click(scene.textNode("Packaging").boundsInWindow.center)
            assertNotNull(scene.textNode("Publisher:"))
            screenshot(scene, "manifest-packaging.png")
        } finally { scene.close() }
    }

    fun testContentUriRulesAreNamespacedAndTargetTheSelectedApplication() {
        val text = """<Package xmlns="${WinRTXmlForms.FOUNDATION}"><Applications><Application Id="A"/><Application Id="B"/></Applications><!--keep--></Package>"""
        val xml = myFixture.addFileToProject("Package.appxmanifest", text) as XmlFile
        WinRTXmlForms.addContentUriRule(project, xml.virtualFile, 1, "https://example.com", "include")
        val field = WinRTXmlForms.snapshot(xml).fields.single { it.attribute == "Match" }
        assertEquals(1, field.path.single { it.name == "Application" }.index)
        assertEquals(WinRTXmlForms.UAP, field.path.last().namespace)
        assertEquals(WinRTManifestPage.ContentUris, WinRTManifestPage.forField(field))
        assertTrue(xml.text.contains("<!--keep-->"))
        WinRTXmlForms.removeEntry(project, xml.virtualFile, field)
        assertFalse(WinRTXmlForms.snapshot(xml).fields.any { it.attribute == "Match" })
    }

    fun testAssetPreviewFindsScaleQualifiedFiles() {
        val root = Files.createTempDirectory("winrt-manifest-assets-")
        val asset = Files.createDirectories(root.resolve("Assets")).resolve("AppList.scale-200.png")
        Files.write(asset, byteArrayOf(1))
        try { assertEquals(asset, WinRTManifestAssets.resolve(root, "Assets\\AppList.png")) }
        finally { Files.deleteIfExists(asset); Files.deleteIfExists(asset.parent); Files.deleteIfExists(root) }
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
        render().close(); render().close()
    }

    private fun ImageComposeScene.textNode(text: String) = nodes().single { it.hasText(text) }
    private fun SemanticsNode.hasText(text: String) = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> {
        fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
        return semanticsOwners.flatMap { descendants(it.unmergedRootSemanticsNode) }
    }
}
