package io.github.composefluent.winrt.ide.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposePanel
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jewel.bridge.compose
import com.intellij.util.ui.UIUtil
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/** Keep the platform's XML editor, actions, navigation and persisted caret state. */
class WinUIXamlPreviewEditorProvider : TextEditorWithPreviewProvider(WinUIXamlPreviewPaneProvider()), DumbAware {
    override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor =
        WinUIXamlEditorWithPreview(firstEditor, secondEditor as WinUIXamlPreviewEditor)
}

private class WinUIXamlPreviewPaneProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (file.isDirectory || !file.extension.equals("xaml", true)) return false
        return com.intellij.openapi.application.ReadAction.compute<Boolean, RuntimeException> {
            val root = (com.intellij.psi.PsiManager.getInstance(project).findFile(file) as? com.intellij.psi.xml.XmlFile)?.rootTag
            // Resource containers have no independent visual root. Keep their normal
            // XML editor; consumers update through the shared resource document listener.
            root == null || root.namespace != io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalog.PRESENTATION ||
                root.localName !in setOf("Application", "ResourceDictionary")
        }
    }
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WinUIXamlPreviewEditor(project, file)
    override fun getEditorTypeId() = "kotlin-winrt-xaml-preview"
    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class WinUIXamlEditorWithPreview(editor: TextEditor, private val preview: WinUIXamlPreviewEditor) :
    TextEditorWithPreview(editor, preview, "Kotlin WinRT XAML", Layout.SHOW_EDITOR_AND_PREVIEW) {
    // Always expose Code / Split / Preview in the editor's top-right toolbar,
    // including when editor tabs are hidden. The content remains Compose/Jewel.
    override val isShowFloatingToolbar = false
    override val isShowActionsInTabs = false
    override val splitterProportionKey = "KotlinWinUIXamlPreview.Splitter"

    override fun getComponent(): JComponent = super.getComponent().also { updatePreviewMode() }
    override fun setState(state: FileEditorState) { super.setState(state); updatePreviewMode() }
    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) { updatePreviewMode() }
    override fun selectNotify() { super.selectNotify(); updatePreviewMode() }

    private fun updatePreviewMode() = preview.setPreviewVisible(getLayout()?.let { it != Layout.SHOW_EDITOR } == true)
}

private class WinUIXamlPreviewEditor(private val project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private var disposed = false
    private var selected = false
    private var previewVisible = false
    private var active by mutableStateOf(false)
    private val component = compose(focusOnClickInside = true) {
        if (active) WinUIXamlPreviewPanel(project, staticFile = file.path)
    }
    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName() = "Preview"
    override fun getFile() = file
    override fun setState(state: FileEditorState) = Unit
    fun setPreviewVisible(visible: Boolean) { previewVisible = visible; updateActive() }
    private fun updateActive() {
        val next = selected && previewVisible && !disposed
        if (next && !active) prepareSelectedXamlPreview(project, file.path)
        active = next
    }
    override fun selectNotify() { selected = true; updateActive() }
    override fun deselectNotify() { selected = false; updateActive() }
    override fun isModified() = false
    override fun isValid() = !disposed && file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    override fun dispose() {
        disposed = true; updateActive()
        UIUtil.findComponentOfType(component, ComposePanel::class.java)?.dispose()
    }
}
