package io.github.composefluent.winrt.ide.templates

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.idea.npw.model.NewProjectModel
import com.android.tools.idea.npw.model.NewProjectModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.module.ModuleDescriptionProvider
import com.android.tools.idea.npw.module.ModuleGalleryEntry
import com.android.tools.idea.npw.project.AndroidProjectEntryProvider
import com.android.tools.idea.npw.project.ChooseAndroidProjectEntry
import com.android.tools.idea.npw.project.ProjectEntryListCell
import com.android.tools.idea.observable.core.BoolValueProperty
import com.android.tools.idea.wizard.model.SkippableWizardStep
import com.android.tools.idea.wizard.model.WizardModel
import com.intellij.icons.AllIcons
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.RadioButtonRow
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.plugins.gradle.settings.GradleSettings
import javax.swing.JComponent

/** Studio replaces IDEA's generator gallery. Its project-entry hook cannot
 * replace the dependent Android SDK/activity steps, so the entry opens our
 * shared Compose wizard instead of routing WinRT through Android's recipe. */
class WinRTAndroidProjectEntryProvider : AndroidProjectEntryProvider {
    override fun getProjectEntries(): List<ChooseAndroidProjectEntry> = listOf(WinRTAndroidProjectEntry())
}

internal class WinRTAndroidProjectEntry : ChooseAndroidProjectEntry {
    private var template by mutableStateOf(WinRTTemplateKind.WinUIApplication)
    override val canGoForward: State<Boolean> = mutableStateOf(false)

    @Composable
    override fun AndroidProjectListEntry(isSelected: Boolean, isFocused: Boolean) {
        ProjectEntryListCell("Kotlin WinRT", null, isSelected, isFocused)
    }

    @Composable
    override fun AndroidProjectEntryDetails() {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Kotlin WinRT")
            Text("Build Windows applications and libraries with Kotlin and WinUI.")
            WinRTTemplateKind.entries.forEach { kind -> RadioButtonRow(kind.title, template == kind, { template = kind }) }
            Text("Configure the project, Windows SDK and JDK in the Kotlin WinRT wizard.")
            DefaultButton(onClick = {
                val gallery = DialogWrapper.findInstanceFromFocus()
                WinRTStudioProjectDialog(template) { created, directory ->
                    gallery?.close(DialogWrapper.CANCEL_EXIT_CODE)
                    ApplicationManager.getApplication().invokeLater {
                        ProjectManagerEx.getInstanceEx().openProject(directory, OpenProjectTask {
                            project = created
                            forceOpenInNewFrame = true
                            runConfigurators = false
                            createModule = false
                        })
                    }
                }.show()
            }) { Text("Create Kotlin WinRT project…") }
        }
    }

    override fun onProceeding(newProjectModuleModel: NewProjectModuleModel, newProjectModel: NewProjectModel) = Unit
}

internal class WinRTStudioProjectDialog(kind: WinRTTemplateKind,
    private val created: (Project, java.nio.file.Path) -> Unit) : DialogWrapper(true) {
    private val context = WizardContext(null, disposable)
    private val base = NewProjectWizardBaseStep(RootNewProjectWizardStep(context)).apply { name = "WinRTApplication" }
    private val step = WinRTWizardStep(base, kind)
    private val form = panel { base.setupUI(this); step.setupUI(this) }

    init {
        title = "New Kotlin WinRT Project"
        isResizable = true
        setOKButtonText("Create")
        init()
    }

    override fun createCenterPanel(): JComponent = form.apply { preferredSize = java.awt.Dimension(720, 640) }
    override fun doValidateAll(): List<ValidationInfo> = form.validateAll()
    override fun doOKAction() {
        if (form.validateAll().isNotEmpty()) return
        form.apply()
        step.validationMessage()?.let { setErrorText(it); return }
        val directory = step.targetDirectory()
        val manager = ProjectManagerEx.getInstanceEx()
        val project = manager.newProject(directory, OpenProjectTask {
            isNewProject = true
            projectName = base.name
            runConfigurators = false
            createModule = false
        }) ?: run { setErrorText("The IDE could not create the project."); return }
        try { step.setupProject(project) }
        catch (error: Exception) {
            com.intellij.openapi.util.Disposer.dispose(project)
            setErrorText(error.message ?: "Project creation failed.")
            return
        }
        super.doOKAction()
        created(project, directory)
    }
}

class WinRTAndroidModuleDescriptionProvider : ModuleDescriptionProvider {
    override fun getDescriptions(project: Project): Collection<ModuleGalleryEntry> = listOf(WinRTAndroidModuleEntry())
}

internal class WinRTAndroidModuleEntry : ModuleGalleryEntry {
    override val icon = AllIcons.Nodes.Module
    override val name = "Kotlin WinRT"
    override val description = "Create a Windows application, WinRT library, controls, resources or SDK projection module."
    override fun createStep(project: Project, moduleParent: String, invoker: ProjectSyncInvoker): SkippableWizardStep<*> =
        WinRTStudioModuleStep(project)
}

private class WinRTStudioModuleModel : WizardModel() {
    var create: () -> Unit = {}
    override fun handleFinished() = create()
}

private class WinRTStudioModuleStep(project: Project,
    private val creation: WinRTStudioModuleModel = WinRTStudioModuleModel()) :
    SkippableWizardStep<WinRTStudioModuleModel>(creation, "Kotlin WinRT Module", AllIcons.Nodes.Module) {
    private val context = WizardContext(project, this)
    private val base = NewProjectWizardBaseStep(RootNewProjectWizardStep(context)).apply {
        path = GradleSettings.getInstance(project).linkedProjectsSettings.firstOrNull()?.externalProjectPath ?: project.basePath.orEmpty()
        name = "winrtApp"
    }
    private val step = WinRTWizardStep(base)
    private val form = panel { base.setupUI(this); step.setupUI(this) }
    private val valid = BoolValueProperty(false)

    init {
        fun validate() { valid.set(form.validateAll().isEmpty() && step.validationMessage() == null) }
        step.validationChanged = ::validate
        base.propertyGraph.afterPropagation(::validate)
        context.addContextListener(object : WizardContext.Listener { override fun buttonsUpdateRequested() = validate() })
        creation.create = { form.apply(); step.setupProject(project) }
    }

    override fun getComponent(): JComponent = form
    override fun canGoForward() = valid
    override fun onEntering() { valid.set(form.validateAll().isEmpty() && step.validationMessage() == null) }
}
