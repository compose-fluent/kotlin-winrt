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
        run(project, module, buildList {
            if (module.xamlCompilations.isNotEmpty()) add("analyzeWinRTXaml")
            add("generateWinRTProjections")
            // runWinRTXamlSdkPreview already owns preparation through its task
            // dependencies. Compile the SDK host when the user opens a preview.
        }, "Prepare Kotlin WinRT XAML analysis") { project.service<WinRTXamlSnapshotService>().refresh() }
    }

    fun run(project: Project, module: WinRTModuleData, tasks: List<String>, title: String,
        environment: Map<String, String> = emptyMap(), onFailure: () -> Unit = {}, onSuccess: () -> Unit = {}) {
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalProjectPath = com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(
                project.service<WinRTProjectService>().buildRootFor(module) ?: module.projectDirectory)
            externalSystemIdString = GradleConstants.SYSTEM_ID.id
            executionName = title
            env = environment
            isPassParentEnvs = true
            val prefix = module.projectPath.trimEnd(':')
            taskNames = tasks.map { "$prefix:$it" }
        }
        ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) onSuccess()
                    }
                }
                override fun onFailure() {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) onFailure()
                    }
                }
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    }
}
