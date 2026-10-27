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
import io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch
import io.github.composefluent.winrt.ide.debug.WinRTNativeDebugOwnership
import io.github.composefluent.winrt.ide.debug.WinRTNativeDebugRestarter
import io.github.composefluent.winrt.ide.project.*
import io.github.composefluent.winrt.ide.xaml.WinUIXamlCatalogService
import io.github.composefluent.winrt.metadata.WinUIXamlDeclarations
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class WinUIXamlHotReloadState(val message: String = "Run an application with XAML Hot Reload to update its properties.",
    val connected: Boolean = false, val busy: Boolean = false, val pid: Long? = null,
    val roots: List<WinUIXamlHotReloadRoot> = emptyList(), val values: List<WinUIXamlHotReloadValue> = emptyList(),
    val inspection: WinUIXamlVisualSnapshot? = null, val inspecting: Boolean = false, val inspectionError: String? = null,
    val inspectionRoots: List<WinUIXamlHotReloadRoot> = emptyList(),
    val startupMillis: Long? = null, val renderMillis: Long? = null, val reusedBuild: Boolean = false,
    val inspectionPath: List<Int>? = null, val animationError: String? = null, val inspectionPropertiesRead: Boolean = false, val inspectionTreeRead: Boolean = false,
    val projectCodeChanged: Boolean = false, val rebuildingCode: Boolean = false,
    val inspectionDocument: WinUIXamlInspectionRequest? = null, val restartOutcome: String? = null)

/** Workspace metadata only: authentication remains in the runtime's session file. */
data class WinUIXamlHotReloadLaunchState(var moduleDirectory: String = "", var taskName: String = "", var sessionDirectory: String = "",
    var processId: Long = 0, var startedUtc: String = "", var previewTheme: String = "Default", var projectCodeChanged: Boolean = false, var preparedExecutable: String = "")

/** Instance ordinals identify live objects only within one process. They cannot
 * establish object identity after a restart; never move input between such roots. */
internal data class WinRTRestartStatePlan(
    val requests: List<WinUIXamlStateRequest>, val skippedComponents: List<String>,
)
internal fun restartStatePlan(roots: List<WinUIXamlHotReloadRoot>) = WinRTRestartStatePlan(
    roots.flatMap { root ->
        if (root.stateKeys.isEmpty()) {
            if (root.instances == 1) listOf(WinUIXamlStateRequest(root.className, root.resourcePath)) else emptyList()
        } else root.stateKeys.filter { it.isNotEmpty() }.distinct().map { key ->
            WinUIXamlStateRequest(root.className, root.resourcePath, componentKey = key)
        }
    },
    roots.filter { it.instances > 1 && (it.stateKeys.size != it.instances || it.stateKeys.any(String::isEmpty)) }
        .map { "${it.className} (${it.resourcePath})" },
)

open class WinRTDevelopmentSession(private val project: Project, private val scope: CoroutineScope,
    private val isolatedPreview: Boolean = false) : Disposable, PersistentStateComponent<WinUIXamlHotReloadLaunchState> {
    private data class Source(val path: String, val markup: WinUIXamlHotReloadMarkup)
    private val display = MutableStateFlow(if (isolatedPreview)
        WinUIXamlHotReloadState("Start the design host to render the XAML document.") else WinUIXamlHotReloadState())
    val state: StateFlow<WinUIXamlHotReloadState> = display
    val automatic = MutableStateFlow(!isolatedPreview)
    private var previewAutomatic = true
    private val automaticBuildEnabled get() = if (isolatedPreview) previewAutomatic else automatic.value
    fun setPreviewAutomatic(enabled: Boolean) {
        previewAutomatic = enabled
        if (enabled) scheduleCodeRebuild()
    }
    private val mutex = Mutex()
    @Volatile private var client: WinUIXamlHotReloadClient? = null
    private val generation = AtomicLong()
    private val lifecycle = Any()
    @Volatile private var disposed = false
    private var runProcess: io.github.composefluent.winrt.ide.run.WinRTDevelopmentProcessHandler? = null
    private var directory: Path? = null
    private var selectedModule: WinRTModuleData? = null
    @Volatile private var previewTheme = "Default"
    fun usesPreviewTheme(theme: String): Boolean = previewTheme == theme
    private var selectedLaunch: WinUIXamlHotReloadLaunchData? = null
    // Execution-only user options. Never persisted with workspace metadata.
    private var launchEnvironment: Map<String, String> = emptyMap()
    private var launchArguments = ""
    private var passParentEnvironment = true
    private var sources = emptyList<Source>()
    private val baselines = mutableMapOf<Pair<String, String>, Source>()
    private val attempted = mutableMapOf<Pair<String, String>, Source>()
    private var worker: Job? = null
    private var edits: Job? = null
    private var codeRebuild: Job? = null
    private val codeRevision = AtomicLong()
    @Volatile private var launchSourceSnapshot: Map<Path, String>? = null

    private fun kotlinSourceSnapshot(): Map<Path, String>? = runCatching {
        val modules = project.service<WinRTProjectService>().modules.value
        winRTKotlinSourceSnapshot(modules.map { Path.of(it.projectDirectory) },
            modules.flatMap { it.sourceSets }.flatMap { it.kotlinRoots }.map(Path::of)) { documentText(it.toString()) }
    }.getOrNull()
    private val designRebuildDocuments = linkedSetOf<String>()
    @Volatile private var codeBuildProcess: com.intellij.execution.process.ProcessHandler? = null
    private var update: Job? = null
    private var inspection: Job? = null
    private var pendingInspection: WinUIXamlInspectionRequest? = null
    private var inspectionOwner: Triple<String, String, Int>? = null
    private var rejectedPreviewDocument = false
    private var uncertain = false
    private var owned: WinUIXamlHotReloadClient? = null
    @Volatile private var gradleProcess: com.intellij.execution.process.ProcessHandler? = null
    private var savedLaunch = WinUIXamlHotReloadLaunchState()
    @Volatile private var debuggerPid: Long? = null
    @Volatile private var debuggerSuspended = false
    private var directPreview: Process? = null
    @Volatile private var connectedPreviewDeployment: String? = null
    @Volatile private var connectedDeploymentReady: CompletableDeferred<Unit>? = null
    private var pendingPreviewCache: Pair<WinRTPreviewLaunchCache, String>? = null
    private var launchStartedNanos = 0L
    private var firstFrameReported = false
    private var firstTreeReported = false
    @Volatile private var cachedFirstFrame: CompletableDeferred<Unit>? = null

    /** Only the full catalog yields; cached event indexes already allow document
     * preparation. Bound the wait so missing design data can never deadlock it. */
    internal suspend fun awaitCachedFirstFrame() {
        val pending = cachedFirstFrame ?: return
        if (pending.isCompleted) return
        val started = System.nanoTime()
        withTimeoutOrNull(2_000) { pending.await() }
        log.info("Full XAML catalog yielded to preview: ${(System.nanoTime() - started) / 1_000_000}ms")
    }
    private val log = com.intellij.openapi.diagnostic.Logger.getInstance(WinRTDevelopmentSession::class.java)

    /** Drain the last UI transaction before LLDB suspends the process. */
    fun suspendForDebugger(pid: Long): java.util.concurrent.CompletableFuture<Unit> {
        val result = java.util.concurrent.CompletableFuture<Unit>()
        debuggerPid = pid
        debuggerSuspended = true
        if (client?.process?.pid() != pid) { result.complete(Unit); return result }
        val drain = scope.launch(Dispatchers.IO) {
            try {
                mutex.withLock {
                    if (client?.process?.pid() == pid) display.value = display.value.copy(
                        message = "XAML updates are queued while the Native debugger is paused. Resume to apply them.", inspecting = false)
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
        debuggerSuspended = false
        resumeDebuggerWork()
    }

    fun debuggerPaused(pid: Long) {
        if (debuggerPid != pid) return
        debuggerSuspended = true
        if (client?.process?.pid() == pid) display.value = display.value.copy(
            message = "XAML updates are queued at the Native breakpoint. Resume to apply them.")
    }

    fun debuggerRunning(pid: Long) {
        if (debuggerPid != pid) return
        debuggerSuspended = false
        resumeDebuggerWork()
    }

    private fun resumeDebuggerWork() {
        if (automatic.value) apply()
        scheduleCodeRebuild()
        synchronized(lifecycle) { pendingInspection }?.let(::inspect)
    }

    private fun debuggerPaused() = debuggerSuspended && client?.process?.pid()?.let { it == debuggerPid } == true

    override fun getState(): WinUIXamlHotReloadLaunchState = synchronized(lifecycle) { savedLaunch.copy(projectCodeChanged = display.value.projectCodeChanged || savedLaunch.projectCodeChanged) }
    override fun loadState(state: WinUIXamlHotReloadLaunchState) = synchronized(lifecycle) {
        savedLaunch = state.copy()
        Unit
    }

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                markProjectCodeChanged(file.path)
                markXamlChanged(file.path)
            }
        }, this)
        project.messageBus.connect(this).subscribe(com.intellij.openapi.vfs.VirtualFileManager.VFS_CHANGES,
            object : com.intellij.openapi.vfs.newvfs.BulkFileListener {
                override fun before(events: List<com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                    events.filterNot { it.isFromSave }.forEach { markProjectCodeChanged(it.path) }
                }
                override fun after(events: List<com.intellij.openapi.vfs.newvfs.events.VFileEvent>) {
                    events.filterNot { it.isFromSave }.forEach {
                        markProjectCodeChanged(it.path)
                        markXamlChanged(it.path)
                    }
                }
            })
        scope.launch {
            project.service<WinRTProjectService>().modules.collect { modules ->
                if (selectedModule != null && modules.none { it.projectDirectory == selectedModule?.projectDirectory }) disconnect()
            }
        }
    }

    /** Dictionaries and unloaded components have no live root to patch. Keep their
     * compiled resources current through the same build-before-restart boundary. */
    internal fun markXamlChanged(path: String) {
        if (disposed || !path.endsWith(".xaml", true) || !display.value.connected) return
        val file = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull() ?: return
        val roots = project.service<WinRTProjectService>().modules.value
            .flatMap { it.xamlCompilations }.flatMap { it.sourceRoots }
        if (roots.none { file.startsWith(Path.of(it).toAbsolutePath().normalize()) }) return
        if (codeRebuild?.isActive == true) codeRevision.incrementAndGet()
        val source = sources.singleOrNull { Path.of(it.path).toAbsolutePath().normalize() == file }
        val loaded = source != null && Files.exists(file) &&
            display.value.inspectionRoots.any { it.className == source.markup.className }
        if (isolatedPreview && source?.markup?.isResourceDictionary == true && runCatching {
            WinUIXamlHotReloadMarkup.parse(documentText(source.path)).containsCompiledBindings()
        }.getOrDefault(false)) {
            requestAutomaticRebuild("Compiled resource bindings changed; rebuilding the preview in the background.")
        } else if (!loaded && !isolatedPreview) {
            display.value = display.value.copy(projectCodeChanged = true,
                message = "XAML resources changed; a build is needed to update compiled resources.")
            savedLaunch = savedLaunch.copy(projectCodeChanged = true)
            codeRevision.incrementAndGet()
            scheduleCodeRebuild()
        } else if (automatic.value && client != null) {
            edits?.cancel()
            edits = scope.launch { delay(450); apply() }
        }
    }

    /** Both the designer and live application run compiled Kotlin. XAML updates cannot
     * certify a changed binary. Automatic restarts build first and preserve compatible control state. */
    internal fun markProjectCodeChanged(path: String) {
        if (disposed || !path.endsWith(".kt", true)) return
        synchronized(lifecycle) {
            val module = selectedModule ?: return
            val launch = selectedLaunch ?: return
            if (launch == module.staticPreview?.launch() ||
                (!display.value.connected && !display.value.busy)) return
            val file = runCatching { Path.of(path).toAbsolutePath().normalize() }.getOrNull() ?: return
            val modules = project.service<WinRTProjectService>().modules.value
            val generated = modules.flatMap { it.sourceSets }.flatMap { it.generatedKotlinRoots.orEmpty() }
            if (generated.any { file.startsWith(Path.of(it).toAbsolutePath().normalize()) }) return
            if (isWinRTUserKotlinSource(file, modules.map { Path.of(it.projectDirectory) },
                    modules.flatMap { it.sourceSets }.flatMap { it.kotlinRoots }.map(Path::of))) {
                val unchanged = launchSourceSnapshot?.get(file)?.let { hash ->
                    runCatching { winRTKotlinSourceHash(documentText(file.toString())) == hash }.getOrDefault(false)
                } == true
                // Reverting an edit during compilation is still a new revision:
                // the in-flight payload may already contain the intermediate text.
                if (unchanged && codeRebuild?.isActive != true) return
                designRebuildDocuments.clear()
                display.value = display.value.copy(projectCodeChanged = true)
                savedLaunch = savedLaunch.copy(projectCodeChanged = true)
                codeRevision.incrementAndGet()
                scheduleCodeRebuild()
            }
        }
    }

    private fun requestAutomaticRebuild(reason: String): Boolean {
        if (!automaticBuildEnabled || selectedLaunch?.developmentBuildTask.isNullOrBlank() || selectedLaunch == selectedModule?.staticPreview?.launch()) return false
        display.value = display.value.copy(projectCodeChanged = true, message = reason, busy = false)
        savedLaunch = savedLaunch.copy(projectCodeChanged = true)
        codeRevision.incrementAndGet()
        scheduleCodeRebuild()
        return true
    }

    /** Compile into staging while the old deployment remains alive. Failed builds
     * never reach start(), whose restart boundary owns stopping the old process. */
    fun dismissRestartOutcome() {
        synchronized(lifecycle) { display.value = display.value.copy(restartOutcome = null) }
    }

    fun setAutomatic(enabled: Boolean) {
        automatic.value = enabled
        if (enabled) { apply(); scheduleCodeRebuild() }
    }

    fun rebuild(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData) {
        if (display.value.connected && usesLaunch(module, launch) && !launch.developmentBuildTask.isNullOrBlank()) {
            if (codeRebuild?.isActive == true) return
            display.value = display.value.copy(projectCodeChanged = true)
            codeRevision.incrementAndGet()
            scheduleCodeRebuild(force = true)
        } else start(module, launch, restart = true)
    }

    private fun scheduleCodeRebuild(force: Boolean = false) {
        if ((!automaticBuildEnabled && !force) || disposed ||
            !display.value.projectCodeChanged || !display.value.connected || codeRebuild?.isActive == true) return
        val module = selectedModule ?: return
        val launch = selectedLaunch ?: return
        val buildTask = launch.developmentBuildTask?.takeIf { it.isNotBlank() } ?: return
        val request = generation.get()
        display.value = display.value.copy(message = "Code changes queued; waiting for edits to settle…",
            rebuildingCode = true, restartOutcome = null)
        codeRebuild = scope.launch(Dispatchers.IO) {
            var lastAttempted = codeRevision.get()
            var pendingDeployment: WinRTPreparedDeployment? = null
            try {
                var builtRevision: Long
                var preparedLaunch: WinRTPreparedLaunch? = null
                // Only direct unpackaged Native tasks expose this preparation contract.
                // Other packaging modes retain their activation/registration owner.
                val prepareDirect = launch.isDirectPreview && launch.nativeCompilerHome.isNotBlank() && launch.taskName.startsWith("runWinAppNative")
                do {
                    delay(900)
                    if (!isCurrent(request) || (!automaticBuildEnabled && !force)) return@launch
                    withContext(Dispatchers.EDT) { writeIntentReadAction { FileDocumentManager.getInstance().saveAllDocuments() } }
                    builtRevision = codeRevision.get()
                    lastAttempted = builtRevision
                    val finished = CompletableDeferred<Boolean>()
                    val slot = if (prepareDirect) UUID.randomUUID().toString() else null
                    val baseExecutable = Path.of(if (isolatedPreview) launch.previewExecutable else launch.executable)
                    pendingDeployment?.abandon()
                    pendingDeployment = null
                    preparedLaunch = null
                    val preparedExecutable = slot?.let { WinRTPreparedLaunch.deployment(baseExecutable.parent, it).resolve(baseExecutable.fileName) }
                    val preparation = preparedExecutable?.let { WinRTPreparedDeployment(baseExecutable, it) }
                    pendingDeployment = preparation
                    withCurrent(request) { display.value = display.value.copy(
                        message = "Building code changes; the current application stays running…", rebuildingCode = true, restartOutcome = null) }
                    withContext(Dispatchers.EDT) {
                        if (isCurrent(request)) WinRTGradleTasks.run(project, module, listOf(if (prepareDirect) launch.taskName else buildTask), "Build Hot Reload changes",
                            environment = launchEnvironment + mapOf("KOTLIN_WINRT_BUILD_REQUEST" to UUID.randomUUID().toString()) +
                                (if (slot != null) mapOf(WinRTPreparedLaunch.SLOT_ENVIRONMENT to slot, "KOTLIN_WINRT_REGISTER_ONLY" to "1") else emptyMap()) +
                                (if (isolatedPreview) mapOf(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT to "1", WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT to previewTheme) else emptyMap()),
                            arguments = if (prepareDirect) launchArguments else "",
                            passParentEnvironment = passParentEnvironment,
                            onProcessStarted = { handler ->
                                if (!withCurrent(request) { codeBuildProcess = handler }) WinRTGradleTasks.cancel(project, handler)
                            }, onFinished = { succeeded ->
                                runCatching { preparation?.buildFinished() }.onFailure { log.warn("Unused Native deployment cleanup failed", it) }
                                finished.complete(succeeded)
                            })
                        else { preparation?.buildFinished(); finished.cancel() }
                    }
                    val succeeded = finished.await()
                    codeBuildProcess = null
                    preparedLaunch = if (succeeded && preparedExecutable != null) {
                        val descriptor = preparedExecutable.parent.resolve(WinRTPreparedLaunch.FILE_NAME)
                        WinRTPreparedLaunch.read(descriptor, preparedExecutable)
                    } else null
                    if (!succeeded) {
                        withCurrent(request) { display.value = display.value.copy(
                            message = "Code build failed; the existing application was not replaced. See Gradle output.") }
                        if (builtRevision == codeRevision.get()) return@launch
                    }
                    while (succeeded && debuggerPaused() && isCurrent(request)) {
                        withCurrent(request) { display.value = display.value.copy(message = "Code build complete; resume the Native debugger to restore state and restart.") }
                        delay(150)
                    }
                } while (builtRevision != codeRevision.get())
                if (!isCurrent(request) || (!automaticBuildEnabled && !force) || debuggerPaused()) return@launch
                mutex.withLock {
                    val connection = client ?: return@launch
                    // A designer re-renders its source after replacement; it must not
                    // replay the synthetic artboard's control state into application roots.
                    withCurrent(request) { display.value = display.value.copy(message = "Build complete; saving current UI state…") }
                    val statePlan = captureWinRTRestartState({ connection.request().roots }, connection::transferState)
                    val states = statePlan.requests.filter { !it.restore.isNullOrEmpty() }
                    val debugRestart = WinRTNativeDebugOwnership.restarter(connection.process.pid(), connection.started)
                    debugRestart?.detachForRestart()?.let { future -> runInterruptible { future.get(25, TimeUnit.SECONDS) } }
                    withContext(Dispatchers.EDT) {
                        if (isCurrent(request) && builtRevision == codeRevision.get() && (automaticBuildEnabled || force) && !debuggerPaused()) {
                            // start cancels outstanding jobs; relinquish this completed build first.
                            codeRebuild = null
                            val accepted = start(module, launch, restart = true, preview = isolatedPreview,
                                theme = previewTheme, restoredState = states, skippedStateComponents = statePlan.skippedComponents,
                                launchEnvironment = launchEnvironment, launchArguments = launchArguments, passParentEnvironment = passParentEnvironment,
                                debugRestart = debugRestart, preparedLaunch = preparedLaunch)
                            if (accepted) pendingDeployment?.claim()
                        } else debugRestart?.restartFailed(IllegalStateException("Sources changed while preparing the Native restart."))
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = display.value.copy(
                message = "Automatic restart failed: ${error.message}. See Gradle output and the connection status.") } }
            finally {
                runCatching { pendingDeployment?.abandon() }.onFailure { log.warn("Unused Native deployment cleanup failed", it) }
                if (isCurrent(request)) {
                    codeRebuild = null
                    display.value = display.value.copy(rebuildingCode = false)
                    if (codeRevision.get() != lastAttempted) scheduleCodeRebuild(force)
                }
            }
        }
    }

    private suspend fun restoreAfterRestart(request: Long, states: List<WinUIXamlStateRequest>, skippedComponents: List<String>) {
        try {
            val pending = states.sortedBy { it.componentKey.count { char -> char == '/' } }.toMutableList()
            val skippedValues = mutableListOf<String>()
            val skipped = skippedComponents.toMutableSet()
            withTimeout(15_000) {
                while (pending.isNotEmpty() && isCurrent(request)) {
                    mutex.withLock {
                        val connection = client ?: error("The restarted application disconnected.")
                        val roots = connection.request().roots
                        val matchingRoots = roots.filter { root -> pending.any {
                            it.className == root.className && it.resourcePath == root.resourcePath
                        } }
                        val statePlan = restartStatePlan(matchingRoots)
                        // Repeated children can finish loading before their parent's
                        // connector supplies their names. Keep waiting for keyed
                        // requests; a transient missing key is not a skipped restore.
                        pending.removeAll { saved ->
                            val ambiguous = matchingRoots.firstOrNull {
                                saved.componentKey.isEmpty() && it.instances > 1 && it.className == saved.className && it.resourcePath == saved.resourcePath
                            }
                            if (ambiguous != null) skipped += "${ambiguous.className} (${ambiguous.resourcePath})"
                            ambiguous != null
                        }
                        // Restoring a Frame can unload/recreate another pending root.
                        // Re-read identities after every component instead of consuming
                        // a ready list captured before navigation changed the tree.
                        val ready = pending.firstOrNull { saved -> statePlan.requests.any {
                            it.className == saved.className && it.resourcePath == saved.resourcePath && it.componentKey == saved.componentKey
                        } }
                        ready?.let { saved ->
                            val reply = connection.transferState(saved)
                            // Loaded/Unloaded can race the server's identity lookup;
                            // a transient unavailable root is retried within this deadline.
                            if (reply.status != WinUIXamlHotReloadProtocol.UNAVAILABLE) {
                                check(reply.status == WinUIXamlHotReloadProtocol.APPLIED) { reply.message }
                                if (Regex("; [1-9][0-9]* missing or changed controls skipped").containsMatchIn(reply.message))
                                    skippedValues += "${saved.className}: ${reply.message}"
                                pending.remove(saved)
                            }
                        }
                    }
                    if (pending.isNotEmpty()) delay(150)
                }
            }
            withCurrent(request) {
                val outcome = if (skippedValues.isNotEmpty()) "Code changes loaded; some saved state was skipped: ${skippedValues.joinToString()}."
                else if (skipped.isEmpty()) "Code changes loaded; saved UI and application checkpoints restored."
                else "Code changes loaded; state skipped for components without a unique source identity: ${skipped.joinToString()}. Other saved control state restored."
                display.value = display.value.copy(message = outcome, restartOutcome = outcome)
            }
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            withCurrent(request) {
                val outcome = "Code changes loaded, but some control state could not be restored: ${error.message}"
                display.value = display.value.copy(message = outcome, restartOutcome = outcome)
            }
        }
    }

    /** Native Gradle owns build, launch, logs and cancellation; Compose only requests the operation. */
    internal fun start(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, restart: Boolean = false, preview: Boolean = false, rebuild: Boolean = restart, theme: String = "Default",
        restoredState: List<WinUIXamlStateRequest> = emptyList(),
        skippedStateComponents: List<String> = emptyList(),
        launchEnvironment: Map<String, String> = emptyMap(), launchArguments: String = "", passParentEnvironment: Boolean = true, debugRestart: WinRTNativeDebugRestarter? = null, preparedLaunch: WinRTPreparedLaunch? = null): Boolean {
        if (disposed || project.isDisposed) return false
        require(theme in setOf("Default", "Light", "Dark"))
        val sdkPreview = preview && launch == module.staticPreview?.launch()
        val baseExecutable = Path.of(if (preview) launch.previewExecutable else launch.executable)
        require(preparedLaunch == null || restart && launch.isDirectPreview && launch.taskName.startsWith("runWinAppNative") &&
            WinRTPreparedLaunch.isReplacement(baseExecutable, Path.of(preparedLaunch.executable))) { "The prepared launch does not belong to this deployment." }
        val startedAt = System.nanoTime()
        val launchCodeRevision = codeRevision.get()
        require(launch in module.hotReloadLaunches || sdkPreview) { "Synchronize Gradle before selecting this application." }
        if (display.value.busy) return false
        val themeOnly = preview && !rebuild && display.value.connected && usesLaunch(module, launch) && !usesPreviewTheme(theme)
        val retainedCodeChange = themeOnly && display.value.projectCodeChanged
        val priorDeployment = connectedPreviewDeployment.takeIf { themeOnly }
        connectedPreviewDeployment = null
        val deploymentReady = CompletableDeferred<Unit>()
        connectedDeploymentReady = deploymentReady
        if (!restart) runProcess = null
        if (!isolatedPreview && restart) runProcess?.prepareRestart()
        val request = beginRequest(WinUIXamlHotReloadState("Preparing the development launch…", busy = true,
            projectCodeChanged = retainedCodeChange))
        launchSourceSnapshot = null
        this.launchEnvironment = launchEnvironment.toMap()
        this.launchArguments = launchArguments
        this.passParentEnvironment = passParentEnvironment
        previewTheme = theme
        launchStartedNanos = startedAt; firstFrameReported = false; firstTreeReported = false
        val firstFrame = CompletableDeferred<Unit>()
        // Publish before scheduling IO: catalog loading must not race the cache
        // decision. A cache miss completes this gate before any Gradle build.
        if (preview && !sdkPreview && !rebuild) cachedFirstFrame = firstFrame
        gradleProcess = null
        worker = scope.launch(Dispatchers.IO) {
            var warmingPreview: Process? = null
            var rollbackLaunch: WinRTPreparedLaunch? = null
            var replacementProcess: Process? = null
            var rolledBack = false
            var activeExecutable = preparedLaunch?.executable ?: launch.executable
            fun discardWarmingPreview() {
                warmingPreview?.let { process ->
                    warmingPreview = null
                    synchronized(lifecycle) { if (directPreview === process) directPreview = null }
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
                }
            }
            try {
                // Compose effects and clicks run on EDT without the platform's
                // write-intent lock. Only app-derived launches need saved files;
                // the SDK designer renders the editor's unsaved XAML directly.
                // FileDocumentManager keeps its unsaved set concurrent. A clean
                // reopened designer needs no EDT save/format queue before hashing.
                if (!sdkPreview && (!preview || FileDocumentManager.getInstance().unsavedDocuments.isNotEmpty())) withContext(Dispatchers.EDT) {
                    writeIntentReadAction {
                        if (isCurrent(request)) FileDocumentManager.getInstance().saveAllDocuments()
                    }
                }
                val sourceSnapshot = if (sdkPreview) null else kotlinSourceSnapshot()
                withCurrent(request) { launchSourceSnapshot = sourceSnapshot }
                mutex.withLock {
                    if (!isCurrent(request)) return@launch
                    if (restart) {
                        if (preparedLaunch != null) {
                            val previous = requireNotNull(owned) { "The previous Native session is unavailable." }
                            val executable = Path.of(requireNotNull(previous.process.info().command().orElse(null)))
                            val descriptor = executable.parent.resolve(WinRTPreparedLaunch.FILE_NAME)
                            rollbackLaunch = if (Files.isRegularFile(descriptor)) WinRTPreparedLaunch.read(descriptor, executable)
                            else if (preview && directPreview != null) WinRTPreparedLaunch(executable.toString(),
                                listOf(WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT), emptyMap())
                            else error("The running application has no saved launch contract. It remains running; restart it once to enable automatic rollback.")
                        }
                        stopOwned(retainDeployment = rollbackLaunch != null)
                    } else {
                        check(owned?.process?.isAlive != true) { "Stop or restart the existing development application first." }
                        stopOwned()
                    }
                    if (!themeOnly) display.value = display.value.copy(projectCodeChanged = false)
                    sources = emptyList()
                    baselines.clear(); attempted.clear(); uncertain = false
                    selectedModule = module; selectedLaunch = launch
                    directory = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}").toAbsolutePath().normalize()
                    Files.createDirectories(directory!!)
                    withCurrent(request) { savedLaunch = WinUIXamlHotReloadLaunchState(module.projectDirectory, launch.taskName, directory.toString(), previewTheme = theme, preparedExecutable = preparedLaunch?.executable.orEmpty()) }
                }
                val launchDirectory = requireNotNull(directory)
                fun startCachedPreview(cache: WinRTPreviewLaunchCache): Process =
                    ProcessBuilder(launch.previewExecutable, WinUIXamlHotReloadProtocol.PREVIEW_ARGUMENT)
                        .directory(cache.deployment.toFile())
                        .redirectErrorStream(true).redirectOutput(launchDirectory.resolve("preview-host.log").toFile())
                        .apply { environment()[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY] = launchDirectory.toString()
                            environment()[WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT] = theme
                            environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1" }.start()
                // Source parsing and cache content validation have no data dependency.
                // Both must finish before a host can connect or inspect source mappings.
                val sourcePreparation = async(Dispatchers.IO) {
                    loadSources().also {
                        if (preview) log.info("XAML preview sources ready: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                    }
                }
                val cache = if (preparedLaunch == null && preview && (sdkPreview && launch.taskName == "runWinRTXamlSdkPreview" || launch.nativeCompilerHome.isNotBlank() && launch.isDirectPreview)) WinRTPreviewLaunchCache(
                    Path.of(project.service<WinRTProjectService>().buildRootFor(module) ?: module.projectDirectory),
                    project.service<WinRTProjectService>().modules.value, module, launch) else null
                val validation = cache?.let { candidate -> runCatching {
                    if (priorDeployment != null && candidate.connectedDeploymentIdentity() == priorDeployment) null to true
                    else if (rebuild) candidate.inputs() to false else candidate.checkedInputs(onCandidateReady = if (sdkPreview) null else {
                        {
                            if (isCurrent(request)) runCatching { startCachedPreview(candidate) }.onSuccess { process ->
                                warmingPreview = process
                                // Disposal must own this child immediately, even
                                // before validation permits a client connection.
                                if (withCurrent(request) { directPreview = process })
                                    log.info("XAML preview host warming: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                                else process.destroy()
                            }.onFailure { log.debug("Preview candidate could not initialize early", it) }
                        }
                    })
                }.getOrNull() }
                val inputs = validation?.first
                // The SDK designer stages project assets after the host connects.
                // Record its deployment only after that document actually renders.
                if (sdkPreview && cache != null && inputs != null) withCurrent(request) {
                    pendingPreviewCache = cache to inputs
                }
                val reusable = validation?.second == true
                if (!reusable) { discardWarmingPreview(); firstFrame.complete(Unit) }
                if (preview && reusable) log.info("XAML preview cache validated: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                val preparedSources = sourcePreparation.await()
                mutex.withLock {
                    if (!isCurrent(request)) return@launch
                    sources = preparedSources
                }
                var reused = false
                suspend fun launchPrepared(spec: WinRTPreparedLaunch) {
                    val process = ProcessBuilder(listOf(spec.executable) + spec.arguments)
                        .directory(Path.of(spec.executable).parent.toFile())
                        .redirectErrorStream(true).redirectOutput(launchDirectory.resolve(if (rolledBack) "rollback-host.log" else "application-host.log").toFile())
                        .apply {
                            if (!passParentEnvironment) environment().clear()
                            environment().putAll(launchEnvironment)
                            environment().putAll(spec.environment)
                            environment()[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY] = launchDirectory.toString()
                            if (preview) {
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1"
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT] = theme
                            }
                        }.start()
                    if (spec === preparedLaunch) replacementProcess = process
                    if (!withCurrent(request) { if (preview) directPreview = process else warmingPreview = process }) {
                        process.destroy(); currentCoroutineContext().ensureActive(); error("The development launch was superseded.")
                    }
                    awaitConnection(request, launchDirectory, spec.executable, process)
                    warmingPreview = null
                }
                if (preparedLaunch != null) {
                    try {
                        launchPrepared(preparedLaunch)
                        log.info("Prepared Native replacement connected: ${(System.nanoTime() - startedAt) / 1_000_000}ms")
                    } catch (error: Exception) {
                        if (error is CancellationException && error !is TimeoutCancellationException) throw error
                        currentCoroutineContext().ensureActive()
                        val previous = rollbackLaunch ?: throw error
                        withCurrent(request) { display.value = display.value.copy(message = "Replacement failed to start; restoring the previous application…", busy = true) }
                        discardWarmingPreview()
                        mutex.withLock { stopOwned(); client?.close(); client = null }
                        runCatching { WinRTPreparedLaunch.discard(baseExecutable, Path.of(preparedLaunch.executable)) }
                            .onFailure { log.debug("Failed Native deployment cleanup deferred", it) }
                        rolledBack = true
                        activeExecutable = previous.executable
                        withCurrent(request) { savedLaunch.preparedExecutable = previous.executable.takeIf {
                            WinRTPreparedLaunch.isReplacement(baseExecutable, Path.of(it)) }.orEmpty() }
                        try { launchPrepared(previous) }
                        catch (recovery: Exception) {
                            if (recovery is CancellationException && recovery !is TimeoutCancellationException) throw recovery
                            error.addSuppressed(recovery)
                            throw IllegalStateException("The replacement and automatic rollback both failed. See application-host.log and rollback-host.log in $launchDirectory.", error)
                        }
                    }
                    reused = true
                }
                if (reusable && cache != null) {
                    try {
                        // Only now can the fully checked candidate enter a session.
                        val process = warmingPreview?.also { warmingPreview = null } ?: startCachedPreview(cache)
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
                        firstFrame.complete(Unit)
                        cache.invalidate()
                        // TimeoutCancellationException is coroutine control flow; IntelliJ
                        // rejects logging it as a Throwable. Preserve the reason without a stack.
                        log.info("Cached preview launch failed for ${launch.taskName}: ${error.javaClass.simpleName}: ${error.message}; rebuilding through Gradle")
                    }
                }
                if (!reused) {
                    val buildInputs = if (!sdkPreview) cache?.buildInputs() else null
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
                            environment = launchEnvironment + mapOf(WinUIXamlHotReloadProtocol.SESSION_DIRECTORY to directory!!.toString()) +
                                (if (!preview && !launch.developmentBuildTask.isNullOrBlank()) mapOf("KOTLIN_WINRT_RELEASE_BUILD_LOCK" to "1") else emptyMap()) +
                                (if (preview) mapOf(WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT to "1",
                                    WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT to theme) else emptyMap()) +
                                if (detachedNative) mapOf("KOTLIN_WINRT_REGISTER_ONLY" to "1") else emptyMap(),
                            arguments = if (detachedNative) "--no-launch" else launchArguments,
                            passParentEnvironment = passParentEnvironment,
                            onProcessStarted = { handler ->
                                if (!withCurrent(request) { gradleProcess = handler }) WinRTGradleTasks.cancel(project, handler)
                            },
                            onSuccess = { buildFinished.complete(Unit) },
                            onFailure = { withCurrent(request) { if (client == null) {
                                worker?.cancel(); runProcess?.complete(1); display.value = WinUIXamlHotReloadState("The Gradle launch failed or stopped. See its Run output.")
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
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT] = theme
                                environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1" }.start()
                        if (!withCurrent(request) { directPreview = process }) { process.destroy(); return@launch }
                        awaitConnection(request, directory!!, executable, process)
                    } else awaitConnection(request, directory!!, if (preview) launch.previewExecutable else launch.executable,
                        building = { !buildFinished.isCompleted && gradleProcess?.isProcessTerminated != true })
                    // Never bless inputs edited while Gradle was compiling an earlier version.
                    if (!sdkPreview && inputs != null && cache != null && isCurrent(request)) runCatching {
                        if (display.value.connected && buildInputs != null) cache.rememberAfterBuild(buildInputs)
                    }.onFailure { log.debug("Preview launch cache unavailable", it) }
                }
                if (preview && cache != null) {
                    val identity = (if (reused) cache.validatedDeploymentIdentity else null) ?: cache.connectedDeploymentIdentity()
                    withCurrent(request) { connectedPreviewDeployment = identity }
                }
                deploymentReady.complete(Unit)
                val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                withCurrent(request) { display.value = display.value.copy(startupMillis = elapsed, reusedBuild = reused) }
                if (preview) log.info("XAML preview connected: ${elapsed}ms, reusedBuild=$reused")
                // A first project build also creates metadata manifests outside the VFS cache.
                if (preview && !reused && isCurrent(request)) {
                    project.service<WinUIXamlCatalogService>().refresh()
                    project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceChanges>().revision.value += 1
                }
                if (restoredState.isNotEmpty() || skippedStateComponents.isNotEmpty()) restoreAfterRestart(request, restoredState, skippedStateComponents)
                debugRestart?.let { debugger ->
                    try {
                        val connection = requireNotNull(client)
                        runInterruptible { debugger.attachAfterRestart(connection.process.pid(), connection.started, Path.of(activeExecutable)).get(45, TimeUnit.SECONDS) }
                    } catch (error: Exception) {
                        debugger.restartFailed(error)
                        withCurrent(request) { display.value = display.value.copy(message = "Application restarted, but the Native debugger could not reattach: ${error.message}") }
                    }
                }
                if (rolledBack) {
                    withCurrent(request) {
                        val outcome = "Replacement failed to start; the previous application was restored. " + (display.value.restartOutcome?.replace("Code changes loaded", "Saved state recovery") ?: "No saved state was required.")
                        display.value = display.value.copy(message = outcome, restartOutcome = outcome, projectCodeChanged = true)
                    }
                } else {
                    rollbackLaunch?.let { previous ->
                        val executable = Path.of(previous.executable)
                        if (WinRTPreparedLaunch.isReplacement(baseExecutable, executable))
                            runCatching { WinRTPreparedLaunch.discard(baseExecutable, executable) }
                                .onFailure { log.debug("Retired Native deployment cleanup deferred", it) }
                    }
                }
                if (!rolledBack && codeRevision.get() != launchCodeRevision) {
                    val changed = restart || sourceSnapshot == null || sourceSnapshot != kotlinSourceSnapshot()
                    withCurrent(request) {
                        display.value = display.value.copy(projectCodeChanged = changed)
                        savedLaunch = savedLaunch.copy(projectCodeChanged = changed)
                    }
                    if (changed) scheduleCodeRebuild()
                }
                watch(request)
            } catch (error: TimeoutCancellationException) {
                debugRestart?.restartFailed(error)
                withCurrent(request) { display.value = WinUIXamlHotReloadState("The application did not publish a development session in time. See Gradle Run output."); runProcess?.complete(1) }
            } catch (error: CancellationException) { debugRestart?.restartFailed(error); throw error }
            catch (error: Exception) { debugRestart?.restartFailed(error); withCurrent(request) {
                val surviving = owned?.process?.takeIf { it.isAlive }
                if (surviving != null) runProcess?.bindProcess(surviving) else runProcess?.complete(1)
                display.value = WinUIXamlHotReloadState(error.message.orEmpty(), pid = surviving?.pid())
            } }
            finally {
                // Cancellation, validation and source-preparation failures must
                // not leave a candidate outside normal session ownership.
                discardWarmingPreview()
                if (preparedLaunch != null && replacementProcess?.isAlive != true &&
                    owned?.process?.info()?.command()?.orElse(null) != preparedLaunch.executable) {
                    runCatching { WinRTPreparedLaunch.discard(baseExecutable, Path.of(preparedLaunch.executable)) }
                        .onFailure { log.debug("Unused Native deployment cleanup deferred", it) }
                }
                deploymentReady.complete(Unit)
                if (!display.value.connected || !isCurrent(request)) firstFrame.complete(Unit)
            }
        }
        return true
    }

    /** Observe an application already being launched by the IDE's native
     * runner. This must not schedule another Gradle execution or stop an
     * application from an earlier Run window. */
    fun attach(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, sessionDirectory: Path, building: () -> Boolean = { false },
        runProcess: io.github.composefluent.winrt.ide.run.WinRTDevelopmentProcessHandler? = null,
        launchEnvironment: Map<String, String> = emptyMap(), launchArguments: String = "", passParentEnvironment: Boolean = true) {
        if (disposed || project.isDisposed) return
        require(launch in module.hotReloadLaunches) { "Synchronize Gradle before selecting this application." }
        this.launchEnvironment = launchEnvironment.toMap()
        this.launchArguments = launchArguments
        this.passParentEnvironment = passParentEnvironment
        val folder = sessionDirectory.toAbsolutePath().normalize()
        val expected = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload").toAbsolutePath().normalize()
        require(folder.parent == expected && runCatching { UUID.fromString(folder.fileName.toString()) }.isSuccess) {
            "The development session does not belong to this module."
        }
        this.runProcess = runProcess
        runProcess?.stopSession = {
            if (this.runProcess === runProcess) stop() else runProcess.stopApplication()
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
                withCurrent(request) { display.value = WinUIXamlHotReloadState("The application did not publish a development session in time. See Gradle Run output."); runProcess?.complete(1) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty()); runProcess?.complete(1) } }
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
            check(process == null || process.isAlive) {
                val output = folder.resolve("application-host.log").takeIf { Files.isRegularFile(it) } ?: folder.resolve("preview-host.log")
                "The ${if (isolatedPreview) "preview host" else "application"} exited before connecting. See $output."
            }
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
                runProcess?.bindProcess(candidate.process)
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

    fun usesLaunch(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData): Boolean =
        selectedModule == module && selectedLaunch == launch

    /** Switching applications must not render against another application's host.
     * The editor coroutine owns this wait, so leaving the tab cancels the request. */
    suspend fun ensurePreviewReady(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, retry: Boolean = false, theme: String = "Default") {
        if (display.value.busy && (!usesLaunch(module, launch) || !usesPreviewTheme(theme))) state.first { !it.busy }
        if (display.value.connected && !usesPreviewTheme(theme)) connectedDeploymentReady?.await()
        currentCoroutineContext().ensureActive()
        ensurePreview(module, launch, retry, theme)
    }

    /** Opening/switching a preview reuses its host; a failed launch needs an explicit retry. */
    fun ensurePreview(module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData, retry: Boolean = false, theme: String = "Default") {
        if (display.value.busy || (usesLaunch(module, launch) && usesPreviewTheme(theme) && (display.value.connected || !retry))) return
        start(module, launch, restart = display.value.pid != null, preview = true, rebuild = false, theme = theme)
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
                    val base = Path.of(if (isolatedPreview) launch.previewExecutable else launch.executable)
                    val executable = saved.preparedExecutable.takeIf { it.isNotEmpty() }?.also {
                        require(WinRTPreparedLaunch.isReplacement(base, Path.of(it))) { "The saved replacement does not belong to this launch." }
                    } ?: base.toString()
                    val next = WinUIXamlHotReloadClient.discover(folder, executable)
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
                        directory = folder; selectedModule = module; selectedLaunch = launch; previewTheme = saved.previewTheme
                        client = candidate; owned = candidate
                        display.value = display.value.copy(projectCodeChanged = display.value.projectCodeChanged || saved.projectCodeChanged)
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
        if (debuggerPaused()) return
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
                pid = connection.process.pid(), roots = reply.roots, inspectionRoots = if (isolatedPreview) visualRoots.filter { it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS } else windows.ifEmpty { visualRoots })
        }
    }

    fun apply() {
        // The static designer replaces its loose design document and reconnects
        // generated bindings. Its wrapper is not the compiled page's patch graph.
        if (isolatedPreview || disposed || project.isDisposed || update?.isActive == true || debuggerPaused()) return
        val request = generation.get()
        update = scope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (!isCurrent(request)) return@withLock
                val connection = client ?: return@withLock
                if (debuggerPaused()) return@withLock
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
                        val patch = try { after.markup.patch(before.markup, root, propertyProblem = { uri, type, property ->
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
                        }, isEvent = { uri, type, name ->
                            catalog?.resolve(uri, type)?.let { catalog.members(it).any { member -> member.name == name && member.isEvent } } == true
                        })
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            if (requestAutomaticRebuild("XAML change requires rebuilding: ${error.message}")) return@withLock
                            throw error
                        }
                        val count = patch.changes.size + patch.resources.size + patch.children.size + patch.events.size
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
        beginRequest(WinUIXamlHotReloadState("Development connection closed. The application can continue running.", pid = owned?.process?.takeIf { it.isAlive }?.pid(), projectCodeChanged = display.value.projectCodeChanged))
    }

    fun inspect(request: WinUIXamlInspectionRequest) {
        synchronized(lifecycle) {
            if (debuggerPaused()) { pendingInspection = request; return }
            val connection = client ?: return
            val epoch = generation.get()
            if (!isCurrent(epoch)) return
            // Coalesce rapid edits/selections, but keep an unrendered design document.
            val previous = pendingInspection
            pendingInspection = if (request.previewMarkup.isEmpty() && previous != null && (previous.previewMarkup.isNotEmpty() || previous.animation != null || previous.readTree && !request.readTree) &&
                previous.className == request.className && previous.resourcePath == request.resourcePath && previous.instance == request.instance) request.copy(
                    capture = request.capture || previous.capture, readTree = request.readTree || previous.readTree,
                    previewMarkup = previous.previewMarkup, width = previous.width, height = previous.height, theme = previous.theme, renderScale = previous.renderScale, captureBounds = previous.captureBounds, animation = request.animation ?: previous.animation)
                else request
            if (display.value.inspecting) return
            display.value = display.value.copy(inspecting = true)
            inspection = scope.launch(Dispatchers.IO) {
                var activeRequest: WinUIXamlInspectionRequest? = null
                try {
                    while (isCurrent(epoch)) {
                        val next = synchronized(lifecycle) {
                            pendingInspection.also {
                                pendingInspection = null
                                if (it == null) { inspection = null; display.value = display.value.copy(inspecting = false) }
                            }
                        } ?: break
                        activeRequest = next
                        val renderStarted = System.nanoTime()
                        val reply = mutex.withLock {
                            if (debuggerPaused()) { synchronized(lifecycle) { pendingInspection = next }; null } else runInterruptible { connection.inspect(next) }
                        } ?: break
                        if (client === connection) withCurrent(epoch) {
                            if (next.previewMarkup.isNotEmpty() && reply.status == WinUIXamlHotReloadProtocol.RESTART_REQUIRED &&
                                designRebuildDocuments.add(WinUIXamlDeclarations.sourceFingerprint(next.previewMarkup))) {
                                if (designRebuildDocuments.size > 64) designRebuildDocuments.remove(designRebuildDocuments.first())
                                requestAutomaticRebuild(reply.message)
                            }
                            if (next.previewMarkup.isNotEmpty() && reply.status != WinUIXamlHotReloadProtocol.APPLIED) {
                                cachedFirstFrame?.complete(Unit)
                                display.value = display.value.copy(inspection = null, inspectionError = reply.message, message = reply.message, inspectionTreeRead = false)
                                inspectionOwner = null
                                rejectedPreviewDocument = true
                                return@withCurrent
                            }
                            if (next.animation != null) {
                                display.value = display.value.copy(animationError = reply.message.takeIf { reply.status != WinUIXamlHotReloadProtocol.APPLIED })
                                if (reply.status != WinUIXamlHotReloadProtocol.APPLIED) return@withCurrent
                            }
                            if (reply.status != WinUIXamlHotReloadProtocol.APPLIED) {
                                display.value = display.value.copy(inspectionError = reply.message,
                                    message = reply.message, inspectionPropertiesRead = false)
                                return@withCurrent
                            }
                            // Selection/capture of the native host's retained
                            // content must not revive a rejected design document.
                            if (rejectedPreviewDocument && next.previewMarkup.isEmpty()) return@withCurrent
                            if (next.previewMarkup.isNotEmpty()) rejectedPreviewDocument = false
                            val owner = Triple(next.className, next.resourcePath, next.instance)
                            val retained = display.value.inspection.takeIf { inspectionOwner == owner && next.previewMarkup.isEmpty() }
                            val observed = reply.inspection?.nodes?.singleOrNull { it.path == next.selectedPath }
                            val expected = retained?.nodes?.singleOrNull { it.path == next.selectedPath }
                            // SDK code can realize a different template at the same
                            // path. Retain provenance only for the same source node.
                            val sameSelection = observed != null && expected != null && observed.typeName == expected.typeName &&
                                observed.name == expected.name && observed.sourceId == expected.sourceId
                            val retainTree = !next.readTree && retained != null && display.value.inspectionTreeRead &&
                                (!next.readProperties || sameSelection)
                            display.value = display.value.copy(inspection = reply.inspection?.let { it.copy(
                                treeToken = if (retainTree) retained!!.treeToken else it.treeToken,
                                nodes = if (retainTree) retained!!.nodes.map { node ->
                                    if (node.path == next.selectedPath && observed != null) node.copy(bounds = observed.bounds) else node
                                } else it.nodes, image = it.image ?: retained?.image) } ?: display.value.inspection,
                                message = if (retainTree) "${retained!!.nodes.size} visual nodes" else reply.message,
                                roots = reply.roots.ifEmpty { display.value.roots }, inspectionError = null,
                                animationError = if (next.previewMarkup.isNotEmpty()) null else display.value.animationError,
                                inspectionDocument = if (next.previewMarkup.isNotEmpty() && reply.inspection?.image != null) next
                                    else display.value.inspectionDocument.takeIf { inspectionOwner == owner },
                                inspectionPath = next.selectedPath, inspectionPropertiesRead = next.readProperties,
                                inspectionTreeRead = reply.inspection != null && (next.readTree || retainTree),
                                renderMillis = (System.nanoTime() - renderStarted) / 1_000_000)
                            if (next.previewMarkup.isNotEmpty()) {
                                log.info("XAML preview rendered: ${display.value.renderMillis}ms")
                                if (!firstFrameReported && launchStartedNanos != 0L && reply.inspection?.image != null) {
                                    firstFrameReported = true
                                    cachedFirstFrame?.complete(Unit)
                                    log.info("XAML preview first frame: ${(System.nanoTime() - launchStartedNanos) / 1_000_000}ms")
                                }
                            }
                            if (isolatedPreview && !firstTreeReported && firstFrameReported && next.readTree && reply.inspection != null) {
                                firstTreeReported = true
                                log.info("XAML preview visual tree ready: ${(System.nanoTime() - launchStartedNanos) / 1_000_000}ms")
                            }
                            if (reply.inspection != null) inspectionOwner = owner
                            if (!next.readTree && (next.previewMarkup.isNotEmpty() && reply.inspection?.image != null ||
                                next.readProperties && !retainTree && reply.inspection != null)) {
                                // Own this continuation in the session, so editor
                                // recomposition cannot leave the picture uneditable.
                                // A newer source document always takes precedence.
                                val queued = pendingInspection
                                if (queued == null) pendingInspection = next.copy(previewMarkup = "", capture = false,
                                    animation = null, readTree = true)
                                else if (queued.previewMarkup.isEmpty() && Triple(queued.className, queued.resourcePath, queued.instance) == owner)
                                    pendingInspection = queued.copy(readTree = true)
                            }
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
                catch (error: Exception) { withCurrent(epoch) {
                    cachedFirstFrame?.complete(Unit)
                    val message = error.message?.takeIf { it.isNotBlank() } ?: "The preview request failed. Refresh the preview."
                    // A lost reply may have replaced the host's document. Do not
                    // re-enable editing of the old image or revive it by selection.
                    if (activeRequest?.previewMarkup?.isNotEmpty() == true) {
                        rejectedPreviewDocument = true
                        inspectionOwner = null
                    }
                    display.value = display.value.copy(message = message, inspectionError = message,
                        inspection = display.value.inspection.takeUnless { rejectedPreviewDocument },
                        inspectionTreeRead = display.value.inspectionTreeRead && !rejectedPreviewDocument, inspectionPropertiesRead = false)
                } }
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
            runCatching { stopOwned() }.fold({ withCurrent(request) { runProcess?.complete(0); display.value = WinUIXamlHotReloadState("Development application stopped.") } },
                { error -> withCurrent(request) { display.value = WinUIXamlHotReloadState(error.message.orEmpty(), pid = owned?.process?.takeIf { it.isAlive }?.pid()) } })
        } }
    }

    private fun stopOwned(retainDeployment: Boolean = false) {
        val previousExecutable = (owned?.process?.info()?.command()?.orElse(null)
            ?: directPreview?.info()?.command()?.orElse(null))?.let(Path::of)
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
        selectedLaunch?.let { launch ->
            val base = Path.of(if (isolatedPreview) launch.previewExecutable else launch.executable)
            if (!retainDeployment && previousExecutable != null && WinRTPreparedLaunch.isReplacement(base, previousExecutable))
                runCatching { WinRTPreparedLaunch.discard(base, previousExecutable) }
                    .onFailure { log.debug("Retired Native deployment cleanup deferred", it) }
        }
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
        display.value = next.copy(inspecting = false, rebuildingCode = false)
        request
    }

    private fun cancelRequests() {
        codeRebuild?.cancel(); codeRebuild = null
        codeBuildProcess?.let { WinRTGradleTasks.cancel(project, it) }; codeBuildProcess = null
        cachedFirstFrame?.complete(Unit); cachedFirstFrame = null
        worker?.cancel(); edits?.cancel(); update?.cancel(); inspection?.cancel()
        pendingInspection = null
        pendingPreviewCache = null
        inspectionOwner = null
        rejectedPreviewDocument = false
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
