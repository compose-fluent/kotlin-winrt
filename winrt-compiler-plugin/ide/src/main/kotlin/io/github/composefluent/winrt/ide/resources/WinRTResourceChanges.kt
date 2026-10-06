package io.github.composefluent.winrt.ide.resources

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Service(Service.Level.PROJECT)
class WinRTResourceChanges(project: Project) {
    val revision = MutableStateFlow(0L)
    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (file.extension.equals("resw", true) || file.extension.equals("xaml", true) ||
                    file.extension.equals("appxmanifest", true) || file.name.equals("AppxManifest.xml", true)) revision.update { it + 1 }
            }
        }, project)
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { "appxResources" in it.path || it.path.endsWith("appx-resource-resolution.json") || it.path.endsWith(".xaml", true) }) revision.update { it + 1 }
            }
        })
    }
}
