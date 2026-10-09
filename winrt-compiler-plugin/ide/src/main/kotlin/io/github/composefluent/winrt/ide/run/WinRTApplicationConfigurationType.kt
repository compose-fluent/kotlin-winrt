package io.github.composefluent.winrt.ide.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** A persistent application profile; Gradle remains the owner of building and launching it. */
class WinRTApplicationConfigurationType : ConfigurationType, DumbAware {
    private val applicationFactory = object : ConfigurationFactory(this) {
        override fun getId() = ID
        override fun isEditableInDumbMode() = true
        override fun createTemplateConfiguration(project: Project): RunConfiguration =
            WinRTApplicationRunConfiguration(project, this, "")
    }

    override fun getId() = ID
    override fun getDisplayName() = "Kotlin WinRT Application"
    override fun getConfigurationTypeDescription() = "Run a Kotlin WinRT Windows application"
    override fun getIcon(): Icon = IconLoader.getIcon("/icons/winrtApplication.svg", WinRTApplicationConfigurationType::class.java)
    override fun getConfigurationFactories(): Array<ConfigurationFactory> = arrayOf(applicationFactory)

    companion object {
        const val ID = "KotlinWinRTApplication"

        fun create(project: Project, execution: ExternalSystemTaskExecutionSettings): RunnerAndConfigurationSettings {
            val factory = ConfigurationTypeUtil.findConfigurationType(WinRTApplicationConfigurationType::class.java).applicationFactory
            val manager = RunManager.getInstance(project)
            val settings = manager.createConfiguration(requireNotNull(execution.executionName), factory)
            (settings.configuration as WinRTApplicationRunConfiguration).settings.setFrom(execution)
            if (manager.setUniqueNameIfNeeded(settings)) {
                (settings.configuration as WinRTApplicationRunConfiguration).settings.executionName = settings.name
            }
            return settings
        }
    }
}
