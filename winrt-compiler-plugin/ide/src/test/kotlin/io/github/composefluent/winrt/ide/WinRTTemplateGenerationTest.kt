package io.github.composefluent.winrt.ide

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.github.composefluent.winrt.ide.templates.WinRTTemplateKind
import io.github.composefluent.winrt.ide.templates.WinRTTemplateOptions
import io.github.composefluent.winrt.ide.templates.WinRTTemplates
import io.github.composefluent.winrt.ide.templates.WinRTTemplateWriter
import java.nio.file.Files
import java.nio.file.Path

/** Produces fresh CLI consumers through the same native commands as the wizard.
 * Projection ownership follows .cswinrt/src/Projections; no handwritten SDK types
 * or generated Kotlin stand-ins are added to make subsequent import tests pass.
 */
class WinRTTemplateGenerationTest : BasePlatformTestCase() {
    fun testCreatePortableProjectWithoutAToolchainCheckout() {
        val checkout = Path.of(System.getProperty("winrt.ide.toolchain")).toRealPath()
        val root = Path.of(requireNotNull(System.getProperty("winrt.ide.templateOutput"))).resolve("portable").toAbsolutePath().normalize()
        require(root.startsWith(checkout.resolve(".gradle")) && !Files.exists(root))
        WinRTTemplateWriter.create(project, root, WinRTTemplates.project(
            WinRTTemplateOptions("portable", "sample.portable", WinRTTemplateKind.ConsoleApplication, packaged = false)))
        FileDocumentManager.getInstance().saveAllDocuments()
        val settings = Files.readString(root.resolve("settings.gradle.kts"))
        assertFalse(settings.contains("includeBuild"))
        assertFalse(settings.contains(checkout.toString()))
        assertTrue(settings.contains(".kotlin-winrt/toolchain/repository"))
        assertTrue(Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar")))
    }

    fun testCreateStandaloneApplicationsAndModulesThroughNativeCommands() {
        val checkout = Path.of(System.getProperty("winrt.ide.toolchain")).toRealPath()
        val directory = Path.of(requireNotNull(System.getProperty("winrt.ide.templateOutput")))
            .toAbsolutePath().normalize()
        require(directory.startsWith(checkout.resolve(".gradle")))
        require(!Files.exists(directory)) { "Validation requires a fresh output directory." }
        listOf(WinRTTemplateKind.ConsoleApplication, WinRTTemplateKind.WinUIApplication).forEach { kind ->
            val root = directory.resolve(if (kind.xaml) "winui" else "console")
            val dependencies = if (kind.xaml) listOf(":controls", ":resources", ":library") else emptyList()
            WinRTTemplateWriter.create(project, root, WinRTTemplates.project(
                WinRTTemplateOptions("hello", "sample.hello", kind, packaged = false, dependencies = dependencies), checkout))
            if (kind.xaml) {
                listOf("controls" to WinRTTemplateKind.WinUIControlLibrary, "resources" to WinRTTemplateKind.ResourceLibrary,
                    "library" to WinRTTemplateKind.WinRTLibrary).forEach { (name, template) ->
                    WinRTTemplateWriter.create(project, root.resolve(name),
                        WinRTTemplates.module(WinRTTemplateOptions(name, "sample.$name", template)), root)
                }
                val source = root.resolve("app/src/main/kotlin/sample/hello/MainWindow.xaml")
                val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(source)!!
                val document = FileDocumentManager.getInstance().getDocument(file)!!
                WriteCommandAction.runWriteCommandAction(project) {
                    document.setText(document.text.replace("xmlns:x=", "xmlns:controls=\"using:sample.controls\" xmlns:x=")
                        .replace("</StackPanel>", "<controls:GreetingControl />\n    </StackPanel>"))
                }
            }
            FileDocumentManager.getInstance().saveAllDocuments()
            assertTrue(Files.isRegularFile(root.resolve("gradlew.bat")))
            Files.walk(root).use { paths -> assertFalse(paths.anyMatch { it.toString().endsWith(".java") }) }
            if (kind.xaml) {
                val settings = Files.readString(root.resolve("settings.gradle.kts"))
                dependencies.forEach { assertTrue(settings, settings.contains("include(\"$it\")")) }
                assertFalse("Editing preparation must precede application compilation", Files.exists(root.resolve("app/build")))
            }
        }
    }
}
