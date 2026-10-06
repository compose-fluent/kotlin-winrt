package io.github.composefluent.winrt.ide

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.xaml.*
import io.github.composefluent.winrt.metadata.*
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameProcessor
import java.nio.file.Files
import java.nio.file.Path

/** Uses the real XML completion/PSI pipeline and the existing WinMD writer/loader. */
@OptIn(KaAllowAnalysisOnEdt::class)
class WinRTXamlEditorTest : BasePlatformTestCase() {
    private var root: Path? = null

    private fun configure(markup: String): XmlFile {
        myFixture.addFileToProject("Shell.kt", "package sample\nclass Shell { fun onClick(sender: Any, args: Any) {} }")
        myFixture.addFileToProject("Widget.kt", "package sample\nclass Widget { var Label: String = \"\" }")
        val file = myFixture.configureByText("Shell.xaml", markup) as XmlFile
        val directory = Files.createTempDirectory("winrt-ide-editor-").also { root = it }
        val metadata = directory.resolve("Controls.winmd")
        // Corresponds to cswinrt's normalized type/member view. No IDE-only
        // fake control registry is used to make XML completion pass.
        WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Controls", listOf(
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Control"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Button", "Microsoft.UI.Xaml.Controls.Control"),
            WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Visibility", enumEntries = listOf("Visible", "Collapsed")),
        ), mapOf(
            "Microsoft.UI.Xaml.Controls.Control" to WinRTXamlApplicationTypeMembers(properties = listOf(
                WinRTXamlApplicationProperty("Width", WinRTTypeRef.named("Double")),
                WinRTXamlApplicationProperty("Visibility", WinRTTypeRef.named("Microsoft.UI.Xaml.Visibility")),
            )),
            "Microsoft.UI.Xaml.Controls.Button" to WinRTXamlApplicationTypeMembers(
                properties = listOf(WinRTXamlApplicationProperty("Content", WinRTTypeRef.named("String"))),
                events = listOf(WinRTXamlApplicationEvent("Click", WinRTTypeRef.named("Sample.ClickHandler"))),
            ),
        ), metadata, mapOf("Sample.ClickHandler" to "Sample", "Windows.Foundation.EventRegistrationToken" to "Windows.Foundation.FoundationContract"))
        val input = directory.resolve("input.json")
        Files.writeString(input, buildJsonObject { put("ReferenceAssemblies", buildJsonArray {
            add(buildJsonObject { put("FullPath", metadata.toString()) })
        }) }.toString())
        val sourceRoot = file.originalFile.virtualFile.parent.path
        project.service<WinRTProjectService>().replaceBuildModels(directory.toString(), listOf(
            WinRTModuleData(":", directory.toString(), directory.resolve("build").toString(), "2.4.0", "",
                emptyList(), emptyList(), emptyList(), emptyList(), listOf(
                    WinRTXamlCompilationData("analyzeWinRTXaml", listOf(sourceRoot), directory.resolve("declarations.json").toString(), input.toString(), "", ""),
                )),
        ))
        val catalogs = project.service<WinRTXamlCatalogService>()
        PlatformTestUtil.waitWithEventsDispatching("WinMD catalog", { catalogs.forFile(file.originalFile.virtualFile.path) != null }, 10)
        val resources = project.service<io.github.composefluent.winrt.ide.resources.WinRTResourceIndex>()
        PlatformTestUtil.waitWithEventsDispatching("Resource index initialized", {
            resources.forFile(file.originalFile.virtualFile.path)?.module?.projectDirectory == directory.toString()
        }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        return file
    }

    fun testXmlHighlightingAndInheritedMembersComeFromWinmd() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" <caret>/>""")
        assertEquals("WinRT XAML", com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByExtension("xaml").name)
        val names = file.rootTag!!.descriptor!!.getAttributesDescriptors(file.rootTag).map { it.name }.toSet()
        assertTrue(names.toString(), names.containsAll(listOf("Content", "Width", "Click", "Visibility", "x:Name")))
        myFixture.complete(CompletionType.BASIC)
        val variants = myFixture.lookupElementStrings.orEmpty()
        assertTrue(variants.toString(), variants.containsAll(listOf("Content", "Width", "Click")))
    }

    fun testTagAndEnumCompletionAndCustomTypeNavigation() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:local="using:sample" Visibility="<caret>"><local:Widget/></Button>""")
        assertEquals(listOf("Visible", "Collapsed"), file.rootTag!!.descriptor!!.getAttributeDescriptor("Visibility", file.rootTag)!!.enumeratedValues!!.toList())
        myFixture.complete(CompletionType.BASIC)
        assertTrue(myFixture.lookupElementStrings.toString(), myFixture.lookupElementStrings.orEmpty().containsAll(listOf("Visible", "Collapsed")))
        val child = file.rootTag!!.subTags.single()
        assertEquals("Widget", (child.descriptor!!.declaration as KtClass).name)
        val descriptors = file.rootTag!!.descriptor!!.getElementsDescriptors(file.rootTag).map { it.name }
        assertTrue(descriptors.toString(), descriptors.containsAll(listOf("Button", "local:Widget")))
    }

    fun testClassAndEventReferencesResolveAndParticipateInRename() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="onClick"/>""")
        val classValue = file.rootTag!!.getAttribute("Class", WinRTXamlCatalog.XAML)!!.valueElement!!
        val handlerValue = file.rootTag!!.getAttribute("Click")!!.valueElement!!
        val classReferences = PsiReferenceService.getService().getReferences(classValue, PsiReferenceService.Hints.NO_HINTS)
        assertTrue(classReferences.toString(), classReferences.any { (it.resolve() as? KtClass)?.name == "Shell" })
        val handler = PsiReferenceService.getService().getReferences(handlerValue, PsiReferenceService.Hints.NO_HINTS)
            .first { allowAnalysisOnEdt { it.resolve() } is KtNamedFunction }
        assertEquals("onClick", (allowAnalysisOnEdt { handler.resolve() } as KtNamedFunction).name)
        WriteCommandAction.runWriteCommandAction(project) { handler.handleElementRename("onPressed") }
        assertEquals("onPressed", handlerValue.value)
        assertTrue(file.text.contains("x:Class=\"sample.Shell\""))
    }

    fun testEventSignaturesUseTheClosedProjectedDelegateAndInheritedHandlers() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.EventPage" Click="inherited"/>""")
        myFixture.addFileToProject("Events.kt", """
            package sample
            open class Args
            class SpecificArgs : Args()
            fun interface ClickHandler<T> { fun invoke(sender: Any?, args: T) }
            open class EventBase { fun inherited(sender: Any?, args: Args) {} }
            class EventPage : EventBase() {
                fun good(sender: Any?, args: SpecificArgs) {}
                fun nonNullableSender(sender: Any, args: Args) {}
                fun wrongArgs(sender: Any?, args: String) {}
                fun wrongCount(sender: Any?) {}
                fun wrongReturn(sender: Any?, args: Args): Int = 1
                suspend fun suspending(sender: Any?, args: Args) {}
                fun <T> generic(sender: Any?, args: Args) {}
                fun overloaded(sender: Any?, args: Args) {}
                fun overloaded(sender: Any?) {}
            }
        """.trimIndent())
        myFixture.addFileToProject("Button.kt", """
            package microsoft.ui.xaml.controls
            class Button { fun addClick(handler: sample.ClickHandler<sample.SpecificArgs>) {} }
        """.trimIndent())
        val result = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!) }!!
        assertTrue(result.delegateAvailable)
        assertNull(result.problem("inherited"))
        assertNull(result.problem("good"))
        assertEquals("inherited", (result.target("inherited") as KtNamedFunction).name)
        for (name in listOf("nonNullableSender", "wrongArgs", "wrongCount", "wrongReturn", "suspending", "generic", "overloaded", "missing"))
            assertNotNull(name, result.problem(name))
        val reference = PsiReferenceService.getService().getReferences(file.rootTag!!.getAttribute("Click")!!.valueElement!!, PsiReferenceService.Hints.NO_HINTS)
            .first { allowAnalysisOnEdt { it.resolve() } is KtNamedFunction }
        assertEquals("inherited", (allowAnalysisOnEdt { reference.resolve() } as KtNamedFunction).name)
    }

    fun testEventCompletionOmitsIncompatibleAndOverloadedMethods() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Page" Click="<caret>"/>""")
        myFixture.addFileToProject("Page.kt", """
            package sample
            fun interface ClickHandler { fun invoke(sender: Any?, args: String) }
            class Page {
                fun valid(sender: Any?, args: Any) {}
                fun invalid(sender: Any, args: String) {}
                fun overloaded(sender: Any?, args: String) {}
                fun overloaded(sender: Any?) {}
            }
        """.trimIndent())
        myFixture.addFileToProject("Button.kt", "package microsoft.ui.xaml.controls\nclass Button { fun addClick(handler: sample.ClickHandler) {} }")
        allowAnalysisOnEdt { myFixture.complete(CompletionType.BASIC) }
        val variants = myFixture.lookupElementStrings.orEmpty()
        // IntelliJ auto-inserts a sole candidate; both outcomes exercise native completion.
        assertTrue(variants.toString(), "valid" in variants || file.text.contains("Click=\"valid\""))
        assertFalse(variants.toString(), variants.any { it == "invalid" || it == "overloaded" })
    }

    fun testNativeHighlightingReportsTheEventContractAtTheAttributeValue() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.BadPage" Click="bad"/>""")
        myFixture.addFileToProject("BadPage.kt", "package sample\nclass BadPage { fun bad(sender: Any?, args: Any?): Int = 1 }")
        val diagnostics = allowAnalysisOnEdt { myFixture.doHighlighting() }
            .filter { it.description?.contains("must return Unit") == true }
        assertEquals(diagnostics.toString(), 1, diagnostics.size)
        assertEquals(file.rootTag!!.getAttribute("Click")!!.valueElement!!.valueTextRange,
            com.intellij.openapi.util.TextRange(diagnostics.single().startOffset, diagnostics.single().endOffset))
    }

    fun testNativeHandlerAndClassRenameUpdateXamlAndItsRequiredFilePair() {
        val file = configure("""<Button xmlns="${WinRTXamlCatalog.PRESENTATION}" xmlns:x="${WinRTXamlCatalog.XAML}" x:Class="sample.Shell" Click="onClick"/>""")
        val handlerValue = file.rootTag!!.getAttribute("Click")!!.valueElement!!
        val handler = allowAnalysisOnEdt { WinRTXamlEventAnalysis.forAttribute(file.rootTag!!.getAttribute("Click")!!)!!.target("onClick") }!!
        val usages = allowAnalysisOnEdt { ReferencesSearch.search(handler).findAll() }
        assertTrue(usages.toString(), usages.any { it.element == handlerValue })
        allowAnalysisOnEdt { RenameProcessor(project, handler, "onPressed", false, false).run() }
        assertEquals("onPressed", file.rootTag!!.getAttributeValue("Click"))
        val owner = WinRTXamlSymbols.ownerClass(file.rootTag!!)!!
        allowAnalysisOnEdt { RenameProcessor(project, owner, "RenamedShell", false, false).run() }
        assertEquals("sample.RenamedShell", file.rootTag!!.getAttributeValue("Class", WinRTXamlCatalog.XAML))
        assertEquals("RenamedShell.kt", owner.containingFile.name)
        assertEquals("RenamedShell.xaml", file.name)
    }

    override fun tearDown() {
        try {
            root?.let { directory ->
                project.service<WinRTProjectService>().replaceBuildModels(directory.toString(), emptyList())
                check(directory.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
                check(directory.fileName.toString().startsWith("winrt-ide-editor-"))
                FileUtil.delete(directory.toFile())
            }
        } finally { super.tearDown() }
    }
}
