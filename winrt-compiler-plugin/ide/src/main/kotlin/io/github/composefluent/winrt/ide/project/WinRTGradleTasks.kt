package io.github.composefluent.winrt.ide.project

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import io.github.composefluent.winrt.ide.analysis.WinUIXamlSnapshotService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import org.jetbrains.plugins.gradle.util.GradleConstants

object WinRTGradleTasks {
    /** Gradle cancellation resolves the JVM and may block: it requires both a
     * background thread and a platform progress context, including late launches. */
    fun cancel(project: Project, handler: ProcessHandler) {
        object : com.intellij.openapi.progress.Task.Backgroundable(project, "Cancel XAML preview build", false) {
            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                handler.destroyProcess()
            }
        }.queue()
    }
    fun prepareXaml(project: Project, module: WinRTModuleData) {
        run(project, module, buildList {
            if (module.xamlCompilations.isNotEmpty()) add("analyzeWinRTXaml")
            add("generateWinRTProjections")
            // runWinRTXamlSdkPreview already owns preparation through its task
            // dependencies. Compile the SDK host when the user opens a preview.
        }, "Prepare Kotlin WinRT XAML analysis") { project.service<WinUIXamlSnapshotService>().refresh() }
    }

    fun run(project: Project, module: WinRTModuleData, tasks: List<String>, title: String,
        environment: Map<String, String> = emptyMap(), onFailure: () -> Unit = {},
        onProcessStarted: ((ProcessHandler) -> Unit)? = null, onSuccess: () -> Unit = {}) {
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
        // The unique development-session environment identifies this launch;
        // never cancel another Gradle run merely because its title matches.
        val connection = onProcessStarted?.let { project.messageBus.connect(project) }
        connection?.subscribe(ExecutionManager.EXECUTION_TOPIC, object : ExecutionListener {
            override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
                val started = (env.runProfile as? ExternalSystemRunConfiguration)?.settings ?: return
                if (started.externalProjectPath != settings.externalProjectPath || started.env != settings.env || started.taskNames != settings.taskNames) return
                connection.disconnect()
                onProcessStarted?.invoke(handler)
            }
        })
        ExternalSystemUtil.runTask(settings, DefaultRunExecutor.EXECUTOR_ID, project, GradleConstants.SYSTEM_ID,
            object : TaskCallback {
                override fun onSuccess() {
                    connection?.disconnect()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) onSuccess()
                    }
                }
                override fun onFailure() {
                    connection?.disconnect()
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) onFailure()
                    }
                }
            }, ProgressExecutionMode.IN_BACKGROUND_ASYNC)
    }
}
