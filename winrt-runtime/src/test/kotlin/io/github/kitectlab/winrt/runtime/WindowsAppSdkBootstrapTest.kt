package io.github.kitectlab.winrt.runtime

import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowsAppSdkBootstrapTest {
    @Test
    fun parses_nuget_global_packages_output() {
        val roots = WindowsAppSdkBootstrap.parseNuGetGlobalPackagesOutput(
            "global-packages: F:\\Dependencies\\nuget\\",
        )

        assertEquals(listOf(Path.of("F:\\Dependencies\\nuget\\")), roots)
    }

    @Test
    fun parses_windows_app_sdk_version_info_header() {
        val versionInfo = WindowsAppSdkBootstrap.parseVersionInfoHeader(
            """
            #define WINDOWSAPPSDK_RELEASE_MAJOR 1
            #define WINDOWSAPPSDK_RELEASE_MINOR 8
            #define WINDOWSAPPSDK_RELEASE_MAJORMINOR 0x00010008
            #define WINDOWSAPPSDK_RELEASE_VERSION_TAG_W L"preview"
            #define WINDOWSAPPSDK_RUNTIME_VERSION_UINT64 0x1F40032608CC0000u
            #define WINDOWSAPPSDK_RUNTIME_PACKAGE_FRAMEWORK_PACKAGEFAMILYNAME "Microsoft.WindowsAppRuntime.1.8_8wekyb3d8bbwe"
            #define WINDOWSAPPSDK_RUNTIME_PACKAGE_MAIN_PACKAGEFAMILYNAME "MicrosoftCorporationII.WinAppRuntime.Main.1.8_8wekyb3d8bbwe"
            #define WINDOWSAPPSDK_RUNTIME_PACKAGE_SINGLETON_PACKAGEFAMILYNAME "Microsoft.WindowsAppRuntime.Singleton_8wekyb3d8bbwe"
            """.trimIndent(),
        )

        assertEquals(1, versionInfo.releaseMajor)
        assertEquals(8, versionInfo.releaseMinor)
        assertEquals(0x00010008, versionInfo.majorMinorVersion)
        assertEquals("preview", versionInfo.versionTag)
        assertEquals(0x1F40032608CC0000L, versionInfo.minVersion)
        assertEquals("Microsoft.WindowsAppRuntime.1.8_8wekyb3d8bbwe", versionInfo.frameworkPackageFamilyName)
    }

    @Test
    fun discovers_bootstrap_library_or_reports_absence() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val library = WindowsAppSdkBootstrap.discoverBootstrapLibrary()
        if (library != null) {
            assertTrue(library.path.toString(), library.path.toString().endsWith(".dll", ignoreCase = true))
        } else {
            assertTrue(WindowsAppSdkBootstrap.initialize().isFailure)
        }
    }
}