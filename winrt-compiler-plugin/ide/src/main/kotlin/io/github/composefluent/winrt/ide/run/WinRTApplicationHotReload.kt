package io.github.composefluent.winrt.ide.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.hotreload.WinUIXamlHotReloadService
import io.github.composefluent.winrt.ide.preview.WinUIXamlVisualInspectionSelection
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import java.nio.file.Path

/** A single IDE execution owns its transport directory; saved run settings do
 * not retain it. Gradle still owns launch, debugging, logs and cancellation. */
internal data class WinRTApplicationHotReload(val module: WinRTModuleData, val launch: WinUIXamlHotReloadLaunchData, val directory: Path,
    val launchEnvironment: Map<String, String> = emptyMap(), val launchArguments: String = "", val passParentEnvironment: Boolean = true) {
    companion object {
        val KEY = Key.create<WinRTApplicationHotReload>("KotlinWinRT.ApplicationHotReload")
        val ACTIVE_KEY = Key.create<WinRTApplicationHotReload>("KotlinWinRT.ActiveApplicationHotReload")
    }
}

class WinRTApplicationExecutionListener : ExecutionListener {
    override fun processStarted(executorId: String, environment: ExecutionEnvironment, handler: ProcessHandler) {
        val launch = environment.getUserData(WinRTApplicationHotReload.KEY) ?: return
        environment.putUserData(WinRTApplicationHotReload.KEY, null)
        environment.putUserData(WinRTApplicationHotReload.ACTIVE_KEY, launch)
        val project = environment.project
        if (project.isDisposed) return
        project.service<WinRTProjectService>().selectedModuleDirectory.value = launch.module.projectDirectory
        project.service<WinUIXamlVisualInspectionSelection>().reset()
        project.service<WinUIXamlHotReloadService>().attach(launch.module, launch.launch, launch.directory, building = { !((handler as? WinRTDevelopmentProcessHandler)?.build ?: handler).isProcessTerminated },
            runProcess = handler as? WinRTDevelopmentProcessHandler,
            launchEnvironment = launch.launchEnvironment, launchArguments = launch.launchArguments,
            passParentEnvironment = launch.passParentEnvironment)
    }

    override fun processTerminated(executorId: String, environment: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val launch = environment.getUserData(WinRTApplicationHotReload.ACTIVE_KEY) ?: return
        environment.putUserData(WinRTApplicationHotReload.ACTIVE_KEY, null)
        // A successful packaged task can return before the activated app
        // publishes its session. Only a failed/cancelled build ends the wait.
        if (exitCode != 0 && !environment.project.isDisposed)
            environment.project.service<WinUIXamlHotReloadService>().launchFailed(launch.directory)
    }

    override fun processNotStarted(executorId: String, environment: ExecutionEnvironment) {
        environment.putUserData(WinRTApplicationHotReload.KEY, null)
    }
}
