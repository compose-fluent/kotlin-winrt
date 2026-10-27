package io.github.composefluent.winrt.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.concurrent.TimeUnit
import kotlin.test.*

class WinUIXamlDevelopmentBuildTest {
    @Test fun launch_environment_and_properties_cannot_enable_production_runtime() = exercise(false)
    @Test fun isolated_development_archive_preserves_preview_and_authenticated_reload() = exercise(true)

    private fun exercise(development: Boolean) {
        val directory = Files.createTempDirectory("winrt-build-policy-")
        try {
            val classes = Path.of(Class.forName("io.github.composefluent.winrt.runtime.WinUIXamlDevelopmentBuildPolicy")
                .protectionDomain.codeSource.location.toURI())
            val archive = directory.resolve("runtime.jar")
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
                if (development) mainAttributes.putValue("Kotlin-WinRT-Development", "true")
            }
            JarOutputStream(Files.newOutputStream(archive), manifest).use { output ->
                Files.walk(classes).use { paths -> paths.filter(Files::isRegularFile).forEach { file ->
                    val name = classes.relativize(file).toString().replace('\\', '/')
                    if (!name.equals("META-INF/MANIFEST.MF", true)) {
                        output.putNextEntry(JarEntry(name)); Files.copy(file, output); output.closeEntry()
                    }
                } }
            }
            val classpath = archive.toString() + File.pathSeparator + System.getProperty("java.class.path")
            val log = directory.resolve("probe.log")
            val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
                "-DKotlin-WinRT-Development=true", "-DKOTLIN_WINRT_XAML_PREVIEW=1", "-cp", classpath,
                WinUIXamlDevelopmentBuildProbe::class.java.name, development.toString(),
                "--kotlin-winrt-xaml-preview", "--kotlin-winrt-hot-reload-directory=" + directory)
                .apply {
                    environment()[WinUIXamlHotReloadProtocol.SESSION_DIRECTORY] = directory.toString()
                    environment()[WinUIXamlHotReloadProtocol.PREVIEW_ENVIRONMENT] = "1"
                    environment()[WinUIXamlHotReloadProtocol.PREVIEW_THEME_ENVIRONMENT] = "Dark"
                }.redirectErrorStream(true).redirectOutput(log.toFile()).start()
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Policy probe timed out")
                assertEquals(0, process.exitValue(), Files.readString(log))
                assertFalse(Files.list(directory).use { it.anyMatch { p -> p.toString().endsWith(".session") } })
            } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
        } finally { directory.toFile().deleteRecursively() }
    }
}

object WinUIXamlDevelopmentBuildProbe {
    @JvmStatic fun main(args: Array<String>) {
        val expected = args[0].toBooleanStrict()
        check(isWinUIXamlDevelopmentBuild() == expected)
        check(isWinUIXamlPreviewHost() == expected)
        check(winUIXamlPreviewStartupTheme() == if (expected) "Dark" else "Default")
        configureWinUIXamlHotReload({ { it(); true } }, { _, value -> value })
        check(isWinUIXamlHotReloadEnabled() == expected)
        val directory = Path.of(System.getenv(WinUIXamlHotReloadProtocol.SESSION_DIRECTORY))
        val sessions = Files.list(directory).use { paths -> paths.filter { it.toString().endsWith(".session") }.toList() }
        check(sessions.size == if (expected) 1 else 0)
        sessions.forEach { session ->
            val info = java.util.Properties().apply { Files.newInputStream(session).use(::load) }
            java.net.Socket(java.net.InetAddress.getLoopbackAddress(), info.getProperty("port").toInt()).use { socket ->
                socket.soTimeout = 5000
                WinUIXamlHotReloadWire.writeRequest(socket.getOutputStream(), info.getProperty("token"), null)
                check(WinUIXamlHotReloadWire.readReply(socket.getInputStream()).status == WinUIXamlHotReloadProtocol.APPLIED)
            }
        }
        println("JVM development policy verified: $expected")
    }
}
