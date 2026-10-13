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

/** Runs on a fresh platform thread in each target's test. */
internal fun verifyApplicationHostApartments() {
    // XamlCompiler CppWinRT_AppPass2.tt and CSharpAppPass1.tt use MTA for UWP
    // entries and STA for desktop entries. CsWinRT's AuthoringWuxConsumptionTest
    // uses STA for a desktop WindowsXamlManager island even though it uses UWP XAML.
    val uwp = WinAppHostConfiguration(
        WinAppPackageIdentity.Packaged,
        WindowsAppSdkDeploymentMode.None,
        applicationModel = WinAppApplicationModel.Uwp,
        xamlFramework = XamlFramework.Uwp,
    )
    FeatureSwitches.clearForTests()
    try {
        RuntimeScope.initializeMultithreaded().use {
            WinAppHostScope.initialize(uwp).use {
                val conflict = assertFailsWith<WinRTIllegalStateException> {
                    RuntimeScope.initializeSingleThreaded().close()
                }
                assertEquals(KnownHResults.RPC_E_CHANGED_MODE, conflict.hResult)
            }
            // Closing the host balances only its own initialization, not the enclosing MTA.
            val conflict = assertFailsWith<WinRTIllegalStateException> {
                RuntimeScope.initializeSingleThreaded().close()
            }
            assertEquals(KnownHResults.RPC_E_CHANGED_MODE, conflict.hResult)
        }
        RuntimeScope.initializeSingleThreaded().use {
            val conflict = assertFailsWith<WinRTIllegalStateException> {
                WinAppHostScope.initialize(uwp).close()
            }
            assertEquals(KnownHResults.RPC_E_CHANGED_MODE, conflict.hResult)
            // A failed host releases ownership, and desktop/system-XAML hosting stays STA.
            WinAppHostScope.initialize(uwp.copy(applicationModel = WinAppApplicationModel.Desktop)).use {
                val desktopConflict = assertFailsWith<WinRTIllegalStateException> {
                    RuntimeScope.initializeMultithreaded().close()
                }
                assertEquals(KnownHResults.RPC_E_CHANGED_MODE, desktopConflict.hResult)
            }
        }
    } finally {
        FeatureSwitches.clearForTests()
        ComWrappersSupport.clearRegistriesForTests()
    }
}
