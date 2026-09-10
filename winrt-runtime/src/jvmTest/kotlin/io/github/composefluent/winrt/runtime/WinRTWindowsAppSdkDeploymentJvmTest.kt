package io.github.composefluent.winrt.runtime

import java.nio.file.Files
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WinRTWindowsAppSdkDeploymentJvmTest {
    @Test
    fun staged_configuration_preserves_a_nonempty_windows_app_sdk_version_tag() {
        val root = Files.createTempDirectory("kotlin-winrt-staged-windows-app-sdk-")
        Files.writeString(
            root.resolve("kotlin-winrt-windows-app-sdk.properties"),
            """
            schemaVersion=1
            majorMinorVersion=65544
            versionTag=preview.7
            minVersion=281509336449024
            """.trimIndent(),
        )

        // MddBootstrapInitialize2 consumes all three staged version fields.
        val configuration = WinRTApplicationHostConfiguration.fromStagedRuntimeAssets(
            packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
            windowsAppSdkDeployment = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
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
            WinRTApplicationHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
                windowsAppSdkDeployment = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
                runtimeAssetsRoot = rootPath,
            )
        }

        Files.writeString(
            root.resolve("kotlin-winrt-windows-app-sdk.properties"),
            """
            schemaVersion=2
            majorMinorVersion=65544
            versionTag=preview.7
            minVersion=281509336449024
            """.trimIndent(),
        )

        assertFailsWith<IllegalStateException> {
            WinRTApplicationHostConfiguration.fromStagedRuntimeAssets(
                packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
                windowsAppSdkDeployment = WinRTWindowsAppSdkDeploymentMode.FrameworkDependent,
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
                    WinRTWindowsAppSdkDeployment.initialize(
                        WinRTWindowsAppSdkDeploymentConfiguration(
                            mode = WinRTWindowsAppSdkDeploymentMode.SelfContained,
                            packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
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
                val recoveryOwner = WinRTWindowsAppSdkDeployment.initialize(
                    WinRTWindowsAppSdkDeploymentConfiguration(
                        mode = WinRTWindowsAppSdkDeploymentMode.ExternallyInitialized,
                        packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
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
