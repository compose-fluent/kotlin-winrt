package io.github.composefluent.winrt.ide.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.project.WinRTRunConfigurationNames
import io.github.composefluent.winrt.ide.settings.WinRTIdeSettings
import io.github.composefluent.winrt.runtime.WinUIXamlHotReloadProtocol
import java.nio.file.Path
import java.util.UUID
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import io.github.composefluent.winrt.ide.debug.WinRTNativeDebugLaunch
import io.github.composefluent.winrt.ide.debug.WinRTAttachNativeDebuggerAction
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinUIXamlHotReloadLaunchData

/** Keeps Gradle's execution, debugging, target and option serialization contracts intact. */
class WinRTApplicationRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    GradleRunConfiguration(project, factory, name) {

    override fun getConfigurationEditor(): SettingsEditor<ExternalSystemRunConfiguration> = WinRTApplicationSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        environment.putUserData(WinRTApplicationHotReload.KEY, null)
        environment.putUserData(WinRTNativeDebugLaunch.KEY, null)
        val (module, launch) = developmentLaunch() ?: return super.getState(executor, environment)
        val nativeDebug = executor.id == DefaultDebugExecutor.EXECUTOR_ID && launch.nativeCompilerHome.isNotBlank()
        if (!service<WinRTIdeSettings>().hotReloadEnabled && !nativeDebug) return super.getState(executor, environment)
        val directory = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}").toAbsolutePath().normalize()
        val execution = clone() as WinRTApplicationRunConfiguration
        execution.settings.env = settings.env + (WinUIXamlHotReloadProtocol.SESSION_DIRECTORY to directory.toString())
        if (!nativeDebug && !launch.developmentBuildTask.isNullOrBlank()) {
            execution.settings.env = execution.settings.env + ("KOTLIN_WINRT_RELEASE_BUILD_LOCK" to "1")
            // Kotlin's runDebugExecutable alias executes linker staging directly.
            // Development sessions must use the imported isolated launch task.
            execution.settings.taskNames = listOf(if (module.projectPath == ":") launch.taskName else "${module.projectPath}:${launch.taskName}")
        }
        if (nativeDebug) {
            if (launch.taskName.contains("ReleaseExecutable")) throw RuntimeConfigurationError("Select a Native Debug executable to use source breakpoints and stepping.")
            val lldb = WinRTAttachNativeDebuggerAction.findLldb(service<WinRTIdeSettings>().nativeDebuggerExecutable)
                ?: throw RuntimeConfigurationError("Set the Windows LLDB executable in Settings | Tools | Kotlin WinRT.")
            val debugDirectory = directory.resolve("debug")
            execution.settings.env = execution.settings.env + ("KOTLIN_WINRT_DEBUG_DIRECTORY" to debugDirectory.toString())
            execution.settings.taskNames = listOf(if (module.projectPath == ":") launch.taskName else "${module.projectPath}:${launch.taskName}")
            environment.putUserData(WinRTNativeDebugLaunch.KEY, WinRTNativeDebugLaunch(debugDirectory, Path.of(launch.executable), Path.of(launch.nativeCompilerHome), lldb, Path.of(module.projectDirectory)))
        }
        val state = execution.gradleState(if (nativeDebug) DefaultRunExecutor.getRunExecutorInstance() else executor, environment)
        if (state != null) environment.putUserData(WinRTApplicationHotReload.KEY, WinRTApplicationHotReload(module, launch, directory,
            settings.env.toMap(), settings.scriptParameters.orEmpty(), settings.isPassParentEnvs))
        if (state == null || nativeDebug || launch.developmentBuildTask.isNullOrBlank()) return state
        return WinRTDevelopmentRunState(state)
    }

    internal fun developmentLaunch(): Pair<WinRTModuleData, WinUIXamlHotReloadLaunchData>? {
        val models = project.service<WinRTProjectService>()
        val module = project.service<WinRTRunConfigurationNames>().applicationModule(this, models.modules.value)
            ?: return null
        val task = settings.taskNames.singleOrNull()?.substringAfterLast(':') ?: return null
        val name = WinRTRunConfigurationNames.taskName(module, task) ?: return null
        val launch = module.hotReloadLaunches.firstOrNull { it.taskName == task }
            ?: module.hotReloadLaunches.firstOrNull {
                WinRTRunConfigurationNames.taskName(module, it.taskName) == name &&
                    (!task.startsWith("runDebugExecutable") && !task.startsWith("runReleaseExecutable") ||
                        it.taskName.contains("ReleaseExecutable") == task.startsWith("runReleaseExecutable"))
            } ?: return null
        return module to launch
    }

    private fun gradleState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = super.getState(executor, environment)

    override fun checkConfiguration() {
        super.checkConfiguration()
        if (settings.externalProjectPath.isNullOrBlank() || settings.taskNames.size != 1)
            throw RuntimeConfigurationError("Select a Kotlin WinRT application. Sync Gradle to load the available applications.")
    }
}

/** Preserve Gradle console/actions while keeping the standard Stop action alive. */
internal class WinRTDevelopmentRunState(val delegate: RunProfileState) : RunProfileState {
    override fun execute(executor: Executor, runner: com.intellij.execution.runners.ProgramRunner<*>): com.intellij.execution.ExecutionResult? {
        val result = delegate.execute(executor, runner) ?: return null
        val build = result.processHandler ?: return result
        return com.intellij.execution.DefaultExecutionResult(result.executionConsole,
            WinRTDevelopmentProcessHandler(build), *result.actions).also {
            if (result is com.intellij.execution.DefaultExecutionResult) it.setRestartActions(*result.restartActions)
        }
    }
}
