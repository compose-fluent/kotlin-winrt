package io.github.composefluent.winrt.ide

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiReferenceService
import com.intellij.psi.xml.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.resources.*

class WinRTResourceReferencesTest : BasePlatformTestCase() {
    private var importedRoot: String? = null
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()

    override fun tearDown() {
        try {
            importedRoot?.let { project.service<WinRTProjectService>().replaceBuildModels(it, emptyList()) }
        } finally { super.tearDown() }
    }
    private fun configure(text: String, name: String = "View.xaml"): XmlFile {
        val file = myFixture.addFileToProject(name, text) as XmlFile
        val directory = file.virtualFile.parent.path
        importedRoot = directory
        myFixture.addFileToProject("assets/Assets/Logo.scale-100.png", "probe")
        myFixture.addFileToProject("assets/Assets/Logo.scale-200.png", "probe")
        myFixture.addFileToProject("assets/Strings/en-US/Resources.resw", """<root><data name="AppName"><value>Application</value></data><data name="Greeting.Text"><value>Hello</value></data></root>""")
        myFixture.addFileToProject("assets/Strings/zh-CN/Resources.resw", """<root><data name="AppName"><value>应用</value></data></root>""")
        val module = WinRTModuleData(":", directory, "$directory/build", "2.4.0", "", listOf(
            WinRTSourceSetData("main", listOf(directory), emptyList(), listOf("$directory/assets"))),
            emptyList(), emptyList(), emptyList(), listOf(WinRTXamlCompilationData("analyze", listOf(directory), "", "", "", "")))
        val index = project.service<WinRTResourceIndex>()
        project.service<WinRTProjectService>().replaceBuildModels(directory, listOf(module))
        PlatformTestUtil.waitWithEventsDispatching("Resource files indexed", {
            index.forFile(file.virtualFile.path)?.entries?.size == 4
        }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return file
    }

    fun testManifestLogosAndLocalizedStringsResolveToSourceCandidates() {
        val file = configure("""<Package><Properties><DisplayName>ms-resource:AppName</DisplayName></Properties><VisualElements Square44x44Logo="Assets/Logo.png"/></Package>""", "AppxManifest.xml")
        val logo = file.rootTag!!.findFirstSubTag("VisualElements")!!.getAttribute("Square44x44Logo")!!.valueElement!!
        val references = PsiReferenceService.getService().getReferences(logo, PsiReferenceService.Hints.NO_HINTS)
        assertTrue(references.toString(), references.filterIsInstance<com.intellij.psi.PsiPolyVariantReference>().any { it.multiResolve(false).size == 2 })
        val display = file.rootTag!!.findFirstSubTag("Properties")!!.findFirstSubTag("DisplayName")!!.value.textElements.single()
        val targets = WinRTResourceReferences.reference(display)!!.multiResolve(false)
        assertEquals(2, targets.size)
        assertTrue(targets.all { (it.element as XmlAttributeValue).value == "AppName" })
    }

    fun testKeysUseLocalAndMergedDictionariesAndOfferCompletion() {
        myFixture.addFileToProject("Theme.xaml", """<ResourceDictionary xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><SolidColorBrush x:Key="MergedAccent"/></ResourceDictionary>""")
        val file = configure("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Page.Resources><ResourceDictionary><SolidColorBrush x:Key="Accent"/><SolidColorBrush x:Key="Alternate"/><ResourceDictionary.MergedDictionaries><ResourceDictionary Source="ms-appx:///Theme.xaml"/></ResourceDictionary.MergedDictionaries></ResourceDictionary></Page.Resources><TextBlock x:Uid="Greeting" Foreground="{StaticResource MergedAccent}"/></Page>""")
        val text = file.rootTag!!.findFirstSubTag("TextBlock")!!
        val key = text.getAttribute("Foreground")!!.valueElement!!
        assertEquals("MergedAccent", (WinRTResourceReferences.reference(key)!!.multiResolve(false).single().element as XmlAttributeValue).value)
        assertEquals("Greeting.Text", (WinRTResourceReferences.reference(text.getAttribute("Uid", "http://schemas.microsoft.com/winfx/2006/xaml")!!.valueElement!!)!!
            .multiResolve(false).single().element as XmlAttributeValue).value)
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("MergedAccent}", "}")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(document.text.indexOf("{StaticResource ") + "{StaticResource ".length)
        myFixture.complete(CompletionType.BASIC)
        assertTrue(myFixture.lookupElementStrings.toString(), myFixture.lookupElementStrings.orEmpty().containsAll(listOf("Accent", "Alternate", "MergedAccent")))
    }

    fun testReferencesReadUnsavedReswAndPreserveUriOnExactFileRename() {
        val file = configure("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><Image Source="ms-appx:///Assets/Logo.scale-100.png"/><TextBlock Text="ms-resource:///Resources/AppName"/></Page>""")
        val source = file.rootTag!!.subTags[0].getAttribute("Source")!!.valueElement!!
        val reference = WinRTResourceReferences.reference(source)!!
        assertEquals("Logo.scale-100.png", reference.multiResolve(false).single().element!!.containingFile.name)
        WriteCommandAction.runWriteCommandAction(project) { reference.handleElementRename("NewLogo.scale-100.png") }
        assertTrue(file.text.contains("ms-appx:///Assets/NewLogo.scale-100.png"))
        val resource = myFixture.tempDirFixture.getFile("assets/Strings/en-US/Resources.resw")!!
        val psi = com.intellij.psi.PsiManager.getInstance(project).findFile(resource)!!
        val document = PsiDocumentManager.getInstance(project).getDocument(psi)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("AppName", "AppTitle")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val localized = file.rootTag!!.subTags[1].getAttribute("Text")!!.valueElement!!
        assertEquals(1, WinRTResourceReferences.reference(localized)!!.multiResolve(false).size)
    }
}
