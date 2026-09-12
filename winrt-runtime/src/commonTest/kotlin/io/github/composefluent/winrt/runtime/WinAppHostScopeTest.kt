package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WinAppHostScopeTest {
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
