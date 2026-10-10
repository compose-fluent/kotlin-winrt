package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.ide.model.WinRTIdeModel
import org.gradle.tooling.GradleConnector
import org.gradle.util.GradleVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Properties

/** Cross-process validation of Gradle model registration and its public interface proxies. */
class WinRTIdeToolingApiTest {
    /** The first-sync projection source contract must survive import optimizations. */
    @Test
    fun first_sync_exposes_projection_sources_without_compilation_and_warm_sync_repairs_them() {
        val directory = Files.createTempDirectory("winrt-ide-tooling-sources-").toFile()
        val metadata = Properties().apply {
            WinRTIdeToolingApiTest::class.java.classLoader.getResourceAsStream("plugin-under-test-metadata.properties")!!.use(::load)
        }
        val classpath = metadata.getProperty("implementation-classpath").split(File.pathSeparator)
            .joinToString(", ") { "\"${it.replace('\\', '/').replace("$", "\\$")}\"" }
        val winmd = directory.toPath().resolve("Sample.winmd")
        io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter.writeProjectionFixtureWinmd(
            assemblyName = "Sample",
            interfaces = listOf(io.github.composefluent.winrt.metadata.WinRTPortableExecutableInterfaceDescriptor(
                interfaceName = "Sample.IProbe", iid = "00000000-0000-0000-0000-000000000001",
            )),
            runtimeClasses = emptyList(), outputFile = winmd,
        )
        directory.resolve("settings.gradle.kts").writeText("rootProject.name = \"ide-import-sources\"\n")
        directory.resolve("gradle.properties").writeText("org.gradle.jvmargs=-Xmx768m\norg.gradle.workers.max=1\n")
        directory.resolve("build.gradle.kts").writeText("""
            buildscript { dependencies { classpath(files($classpath)) } }
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "io.github.compose-fluent.windows-toolkit")
            configure<io.github.composefluent.windows.toolkit.gradle.WindowsExtension> {
                packageReferences { winmd("${winmd.toString().replace('\\', '/')}"); type("Sample.IProbe") }
            }
            tasks.configureEach { doFirst { error("Sync must expose projection sources without compiling") } }
        """.trimIndent())
        try {
            GradleConnector.newConnector().forProjectDirectory(directory)
                .useGradleVersion(GradleVersion.current().version).connect().use { connection ->
                    fun sync() = connection.model(WinRTIdeModel::class.java)
                        .setJavaHome(File(System.getProperty("java.home")))
                        .withArguments("--no-configuration-cache").get()
                    fun projection(model: WinRTIdeModel): File = model.sourceSets.flatMap { it.kotlinRoots }
                        .flatMap { root -> File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
                        .first { it.readText().contains("interface IProbe") }
                    val source = projection(sync())
                    val contents = source.readText()
                    val timestamp = source.lastModified()
                    assertEquals(source, projection(sync()))
                    assertEquals(timestamp, source.lastModified())
                    assertTrue(source.delete())
                    assertEquals(source, projection(sync()))
                    assertEquals(contents, source.readText())
                    assertTrue(!directory.resolve("build/classes").exists())
                }
        } finally {
            check(directory.canonicalFile.parentFile == File(System.getProperty("java.io.tmpdir")).canonicalFile)
            check(directory.name.startsWith("winrt-ide-tooling-"))
            directory.deleteRecursively()
        }
    }

    @Test
    fun tooling_api_imports_the_model_without_running_restore_or_projection_tasks() {
        val directory = Files.createTempDirectory("winrt-ide-tooling-").toFile()
        val metadata = Properties().apply {
            WinRTIdeToolingApiTest::class.java.classLoader.getResourceAsStream("plugin-under-test-metadata.properties")!!.use(::load)
        }
        val classpath = metadata.getProperty("implementation-classpath").split(File.pathSeparator)
            .joinToString(", ") { "\"${it.replace('\\', '/').replace("$", "\\$")}\"" }
        directory.resolve("settings.gradle.kts").writeText("rootProject.name = \"ide-import\"\n")
        directory.resolve("gradle.properties").writeText(
            "org.gradle.jvmargs=-Xmx512m -Dfile.encoding=UTF-8\norg.gradle.workers.max=1\n",
        )
        directory.resolve("src/main/kotlin/Shell.xaml").apply {
            parentFile.mkdirs()
            writeText("""<Page xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" x:Class="sample.Shell"/>""")
        }
        directory.resolve("src/main/kotlin/Shell.kt").writeText("package sample\nclass Shell\n")
        directory.resolve("build.gradle.kts").writeText(
            """
            buildscript { dependencies { classpath(files($classpath)) } }
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "io.github.compose-fluent.windows-toolkit")
            configure<io.github.composefluent.windows.toolkit.gradle.WindowsExtension> {
                application {
                    mainClass = "sample.MainKt"
                    packageType = io.github.composefluent.windows.toolkit.gradle.WindowsPackageType.None
                    minWindowsVersion = "10.0.19041.0"
                }
                packageReferences {
                    windowsSdk("10.0.26100.0")
                    nugetPackage("Microsoft.WindowsAppSDK", "2.2.0")
                }
            }
            tasks.configureEach {
                doFirst { throw GradleException("IDE import must not execute tasks") }
            }
            """.trimIndent(),
        )
        try {
            GradleConnector.newConnector().forProjectDirectory(directory)
                .useGradleVersion(GradleVersion.current().version).connect().use { connection ->
                    val model = connection.model(WinRTIdeModel::class.java)
                        .setJavaHome(File(System.getProperty("java.home")))
                        .withArguments("--no-configuration-cache").get()
                    assertTrue(model.isEnabled)
                    assertEquals(WinRTIdeModel.SCHEMA_VERSION, model.schemaVersion)
                    assertEquals(":", model.projectPath)
                    assertTrue(model.sourceSets.any { it.name == "main" })
                    assertEquals("Microsoft.WindowsAppSDK", model.nuGetPackages.single().id)
                    assertEquals("2.2.0", model.nuGetPackages.single().version)
                    assertTrue(model.xamlCompilations.isNotEmpty())
                    val xaml = model.xamlCompilations.first()
                    assertTrue(xaml.taskName.startsWith("analyzeWinRTXaml"))
                    assertTrue(xaml.sourceRoots.contains(directory.resolve("src/main/kotlin").path))
                    assertTrue(xaml.declarationsFile.endsWith("declarations.json"))
                    assertTrue(!File(xaml.declarationsFile).exists())
                    assertTrue(model.restoreLockFiles.isNotEmpty())
                    val layout = model.packageLayouts.first { it.taskName.startsWith("stageWinAppPackage") }
                    assertEquals("10.0.19041.0", layout.minWindowsVersion)
                    assertEquals("10.0.26100.0", layout.maxVersionTested)
                    assertTrue(layout.resourceReportFile.endsWith("appx-resource-resolution.json"))
                    assertTrue(!File(layout.resourceReportFile).exists())
                    val launch = model.hotReloadLaunches.first()
                    assertTrue(launch.taskName.startsWith("runWinAppHost"))
                    assertTrue(launch.executable.endsWith(".exe"))
                    assertTrue(!File(launch.executable).exists())
                }
        } finally {
            check(directory.canonicalFile.parentFile == File(System.getProperty("java.io.tmpdir")).canonicalFile)
            check(directory.name.startsWith("winrt-ide-tooling-"))
            directory.deleteRecursively()
        }
    }
}
