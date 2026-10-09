package io.github.composefluent.winrt.ide.resources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.composefluent.winrt.ide.templates.WinRTInstalledSdks
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.*
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

class WinRTXmlFormEditorProvider : FileEditorProvider, com.intellij.openapi.project.DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = !file.isDirectory &&
        (isManifest(file.name) || file.extension.equals("resw", true))
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = WinRTXmlFormEditor(project, file)
    override fun getEditorTypeId() = EDITOR_ID
    override fun getPolicy() = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
    companion object {
        const val EDITOR_ID = "kotlin-winrt-xml-form"
        fun isManifest(name: String) = name.equals("AppxManifest.xml", true) || name.endsWith(".appxmanifest", true)
        fun open(project: Project, path: String) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(path.replace('\\', '/'))?.let { file ->
                        FileEditorManager.getInstance(project).apply { openFile(file, true); setSelectedEditor(file, EDITOR_ID) }
                    }
            }
        }
    }
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
        val scope = rememberCoroutineScope()
        val installed by service<WinRTInstalledSdks>().state.collectAsState()
        var catalog by remember { mutableStateOf(WinRTManifestCatalog.Empty) }
        var generating by remember { mutableStateOf(false) }
        LaunchedEffect(installed.sdks, module?.windowsSdkVersion) {
            if (!resw) catalog = withContext(Dispatchers.IO) {
                val sdk = if (module?.windowsSdkVersion?.isNotBlank() == true) installed.sdks.firstOrNull { it.version == module.windowsSdkVersion } else installed.sdks.firstOrNull()
                runCatching { sdk?.let { WinRTManifestCatalog.read(it.schemas) } ?: WinRTManifestCatalog.Empty }.getOrDefault(WinRTManifestCatalog.Empty)
            }
        }
        fun command(action: () -> Unit) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed && virtualFile.isValid) { failure = null; runCatching(action).onFailure { failure = it.message }; refresh() }
            }
        }
        if (!resw) {
            WinRTManifestDesigner(
                snapshot = snapshot,
                errors = snapshot.errors + listOfNotNull(failure),
                windowsVersions = module?.packageLayouts?.distinctBy { it.variant }?.map { "${it.variant}: Windows ${it.minWindowsVersion} · tested ${it.maxVersionTested}" }.orEmpty(),
                windowsVersionsFromGradle = module?.packageLayouts?.isNotEmpty() == true,
                onEdit = { field, text -> command { WinRTXmlForms.set(project, virtualFile, field, text) } },
                onRemove = { field -> command { WinRTXmlForms.removeEntry(project, virtualFile, field) } },
                onCapability = { text, restricted, device -> command { WinRTXmlForms.addCapability(project, virtualFile, text, restricted, device) } },
                onExtension = { application, text, extension -> command { WinRTXmlForms.addExtension(project, virtualFile, application, text, extension) } },
                onContentUri = { application, match, type -> command { WinRTXmlForms.addContentUriRule(project, virtualFile, application, match, type) } },
                onGradle = { module?.let { project.service<WinRTProjectService>().openFile("${it.projectDirectory}/build.gradle.kts") } },
                onBrowse = { field -> WinRTManifestAssets.choose(project, virtualFile) { asset ->
                    command { val relative = WinRTManifestAssets.import(project, virtualFile, asset); WinRTXmlForms.set(project, virtualFile, field, relative) }
                } },
                assetPath = { text -> WinRTManifestAssets.resolve(virtualFile.toNioPath().parent, text) },
                catalog = catalog,
                onCatalogCapability = { capability -> command { WinRTXmlForms.addCapability(project, virtualFile, capability) } },
                onDeclaration = { application, declaration -> command { WinRTXmlForms.addDeclaration(project, virtualFile, application, declaration) } },
                onAddNode = { node, child -> command { WinRTXmlForms.addNode(project, virtualFile, node, child.namespace, child.name) } },
                onRemoveNode = { node -> command { WinRTXmlForms.removeNode(project, virtualFile, node) } },
                onSelection = { application, rotation, selection, checked -> command { WinRTXmlForms.setSelection(project, virtualFile, application, rotation, selection, checked) } },
                onCertificate = { field -> WinRTPackageIdentity.chooseCertificate(project, virtualFile,
                    { subject -> command { WinRTXmlForms.set(project, virtualFile, field, subject) } }, { failure = it }) },
                onGenerateAssets = { request -> if (!generating) {
                    generating = true
                    val fields = WinRTManifestAssetKind.entries.mapNotNull { kind -> kind.field(snapshot, request.application)?.let { kind to it } }.toMap()
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { WinRTManifestAssets.generate(virtualFile.toNioPath().parent, request, fields) } }
                        generating = false
                        result.fold({ (files, edits) -> command {
                            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project, "Generate manifest assets", null, Runnable {
                                PsiDocumentManager.getInstance(project).commitDocument(document)
                                val xml = PsiManager.getInstance(project).findFile(virtualFile) as? XmlFile ?: error("No editable manifest.")
                                val current = WinRTXmlForms.snapshot(xml).fields.associateBy { it.id }
                                require(edits.keys.all { current[it.id]?.value == it.value }) { "The asset paths changed while generating. Try again." }
                                WinRTManifestAssets.write(project, virtualFile, files, request.overwrite)
                                edits.forEach { (field, text) -> WinRTXmlForms.set(project, virtualFile, field, text) }
                            })
                        } }, { failure = it.message })
                    }
                } },
                onBrowseAssetVariant = { field, scale -> WinRTManifestAssets.choose(project, virtualFile) { asset -> command {
                    val base = WinRTManifestAssets.importVariant(project, virtualFile, asset, field, scale)
                    if (base != field.value) WinRTXmlForms.set(project, virtualFile, field, base)
                } } },
                onRemoveAssetVariant = { field, scale -> command { WinRTManifestAssets.removeVariant(project, virtualFile, field, scale) } },
                onChooseAssetSource = { chosen -> WinRTManifestAssets.choose(project, virtualFile) { chosen(it.path) } },
                generatingAssets = generating,
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(if (resw) ".resw resources" else "AppX manifest")
                (snapshot.errors + listOfNotNull(failure)).forEach { Text(it) }
            }
            items(snapshot.fields, key = { it.id }) { field ->
                val buffer = remember(field.id, field.value) { TextFieldState(field.value) }
                val governed = module?.packageLayouts?.isNotEmpty() == true && field.path.last().name == "TargetDeviceFamily" &&
                    field.attribute in listOf("MinVersion", "MaxVersionTested")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(field.label)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(buffer, enabled = !governed, modifier = Modifier.weight(1f).semantics { contentDescription = field.label })
                        DefaultButton(enabled = !governed, onClick = { command { WinRTXmlForms.set(project, virtualFile, field, buffer.text.toString()) } }) { Text("Apply") }
                        if (field.attribute in listOf("name", "Name", "Language") && field.path.last().name in
                            listOf("data", "Capability", "DeviceCapability", "Protocol", "FileTypeAssociation", "Resource")) {
                            DefaultButton(onClick = { command { WinRTXmlForms.removeEntry(project, virtualFile, field) } }) { Text("Remove") }
                        }
                    }
                }
            }
            item {
                Text("Add resource key/value")
                TextField(name, placeholder = { Text("Name") }, modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = if (resw) "Resource key" else "Capability or extension name"
                })
                TextField(value, placeholder = { Text("Value") }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Resource value" })
                DefaultButton(onClick = { command { WinRTXmlForms.addResw(project, virtualFile, name.text.toString(), value.text.toString()) } }) { Text("Add key") }
            }
        }
    }

    override fun getComponent(): JComponent = component
    override fun getPreferredFocusedComponent(): JComponent = component
    override fun getName() = if (resw) "Resources" else "Manifest Designer"
    override fun getFile() = virtualFile
    override fun setState(state: FileEditorState) = Unit
    override fun isModified() = FileDocumentManager.getInstance().isFileModified(virtualFile)
    override fun isValid() = !disposed && virtualFile.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = changes.addPropertyChangeListener(listener)
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = changes.removePropertyChangeListener(listener)
    override fun dispose() { disposed = true }
}
