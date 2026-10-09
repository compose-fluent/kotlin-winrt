package io.github.composefluent.winrt.ide.project

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

/** Import runnable applications using Gradle's runner/debugger and preserve user configurations. */
@Service(Service.Level.PROJECT)
class WinRTRunConfigurationNames(private val project: Project) : Disposable {
    private var updating = false
    private var scheduled = false

    init {
        project.messageBus.connect(this).subscribe(RunManagerListener.TOPIC, object : RunManagerListener {
            override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) { if (!updating) refresh() }
            override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) { if (!updating) refresh() }
            override fun stateLoaded(runManager: RunManager, isFirstLoad: Boolean) = refresh()
        })
    }

    fun refresh() {
        if (scheduled || project.isDisposed) return
        scheduled = true
        ApplicationManager.getApplication().invokeLater {
            scheduled = false
            if (!project.isDisposed) {
                val modules = project.service<WinRTProjectService>().modules.value
                val manager = if (modules.isNotEmpty()) RunManager.getInstance(project) else RunManager.getInstanceIfCreated(project) ?: return@invokeLater
                updating = true
                try {
                    val selected = manager.selectedConfiguration
                    manager.allSettings.forEach { settings ->
                    // addConfiguration updates the platform's name-based key as
                    // well as publishing the change; a notification alone does not.
                    if (rename(settings, modules)) manager.addConfiguration(settings)
                }
                    if (selected != null) manager.selectedConfiguration = selected
                    registerApplications(manager, modules)
                } finally { updating = false }
            }
        }
    }

    private fun registerApplications(manager: RunManager, modules: List<WinRTModuleData>) {
        val previousSelection = manager.selectedConfiguration
        var firstCreated: RunnerAndConfigurationSettings? = null
        modules.forEach { module ->
            val root = project.service<WinRTProjectService>().buildRootFor(module) ?: return@forEach
            // Prefer a concrete main/debug task over the same target's lifecycle aliases.
            module.runTasks.mapNotNull { task -> taskName(module, task)?.let { it to task } }.groupBy { it.first }
                .toSortedMap(compareBy<String> { !it.contains("[jvm,") }.thenBy { !it.contains("packaged]") }.thenBy { it })
                .forEach { (name, candidates) ->
                    val task = candidates.map { it.second }.sortedWith(compareBy<String> { it.contains("ReleaseExecutable") }
                        .thenBy { !it.contains("Main", true) }.thenByDescending { it.length }).first()
                    val qualified = (module.projectPath.takeUnless { it == ":" }.orEmpty() + ":" + task)
                    val existing = manager.allSettings.any { settings ->
                        val config = settings.configuration as? ExternalSystemRunConfiguration
                        config != null && config.settings.externalSystemIdString == GradleConstants.SYSTEM_ID.id &&
                            FileUtil.pathsEqual(config.settings.externalProjectPath, root) && config.settings.taskNames.size == 1 &&
                            (config.settings.taskNames.single() == qualified || config.settings.taskNames.single().let { value ->
                                value.substringBeforeLast(':', "") == module.projectPath &&
                                    taskName(module, value.substringAfterLast(':')) == name
                            })
                    }
                    if (!existing) {
                        val execution = ExternalSystemTaskExecutionSettings().apply {
                            externalProjectPath = FileUtil.toSystemIndependentName(root)
                            externalSystemIdString = GradleConstants.SYSTEM_ID.id
                            taskNames = listOf(qualified)
                            executionName = name
                        }
                        ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(execution, project, GradleConstants.SYSTEM_ID)?.let { settings ->
                            settings.configuration.name = name
                            manager.addConfiguration(settings)
                            if (firstCreated == null) firstCreated = settings
                        }
                    }
                }
        }
        // The platform may select every newly added configuration. Restore the
        // user's selection, or choose the first application after the full import.
        (previousSelection ?: firstCreated)?.let { manager.selectedConfiguration = it }
    }

    internal fun rename(settings: RunnerAndConfigurationSettings, modules: List<WinRTModuleData>): Boolean {
        val configuration = settings.configuration as? ExternalSystemRunConfiguration ?: return false
        val execution = configuration.settings
        if (execution.externalSystemIdString != GradleConstants.SYSTEM_ID.id || execution.taskNames.size != 1) return false
        val task = execution.taskNames.single()
        val path = task.substringBeforeLast(':', "")
        val local = task.substringAfterLast(':')
        val module = modules.filter { current ->
            (path.isNotEmpty() && path == current.projectPath) ||
                (path.isEmpty() && FileUtil.pathsEqual(execution.externalProjectPath, current.projectDirectory))
        }.singleOrNull() ?: return false
        val root = project.service<WinRTProjectService>().buildRootFor(module)
        if (!FileUtil.pathsEqual(execution.externalProjectPath, module.projectDirectory) &&
            (root == null || !FileUtil.pathsEqual(execution.externalProjectPath, root))) return false
        val name = taskName(module, local) ?: return false
        if (configuration.name == name) return false
        val defaults = setOf(configuration.suggestedName(), "${project.name} [$task]",
            "${Path.of(execution.externalProjectPath).fileName} [$task]")
        val previous = (listOf("jvm") + module.targets.filter { it.platform == "native" }.map { it.name }).flatMap { platform -> listOf(false, true).map {
            displayName(module.projectPath.trim(':'), platform, it)
        } }
        if (configuration.name !in defaults && configuration.name !in previous) return false
        configuration.name = name
        execution.executionName = name
        return true
    }

    override fun dispose() = Unit

    companion object {
        fun displayName(module: String, platform: String, packaged: Boolean) =
            "$module:Run[$platform, ${if (packaged) "packaged" else "unpackaged"}]"

        internal fun taskName(module: WinRTModuleData, task: String): String? {
            val packaged = task.startsWith("runWinAppPackage")
            if (!packaged && !task.startsWith("runWinAppHost") && task != "runWindows" &&
                !task.startsWith("runDebugExecutable") && !task.startsWith("runReleaseExecutable")) return null
            val suffix = listOf("runWinAppPackage", "runWinAppHost", "runDebugExecutable", "runReleaseExecutable", "runWindows")
                .firstNotNullOfOrNull { prefix -> task.takeIf { it.startsWith(prefix) }?.removePrefix(prefix) } ?: return null
            val explicit = module.targets.filter { suffix.startsWith(it.name, ignoreCase = true) }
            val target = explicit.maxByOrNull { it.name.length } ?: if (suffix.isNotEmpty()) return null else {
                // The toolkit's unsuffixed alias selects JVM main when present.
                module.targets.singleOrNull { it.platform == "jvm" } ?: module.targets.singleOrNull()
            } ?: return null
            val platform = when (target.platform) { "jvm" -> "jvm"; "native" -> target.name; else -> return null }
            return displayName(module.projectPath.trim(':').ifEmpty { Path.of(module.projectDirectory).fileName.toString() }, platform, packaged)
        }
    }
}
