package io.github.composefluent.winrt.ide.preview

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.TextEditorWithPreview.Layout
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadService
import io.github.composefluent.winrt.ide.hotreload.WinRTStaticPreviewService
import org.jdom.Element
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme

/** Editor integration follows the platform's TextEditorWithPreview contract;
 * the actual WinUI renderer is exercised separately by WinRTPreviewRuntimeTest. */
class WinUIXamlPreviewEditorTest : BasePlatformTestCase() {
    private var previousLayout: String? = null
    override fun setUp() {
        super.setUp()
        previousLayout = PropertiesComponent.getInstance().getValue("Kotlin WinRT XAMLLayout")
        PropertiesComponent.getInstance().unsetValue("Kotlin WinRT XAMLLayout")
    }
    override fun tearDown() {
        try { PropertiesComponent.getInstance().setValue("Kotlin WinRT XAMLLayout", previousLayout) }
        finally { super.tearDown() }
    }
    private val markup = """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><TextBlock Text="Preview"/></Page>"""

    fun testPreviewIsRegisteredForXaml() {
        val file = myFixture.addFileToProject("Preview.xaml", markup).virtualFile
        assertTrue(FileEditorProviderManager.getInstance().getProviders(project, file).any { it is WinUIXamlPreviewEditorProvider })
    }

    fun testNativeEditorHasPersistentTopRightModeActionsAndKeepsItsXmlDocument() {
        val file = myFixture.addFileToProject("Page.xaml", markup).virtualFile
        // LightFileEditorManager deliberately bypasses editor providers. Use
        // the actual registered provider and its native split-editor factory.
        val provider = WinUIXamlPreviewEditorProvider()
        val editor = provider.createEditor(project, file) as TextEditorWithPreview
        editor.selectNotify()
        try {
            editor.component
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals("New XAML documents open beside their automatic designer", Layout.SHOW_EDITOR_AND_PREVIEW, editor.getLayout())
            assertSame(FileDocumentManager.getInstance().getDocument(file), editor.editor.document)
            assertSame(editor, TextEditorWithPreview.getParentSplitEditor(editor.textEditor))
            assertSame(editor, TextEditorWithPreview.getParentSplitEditor(editor.previewEditor))

            val toolbars = UIUtil.uiTraverser(editor.component).filter(ActionToolbar::class.java).toList()
            val modes = listOf("EditorOnly", "EditorAndPreview", "PreviewOnly").map {
                ActionManager.getInstance().getAction("TextEditorWithPreview.Layout.$it")
            }
            assertTrue("All three native mode actions must be visible in the editor toolbar", toolbars.any { toolbar ->
                toolbar.component.isVisible && toolbar.actionGroup.getChildren(null).toList().containsAll(modes)
            })
            val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project)
                .add(PlatformDataKeys.FILE_EDITOR, editor).build()
            fun choose(index: Int) = modes[index].actionPerformed(AnActionEvent.createFromAnAction(
                modes[index], null, ActionPlaces.TEXT_EDITOR_WITH_PREVIEW, context))
            choose(0)
            assertEquals(Layout.SHOW_EDITOR, editor.getLayout())
            assertTrue(editor.textEditor.component.isVisible)
            assertFalse(editor.previewEditor.component.isVisible)
            choose(1)
            assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, editor.getLayout())
            assertTrue(editor.textEditor.component.isVisible && editor.previewEditor.component.isVisible)
            choose(2)
            assertEquals(Layout.SHOW_PREVIEW, editor.getLayout())
            assertFalse(editor.textEditor.component.isVisible)
            assertTrue(editor.previewEditor.component.isVisible)
            choose(0)
            assertSame(editor.textEditor.preferredFocusedComponent, editor.preferredFocusedComponent)
        } finally { editor.deselectNotify(); provider.disposeEditor(editor) }
    }

    fun testNativeStateRoundTripRestoresPreviewModeAndCaret() {
        val file = myFixture.addFileToProject("Window.xaml", markup).virtualFile
        val provider = WinUIXamlPreviewEditorProvider()
        assertEquals(FileEditorPolicy.HIDE_DEFAULT_EDITOR, provider.policy)
        val first = provider.createEditor(project, file) as TextEditorWithPreview
        val saved = Element("state")
        try {
            first.component
            first.editor.caretModel.moveToOffset(markup.indexOf("TextBlock"))
            first.setLayout(Layout.SHOW_EDITOR_AND_PREVIEW)
            provider.writeState(first.getState(FileEditorStateLevel.FULL), project, saved)
        } finally { provider.disposeEditor(first) }
        val reopened = provider.createEditor(project, file) as TextEditorWithPreview
        try {
            reopened.setState(provider.readState(saved, project, file))
            reopened.component
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            assertEquals(Layout.SHOW_EDITOR_AND_PREVIEW, reopened.getLayout())
            assertTrue(reopened.textEditor.component.isVisible && reopened.previewEditor.component.isVisible)
            assertEquals(markup.indexOf("TextBlock"), reopened.editor.caretModel.offset)
            reopened.setLayout(Layout.SHOW_EDITOR)
        } finally { provider.disposeEditor(reopened) }
    }

    fun testBothNativePreviewServicesInitializeAndRemainIndependent() {
        // Regression for the packaged UI's NoSuchFieldError during composition.
        val live = project.service<WinUIXamlHotReloadService>()
        val design = project.service<WinRTStaticPreviewService>()
        assertFalse(live.state.value.connected)
        assertFalse(design.state.value.connected)
        assertNotSame(live.state, design.state)
        assertFalse(design.automatic.value)
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun testPreviewOptionsRemainReachableAtDoubleScale() {
        val file = myFixture.addFileToProject("Scaled.xaml", markup).virtualFile
        val scene = ImageComposeScene(600, 800, Density(2f))
        try {
            scene.setContent { SwingBridgeTheme { WinUIXamlPreviewPanel(project, staticFile = file.path) } }
            fun nodes(): List<SemanticsNode> {
                fun visit(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::visit)
                return scene.semanticsOwners.flatMap { visit(it.unmergedRootSemanticsNode) }
            }
            fun labels(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().map { it.text } + node.children.flatMap(::labels)
            scene.render().close()
            assertFalse("Dimensions stay out of the default canvas workspace", nodes().any { "Preview width in DIP" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
            val expand = nodes().first { "Settings" in labels(it) && it.config.getOrNull(SemanticsActions.OnClick) != null }
            assertTrue(expand.config[SemanticsActions.OnClick].action!!.invoke())
            scene.render().close()
            val scroll = nodes().single { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
            assertTrue("Expanded options must scroll in a small high-DPI viewport", scroll.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
            assertTrue(scroll.config[SemanticsActions.ScrollBy].action!!.invoke(0f, 2000f))
            scene.render().close()
            scene.render().close()
            val lastOption = nodes().first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == "Visual Tree and properties" } }
            assertTrue("The final option must be visible after scrolling", lastOption.boundsInWindow.height > 0f && lastOption.boundsInWindow.bottom <= 800f)
            assertTrue(nodes().any { "Preview width in DIP" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
            assertTrue(nodes().any { "Preview height in DIP" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
        } finally { scene.close() }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    fun testActualPreviewCompositionRendersItsRetryActionAndMissingModuleGuidance() {
        val file = myFixture.addFileToProject("Design.xaml", markup).virtualFile
        // Render this plugin's own UI offscreen; do not capture a user desktop.
        val scene = ImageComposeScene(800, 600, Density(1f))
        try {
            scene.setContent { SwingBridgeTheme { WinUIXamlPreviewPanel(project, staticFile = file.path) } }
            scene.render().use { image -> assertEquals(800, image.width); assertEquals(600, image.height) }
            fun text(node: SemanticsNode): List<String> = node.config.getOrNull(SemanticsProperties.Text)
                .orEmpty().map { it.text } + node.children.flatMap(::text)
            val labels = scene.semanticsOwners.flatMap { text(it.unmergedRootSemanticsNode) }
            assertContainsElements(labels, "Retry preview", "Design.xaml")
            assertFalse(labels.contains("Build & Refresh"))
            assertTrue(labels.toString(), labels.any { it.startsWith("Synchronize a Kotlin WinRT module") })
        } finally { scene.close() }
    }
}
