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
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import com.sun.nio.file.ExtendedOpenOption

class WinUIXamlPreviewSourcesTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture() = com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl()
    private var importedRoot: String? = null
    override fun tearDown() {
        try { importedRoot?.let { project.service<WinRTProjectService>().replaceBuildModels(it, emptyList()) } }
        finally { super.tearDown() }
    }

    fun testUnbuiltSourcesUnsavedDictionariesAndPassiveAssetsUseTheOwnedDesignerDirectory() = verifySources("host")

    fun testUwpDesignerStagesPassiveAssetsInItsIndependentPackage() = verifySources("AppX")

    private fun verifySources(directory: String) {
        val ns = """xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" """
        val page = myFixture.addFileToProject("src/main/kotlin/UI/Page.xaml", """<Page $ns/>""").virtualFile
        val colors = myFixture.addFileToProject("src/main/kotlin/Styles/Colors.xaml", """<ResourceDictionary $ns x:Class="sample.Colors"><SolidColorBrush x:Key="Accent" Color="Blue"/></ResourceDictionary>""").virtualFile
        val app = myFixture.addFileToProject("src/main/kotlin/App.xaml", """<Application $ns><Application.Resources><ResourceDictionary Source="Styles/Colors.xaml"/></Application.Resources></Application>""").virtualFile
        val asset = myFixture.addFileToProject("src/main/appxResources/Assets/Logo.png", "passive asset").virtualFile
        val root = myFixture.tempDirFixture.getFile("")!!.path
        importedRoot = root
        val host = "$root/build/kotlin-winrt/xaml-sdk-preview/$directory"
        val module = WinRTModuleData(":app", root, "$root/build", "2.4.0", "", listOf(
            WinRTSourceSetData("main", listOf("$root/src/main/kotlin"), emptyList(), listOf("$root/src/main/appxResources"))),
            emptyList(), emptyList(), emptyList(), staticPreview = WinRTStaticPreviewData("runWinRTXamlSdkPreview", "$host/KotlinWinUIXamlPreview.exe", host, "$host/../references.json"))
        val index = project.service<WinRTResourceIndex>()
        project.service<WinRTProjectService>().replaceBuildModels(root, listOf(module))
        PlatformTestUtil.waitWithEventsDispatching("Source resources indexed without XAMLC", { index.forFile(page.path)?.entries?.any { it.source.replace('\\', '/') == asset.path } == true }, 10)
        val editorDocument = FileDocumentManager.getInstance().getDocument(colors)!!
        WriteCommandAction.runWriteCommandAction(project) { editorDocument.setText(editorDocument.text.replace("Blue", "Tomato")) }
        val sources = WinUIXamlPreviewSources(project, module, listOf(page.path, colors.path, app.path), page.path)
        assertEquals("UI/Page.xaml", sources.target(page.path))
        assertEquals(app.path, sources.application())
        val relative = sources.resolve("../Styles/Colors.xaml", sources.target(page.path))!!
        assertTrue(relative.text.contains("Color=\"Tomato\""))
        assertEquals(relative, sources.resolve("ms-appx:///Styles/Colors.xaml", "UI/Page.xaml"))
        assertEquals(relative, sources.resolve("using:sample.Colors", "UI/Page.xaml"))
        assertNull(sources.resolve("../../../outside.xaml", "UI/Page.xaml"))
        sources.stageAssets()
        val staged = Path.of(host, "Assets/Logo.png")
        assertEquals("passive asset", Files.readString(staged))
        // Simulate an existing deployment and WinUI retaining its package icon.
        // A timestamp difference must not rewrite unchanged, locked bytes.
        val deploymentTime = FileTime.fromMillis(1_000)
        Files.setLastModifiedTime(staged, deploymentTime)
        Files.newByteChannel(staged, setOf<OpenOption>(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE)).use {
            sources.stageAssets()
            assertEquals(deploymentTime, Files.getLastModifiedTime(staged))
            assertEquals("passive asset", Files.readString(staged))
        }
        // The content check must also detect changes hidden by matching metadata.
        val input = Path.of(asset.path)
        Files.writeString(input, "changed asset")
        Files.setLastModifiedTime(input, deploymentTime)
        Files.newByteChannel(staged, setOf<OpenOption>(StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_WRITE, ExtendedOpenOption.NOSHARE_DELETE)).use {
            val failure = runCatching { sources.stageAssets() }.exceptionOrNull()
            assertTrue("A changed, locked asset must report its write failure", failure is java.nio.file.FileSystemException)
        }
        sources.stageAssets()
        assertEquals("changed asset", Files.readString(staged))
        assertFalse(Files.exists(Path.of(root, "build/classes/kotlin")))
        // Cache validity includes editor buffers and WinMD bytes, even before
        // the asynchronous source enumeration has supplied its file list.
        val metadata = Path.of(root, "preview.winmd")
        Files.writeString(metadata, "first metadata")
        val references = Path.of(root, "preview-references.json")
        Files.writeString(references, kotlinx.serialization.json.buildJsonObject {
            put("ReferenceAssemblies", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("FullPath", kotlinx.serialization.json.JsonPrimitive(metadata.toString()))
                })
            })
        }.toString())
        val cachedModule = module.copy(xamlCompilations = listOf(WinUIXamlCompilationData(
            "compileXaml", listOf("$root/src/main/kotlin"), "", references.toString(), "", "")))
        fun cacheKey() = WinUIXamlPreviewSources(project, cachedModule, emptyList(), page.path).projectDocumentKey()
        val originalKey = cacheKey()
        assertNotNull(originalKey)
        val pageDocument = FileDocumentManager.getInstance().getDocument(page)!!
        val originalText = pageDocument.text
        WriteCommandAction.runWriteCommandAction(project) { pageDocument.setText(originalText.replace("/>", " Width=\"123\"/>")) }
        assertFalse(originalKey == cacheKey())
        WriteCommandAction.runWriteCommandAction(project) { pageDocument.setText(originalText) }
        assertEquals(originalKey, cacheKey())
        val metadataTime = Files.getLastModifiedTime(metadata)
        Files.writeString(metadata, "other metadata")
        Files.setLastModifiedTime(metadata, metadataTime)
        assertFalse("Equal-length metadata with unchanged timestamps must invalidate the document", originalKey == cacheKey())
    }
}
