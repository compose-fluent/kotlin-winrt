package io.github.composefluent.winrt.ide

import io.github.composefluent.winrt.ide.templates.WinRTTemplateKind
import io.github.composefluent.winrt.ide.templates.WinRTTemplateOptions
import io.github.composefluent.winrt.ide.templates.WinRTTemplates
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

}
