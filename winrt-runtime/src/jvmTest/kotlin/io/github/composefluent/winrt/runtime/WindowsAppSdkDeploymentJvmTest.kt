package io.github.composefluent.winrt.runtime

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowsAppSdkDeploymentJvmTest {
    @Test
    fun staged_uwp_host_discovery_uses_the_host_configuration_resource() {
        // CsWinRT ActivationFactory.cs anchors assets at AppContext.BaseDirectory;
        // a UWP application has host metadata there without a WASDK bootstrap DLL.
        val root = Files.createTempDirectory("kotlin-winrt-uwp-discovery-")
        val properties = root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME)
        val thread = Thread.currentThread()
        val originalLoader = thread.contextClassLoader
        val propertyName = WinRTRuntimeAssets.runtimeAssetsRootPropertyName
        val originalRoot = System.getProperty(propertyName)
        try {
            val expected = WinAppHostConfiguration(
                WinAppPackageIdentity.Packaged, WindowsAppSdkDeploymentMode.None,
                kotlinx.io.files.Path(root.toString()), applicationModel = WinAppApplicationModel.UWP,
                xamlFramework = XamlFramework.UWP,
            )
            Files.writeString(properties, expected.toStagedProperties())
            thread.contextClassLoader = object : ClassLoader(originalLoader) {
                override fun getResource(name: String): java.net.URL? =
                    if (name == "${WinRTRuntimeAssets.runtimeAssetsDirectoryName}/${properties.fileName}") {
                        properties.toUri().toURL()
                    } else null
            }
            System.clearProperty(propertyName)
            assertEquals(expected, WinAppHostConfiguration.fromStagedRuntimeAssets())
        } finally {
            thread.contextClassLoader = originalLoader
            if (originalRoot == null) System.clearProperty(propertyName) else System.setProperty(propertyName, originalRoot)
            Files.deleteIfExists(properties)
            Files.delete(root)
        }
    }

    @Test
    fun staged_uwp_host_preserves_framework_and_assets_without_windows_app_sdk() {
        val root = Files.createTempDirectory("kotlin-winrt-uwp-host-")
        try {
            val rootPath = kotlinx.io.files.Path(root.toString())
            val expected = WinAppHostConfiguration(
                packageIdentity = WinAppPackageIdentity.Packaged,
                windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.None,
                runtimeAssetsRoot = rootPath,
                applicationModel = WinAppApplicationModel.UWP,
                xamlFramework = XamlFramework.UWP,
            )
            Files.writeString(root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME), expected.toStagedProperties())
            assertEquals(expected, WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath))
            Files.writeString(root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME),
                "schemaVersion=1\npackageIdentity=Packaged\nwindowsAppSdkDeployment=None\n")
            val legacy = WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath)
            assertEquals(WinAppApplicationModel.Desktop, legacy.applicationModel)
            assertEquals(XamlFramework.WinUI, legacy.xamlFramework)
        } finally {
            Files.deleteIfExists(root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME))
            Files.delete(root)
        }
    }

    // Windows App SDK bootstrap and self-contained deployment are selected at the host boundary,
    // independently of the Kotlin compilation, as in CsWinRT's WinUI desktop sample.
    @Test
    fun staged_host_configuration_selects_each_packaging_mode_without_compiled_defaults() {
        val root = Files.createTempDirectory("kotlin-winrt-host-modes-")
        val rootPath = kotlinx.io.files.Path(root.toString())
        Files.writeString(root.resolve("windows-app-sdk.properties"),
            "schemaVersion=1\nmajorMinorVersion=65544\nversionTag=preview.7\nminVersion=281509336449024\n")
        for (identity in WinAppPackageIdentity.entries) {
            for (deployment in listOf(WindowsAppSdkDeploymentMode.None,
                WindowsAppSdkDeploymentMode.FrameworkDependent, WindowsAppSdkDeploymentMode.SelfContained)) {
                Files.writeString(root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME),
                    WinAppHostConfiguration(identity, deployment).toStagedProperties())
                val actual = WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath)
                assertEquals(identity, actual.packageIdentity)
                assertEquals(deployment, actual.windowsAppSdkDeployment)
                if (deployment == WindowsAppSdkDeploymentMode.FrameworkDependent) {
                    assertEquals("preview.7", assertNotNull(actual.windowsAppSdkVersion).versionTag)
                } else {
                    assertEquals(null, actual.windowsAppSdkVersion)
                }
            }
        }
    }

    @Test
    fun staged_host_configuration_rejects_missing_unknown_or_incomplete_metadata() {
        val root = Files.createTempDirectory("kotlin-winrt-invalid-host-")
        val rootPath = kotlinx.io.files.Path(root.toString())
        val file = root.resolve(WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME)
        assertFailsWith<IllegalArgumentException> { WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath) }
        Files.writeString(file, "schemaVersion=2\npackageIdentity=Unpackaged\nwindowsAppSdkDeployment=None\n")
        assertFailsWith<IllegalStateException> { WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath) }
        Files.writeString(file, "schemaVersion=1\npackageIdentity=Invalid\nwindowsAppSdkDeployment=None\n")
        assertFailsWith<IllegalArgumentException> { WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath) }
        Files.writeString(file, "schemaVersion=1\npackageIdentity=Unpackaged\n")
        assertFailsWith<IllegalStateException> { WinAppHostConfiguration.fromStagedRuntimeAssets(rootPath) }
    }

    @Test
    fun staged_configuration_preserves_a_nonempty_windows_app_sdk_version_tag() {
        val root = Files.createTempDirectory("kotlin-winrt-staged-windows-app-sdk-")
        Files.writeString(
            root.resolve("windows-app-sdk.properties"),
            """
            schemaVersion=1
            majorMinorVersion=65544
            versionTag=preview.7
            minVersion=281509336449024
            """.trimIndent(),
        )

        // MddBootstrapInitialize2 consumes all three staged version fields.
        val configuration = WinAppHostConfiguration.fromStagedRuntimeAssets(
            packageIdentity = WinAppPackageIdentity.Unpackaged,
            windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.FrameworkDependent,
            runtimeAssetsRoot = kotlinx.io.files.Path(root.toString()),
        )

        val version = assertNotNull(configuration.windowsAppSdkVersion)
        assertEquals(65544, version.majorMinorVersion)
        assertEquals("preview.7", version.versionTag)
        assertEquals(281509336449024, version.minVersion)
    }

    @Test
    fun staged_configuration_rejects_missing_or_invalid_version_properties() {
        val root = Files.createTempDirectory("kotlin-winrt-invalid-windows-app-sdk-")
        val rootPath = kotlinx.io.files.Path(root.toString())

        assertFailsWith<IllegalArgumentException> {
            WinAppHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = WinAppPackageIdentity.Unpackaged,
                windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.FrameworkDependent,
                runtimeAssetsRoot = rootPath,
            )
        }

        Files.writeString(
            root.resolve("windows-app-sdk.properties"),
            """
            schemaVersion=2
            majorMinorVersion=65544
            versionTag=preview.7
            minVersion=281509336449024
            """.trimIndent(),
        )

        assertFailsWith<IllegalStateException> {
            WinAppHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = WinAppPackageIdentity.Unpackaged,
                windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.FrameworkDependent,
                runtimeAssetsRoot = rootPath,
            )
        }
    }

    @Test
    fun self_contained_failure_after_manifest_activation_restores_environment_and_releases_owner() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val root = Files.createTempDirectory("kotlin-winrt-self-contained-rollback-")
        Files.writeString(root.resolve("rollback.exe.manifest"), minimalActivationManifest)
        val environmentVariable = "MICROSOFT_WINDOWSAPPRUNTIME_BASE_DIRECTORY"
        val originalValue = platformGetWindowsEnvironmentVariable(environmentVariable)
        try {
            runOnFreshPlatformThread {
                val failure = assertFailsWith<IllegalStateException> {
                    WindowsAppSdkDeployment.initialize(
                        WindowsAppSdkDeploymentConfiguration(
                            mode = WindowsAppSdkDeploymentMode.SelfContained,
                            packageIdentity = WinAppPackageIdentity.Unpackaged,
                            runtimeAssetsRoot = kotlinx.io.files.Path(root.toString()),
                        ),
                    )
                }
                assertTrue(
                    failure.message.orEmpty().contains("Microsoft.WindowsAppRuntime.dll"),
                    "Expected failure after activation context setup, but got: ${failure.message}",
                )
                assertEquals(originalValue, platformGetWindowsEnvironmentVariable(environmentVariable))

                // A failed self-contained deployment must release its process owner reservation.
                val recoveryOwner = WindowsAppSdkDeployment.initialize(
                    WindowsAppSdkDeploymentConfiguration(
                        mode = WindowsAppSdkDeploymentMode.ExternallyInitialized,
                        packageIdentity = WinAppPackageIdentity.Unpackaged,
                    ),
                )
                assertNotNull(recoveryOwner).close()
            }
            assertEquals(originalValue, platformGetWindowsEnvironmentVariable(environmentVariable))
        } finally {
            platformSetWindowsEnvironmentVariable(environmentVariable, originalValue)
        }
    }

    private fun runOnFreshPlatformThread(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join(30_000)
        assertFalse(worker.isAlive, "Timed out while running a Windows App SDK deployment test.")
        failure.get()?.let { error ->
            throw AssertionError("Windows App SDK deployment test failed on its platform thread.", error)
        }
    }

    private companion object {
        val minimalActivationManifest: String =
            """
            <?xml version="1.0" encoding="utf-8" standalone="yes"?>
            <assembly manifestVersion="1.0" xmlns="urn:schemas-microsoft-com:asm.v1">
                <assemblyIdentity type="win32" name="kotlin.winrt.rollback.test" version="1.0.0.0" processorArchitecture="amd64"/>
            </assembly>
            """.trimIndent()
    }
}
