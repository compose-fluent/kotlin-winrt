package io.github.composefluent.winrt.ide.analysis

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarationIndex
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEvent
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class WinRTXamlSnapshot(val text: String, val declarations: WinRTXamlDeclarationIndex, val error: String? = null, val revision: Long = 0)

/** Owns analysis inputs, not generated Kotlin declarations or application ABI code. */
@Service(Service.Level.PROJECT)
@OptIn(FlowPreview::class)
class WinRTXamlSnapshotService(private val project: Project, private val scope: CoroutineScope) : Disposable, com.intellij.openapi.util.ModificationTracker {
    private val snapshots = MutableStateFlow<Map<String, WinRTXamlSnapshot>>(emptyMap())
    val state: StateFlow<Map<String, WinRTXamlSnapshot>> = snapshots
    private val documents = ConcurrentHashMap<String, WinRTXamlDocument>()
    private val edits = MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val generation = AtomicLong()
    private val publishedGeneration = AtomicLong()
    override fun getModificationCount(): Long = publishedGeneration.get()

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (project.isDisposed) return
                if (events.any { event -> runCatching { key(event.path) }.getOrNull()?.let { it in snapshots.value } == true }) refresh()
                if (events.any { it.path.endsWith(".xaml", true) && owns(it.path) }) queueEdit()
            }
        })
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (project.isDisposed) return
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (!file.isInLocalFileSystem || !file.extension.equals("xaml", true) || !owns(file.path)) return
                documents[key(file.path)] = WinRTXamlDocument(file.path, event.document.text, event.document.modificationStamp)
                queueEdit()
            }
        }, this)
        scope.launch(Dispatchers.IO) {
            edits.debounce(350).collectLatest { harvest ->
                if (!harvest) return@collectLatest
                val request = generation.get()
                val current = documents.values.toList()
                compilations().forEach { compilation ->
                    val inputs = current.filter { document -> compilation.sourceRoots.any { root -> within(document.path, root) } }
                    try {
                        check(TrustedProjects.isProjectTrusted(project)) { "Trust this project before running XAML analysis." }
                        val text = WinRTXamlDocumentCompiler.harvest(compilation, inputs,
                            PathManager.getSystemDir().resolve("kotlin-winrt/xaml/${project.locationHash}"))
                        if (generation.get() == request) publish(compilation.declarationsFile, text)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        if (generation.get() == request) publish(compilation.declarationsFile, EMPTY_DECLARATIONS, failure.message)
                    }
                }
            }
        }
        scope.launch(Dispatchers.IO) {
            val projects = project.service<WinRTProjectService>()
            projects.refreshFromGradleCache()
            projects.modules.collectLatest { refreshSavedDeclarations() }
        }
    }

    fun currentText(declarationsFile: String): String? = snapshots.value[key(declarationsFile)]?.text
    fun refresh() { scope.launch(Dispatchers.IO) { refreshSavedDeclarations() } }

    private fun queueEdit(harvest: Boolean = true) { generation.incrementAndGet(); edits.tryEmit(harvest) }

    private fun compilations() = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }
    private fun owns(path: String) = compilations().any { compilation -> compilation.sourceRoots.any { within(path, it) } }
    private fun within(path: String, root: String) = runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(root).toAbsolutePath().normalize())
    }.getOrDefault(false)

    private fun refreshSavedDeclarations() {
        generation.incrementAndGet()
        val previous = snapshots.value
        val next = compilations().associate { compilation ->
            val path = key(compilation.declarationsFile)
            // Reimport/output refresh cannot replace an unsaved document's live
            // declaration with an older on-disk index while harvesting is pending.
            val live = previous[path]?.takeIf {
                documents.values.any { document -> compilation.sourceRoots.any { within(document.path, it) } }
            }
            val result = runCatching { Files.readString(Path.of(compilation.declarationsFile)) }
            path to (live ?: result.fold(
                onSuccess = { text -> snapshot(text) },
                onFailure = { snapshot(EMPTY_DECLARATIONS, "Prepare XAML analysis with ${compilation.taskName}.") },
            ).copy(revision = generation.get()))
        }
        snapshots.value = next
        if (previous.mapValues { it.value.text } != next.mapValues { it.value.text }) invalidate(previous.hashCode(), next.hashCode())
        documents.keys.removeIf { !owns(it) }
        queueEdit(documents.isNotEmpty())
        // The service may start after a XAML editor has already been modified,
        // or after sync assigns an existing document to a new source root.
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            var changed = false
            FileDocumentManager.getInstance().unsavedDocuments.forEach { document ->
                val file = FileDocumentManager.getInstance().getFile(document) ?: return@forEach
                if (!file.isInLocalFileSystem || !file.extension.equals("xaml", true) || !owns(file.path)) return@forEach
                val path = key(file.path)
                if (documents[path]?.revision != document.modificationStamp) {
                    documents[path] = WinRTXamlDocument(file.path, document.text, document.modificationStamp)
                    changed = true
                }
            }
            if (changed) queueEdit()
        }
    }

    /** Also used by the document snapshot producer; malformed inputs remove stale symbols. */
    fun publish(declarationsFile: String, text: String, error: String? = null) {
        val value = snapshot(text, error).copy(revision = generation.get())
        val path = key(declarationsFile)
        val previous = snapshots.value[path]
        snapshots.update { it + (path to value) }
        if (previous?.text != value.text) invalidate(previous?.text.orEmpty(), value.text)
    }

    private fun snapshot(text: String, error: String? = null): WinRTXamlSnapshot = runCatching {
        val index = WinRTXamlDeclarations.parse(text)
        WinRTXamlSnapshot(WinRTXamlDeclarations.canonicalText(index), index, error)
    }.getOrElse {
        WinRTXamlSnapshot(EMPTY_DECLARATIONS, WinRTXamlDeclarations.parse(EMPTY_DECLARATIONS), error ?: it.message)
    }

    @OptIn(KaPlatformInterface::class)
    private fun invalidate(before: Any, after: Any) {
        publishedGeneration.incrementAndGet()
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            ApplicationManager.getApplication().runWriteAction {
                // Compiler configurations cache registrar instances separately from FIR sessions.
                project.messageBus.syncPublisher(KotlinCompilerSettingsListener.TOPIC).settingsChanged(before, after)
                project.messageBus.syncPublisher(KotlinModificationEvent.TOPIC)
                    .onModification(KotlinGlobalModuleStateModificationEvent)
            }
            DaemonCodeAnalyzer.getInstance(project).restart("WinRT XAML declarations changed")
        }
    }

    override fun dispose() = Unit

    companion object {
        const val EMPTY_DECLARATIONS = "{\"SchemaVersion\":3,\"Pages\":[],\"Resources\":[]}"
        fun key(path: String): String = Path.of(path).toAbsolutePath().normalize().toString().lowercase(java.util.Locale.ROOT)
    }
}
