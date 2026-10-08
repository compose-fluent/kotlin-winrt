package io.github.composefluent.winrt.ide.xaml

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTMetadataLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
class WinRTXamlCatalogService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    val modificationTracker = SimpleModificationTracker()
    internal val changes = MutableStateFlow(0L)
    @Volatile private var catalogs: Map<String, WinRTXamlCatalog> = emptyMap()
    private val revision = AtomicLong()
    private var loading: Job? = null

    init {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { it.path.endsWith(".winmd", true) || referenceFiles().any { path ->
                        it.path.replace('\\', '/').equals(path.replace('\\', '/'), true) } }) refresh()
            }
        })
        scope.launch(Dispatchers.IO) {
            val projects = project.service<WinRTProjectService>()
            projects.refreshFromGradleCache()
            projects.modules.collectLatest { load() }
        }
    }

    fun forFile(path: String): WinRTXamlCatalog? = compilations().firstNotNullOfOrNull { compilation ->
        if (compilation.sourceRoots.any { root -> runCatching {
                Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(root).toAbsolutePath().normalize())
            }.getOrDefault(false) }) catalogs[compilation.inputFile] else null
    } ?: project.service<WinRTProjectService>().modules.value.filter { module -> runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(module.projectDirectory).toAbsolutePath().normalize())
    }.getOrDefault(false) }.maxByOrNull { it.projectDirectory.length }?.staticPreview?.let { catalogs[it.metadataReferencesFile] }

    fun refresh() { loading?.cancel(); loading = scope.launch(Dispatchers.IO) { load() } }
    internal fun forDesigner(module: WinRTModuleData): WinRTXamlCatalog? = module.staticPreview?.let { catalogs[it.metadataReferencesFile] }
    private fun compilations() = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }
    private fun referenceFiles() = compilations().map { it.inputFile } + project.service<WinRTProjectService>().modules.value
        .mapNotNull { it.staticPreview?.metadataReferencesFile }

    private suspend fun load() {
        val request = revision.incrementAndGet()
        val current = referenceFiles().distinct()
        val next = current.mapNotNull { referenceFile ->
            coroutineContext.ensureActive()
            val catalog = runCatching {
                val input = Json.parseToJsonElement(Files.readString(Path.of(referenceFile))).jsonObject
                val paths = input["ReferenceAssemblies"]?.jsonArray.orEmpty().mapNotNull { item ->
                    (item.jsonObject["FullPath"] ?: item.jsonObject["ItemSpec"])?.jsonPrimitive?.content?.let(Path::of)
                }.filter { it.toString().endsWith(".winmd", true) && Files.isRegularFile(it) }.distinct()
                require(paths.isNotEmpty())
                WinRTXamlCatalog(WinRTMetadataLoader.load(paths))
            }.getOrNull() ?: return@mapNotNull null
            referenceFile to catalog
        }.toMap()
        coroutineContext.ensureActive()
        if (revision.get() == request) publish(next)
    }

    internal fun publish(next: Map<String, WinRTXamlCatalog>) {
        catalogs = next.toMap()
        modificationTracker.incModificationCount()
        changes.value += 1
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("WinRT XAML metadata changed")
        }
    }

    override fun dispose() = Unit
}
