package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class WinRTHotReloadState(val message: String = "Start a JVM application with XAML Hot Reload to update its properties.",
    val connected: Boolean = false, val busy: Boolean = false, val pid: Long? = null,
    val roots: List<WinRTXamlHotReloadRoot> = emptyList(), val values: List<WinRTXamlHotReloadValue> = emptyList(),
    val inspection: WinRTXamlVisualSnapshot? = null, val inspecting: Boolean = false)

/** Workspace metadata only: authentication remains in the runtime's session file. */
data class WinRTHotReloadLaunchState(var moduleDirectory: String = "", var taskName: String = "", var sessionDirectory: String = "",
    var processId: Long = 0, var startedUtc: String = "")

open class WinRTDevelopmentSession(private val project: Project, private val scope: CoroutineScope,
    private val isolatedPreview: Boolean = false) : Disposable, PersistentStateComponent<WinRTHotReloadLaunchState> {
    private data class Source(val path: String, val markup: WinRTHotReloadMarkup)
    private val display = MutableStateFlow(if (isolatedPreview)
        WinRTHotReloadState("Start the design host to render the XAML document.") else WinRTHotReloadState())
    val state: StateFlow<WinRTHotReloadState> = display
    val automatic = MutableStateFlow(!isolatedPreview)
    private val mutex = Mutex()
    @Volatile private var client: WinRTHotReloadClient? = null
    private val generation = AtomicLong()
    private val lifecycle = Any()
    @Volatile private var disposed = false
    private var directory: Path? = null
    private var selectedModule: WinRTModuleData? = null
    private var selectedLaunch: WinRTHotReloadLaunchData? = null
    private var sources = emptyList<Source>()
    private val baselines = mutableMapOf<Pair<String, String>, Source>()
    private val attempted = mutableMapOf<Pair<String, String>, Source>()
    private var worker: Job? = null
    private var edits: Job? = null
    private var update: Job? = null
    private var inspection: Job? = null
    private var pendingInspection: WinRTXamlInspectionRequest? = null
    private var inspectionOwner: Triple<String, String, Int>? = null
    private var uncertain = false
    private var owned: WinRTHotReloadClient? = null
    private var savedLaunch = WinRTHotReloadLaunchState()

    override fun getState(): WinRTHotReloadLaunchState = synchronized(lifecycle) { savedLaunch.copy() }
    override fun loadState(state: WinRTHotReloadLaunchState) = synchronized(lifecycle) {
        savedLaunch = state.copy()
        Unit
    }

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (!disposed && FileDocumentManager.getInstance().getFile(event.document)?.extension.equals("xaml", true) && automatic.value && client != null) {
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
    fun start(module: WinRTModuleData, launch: WinRTHotReloadLaunchData, restart: Boolean = false, preview: Boolean = false) {
        if (disposed || project.isDisposed) return
        val sdkPreview = preview && launch == module.staticPreview?.launch()
        require(launch in module.hotReloadLaunches || sdkPreview) { "Synchronize Gradle before selecting this application." }
        if (display.value.busy) return
        val request = beginRequest(WinRTHotReloadState("Preparing the development launch…", busy = true))
        worker = scope.launch(Dispatchers.IO) {
            try {
                // Compose effects and clicks run on EDT without the platform's
                // write-intent lock. Only app-derived launches need saved files;
                // the SDK designer renders the editor's unsaved XAML directly.
                if (!sdkPreview) withContext(Dispatchers.EDT) {
                    writeIntentReadAction {
                        if (isCurrent(request)) FileDocumentManager.getInstance().saveAllDocuments()
                    }
                }
                mutex.withLock {
                    if (!isCurrent(request)) return@launch
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
                    withCurrent(request) { savedLaunch = WinRTHotReloadLaunchState(module.projectDirectory, launch.taskName, directory.toString()) }
                }
                ApplicationManager.getApplication().invokeLater {
                    if (isCurrent(request)) WinRTGradleTasks.run(project, module,
                        listOf(launch.taskName), if (preview) "XAML Static Preview" else "Run with XAML Hot Reload",
                        environment = mapOf(WinRTXamlHotReloadProtocol.SESSION_DIRECTORY to directory!!.toString()) +
                            if (preview) mapOf(WinRTXamlHotReloadProtocol.PREVIEW_ENVIRONMENT to "1") else emptyMap(),
                        onFailure = { withCurrent(request) { if (client == null) {
                            worker?.cancel(); display.value = WinRTHotReloadState("The Gradle launch failed or stopped. See its Run output.")
                        } } })
                }
                if (!withCurrent(request) {
                    display.value = WinRTHotReloadState(if (sdkPreview) "Preparing the WinUI designer. Project code is not compiled. See Gradle Run output."
                        else "Building and waiting for the application. See Gradle Run output.", busy = true)
                }) return@launch
                withTimeout(10 * 60_000L) {
                    while (isActive && isCurrent(request)) {
                        val found = WinRTHotReloadClient.discover(directory!!, if (preview) launch.previewExecutable else launch.executable)
                        require(found.size <= 1) { "Multiple development processes use this session directory. Stop them before reconnecting." }
                        val candidate = found.singleOrNull()
                        if (candidate != null) {
                            mutex.withLock {
                                if (!withCurrent(request) { client = candidate; owned = candidate }) {
                                    candidate.close(); return@withTimeout
                                }
                                refresh(candidate, request)
                                if (sdkPreview) {
                                    project.service<WinRTXamlCatalogService>().refresh()
                                    project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceChanges>().revision.value += 1
                                }
                            }
                            break
                        }
                        delay(750)
                    }
                }
                watch(request)
            } catch (error: TimeoutCancellationException) {
                withCurrent(request) { display.value = WinRTHotReloadState("The application did not publish a development session in time. See Gradle Run output.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = WinRTHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } }
        }
    }

    /** Opening/switching a preview reuses its host; a failed launch needs an explicit retry. */
    fun ensurePreview(module: WinRTModuleData, launch: WinRTHotReloadLaunchData) {
        if (display.value.busy || (selectedModule == module && selectedLaunch == launch)) return
        start(module, launch, restart = display.value.pid != null, preview = true)
    }

    fun reconnect() {
        if (disposed || project.isDisposed) return
        val request = beginRequest(display.value.copy(message = "Reconnecting to the development application…", connected = false, busy = true))
        worker = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (!isCurrent(request)) return@launch
                    val saved = getState()
                    check(saved.moduleDirectory.isNotBlank() && saved.taskName.isNotBlank() && saved.sessionDirectory.isNotBlank()) {
                        "No previous development launch is saved. Start with Hot Reload first."
                    }
                    val restoring = directory == null || selectedLaunch == null
                    check(!restoring || (saved.processId > 0 && saved.startedUtc.isNotBlank())) {
                        "The previous application was not connected before closing the project. Start a new session."
                    }
                    val module = project.service<WinRTProjectService>().modules.value.singleOrNull {
                        Path.of(it.projectDirectory).toAbsolutePath().normalize() == Path.of(saved.moduleDirectory).toAbsolutePath().normalize()
                    } ?: error("Synchronize the module from the saved development launch before reconnecting.")
                    val launch = (module.hotReloadLaunches + listOfNotNull(module.staticPreview?.launch())).singleOrNull { it.taskName == saved.taskName }
                        ?: error("The saved development launch is no longer configured. Synchronize Gradle and start a new session.")
                    val folder = Path.of(saved.sessionDirectory).toAbsolutePath().normalize()
                    val expected = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload").toAbsolutePath().normalize()
                    require(folder.parent == expected && runCatching { UUID.fromString(folder.fileName.toString()).toString().equals(folder.fileName.toString(), true) }.getOrDefault(false)) {
                        "The saved development session does not belong to this module. Start a new session."
                    }
                    val next = WinRTHotReloadClient.discover(folder, if (isolatedPreview) launch.previewExecutable else launch.executable)
                    require(next.size == 1) { "No unique live development session. Rebuild and restart the application." }
                    val candidate = next.single()
                    if (saved.processId > 0) {
                        require(candidate.process.pid() == saved.processId && candidate.started == Instant.parse(saved.startedUtc)) {
                            candidate.close()
                            "The saved application has exited or its process ID was reused. Start a new session."
                        }
                    }
                    val recoveredSources = if (restoring) loadSources() else null
                    if (!withCurrent(request) {
                        if (recoveredSources != null) {
                            sources = recoveredSources; baselines.clear(); attempted.clear(); uncertain = false
                        }
                        directory = folder; selectedModule = module; selectedLaunch = launch
                        client = candidate; owned = candidate
                    }) {
                        candidate.close(); return@launch
                    }
                    // The workspace records only this plugin's module-owned launch directory.
                    refresh(candidate, request)
                }
                watch(request)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = WinRTHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } }
        }
    }

    private suspend fun watch(request: Long) {
        while (scope.isActive && isCurrent(request)) {
            delay(2_000)
            try { mutex.withLock { if (isCurrent(request)) client?.let { refresh(it, request) } } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                withCurrent(request) {
                    client?.close(); client = null
                    display.value = display.value.copy(message = error.message.orEmpty(), connected = false, busy = false,
                        pid = owned?.process?.takeIf { it.isAlive }?.pid())
                }
                break
            }
        }
    }

    private fun refresh(connection: WinRTHotReloadClient, request: Long) {
        val reply = connection.request()
        if (!isCurrent(request) || client !== connection) return
        val nextBaselines = baselines.toMutableMap()
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
                if (matching.size == 1) nextBaselines[key] = matching.single()
            }
        }
        withCurrent(request) {
            if (client !== connection) return@withCurrent
            baselines.clear(); baselines.putAll(nextBaselines)
            if (reply.status == WinRTXamlHotReloadProtocol.APPLIED) {
                uncertain = false
                savedLaunch = savedLaunch.copy(processId = connection.process.pid(), startedUtc = connection.started.toString())
            }
            val missing = reply.roots.filter { it.className != WinRTXamlHotReloadProtocol.PREVIEW_CLASS &&
                baselines[it.className to it.resourcePath]?.markup?.hash != it.sourceHash }
            val previous = display.value
            display.value = previous.copy(message = if (missing.isEmpty()) {
                if (!previous.connected || previous.busy || previous.roots.isEmpty()) reply.message else previous.message
            } else
                "Source does not match ${missing.joinToString { it.className }}. Rebuild and restart before updating.",
                connected = reply.status == WinRTXamlHotReloadProtocol.APPLIED, busy = reply.status == WinRTXamlHotReloadProtocol.UNAVAILABLE,
                pid = connection.process.pid(), roots = reply.roots)
        }
    }

    fun apply() {
        if (disposed || project.isDisposed || update?.isActive == true) return
        val request = generation.get()
        update = scope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (!isCurrent(request)) return@withLock
                val connection = client ?: return@withLock
                if (uncertain || !display.value.connected) return@withLock
                try {
                    val results = mutableListOf<WinRTXamlHotReloadValue>()
                    var updated = 0
                    for (root in display.value.roots) {
                        if (!isCurrent(request) || client !== connection) return@withLock
                        val key = root.className to root.resourcePath
                        val before = baselines[key] ?: continue
                        val after = before.copy(markup = WinRTHotReloadMarkup.parse(documentText(before.path)))
                        if (after.markup.hash == root.sourceHash) continue
                        val catalog = project.service<WinRTXamlCatalogService>().forFile(before.path)
                        val patch = after.markup.patch(before.markup, root, propertyProblem = { uri, type, property ->
                            val member = catalog?.resolve(uri, type)?.let { catalog.members(it).firstOrNull { m -> m.name == property } }
                            if (member?.isEvent == true) "Changing event connections requires rebuilding and restarting." else null
                        }, contentMember = { uri, type, property ->
                            catalog?.resolve(uri, type)?.let {
                                if (property == null) catalog.contentMember(it) else catalog.propertyContent(it, property)
                            }
                        })
                        val count = patch.changes.size + patch.resources.size + patch.children.size
                        if (!withCurrent(request) {
                            attempted[key] = after
                            display.value = display.value.copy(message = "Applying $count XAML updates…", busy = true)
                        }) return@withLock
                        val reply = connection.request(patch)
                        if (!isCurrent(request) || client !== connection) return@withLock
                        if (reply.status != WinRTXamlHotReloadProtocol.APPLIED) {
                            withCurrent(request) {
                                uncertain = reply.status == WinRTXamlHotReloadProtocol.UNAVAILABLE
                                display.value = display.value.copy(message = reply.message, busy = uncertain,
                                    connected = reply.status == WinRTXamlHotReloadProtocol.REJECTED, roots = reply.roots)
                            }
                            return@withLock
                        }
                        if (!withCurrent(request) {
                            baselines[key] = after; attempted.remove(key)
                            results += reply.values; updated += count
                            display.value = display.value.copy(roots = reply.roots)
                        }) return@withLock
                    }
                    val missing = display.value.roots.count { baselines[it.className to it.resourcePath]?.markup?.hash != it.sourceHash }
                    withCurrent(request) { display.value = display.value.copy(message = if (missing != 0) "$updated updates applied; $missing classes have no matching source. Rebuild to update them."
                        else if (updated == 0) "XAML matches the running components." else "$updated XAML updates applied.",
                        busy = false, values = results.distinct()) }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (!isCurrent(request) || client !== connection) return@withLock
                    // A lost response may have committed on the UI thread. Handshake reconciles hashes before another request.
                    withCurrent(request) {
                        uncertain = attempted.isNotEmpty()
                        display.value = display.value.copy(message = error.message.orEmpty(), busy = false, connected = !uncertain)
                    }
                }
            }
        }
    }

    fun disconnect() {
        beginRequest(WinRTHotReloadState("Development connection closed. The application can continue running.", pid = owned?.process?.takeIf { it.isAlive }?.pid()))
    }

    fun inspect(request: WinRTXamlInspectionRequest) {
        synchronized(lifecycle) {
            val connection = client ?: return
            val epoch = generation.get()
            if (!isCurrent(epoch)) return
            // Coalesce rapid edits/selections, but keep an unrendered design document.
            val previous = pendingInspection
            pendingInspection = if (request.previewMarkup.isEmpty() && previous?.previewMarkup?.isNotEmpty() == true &&
                previous.className == request.className && previous.resourcePath == request.resourcePath) request.copy(
                    previewMarkup = previous.previewMarkup, width = previous.width, height = previous.height, theme = previous.theme)
                else request
            if (display.value.inspecting) return
            display.value = display.value.copy(inspecting = true)
            inspection = scope.launch(Dispatchers.IO) {
                try {
                    while (isCurrent(epoch)) {
                        val next = synchronized(lifecycle) {
                            pendingInspection.also {
                                pendingInspection = null
                                if (it == null) { inspection = null; display.value = display.value.copy(inspecting = false) }
                            }
                        } ?: break
                        val reply = mutex.withLock { runInterruptible { connection.inspect(next) } }
                        if (client === connection) withCurrent(epoch) {
                            val owner = Triple(next.className, next.resourcePath, next.instance)
                            val image = display.value.inspection?.image.takeIf { inspectionOwner == owner }
                            display.value = display.value.copy(inspection = reply.inspection?.let { it.copy(image = it.image ?: image) } ?: display.value.inspection,
                                message = reply.message, roots = reply.roots.ifEmpty { display.value.roots })
                            if (reply.inspection != null) inspectionOwner = owner
                        }
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Exception) { withCurrent(epoch) { display.value = display.value.copy(message = error.message.orEmpty()) } }
                finally { withCurrent(epoch) { if (inspection === coroutineContext[Job]) {
                    inspection = null; display.value = display.value.copy(inspecting = false)
                } } }
            }
        }
    }

    fun sourcePath(root: WinRTXamlHotReloadRoot): String? = sourcePath(root.className)
    fun sourcePath(className: String): String? = sources.firstOrNull { it.markup.className == className }?.path

    fun stop() {
        if (disposed || project.isDisposed) return
        val request = beginRequest(WinRTHotReloadState("Stopping the development application…", busy = true,
            pid = owned?.process?.takeIf { it.isAlive }?.pid()))
        worker = scope.launch(Dispatchers.IO) { mutex.withLock {
            if (!isCurrent(request)) return@withLock
            runCatching { stopOwned() }.fold({ withCurrent(request) { display.value = WinRTHotReloadState("Development application stopped.") } },
                { error -> withCurrent(request) { display.value = WinRTHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } })
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

    private fun isCurrent(request: Long) = !disposed && !project.isDisposed && request == generation.get()

    private inline fun withCurrent(request: Long, action: () -> Unit): Boolean = synchronized(lifecycle) {
        if (!isCurrent(request)) false else { action(); true }
    }

    private fun beginRequest(next: WinRTHotReloadState): Long = synchronized(lifecycle) {
        val request = generation.incrementAndGet()
        cancelRequests()
        display.value = next.copy(inspecting = false)
        request
    }

    private fun cancelRequests() {
        worker?.cancel(); edits?.cancel(); update?.cancel(); inspection?.cancel()
        pendingInspection = null
        inspectionOwner = null
        // Closing the sockets interrupts blocking transport IO immediately.
        // Cancellation alone cannot interrupt Socket.read on Dispatchers.IO.
        client?.close(); client = null
    }

    override fun dispose() = synchronized(lifecycle) {
        disposed = true; generation.incrementAndGet(); cancelRequests()
        owned?.let { process ->
            if (isolatedPreview && process.process.isAlive && process.process.info().startInstant().orElse(null) == process.started)
                process.process.destroy()
            process.close()
        }
        Unit
    }
}

@Service(Service.Level.PROJECT)
@State(name = "KotlinWinRTXamlHotReload", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WinRTHotReloadService(project: Project, scope: CoroutineScope) : WinRTDevelopmentSession(project, scope)

@Service(Service.Level.PROJECT)
@State(name = "KotlinWinRTXamlStaticPreview", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WinRTStaticPreviewService(project: Project, scope: CoroutineScope) : WinRTDevelopmentSession(project, scope, isolatedPreview = true)
