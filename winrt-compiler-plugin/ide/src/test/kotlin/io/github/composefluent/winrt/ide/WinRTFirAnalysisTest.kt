package io.github.composefluent.winrt.ide

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.xaml.WinRTXamlGeneratedNavigation
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream

/** Uses the actual IDE plugin loader and Analysis API, not a generated Kotlin substitute. */
@OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
class WinRTFirAnalysisTest : BasePlatformTestCase() {
    private var fixtureDirectory: Path? = null

    fun testGeneratedNamesAndSupertypesUpdateAfterDeclarationSnapshotChanges() {
        val root = Files.createTempDirectory("winrt-ide-fir-").also { fixtureDirectory = it }
        val declarations = root.resolve("declarations.json")
        Files.writeString(declarations, declaration("Launch"))
        val originalPlugin = root.resolve("winrt-compiler-plugin.jar")
        JarOutputStream(Files.newOutputStream(originalPlugin)).close()
        ApplicationManager.getApplication().runWriteAction {
            val manager = FacetManager.getInstance(module)
            val facet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null)
            facet.configuration.settings.useProjectSettings = false
            facet.configuration.settings.compilerArguments = K2JVMCompilerArguments().apply {
                pluginClasspaths = arrayOf(originalPlugin.toString())
                pluginOptions = arrayOf("plugin:io.github.composefluent.winrt.compiler:xamlDeclarations=$declarations")
            }
            manager.createModifiableModel().apply { addFacet(facet); commit() }
        }
        myFixture.addFileToProject("Button.kt", "package microsoft.ui.xaml.controls\nclass Button")
        myFixture.addFileToProject("Page.kt", "package microsoft.ui.xaml.controls\nopen class Page { val Launch: Button? = null }")
        myFixture.addFileToProject("Connector.kt", "package microsoft.ui.xaml.markup\ninterface IComponentConnector")
        myFixture.addFileToProject("Component.kt", "package io.github.composefluent.winrt.runtime\ninterface WinRTXamlComponent\nclass WinRTXamlLoadState")
        val file = myFixture.configureByText("Shell.kt", "package sample\nclass Shell : microsoft.ui.xaml.controls.Page() { fun element() = Open }") as KtFile
        val markup = root.resolve("Shell.xaml")
        Files.writeString(markup, """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.Shell"><Button x:Name="Open"/><Button Content="{Binding ElementName=Open}"/></Page>""")
        val markupFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(markup.toFile())!!
        PsiTestUtil.addContentRoot(module, markupFile.parent)
        project.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(
            WinRTModuleData(":", root.toString(), root.resolve("build").toString(), "2.4.0", "",
                emptyList(), emptyList(), emptyList(), emptyList(), listOf(
                    WinRTXamlCompilationData("analyzeWinRTXamlMain", listOf(root.toString()), declarations.toString(), "", "", ""),
                )),
        ))
        val snapshots = project.service<WinRTXamlSnapshotService>()
        PlatformTestUtil.waitWithEventsDispatching("XAML snapshot", { snapshots.currentText(declarations.toString()) != null }, 10)
        assertTrue(memberNames(file).contains("Launch"))
        assertTrue(supertypeNames(file).any { it.contains("IComponentConnector") })
        assertTrue(nameConflictDiagnostics(file).any { it.contains("x:Name 'Launch'") })
        snapshots.publish(declarations.toString(), declaration("Open"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val updated = memberNames(file)
        assertFalse(updated.contains("Launch"))
        assertTrue(updated.contains("Open"))
        assertTrue(nameConflictDiagnostics(file).isEmpty())
        val reference = PsiTreeUtil.findChildrenOfType(file, KtNameReferenceExpression::class.java).first { it.getReferencedName() == "Open" }
        val target = allowAnalysisOnEdt {
            WinRTXamlGeneratedNavigation().getGotoDeclarationTargets(reference, reference.textOffset, myFixture.editor)
        }!!.single() as XmlAttributeValue
        assertEquals("Open", target.value)
        val usages = allowAnalysisOnEdt { ReferencesSearch.search(target).findAll() }
        assertTrue(usages.toString(), usages.any { it.element.containingFile == file })
        assertTrue(usages.toString(), usages.any { it.element.containingFile.virtualFile == markupFile })
        allowAnalysisOnEdt { RenameProcessor(project, target, "Run", false, false).run() }
        assertTrue(file.text, file.text.contains("element() = Run"))
        val renamedMarkup = PsiManager.getInstance(project).findFile(markupFile)!!
        assertTrue(renamedMarkup.text, renamedMarkup.text.contains("x:Name=\"Run\""))
        assertTrue(renamedMarkup.text, renamedMarkup.text.contains("ElementName=Run"))
        snapshots.publish(declarations.toString(), declaration("Run"))
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(memberNames(file).contains("Open"))
        assertTrue(memberNames(file).contains("Run"))
        snapshots.publish(declarations.toString(), WinRTXamlSnapshotService.EMPTY_DECLARATIONS)
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        assertFalse(memberNames(file).contains("Open"))
        assertFalse(memberNames(file).contains("Run"))
    }

    private fun memberNames(file: KtFile): Set<String> = allowAnalysisOnEdt {
        analyze(file) { (file.declarations.single() as KtClass).namedClassSymbol!!.declaredMemberScope.callables
            .mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet() }
    }

    private fun supertypeNames(file: KtFile): List<String> = allowAnalysisOnEdt {
        analyze(file) { (file.declarations.single() as KtClass).namedClassSymbol!!.superTypes.map { it.toString() } }
    }

    private fun nameConflictDiagnostics(file: KtFile): List<String> = allowAnalysisOnEdt {
        analyze(file) { file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            .map { it.defaultMessage }.filter { it.contains("XAML x:Name") } }
    }

    private fun declaration(name: String) = """{
      "SchemaVersion":3,"Resources":[],"Pages":[{
        "ClassName":"sample.Shell","ResourcePath":"Shell.xaml","BaseTypeName":"Microsoft.UI.Xaml.Controls.Page",
        "IsApplication":false,"Features":["named-elements"],"Connections":[{
          "Id":1,"TypeName":"Microsoft.UI.Xaml.Controls.Button","FieldName":"$name",
          "Location":{"Line":2,"Column":3},"Events":[]
        }]
      }]
    }"""

    override fun tearDown() {
        try {
            fixtureDirectory?.let { path ->
                project.service<WinRTProjectService>().replaceBuildModels(path.toString(), emptyList())
                LocalFileSystem.getInstance().findFileByNioFile(path)?.let { PsiTestUtil.removeContentEntry(module, it) }
                check(path.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                check(path.fileName.toString().startsWith("winrt-ide-fir-"))
                FileUtil.delete(path.toFile())
            }
        } finally {
            super.tearDown()
        }
    }
}
