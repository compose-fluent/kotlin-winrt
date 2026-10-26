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

/** Real toolkit deployment and SDK state access, following CsWinRT's generated
 * custom-property/runtime ownership split. Opt in with a prepared local JVM fixture. */
class WinRTApplicationRestartIntegrationTest : BasePlatformTestCase() {
    override fun runInDispatchThread() = false

    fun testUnpackagedJvmAutomaticallyBuildsReplacesAndRestoresNamedComponents() {
        val root = Path.of(System.getProperty("winrt.ide.restartProject")).toAbsolutePath().normalize()
        require(root.startsWith(Path.of(System.getProperty("winrt.ide.toolchain")).resolve(".gradle")))
        val app = root.resolve("app")
        val sources = app.resolve("src/winuiMain/kotlin")
        val code = sources.resolve("sample/hello/MainWindow.kt")
        val original = Files.readString(code)
        require(original.contains("JVM component state ready"))
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString(), System.getProperty("winrt.ide.toolchain"), System.getProperty("java.home"))
        System.getenv("GRADLE_USER_HOME")?.let { VfsRootAccess.allowRootAccess(testRootDisposable, Path.of(it).parent.toString()) }
        val previousSdks = ProjectJdkTable.getInstance().allJdks.toSet()
        val sdk = JavaSdk.getInstance().createJdk("WinRT restart validation", System.getProperty("java.home"), false)
        val manager = ProjectManagerEx.getInstanceEx()
        val opened = EdtTestUtil.runInEdtAndGet<com.intellij.openapi.project.Project, Exception> {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
            manager.openProject(root, OpenProjectTask {
                isNewProject = true; useDefaultProjectAsTemplate = false; forceOpenInNewFrame = true
                runConfigurators = false; createModule = false
            })!!
        }
        val deployment = app.resolve("build/kotlin-winrt/application-run/jvm_main/Unpackaged")
        val launch = WinUIXamlHotReloadLaunchData("runWinAppHostJvmMain", deployment.resolve("jvm-project-preview.exe").toString(),
            deployment.toString(), developmentBuildTask = "buildWinAppHostJvmMain")
        val module = WinRTModuleData(":app", app.toString(), app.resolve("build").toString(), "2.4.0", "10.0.26100.0",
            listOf(WinRTSourceSetData("winuiMain", listOf(sources.toString()), emptyList(), emptyList())), emptyList(), emptyList(), emptyList(),
            listOf(WinUIXamlCompilationData("analyzeWinRTXaml", listOf(sources.toString()), app.resolve("declarations.json").toString(),
                app.resolve("input.json").toString(), sources.toString(), "")), hotReloadLaunches = listOf(launch))
        val service = opened.service<WinUIXamlHotReloadService>()
        fun await(label: String, condition: () -> Boolean) = runBlocking {
            try { withTimeout(180_000) { while (!condition()) {
                val message = service.state.value.message
                check(listOf("The Gradle launch failed", "The Gradle launch finished, but", "Code build failed", "Automatic restart failed").none(message::startsWith)) { "$label: $message" }
                delay(100)
            } } }
            catch (error: TimeoutCancellationException) { throw AssertionError("$label: ${service.state.value.message}", error) }
        }
        fun client() = WinUIXamlHotReloadClient.discover(Path.of(service.getState().sessionDirectory), launch.executable)
            .single { it.process.pid() == service.state.value.pid }
        try {
            EdtTestUtil.runInEdtAndWait<Exception> {
                ApplicationManager.getApplication().runWriteAction { ProjectRootManager.getInstance(opened).projectSdk = sdk }
                GradleSettings.getInstance(opened).linkProject(GradleProjectSettings().apply {
                    externalProjectPath = root.toString().replace('\\', '/'); distributionType = DistributionType.DEFAULT_WRAPPED; gradleJvm = sdk.name
                })
                opened.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(module))
                service.start(module, launch)
            }
            await("Initial application and named component connections") {
                service.state.value.connected && restartStatePlan(service.state.value.roots).requests.size == 3
            }
            val before = service.state.value.pid
            val saved = client().use { connection ->
                val roots = connection.request().roots
                val requests = restartStatePlan(roots).requests
                assertEquals("Both named dependency components and the window need stable identities", 3, requests.size)
                requests.map { request ->
                    val main = request.className == "sample.hello.MainWindow"
                    val value = when { main -> "JVM root input"; request.componentKey.contains("GreetingLeft") -> "JVM left input"; else -> "JVM right input" }
                    val state = listOf(WinUIXamlSavedElement(if (main) "ReloadInput" else "RepeatedInput", "TextBox", listOf(WinUIXamlVisualProperty("Text", value))))
                    assertEquals(WinUIXamlHotReloadProtocol.APPLIED, connection.transferState(request.copy(restore = state)).status)
                    request to value
                }
            }
            Files.writeString(code, original.replace("JVM component state ready", "JVM rebuilt without manual Run"))
            service.markProjectCodeChanged(code.toString())
            await("Automatic replacement and state recovery") {
                service.state.value.connected && service.state.value.pid != before && service.state.value.restartOutcome != null
            }
            client().use { connection -> saved.forEach { (request, expected) ->
                val restored = connection.transferState(request)
                assertEquals(restored.message, WinUIXamlHotReloadProtocol.APPLIED, restored.status)
                assertEquals(expected, restored.savedState!!.single { it.name == if (request.className == "sample.hello.MainWindow") "ReloadInput" else "RepeatedInput" }
                    .values.single { it.name == "Text" }.value)
            } }
            client().use { connection ->
                val main = connection.request().roots.single { it.className == "sample.hello.MainWindow" }
                val read = connection.request(WinUIXamlHotReloadPatch(main.className, main.resourcePath,
                    main.sourceHash, main.sourceHash, main.version + 1, emptyList(),
                    reads = listOf(WinUIXamlHotReloadRead(WinUIXamlHotReloadTarget("BoundInput"), "Text"))))
                assertEquals(read.message, WinUIXamlHotReloadProtocol.APPLIED, read.status)
                assertEquals("JVM rebuilt without manual Run", read.values.single().value)
            }
            assertEquals("Code changes loaded; saved control state restored.", service.state.value.restartOutcome)
            println("Actual JVM automatic restart restored three component inputs: $before -> ${service.state.value.pid}")
        } finally {
            EdtTestUtil.runInEdtAndWait<Exception> { service.stop() }
            Files.writeString(code, original)
            EdtTestUtil.runInEdtAndWait<Exception> {
                manager.forceCloseProject(opened)
                ApplicationManager.getApplication().runWriteAction {
                    // Gradle execution may register its JBR and the Kotlin SDK.
                    // Those belong to this owned test project as well.
                    val table = ProjectJdkTable.getInstance()
                    table.allJdks.filterNot(previousSdks::contains).forEach(table::removeJdk)
                }
            }
        }
    }
}
