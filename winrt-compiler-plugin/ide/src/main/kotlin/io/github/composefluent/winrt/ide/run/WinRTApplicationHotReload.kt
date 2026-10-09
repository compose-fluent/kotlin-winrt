package io.github.composefluent.winrt.ide.run

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import io.github.composefluent.winrt.ide.gradle.WinRTHotReloadLaunchData
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.hotreload.WinRTHotReloadService
import io.github.composefluent.winrt.ide.preview.WinRTVisualInspectionSelection
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import java.nio.file.Path

/** A single IDE execution owns its transport directory; saved run settings do
 * not retain it. Gradle still owns launch, debugging, logs and cancellation. */
internal data class WinRTApplicationHotReload(val module: WinRTModuleData, val launch: WinRTHotReloadLaunchData, val directory: Path) {
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
        project.service<WinRTVisualInspectionSelection>().reset()
        project.service<WinRTHotReloadService>().attach(launch.module, launch.launch, launch.directory)
    }

    override fun processTerminated(executorId: String, environment: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        val launch = environment.getUserData(WinRTApplicationHotReload.ACTIVE_KEY) ?: return
        environment.putUserData(WinRTApplicationHotReload.ACTIVE_KEY, null)
        // A successful packaged task can return before the activated app
        // publishes its session. Only a failed/cancelled build ends the wait.
        if (exitCode != 0 && !environment.project.isDisposed)
            environment.project.service<WinRTHotReloadService>().launchFailed(launch.directory)
    }

    override fun processNotStarted(executorId: String, environment: ExecutionEnvironment) {
        environment.putUserData(WinRTApplicationHotReload.KEY, null)
    }
}
