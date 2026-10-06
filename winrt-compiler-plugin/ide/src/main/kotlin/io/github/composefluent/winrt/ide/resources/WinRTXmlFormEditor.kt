package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.*
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

class WinRTXmlFormEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean = !file.isDirectory &&
        (file.name.equals("AppxManifest.xml", true) || file.extension.equals("appxmanifest", true) || file.extension.equals("resw", true))
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WinRTXmlFormEditor(project, file)
    override fun getEditorTypeId() = "kotlin-winrt-xml-form"
    override fun getPolicy() = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR
}

private class WinRTXmlFormEditor(private val project: Project, private val virtualFile: VirtualFile) : UserDataHolderBase(), FileEditor {
    private val changes = PropertyChangeSupport(this)
    private val document = requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
    private val state = MutableStateFlow(WinRTXmlSnapshot(emptyList(), emptyList()))
    private var disposed = false
    private val resw = virtualFile.extension.equals("resw", true)
    private val component = compose(focusOnClickInside = true) { Form() }

    init {
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                changes.firePropertyChange(FileEditor.getPropModified(), false, true)
                refresh()
            }
        }, this)
        refresh()
    }

    private fun refresh() = ApplicationManager.getApplication().invokeLater {
        if (!disposed && !project.isDisposed && virtualFile.isValid) {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            state.value = ReadAction.compute<WinRTXmlSnapshot, RuntimeException> {
                (PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile)?.let(WinRTXmlForms::snapshot)
                    ?: WinRTXmlSnapshot(emptyList(), listOf("Open this file with the XML file type."))
            }
        }
    }

    @Composable
    private fun Form() {
        val snapshot by state.collectAsState()
        val modules by project.service<WinRTProjectService>().modules.collectAsState()
        val module = modules.filter { virtualFile.path.startsWith(it.projectDirectory.replace('\\', '/') + "/") }.maxByOrNull { it.projectDirectory.length }
        var failure by remember { mutableStateOf<String?>(null) }
        val name = remember { TextFieldState() }
        val value = remember { TextFieldState() }
        val extension = remember { TextFieldState(".txt") }
        var restricted by remember { mutableStateOf(false) }
        var device by remember { mutableStateOf(false) }
        var application by remember { mutableIntStateOf(0) }
        fun command(action: () -> Unit) {
            ApplicationManager.getApplication().invokeLater {
                if (!disposed) { failure = null; runCatching(action).onFailure { failure = it.message }; refresh() }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(if (resw) ".resw resources" else "AppX manifest")
                (snapshot.errors + listOfNotNull(failure)).forEach { Text(it) }
                if (!resw) {
                    module?.packageLayouts?.distinctBy { it.variant }?.forEach { layout ->
                        Text("${layout.variant}: Windows ${layout.minWindowsVersion} · tested ${layout.maxVersionTested} (Gradle configuration)")
                    }
                    module?.let { DefaultButton(onClick = { project.service<WinRTProjectService>().openFile("${it.projectDirectory}/build.gradle.kts") }) { Text("Windows version configuration") } }
                }
            }
            items(snapshot.fields, key = { it.id }) { field ->
                val buffer = remember(field.id, field.value) { TextFieldState(field.value) }
                val governed = module?.packageLayouts?.isNotEmpty() == true && field.path.last().name == "TargetDeviceFamily" &&
                    field.attribute in listOf("MinVersion", "MaxVersionTested")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(field.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(buffer, enabled = !governed, modifier = Modifier.weight(1f))
                        DefaultButton(enabled = !governed, onClick = { command { WinRTXmlForms.set(project, virtualFile, field, buffer.text.toString()) } }) { Text("Apply") }
                        if (field.attribute in listOf("name", "Name", "Language") && field.path.last().name in
                            listOf("data", "Capability", "DeviceCapability", "Protocol", "FileTypeAssociation", "Resource")) {
                            DefaultButton(onClick = { command { WinRTXmlForms.removeEntry(project, virtualFile, field) } }) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                Text(if (resw) "Add resource key/value" else "Add capability or application extension")
                TextField(name, placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                if (resw) {
                    TextField(value, placeholder = { Text("Value") }, modifier = Modifier.fillMaxWidth())
                    DefaultButton(onClick = { command { WinRTXmlForms.addResw(project, virtualFile, name.text.toString(), value.text.toString()) } }) { Text("Add key") }
                } else {
                    CheckboxRow("Restricted capability", restricted, { restricted = it; if (it) device = false })
                    CheckboxRow("Device capability", device, { device = it; if (it) restricted = false })
                    DefaultButton(onClick = { command { WinRTXmlForms.addCapability(project, virtualFile, name.text.toString(), restricted, device) } }) { Text("Add capability") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(snapshot.applicationCount) { index ->
                            RadioButtonRow("Application ${index + 1}", application == index, { application = index })
                        }
                    }
                    DefaultButton(onClick = { command { WinRTXmlForms.addExtension(project, virtualFile, application, name.text.toString(), null) } }) { Text("Add protocol") }
                    TextField(extension, placeholder = { Text("File extension") }, modifier = Modifier.fillMaxWidth())
                    DefaultButton(onClick = { command { WinRTXmlForms.addExtension(project, virtualFile, application, name.text.toString(), extension.text.toString()) } }) { Text("Add file association") }
                }
            }
        }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName() = if (resw) "Resources" else "Manifest"
    override fun getFile() = virtualFile
    override fun setState(state: FileEditorState) = Unit
    override fun isModified() = FileDocumentManager.getInstance().isFileModified(virtualFile)
    override fun isValid() = !disposed && virtualFile.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = changes.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = changes.removePropertyChangeListener(listener)
    override fun dispose() { disposed = true }
}
