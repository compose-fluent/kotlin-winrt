@file:OptIn(io.github.composefluent.winrt.runtime.InternalWinRTApi::class)

package io.github.composefluent.winrt.ide.hotreload

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.runtime.*
import kotlinx.coroutines.*
import org.jetbrains.plugins.gradle.settings.*
import java.nio.file.Files
import java.nio.file.Path

/** Actual Native SDK state and toolkit build/replacement acceptance. The generated
 * custom-property adapter follows CsWinRT's source-generator/runtime split; the
 * application participant explicitly owns business and asynchronous checkpoints. */
class WinRTNativeRestartIntegrationTest : BasePlatformTestCase() {
    override fun runInDispatchThread() = false

    fun testNativeRebuildRestoresNavigationDynamicInputsAndApplicationCheckpoint() {
        val root = Path.of(System.getProperty("winrt.ide.nativeRestartProject")).toAbsolutePath().normalize()
        require(root.startsWith(Path.of(System.getProperty("winrt.ide.toolchain")).resolve(".gradle")))
        val app = root.resolve("app")
        val code = app.resolve("src/winuiMain/kotlin/sample/hello/MainWindow.kt")
        val original = Files.readString(code)
        require(original.contains("Native recovery verified without reconnect") && original.contains("WinUIXamlRestartParticipant"))
        require(Files.isRegularFile(code.resolveSibling("RecoveryAlternatePage.xaml")))
        require(original.contains("frame.navigate(RecoveryPage::class, \"checkpoint route\")"))
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString(), System.getProperty("winrt.ide.toolchain"), System.getProperty("java.home"))
        System.getenv("GRADLE_USER_HOME")?.let { VfsRootAccess.allowRootAccess(testRootDisposable, Path.of(it).parent.toString()) }
        val restoreMetadata = preserveWinRTIntegrationProjectMetadata(root)
        val previousSdks = ProjectJdkTable.getInstance().allJdks.toSet()
        val sdk = JavaSdk.getInstance().createJdk("WinRT Native restart validation", System.getProperty("java.home"), false)
        val manager = ProjectManagerEx.getInstanceEx()
        val opened = EdtTestUtil.runInEdtAndGet<com.intellij.openapi.project.Project, Exception> {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
            manager.openProject(root, OpenProjectTask {
                isNewProject = false; useDefaultProjectAsTemplate = false; forceOpenInNewFrame = true
                runConfigurators = false; createModule = false
            })!!
        }
        val deployment = app.resolve("build/kotlin-winrt/application-run/mingwX64_main_debugExecutable/Unpackaged")
        val launch = WinUIXamlHotReloadLaunchData("runWinAppNativeMingwX64MainDebugExecutable",
            deployment.resolve("native-project-preview.exe").toString(), deployment.toString(),
            nativeCompilerHome = System.getProperty("winrt.ide.nativeDebugKonanHome")
                ?: Path.of(System.getenv("KONAN_DATA_DIR") ?: Path.of(System.getProperty("user.home"), ".konan").toString())
                    .resolve("kotlin-native-prebuilt-windows-x86_64-2.4.0").toString(),
            isDirectPreview = true, developmentBuildTask = "stageWinAppPackageMingwX64MainDebugExecutable")
        fun module(name: String, launches: List<WinUIXamlHotReloadLaunchData>): WinRTModuleData {
            val folder = root.resolve(name)
            val sources = folder.resolve("src/winuiMain/kotlin")
            return WinRTModuleData(":$name", folder.toString(), folder.resolve("build").toString(), "2.4.0", "10.0.26100.0",
                listOf(WinRTSourceSetData("winuiMain", listOf(sources.toString()), emptyList(), emptyList())), emptyList(), emptyList(), emptyList(),
                listOf(WinUIXamlCompilationData("analyzeWinRTXaml", listOf(sources.toString()), folder.resolve("declarations.json").toString(),
                    folder.resolve("input.json").toString(), sources.toString(), "")), hotReloadLaunches = launches)
        }
        val service = opened.service<WinUIXamlHotReloadService>()
        fun await(label: String, condition: () -> Boolean) = runBlocking {
            var previous = ""
            try { withTimeout(900_000) { while (!condition()) {
                val message = service.state.value.message
                if (message != previous) {
                    println("$label: $message; connected=${service.state.value.connected}, pid=${service.state.value.pid}, roots=${service.state.value.roots.map { it.className to it.instances }}")
                    previous = message
                }
                check(listOf("The Gradle launch failed", "The Gradle launch finished, but", "Code build failed", "Automatic restart failed", "The preview host exited", "The application exited").none(message::startsWith)) { "$label: $message" }
                delay(100)
            } } }
            catch (error: TimeoutCancellationException) { throw AssertionError("$label: ${service.state.value.message}", error) }
        }
        fun client() = WinUIXamlHotReloadClient.discover(Path.of(service.getState().sessionDirectory), service.getState().preparedExecutable.ifEmpty { launch.executable })
            .single { it.process.pid() == service.state.value.pid }
        fun expected(request: WinUIXamlStateRequest, state: WinUIXamlSavedElement): String? = when {
            state.kind.startsWith("Participant:") -> "3"
            state.kind != "TextBox" -> null
            state.name == "ReloadInput" -> "Native root recovered"
            state.name.endsWith("DynamicRecoveryInput") -> "Native dynamic recovered"
            state.name == "NavigationInput" -> "Native navigation recovered"
            state.name == "RepeatedInput" && request.componentKey.contains("GreetingLeft") -> "Native left recovered"
            state.name == "RepeatedInput" && request.componentKey.contains("GreetingRight") -> "Native right recovered"
            else -> null
        }
        try {
            EdtTestUtil.runInEdtAndWait<Exception> {
                ApplicationManager.getApplication().runWriteAction { ProjectRootManager.getInstance(opened).projectSdk = sdk }
                val gradle = GradleSettings.getInstance(opened)
                val projectPath = root.toString().replace('\\', '/')
                val linked = gradle.getLinkedProjectSettings(projectPath)
                if (linked != null) {
                    linked.distributionType = DistributionType.DEFAULT_WRAPPED
                    linked.gradleJvm = sdk.name
                } else gradle.linkProject(GradleProjectSettings().apply {
                    externalProjectPath = projectPath; distributionType = DistributionType.DEFAULT_WRAPPED; gradleJvm = sdk.name
                })
                val appModule = module("app", listOf(launch))
                opened.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(appModule, module("controls", emptyList())))
                service.start(appModule, launch)
            }
            await("Native application and navigated page") {
                service.state.value.connected && service.state.value.roots.any { it.className == "sample.hello.RecoveryPage" }
            }
            val before = service.state.value.pid
            // Exercise the actual Native mutable-vector baseline before restarting.
            // The generated Children accessor follows CsWinRT's collection projection;
            // owner/name anchors must share one baseline without losing collection edits.
            val xmlFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val xml = xmlFactory.newDocumentBuilder().parse(code.resolveSibling("MainWindow.xaml").toFile())
            val grids = xml.getElementsByTagNameNS("http://schemas.microsoft.com/winfx/2006/xaml/presentation", "Grid")
            val layout = (0 until grids.length).map { grids.item(it) as org.w3c.dom.Element }.single {
                it.getAttributeNS("http://schemas.microsoft.com/winfx/2006/xaml", "Name") == "LayoutRoot"
            }
            val childCount = (0 until layout.childNodes.length).count {
                (layout.childNodes.item(it) as? org.w3c.dom.Element)?.localName?.contains('.') == false
            }
            require(childCount > 1)
            client().use { connection ->
                // LayoutCanvas has two children inserted by application code;
                // it is intentionally outside the compiled collection baseline.
                // Reorder the unchanged LayoutRoot collection instead.
                val canvas = xml.getElementsByTagNameNS("http://schemas.microsoft.com/winfx/2006/xaml/presentation", "Canvas").item(0)
                val compiledChildren = (0 until canvas.childNodes.length).count {
                    (canvas.childNodes.item(it) as? org.w3c.dom.Element)?.localName?.contains('.') == false
                }
                val beforeGuard = connection.request().roots.single { it.className == "sample.hello.MainWindow" }
                val rejected = connection.request(WinUIXamlHotReloadPatch(beforeGuard.className, beforeGuard.resourcePath,
                    beforeGuard.sourceHash, beforeGuard.sourceHash, beforeGuard.version + 1, emptyList(),
                    children = listOf(WinUIXamlHotReloadChildren(WinUIXamlHotReloadTarget("LayoutCanvas",
                        listOf(WinUIXamlHotReloadStep.Property("Children"))), compiledChildren,
                        (0 until compiledChildren).map { WinUIXamlHotReloadItem.Existing(it) }))))
                assertEquals(WinUIXamlHotReloadProtocol.REJECTED, rejected.status)
                assertEquals(beforeGuard.version, connection.request().roots.single { it.className == beforeGuard.className }.version)
                repeat(2) {
                    val main = connection.request().roots.single { it.className == "sample.hello.MainWindow" }
                    val order = (0 until childCount).toMutableList().apply { this[0] = 1; this[1] = 0 }
                    val patch = WinUIXamlHotReloadPatch(main.className, main.resourcePath, main.sourceHash, main.sourceHash,
                        main.version + 1, emptyList(), children = listOf(WinUIXamlHotReloadChildren(
                            WinUIXamlHotReloadTarget("LayoutRoot", listOf(WinUIXamlHotReloadStep.Property("Children"))),
                            childCount, order.map { WinUIXamlHotReloadItem.Existing(it) })))
                    val reply = connection.request(patch)
                    assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                }
            }
            println("Actual Native collection reordered and restored through the component baseline")
            runBlocking {
                var description = ""
                try { withTimeout(15_000) {
                    while (true) {
                        val roots = client().use { it.request().roots }
                        val current = roots.map { it.className to it.instances }.toString()
                        if (current != description) { description = current; println("Native components after collection edit: $current") }
                        if (roots.any { it.className == "sample.hello.RecoveryPage" } &&
                            roots.any { it.className == "sample.controls.GreetingControl" && it.instances == 2 }) break
                        delay(100)
                    }
                } } catch (error: TimeoutCancellationException) {
                    throw AssertionError("Reparented components did not become available: $description", error)
                }
            }
            var navigation = ""
            var saved = client().use { connection ->
                val snapshot = runBlocking { captureWinRTRestartState({ connection.request().roots }, connection::transferState) }
                val requests = snapshot.requests.map { it.copy(restore = null) }
                val values = snapshot.requests.map { captured ->
                    val request = captured.copy(restore = null)
                    captured.restore!!.singleOrNull { it.kind == "Frame" }?.let { frame ->
                        navigation = frame.values.single { it.name == "NavigationState" }.value
                    }
                    val updates = captured.restore!!.mapNotNull { state -> expected(request, state)?.let { value ->
                        state.copy(values = listOf(WinUIXamlVisualProperty(if (state.kind.startsWith("Participant:")) "clicks" else "Text", value)))
                    } }
                    assertEquals(WinUIXamlHotReloadProtocol.APPLIED, connection.transferState(request.copy(restore = updates)).status)
                    request to updates
                }
                assertEquals("Window, dynamic input, page, two dependency instances and business checkpoint", 6, values.sumOf { it.second.size })
                assertTrue("Navigation must be captured by the real SDK Frame", requests.any { request ->
                    connection.transferState(request).savedState!!.any { it.kind == "Frame" && it.values.any { value -> value.name == "NavigationState" && value.value.isNotEmpty() } }
                })
                values
            }
            Files.writeString(code, original.replace("Native recovery verified without reconnect", "Native rebuilt and recovered")
                .replace("frame.navigate(RecoveryPage::class, \"checkpoint route\")", "frame.navigate(RecoveryAlternatePage::class, \"fresh default\")"))
            service.markProjectCodeChanged(code.toString())
            await("Build while old application stays interactive") { service.state.value.rebuildingCode }
            assertEquals(before, service.state.value.pid)
            assertTrue(ProcessHandle.of(requireNotNull(before)).orElseThrow().isAlive)
            saved = saved.map { (request, updates) -> request to updates.map { state ->
                if (state.name == "ReloadInput") state.copy(values = listOf(WinUIXamlVisualProperty("Text", "Input entered during the build"))) else state
            } }
            client().use { connection ->
                saved.forEach { (request, updates) ->
                    assertEquals(WinUIXamlHotReloadProtocol.APPLIED, connection.transferState(request.copy(restore = updates)).status)
                }
            }
            await("Native replacement and state recovery") {
                service.state.value.connected && service.state.value.pid != before && service.state.value.restartOutcome != null
            }
            println("Native restart outcome: ${service.state.value.restartOutcome}; handoff=${service.state.value.startupMillis}ms")
            assertTrue(io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch.isReplacement(Path.of(launch.executable), Path.of(service.getState().preparedExecutable)))
            assertEquals("Code changes loaded; saved UI and application checkpoints restored.", service.state.value.restartOutcome)
            client().use { connection ->
                saved.forEach { (request, updates) ->
                    val reply = connection.transferState(request)
                    assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                    updates.forEach { update ->
                        val actual = reply.savedState!!.single { it.name == update.name && it.kind == update.kind }
                        update.values.forEach { value -> assertEquals("${request.className}/${update.name}/${value.name}", value.value, actual.values.single { it.name == value.name }.value) }
                    }
                }
                val roots = connection.request().roots
                assertTrue("Restored navigation must replace the newly compiled default route", roots.none { it.className == "sample.hello.RecoveryAlternatePage" })
                val mainRequest = saved.single { it.first.className == "sample.hello.MainWindow" }.first
                val frame = connection.transferState(mainRequest).savedState!!.single { it.kind == "Frame" }
                assertEquals(navigation, frame.values.single { it.name == "NavigationState" }.value)
                val main = roots.single { it.className == "sample.hello.MainWindow" }
                val read = connection.request(WinUIXamlHotReloadPatch(main.className, main.resourcePath, main.sourceHash, main.sourceHash,
                    main.version + 1, emptyList(), reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("BoundInput"), "Text"),
                        WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("TextBlock1"), "Text"))))
                assertEquals(read.message, WinUIXamlHotReloadProtocol.APPLIED, read.status)
                assertEquals(listOf("Native rebuilt and recovered", "Business checkpoint restored: 3"), read.values.map { it.value })
            }
            assertEquals("Code changes loaded; saved UI and application checkpoints restored.", service.state.value.restartOutcome)
            println("Actual Native replacement recovered navigation, dynamic input, two dependency instances and business checkpoint: $before -> ${service.state.value.pid}")
            val healthyExecutable = Path.of(service.getState().preparedExecutable)
            val healthyPid = service.state.value.pid
            val rollbackState = client().use { connection ->
                restartStatePlan(connection.request().roots).requests.map { request ->
                    request.copy(restore = requireNotNull(connection.transferState(request).savedState))
                }
            }
            val brokenExecutable = io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch.deployment(
                deployment, java.util.UUID.randomUUID().toString()).resolve(healthyExecutable.fileName)
            Files.createDirectories(brokenExecutable.parent)
            Files.writeString(brokenExecutable, "Invalid PE image: deliberate failed-replacement acceptance")
            EdtTestUtil.runInEdtAndWait<Exception> {
                service.start(module("app", listOf(launch)), launch, restart = true,
                    restoredState = rollbackState,
                    preparedLaunch = io.github.composefluent.winrt.ide.model.WinRTPreparedLaunch(brokenExecutable.toString(), emptyList(), emptyMap()))
            }
            await("Automatic rollback after invalid Native replacement") {
                service.state.value.connected && service.state.value.pid != healthyPid &&
                    service.state.value.restartOutcome?.startsWith("Replacement failed to start; the previous application was restored.") == true
            }
            assertEquals(healthyExecutable.toString(), service.getState().preparedExecutable)
            assertTrue(Files.isRegularFile(healthyExecutable))
            assertFalse(Files.exists(brokenExecutable.parent))
            assertTrue(service.state.value.projectCodeChanged)
            client().use { connection ->
                saved.forEach { (request, updates) ->
                    val reply = connection.transferState(request)
                    assertEquals(reply.message, WinUIXamlHotReloadProtocol.APPLIED, reply.status)
                    updates.forEach { update ->
                        val actual = reply.savedState!!.single { it.name == update.name && it.kind == update.kind }
                        update.values.forEach { value -> assertEquals(value.value, actual.values.single { it.name == value.name }.value) }
                    }
                }
            }
            println("Actual invalid Native replacement rolled back with saved state and removed the failed deployment: $healthyPid -> ${service.state.value.pid}")

        } finally {
            EdtTestUtil.runInEdtAndWait<Exception> { service.stop() }
            Files.writeString(code, original)
            try { EdtTestUtil.runInEdtAndWait<Exception> {
                manager.forceCloseProject(opened)
                ApplicationManager.getApplication().runWriteAction {
                    val table = ProjectJdkTable.getInstance()
                    table.allJdks.filterNot(previousSdks::contains).forEach(table::removeJdk)
                }
            } } finally { restoreMetadata() }
        }
    }
}
