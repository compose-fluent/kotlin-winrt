package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class WinAppHostScopeTest {
    // CsWinRT's UWP host selects system XAML separately from package identity and WASDK deployment.
    @Test
    fun uwp_host_requires_packaged_system_xaml_without_windows_app_sdk() {
        val configuration = WinAppHostConfiguration(
            packageIdentity = WinAppPackageIdentity.Packaged,
            windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.None,
            applicationModel = WinAppApplicationModel.Uwp,
            xamlFramework = XamlFramework.Uwp,
        )
        assertEquals(null, configuration.windowsAppSdkVersion)
        assertFailsWith<IllegalArgumentException> {
            configuration.copy(packageIdentity = WinAppPackageIdentity.Unpackaged)
        }
        assertFailsWith<IllegalArgumentException> {
            configuration.copy(windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.FrameworkDependent)
        }
        assertFailsWith<IllegalArgumentException> {
            configuration.copy(xamlFramework = XamlFramework.WinUi)
        }
        // Packaged desktop applications remain a distinct supported model.
        assertEquals(WinAppApplicationModel.Desktop,
            WinAppHostConfiguration(WinAppPackageIdentity.Packaged, WindowsAppSdkDeploymentMode.None).applicationModel)
    }

    @Test
    fun non_windows_application_host_fails_explicitly() {
        if (PlatformRuntime.isWindows) {
            return
        }

        assertFailsWith<IllegalStateException> {
            WinAppHostScope.initialize(
                WinAppHostConfiguration(
                    packageIdentity = WinAppPackageIdentity.Unpackaged,
                    windowsAppSdkDeployment = WindowsAppSdkDeploymentMode.None,
                ),
            )
        }
    }
}
