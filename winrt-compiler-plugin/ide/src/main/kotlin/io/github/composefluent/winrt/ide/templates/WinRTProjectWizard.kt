package io.github.composefluent.winrt.ide.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.execution.RunManager
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.Panel
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import javax.swing.Icon

class WinRTProjectWizard : GeneratorNewProjectWizard {
    override val id = "kotlin.winrt"
    override val name = "Kotlin WinRT"
    override val icon: Icon = AllIcons.Nodes.Module
    override fun createStep(context: WizardContext): NewProjectWizardStep =
        NewProjectWizardChainStep(RootNewProjectWizardStep(context))
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::WinRTWizardStep)
}

private class WinRTWizardStep(private val base: NewProjectWizardBaseStep) : AbstractNewProjectWizardStep(base) {
    private val checkout = TextFieldState(PropertiesComponent.getInstance().getValue("kotlin.winrt.toolchain.checkout", ""))
    private val packageName = TextFieldState("io.github.composefluent.winrt.app")
    private val sdk = TextFieldState("10.0.26100.0")
    private val jdk = TextFieldState(PropertiesComponent.getInstance().getValue("kotlin.winrt.jdk",
        System.getenv("JAVA_HOME") ?: System.getProperty("java.home")))
    private val appSdk = TextFieldState("2.5.1")
    private val dependencies = TextFieldState()
    private val projections = TextFieldState(":winrt-projections")
    private val buildRoot = TextFieldState(context.project?.let { GradleSettings.getInstance(it).linkedProjectsSettings.firstOrNull()?.externalProjectPath }.orEmpty())
    private var kind by mutableStateOf(WinRTTemplateKind.WinUIApplication)
    private var packaged by mutableStateOf(true)
    private var prepare by mutableStateOf(true)
    private var includeWinUI by mutableStateOf(true)
    private var validate: () -> Unit = {}

    private fun options() = WinRTTemplateOptions(base.name, packageName.text.toString().trim(), kind,
        sdk.text.toString().trim(), appSdk.text.toString().trim(), packaged,
        dependencies.text.toString().split(',').map(String::trim).filter(String::isNotEmpty),
        projectionModule = projections.text.toString().trim(), includeWinUI = includeWinUI)

    private fun error(): String? = runCatching {
        options().validate()
        val jdkPath = Path.of(jdk.text.toString().trim())
        require(java.nio.file.Files.isRegularFile(jdkPath.resolve("include/jni.h")) &&
            java.nio.file.Files.readString(jdkPath.resolve("release")).contains(Regex("JAVA_VERSION=\"25(?:[.\"]|-)"))) {
            "Select a full JDK 25 installation, including JNI headers for the Windows launcher."
        }
        if (context.isCreatingNewProject) WinRTTemplates.validateToolchain(Path.of(checkout.text.toString().trim()))
        else {
            val root = Path.of(buildRoot.text.toString().trim()).toAbsolutePath().normalize()
            require(java.nio.file.Files.isRegularFile(root.resolve("settings.gradle.kts"))) { "Select the existing Gradle build root." }
            require(target().parent == root) { "Set Location to the selected Gradle build root." }
        }
    }.exceptionOrNull()?.message

    override fun setupUI(builder: Panel) {
        val component = compose(focusOnClickInside = true) {
            LaunchedEffect(Unit) {
                snapshotFlow { listOf(checkout.text, packageName.text, jdk.text, sdk.text, appSdk.text, dependencies.text, buildRoot.text, projections.text, kind, packaged, includeWinUI) }
                    .collect { validate() }
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Template · Kotlin/JVM · JDK 25")
                WinRTTemplateKind.entries.forEach { template ->
                    RadioButtonRow(template.title, selected = kind == template, onClick = { kind = template })
                }
                Text("Kotlin package")
                TextField(packageName, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Kotlin package" })
                Text("JDK 25 installation")
                TextField(jdk, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "JDK 25 installation" })
                Text("Windows SDK version")
                TextField(sdk, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Windows SDK version" })
                if (kind == WinRTTemplateKind.ProjectionLibrary) {
                    CheckboxRow("Include WinUI projections", checked = includeWinUI, onCheckedChange = { includeWinUI = it })
                }
                if (kind.xaml || (kind == WinRTTemplateKind.ProjectionLibrary && includeWinUI)) {
                    Text("Windows App SDK version")
                    TextField(appSdk, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Windows App SDK version" })
                }
                if (context.isCreatingNewProject) {
                    Text("Kotlin WinRT toolchain checkout")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(checkout, modifier = Modifier.weight(1f).semantics { contentDescription = "Kotlin WinRT toolchain checkout" })
                        DefaultButton(onClick = {
                            FileChooser.chooseFile(FileChooserDescriptorFactory.createSingleFolderDescriptor(), context.project, null)?.let { folder ->
                                checkout.edit { replace(0, length, folder.path) }
                            }
                        }) { Text("Browse…") }
                    }
                } else {
                    Text("Existing Gradle build root")
                    TextField(buildRoot, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Existing Gradle build root" })
                }
                if (!context.isCreatingNewProject) {
                    if (kind != WinRTTemplateKind.ProjectionLibrary) {
                        Text("Shared SDK projection module")
                        TextField(projections, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Shared SDK projection module" })
                    }
                    Text("Module dependencies (comma-separated project paths)")
                    TextField(dependencies, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Module dependencies" })
                }
                if (kind.application) {
                    CheckboxRow("MSIX package identity", checked = packaged, onCheckedChange = { packaged = it })
                    Text("Includes the default application icon set, all resource variants and an AppX manifest.")
                }
                CheckboxRow("Prepare projections and XAML after Gradle sync", checked = prepare, onCheckedChange = { prepare = it })
            }
        }
        builder.row {
            cell(component).align(Align.FILL).resizableColumn()
                .validationRequestor { request -> validate = { request(); context.requestWizardButtonsUpdate() } }
                .validationOnInput { error()?.let { ValidationInfo(it, component) } }
                .validationOnApply { error()?.let { ValidationInfo(it, component) } }
        }.resizableRow()
    }

    override fun setupProject(project: Project) {
        error()?.let { error(it) }
        val selected = options()
        val newProject = context.isCreatingNewProject
        val target = target()
        val root = if (newProject) target else Path.of(buildRoot.text.toString().trim()).toAbsolutePath().normalize()
        val moduleName = if (newProject) WinRTTemplates.primaryModule(kind) else selected.name
        val files = if (newProject) WinRTTemplates.project(selected, Path.of(checkout.text.toString().trim())) else WinRTTemplates.module(selected)
        WinRTTemplateWriter.create(project, target, files, if (newProject) null else root)
        if (newProject) PropertiesComponent.getInstance().setValue("kotlin.winrt.toolchain.checkout", checkout.text.toString().trim())
        val jdkHome = jdk.text.toString().trim()
        val jdks = ProjectJdkTable.getInstance().allJdks
        val javaSdk = jdks.firstOrNull { com.intellij.openapi.util.io.FileUtil.pathsEqual(it.homePath, jdkHome) }
            ?: JavaSdk.getInstance().createJdk(generateSequence(1) { it + 1 }
                .map { if (it == 1) "Kotlin WinRT JDK 25" else "Kotlin WinRT JDK 25 ($it)" }
                .first { candidate -> jdks.none { it.name == candidate } }, jdkHome, false).also {
                com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(it) }
            }
        if (newProject) com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            ProjectRootManager.getInstance(project).projectSdk = javaSdk
        }
        PropertiesComponent.getInstance().setValue("kotlin.winrt.jdk", jdkHome)
        if (kind.application) {
            val execution = ExternalSystemTaskExecutionSettings().apply {
                externalProjectPath = com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(root.toString())
                externalSystemIdString = GradleConstants.SYSTEM_ID.id
                executionName = "Run $moduleName"
                taskNames = listOf(":$moduleName:runWindows")
            }
            ExternalSystemUtil.createExternalSystemRunnerAndConfigurationSettings(execution, project, GradleConstants.SYSTEM_ID)?.let {
                RunManager.getInstance(project).addConfiguration(it)
                RunManager.getInstance(project).selectedConfiguration = it
            }
        }
        // Shared source documents participate in the IDE command/undo stack before Gradle sees them.
        if (!newProject) {
            val documents = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByNioFile(root.resolve("settings.gradle.kts"))
                ?.let(documents::getDocument)?.let(documents::saveDocument)
        }
        val externalRoot = com.intellij.openapi.util.io.FileUtil.toSystemIndependentName(root.toString())
        val linked = GradleProjectSettings().apply {
            externalProjectPath = externalRoot
            distributionType = DistributionType.DEFAULT_WRAPPED
            gradleJvm = javaSdk.name
        }
        if (prepare) project.service<WinRTProjectService>().prepareAfterImport(root.resolve(moduleName).toString())
        if (GradleSettings.getInstance(project).getLinkedProjectSettings(externalRoot) == null) {
            ExternalSystemUtil.linkExternalProject(linked, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
        } else {
            ExternalSystemUtil.refreshProject(externalRoot, ImportSpecBuilder(project, GradleConstants.SYSTEM_ID).use(ProgressExecutionMode.IN_BACKGROUND_ASYNC))
        }
    }

    private fun target(): Path = Path.of(base.path).resolve(base.name).toAbsolutePath().normalize()
}
