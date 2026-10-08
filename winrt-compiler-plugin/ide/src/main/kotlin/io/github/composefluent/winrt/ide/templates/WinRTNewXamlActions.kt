package io.github.composefluent.winrt.ide.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDirectory
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.project.WinRTGradleTasks
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent

class WinRTNewXamlPageAction : WinRTNewXamlAction(WinRTXamlFileKind.Page)
class WinRTNewXamlUserControlAction : WinRTNewXamlAction(WinRTXamlFileKind.UserControl)
class WinRTNewXamlResourcesAction : WinRTNewXamlAction(WinRTXamlFileKind.ResourceDictionary)

abstract class WinRTNewXamlAction(private val kind: WinRTXamlFileKind) : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null && event.getData(LangDataKeys.IDE_VIEW)?.directories.orEmpty()
            .any { WinRTXamlFileCreation.context(project, it, kind) != null }
    }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val view = event.getData(LangDataKeys.IDE_VIEW) ?: return
        val directory = view.orChooseDirectory ?: return
        val context = WinRTXamlFileCreation.context(project, directory, kind) ?: return
        WinRTNewXamlDialog(project, directory, kind, WinRTXamlFileCreation.packageName(directory, context)) created@ { files ->
            if (!directory.isValid) return@created
            files.lastOrNull()?.let { file ->
                directory.findFile(file)?.let(view::selectElement)
                directory.virtualFile.findChild(file)?.let { OpenFileDescriptor(project, it).navigate(true) }
            }
            // A new class/page changes XAMLC's authoring input. Reuse the owning
            // declaration/projection tasks; this does not compile the application.
            if (context.module.xamlCompilations.isNotEmpty()) WinRTGradleTasks.run(project, context.module,
                listOf("analyzeWinRTXaml", "generateWinRTProjections"), "Prepare ${kind.title} analysis") {
                project.service<WinRTXamlSnapshotService>().refresh()
            }
        }.show()
    }
}

internal object WinRTXamlFileCreation {
    data class Context(val module: WinRTModuleData, val kotlinRoot: Path?)

    fun context(project: Project, directory: PsiDirectory, kind: WinRTXamlFileKind): Context? {
        if (!directory.isValid || !directory.virtualFile.isWritable) return null
        val path = Path.of(directory.virtualFile.path).toAbsolutePath().normalize()
        val modules = project.service<WinRTProjectService>().modules.value
        if (modules.any { path.startsWith(Path.of(it.buildDirectory).toAbsolutePath().normalize()) }) return null
        return modules.sortedByDescending { it.projectDirectory.length }.firstNotNullOfOrNull { module ->
            val kotlinRoot = module.sourceSets.flatMap { it.kotlinRoots }.map { Path.of(it).toAbsolutePath().normalize() }
                .filter { path.startsWith(it) }.maxByOrNull { it.nameCount }
            val resourceRoot = kind.kotlinBase == null && module.sourceSets.flatMap { it.appxResourceRoots }
                .any { path.startsWith(Path.of(it).toAbsolutePath().normalize()) }
            if (kotlinRoot != null || resourceRoot) Context(module, kotlinRoot) else null
        }
    }

    fun packageName(directory: PsiDirectory, context: Context): String {
        val declared = directory.files.filterIsInstance<KtFile>().map { it.packageFqName.asString() }.distinct().singleOrNull()
        return declared?.takeIf { it.isNotBlank() } ?: context.kotlinRoot?.relativize(Path.of(directory.virtualFile.path).toAbsolutePath().normalize())
            ?.joinToString(".").orEmpty()
    }

    fun error(directory: PsiDirectory, kind: WinRTXamlFileKind, name: String, packageName: String): String? = runCatching {
        require(directory.isValid && directory.virtualFile.isWritable) { "The selected directory is no longer writable." }
        WinRTXamlFileTemplates.validate(kind, name, packageName)
        val files = listOfNotNull("$name.xaml", "$name.kt".takeIf { kind.kotlinBase != null })
        val existing = directory.virtualFile.children.firstOrNull { child -> files.any { it.equals(child.name, ignoreCase = true) } }
        require(existing == null) { "A file already exists: ${existing?.name}" }
    }.exceptionOrNull()?.message

    fun create(project: Project, directory: PsiDirectory, kind: WinRTXamlFileKind, name: String, packageName: String): List<String> {
        error(directory, kind, name, packageName)?.let { error(it) }
        val files = WinRTXamlFileTemplates.files(kind, name, packageName)
        WinRTTemplateWriter.create(project, Path.of(directory.virtualFile.path), files, commandName = "Create ${kind.title}")
        return files.keys.toList()
    }
}

private class WinRTNewXamlDialog(private val project: Project, private val directory: PsiDirectory,
    private val kind: WinRTXamlFileKind, packageName: String, private val created: (List<String>) -> Unit) : DialogWrapper(project) {
    private val name = TextFieldState()
    private val packageField = TextFieldState(packageName)
    private var failure by mutableStateOf<String?>(null)
    private var creating by mutableStateOf(false)
    init { title = "New ${kind.title}"; init() }
    override fun createActions(): Array<Action> = emptyArray()
    override fun createCenterPanel(): JComponent = compose(focusOnClickInside = true) {
        val problem = ReadAction.compute<String?, RuntimeException> {
            WinRTXamlFileCreation.error(directory, kind, name.text.toString(), packageField.text.toString())
        }
        WinRTNewXamlForm(kind, directory.virtualFile.path, name, packageField, failure ?: problem,
            creating, problem == null, onCreate = ::createFiles, onCancel = { close(CANCEL_EXIT_CODE) })
    }

    private fun createFiles() {
        if (creating || isDisposed) return
        val className = name.text.toString()
        val kotlinPackage = packageField.text.toString()
        creating = true; failure = null
        // Compose callbacks do not hold write intent, including inside a
        // modal dialog. Enter the platform before the undoable VFS write.
        ApplicationManager.getApplication().invokeLater({
            if (isDisposed) return@invokeLater
            if (project.isDisposed) { close(CANCEL_EXIT_CODE); return@invokeLater }
            val files = try {
                WinRTXamlFileCreation.create(project, directory, kind, className, kotlinPackage)
            } catch (cancelled: ProcessCanceledException) {
                creating = false
                throw cancelled
            } catch (error: Exception) {
                creating = false; failure = error.message
                return@invokeLater
            }
            close(OK_EXIT_CODE)
            ApplicationManager.getApplication().invokeLater({
                if (!project.isDisposed) created(files)
            }, ModalityState.nonModal())
        }, ModalityState.current())
    }
}

@Composable
internal fun WinRTNewXamlForm(kind: WinRTXamlFileKind, directory: String, name: TextFieldState,
    packageName: TextFieldState, problem: String?, creating: Boolean, valid: Boolean, onCreate: () -> Unit, onCancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submitOnEnter = Modifier.onPreviewKeyEvent {
        if (it.key == Key.Enter && it.type == KeyEventType.KeyDown && valid && !creating) {
            onCreate(); true
        } else false
    }
    Column(Modifier.width(460.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (kind.kotlinBase == null) "Resource name" else "Class name")
        TextField(name, enabled = !creating, modifier = Modifier.fillMaxWidth().focusRequester(focus).then(submitOnEnter).semantics { contentDescription = "XAML name" })
        if (kind.kotlinBase != null) {
            Text("Kotlin package")
            TextField(packageName, enabled = !creating, modifier = Modifier.fillMaxWidth().then(submitOnEnter).semantics { contentDescription = "Kotlin package" })
        }
        Text("Location: $directory")
        if (name.text.isNotBlank()) Text("Files: ${name.text}.xaml${if (kind.kotlinBase != null) " + ${name.text}.kt" else ""}")
        if (name.text.isNotBlank()) problem?.let { Text(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DefaultButton(enabled = valid && !creating, onClick = onCreate) { Text(if (creating) "Creating…" else "Create") }
            OutlinedButton(enabled = !creating, onClick = onCancel) { Text("Cancel") }
        }
    }
}
