package io.github.composefluent.winrt.ide

import org.junit.Assert.*
import io.github.composefluent.winrt.ide.templates.WinRTBundledToolchain
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Path
import javax.imageio.ImageIO

class WinRTTemplateClassLoaderTest {
    @Test fun packagedProjectLoadsToolchainAndIconsWithPlatformOwnedKotlinCollections() {
        val pluginJar = Path.of(requireNotNull(System.getProperty("winrt.ide.pluginJar")))
        val kotlinResource = requireNotNull(Class.forName("kotlin.collections.builders.MapBuilder").getResource("MapBuilder.class"))
        require(kotlinResource.protocol == "jar")
        val kotlinJar = URI(kotlinResource.toExternalForm().removePrefix("jar:").substringBefore("!/")).toURL()

        // An installed IDE owns Kotlin's MapBuilder in its parent loader. Unlike
        // the ordinary test classpath, that loader cannot see plugin resources.
        URLClassLoader(arrayOf(kotlinJar), ClassLoader.getPlatformClassLoader()).use { platform ->
            URLClassLoader(arrayOf(pluginJar.toUri().toURL()), platform).use { plugin ->
                assertNull(platform.getResource("templates/toolchain.zip"))
                assertNull(platform.getResource("templates/application-assets.zip"))
                assertSame(platform, platform.loadClass("kotlin.collections.builders.MapBuilder").classLoader)

                val bundleType = plugin.loadClass("io.github.composefluent.winrt.ide.templates.WinRTBundledToolchain")
                assertSame(plugin, bundleType.classLoader)
                assertEquals(true, bundleType.getMethod("available").invoke(bundleType.getField("INSTANCE").get(null)))

                val kindType = plugin.loadClass("io.github.composefluent.winrt.ide.templates.WinRTTemplateKind")
                val optionsType = plugin.loadClass("io.github.composefluent.winrt.ide.templates.WinRTTemplateOptions")
                val options = optionsType.getConstructor(
                    String::class.java, String::class.java, kindType, String::class.java, String::class.java,
                    Boolean::class.javaPrimitiveType, List::class.java, String::class.java, String::class.java,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                ).newInstance(
                    "hello", "sample.hello", kindType.getField("WinUIApplication").get(null),
                    "10.0.26100.0", "2.5.1", true, emptyList<String>(), "hello", ":winrt-projections", true,
                    true, true,
                )
                val templatesType = plugin.loadClass("io.github.composefluent.winrt.ide.templates.WinRTTemplates")
                val files = templatesType.getMethod("project", optionsType)
                    .invoke(templatesType.getField("INSTANCE").get(null), options) as Map<*, *>

                val paths = files.keys.map { it as String }
                assertTrue(paths.any { it.contains("winrt-runtime-jvm/${WinRTBundledToolchain.VERSION}/") && it.endsWith(".jar") })
                assertTrue(paths.any { it.contains("windows-toolkit-gradle-plugin/${WinRTBundledToolchain.VERSION}/") && it.endsWith(".pom") })
                assertTrue(paths.contains("gradle/wrapper/gradle-wrapper.jar"))
                assertEquals(122, paths.count { it.endsWith(".png") })
                assertTrue(paths.any { it.contains("winrt-runtime-mingwx64/${WinRTBundledToolchain.VERSION}/") && it.endsWith(".klib") })
                assertNotNull(ImageIO.read(ByteArrayInputStream(files["app/src/winuiMain/appxResources/Assets/AppList.scale-100.png"] as ByteArray)))
                assertTrue(paths.contains("app/src/winuiMain/appxResources/AppxManifest.xml"))
                assertTrue(paths.contains("app/src/winuiMain/kotlin/sample/hello/MainWindow.kt"))
                assertTrue(paths.none { it.endsWith(".java") })
            }
        }
    }
}
