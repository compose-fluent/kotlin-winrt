package io.github.composefluent.winrt.ide.preview

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.gradle.*
import io.github.composefluent.winrt.ide.hotreload.WinRTStaticPreviewService
import io.github.composefluent.winrt.ide.project.WinRTProjectService
import io.github.composefluent.winrt.ide.xaml.WinRTXamlCatalogService
import io.github.composefluent.winrt.runtime.WinRTXamlHotReloadProtocol
import io.github.composefluent.winrt.runtime.WinRTXamlInspectionRequest
import kotlinx.coroutines.*
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/** Opt-in Windows acceptance with a prepared SDK-only Gradle fixture. This
 * exercises the real Compose effect -> development session -> native Gradle
 * runner -> WinUI renderer, rather than attaching to an already running host. */
@OptIn(ExperimentalComposeUiApi::class)
class WinRTPreviewLaunchIntegrationTest : BasePlatformTestCase() {
    override fun runInDispatchThread() = false

    fun testOpeningPreviewStartsNativeSdkDesignerFromNakedComposeEdt() {
        val root = Path.of(System.getProperty("winrt.ide.sdkPreviewProject")).toAbsolutePath().normalize()
        require(root.startsWith(Path.of(System.getProperty("winrt.ide.toolchain")).resolve(".gradle")))
        require(Files.isRegularFile(root.resolve("gradlew.bat")))
        val app = root.resolve("app")
        val sources = app.resolve("src/winuiJvm/kotlin")
        val source = sources.resolve("IdeLaunchPreview.xaml")
        require(!Files.exists(source))
        Files.createDirectories(sources)
        Files.writeString(source, """<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"><TextBlock Text="Launched from the IDE"/></Page>""")
        VfsRootAccess.allowRootAccess(testRootDisposable, root.toString(), System.getProperty("winrt.ide.toolchain"), System.getProperty("java.home"))
        System.getenv("GRADLE_USER_HOME")?.let { VfsRootAccess.allowRootAccess(testRootDisposable, Path.of(it).parent.toString()) }
        val sdk = JavaSdk.getInstance().createJdk("WinRT preview launch validation", System.getProperty("java.home"), false)
        val manager = ProjectManagerEx.getInstanceEx()
        val opened = EdtTestUtil.runInEdtAndGet<com.intellij.openapi.project.Project, Exception> {
            ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk) }
            manager.openProject(root, OpenProjectTask {
                isNewProject = true
                useDefaultProjectAsTemplate = false
                forceOpenInNewFrame = true
                runConfigurators = false
                createModule = false
            })!!
        }
        var scene: ImageComposeScene? = null
        try {
            val design = EdtTestUtil.runInEdtAndGet<WinRTStaticPreviewService, Exception> {
                ApplicationManager.getApplication().runWriteAction { ProjectRootManager.getInstance(opened).projectSdk = sdk }
                GradleSettings.getInstance(opened).linkProject(GradleProjectSettings().apply {
                    externalProjectPath = root.toString().replace('\\', '/')
                    distributionType = DistributionType.DEFAULT_WRAPPED
                    gradleJvm = sdk.name
                })
                val host = app.resolve("build/kotlin-winrt/xaml-sdk-preview/host")
                val module = WinRTModuleData(":app", app.toString(), app.resolve("build").toString(), "2.4.0", "10.0.26100.0",
                    listOf(WinRTSourceSetData("winuiJvmMain", listOf(sources.toString()), emptyList(), emptyList())),
                    emptyList(), emptyList(), emptyList(), staticPreview = WinRTStaticPreviewData("runWinRTXamlSdkPreview",
                        host.resolve("KotlinWinRTXamlPreview.exe").toString(), host.toString(), host.parent.resolve("references.json").toString()))
                opened.service<WinRTProjectService>().replaceBuildModels(root.toString(), listOf(module))
                assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source))
                opened.service<WinRTStaticPreviewService>()
            }
            EdtTestUtil.runInEdtAndWait<Exception>({
                assertFalse(ApplicationManager.getApplication().isWriteIntentLockAcquired)
                scene = ImageComposeScene(800, 700, Density(1f)).also { renderer ->
                    renderer.setContent { SwingBridgeTheme { WinRTPreviewPanel(opened, staticFile = source.toString()) } }
                }
            }, false)
            runBlocking {
                withTimeout(180_000) {
                    while (design.state.value.inspection?.image == null) {
                        EdtTestUtil.runInEdtAndWait<Exception>({ scene!!.render().close() }, false)
                        check(!design.state.value.message.startsWith("The Gradle launch failed")) { design.state.value.message }
                        delay(100)
                    }
                }
            }
            assertTrue(design.state.value.message, design.state.value.connected)
            assertEquals(WinRTXamlHotReloadProtocol.PREVIEW_CLASS, design.state.value.roots.single().className)
            assertTrue(design.state.value.inspection!!.image!!.pixels.isNotEmpty())
            // The repository's Gallery exercises real application dictionaries
            // and authored containers, beyond a single built-in TextBlock.
            val gallery = Path.of(System.getProperty("winrt.ide.toolchain"), "winui-gallery")
            val gallerySources = listOf(gallery.resolve("src/winuiMain/kotlin"), gallery.resolve("resources/src/winuiMain/resources"))
            val files = gallerySources.flatMap { directory -> Files.walk(directory).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".xaml") }.map(Path::toString).toList()
            } }
            val base = gallerySources.first().resolve("io/github/composefluent/winrt/gallery")
            val module = opened.service<WinRTProjectService>().modules.value.single().copy(
                projectDirectory = gallery.toString(), sourceSets = listOf(WinRTSourceSetData("winuiMain", gallerySources.map(Path::toString), emptyList(), emptyList())))
            val catalog = requireNotNull(opened.service<WinRTXamlCatalogService>().forDesigner(module))
            for ((relative, expected) in listOf("basicinput/ToggleButtonPage.xaml" to "ToggleButton", "MainWindow.xaml" to "NavigationView")) {
                val page = base.resolve(relative).toString()
                val documents = WinRTXamlPreviewSources(opened, module, files, page)
                val application = documents.application()!!
                val prepared = WinRTXamlDesignDocument.prepare(documents.read(page), documents.read(application),
                    documents.target(page), documents.target(application), documents::resolve,
                    sdkType = { uri, type -> catalog.resolve(uri, type) != null },
                    visualType = { uri, type -> catalog.resolve(uri, type)?.let(catalog::isVisual) == true }) { uri, type, attribute ->
                    catalog.resolve(uri, type)?.let { catalog.members(it) }.orEmpty().any { it.isEvent && it.name == attribute }
                }
                Files.writeString(root.parent.resolve("preview-gallery-${Path.of(relative).fileName}"), prepared.markup)
                assertTrue("$relative must retain its SDK visual content", prepared.markup.contains("<$expected"))
                val previous = design.state.value.inspection
                val previewRoot = design.state.value.roots.single()
                design.inspect(WinRTXamlInspectionRequest(previewRoot.className, previewRoot.resourcePath, previewMarkup = prepared.markup))
                runBlocking { withTimeout(15_000) { while (design.state.value.inspecting) delay(100) } }
                assertNotNull("$relative: ${design.state.value.message}", design.state.value.inspection)
                assertNotSame("$relative: ${design.state.value.message}", previous, design.state.value.inspection)
                val rendered = design.state.value.inspection!!
                Files.writeString(root.parent.resolve("preview-gallery-${Path.of(relative).fileName}.nodes.txt"), rendered.nodes.joinToString("\n"))
                assertTrue("$relative: ${design.state.value.message}", rendered.nodes.any {
                    it.typeName.endsWith(".$expected") && it.bounds.width > 0 && it.bounds.height > 0
                })
                if (expected == "NavigationView") assertTrue("Nonvisual window properties must not shrink the content layout", rendered.nodes.single {
                    it.typeName.endsWith(".$expected")
                }.bounds.height > 400)
                ImageIO.write(bgraImage(rendered.image!!), "png", root.parent.resolve("preview-gallery-${Path.of(relative).fileName}.png").toFile())
            }
            assertFalse("SDK preview must not compile application classes", Files.exists(app.resolve("build/classes/kotlin/winuiJvm/main")))
        } finally {
            EdtTestUtil.runInEdtAndWait<Exception> { scene?.close() }
            val design = opened.service<WinRTStaticPreviewService>()
            design.stop()
            runBlocking { withTimeout(15_000) { while (design.state.value.busy) delay(100) } }
            EdtTestUtil.runInEdtAndWait<Exception> {
                manager.forceCloseProject(opened)
                ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().removeJdk(sdk) }
            }
            Files.deleteIfExists(source)
        }
    }
}
