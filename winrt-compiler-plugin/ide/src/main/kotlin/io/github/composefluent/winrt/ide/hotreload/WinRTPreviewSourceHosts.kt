package io.github.composefluent.winrt.ide.hotreload

import com.intellij.openapi.components.*
import io.github.composefluent.winrt.ide.gradle.*
import java.nio.file.Path

/** Proven source-to-host routing only. The launch cache still validates build inputs
 * and the runtime still resolves the authored class before rendering a document. */
@Service(Service.Level.PROJECT)
@State(name = "KotlinWinRTPreviewSourceHosts", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class WinRTPreviewSourceHosts : PersistentStateComponent<WinRTPreviewSourceHosts.State> {
    data class Binding(var source: String = "", var module: String = "", var task: String = "")
    data class State(var bindings: MutableList<Binding> = mutableListOf())
    private var saved = State()
    @Synchronized override fun getState() = State(saved.bindings.map { it.copy() }.toMutableList())
    @Synchronized override fun loadState(state: State) { saved = State(state.bindings.takeLast(256).map { it.copy() }.toMutableList()) }
    @Synchronized fun remember(path: String, module: WinRTModuleData, launch: WinUIXamlHotReloadLaunchData) {
        val source = Path.of(path).toAbsolutePath().normalize().toString()
        saved.bindings.removeAll { it.source == source }
        saved.bindings.add(Binding(source, module.projectDirectory, launch.taskName))
        while (saved.bindings.size > 256) saved.bindings.removeAt(0)
    }
    @Synchronized fun resolve(path: String, modules: List<WinRTModuleData>): Pair<WinRTModuleData, WinUIXamlHotReloadLaunchData>? = runCatching {
        val source = Path.of(path).toAbsolutePath().normalize()
        // A stale workspace entry must never launch a host for an unimported file.
        if (modules.none { module -> module.xamlCompilations.any { compilation -> compilation.sourceRoots.any {
                source.startsWith(Path.of(it).toAbsolutePath().normalize()) } } }) return null
        val binding = saved.bindings.singleOrNull { Path.of(it.source).toAbsolutePath().normalize() == source } ?: return null
        val module = modules.singleOrNull { it.projectDirectory == binding.module } ?: return null
        val launch = module.hotReloadLaunches.singleOrNull { it.taskName == binding.task && it.previewExecutable.isNotBlank() } ?: return null
        module to launch
    }.getOrNull()
}
