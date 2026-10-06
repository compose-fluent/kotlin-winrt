package io.github.composefluent.winrt.ide

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import java.nio.file.Files
import java.nio.file.Path

/** BindingPath.g4 and XamlCompiledBindingBodies are the reference contracts.
 * Uses real XML references/completion, WinMD and substituted IDE types.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
class WinRTXamlBindingTest : BasePlatformTestCase() {
    private var directory: Path? = null
    private fun configure(content: String): XmlFile {
        myFixture.addFileToProject("Models.kt", """
            package sample
            open class ModelBase { val inherited: String = "" }
            class Person : ModelBase() { val name: String = ""; val other: String = "" }
            class Box<T>(val item: T)
            class Page {
                val model: Box<Person> = Box(Person())
                val items: List<Person> = emptyList()
                fun format(person: Person): String = person.name
                fun pressed(sender: Any?, args: Any?) {}
            }
            class Tools { companion object { val selected: Person = Person(); fun GetLabel(target: Any): String = "" } }
        """.trimIndent())
        myFixture.addFileToProject("Controls.kt", "package microsoft.ui.xaml.controls\nclass Button { var content: String = \"\"; var width: Double = 0.0 }")
        val file = myFixture.configureByText("Page.xaml", """<Panel xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" xmlns:local="using:sample" x:Class="sample.Page">$content</Panel>""") as XmlFile
        val root = Files.createTempDirectory("winrt-ide-bindings-").also { directory = it }
        val winmd = root.resolve("Controls.winmd")
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Controls", listOf(
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Panel"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Button"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.FrameworkTemplate"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.DataTemplate", "Microsoft.UI.Xaml.FrameworkTemplate"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.ControlTemplate", "Microsoft.UI.Xaml.FrameworkTemplate"),
        ), mapOf("Microsoft.UI.Xaml.Controls.Button" to WinRTXamlApplicationTypeMembers(properties = listOf(
            WinRTXamlApplicationProperty("Content", WinRTTypeRef.named("String")),
            WinRTXamlApplicationProperty("Width", WinRTTypeRef.named("Double")),
        ))), winmd, emptyMap())
        val input = root.resolve("input.json")
        Files.writeString(input, buildJsonObject { put("ReferenceAssemblies", buildJsonArray {
            add(buildJsonObject { put("FullPath", winmd.toString()) })
        }) }.toString())
        project.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(
            WinRTModuleData(":", root.toString(), root.resolve("build").toString(), "2.4.0", "", emptyList(), emptyList(), emptyList(), emptyList(),
                listOf(WinRTXamlCompilationData("analyze", listOf(file.virtualFile.parent.path), root.resolve("declarations.json").toString(), input.toString(), "", ""))),
        ))
        val catalogs = project.service<WinRTXamlCatalogService>()
        PlatformTestUtil.waitWithEventsDispatching("WinMD", { catalogs.forFile(file.virtualFile.path) != null }, 10)
        val resources = project.service<WinRTResourceIndex>()
        PlatformTestUtil.waitWithEventsDispatching("Resource index", { resources.forFile(file.virtualFile.path)?.module?.projectDirectory == root.toString() }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertNotNull("Kotlin owner indexed in ${WinRTXamlSymbols.classNames(file)}", WinRTXamlSymbols.ownerClass(file.rootTag!!))
        return file
    }

    private fun value(file: XmlFile, attribute: String = "Content") =
        PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java).first { it.localName == attribute }.valueElement!!
    private fun analysis(value: XmlAttributeValue) = allowAnalysisOnEdt { WinRTXamlBindingAnalysis.forValue(value) }!!

    fun testNestedGenericAndInheritedPathsResolveToKotlinDeclarations() {
        val file = configure("""<Button Content="{x:Bind model.item.inherited}"/>""")
        val result = analysis(value(file))
        assertTrue(result.sites.toString(), result.sites.all { it.problem == null })
        assertEquals(result.toString(), listOf("model", "item", "inherited"), result.sites.map { (it.target as? KtNamedDeclaration)?.name })
        val refs = PsiReferenceService.getService().getReferences(value(file), PsiReferenceService.Hints.NO_HINTS)
        assertEquals(setOf("model", "item", "inherited"), refs.mapNotNull { allowAnalysisOnEdt { it.resolve() } as? KtNamedDeclaration }.map { it.name }.toSet())
    }

    fun testCompletionTraversesTheReceiverAndKeepsTheExistingPath() {
        val file = configure("""<Button Content="{x:Bind model.item.<caret>}"/>""")
        allowAnalysisOnEdt { myFixture.complete(CompletionType.BASIC) }
        assertTrue("${myFixture.lookupElementStrings}: ${file.text}", myFixture.lookupElementStrings.orEmpty().containsAll(listOf("name", "other", "inherited")))
        assertTrue(file.text.contains("{x:Bind model.item."))
    }

    fun testOnlyCompiledPathsAreDiagnosedForUnknownMembers() {
        val file = configure("""<Button Content="{x:Bind model.item.missing}"/><Button Content="{Binding model.item.missing}"/>""")
        val values = PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java).filter { it.localName == "Content" }.map { it.valueElement!! }
        assertTrue(analysis(values[0]).sites.any { it.problem == "Unresolved x:Bind member 'missing'." })
        assertTrue(analysis(values[1]).sites.all { it.problem == null && it.target == null })
    }

    fun testTemplateDataTypeAndNamesDoNotLeakAcrossScopes() {
        val file = configure("""<Button x:Name="Outer"/>
            <DataTemplate x:DataType="local:Person"><Panel><Button x:Name="Same"/><Button Content="{x:Bind name}" Width="{Binding Path=width, ElementName=Same}"/></Panel></DataTemplate>
            <DataTemplate x:DataType="local:Person"><Panel><Button x:Name="Same"/><Button Content="{x:Bind name}"/></Panel></DataTemplate>""".trimIndent())
        val templates = file.rootTag!!.findSubTags("DataTemplate")
        val panel = templates[0].subTags.single()
        val names = WinRTXamlScopes.namedElements(panel.subTags[1])
        assertEquals(listOf("Same"), names.map { it.first })
        assertEquals(panel.subTags[0], (names.single().second.parent as XmlAttribute).parent)
        val result = analysis(panel.subTags[1].getAttribute("Content")!!.valueElement!!)
        assertEquals("name", (result.sites.single().target as KtNamedDeclaration).name)
        val binding = analysis(panel.subTags[1].getAttribute("Width")!!.valueElement!!)
        assertEquals(names.single().second, binding.sites.first { it.name == "Same" }.target)
        assertEquals("width", (binding.sites.first { it.name == "width" }.target as KtNamedDeclaration).name)
    }

    fun testFunctionArgumentsAndEscapedStringArgumentsPreserveReferenceSpans() {
        val file = configure("""<Button Content="{x:Bind format(model.item)}"/>""")
        val result = analysis(value(file))
        assertEquals(setOf("format", "model", "item"), result.sites.map { it.name }.toSet())
        assertTrue(result.sites.toString(), result.sites.all { it.target != null && it.problem == null })
        for (site in result.sites) assertEquals(site.name, value(file).text.substring(site.range.startOffset, site.range.endOffset))
        val syntaxFile = myFixture.addFileToProject("Arguments.xaml", """<Button xmlns:x="${WinRTXamlCatalog.XAML}" Content="{x:Bind format(&quot;literal,ElementName=Wrong&quot;, model.item), Mode=OneWay}"/>""") as XmlFile
        val syntax = WinRTXamlBindingSyntax.parse(value(syntaxFile))!!
        assertTrue(syntax.toString(), syntax.complete)
        assertNull(syntax.elementName)
        assertEquals(2, syntax.expression!!.arguments.size)
        assertEquals("item", syntax.expression.arguments[1].token!!.text)
    }

    fun testFindUsagesAndNativeKotlinRenameUpdateOnlyResolvedCompiledPaths() {
        val file = configure("""<Button Content="{x:Bind model.item.name}"/><Button Content="{Binding model.item.name}"/>""")
        val consumer = myFixture.addFileToProject("Consumer.kt", "package sample\nfun read(page: Page) = page.model.item.name")
        val target = analysis(value(file)).sites.first { it.name == "model" }.target as KtProperty
        val references = allowAnalysisOnEdt { ReferencesSearch.search(target).findAll() }
        assertTrue(references.toString(), references.any { it.element.containingFile == file })
        assertTrue(references.toString(), references.any { it.element.containingFile == consumer })
        allowAnalysisOnEdt { RenameProcessor(project, target, "viewModel", false, false).run() }
        assertTrue(file.text, file.text.contains("{x:Bind viewModel.item.name}"))
        assertTrue(file.text, file.text.contains("{Binding model.item.name}"))
        assertTrue(consumer.text, consumer.text.contains("page.viewModel.item.name"))
    }

    fun testStaticCastIndexerAttachedAndBindBackReferencesUseTheCompilerLookupShape() {
        val file = configure("""<Button Content="{x:Bind local:Tools.selected.name}"/><Button Content="{x:Bind items[0].name}"/>
            <Button Content="{x:Bind ((local:Person)model.item).name}"/><Button Content="{x:Bind model.item.(local:Tools.Label)}"/>
            <Button Content="{x:Bind model.item.name, BindBack=format}"/>""".trimIndent())
        val values = PsiTreeUtil.findChildrenOfType(file, XmlAttribute::class.java).filter { it.localName == "Content" }.map { it.valueElement!! }
        for (item in values) {
            val result = analysis(item)
            assertTrue(result.toString(), result.sites.all { it.target != null && it.problem == null })
            for (site in result.sites) assertEquals(site.name, item.text.substring(site.range.startOffset, site.range.endOffset))
        }
        assertEquals("GetLabel", (analysis(values[3]).sites.last().target as KtNamedDeclaration).name)
    }

    fun testTemplateNameRenameUsesNativeUsagesAndPreservesTheOtherTemplate() {
        val file = configure("""<DataTemplate x:DataType="local:Person"><Panel><Button x:Name="Same"/><Button Content="{Binding ElementName=Same}"/></Panel></DataTemplate>
            <DataTemplate x:DataType="local:Person"><Panel><Button x:Name="Same"/><Button Content="{Binding ElementName=Same}"/></Panel></DataTemplate>""".trimIndent())
        val first = file.rootTag!!.findSubTags("DataTemplate")[0]
        val definition = first.subTags[0].subTags[0].getAttribute("Name", WinRTXamlCatalog.XAML)!!.valueElement!!
        val refs = allowAnalysisOnEdt { ReferencesSearch.search(definition).findAll() }
        assertEquals(refs.toString(), 1, refs.size)
        allowAnalysisOnEdt { RenameProcessor(project, definition, "Renamed", false, false).run() }
        assertTrue(first.text, first.text.contains("x:Name=\"Renamed\"") && first.text.contains("ElementName=Renamed"))
        val second = file.rootTag!!.findSubTags("DataTemplate")[1]
        assertTrue(second.text, second.text.contains("x:Name=\"Same\"") && second.text.contains("ElementName=Same"))
    }

    override fun tearDown() {
        try {
            directory?.let { root ->
                project.service<WinRTProjectService>().replaceBuildModels(root.toString(), emptyList())
                check(root.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                check(root.fileName.toString().startsWith("winrt-ide-bindings-"))
                FileUtil.delete(root.toFile())
            }
        } finally { super.tearDown() }
    }
}
