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
        val metadata = myFixture.addFileToProject("Controls.winmd", "")
        io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter.writeXamlSchemaWinmd("Controls", listOf(
            io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.Page"),
            io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Controls.TextBlock"),
            io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.Media.SolidColorBrush"),
            io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeDescriptor("Microsoft.UI.Xaml.ResourceDictionary"),
        ), mapOf("Microsoft.UI.Xaml.Controls.TextBlock" to io.github.composefluent.winrt.metadata.WinRTXamlApplicationTypeMembers(
            properties = listOf("Foreground", "Style", "Tag").map { property -> io.github.composefluent.winrt.metadata.WinRTXamlApplicationProperty(
                property, io.github.composefluent.winrt.metadata.WinRTTypeRef.named("String")) })),
            java.nio.file.Path.of(metadata.virtualFile.path), emptyMap())
        val metadataInput = myFixture.addFileToProject("resource-editor-input.json", kotlinx.serialization.json.buildJsonObject {
            put("ReferenceAssemblies", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.buildJsonObject {
                put("FullPath", kotlinx.serialization.json.JsonPrimitive(metadata.virtualFile.path))
            }) })
        }.toString())
        val module = WinRTModuleData(":", directory, "$directory/build", "2.4.0", "", listOf(
            WinRTSourceSetData("main", listOf(directory), emptyList(), listOf("$directory/assets"))),
            emptyList(), emptyList(), emptyList(), listOf(WinRTXamlCompilationData("analyze", listOf(directory), "", metadataInput.virtualFile.path, "", "")))
        val index = project.service<WinRTResourceIndex>()
        val catalog = project.service<io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService>()
        project.service<WinRTProjectService>().replaceBuildModels(directory, listOf(module))
        PlatformTestUtil.waitWithEventsDispatching("Resource files indexed", {
            index.forFile(file.virtualFile.path)?.entries?.size == 4
        }, 10)
        PlatformTestUtil.waitWithEventsDispatching("WinMD catalog indexed", { catalog.forFile(file.virtualFile.path) != null }, 10)
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

    fun testStaticAndThemeResourceColorsAndDiagnosticsUseOnlyTheKeySpan() {
        val file = configure("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Page.Resources><SolidColorBrush x:Key="Accent.Brush"/></Page.Resources><TextBlock Foreground="{StaticResource Accent.Brush}"/><TextBlock Foreground="{ThemeResource ResourceKey=Accent.Brush}"/><TextBlock Foreground="{ThemeResource Missing}"/></Page>""")
        val values = file.rootTag!!.findSubTags("TextBlock").map { it.getAttribute("Foreground")!!.valueElement!! }
        for (value in values) assertNotNull(value.text, io.github.composefluent.winrt.ide.xaml.WinRTXamlMarkupHighlighting.parse(value)?.resourceKey)
        assertEquals(1, WinRTResourceReferences.reference(values[0])!!.multiResolve(false).size)
        assertEquals(1, WinRTResourceReferences.reference(values[1])!!.multiResolve(false).size)
        assertEquals(0, WinRTResourceReferences.reference(values[2])!!.multiResolve(false).size)
        val highlights = myFixture.doHighlighting()
        val resolved = highlights.filter { it.forcedTextAttributesKey == io.github.composefluent.winrt.ide.xaml.WinRTXamlMarkupColors.RESOURCE }
        assertEquals(highlights.map { "${it.forcedTextAttributesKey}: ${it.description}" }.toString(), 2, resolved.size)
        assertTrue(resolved.all { file.text.substring(it.startOffset, it.endOffset) == "Accent.Brush" })
        val extensions = highlights.filter { it.forcedTextAttributesKey == io.github.composefluent.winrt.ide.xaml.WinRTXamlMarkupColors.EXTENSION }
        assertEquals(setOf("StaticResource", "ThemeResource"), extensions.map { file.text.substring(it.startOffset, it.endOffset) }.toSet())
        val unknown = highlights.single { it.description?.startsWith("No resource source candidate") == true }
        assertEquals("Missing", file.text.substring(unknown.startOffset, unknown.endOffset))
        val key = file.rootTag!!.findSubTags("TextBlock")[0].getAttribute("Foreground")!!.valueElement!!
        val reference = WinRTResourceReferences.reference(key)!!
        assertEquals("Accent.Brush", reference.rangeInElement.substring(key.text))
        WriteCommandAction.runWriteCommandAction(project) { reference.handleElementRename("RenamedAccent") }
        assertTrue(file.text.contains("{StaticResource RenamedAccent}"))
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

    fun testFrameworkKeysAndXamlCompilerLinksResolveWithoutStaging() {
        val dictionary = myFixture.addFileToProject("physical/Styles.xaml", """<ResourceDictionary xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><SolidColorBrush x:Key="LinkedAccent"/></ResourceDictionary>""")
        val generic = myFixture.addFileToProject("winui/lib/native/Microsoft.UI/Themes/generic.xaml", """<ResourceDictionary xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Style x:Key="TitleTextBlockStyle"/><ResourceDictionary.ThemeDictionaries><ResourceDictionary x:Key="Default"><SolidColorBrush x:Key="SolidBackgroundFillColorBaseBrush"/></ResourceDictionary></ResourceDictionary.ThemeDictionaries></ResourceDictionary>""")
        val winmd = myFixture.addFileToProject("winui/metadata/Microsoft.UI.Xaml.winmd", "")
        val system = myFixture.addFileToProject("sdk/DesignTime/CommonConfiguration/Neutral/UAP/10.0.26100.0/Generic/themeresources.xaml", """<ResourceDictionary xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Color x:Key="SystemColorWindowColor">Black</Color></ResourceDictionary>""")
        val contract = myFixture.addFileToProject("sdk/References/10.0.26100.0/Windows.Foundation/1.0.0.0/Windows.Foundation.winmd", "")
        val app = myFixture.addFileToProject("App.xaml", """<Application xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Application.Resources><ResourceDictionary><ResourceDictionary.MergedDictionaries><ResourceDictionary Source="ms-appx:///Logical/Theme.xaml"/></ResourceDictionary.MergedDictionaries></ResourceDictionary></Application.Resources></Application>""")
        val input = myFixture.addFileToProject("input.json", kotlinx.serialization.json.buildJsonObject {
            put("ReferenceAssemblies", kotlinx.serialization.json.buildJsonArray {
                listOf(winmd, contract).forEach { reference -> add(kotlinx.serialization.json.buildJsonObject { put("FullPath", kotlinx.serialization.json.JsonPrimitive(reference.virtualFile.path)) }) }
            })
            put("XamlPages", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("FullPath", kotlinx.serialization.json.JsonPrimitive(dictionary.virtualFile.path)); put("MSBuild_Link", kotlinx.serialization.json.JsonPrimitive("Logical/Theme.xaml"))
                })
            })
        }.toString())
        val file = configure("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"><Page.Resources><ResourceDictionary><StaticResource x:Key="Alias" ResourceKey="SolidBackgroundFillColorBaseBrush"/></ResourceDictionary></Page.Resources><TextBlock Style="{StaticResource TitleTextBlockStyle}" Foreground="{ThemeResource SystemColorWindowColor}" Tag="{StaticResource LinkedAccent}"/></Page>""")
        val projects = project.service<WinRTProjectService>()
        val module = projects.modules.value.single().let { original -> original.copy(xamlCompilations = original.xamlCompilations.map { it.copy(inputFile = input.virtualFile.path) }) }
        val index = project.service<WinRTResourceIndex>()
        val lookup = index.read(module, listOf(module))
        assertEquals(setOf(generic.virtualFile.path, system.virtualFile.path), lookup.frameworkDictionaries.map { it.replace('\\', '/') }.toSet())
        projects.replaceBuildModels(module.projectDirectory, listOf(module))
        PlatformTestUtil.waitWithEventsDispatching("Framework dictionaries indexed", {
            index.forFile(file.virtualFile.path)?.frameworkDictionaries?.size == 2
        }, 10)
        val text = file.rootTag!!.findFirstSubTag("TextBlock")!!
        fun target(value: XmlAttributeValue) = WinRTResourceReferences.reference(value)!!.multiResolve(false).single().element!!
        assertEquals(generic, target(text.getAttribute("Style")!!.valueElement!!).containingFile)
        assertEquals(system, target(text.getAttribute("Foreground")!!.valueElement!!).containingFile)
        assertEquals(dictionary, target(text.getAttribute("Tag")!!.valueElement!!).containingFile)
        val alias = file.rootTag!!.findFirstSubTag("Page.Resources")!!.subTags.single().subTags.single()
        assertEquals(generic, target(alias.getAttribute("ResourceKey")!!.valueElement!!).containingFile)
        val source = (app as XmlFile).rootTag!!.subTags.single().subTags.single().subTags.single().subTags.single().getAttribute("Source")!!.valueElement!!
        assertEquals(dictionary, target(source))
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.setText(document.text.replace("TitleTextBlockStyle}", "}")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.editor.caretModel.moveToOffset(document.text.indexOf("{StaticResource ") + "{StaticResource ".length)
        myFixture.complete(CompletionType.BASIC)
        assertTrue(myFixture.lookupElementStrings.toString(), myFixture.lookupElementStrings.orEmpty().containsAll(
            listOf("TitleTextBlockStyle", "SolidBackgroundFillColorBaseBrush", "SystemColorWindowColor", "LinkedAccent")))
    }
}
