package io.github.composefluent.windows.toolkit.gradle

import io.github.composefluent.winrt.runtime.XamlFramework
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class XamlCompilerConfigurationTest {
    private fun configuration(): Pair<Project, WindowsExtension> {
        val project = ProjectBuilder.builder().build()
        return project to project.objects.newInstance(WindowsExtension::class.java, project)
    }

    @Test fun winui_keeps_its_published_default_version_checksum_and_archive() {
        val (_, windows) = configuration()
        assertEquals("0.1.0-preview.6", windows.xaml.compilerVersion.get())
        assertEquals("7718088e70e1d4e95446e0b1e6891fcce09ab70c82ba3ae6027d6aaa43fbc628",
            windows.xaml.archiveSha256.get())
        assertEquals("https://github.com/compose-fluent/microsoft-ui-xaml/releases/download/" +
            "kotlin-xamlc-v0.1.0-preview.6/kotlin-xamlc-0.1.0-preview.6-win-x64.zip", windows.xaml.archiveUrl.get())
    }

    @Test fun selecting_uwp_does_not_reuse_the_winui_release_or_checksum() {
        val (_, windows) = configuration()
        assertFalse(windows.xaml.archiveUrl.get().isEmpty())
        windows.application.applicationModel.set(WindowsApplicationModel.Uwp)
        assertEquals(XamlFramework.Uwp, windows.xaml.framework.get())
        assertEquals("0.1.0-preview.7", windows.xaml.compilerVersion.get())
        assertEquals("c771ea8a7875500ed293e8da7d9de0b27d87695d9b4a8999143d50a2e63696d2", windows.xaml.archiveSha256.get())
        assertEquals("https://github.com/compose-fluent/microsoft-ui-xaml/releases/download/" +
            "kotlin-xamlc-v0.1.0-preview.7/kotlin-xamlc-0.1.0-preview.7-uwp-win-x64.zip", windows.xaml.archiveUrl.get())
    }

    @Test fun explicit_uwp_release_requires_its_own_checksum_and_uses_the_uwp_archive() {
        val (project, windows) = configuration()
        windows.xaml.framework.set(XamlFramework.Uwp)
        windows.xaml.compilerVersion.set("1.2.3")
        assertEquals("", windows.xaml.archiveSha256.get())
        assertTrue(resolveFailure(project, windows).contains("windows.xaml.archiveSha256"))
        windows.xaml.archiveSha256.set("b".repeat(64))
        assertEquals("b".repeat(64), windows.xaml.archiveSha256.get())
        assertEquals("https://github.com/compose-fluent/microsoft-ui-xaml/releases/download/" +
            "kotlin-xamlc-v1.2.3/kotlin-xamlc-1.2.3-uwp-win-x64.zip", windows.xaml.archiveUrl.get())
    }

    @Test fun explicitly_empty_uwp_version_is_rejected_even_with_a_checksum() {
        val (project, windows) = configuration()
        windows.xaml.framework.set(XamlFramework.Uwp)
        windows.xaml.compilerVersion.set("")
        windows.xaml.archiveSha256.set("b".repeat(64))
        assertTrue(resolveFailure(project, windows).contains("windows.xaml.compilerVersion"))
        assertEquals("", windows.xaml.archiveUrl.get())
    }

    @Test fun changing_winui_version_does_not_reuse_the_default_releases_checksum() {
        val (project, windows) = configuration()
        windows.xaml.compilerVersion.set("1.2.3")
        assertEquals("", windows.xaml.archiveSha256.get())
        assertTrue(resolveFailure(project, windows).contains("windows.xaml.archiveSha256"))
        assertTrue(windows.xaml.archiveUrl.get().endsWith("kotlin-xamlc-1.2.3-win-x64.zip"))
    }

    @Test fun local_uwp_override_skips_download_inputs_with_configuration_cache() {
        // Exercise the actual task graph: local packages require neither a published
        // release nor download inputs, including when Gradle serializes a skipped task.
        val root = Files.createTempDirectory("uwp-xaml-compiler-override-")
        Files.createDirectories(root.resolve("compiler"))
        Files.createDirectories(root.resolve("src/main/kotlin"))
        Files.writeString(root.resolve("src/main/kotlin/Resources.xaml"),
            """<ResourceDictionary xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" />""")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'uwp-xaml-override'\n")
        Files.writeString(root.resolve("gradle.properties"), """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
        """.trimIndent())
        Files.writeString(root.resolve("build.gradle"), """
            plugins {
                id 'org.jetbrains.kotlin.jvm'
                id 'io.github.compose-fluent.windows-toolkit'
            }
            windows.xaml {
                framework = io.github.composefluent.winrt.runtime.XamlFramework.Uwp
                compilerDirectory = layout.projectDirectory.dir('compiler')
            }
        """.trimIndent())
        fun run() = GradleRunner.create().withProjectDir(root.toFile()).withPluginClasspath()
            .withArguments("resolveWinRTXamlCompiler", "--offline", "--configuration-cache", "--stacktrace").build()
        assertEquals(TaskOutcome.SKIPPED, run().task(":resolveWinRTXamlCompiler")?.outcome)
        val reused = run()
        assertEquals(TaskOutcome.SKIPPED, reused.task(":resolveWinRTXamlCompiler")?.outcome)
        assertTrue(reused.output, reused.output.contains("Reusing configuration cache"))
    }

    private fun resolveFailure(project: Project, windows: WindowsExtension): String {
        val task = project.tasks.create("resolveCompilerForTest", ResolveWinUiXamlCompilerTask::class.java)
        task.compilerVersion.set(windows.xaml.compilerVersion)
        task.archiveSha256.set(windows.xaml.archiveSha256)
        task.archiveUrl.set(windows.xaml.archiveUrl)
        val failure = runCatching { task.resolve() }.exceptionOrNull()
        assertNotNull("Expected a compiler download configuration error", failure)
        return failure!!.message.orEmpty()
    }
}
