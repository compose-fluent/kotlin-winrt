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
import java.nio.file.Files
import java.nio.file.Path

/** Uses the real XML completion/PSI pipeline and the existing WinMD writer/loader. */
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
            .first { it.resolve() is KtNamedFunction }
        assertEquals("onClick", (handler.resolve() as KtNamedFunction).name)
        WriteCommandAction.runWriteCommandAction(project) { handler.handleElementRename("onPressed") }
        assertEquals("onPressed", handlerValue.value)
        assertTrue(file.text.contains("x:Class=\"sample.Shell\""))
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
