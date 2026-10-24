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
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalogService
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

data class WinUIXamlHotReloadState(val message: String = "Start a JVM application with XAML Hot Reload to update its properties.",
    val connected: Boolean = false, val busy: Boolean = false, val pid: Long? = null,
    val roots: List<WinUIXamlHotReloadRoot> = emptyList(), val values: List<WinUIXamlHotReloadValue> = emptyList(),
    val inspection: WinUIXamlVisualSnapshot? = null, val inspecting: Boolean = false, val inspectionError: String? = null,
    val inspectionRoots: List<WinUIXamlHotReloadRoot> = emptyList(),
    val startupMillis: Long? = null, val renderMillis: Long? = null, val reusedBuild: Boolean = false,
    val inspectionPath: List<Int>? = null)

/** Workspace metadata only: authentication remains in the runtime's session file. */
data class WinUIXamlHotReloadLaunchState(var moduleDirectory: String = "", var taskName: String = "", var sessionDirectory: String = "",
    var processId: Long = 0, var startedUtc: String = "")

open class WinRTDevelopmentSession(private val project: Project, private val scope: CoroutineScope,
    private val isolatedPreview: Boolean = false) : Disposable, PersistentStateComponent<WinUIXamlHotReloadLaunchState> {
    private data class Source(val path: String, val markup: WinUIXamlHotReloadMarkup)
    private val display = MutableStateFlow(if (isolatedPreview)
        WinUIXamlHotReloadState("Start the design host to render the XAML document.") else WinUIXamlHotReloadState())
    val state: StateFlow<WinUIXamlHotReloadState> = display
    val automatic = MutableStateFlow(!isolatedPreview)
    private val mutex = Mutex()
    @Volatile private var client: WinUIXamlHotReloadClient? = null
    private val generation = AtomicLong()
    private val lifecycle = Any()
    @Volatile private var disposed = false
    private var directory: Path? = null
    private var selectedModule: WinRTModuleData? = null
    private var selectedLaunch: WinUIXamlHotReloadLaunchData? = null
    private var sources = emptyList<Source>()
    private val baselines = mutableMapOf<Pair<String, String>, Source>()
    private val attempted = mutableMapOf<Pair<String, String>, Source>()
    private var worker: Job? = null
    private var edits: Job? = null
    private var update: Job? = null
    private var inspection: Job? = null
    private var pendingInspection: WinUIXamlInspectionRequest? = null
    private var inspectionOwner: Triple<String, String, Int>? = null
    private var uncertain = false
    private var owned: WinUIXamlHotReloadClient? = null
    @Volatile private var gradleProcess: com.intellij.execution.process.ProcessHandler? = null
    private var savedLaunch = WinUIXamlHotReloadLaunchState()
    @Volatile private var debuggerPid: Long? = null
    private var directPreview: Process? = null
    private var pendingPreviewCache: Pair<WinRTPreviewLaunchCache, String>? = null
    private var launchStartedNanos = 0L
    private var firstFrameReported = false
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(WinRTDevelopmentSession::class.java)

    /** Drain the last UI transaction before LLDB suspends the process. */
    fun suspendForDebugger(pid: Long): java.util.concurrent.CompletableFuture<Unit> {
        val result = java.util.concurrent.CompletableFuture<Unit>()
        debuggerPid = pid
        if (client?.process?.pid() != pid) { result.complete(Unit); return result }
        val drain = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (client?.process?.pid() == pid) display.value = display.value.copy(
                        message = "XAML updates are paused while the Native debugger is attached.", inspecting = false)
                }
                result.complete(Unit)
            } catch (error: Throwable) { result.completeExceptionally(error) }
        }
        drain.invokeOnCompletion { error -> if (error != null) result.completeExceptionally(error) }
        return result
    }

    fun resumeAfterDebugger(pid: Long) {
        if (debuggerPid != pid) return
        debuggerPid = null
        if (automatic.value) apply()
    }

    private fun debuggerAttached() = client?.process?.pid()?.let { it == debuggerPid } == true

    override fun getState(): WinUIXamlHotReloadLaunchState = synchronized(lifecycle) { savedLaunch.copy() }
    override fun loadState(state: WinUIXamlHotReloadLaunchState) = synchronized(lifecycle) {
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
    fun start(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, restart: Boolean = false, preview: Boolean = false) {
        if (disposed || project.isDisposed) return
        val sdkPreview = preview && launch == module.staticPreview?.launch()
        val startedAt = System.nanoTime()
        require(launch in module.hotReloadLaunches || sdkPreview) { "Synchronize Gradle before selecting this application." }
        if (display.value.busy) return
        val request = beginRequest(WinUIXamlHotReloadState("Preparing the development launch…", busy = true))
        launchStartedNanos = startedAt; firstFrameReported = false
        gradleProcess = null
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
                    withCurrent(request) { savedLaunch = WinUIXamlHotReloadLaunchState(module.projectDirectory, launch.taskName, directory.toString()) }
                }
                if (preview) log.info("XAML preview sources ready: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                val cache = if (preview && (sdkPreview && launch.taskName == "runWinRTXamlSdkPreview" || launch.nativeCompilerHome.isNotBlank() && launch.taskName.startsWith("runWinAppPackage"))) WinRTPreviewLaunchCache(
                    Path.of(project.service<WinRTProjectService>().buildRootFor(module) ?: module.projectDirectory),
                    project.service<WinRTProjectService>().modules.value, module, launch) else null
                val inputs = cache?.let { runCatching { it.inputs() }.getOrNull() }
                if (preview) log.info("XAML preview inputs checked: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                // The SDK designer stages project assets after the host connects.
                // Record its deployment only after that document actually renders.
                if (sdkPreview && cache != null && inputs != null) withCurrent(request) {
                    pendingPreviewCache = cache to inputs
                }
                var reused = false
                if (cache != null && inputs != null && !restart && cache.reusable(inputs)) {
                    try {
                        if (preview) log.info("XAML preview cache validated: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                        val process = ProcessBuilder(launch.previewExecutable, WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT)
                            .directory(cache.deployment.toFile())
                            .redirectErrorStream(true).redirectOutput(directory!!.resolve("preview-host.log").toFile())
                            .apply { environment()[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY] = directory!!.toString()
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1" }.start()
                        if (!withCurrent(request) {
                            directPreview = process
                            display.value = display.value.copy(message = "Starting the built preview host…")
                        }) { process.destroy(); return@launch }
                        // Reusing the build does not make JVM/WinUI startup instantaneous,
                        // especially while the IDE is initializing. Use the same bounded
                        // publication and root-initialization waits as a freshly built host.
                        awaitConnection(request, directory!!, launch.previewExecutable, process)
                        reused = true
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        currentCoroutineContext().ensureActive()
                        mutex.withLock { stopOwned(); client?.close(); client = null }
                        cache.invalidate()
                        // TimeoutCancellationException is coroutine control flow; IntelliJ
                        // rejects logging it as a Throwable. Preserve the reason without a stack.
                        log.info("Cached preview launch failed for ${launch.taskName}: ${error.javaClass.simpleName}: ${error.message}; rebuilding through Gradle")
                    }
                }
                if (!reused) {
                    val buildFinished = CompletableDeferred<Unit>()
                    // A Gradle Exec task keeps the project execution lock while
                    // its host lives. Prepare the desktop SDK host, release Gradle,
                    // then own the process so project compilation can proceed.
                    val detachedSdk = sdkPreview && launch.taskName == "runWinRTXamlSdkPreview"
                    // Register the desktop Native package without launching a child
                    // that would inherit Gradle pipes. UWP still uses package activation.
                    val detachedNative = preview && launch.isDirectPreview
                    ApplicationManager.getApplication().invokeLater {
                        if (isCurrent(request)) WinRTGradleTasks.run(project, module,
                            listOf(if (detachedSdk) "prepareWinRTXamlSdkPreview" else launch.taskName), if (preview) "XAML Static Preview" else "Run with XAML Hot Reload",
                            environment = mapOf(WinUIXamlHotReloadProtocol.SESSION_DIRECTORY to directory!!.toString()) +
                                (if (preview) mapOf(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT to "1") else emptyMap()) +
                                if (detachedNative) mapOf("KOTLIN_WINRT_REGISTER_ONLY" to "1") else emptyMap(),
                            arguments = if (detachedNative) "--no-launch" else "",
                            onProcessStarted = { handler ->
                                if (!withCurrent(request) { gradleProcess = handler }) WinRTGradleTasks.cancel(project, handler)
                            },
                            onSuccess = { buildFinished.complete(Unit) },
                            onFailure = { withCurrent(request) { if (client == null) {
                                worker?.cancel(); display.value = WinUIXamlHotReloadState("The Gradle launch failed or stopped. See its Run output.")
                            } } })
                    }
                    if (!withCurrent(request) {
                        display.value = WinUIXamlHotReloadState(if (sdkPreview) "Preparing the WinUI designer. Project code is not compiled. See Gradle Run output."
                            else "Building and waiting for the application. See Gradle Run output.", busy = true)
                    }) return@launch
                    if (detachedSdk || detachedNative) {
                        buildFinished.await()
                        currentCoroutineContext().ensureActive()
                        val executable = if (detachedNative) launch.previewExecutable else launch.executable
                        val process = ProcessBuilder(executable, WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT)
                            .directory((if (detachedNative) Path.of(executable).parent else Path.of(launch.workingDirectory)).toFile())
                            .redirectErrorStream(true).redirectOutput(directory!!.resolve("preview-host.log").toFile())
                            .apply { environment()[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY] = directory!!.toString()
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1" }.start()
                        if (!withCurrent(request) { directPreview = process }) { process.destroy(); return@launch }
                        awaitConnection(request, directory!!, executable, process)
                    } else awaitConnection(request, directory!!, if (preview) launch.previewExecutable else launch.executable,
                        building = { !buildFinished.isCompleted && gradleProcess?.isProcessTerminated != true })
                    // Never bless inputs edited while Gradle was compiling an earlier version.
                    if (!sdkPreview && inputs != null && cache != null && isCurrent(request)) runCatching {
                        if (display.value.connected) cache.rememberIfUnchanged(inputs)
                    }.onFailure { log.debug("Preview launch cache unavailable", it) }
                }
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                withCurrent(request) { display.value = display.value.copy(startupMillis = elapsed, reusedBuild = reused) }
                if (preview) log.info("XAML preview connected: ${elapsed}ms, reusedBuild=$reused")
                // A first project build also creates metadata manifests outside the VFS cache.
                if (preview && !reused && isCurrent(request)) {
                    project.service<WinUIXamlCatalogService>().refresh()
                    project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceChanges>().revision.value += 1
                }
                watch(request)
            } catch (error: TimeoutCancellationException) {
                withCurrent(request) { display.value = WinUIXamlHotReloadState("The application did not publish a development session in time. See Gradle Run output.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } }
        }
    }

    /** Observe an application already being launched by the IDE's native
     * runner. This must not schedule another Gradle execution or stop an
     * application from an earlier Run window. */
    fun attach(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, sessionDirectory: Path, building: () -> Boolean = { false }) {
        if (disposed || project.isDisposed) return
        require(launch in module.hotReloadLaunches) { "Synchronize Gradle before selecting this application." }
        val folder = sessionDirectory.toAbsolutePath().normalize()
        val expected = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload").toAbsolutePath().normalize()
        require(folder.parent == expected && runCatching { UUID.fromString(folder.fileName.toString()) }.isSuccess) {
            "The development session does not belong to this module."
        }
        val request = beginRequest(WinUIXamlHotReloadState("Building and waiting for the application. See Gradle Run output.", busy = true))
        withCurrent(request) { savedLaunch = WinUIXamlHotReloadLaunchState(module.projectDirectory, launch.taskName, folder.toString()) }
        worker = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (!isCurrent(request)) return@launch
                    owned?.close(); owned = null
                    sources = loadSources()
                    baselines.clear(); attempted.clear(); uncertain = false
                    selectedModule = module; selectedLaunch = launch; directory = folder
                    Files.createDirectories(folder)
                    withCurrent(request) { savedLaunch = WinUIXamlHotReloadLaunchState(module.projectDirectory, launch.taskName, folder.toString()) }
                }
                awaitConnection(request, folder, launch.executable, building = building)
                watch(request)
            } catch (error: TimeoutCancellationException) {
                withCurrent(request) { display.value = WinUIXamlHotReloadState("The application did not publish a development session in time. See Gradle Run output.") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty()) } }
        }
    }

    fun launchFailed(sessionDirectory: Path) = synchronized(lifecycle) {
        if (!disposed && savedLaunch.sessionDirectory == sessionDirectory.toAbsolutePath().normalize().toString() &&
            client == null && display.value.pid == null)
            beginRequest(WinUIXamlHotReloadState("The Gradle launch failed or stopped. See its Run output."))
        Unit
    }

    private suspend fun awaitConnection(request: Long, folder: Path, executable: String, process: Process? = null,
        building: () -> Boolean = { false }) {
        val candidate = awaitDevelopmentPublication(building) {
            currentCoroutineContext().ensureActive()
            check(isCurrent(request)) { "The development launch was superseded." }
            check(process == null || process.isAlive) { "The cached preview host exited before connecting." }
            val found = WinUIXamlHotReloadClient.discover(folder, executable)
            require(found.size <= 1) { "Multiple development processes use this session directory. Stop them before reconnecting." }
            found.singleOrNull()
        }
        // Compilation is cancellable without a wall-clock limit. A published
        // host must still complete its handshake and root initialization promptly.
        withTimeout(30_000) {
            mutex.withLock {
                if (!withCurrent(request) { client = candidate; owned = candidate }) {
                    candidate.close(); return@withTimeout
                }
                refresh(candidate, request)
            }
            while (isolatedPreview && isCurrent(request) && display.value.inspectionRoots.none {
                    it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS }) {
                delay(50)
                mutex.withLock { if (isCurrent(request)) refresh(candidate, request) }
            }
        }
    }

    /** Reuse only a host that has actually exposed this component, not an arbitrary
     * application in the workspace. A library need not have its own executable. */
    fun loadedSourceLaunch(path: String): Pair<WinRTModuleData, WinUIXamlHotReloadLaunchData>? {
        val current = display.value
        if (!current.connected) return null
        val normalized = Path.of(path).toAbsolutePath().normalize()
        val source = sources.singleOrNull { Path.of(it.path).toAbsolutePath().normalize() == normalized } ?: return null
        val name = source.markup.className
        if (current.inspectionRoots.none { it.className == name } &&
            current.inspection?.nodes?.none { it.typeName == name } != false) return null
        val module = selectedModule ?: return null
        val launch = selectedLaunch ?: return null
        project.service<WinRTPreviewSourceHosts>().remember(path, module, launch)
        return module to launch
    }

    fun sourcePreviewLaunch(path: String): Pair<WinRTModuleData, WinUIXamlHotReloadLaunchData>? =
        loadedSourceLaunch(path) ?: project.service<WinRTPreviewSourceHosts>().resolve(path,
            project.service<WinRTProjectService>().modules.value)

    /** Opening/switching a preview reuses its host; a failed launch needs an explicit retry. */
    fun ensurePreview(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData) {
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
                    val next = WinUIXamlHotReloadClient.discover(folder, if (isolatedPreview) launch.previewExecutable else launch.executable)
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
            catch (error: Exception) { withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } }
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

    private fun refresh(connection: WinUIXamlHotReloadClient, request: Long) {
        if (connection.process.pid() == debuggerPid) return
        val reply = connection.request()
        if (!isCurrent(request) || client !== connection) return
        val pages = project.service<io.github.composefluent.winrt.ide.analysis.WinUIXamlSnapshotService>()
            .state.value.values.flatMap { it.declarations.pages }.associateBy { it.className }
        val visualRoots = reply.roots.filterNot { root ->
            pages[root.className]?.isApplication == true || sources.any { it.markup.className == root.className && it.markup.isApplication }
        }
        val windows = visualRoots.filter { root -> sources.any { source -> source.markup.className == root.className &&
            source.markup.isWindow(project.service<WinUIXamlCatalogService>().forFile(source.path), pages[root.className]?.baseTypeName) } }
        val nextBaselines = baselines.toMutableMap()
        if (reply.status == WinUIXamlHotReloadProtocol.APPLIED) {
            val classes = reply.roots.map { it.className }.toSet()
            val current = sources.filter { it.markup.className in classes }.mapNotNull { source ->
                runCatching { source.copy(markup = WinUIXamlHotReloadMarkup.parse(documentText(source.path))) }.getOrNull()
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
            if (reply.status == WinUIXamlHotReloadProtocol.APPLIED) {
                uncertain = false
                savedLaunch = savedLaunch.copy(processId = connection.process.pid(), startedUtc = connection.started.toString())
            }
            val missing = reply.roots.filter { it.className != WinUIXamlHotReloadProtocol.PREVIEW_CLASS &&
                baselines[it.className to it.resourcePath]?.markup?.hash != it.sourceHash }
            val previous = display.value
            display.value = previous.copy(message = if (missing.isEmpty()) {
                if (!previous.connected || previous.busy || previous.roots.isEmpty()) reply.message else previous.message
            } else
                "Source does not match ${missing.joinToString { it.className }}. Rebuild and restart before updating.",
                connected = reply.status == WinUIXamlHotReloadProtocol.APPLIED, busy = reply.status == WinUIXamlHotReloadProtocol.UNAVAILABLE,
                pid = connection.process.pid(), roots = reply.roots, inspectionRoots = windows.ifEmpty { visualRoots })
        }
    }

    fun apply() {
        if (disposed || project.isDisposed || update?.isActive == true || debuggerAttached()) return
        val request = generation.get()
        update = scope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (!isCurrent(request)) return@withLock
                val connection = client ?: return@withLock
                if (debuggerAttached()) return@withLock
                if (uncertain || !display.value.connected) return@withLock
                try {
                    val results = mutableListOf<WinUIXamlHotReloadValue>()
                    var updated = 0
                    for (root in display.value.roots) {
                        if (!isCurrent(request) || client !== connection) return@withLock
                        val key = root.className to root.resourcePath
                        val before = baselines[key] ?: continue
                        val after = before.copy(markup = WinUIXamlHotReloadMarkup.parse(documentText(before.path)))
                        if (after.markup.hash == root.sourceHash) continue
                        val catalog = project.service<WinUIXamlCatalogService>().forFile(before.path)
                        val patch = after.markup.patch(before.markup, root, propertyProblem = { uri, type, property ->
                            val member = catalog?.resolve(uri, type)?.let { catalog.members(it).firstOrNull { m -> m.name == property } }
                            if (member?.isEvent == true) "Changing event connections requires rebuilding and restarting." else null
                        }, contentMember = { uri, type, property ->
                            catalog?.resolve(uri, type)?.let {
                                if (property == null) catalog.contentMember(it) else catalog.propertyContent(it, property)
                            }
                        }, attachedProperty = { uri, owner, property ->
                            catalog?.resolve(uri, owner)?.let { type ->
                                catalog.attachedMembers(type).firstOrNull { it.name == property && it.isWritable }
                                    ?.let { "${it.owner}.${it.name}" }
                            }
                        })
                        val count = patch.changes.size + patch.resources.size + patch.children.size
                        if (!withCurrent(request) {
                            attempted[key] = after
                            display.value = display.value.copy(message = "Applying $count XAML updates…", busy = true)
                        }) return@withLock
                        val reply = connection.request(patch)
                        if (!isCurrent(request) || client !== connection) return@withLock
                        if (reply.status != WinUIXamlHotReloadProtocol.APPLIED) {
                            withCurrent(request) {
                                uncertain = reply.status == WinUIXamlHotReloadProtocol.UNAVAILABLE
                                display.value = display.value.copy(message = reply.message, busy = uncertain,
                                    connected = reply.status == WinUIXamlHotReloadProtocol.REJECTED, roots = reply.roots)
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
        beginRequest(WinUIXamlHotReloadState("Development connection closed. The application can continue running.", pid = owned?.process?.takeIf { it.isAlive }?.pid()))
    }

    fun inspect(request: WinUIXamlInspectionRequest) {
        synchronized(lifecycle) {
            if (debuggerAttached()) return
            val connection = client ?: return
            val epoch = generation.get()
            if (!isCurrent(epoch)) return
            // Coalesce rapid edits/selections, but keep an unrendered design document.
            val previous = pendingInspection
            pendingInspection = if (request.previewMarkup.isEmpty() && previous?.previewMarkup?.isNotEmpty() == true &&
                previous.className == request.className && previous.resourcePath == request.resourcePath) request.copy(
                    capture = request.capture || previous.capture,
                    previewMarkup = previous.previewMarkup, width = previous.width, height = previous.height, theme = previous.theme, renderScale = previous.renderScale)
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
                        val renderStarted = System.nanoTime()
                        val reply = mutex.withLock {
                            if (debuggerAttached()) null else runInterruptible { connection.inspect(next) }
                        } ?: break
                        if (client === connection) withCurrent(epoch) {
                            if (next.previewMarkup.isNotEmpty() && reply.status != WinUIXamlHotReloadProtocol.APPLIED) {
                                display.value = display.value.copy(inspection = null, inspectionError = reply.message, message = reply.message)
                                inspectionOwner = null
                                return@withCurrent
                            }
                            // Selection/capture of the native host's retained
                            // content must not revive a rejected design document.
                            if (display.value.inspectionError != null && next.previewMarkup.isEmpty()) return@withCurrent
                            val owner = Triple(next.className, next.resourcePath, next.instance)
                            val image = display.value.inspection?.image.takeIf { inspectionOwner == owner }
                            display.value = display.value.copy(inspection = reply.inspection?.let { it.copy(image = it.image ?: image) } ?: display.value.inspection,
                                message = reply.message, roots = reply.roots.ifEmpty { display.value.roots }, inspectionError = null,
                                inspectionPath = next.selectedPath,
                                renderMillis = (System.nanoTime() - renderStarted) / 1_000_000)
                            if (next.previewMarkup.isNotEmpty()) {
                                log.info("XAML preview rendered: ${display.value.renderMillis}ms")
                                if (!firstFrameReported && launchStartedNanos != 0L && reply.inspection?.image != null) {
                                    firstFrameReported = true
                                    log.info("XAML preview first frame: ${(System.nanoTime() - launchStartedNanos) / 1_000_000}ms")
                                }
                            }
                            if (reply.inspection != null) inspectionOwner = owner
                        }
                        if (next.previewMarkup.isNotEmpty() && reply.status == WinUIXamlHotReloadProtocol.APPLIED &&
                            reply.inspection?.image != null && client === connection) {
                            val pending = synchronized(lifecycle) {
                                if (isCurrent(epoch)) pendingPreviewCache.also { pendingPreviewCache = null } else null
                            }
                            pending?.let { (cache, inputs) -> runCatching {
                                if (inputs == cache.inputs()) withCurrent(epoch) { cache.remember(inputs) }
                            }.onFailure { log.debug("Preview launch cache unavailable", it) } }
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

    fun sourcePath(root: WinUIXamlHotReloadRoot): String? = sourcePath(root.className)
    fun sourcePath(className: String): String? = sources.firstOrNull { it.markup.className == className }?.path

    fun stop() {
        if (disposed || project.isDisposed) return
        val building = display.value.busy && owned == null
        val buildProcess = gradleProcess.takeIf { building }
        val request = beginRequest(WinUIXamlHotReloadState("Stopping the development application…", busy = true,
            pid = owned?.process?.takeIf { it.isAlive }?.pid()))
        if (buildProcess != null) WinRTGradleTasks.cancel(project, buildProcess)
        worker = scope.launch(Dispatchers.IO) { mutex.withLock {
            if (!isCurrent(request)) return@withLock
            runCatching { stopOwned() }.fold({ withCurrent(request) { display.value = WinUIXamlHotReloadState("Development application stopped.") } },
                { error -> withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } })
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
        directPreview?.let { process ->
            if (process.isAlive) { process.destroy(); check(process.waitFor(8, TimeUnit.SECONDS)) { "Preview host did not stop." } }
        }
        directPreview = null
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
        }.distinct().mapNotNull { path -> runCatching { Source(path, WinUIXamlHotReloadMarkup.parse(documentText(path))) }
            .getOrNull()?.takeIf { it.markup.className.isNotEmpty() } }

    private fun isCurrent(request: Long) = !disposed && !project.isDisposed && request == generation.get()

    private inline fun withCurrent(request: Long, action: () -> Unit): Boolean = synchronized(lifecycle) {
        if (!isCurrent(request)) false else { action(); true }
    }

    private fun beginRequest(next: WinUIXamlHotReloadState): Long = synchronized(lifecycle) {
        val request = generation.incrementAndGet()
        cancelRequests()
        display.value = next.copy(inspecting = false)
        request
    }

    private fun cancelRequests() {
        worker?.cancel(); edits?.cancel(); update?.cancel(); inspection?.cancel()
        pendingInspection = null
        pendingPreviewCache = null
        inspectionOwner = null
        // Closing the sockets interrupts blocking transport IO immediately.
        // Cancellation alone cannot interrupt Socket.read on Dispatchers.IO.
        client?.close(); client = null
    }

    override fun dispose() = synchronized(lifecycle) {
        disposed = true; generation.incrementAndGet(); cancelRequests()
        directPreview?.destroy()
        directPreview = null
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
class WinUIXamlHotReloadService(project: Project, scope: CoroutineScope) : WinRTDevelopmentSession(project, scope)

@Service(Service.Level.PROJECT)
@State(name = "KotlinWinRTXamlStaticPreview", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WinRTStaticPreviewService(project: Project, scope: CoroutineScope) : WinRTDevelopmentSession(project, scope, isolatedPreview = true)

/** Independent SDK host keeps a usable artboard while project code compiles. */
@Service(Service.Level.PROJECT)
@State(name = "KotlinWinRTXamlSdkPreview", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WinRTSdkPreviewService(project: Project, scope: CoroutineScope) : WinRTDevelopmentSession(project, scope, isolatedPreview = true)
