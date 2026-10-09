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
    @Test fun normalProjectUsesAPortableMavenToolchain() {
        val files = WinRTTemplates.project(WinRTTemplateOptions("hello", "sample.hello", WinRTTemplateKind.WinUIApplication))
        val settings = files.getValue("settings.gradle.kts").toString(Charsets.UTF_8)
        assertFalse(settings.contains("includeBuild"))
        assertTrue(settings.contains("id(\"io.github.compose-fluent.windows-toolkit\") version"))
        assertTrue(files.keys.any { it.contains("winrt-runtime-jvm/0.1.0-SNAPSHOT/") && it.endsWith(".jar") })
        assertTrue(files.keys.any { it.contains("callsite-lowering/0.1.0-SNAPSHOT/") && it.endsWith(".jar") })
        listOf("winrt-runtime", "winrt-authoring").forEach { module ->
            assertTrue(files.keys.any { it.contains("$module-mingwx64/0.1.0-SNAPSHOT/") && it.endsWith(".klib") })
        }
        assertTrue(files.keys.none { it.endsWith(".java") })
    }

    @Test fun applicationTemplatesPreserveQualifiedIconsAndManifestReferences() {
        listOf(WinRTTemplateKind.ConsoleApplication, WinRTTemplateKind.WinUIApplication).forEach { kind ->
            val files = WinRTTemplates.module(WinRTTemplateOptions("hello", "sample.hello", kind))
            val assets = files.filterKeys { it.endsWith(".png") }
            assertEquals(122, assets.size)
            assertTrue(assets.keys.none { "__MACOSX" in it })
            val manifest = files.getValue("src/winuiMain/appxResources/AppxManifest.xml").toString(Charsets.UTF_8)
            listOf("StoreLogo", "MedTile", "AppList", "SmallTile", "WideTile", "LargeTile", "SplashScreen").forEach { name ->
                assertTrue(manifest.contains("Assets\\$name.png"))
                assertNotNull(ImageIO.read(ByteArrayInputStream(files.getValue("src/winuiMain/appxResources/Assets/$name.scale-100.png"))))
            }
            val icon = ByteBuffer.wrap(files.getValue("src/winuiMain/appxResources/Assets/Application.ico")).order(ByteOrder.LITTLE_ENDIAN)
            assertEquals(0, icon.short.toInt())
            assertEquals(1, icon.short.toInt())
            assertEquals(6, icon.short.toInt())
            repeat(6) { entry ->
                val size = icon.get(6 + entry * 16).toInt().and(255).let { if (it == 0) 256 else it }
                val length = icon.getInt(6 + entry * 16 + 8)
                val offset = icon.getInt(6 + entry * 16 + 12)
                assertArrayEquals(files.getValue("src/winuiMain/appxResources/Assets/AppList.targetsize-$size.png"),
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

    @Test fun targetsDefaultToBothAndAtLeastOneIsRequired() {
        val options = WinRTTemplateOptions("app", "sample.app", WinRTTemplateKind.WinUIApplication)
        assertTrue(options.jvm)
        assertTrue(options.mingwX64)
        assertThrows(IllegalArgumentException::class.java) { options.copy(jvm = false, mingwX64 = false).validate() }
    }

    @Test fun selectedTargetsControlApplicationsLibrariesAndSharedSdkProjects() {
        listOf(true to true, true to false, false to true).forEach { (jvm, mingw) ->
            WinRTTemplateKind.entries.forEach { kind ->
                val options = WinRTTemplateOptions("hello", "sample.hello", kind, dependencies = listOf(":controls"),
                    jvm = jvm, mingwX64 = mingw)
                val files = WinRTTemplates.module(options)
                val script = files.getValue("build.gradle.kts").toString(Charsets.UTF_8)
                assertEquals(mingw, script.contains("mingwX64"))
                assertEquals(jvm, script.contains("jvmToolchain"))
                assertEquals(jvm && kind.application, script.contains("runTask(\"runWindows\")"))
                assertEquals(mingw && kind.application, script.contains("entryPoint = \"sample.hello.main\""))
                val sourceSet = if (mingw) "winuiMain" else "main"
                assertTrue(files.keys.filter { it.startsWith("src/") }.all { it.startsWith("src/$sourceSet/") })
                assertTrue(files.keys.none { it.endsWith(".java") })
            }
            val project = WinRTTemplates.project(WinRTTemplateOptions("hello", "sample.hello", WinRTTemplateKind.WinUIApplication,
                jvm = jvm, mingwX64 = mingw))
            listOf("app", "winrt-projections").forEach { module ->
                val script = project.getValue("$module/build.gradle.kts").toString(Charsets.UTF_8)
                assertEquals(mingw, script.contains("mingwX64"))
                assertEquals(jvm, script.contains("jvmToolchain"))
            }
            assertTrue(project.getValue("settings.gradle.kts").toString(Charsets.UTF_8).contains("kotlin(\"multiplatform\") version"))
        }
    }

}
