package io.github.composefluent.winrt.ide.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.gradle.util.GradleConstants

@Service(Service.Level.PROJECT)
class WinRTProjectService(private val project: Project) {
    private val imported = MutableStateFlow<List<WinRTModuleData>>(emptyList())
    val modules: StateFlow<List<WinRTModuleData>> = imported
    val selectedModuleDirectory = MutableStateFlow<String?>(null)
    private val builds = linkedMapOf<String, List<WinRTModuleData>>()
    private val preparationRequests = mutableSetOf<String>()
    private val dependencyRequests = mutableSetOf<String>()
    val dependencyRevision = MutableStateFlow(0L)
    private var restored = false

    init {
        // Editor services may request the model before the platform has loaded
        // its external-project cache. Retry after that lifecycle boundary.
        ExternalProjectsManager.getInstance(project).runWhenInitializedInBackground {
            synchronized(this) { restored = false; refreshFromGradleCache() }
        }
    }

    @Synchronized
    fun refreshFromGradleCache() {
        if (project.isDisposed || restored) return
        val manager = ProjectDataManager.getInstance()
        manager.getExternalProjectsData(project, GradleConstants.SYSTEM_ID).forEach { data ->
            val structure = data.externalProjectStructure ?: return@forEach
            manager.ensureTheDataIsReadyToUse(structure)
            val models = ExternalSystemApiUtil.findAllRecursively(structure, WinRTModuleData.KEY).map { it.data }
            // The platform can expose a skeletal graph from Gradle settings
            // before its persisted custom nodes are loaded. Do not retain that
            // empty placeholder over the later complete graph.
            if (models.isNotEmpty()) builds.putIfAbsent(structure.data.linkedExternalProjectPath, models)
        }
        restored = true
        publish()
    }

    @Synchronized
    fun replaceBuildModels(buildRoot: String, models: List<WinRTModuleData>) {
        if (project.isDisposed) return
        refreshFromGradleCache()
        builds[buildRoot] = models.toList()
        publish()
    }

    private fun publish() {
        imported.value = builds.values.flatten()
            .distinctBy { it.projectDirectory }
            .sortedBy { it.projectDirectory }
        io.github.composefluent.winrt.ide.analysis.WinRTFirModuleConfiguration.restore(project, imported.value)
        imported.value.filter { preparationRequests.remove(it.projectDirectory.replace('\\', '/').lowercase()) }.forEach { module ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) WinRTGradleTasks.prepareXaml(project, module)
            }
        }
        imported.value.filter { dependencyRequests.remove(it.projectDirectory.replace('\\', '/').lowercase()) }.forEach { module ->
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) WinRTGradleTasks.run(project, module,
                    listOf("restoreWinAppDependencies", "generateWinRTProjections"), "Restore WinRT NuGet dependencies") {
                    dependencyRevision.value += 1
                    project.getService(io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService::class.java).refresh()
                }
            }
        }
    }

    @Synchronized
    fun prepareAfterImport(moduleDirectory: String) {
        preparationRequests += moduleDirectory.replace('\\', '/').lowercase()
    }

    @Synchronized
    fun restoreDependenciesAfterImport(moduleDirectory: String) {
        dependencyRequests += moduleDirectory.replace('\\', '/').lowercase()
    }

    @Synchronized
    fun buildRootFor(module: WinRTModuleData): String? =
        builds.entries.firstOrNull { (_, models) -> models.any { it.projectDirectory == module.projectDirectory } }?.key

    fun openFile(path: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))?.let { file ->
                    if (file.isDirectory) com.intellij.ide.projectView.ProjectView.getInstance(project).select(null, file, true)
                    else FileEditorManager.getInstance(project).openFile(file, true)
                }
            }
        }
    }
}
