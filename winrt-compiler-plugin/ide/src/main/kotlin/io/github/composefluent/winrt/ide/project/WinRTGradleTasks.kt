package io.github.composefluent.winrt.ide.project

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import org.jetbrains.plugins.gradle.util.GradleConstants

object WinRTGradleTasks {
    fun prepareXaml(project: Project, module: WinRTModuleData) {
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = project.service<WinRTProjectService>().buildRootFor(module) ?: module.projectDirectory
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            executionName = "Prepare Kotlin WinRT XAML analysis"
            val prefix = module.projectPath.trimEnd(':')
            taskNames = listOf("$prefix:analyzeWinRTXaml", "$prefix:generateWinRTProjections")
        }
        ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() { if (!project.isDisposed) project.service<WinRTXamlSnapshotService>().refresh() }
                override fun onFailure() = Unit
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    }
}
