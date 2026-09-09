package io.github.composefluent.winrt.gradle

import org.gradle.api.GradleException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinAppConfigurationTest {
    @Test
    fun sdk_toolchain_revision_is_explicit_and_independent_from_manifest_os_versions() {
        val project = org.gradle.testfixtures.ProjectBuilder.builder().build()
        project.pluginManager.apply(KotlinWinRTPlugin::class.java)
        val extension = project.extensions.getByType(WinRTExtension::class.java)
        extension.windowsSdk("10.0.26100.0")
        extension.windowsSdkToolsVersion.set("10.0.26100.4654")
        extension.nugetPackage("Microsoft.WindowsAppSDK", "2.2.0")
        val task = project.tasks.named("generateWinAppConfiguration", GenerateWinAppConfigurationTask::class.java).get()
        task.generate()
        val yaml = java.nio.file.Files.readString(task.outputFile.get().asFile.toPath())
        assertTrue(yaml, yaml.contains("name: Microsoft.Windows.SDK.CPP\n    version: 10.0.26100.4654"))
        assertTrue(yaml, yaml.contains("name: Microsoft.Windows.SDK.BuildTools\n    version: 10.0.26100.4654"))
        assertEquals("10.0.26100.0", extension.application.maxVersionTested.get())
        extension.windowsSdkToolsVersion.set("latest")
        val error = runCatching { task.generate() }.exceptionOrNull()
        assertTrue(error?.message.orEmpty().contains("exact version"))
    }

    @Test
    fun generated_configuration_is_sorted_and_includes_pinned_tooling() {
        val packages = resolveWinAppPackagePins(
            packageSpecs = listOf(
                "WinUIEssential.WinUI3@1.8.0",
                "Microsoft.WindowsAppSDK@2.2.0",
                "microsoft.windowsappsdk@2.2.0",
            ),
        )

        assertEquals(
            listOf(
                WinAppPackagePin("Microsoft.Windows.CppWinRT", "2.0.240405.15"),
                WinAppPackagePin("Microsoft.Windows.SDK.BuildTools", "10.0.26100.1742"),
                WinAppPackagePin("Microsoft.Windows.SDK.CPP", "10.0.26100.1742"),
                WinAppPackagePin("Microsoft.WindowsAppSDK", "2.2.0"),
                WinAppPackagePin("WinUIEssential.WinUI3", "1.8.0"),
            ),
            packages,
        )
        assertEquals(
            """
            packages:
              - name: Microsoft.Windows.CppWinRT
                version: 2.0.240405.15
              - name: Microsoft.Windows.SDK.BuildTools
                version: 10.0.26100.1742
              - name: Microsoft.Windows.SDK.CPP
                version: 10.0.26100.1742
              - name: Microsoft.WindowsAppSDK
                version: 2.2.0
              - name: WinUIEssential.WinUI3
                version: 1.8.0

            """.trimIndent(),
            renderWinAppConfiguration(packages),
        )
    }

    @Test
    fun empty_configuration_does_not_inject_tooling() {
        assertEquals("packages: []\n", renderWinAppConfiguration(emptyList()))
    }

    @Test
    fun conflicting_declared_versions_fail_before_restore() {
        val failure = runCatching {
            resolveWinAppPackagePins(
                listOf(
                    "Microsoft.WindowsAppSDK@2.1.0",
                    "microsoft.windowsappsdk@2.2.0",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("2.1.0, 2.2.0"))
    }

    @Test
    fun incompatible_tooling_override_fails_before_restore() {
        val failure = runCatching {
            resolveWinAppPackagePins(listOf("Microsoft.Windows.CppWinRT@3.0.0"))
        }.exceptionOrNull()

        assertTrue(failure is GradleException)
        assertTrue(failure?.message.orEmpty().contains("required by kotlin-winrt"))
    }
}
