package io.github.composefluent.winrt.ide.templates

import com.intellij.ide.IdeView
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTSourceSetData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/** Native New-menu registration, paired PSI, file protection and VFS undo.
 * The paired shape follows .cswinrt/src/Samples/WinUIDesktopSample/MainPage.xaml.cs;
 * Kotlin component initialization remains owned by compiler/xaml/XamlPageBodies. */
class WinRTXamlFileCreationTest : BasePlatformTestCase() {
    private var importedRoot: String? = null
    override fun createTempDirTestFixture() = TempDirTestFixtureImpl()

    fun testNativeNewMenuIsAvailableOnlyInWritableWinRTSourceRoots() {
        val source = directory("app/src/main/kotlin/sample/views")
        val resources = directory("app/src/main/appxResources/Styles")
        val generated = directory("app/build/generated/kotlin")
        val outside = directory("app/src/main/kotlinOther")
        val actions = listOf("KotlinWinRT.NewXamlPage", "KotlinWinRT.NewXamlUserControl", "KotlinWinRT.NewXamlResources")
            .map { requireNotNull(ActionManager.getInstance().getAction(it)) }
        val group = ActionManager.getInstance().getAction("NewGroup") as ActionGroup
        assertTrue(group.getChildren(null).toList().containsAll(actions))
        actions.forEach { assertFalse(update(it, source)) }
        importModule()
        actions.forEach { assertTrue(update(it, source)) }
        assertFalse(update(actions[0], resources))
        assertFalse(update(actions[1], resources))
        assertTrue(update(actions[2], resources))
        actions.forEach {
            assertFalse("Generated source must not offer new authored files", update(it, generated))
            assertFalse("Sibling path prefixes are not source roots", update(it, outside))
        }
    }

    fun testPageAndUserControlCreateNavigableKotlinAndXamlPairs() {
        val directory = directory("app/src/main/kotlin/sample/views")
        importModule()
        val context = requireNotNull(WinRTXamlFileCreation.context(project, directory, WinRTXamlFileKind.Page))
        val packageName = WinRTXamlFileCreation.packageName(directory, context)
        assertEquals("sample.views", packageName)
        for (kind in listOf(WinRTXamlFileKind.Page, WinRTXamlFileKind.UserControl)) {
            val name = "New${kind.rootTag}"
            val created = WinRTXamlFileCreation.create(project, directory, kind, name, packageName)
            assertEquals(setOf("$name.kt", "$name.xaml"), created.toSet())
            val kotlin = directory.findFile("$name.kt") as KtFile
            val declaration = kotlin.declarations.single() as KtClass
            assertEquals("$packageName.$name", declaration.fqName!!.asString())
            assertEquals(listOf("${kind.rootTag}()"), declaration.superTypeListEntries.map { it.text })
            assertEquals(kind.kotlinBase, kotlin.importDirectives.single().importedFqName!!.asString())
            assertFalse("Construction must use the existing compiler hook", kotlin.text.contains("initializeComponent"))
            assertFalse(PsiTreeUtil.hasErrorElements(kotlin))
            val xaml = directory.findFile("$name.xaml") as XmlFile
            val root = xaml.rootTag!!
            assertEquals(kind.rootTag, root.name)
            val classValue = root.getAttribute("Class", "http://schemas.microsoft.com/winfx/2006/xaml")!!.valueElement!!
            assertEquals(declaration.fqName!!.asString(), classValue.value)
            assertFalse(PsiTreeUtil.hasErrorElements(xaml))
            IndexingTestUtil.waitUntilIndexesAreReady(project)
            val references = PsiReferenceService.getService().getReferences(classValue, PsiReferenceService.Hints.NO_HINTS)
            assertTrue("The generated x:Class must navigate to its new Kotlin declaration",
                references.any { PsiManager.getInstance(project).areElementsEquivalent(it.resolve(), declaration) })
        }
    }

    fun testBothFilesUndoAndRedoAsOneCreation() {
        val directory = directory("app/src/main/kotlin/sample/views")
        WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.Page, "DetailsPage", "sample.views")
        val kotlin = directory.findFile("DetailsPage.kt")!!.virtualFile
        val editor = FileEditorManager.getInstance(project).openFile(kotlin, true).first()
        val undo = UndoManager.getInstance(project)
        assertTrue(undo.isUndoAvailable(editor))
        undo.undo(editor)
        assertNull(directory.virtualFile.findChild("DetailsPage.kt"))
        assertNull(directory.virtualFile.findChild("DetailsPage.xaml"))
        // Undoing a creation closes its deleted file's editor. Native Redo is
        // now global, as it is from the Project view after that editor closes.
        assertTrue(undo.isRedoAvailable(null))
        undo.redo(null)
        assertNotNull(directory.virtualFile.findChild("DetailsPage.kt"))
        assertNotNull(directory.virtualFile.findChild("DetailsPage.xaml"))
    }

    fun testClassNamedPageDoesNotInheritItself() {
        val directory = directory("app/src/main/kotlin/sample/views")
        WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.Page, "Page", "sample.views")
        val kotlin = directory.findFile("Page.kt") as KtFile
        val alias = kotlin.importDirectives.single().aliasName
        assertEquals("microsoft.ui.xaml.controls.Page", kotlin.importDirectives.single().importedFqName!!.asString())
        assertNotNull(alias)
        assertFalse(alias == "Page")
        assertEquals("$alias()", (kotlin.declarations.single() as KtClass).superTypeListEntries.single().text)
    }

    fun testEitherExistingFileProtectsThePairIncludingUnsavedContentAndWindowsCase() {
        val directory = directory("app/src/main/kotlin/sample/views")
        val existing = myFixture.addFileToProject("app/src/main/kotlin/sample/views/Taken.kt", "// Original Kotlin\n")
        val document = FileDocumentManager.getInstance().getDocument(existing.virtualFile)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("// Unsaved user content\n") }
        expectRejected { WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.UserControl, "taken", "sample.views") }
        assertEquals("// Unsaved user content\n", document.text)
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
        assertNull(directory.virtualFile.findChild("taken.xaml"))
        myFixture.addFileToProject("app/src/main/kotlin/sample/views/Colors.xaml", "<!-- Existing dictionary -->")
        expectRejected { WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.Page, "Colors", "sample.views") }
        assertNull(directory.virtualFile.findChild("Colors.kt"))
        assertEquals("<!-- Existing dictionary -->", directory.findFile("Colors.xaml")!!.text)
        expectRejected { WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.Page, "../Outside", "sample.views") }
        expectRejected { WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.Page, "ValidPage", "sample.when") }
        expectRejected { WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.ResourceDictionary, "CON.Theme", "") }
        assertEquals(setOf("Taken.kt", "Colors.xaml"), directory.files.map { it.name }.toSet())
    }

    fun testResourceDictionaryHasNoKotlinCompanionOrClassDirective() {
        val directory = directory("app/src/main/appxResources/Styles")
        val created = WinRTXamlFileCreation.create(project, directory, WinRTXamlFileKind.ResourceDictionary, "Palette.Dark", "")
        assertEquals(listOf("Palette.Dark.xaml"), created)
        val file = directory.findFile("Palette.Dark.xaml") as XmlFile
        assertEquals("ResourceDictionary", file.rootTag!!.name)
        assertNull(file.rootTag!!.getAttribute("Class", "http://schemas.microsoft.com/winfx/2006/xaml"))
        assertEquals(listOf("Palette.Dark.xaml"), directory.files.map { it.name })
        assertFalse(PsiTreeUtil.hasErrorElements(file))
    }

    fun testPackageInferenceRespectsExistingKotlinDeclarations() {
        val directory = directory("app/src/main/kotlin/folder/name")
        myFixture.addFileToProject("app/src/main/kotlin/folder/name/Existing.kt", "package sample.actual\nclass Existing")
        importModule()
        val context = requireNotNull(WinRTXamlFileCreation.context(project, directory, WinRTXamlFileKind.Page))
        assertEquals("sample.actual", WinRTXamlFileCreation.packageName(directory, context))
    }

    private fun directory(path: String) = requireNotNull(PsiManager.getInstance(project).findDirectory(myFixture.tempDirFixture.findOrCreateDir(path)))

    private fun importModule() {
        val root = myFixture.tempDirFixture.tempDirPath.also { importedRoot = it }
        // A model-only import does not register IntelliJ content/source roots.
        // Match the native module roots that a real Gradle import supplies.
        val content = directory("app").virtualFile
        val sources = directory("app/src/main/kotlin").virtualFile
        ModuleRootModificationUtil.updateModel(module) { it.addContentEntry(content).addSourceFolder(sources, false) }
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(WinRTModuleData(":app", "$root/app", "$root/app/build", "2.4.0", "",
            listOf(WinRTSourceSetData("main", listOf("$root/app/src/main/kotlin", "$root/app/build/generated/kotlin"), emptyList(),
                listOf("$root/app/src/main/appxResources"))), emptyList(), emptyList(), emptyList())))
    }

    private fun update(action: AnAction, directory: PsiDirectory): Boolean {
        val view = object : IdeView {
            override fun getDirectories() = arrayOf(directory)
            override fun getOrChooseDirectory() = directory
        }
        val data = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(LangDataKeys.IDE_VIEW, view).build()
        val event = AnActionEvent.createFromAnAction(action, null, ActionPlaces.PROJECT_VIEW_POPUP, data)
        action.update(event)
        return event.presentation.isEnabledAndVisible
    }

    private fun expectRejected(create: () -> Unit) {
        try { create(); fail("Invalid or conflicting creation must not write files") }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
    }

    override fun tearDown() {
        try { importedRoot?.let { project.service<WinRTProjectService>().replaceBuildModels(it, emptyList()) } }
        finally { super.tearDown() }
    }
}
