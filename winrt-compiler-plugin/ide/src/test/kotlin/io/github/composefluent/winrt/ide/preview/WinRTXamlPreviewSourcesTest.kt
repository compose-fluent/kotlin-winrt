package io.github.composefluent.winrt.ide.preview

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.resources.WinRTResourceIndex
import java.nio.file.Files
import java.nio.file.Path

class WinRTXamlPreviewSourcesTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()
    private var importedRoot: String? = null
    override fun tearDown() {
        try { importedRoot?.let { project.service<WinRTProjectService>().replaceBuildModels(it, emptyList()) } }
        finally { super.tearDown() }
    }

    fun testUnbuiltSourcesUnsavedDictionariesAndPassiveAssetsUseTheOwnedDesignerDirectory() {
        val ns = """xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" """
        val page = myFixture.addFileToProject("src/main/kotlin/UI/Page.xaml", """<Page $ns/>""").virtualFile
        val colors = myFixture.addFileToProject("src/main/kotlin/Styles/Colors.xaml", """<ResourceDictionary $ns x:Class="sample.Colors"><SolidColorBrush x:Key="Accent" Color="Blue"/></ResourceDictionary>""").virtualFile
        val app = myFixture.addFileToProject("src/main/kotlin/App.xaml", """<Application $ns><Application.Resources><ResourceDictionary Source="Styles/Colors.xaml"/></Application.Resources></Application>""").virtualFile
        val asset = myFixture.addFileToProject("src/main/appxResources/Assets/Logo.png", "passive asset").virtualFile
        val root = myFixture.tempDirFixture.getFile("")!!.path
        importedRoot = root
        val host = "$root/build/kotlin-winrt/xaml-sdk-preview/host"
        val module = WinRTModuleData(":app", root, "$root/build", "2.4.0", "", listOf(
            WinRTSourceSetData("main", listOf("$root/src/main/kotlin"), emptyList(), listOf("$root/src/main/appxResources"))),
            emptyList(), emptyList(), emptyList(), staticPreview = WinRTStaticPreviewData("runWinRTXamlSdkPreview", "$host/KotlinWinRTXamlPreview.exe", host, "$host/../references.json"))
        val index = project.service<WinRTResourceIndex>()
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(module))
        PlatformTestUtil.waitWithEventsDispatching("Source resources indexed without XAMLC", { index.forFile(page.path)?.entries?.any { it.source.replace('\\', '/') == asset.path } == true }, 10)
        val editorDocument = FileDocumentManager.getInstance().getDocument(colors)!!
        WriteCommandAction.runWriteCommandAction(project) { editorDocument.setText(editorDocument.text.replace("Blue", "Tomato")) }
        val sources = WinRTXamlPreviewSources(project, module, listOf(page.path, colors.path, app.path), page.path)
        assertEquals("UI/Page.xaml", sources.target(page.path))
        assertEquals(app.path, sources.application())
        val relative = sources.resolve("../Styles/Colors.xaml", sources.target(page.path))!!
        assertTrue(relative.text.contains("Color=\"Tomato\""))
        assertEquals(relative, sources.resolve("ms-appx:///Styles/Colors.xaml", "UI/Page.xaml"))
        assertEquals(relative, sources.resolve("using:sample.Colors", "UI/Page.xaml"))
        assertNull(sources.resolve("../../../outside.xaml", "UI/Page.xaml"))
        sources.stageAssets()
        assertEquals("passive asset", Files.readString(Path.of(host, "Assets/Logo.png")))
        assertFalse(Files.exists(Path.of(root, "build/classes/kotlin")))
    }
}
