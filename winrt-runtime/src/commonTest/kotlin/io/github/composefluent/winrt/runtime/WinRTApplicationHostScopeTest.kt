package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFailsWith

class WinRTApplicationHostScopeTest {
    @Test
    fun non_windows_application_host_fails_explicitly() {
        if (PlatformRuntime.isWindows) {
            return
        }

        assertFailsWith<IllegalStateException> {
            WinRTApplicationHostScope.initialize(
                WinRTApplicationHostConfiguration(
                    packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
                    windowsAppSdkDeployment = WinRTWindowsAppSdkDeploymentMode.None,
                ),
            )
        }
    }
}
