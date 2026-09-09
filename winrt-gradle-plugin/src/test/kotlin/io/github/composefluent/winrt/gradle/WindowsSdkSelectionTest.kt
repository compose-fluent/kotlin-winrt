package io.github.composefluent.winrt.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class WindowsSdkSelectionTest {
    @Test
    fun installed_versions_are_compared_numerically_and_explicit_selection_is_preserved() {
        val root = Files.createTempDirectory("windows-sdk-selection-")
        listOf("10.0.9999.0", "10.0.10000.2", "10.0.10000.10").forEach { createSdk(root, it) }
        val roots = listOf(root.toString())

        assertEquals("10.0.10000.10", findWindowsSdk(registryRoots = roots)?.version)
        assertEquals("10.0.9999.0", findWindowsSdk(version = "10.0.9999.0", registryRoots = roots)?.version)
    }

    @Test
    fun automatic_sdk_selection_is_shared_and_invalidates_the_configuration_cache() {
        // CsWinRT Samples/AuthoringDemo/WinUI3CppApp owns SDK/minimum versions in build properties.
        assumeTrue(isWindowsHost())
        val root = Files.createTempDirectory("windows-sdk-cache-")
        val sdkRoot = root.resolve("sdk")
        createSdk(sdkRoot, "10.0.19041.0")
        Files.writeString(root.resolve("settings.gradle"), "rootProject.name = 'sdk-selection'")
        Files.writeString(root.resolve("build.gradle"), """
            plugins { id 'java'; id 'io.github.compose-fluent.winrt' }
            def selectedSdk = providers.of(io.github.composefluent.winrt.gradle.WindowsSdkVersionValueSource) {
                parameters.registryRoots.set([file('sdk').absolutePath])
            }
            winRT.windowsSdkVersion.convention(selectedSdk)
            winRT {
                windowsSdk(null, false, false)
                application {
                    mainClass = 'sample.Main'
                    minWindowsVersion = '10.0.17763.0'
                    variants { create('desktop') { variantName = 'jvm:main' } }
                }
            }
            tasks.register('writeSdkSelection', WriteProperties) {
                destinationFile = layout.buildDirectory.file('selected-sdk.properties')
                property('projection', tasks.named('generateWinRTProjections').get().windowsSdkVersion.get())
                property('host', tasks.named('buildWinRTApplicationHostDesktop').get().windowsSdkVersion.get())
                property('minimum', winRT.application.minWindowsVersion.get())
                property('normal', tasks.named('stageWinRTApplicationPackageDesktop').get().maxVersionTested.get())
                property('dev', tasks.named('stageWinRTApplicationDevelopmentPackageDesktop').get().maxVersionTested.get())
            }
        """.trimIndent())
        fun runner() = GradleRunner.create()
            .withProjectDir(root.toFile())
            .withPluginClasspath()
            .withArguments("writeSdkSelection", "--configuration-cache", "--console=plain", "--max-workers=1")
        fun assertSelected(version: String) {
            val values = Properties().apply {
                Files.newInputStream(root.resolve("build/selected-sdk.properties")).use(::load)
            }
            listOf("projection", "host", "normal", "dev").forEach { key ->
                assertEquals(key, version, values.getProperty(key))
            }
            assertEquals("10.0.17763.0", values.getProperty("minimum"))
        }

        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":writeSdkSelection")?.outcome)
        assertSelected("10.0.19041.0")
        val unchanged = runner().build()
        assertTrue(unchanged.output, unchanged.output.contains("Reusing configuration cache"))
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":writeSdkSelection")?.outcome)

        createSdk(sdkRoot, "10.0.26100.0")
        assertEquals(TaskOutcome.SUCCESS, runner().build().task(":writeSdkSelection")?.outcome)
        assertSelected("10.0.26100.0")
    }

    private fun createSdk(root: Path, version: String) {
        Files.createDirectories(root.resolve("Include/$version/um"))
        Files.createDirectories(root.resolve("Lib/$version"))
        Files.createDirectories(root.resolve("bin/$version"))
    }
}
