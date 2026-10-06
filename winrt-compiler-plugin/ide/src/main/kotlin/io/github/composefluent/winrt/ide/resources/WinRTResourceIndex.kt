package io.github.composefluent.winrt.ide.resources

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import java.nio.file.Files
import java.nio.file.Path

internal data class WinRTResourceLookup(val module: WinRTModuleData, val entries: List<WinRTResourceEntry>,
    val xamlFiles: List<String>, val pri: List<WinRTPriCandidate>)

/** File enumeration/staging-report IO never runs in PSI reference or completion requests. */
@Service(Service.Level.PROJECT)
class WinRTResourceIndex(private val project: Project, scope: CoroutineScope) : Disposable {
    @Volatile private var lookups = emptyList<WinRTResourceLookup>()
    init {
        scope.launch(Dispatchers.IO) {
            val projects = project.service<WinRTProjectService>()
            projects.refreshFromGradleCache()
            val changes = project.service<WinRTResourceChanges>()
            combine(projects.modules, changes.revision, projects.dependencyRevision) { modules, revision, dependencyRevision ->
                Triple(modules, revision, dependencyRevision)
            }.collectLatest { (modules, revision, dependencyRevision) ->
                delay(250)
                val next = modules.mapNotNull { module ->
                    coroutineContext.ensureActive()
                    runCatching { read(module, modules) }.getOrNull()
                }
                coroutineContext.ensureActive()
                if (modules == projects.modules.value && revision == changes.revision.value &&
                    dependencyRevision == projects.dependencyRevision.value) publish(next)
            }
        }
    }

    internal fun forFile(path: String): WinRTResourceLookup? = lookups.filter { lookup ->
        val roots = listOf(lookup.module.projectDirectory) + lookup.module.xamlCompilations.flatMap { it.sourceRoots }
        roots.any { runCatching { Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(it).toAbsolutePath().normalize()) }.getOrDefault(false) }
    }.maxByOrNull { it.module.projectDirectory.length }

    internal fun publish(next: List<WinRTResourceLookup>) {
        if (lookups == next) return
        lookups = next.toList()
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("WinRT resource candidates changed")
        }
    }

    internal fun read(module: WinRTModuleData, modules: List<WinRTModuleData>): WinRTResourceLookup {
        val staged = module.packageLayouts.map { WinRTResourceCatalog.staged(module, it, modules) }.filter { it.error == null }
        val entries = (module.sourceSets.flatMap { WinRTResourceCatalog.sourceSet(module, it).entries } + staged.flatMap { it.entries })
            .distinctBy { it.target.lowercase() to it.source.lowercase() }
        val files = module.xamlCompilations.flatMap { it.sourceRoots }.distinct().flatMap { root ->
            val directory = Path.of(root)
            if (!Files.isDirectory(directory)) emptyList() else Files.walk(directory).use { paths -> paths.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".xaml", true)
            }.limit(4096).map { it.toAbsolutePath().normalize().toString() }.toList() }
        }.distinct()
        val vfs = LocalFileSystem.getInstance()
        (entries.map { it.source } + files).distinct().forEach { vfs.refreshAndFindFileByNioFile(Path.of(it)) }
        return WinRTResourceLookup(module, entries, files, staged.flatMap { it.candidates })
    }
    override fun dispose() = Unit
}
