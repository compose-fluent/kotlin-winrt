package io.github.composefluent.winrt.ide.analysis

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.VirtualFileManager
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarationIndex
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationEvent
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import java.nio.file.Files
import java.nio.file.Path

data class WinRTXamlSnapshot(val text: String, val declarations: WinRTXamlDeclarationIndex, val error: String? = null)

/** Owns analysis inputs, not generated Kotlin declarations or application ABI code. */
@Service(Service.Level.PROJECT)
class WinRTXamlSnapshotService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private val snapshots = MutableStateFlow<Map<String, WinRTXamlSnapshot>>(emptyMap())
    val state: StateFlow<Map<String, WinRTXamlSnapshot>> = snapshots

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { event -> key(event.path) in snapshots.value }) refresh()
            }
        })
        scope.launch(Dispatchers.IO) {
            val projects = project.service<WinRTProjectService>()
            projects.refreshFromGradleCache()
            projects.modules.collectLatest { refreshSavedDeclarations() }
        }
    }

    fun currentText(declarationsFile: String): String? = snapshots.value[key(declarationsFile)]?.text
    fun refresh() { scope.launch(Dispatchers.IO) { refreshSavedDeclarations() } }

    private fun refreshSavedDeclarations() {
        val compilations = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }
        val next = compilations.associate { compilation ->
            val result = runCatching { Files.readString(Path.of(compilation.declarationsFile)) }
            key(compilation.declarationsFile) to result.fold(
                onSuccess = { text -> snapshot(text) },
                onFailure = { snapshot(EMPTY_DECLARATIONS, "Prepare XAML analysis with ${compilation.taskName}.") },
            )
        }
        val previous = snapshots.value
        snapshots.value = next
        if (previous.mapValues { it.value.text } != next.mapValues { it.value.text }) invalidate(previous.hashCode(), next.hashCode())
    }

    /** Also used by the document snapshot producer; malformed inputs remove stale symbols. */
    fun publish(declarationsFile: String, text: String, error: String? = null) {
        val value = snapshot(text, error)
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
