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
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol
import java.nio.file.Path
import java.util.UUID

/** Keeps Gradle's execution, debugging, target and option serialization contracts intact. */
class WinRTApplicationRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    GradleRunConfiguration(project, factory, name) {

    override fun getConfigurationEditor(): SettingsEditor<ExternalSystemRunConfiguration> = WinRTApplicationSettingsEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        environment.putUserData(WinRTApplicationHotReload.KEY, null)
        if (!service<WinRTIdeSettings>().hotReloadEnabled) return super.getState(executor, environment)
        val models = project.service<WinRTProjectService>()
        val module = project.service<WinRTRunConfigurationNames>().applicationModule(this, models.modules.value)
            ?: return super.getState(executor, environment)
        val task = settings.taskNames.single().substringAfterLast(':')
        val name = WinRTRunConfigurationNames.taskName(module, task) ?: return super.getState(executor, environment)
        val launch = module.hotReloadLaunches.firstOrNull {
            it.taskName == task || WinRTRunConfigurationNames.taskName(module, it.taskName) == name
        } ?: return super.getState(executor, environment)
        val directory = Path.of(module.buildDirectory).resolve("kotlin-winrt/ide-hot-reload/${UUID.randomUUID()}").toAbsolutePath().normalize()
        // The platform runner requires ExternalSystemRunnableState, including
        // for Debug. Clone the native configuration rather than wrapping its state
        // or mutating the user's persistent environment for each execution.
        val execution = clone() as WinRTApplicationRunConfiguration
        execution.settings.env = settings.env + (WinRTXamlHotReloadProtocol.SESSION_DIRECTORY to directory.toString())
        val state = execution.gradleState(executor, environment)
        if (state != null) environment.putUserData(WinRTApplicationHotReload.KEY, WinRTApplicationHotReload(module, launch, directory))
        return state
    }

    private fun gradleState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? = super.getState(executor, environment)

    override fun checkConfiguration() {
        super.checkConfiguration()
        if (settings.externalProjectPath.isNullOrBlank() || settings.taskNames.size != 1)
            throw RuntimeConfigurationError("Select a Kotlin WinRT application. Sync Gradle to load the available applications.")
    }
}
