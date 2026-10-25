package io.github.composefluent.winrt.ide.preview

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import io.github.composefluent.winrt.ide.hotreload.WinRTStaticPreviewService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import java.nio.file.Path

internal fun previewSourceModule(path: String, modules: List<WinRTModuleData>): WinRTModuleData? =
    modules.filter { candidate -> runCatching {
        Path.of(path).toAbsolutePath().normalize().startsWith(Path.of(candidate.projectDirectory).toAbsolutePath().normalize())
    }.getOrDefault(false) }.maxByOrNull { it.projectDirectory.length }

internal fun previewProjectLaunches(module: WinRTModuleData?, inherited: WinUIXamlHotReloadLaunchData?, static: Boolean) =
    (inherited?.let(::listOf) ?: module?.hotReloadLaunches.orEmpty())
        .filter { !static || it.previewExecutable.isNotBlank() }.distinctBy { it.executable }

/** A selected, visible split editor has already requested preview. Start its
 * existing session before Compose initializes the canvas and property UI, so
 * cache validation and native initialization can overlap that work. No background
 * tab or code-only editor calls this; absent models still use the normal effect.
 */
internal fun prepareSelectedXamlPreview(project: Project, path: String) {
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
