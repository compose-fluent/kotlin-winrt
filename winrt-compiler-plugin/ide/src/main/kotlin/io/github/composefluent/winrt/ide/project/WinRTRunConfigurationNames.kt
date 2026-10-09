package io.github.composefluent.winrt.ide.project

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.run.WinRTApplicationConfigurationType
import io.github.composefluent.winrt.ide.run.WinRTApplicationRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

/** Import application profiles using Gradle's runner/debugger and preserve user configurations. */
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
                        migrateApplication(manager, settings, modules)
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
            applicationTasks(module).forEach { (name, task) ->
                val qualified = (module.projectPath.takeUnless { it == ":" }.orEmpty() + ":" + task)
                val existing = manager.allSettings.filter { settings ->
                    val config = settings.configuration as? WinRTApplicationRunConfiguration
                    config != null &&
                        FileUtil.pathsEqual(config.settings.externalProjectPath, root) && config.settings.taskNames.size == 1 &&
                        (config.settings.taskNames.single() == qualified || config.settings.taskNames.single().let { value ->
                            value.substringBeforeLast(':', "").ifEmpty { ":" } == module.projectPath &&
                                taskName(module, value.substringAfterLast(':')) == name
                        })
                }
                if (existing.isEmpty()) {
                    val execution = ExternalSystemTaskExecutionSettings().apply {
                        externalProjectPath = FileUtil.toSystemIndependentName(root)
                        externalSystemIdString = GradleConstants.SYSTEM_ID.id
                        taskNames = listOf(qualified)
                        executionName = name
                    }
                    val settings = WinRTApplicationConfigurationType.create(project, execution)
                    manager.addConfiguration(settings)
                    if (firstCreated == null) firstCreated = settings
                } else existing.filter { it.isTemporary }.forEach(manager::makeStable)
            }
        }
        // The platform may select every newly added configuration. Restore the
        // user's selection, or choose the first application after the full import.
        (previousSelection ?: firstCreated)?.let { manager.selectedConfiguration = it }
    }

    internal fun rename(settings: RunnerAndConfigurationSettings, modules: List<WinRTModuleData>): Boolean {
        val configuration = settings.configuration as? ExternalSystemRunConfiguration ?: return false
        val execution = configuration.settings
        val module = applicationModule(configuration, modules) ?: return false
        val task = execution.taskNames.single()
        val name = taskName(module, task.substringAfterLast(':')) ?: return false
        if (configuration.name == name) return false
        // An explicit executionName can be a user label, including one saved by
        // the application editor. It is not a generated Gradle name.
        val defaults = setOf("${project.name} [$task]", "${Path.of(execution.externalProjectPath).fileName} [$task]") +
            listOfNotNull(configuration.suggestedName().takeIf { execution.executionName.isNullOrBlank() })
        val previous = (listOf("jvm") + module.targets.filter { it.platform == "native" }.map { it.name }).flatMap { platform -> listOf(false, true).map {
            displayName(module.projectPath.trim(':'), platform, it)
        } }
        if (configuration.name !in defaults && configuration.name !in previous) return false
        settings.name = name
        execution.executionName = name
        return true
    }

    private fun applicationModule(configuration: ExternalSystemRunConfiguration, modules: List<WinRTModuleData>): WinRTModuleData? {
        val execution = configuration.settings
        if (execution.externalSystemIdString != GradleConstants.SYSTEM_ID.id || execution.taskNames.size != 1) return null
        val task = execution.taskNames.single()
        val path = task.substringBeforeLast(':', "")
        return modules.singleOrNull { current ->
            val root = project.service<WinRTProjectService>().buildRootFor(current)
            val sameBuild = FileUtil.pathsEqual(execution.externalProjectPath, current.projectDirectory) ||
                (root != null && FileUtil.pathsEqual(execution.externalProjectPath, root))
            sameBuild && (path.ifEmpty { ":" } == current.projectPath ||
                (path.isEmpty() && FileUtil.pathsEqual(execution.externalProjectPath, current.projectDirectory)))
        }
    }

    private fun migrateApplication(manager: RunManager, settings: RunnerAndConfigurationSettings, modules: List<WinRTModuleData>) {
        val configuration = settings.configuration as? GradleRunConfiguration ?: return
        if (configuration.type !is GradleExternalTaskConfigurationType) return
        val module = applicationModule(configuration, modules) ?: return
        if (taskName(module, configuration.settings.taskNames.single().substringAfterLast(':')) == null) return
        val persistent = settings as? RunnerAndConfigurationSettingsImpl ?: return
        // The platform's scheme includes runner settings, before-run tasks,
        // folders and storage flags. Read it into the same settings object so
        // selection and user customizations survive the type change.
        val original = persistent.writeScheme()
        val converted = original.clone().apply {
            setAttribute("type", WinRTApplicationConfigurationType.ID)
            setAttribute("factoryName", WinRTApplicationConfigurationType.ID)
        }
        val shared = settings.isStoredInDotIdeaFolder || settings.isStoredInArbitraryFileInProject
        val path = settings.pathIfStoredInArbitraryFileInProject
        manager.removeConfiguration(settings)
        try {
            persistent.readExternal(converted, shared, path)
            manager.setUniqueNameIfNeeded(settings)
        } catch (error: Exception) {
            persistent.readExternal(original, shared, path)
            LOG.warn("Could not migrate the WinRT application configuration ${settings.name}", error)
        } finally {
            manager.addConfiguration(settings)
        }
    }

    override fun dispose() = Unit

    companion object {
        private val LOG = Logger.getInstance(WinRTRunConfigurationNames::class.java)

        fun displayName(module: String, platform: String, packaged: Boolean) =
            "$module:Run[$platform, ${if (packaged) "packaged" else "unpackaged"}]"

        /** Collapse aliases, preferring a concrete main/debug launch task for each application variant. */
        internal fun applicationTasks(module: WinRTModuleData): Map<String, String> =
            module.runTasks.mapNotNull { task -> taskName(module, task)?.let { it to task } }.groupBy { it.first }
                .toSortedMap(compareBy<String> { !it.contains("[jvm,") }.thenBy { !it.contains("packaged]") }.thenBy { it })
                .mapValues { (_, candidates) -> candidates.map { it.second }.sortedWith(compareBy<String> { it.contains("ReleaseExecutable") }
                    .thenBy { !it.contains("Main", true) }.thenByDescending { it.length }).first() }

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
