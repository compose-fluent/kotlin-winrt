package io.github.composefluent.winrt.ide

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.serialization.json.*
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

/** Optional Windows integration fixture from a prepared declaration pass; never edits its source or outputs. */
class WinRTXamlDocumentCompilerTest : BasePlatformTestCase() {
    fun testUnsavedNameIsHarvestedByTheRealXamlCompilerWithoutChangingTheOriginalFile() {
        val inputProperty = System.getProperty("winrt.ide.xamlInput")
        val compilerProperty = System.getProperty("winrt.ide.xamlCompiler")
        assumeTrue("Requires prepared XAMLC input and compiler directory", inputProperty != null && compilerProperty != null)
        val prepared = Path.of(inputProperty)
        val input = Json.parseToJsonElement(Files.readString(prepared)).jsonObject
        val index = WinRTXamlDeclarations.parse(Files.readString(prepared.parent.resolve("declarations.json")))
        val pages = input.getValue("XamlPages").jsonArray.associateBy { it.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content }
        val candidate = index.pages.asSequence().flatMap { page ->
            val item = pages[page.resourcePath] ?: return@flatMap emptySequence()
            val source = item.jsonObject.getValue("FullPath").jsonPrimitive.content
            val text = Files.readString(Path.of(source))
            page.connections.asSequence().filter { !it.isTemplateChild && it.fieldName != null }.mapNotNull { connection ->
                val name = connection.fieldName!!
                if (Regex(Regex.escape(name)).findAll(text).count() == 1 && text.contains("x:Name=\"$name\""))
                    Triple(item, source, name) else null
            }
        }.first()
        val original = Files.readString(Path.of(candidate.second))
        val root = Files.createTempDirectory("winrt-ide-document-")
        try {
            val source = root.resolve("Page.xaml")
            Files.writeString(source, original)
            val inputFile = root.resolve("input.json")
            val item = JsonObject(candidate.first.jsonObject + mapOf("FullPath" to JsonPrimitive(source.toString()), "ItemSpec" to JsonPrimitive(source.toString())))
            Files.writeString(inputFile, JsonObject(input + ("XamlPages" to JsonArray(listOf(item)))).toString())
            val declarations = root.resolve("declarations.json")
            Files.writeString(declarations, WinRTXamlDeclarations.canonicalText(index.copy(
                pages = index.pages.filter { it.resourcePath == candidate.first.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content }, resources = emptyList())))
            val compilation = WinRTXamlCompilationData("analyzeWinRTXaml", listOf(root.toString()), declarations.toString(), inputFile.toString(), compilerProperty, "")
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(
                WinRTModuleData(":", root.toString(), root.resolve("build").toString(), "2.4.0", "",
                    emptyList(), emptyList(), emptyList(), emptyList(), listOf(compilation)),
            ))
            val snapshots = project.service<WinRTXamlSnapshotService>()
            val key = WinRTXamlSnapshotService.key(declarations.toString())
            PlatformTestUtil.waitWithEventsDispatching("Initial declarations", { snapshots.state.value[key] != null }, 10)
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(source.toFile())!!
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)!!
            val updated = document.text.replace("x:Name=\"${candidate.third}\"", "x:Name=\"IdeUnsavedElement\"")
            ApplicationManager.getApplication().runWriteAction { document.setText(updated) }
            PlatformTestUtil.waitWithEventsDispatching("Live XAML declarations", {
                val value = snapshots.state.value[key]
                value?.error != null || value?.declarations?.pages?.flatMap { it.connections }?.any { it.fieldName == "IdeUnsavedElement" } == true
            }, 30)
            val value = snapshots.state.value.getValue(key)
            assertNull(value.error, value.error)
            val names = value.declarations.pages.flatMap { it.connections }.mapNotNull { it.fieldName }
            assertTrue(names.contains("IdeUnsavedElement"))
            assertFalse(names.contains(candidate.third))
            assertEquals(original, Files.readString(source))
            assertEquals(original, Files.readString(Path.of(candidate.second)))
            ApplicationManager.getApplication().runWriteAction { document.setText("<Page") }
            PlatformTestUtil.waitWithEventsDispatching("Invalid markup clears stale declarations", { snapshots.state.value[key]?.error != null }, 30)
            assertTrue(snapshots.state.value.getValue(key).declarations.pages.isEmpty())
            assertEquals(original, Files.readString(source))
            FileDocumentManager.getInstance().reloadFromDisk(document)
            Files.list(PathManager.getSystemDir().resolve("kotlin-winrt/xaml/${project.locationHash}")).use { assertEquals(0L, it.count()) }
        } finally {
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), emptyList())
            check(root.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
            check(root.fileName.toString().startsWith("winrt-ide-document-"))
            FileUtil.delete(root.toFile())
        }
    }
}
