package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.util.Properties

class NuGetConfigHierarchyTest {
    @Test
    fun configuration_cache_ignores_siblings_and_tracks_config_additions_removals_and_content() {
        // Microsoft.Windows.CsWinRT.targets separates NuGet/reference inputs from projection compilation.
        // Hierarchical restore configuration must stay an input without treating unrelated files as one.
        assumeTrue(isWindowsHost())
        val parent = Files.createTempDirectory("winrt-nuget-config-cache-")
        val root = Files.createDirectories(parent.resolve("project"))
        val ancestorConfig = parent.resolve("nuget.config")
        Files.writeString(ancestorConfig, "<configuration />")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'nuget-config-inputs'")
        Files.writeString(root.resolve("gradle.properties"), "org.gradle.jvmargs=-Xmx768m -XX:+UseSerialGC")
        Files.writeString(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.windows-toolkit' }
            def configs = tasks.named('restoreWinAppDependencies').get().nugetConfigHierarchyFiles
            tasks.register('writeNuGetConfigInputs', WriteProperties) {
                destinationFile = layout.buildDirectory.file('nuget-configs.properties')
                property('configs', configs.files.collect { it.absolutePath }.sort().join('\n'))
                inputs.files(configs)
            }
        """.trimIndent())
        fun runner() = GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withArguments("writeNuGetConfigInputs", "--configuration-cache", "--console=plain", "--max-workers=1")
        fun configPaths(): Set<String> = Properties().apply {
            Files.newInputStream(root.resolve("build/nuget-configs.properties")).use(::load)
        }.getProperty("configs").lines().toSet()

        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":writeNuGetConfigInputs")?.outcome)
        assertTrue(configPaths().contains(ancestorConfig.toString()))

        Files.writeString(parent.resolve("unrelated.log"), "A sibling project or log is not a NuGet input.")
        Files.createDirectories(root.resolve("unrelated-directory"))
        val siblingsChanged = runner().build()
        assertTrue(siblingsChanged.output, siblingsChanged.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, siblingsChanged.task(":writeNuGetConfigInputs")?.outcome)

        Files.writeString(ancestorConfig, "<configuration><packageSources><clear /></packageSources></configuration>")
        val contentChanged = runner().build()
        assertTrue(contentChanged.output, contentChanged.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SUCCESS, contentChanged.task(":writeNuGetConfigInputs")?.outcome)

        val projectConfig = root.resolve("NuGet.Config")
        Files.writeString(projectConfig, "<configuration />")
        val added = runner().build()
        assertFalse(added.output, added.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SUCCESS, added.task(":writeNuGetConfigInputs")?.outcome)
        assertTrue(configPaths().contains(projectConfig.toString()))

        Files.delete(projectConfig)
        val removed = runner().build()
        assertFalse(removed.output, removed.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.SUCCESS, removed.task(":writeNuGetConfigInputs")?.outcome)
        assertFalse(configPaths().contains(projectConfig.toString()))
    }
}
