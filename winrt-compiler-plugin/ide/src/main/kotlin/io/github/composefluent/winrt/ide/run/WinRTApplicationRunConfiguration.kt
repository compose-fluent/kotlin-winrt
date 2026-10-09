package io.github.composefluent.winrt.ide.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

/** Keeps Gradle's execution, debugging, target and option serialization contracts intact. */
class WinRTApplicationRunConfiguration(project: Project, factory: ConfigurationFactory, name: String) :
    GradleRunConfiguration(project, factory, name) {

    override fun getConfigurationEditor(): SettingsEditor<ExternalSystemRunConfiguration> = WinRTApplicationSettingsEditor(project)

    override fun checkConfiguration() {
        super.checkConfiguration()
        if (settings.externalProjectPath.isNullOrBlank() || settings.taskNames.size != 1)
            throw RuntimeConfigurationError("Select a Kotlin WinRT application. Sync Gradle to load the available applications.")
    }
}
