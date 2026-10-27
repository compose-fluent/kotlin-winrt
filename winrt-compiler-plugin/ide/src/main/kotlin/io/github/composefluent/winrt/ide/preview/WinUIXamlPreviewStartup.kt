@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.preview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import io.github.composefluent.winrt.ide.hotreload.WinRTStaticPreviewService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import java.nio.file.Path
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadState
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol

internal fun previewSourceModule(path: String, modules: List<WinRTModuleData>): WinRTModuleData? =
    modules.filter { candidate -> runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(candidate.projectDirectory).toAbsolutePath().normalize())
    }.getOrDefault(false) }.maxByOrNull { it.projectDirectory.length }

internal fun previewProjectLaunches(module: WinRTModuleData?, inherited: WinUIXamlHotReloadLaunchData?, static: Boolean) =
    (inherited?.let(::listOf) ?: module?.hotReloadLaunches.orEmpty())
        .filter { !static || it.previewExecutable.isNotBlank() }.distinctBy { it.executable }

/** The editor owns/cancels each wait; the project owns the injected scope and
 * the eventual development session. During IDE restore the model may arrive
 * after selectNotify but before the first Compose frame.
 */
@Service(Service.Level.PROJECT)
internal class WinUIXamlPreviewStartup(private val project: Project, private val scope: CoroutineScope) {
    @Volatile private var preparation: CompletableDeferred<Unit>? = null

    fun prepare(path: String): kotlinx.coroutines.Job {
        val ready = CompletableDeferred<Unit>()
        preparation = ready
        return scope.launch(Dispatchers.EDT) {
            project.service<WinRTProjectService>().modules.first { previewSourceModule(path, it) != null }
            prepareSelectedXamlPreview(project, path)
        }.also { job -> job.invokeOnCompletion { ready.complete(Unit) } }
    }

    // The catalog's IO coroutine can receive the restored model before the EDT
    // resumes selectNotify. Honor the already-visible editor's pending request.
    internal suspend fun awaitPreparation() {
        preparation?.let { withTimeoutOrNull(1_000) { it.await() } }
    }

}

/** A selected, visible split editor has already requested preview. Start its
 * existing session before Compose initializes the canvas and property UI, so
 * cache validation and native initialization can overlap that work. No background
 * tab or code-only editor calls this. The normal effect shares its session.
 */
internal fun prepareSelectedXamlPreview(project: Project, path: String) {
    val source = Path.of(path).toAbsolutePath().normalize()
    if (FileEditorManager.getInstance(project).selectedFiles.none {
            Path.of(it.path).toAbsolutePath().normalize() == source }) return
    val modules = project.service<WinRTProjectService>().modules.value
    val module = previewSourceModule(path, modules) ?: return
    val preferences = PropertiesComponent.getInstance(project)
    if (!preferences.getBoolean("winrt.preview.projectCode.${module.projectDirectory}", true)) return
    val design = project.service<WinRTStaticPreviewService>()
    val inherited = path.takeIf { module.hotReloadLaunches.isEmpty() }?.let(design::sourcePreviewLaunch)
    val launch = previewProjectLaunches(module, inherited?.second, true).firstOrNull() ?: return
    if (launch.nativeCompilerHome.isBlank()) return
    val theme = preferences.getValue("winrt.preview.theme.${module.projectDirectory}", "Default")
        .takeIf { it in setOf("Default", "Light", "Dark") } ?: "Default"
    com.intellij.openapi.diagnostic.Logger.getInstance(WinUIXamlPreviewStartup::class.java)
        .info("XAML editor preview requested: ${source.fileName}, theme=$theme")
    design.ensurePreview(inherited?.first ?: module, launch, theme = theme)
}

/** Project previews own the build queue until they connect or fail. A slow build
 * is not a failure: starting the SDK compiler alongside it delays the first frame.
 */
internal suspend fun startPreferredXamlPreview(
    wantsProjectCode: Boolean,
    projectConnected: Boolean,
    startProject: suspend () -> Unit,
    awaitProject: suspend () -> Boolean,
    startBasic: suspend () -> Unit,
    continueStartup: () -> Boolean = { true },
) {
    if (!continueStartup() || wantsProjectCode && projectConnected) return
    com.intellij.openapi.diagnostic.Logger.getInstance("WinUIXamlPreviewStartup")
        .info("XAML designer startup requested: projectCode=$wantsProjectCode")
    if (wantsProjectCode) {
        startProject()
        if (awaitProject()) return
    }
    if (continueStartup()) startBasic()
}

/** Connection changes drive rendering directly, without waiting for the Swing/Compose
 * frame that displays that connection. Disconnects cancel pending zoom coalescing;
 * property/bitmap replies do not resubmit the same document. */
internal fun Flow<WinUIXamlHotReloadState>.previewRenderTargets() = map { state ->
    if (!state.connected) null else state.inspectionRoots.firstOrNull {
        it.className == WinUIXamlHotReloadProtocol.PREVIEW_CLASS
    }?.let { state.pid to it }
}.distinctUntilChanged()
