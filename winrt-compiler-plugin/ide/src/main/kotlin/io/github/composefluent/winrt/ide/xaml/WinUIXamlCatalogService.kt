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
import io.github.composefluent.winrt.ide.gradle.WinUIXamlCompilationData
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTMetadataCache
import io.github.composefluent.winrt.metadata.WinRTMetadataModelCodec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

@Service(Service.Level.PROJECT)
class WinUIXamlCatalogService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    val modificationTracker = SimpleModificationTracker()
    internal val changes = MutableStateFlow(0L)
    @Volatile private var catalogs: Map<String, WinUIXamlCatalog> = emptyMap()
    @Volatile private var previewEvents: Map<String, WinUIXamlPreviewEvents> = emptyMap()
    private val revision = AtomicLong()
    private var loading: Job? = null
    private val loadMutex = Mutex()
    private val sharedCatalogs = mutableMapOf<String, WinUIXamlCatalog>()

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

    fun forFile(path: String): WinUIXamlCatalog? = compilations().firstNotNullOfOrNull { compilation ->
        if (compilation.sourceRoots.any { root -> runCatching {
                Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(root).toAbsolutePath().normalize())
            }.getOrDefault(false) }) catalogs[compilation.inputFile] else null
    } ?: project.service<WinRTProjectService>().modules.value.filter { module -> runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(module.projectDirectory).toAbsolutePath().normalize())
    }.getOrDefault(false) }.maxByOrNull { it.projectDirectory.length }?.staticPreview?.let { catalogs[it.metadataReferencesFile] }

    internal fun eventsForFile(path: String): WinUIXamlPreviewEvents? = compilations().firstNotNullOfOrNull { compilation ->
        if (compilation.sourceRoots.any { root -> runCatching {
            Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(root).toAbsolutePath().normalize())
        }.getOrDefault(false) }) previewEvents[compilation.inputFile] else null
    }

    fun refresh() { loading?.cancel(); loading = scope.launch(Dispatchers.IO) { load() } }
    internal fun forDesigner(module: WinRTModuleData): WinUIXamlCatalog? = module.staticPreview?.let { catalogs[it.metadataReferencesFile] }
    private fun compilations() = project.service<WinRTProjectService>().modules.value.flatMap { it.xamlCompilations }
    private fun referenceFiles() = compilations().map { it.inputFile } + project.service<WinRTProjectService>().modules.value
        .mapNotNull { it.staticPreview?.metadataReferencesFile }

    private suspend fun load() = loadMutex.withLock {
        val request = revision.incrementAndGet()
        val current = referenceFiles().distinct()
        // Opening a manifest in an ordinary project needs no XAML catalog
        // invalidation. Avoid restarting its highlighting during initial load.
        if (current.isEmpty() && catalogs.isEmpty()) return@withLock
        val next = linkedMapOf<String, WinUIXamlCatalog>()
        val eventsDirectory = Path.of(com.intellij.openapi.application.PathManager.getSystemPath())
            .resolve("kotlin-winrt/xaml-preview-events")
        previewEvents = emptyMap()
        current.forEach { referenceFile ->
            coroutineContext.ensureActive()
            var contentKey: String? = null
            val catalog = runCatching {
                val input = Json.parseToJsonElement(Files.readString(Path.of(referenceFile))).jsonObject
                val paths = input["ReferenceAssemblies"]?.jsonArray.orEmpty().mapNotNull { item ->
                    (item.jsonObject["FullPath"] ?: item.jsonObject["ItemSpec"])?.jsonPrimitive?.content?.let(Path::of)
                }.filter { it.toString().endsWith(".winmd", true) && Files.isRegularFile(it) }.distinct()
                require(paths.isNotEmpty())
                // The metadata owner supplies the content-keyed normalized model cache.
                // Multiple targets/reference manifests often describe the same SDK.
                val key = WinRTMetadataModelCodec.cacheKey(paths)
                contentKey = key
                WinUIXamlPreviewEvents.read(eventsDirectory, key)?.let {
                    previewEvents = previewEvents + (referenceFile to it)
                    changes.value += 1
                }
                sharedCatalogs.getOrPut(key) {
                    val started = System.nanoTime()
                    val directory = Path.of(com.intellij.openapi.application.PathManager.getSystemPath())
                        .resolve("kotlin-winrt/xaml-models")
                    val metadata = WinRTMetadataCache(paths)
                    WinUIXamlCatalog(runCatching { metadata.load(directory) }.getOrElse { metadata.load() }).also {
                        com.intellij.openapi.diagnostic.Logger.getInstance(WinUIXamlCatalogService::class.java)
                            .info("XAML catalog ready: ${(System.nanoTime() - started) / 1_000_000}ms")
                    }
                }
            }.getOrNull() ?: return@forEach
            next[referenceFile] = catalog
            coroutineContext.ensureActive()
            // A project preview must not wait for unrelated SDK/target catalogs.
            if (revision.get() == request) publish(next)
            // Publish the full catalog before materializing its smaller startup view.
            // First-time extraction must never delay this document's first render.
            if (previewEvents[referenceFile] == null) runCatching {
                val events = WinUIXamlPreviewEvents.from(catalog)
                coroutineContext.ensureActive()
                events.save(eventsDirectory, requireNotNull(contentKey))
                previewEvents = previewEvents + (referenceFile to events)
            }.onFailure { if (it is CancellationException) throw it }
        }
        coroutineContext.ensureActive()
        if (revision.get() == request) {
            sharedCatalogs.entries.removeIf { it.value !in next.values }
            if (next.isEmpty()) publish(next)
        }
    }

    internal fun publish(next: Map<String, WinUIXamlCatalog>) {
        catalogs = next.toMap()
        modificationTracker.incModificationCount()
        changes.value += 1
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) DaemonCodeAnalyzer.getInstance(project).restart("WinRT XAML metadata changed")
        }
    }

    override fun dispose() = Unit
}
