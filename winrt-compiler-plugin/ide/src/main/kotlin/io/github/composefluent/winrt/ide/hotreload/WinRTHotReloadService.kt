package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.*
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit

data class WinRTHotReloadState(val message: String = "Start an unpackaged JVM application to update XAML properties.",
    val connected: Boolean = false, val busy: Boolean = false, val pid: Long? = null,
    val roots: List<WinRTXamlHotReloadRoot> = emptyList(), val values: List<WinRTXamlHotReloadValue> = emptyList())

@Service(Service.Level.PROJECT)
class WinRTHotReloadService(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private data class Source(val path: String, val markup: WinRTHotReloadMarkup)
    private val display = MutableStateFlow(WinRTHotReloadState())
    val state: StateFlow<WinRTHotReloadState> = display
    val automatic = MutableStateFlow(true)
    private val mutex = Mutex()
    @Volatile private var client: WinRTHotReloadClient? = null
    @Volatile private var generation = 0L
    private var directory: Path? = null
    private var selectedModule: WinRTModuleData? = null
    private var selectedLaunch: WinRTHotReloadLaunchData? = null
    private var sources = emptyList<Source>()
    private val baselines = mutableMapOf<Pair<String, String>, Source>()
    private val attempted = mutableMapOf<Pair<String, String>, Source>()
    private var worker: Job? = null
    private var edits: Job? = null
    private var uncertain = false
    private var owned: WinRTHotReloadClient? = null

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (FileDocumentManager.getInstance().getFile(event.document)?.extension.equals("xaml", true) && automatic.value && client != null) {
                    edits?.cancel()
                    edits = scope.launch { delay(450); apply() }
                }
            }
        }, this)
        scope.launch {
            project.service<WinRTProjectService>().modules.collect { modules ->
                if (selectedModule != null && modules.none { it.projectDirectory == selectedModule?.projectDirectory }) disconnect()
            }
        }
    }

    /** Native Gradle owns build, launch, logs and cancellation; Compose only requests the operation. */
    fun start(module: WinRTModuleData, launch: WinRTHotReloadLaunchData, restart: Boolean = false) {
        require(launch in module.hotReloadLaunches) { "Synchronize Gradle before selecting this application." }
        if (display.value.busy) return
        FileDocumentManager.getInstance().saveAllDocuments()
        val request = ++generation
        worker?.cancel(); edits?.cancel(); client?.close(); client = null
        display.value = WinRTHotReloadState("Preparing the development launch…", busy = true)
        worker = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (restart) stopOwned()
                    else {
                        check(owned?.process?.isAlive != true) { "Stop or restart the existing development application first." }
                        stopOwned()
                    }
                    sources = loadSources()
                    baselines.clear(); attempted.clear(); uncertain = false
                    selectedModule = module; selectedLaunch = launch
                    directory = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}").toAbsolutePath().normalize()
                    Files.createDirectories(directory!!)
                }
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed && request == generation) WinRTGradleTasks.run(project, module,
                        listOf(launch.taskName), "Run with XAML Hot Reload",
                        environment = mapOf(WinRTXamlHotReloadProtocol.SESSION_DIRECTORY to directory!!.toString()),
                        onFailure = { if (generation == request && client == null) {
                            worker?.cancel(); display.value = WinRTHotReloadState("The Gradle launch failed or stopped. See its Run output.")
                        } })
                }
                display.value = WinRTHotReloadState("Building and waiting for the application. See Gradle Run output.", busy = true)
                withTimeout(10 * 60_000L) {
                    while (isActive && request == generation) {
                        val found = WinRTHotReloadClient.discover(directory!!, launch.executable)
                        require(found.size <= 1) { "Multiple development processes use this session directory. Stop them before reconnecting." }
                        val candidate = found.singleOrNull()
                        if (candidate != null) {
                            client = candidate; owned = candidate
                            mutex.withLock { refresh(candidate) }
                            break
                        }
                        delay(750)
                    }
                }
                watch(request)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (request == generation) display.value = WinRTHotReloadState(error.message.orEmpty()) }
        }
    }

    fun reconnect() {
        val folder = directory ?: return
        val launch = selectedLaunch ?: return
        val request = ++generation
        worker?.cancel(); client?.close(); client = null
        worker = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    val next = WinRTHotReloadClient.discover(folder, launch.executable)
                    require(next.size == 1) { "No unique live development session. Rebuild and restart the application." }
                    client = next.single()
                    // This directory was created for this service's own Gradle launch.
                    owned = client
                    refresh(client!!)
                }
                watch(request)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { display.value = WinRTHotReloadState(error.message.orEmpty()) }
        }
    }

    private suspend fun watch(request: Long) {
        while (scope.isActive && request == generation) {
            delay(2_000)
            try { mutex.withLock { client?.let { refresh(it) } } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                client?.close(); client = null
                display.value = display.value.copy(message = error.message.orEmpty(), connected = false, busy = false,
                    pid = owned?.process?.takeIf { it.isAlive }?.pid())
                break
            }
        }
    }

    private fun refresh(connection: WinRTHotReloadClient) {
        val reply = connection.request()
        if (reply.status == WinRTXamlHotReloadProtocol.APPLIED) {
            val classes = reply.roots.map { it.className }.toSet()
            val current = sources.filter { it.markup.className in classes }.mapNotNull { source ->
                runCatching { source.copy(markup = WinRTHotReloadMarkup.parse(documentText(source.path))) }.getOrNull()
            }
            reply.roots.forEach { root ->
                val key = root.className to root.resourcePath
                val candidates = listOfNotNull(baselines[key], attempted[key]) + sources + current
                val matching = candidates.filter { it.markup.className == root.className && it.markup.hash == root.sourceHash &&
                    it.path.replace('\\', '/').endsWith("/${root.resourcePath.replace('\\', '/')}", true) }.distinctBy { it.path }
                if (matching.size == 1) baselines[key] = matching.single()
            }
            uncertain = false
        }
        val missing = reply.roots.filter { baselines[it.className to it.resourcePath]?.markup?.hash != it.sourceHash }
        val previous = display.value
        display.value = previous.copy(message = if (missing.isEmpty()) {
            if (!previous.connected || previous.busy || previous.roots.isEmpty()) reply.message else previous.message
        } else
            "Source does not match ${missing.joinToString { it.className }}. Rebuild and restart before updating.",
            connected = reply.status == WinRTXamlHotReloadProtocol.APPLIED, busy = reply.status == WinRTXamlHotReloadProtocol.UNAVAILABLE,
            pid = connection.process.pid(), roots = reply.roots)
    }

    fun apply() {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val connection = client ?: return@withLock
                if (uncertain || !display.value.connected) return@withLock
                try {
                    val results = mutableListOf<WinRTXamlHotReloadValue>()
                    var updated = 0
                    for (root in display.value.roots) {
                        val key = root.className to root.resourcePath
                        val before = baselines[key] ?: continue
                        val after = before.copy(markup = WinRTHotReloadMarkup.parse(documentText(before.path)))
                        if (after.markup.hash == root.sourceHash) continue
                        val catalog = project.service<WinRTXamlCatalogService>().forFile(before.path)
                        val patch = after.markup.patch(before.markup, root) { uri, type, property ->
                            val member = catalog?.resolve(uri, type)?.let { catalog.members(it).firstOrNull { m -> m.name == property } }
                            if (member?.isEvent == true) "Changing event connections requires rebuilding and restarting." else null
                        }
                        attempted[key] = after
                        display.value = display.value.copy(message = "Applying ${patch.changes.size} property changes…", busy = true)
                        val reply = connection.request(patch)
                        if (reply.status != WinRTXamlHotReloadProtocol.APPLIED) {
                            uncertain = reply.status == WinRTXamlHotReloadProtocol.UNAVAILABLE
                            display.value = display.value.copy(message = reply.message, busy = uncertain,
                                connected = reply.status == WinRTXamlHotReloadProtocol.REJECTED, roots = reply.roots)
                            return@withLock
                        }
                        baselines[key] = after; attempted.remove(key)
                        results += reply.values; updated += patch.changes.size
                        display.value = display.value.copy(roots = reply.roots)
                    }
                    val missing = display.value.roots.count { baselines[it.className to it.resourcePath]?.markup?.hash != it.sourceHash }
                    display.value = display.value.copy(message = if (missing != 0) "$updated properties updated; $missing classes have no matching source. Rebuild to update them."
                        else if (updated == 0) "XAML matches the running components." else "$updated properties updated in place.",
                        busy = false, values = results)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    // A lost response may have committed on the UI thread. Handshake reconciles hashes before another request.
                    uncertain = attempted.isNotEmpty()
                    display.value = display.value.copy(message = error.message.orEmpty(), busy = false, connected = !uncertain)
                }
            }
        }
    }

    fun disconnect() {
        ++generation; worker?.cancel(); edits?.cancel(); client?.close(); client = null
        display.value = WinRTHotReloadState("Development connection closed. The application can continue running.", pid = owned?.process?.takeIf { it.isAlive }?.pid())
    }

    fun stop() {
        disconnect()
        scope.launch(Dispatchers.IO) { mutex.withLock {
            runCatching { stopOwned() }.fold({ display.value = WinRTHotReloadState("Development application stopped.") },
                { display.value = WinRTHotReloadState(it.message.orEmpty()) })
        } }
    }

    private fun stopOwned() {
        owned?.let { previous ->
            if (previous.process.isAlive) {
                check(previous.process.info().startInstant().orElse(null) == previous.started) { "The original application has exited; refusing to stop a reused process ID." }
                check(previous.process.destroy()) { "Stop the application from its Gradle Run window." }
                previous.process.onExit().get(8, TimeUnit.SECONDS)
            }
            previous.close()
            // This file belongs to a process launched in this service's fresh session directory.
            Files.deleteIfExists(previous.sessionFile)
        }
        owned = null
    }

    private fun documentText(path: String): String = ReadAction.compute<String?, RuntimeException> {
        LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))?.let { FileDocumentManager.getInstance().getCachedDocument(it)?.text }
    } ?: Files.readString(Path.of(path))

    private fun loadSources(): List<Source> = project.service<WinRTProjectService>().modules.value
        .flatMap { it.xamlCompilations }.flatMap { it.sourceRoots }.distinct().flatMap { root ->
            val path = Path.of(root)
            if (!Files.isDirectory(path)) emptyList() else Files.walk(path).use { files -> files.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".xaml", true) && Files.size(it) <= 2 * 1024 * 1024
            }.limit(4096).map { it.toAbsolutePath().normalize().toString() }.toList() }
        }.distinct().mapNotNull { path -> runCatching { Source(path, WinRTHotReloadMarkup.parse(documentText(path))) }
            .getOrNull()?.takeIf { it.markup.className.isNotEmpty() } }

    override fun dispose() { ++generation; client?.close(); owned?.close() }
}
