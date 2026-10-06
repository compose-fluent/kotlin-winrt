package io.github.composefluent.winrt.ide

import io.github.composefluent.winrt.ide.templates.WinRTTemplateKind
import io.github.composefluent.winrt.ide.templates.WinRTTemplateOptions
import io.github.composefluent.winrt.ide.templates.WinRTTemplates
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.imageio.ImageIO

class WinRTTemplatesTest {
    @Test fun applicationTemplatesPreserveQualifiedIconsAndManifestReferences() {
        listOf(WinRTTemplateKind.ConsoleApplication, WinRTTemplateKind.WinUIApplication).forEach { kind ->
            val files = WinRTTemplates.module(WinRTTemplateOptions("hello", "sample.hello", kind))
            val assets = files.filterKeys { it.endsWith(".png") }
            assertEquals(122, assets.size)
            assertTrue(assets.keys.none { "__MACOSX" in it })
            val manifest = files.getValue("src/main/appxResources/AppxManifest.xml").toString(Charsets.UTF_8)
            listOf("StoreLogo", "MedTile", "AppList", "SmallTile", "WideTile", "LargeTile", "SplashScreen").forEach { name ->
                assertTrue(manifest.contains("Assets\\$name.png"))
                assertNotNull(ImageIO.read(ByteArrayInputStream(files.getValue("src/main/appxResources/Assets/$name.scale-100.png"))))
            }
            val icon = ByteBuffer.wrap(files.getValue("src/main/appxResources/Assets/Application.ico")).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0, icon.short.toInt())
            assertEquals(1, icon.short.toInt())
            assertEquals(6, icon.short.toInt())
            repeat(6) { entry ->
                val size = icon.get(6 + entry * 16).toInt().and(255).let { if (it == 0) 256 else it }
                val length = icon.getInt(6 + entry * 16 + 8)
                val offset = icon.getInt(6 + entry * 16 + 12)
                assertArrayEquals(files.getValue("src/main/appxResources/Assets/AppList.targetsize-$size.png"),
                    icon.array().copyOfRange(offset, offset + length))
            }
        }
        val library = WinRTTemplates.module(WinRTTemplateOptions("controls", "sample.controls", WinRTTemplateKind.WinUIControlLibrary))
        assertFalse(library.keys.any { it.endsWith(".png") || it.endsWith("AppxManifest.xml") })
    }

    @Test fun invalidNamesAndModulePathsCannotEscapeTheTemplate() {
        listOf("../app", "a/b", "NUL", "hello\"", "hello\n").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { WinRTTemplates.module(WinRTTemplateOptions(name, "sample.app", WinRTTemplateKind.WinRTLibrary)) }
        }
        assertThrows(IllegalArgumentException::class.java) { WinRTTemplates.module(WinRTTemplateOptions("app", "sample.when", WinRTTemplateKind.WinRTLibrary)) }
        assertThrows(IllegalArgumentException::class.java) {
            WinRTTemplates.module(WinRTTemplateOptions("app", "sample.app", WinRTTemplateKind.WinRTLibrary, dependencies = listOf("../../other")))
        }
    }

    @Test fun generateStandaloneConsumersForWindowsValidation() {
        val output = System.getProperty("winrt.ide.templateOutput")
        org.junit.Assume.assumeNotNull(output)
        val checkout = Path.of(System.getProperty("winrt.ide.toolchain"))
        val directory = Path.of(requireNotNull(output))
        listOf(WinRTTemplateKind.ConsoleApplication, WinRTTemplateKind.WinUIApplication).forEach { kind ->
            val root = directory.resolve(if (kind.xaml) "winui" else "console")
            val dependencies = if (kind.xaml) listOf(":controls", ":resources", ":library") else emptyList()
            WinRTTemplates.project(WinRTTemplateOptions("hello", "sample.hello", kind, packaged = false, dependencies = dependencies), checkout).forEach { (path, bytes) ->
                val file = root.resolve(path)
                Files.createDirectories(file.parent)
                Files.write(file, bytes, StandardOpenOption.CREATE_NEW)
            }
            if (kind.xaml) {
                val xaml = root.resolve("app/src/main/kotlin/sample/hello/MainWindow.xaml")
                Files.writeString(xaml, Files.readString(xaml).replace("xmlns:x=", "xmlns:controls=\"using:sample.controls\" xmlns:x=")
                    .replace("</StackPanel>", "<controls:GreetingControl />\n    </StackPanel>"))
                listOf("controls" to WinRTTemplateKind.WinUIControlLibrary, "resources" to WinRTTemplateKind.ResourceLibrary,
                    "library" to WinRTTemplateKind.WinRTLibrary).forEach { (name, template) ->
                    WinRTTemplates.module(WinRTTemplateOptions(name, "sample.$name", template)).forEach { (path, bytes) ->
                        val file = root.resolve(name).resolve(path)
                        Files.createDirectories(file.parent)
                        Files.write(file, bytes, StandardOpenOption.CREATE_NEW)
                    }
                    Files.writeString(root.resolve("settings.gradle.kts"), "\ninclude(\":$name\")\n", StandardOpenOption.APPEND)
                }
            }
        }
    }
}
