package io.github.composefluent.winrt.ide

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.facet.FacetManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.analysis.WinRTXamlSnapshotService
import io.github.composefluent.winrt.ide.analysis.WinRTXamlDocumentCompiler
import io.github.composefluent.winrt.ide.analysis.WinRTXamlDocument
import io.github.composefluent.winrt.ide.gradle.WinRTModuleData
import io.github.composefluent.winrt.ide.gradle.WinRTXamlCompilationData
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.metadata.WinRTXamlDeclarations
import kotlinx.serialization.json.*
import kotlinx.coroutines.*
import org.junit.Assume.assumeTrue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import java.util.jar.JarOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Optional Windows integration fixture from a prepared declaration pass; never edits its source or outputs. */
@OptIn(KaExperimentalApi::class, KaAllowAnalysisOnEdt::class)
class WinRTXamlDocumentCompilerTest : BasePlatformTestCase() {
    fun testCancellingLiveXamlCompilationTerminatesTheOwnedProcessAndRemovesItsSnapshot() {
        val prepared = Path.of(requireNotNull(System.getProperty("winrt.ide.xamlInput")))
        val compilerDirectory = requireNotNull(System.getProperty("winrt.ide.xamlCompiler"))
        val input = Json.parseToJsonElement(Files.readString(prepared)).jsonObject
        val source = input.getValue("XamlPages").jsonArray.map {
            Path.of(it.jsonObject.getValue("FullPath").jsonPrimitive.content)
        }.first { Files.readString(it).contains("</StackPanel>") }
        val original = Files.readString(source)
        // Keep the real DOM/harvester busy long enough to observe its process;
        // all input identities/references still come from the prepared pass.
        val expanded = original.replaceFirst("</StackPanel>",
            "<TextBlock Text=\"Cancellation probe\"/>".repeat(12_000) + "</StackPanel>")
        val cache = Files.createTempDirectory("winrt-ide-cancel-")
        val compilation = WinRTXamlCompilationData("analyzeWinRTXaml", listOf(source.parent.toString()),
            prepared.parent.resolve("declarations.json").toString(), prepared.toString(), compilerDirectory, "")
        var owned: ProcessHandle? = null
        try {
            runBlocking {
                val previous = ProcessHandle.current().children().use { children -> children.map { it.pid() }.toList().toSet() }
                val worker = async(Dispatchers.IO) {
                    WinRTXamlDocumentCompiler.harvest(compilation, listOf(WinRTXamlDocument(source.toString(), expanded, 1)), cache)
                }
                try {
                    withTimeout(15_000) {
                        val executable = Path.of(compilerDirectory).resolve("XamlCompiler.exe").toAbsolutePath().normalize()
                        while (owned == null) {
                            owned = ProcessHandle.current().children().use { children -> children.filter { child ->
                                child.pid() !in previous && child.info().command().map {
                                    Path.of(it).toAbsolutePath().normalize() == executable
                                }.orElse(false)
                            }.toList().singleOrNull() }
                            if (owned == null && worker.isCompleted) {
                                worker.await()
                                error("The compiler finished before its live process could be observed.")
                            }
                            if (owned == null) delay(10)
                        }
                    }
                    assertTrue(owned!!.isAlive)
                    withTimeout(10_000) { worker.cancelAndJoin() }
                    assertTrue(worker.isCancelled)
                    assertFalse("Cancelled XAMLC process must exit", owned!!.isAlive)
                    Files.list(cache).use { assertEquals("Cancelled snapshots must be removed", 0L, it.count()) }
                    assertEquals(original, Files.readString(source))
                } finally { withContext(NonCancellable) { worker.cancelAndJoin() } }
            }
        } finally {
            // Preserve the assertion above while also cleaning up a failed test.
            // Never remove a snapshot while its observed compiler is still alive.
            owned?.takeIf { it.isAlive }?.let { child ->
                child.destroyForcibly()
                child.onExit().get(10, TimeUnit.SECONDS)
            }
            check(cache.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
            check(cache.fileName.toString().startsWith("winrt-ide-cancel-"))
            FileUtil.delete(cache.toFile())
        }
    }

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
                if (Regex("\\b${Regex.escape(name)}\\b").findAll(text).count() == 1 && text.contains("x:Name=\"$name\""))
                    Triple(item, source, name) else null
            }
        }.first()
        val original = Files.readString(Path.of(candidate.second))
        val root = Files.createTempDirectory("winrt-ide-document-")
        var fixtureFacet: KotlinFacet? = null
        try {
            val source = root.resolve("Page.xaml")
            Files.writeString(source, original)
            val inputFile = root.resolve("input.json")
            val item = JsonObject(candidate.first.jsonObject + mapOf("FullPath" to JsonPrimitive(source.toString()), "ItemSpec" to JsonPrimitive(source.toString())))
            Files.writeString(inputFile, JsonObject(input + ("XamlPages" to JsonArray(listOf(item)))).toString())
            val declarations = root.resolve("declarations.json")
            val initialIndex = index.copy(pages = index.pages.filter { it.resourcePath == candidate.first.jsonObject.getValue("MSBuild_Link").jsonPrimitive.content }, resources = emptyList())
            Files.writeString(declarations, WinRTXamlDeclarations.canonicalText(initialIndex))
            val originalPlugin = root.resolve("winrt-compiler-plugin.jar")
            JarOutputStream(Files.newOutputStream(originalPlugin)).close()
            ApplicationManager.getApplication().runWriteAction {
                val manager = FacetManager.getInstance(module)
                val facet = manager.createFacet(KotlinFacetType.INSTANCE, "Kotlin", null).also { fixtureFacet = it }
                facet.configuration.settings.useProjectSettings = false
                facet.configuration.settings.compilerArguments = K2JVMCompilerArguments().apply {
                    pluginClasspaths = arrayOf(originalPlugin.toString())
                    pluginOptions = arrayOf("plugin:io.github.composefluent.winrt.compiler:xamlDeclarations=$declarations")
                }
                manager.createModifiableModel().apply { addFacet(facet); commit() }
            }
            // Only external SDK contracts are fixture sources. Named properties
            // and supertypes are supplied by the real FIR adapter from XAMLC.
            val page = initialIndex.pages.single()
            val sdkTypes = (page.connections.map { it.typeName } + page.baseTypeName).distinct()
            sdkTypes.forEachIndexed { position, type ->
                val projected = io.github.composefluent.winrt.ide.fir.WinRTIdeTypeNames.projection(type)
                myFixture.addFileToProject("Sdk$position.kt", "package ${projected.substringBeforeLast('.')}\nopen class ${projected.substringAfterLast('.')}")
            }
            myFixture.addFileToProject("Connector.kt", "package microsoft.ui.xaml.markup\ninterface IComponentConnector")
            myFixture.addFileToProject("Component.kt", "package io.github.composefluent.winrt.runtime\ninterface WinRTXamlComponent\nclass WinRTXamlLoadState")
            val kotlinFile = myFixture.configureByText("${page.className.substringAfterLast('.')}.kt",
                "package ${page.className.substringBeforeLast('.')}\nclass ${page.className.substringAfterLast('.')} { fun element() = IdeUnsavedElement }") as KtFile
            val compilation = WinRTXamlCompilationData("analyzeWinRTXaml", listOf(root.toString()), declarations.toString(), inputFile.toString(), compilerProperty, "")
            val models = listOf(
                WinRTModuleData(":", root.toString(), root.resolve("build").toString(), "2.4.0", "",
                    emptyList(), emptyList(), emptyList(), emptyList(), listOf(compilation)),
            )
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), models)
            val snapshots = project.service<WinRTXamlSnapshotService>()
            val key = WinRTXamlSnapshotService.key(declarations.toString())
            PlatformTestUtil.waitWithEventsDispatching("Initial declarations", { snapshots.state.value[key] != null }, 10)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertTrue(memberNames(kotlinFile).contains(candidate.third))
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
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertTrue(memberNames(kotlinFile).contains("IdeUnsavedElement"))
            assertFalse(memberNames(kotlinFile).contains(candidate.third))
            assertTrue(unresolvedDiagnostics(kotlinFile).isEmpty())
            assertEquals(original, Files.readString(source))
            assertEquals(original, Files.readString(Path.of(candidate.second)))
            // A Gradle reimport must retain this unsaved live result, including
            // generated-member resolution, rather than loading the older disk index.
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), emptyList())
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), models)
            PlatformTestUtil.waitWithEventsDispatching("Live declarations after reimport", {
                snapshots.state.value[key]?.declarations?.pages?.flatMap { it.connections }?.any { it.fieldName == "IdeUnsavedElement" } == true
            }, 30)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertTrue(memberNames(kotlinFile).contains("IdeUnsavedElement"))
            ApplicationManager.getApplication().runWriteAction { document.setText(updated.replace("x:Name=\"IdeUnsavedElement\"", "")) }
            PlatformTestUtil.waitWithEventsDispatching("Removed names", {
                snapshots.state.value[key]?.declarations?.pages?.isNotEmpty() == true &&
                    snapshots.state.value[key]?.declarations?.pages?.flatMap { it.connections }?.none { it.fieldName == "IdeUnsavedElement" } == true
            }, 30)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertFalse(memberNames(kotlinFile).contains("IdeUnsavedElement"))
            assertTrue(unresolvedDiagnostics(kotlinFile).any { it.contains("IdeUnsavedElement") })
            ApplicationManager.getApplication().runWriteAction { document.setText(updated) }
            PlatformTestUtil.waitWithEventsDispatching("Added names", {
                snapshots.state.value[key]?.declarations?.pages?.flatMap { it.connections }?.any { it.fieldName == "IdeUnsavedElement" } == true
            }, 30)
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertTrue(memberNames(kotlinFile).contains("IdeUnsavedElement"))
            ApplicationManager.getApplication().runWriteAction { document.setText("<Page") }
            PlatformTestUtil.waitWithEventsDispatching("Invalid markup clears stale declarations", { snapshots.state.value[key]?.error != null }, 30)
            assertTrue(snapshots.state.value.getValue(key).declarations.pages.isEmpty())
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            assertFalse(memberNames(kotlinFile).contains("IdeUnsavedElement"))
            assertEquals(original, Files.readString(source))
            FileDocumentManager.getInstance().reloadFromDisk(document)
            Files.list(snapshots.cacheDirectory).use { assertEquals(0L, it.count()) }
        } finally {
            fixtureFacet?.let { facet -> ApplicationManager.getApplication().runWriteAction {
                val manager = FacetManager.getInstance(module)
                if (manager.allFacets.any { it === facet }) manager.createModifiableModel().apply { removeFacet(facet); commit() }
            } }
            project.service<WinRTProjectService>().replaceBuildModels(root.toString(), emptyList())
            check(root.toRealPath().parent == Path.of(System.getProperty("java.io.tmpdir")).toRealPath())
            check(root.fileName.toString().startsWith("winrt-ide-document-"))
            FileUtil.delete(root.toFile())
        }
    }

    private fun memberNames(file: KtFile): Set<String> = allowAnalysisOnEdt {
        analyze(file) { (file.declarations.single() as KtClass).namedClassSymbol!!.declaredMemberScope.callables
            .mapNotNull { (it as? KaNamedSymbol)?.name?.asString() }.toSet() }
    }
    private fun unresolvedDiagnostics(file: KtFile): List<String> = allowAnalysisOnEdt {
        analyze(file) { file.collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
            .filter { it.factoryName.contains("UNRESOLVED_REFERENCE") }.map { it.defaultMessage } }
    }
}
