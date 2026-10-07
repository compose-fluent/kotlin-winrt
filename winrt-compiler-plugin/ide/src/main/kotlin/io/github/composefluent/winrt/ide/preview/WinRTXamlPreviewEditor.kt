package io.github.composefluent.winrt.ide.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jewel.bridge.compose
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class WinRTXamlPreviewEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile) = !file.isDirectory && file.extension.equals("xaml", true)
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WinRTXamlPreviewEditor(project, file)
    override fun getEditorTypeId() = "kotlin-winrt-xaml-preview"
    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class WinRTXamlPreviewEditor(project: Project, private val file: VirtualFile) : UserDataHolderBase(), FileEditor {
    private var disposed = false
    private var active by mutableStateOf(false)
    private val component = compose(focusOnClickInside = true) {
        if (active) WinRTPreviewPanel(project, staticFile = file.path)
    }
    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName() = "Preview"
    override fun getFile() = file
    override fun setState(state: FileEditorState) = Unit
    override fun selectNotify() { active = true }
    override fun deselectNotify() { active = false }
    override fun isModified() = false
    override fun isValid() = !disposed && file.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() { active = false; disposed = true }
}
