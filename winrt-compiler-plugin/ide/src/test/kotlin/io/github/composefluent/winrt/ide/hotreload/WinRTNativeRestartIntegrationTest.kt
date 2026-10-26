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
        val previousSdks = ProjectJdkTable.getInstance().allJdks.toSet()
        val sdk = JavaSdk.getInstance().createJdk("WinRT Native restart validation", System.getProperty("java.home"), false)
        val manager = ProjectManagerEx.getInstanceEx()
        val opened = EdtTestUtil.runInEdtAndGet<com.intellij.openapi.project.Project, Exception> {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
            manager.openProject(root, OpenProjectTask {
                isNewProject = true; useDefaultProjectAsTemplate = false; forceOpenInNewFrame = true
                runConfigurators = false; createModule = false
            })!!
        }
        val deployment = app.resolve("build/kotlin-winrt/application-run/mingwX64_main_debugExecutable/Unpackaged")
        val launch = WinUIXamlHotReloadLaunchData("runWinAppNativeMingwX64MainDebugExecutable",
            deployment.resolve("native-project-preview.exe").toString(), deployment.toString(),
            developmentBuildTask = "stageWinAppPackageMingwX64MainDebugExecutable")
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
            try { withTimeout(360_000) { while (!condition()) {
                val message = service.state.value.message
                if (message != previous) {
                    println("$label: $message; connected=${service.state.value.connected}, pid=${service.state.value.pid}, roots=${service.state.value.roots.map { it.className }}")
                    previous = message
                }
                check(listOf("The Gradle launch failed", "The Gradle launch finished, but", "Code build failed", "Automatic restart failed").none(message::startsWith)) { "$label: $message" }
                delay(100)
            } } }
            catch (error: TimeoutCancellationException) { throw AssertionError("$label: ${service.state.value.message}", error) }
        }
        fun client() = WinUIXamlHotReloadClient.discover(Path.of(service.getState().sessionDirectory), launch.executable)
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
                GradleSettings.getInstance(opened).linkProject(GradleProjectSettings().apply {
                    externalProjectPath = root.toString().replace('\\', '/'); distributionType = DistributionType.DEFAULT_WRAPPED; gradleJvm = sdk.name
                })
                val appModule = module("app", listOf(launch))
                opened.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(appModule, module("controls", emptyList())))
                service.start(appModule, launch)
            }
            await("Native application and navigated page") {
                service.state.value.connected && service.state.value.roots.any { it.className == "sample.hello.RecoveryPage" }
            }
            val before = service.state.value.pid
            var navigation = ""
            val saved = client().use { connection ->
                val requests = restartStatePlan(connection.request().roots).requests
                val values = requests.map { request ->
                    val captured = connection.transferState(request)
                    assertEquals(captured.message, WinUIXamlHotReloadProtocol.APPLIED, captured.status)
                    captured.savedState!!.singleOrNull { it.kind == "Frame" }?.let { frame ->
                        navigation = frame.values.single { it.name == "NavigationState" }.value
                    }
                    val updates = captured.savedState!!.mapNotNull { state -> expected(request, state)?.let { value ->
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
            await("Native replacement and state recovery") {
                service.state.value.connected && service.state.value.pid != before && service.state.value.restartOutcome != null
            }
            println("Native restart outcome: ${service.state.value.restartOutcome}")
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
        } finally {
            EdtTestUtil.runInEdtAndWait<Exception> { service.stop() }
            Files.writeString(code, original)
            EdtTestUtil.runInEdtAndWait<Exception> {
                manager.forceCloseProject(opened)
                ApplicationManager.getApplication().runWriteAction {
                    val table = ProjectJdkTable.getInstance()
                    table.allJdks.filterNot(previousSdks::contains).forEach(table::removeJdk)
                }
            }
        }
    }
}
