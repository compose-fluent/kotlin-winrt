package io.github.composefluent.winrt.ide.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.ui.UIUtil
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.project.WinRTRunConfigurationNames
import io.github.composefluent.winrt.ide.ui.WinRTChoice
import io.github.composefluent.winrt.ide.ui.WinRTDetails
import kotlinx.coroutines.flow.drop
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextArea
import org.jetbrains.jewel.ui.component.TextField
import java.awt.Dimension
import javax.swing.JComponent

internal class WinRTApplicationSettingsEditor(private val project: Project) : SettingsEditor<ExternalSystemRunConfiguration>() {
    private var buildRoot by mutableStateOf("")
    private var task by mutableStateOf("")
    private var inheritEnvironment by mutableStateOf(true)
    private val arguments = TextFieldState()
    private val vmOptions = TextFieldState()
    private val environment = TextFieldState()
    private var editorComponent: JComponent? = null

    override fun resetEditorFrom(configuration: ExternalSystemRunConfiguration) {
        val execution = configuration.settings
        buildRoot = FileUtil.toSystemIndependentName(execution.externalProjectPath.orEmpty())
        task = execution.taskNames.singleOrNull().orEmpty()
        arguments.setText(execution.scriptParameters.orEmpty())
        vmOptions.setText(execution.vmOptions.orEmpty())
        environment.setText(execution.env.entries.joinToString("\n") { (key, value) -> "$key=$value" })
        inheritEnvironment = execution.isPassParentEnvs
    }

    override fun applyEditorTo(configuration: ExternalSystemRunConfiguration) {
        if (buildRoot.isBlank() || task.isBlank())
            throw ConfigurationException("Select an application. Sync Gradle to load Kotlin WinRT applications.")
        val env = environment.text.lineSequence().filter { it.isNotBlank() }.associate { line ->
            val separator = line.indexOf('=')
            if (separator <= 0 || line.substring(0, separator).trim().isEmpty())
                throw ConfigurationException("Enter environment variables as NAME=value, one per line.")
            line.substring(0, separator).trim() to line.substring(separator + 1)
        }
        configuration.settings.apply {
            externalProjectPath = buildRoot
            taskNames = listOf(task)
            executionName = configuration.name
            scriptParameters = arguments.text.toString()
            this.vmOptions = this@WinRTApplicationSettingsEditor.vmOptions.text.toString()
            this.env = env
            isPassParentEnvs = inheritEnvironment
        }
    }

    override fun createEditor(): JComponent = compose(focusOnClickInside = true) {
        val service = project.service<WinRTProjectService>()
        val modules by service.modules.collectAsState()
        val applications = modules.flatMap { module ->
            val root = service.buildRootFor(module)?.let(FileUtil::toSystemIndependentName) ?: return@flatMap emptyList()
            WinRTRunConfigurationNames.applicationTasks(module).map { (name, localTask) ->
                Triple(root, module.projectPath.takeUnless { it == ":" }.orEmpty() + ":" + localTask, name)
            }
        }
        val current = applications.firstOrNull { (root, qualified) ->
            FileUtil.pathsEqual(root, buildRoot) && (qualified == task ||
                (qualified.substringBeforeLast(':', "") == task.substringBeforeLast(':', "") && modules.any { module ->
                module.projectPath == task.substringBeforeLast(':', "").ifEmpty { ":" } &&
                    WinRTRunConfigurationNames.taskName(module, task.substringAfterLast(':')) ==
                    WinRTRunConfigurationNames.taskName(module, qualified.substringAfterLast(':'))
            }))
        }
        val selected = current?.let { "${it.first}\u0000${it.second}" }
        LaunchedEffect(Unit) {
            snapshotFlow { listOf(buildRoot, task, inheritEnvironment, arguments.text, vmOptions.text, environment.text) }
                .drop(1).collect { fireEditorStateChanged() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WinRTChoice("Application", applications.map { "${it.first}\u0000${it.second}" to it.third }, selected) { key ->
                applications.firstOrNull { "${it.first}\u0000${it.second}" == key }?.let {
                    buildRoot = it.first
                    task = it.second
                }
            }
            if (applications.isEmpty()) Text("Sync Gradle to load Kotlin WinRT applications.")
            if (current == null && task.isNotBlank()) Text("Saved application: $task")
            if (buildRoot.isNotBlank()) Text("Build: $buildRoot")
            WinRTDetails("Advanced") {
                Text("Gradle arguments")
                TextField(arguments, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Gradle arguments" })
                Text("Gradle JVM options")
                TextField(vmOptions, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Gradle JVM options" })
                Text("Environment variables (NAME=value, one per line)")
                TextArea(environment, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Environment variables" })
                CheckboxRow("Include system environment variables", inheritEnvironment, { inheritEnvironment = it })
            }
        }
    }.also {
        it.preferredSize = Dimension(640, 360)
        editorComponent = it
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun disposeEditor() {
        editorComponent?.let { UIUtil.findComponentOfType(it, ComposePanel::class.java)?.dispose() }
        editorComponent = null
    }

    private fun TextFieldState.setText(value: String) = edit { replace(0, length, value) }
}
