package io.github.composefluent.winrt.ide.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
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
    private val builds = linkedMapOf<String, List<WinRTModuleData>>()
    private var restored = false

    @Synchronized
    fun refreshFromGradleCache() {
        if (project.isDisposed || restored) return
        ProjectDataManager.getInstance().getExternalProjectsData(project, GradleConstants.SYSTEM_ID).forEach { data ->
            val structure = data.externalProjectStructure ?: return@forEach
            builds.putIfAbsent(structure.data.linkedExternalProjectPath,
                ExternalSystemApiUtil.findAllRecursively(structure, WinRTModuleData.KEY).map { it.data })
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
    }

    fun openFile(path: String) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                LocalFileSystem.getInstance().findFileByPath(path.replace('\\', '/'))?.let { file ->
                    FileEditorManager.getInstance(project).openFile(file, true)
                }
            }
        }
    }
}
