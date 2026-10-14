@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.toCPointer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.windows.GetModuleFileNameW
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WinAppHostDiscoveryNativeTest {
    @Test
    fun staged_uwp_host_is_discovered_beside_the_executable_without_windows_app_sdk() {
        // CsWinRT ActivationFactory.cs uses AppContext.BaseDirectory. Native's existing
        // module-path search provides that anchor even when no WASDK DLL is staged.
        val executable = PlatformAbi.confinedScope().use { scope ->
            val buffer = PlatformAbi.allocateBytes(scope, 32768L * 2L)
            val length = GetModuleFileNameW(null, buffer.value.toCPointer(), 32768u).toInt()
            assertTrue(length in 1 until 32768)
            Path(PlatformAbi.readUtf16(buffer, length))
        }
        val root = assertNotNull(executable.parentPath())
        val properties = Path(root, WinAppHostConfiguration.STAGED_CONFIGURATION_FILE_NAME)
        val previous = if (properties.isRegularFile()) properties.readText() else null
        try {
            val expected = WinAppHostConfiguration(
                WinAppPackageIdentity.Packaged, WindowsAppSdkDeploymentMode.None, root,
                applicationModel = WinAppApplicationModel.UWP, xamlFramework = XamlFramework.UWP,
            )
            properties.writeText(expected.toStagedProperties())
            assertEquals(expected, WinAppHostConfiguration.fromStagedRuntimeAssets())
        } finally {
            if (previous != null) properties.writeText(previous) else SystemFileSystem.delete(properties)
        }
    }
}
