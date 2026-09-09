package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeScopeTest {
    @Test
    fun multithreaded_scope_initializes_runtime() {
        if (!PlatformRuntime.isWindows) {
            val failure = assertFailsWith<WinRTRuntimeException> {
                RuntimeScope.initializeMultithreaded()
            }
            assertIs<WinRTUnsupportedOperationException>(failure)
            return
        }
        val scope = RuntimeScope.initializeMultithreaded()
        scope.close()
        scope.close()
    }

    @Test
    fun non_windows_runtime_initialization_reports_unsupported() {
        if (PlatformRuntime.isWindows) {
            return
        }

        assertEquals(KnownHResults.E_NOTSUPPORTED, ComRuntime.initializeMultithreaded())
        assertEquals(KnownHResults.E_NOTSUPPORTED, WinRTRuntime.initializeMultithreaded())
    }

    @Test
    fun same_apartment_scope_can_be_nested_without_shared_cleanup() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val outer = RuntimeScope.initializeMultithreaded()
        try {
            val inner = RuntimeScope.initializeMultithreaded()
            inner.close()
            inner.close()
        } finally {
            outer.close()
        }
    }

    @Test
    fun none_deployment_does_not_acquire_an_owner() {
        assertNull(
            WinRTWindowsAppSdkDeployment.initialize(
                WinRTWindowsAppSdkDeploymentConfiguration(
                    mode = WinRTWindowsAppSdkDeploymentMode.None,
                    packageIdentity = WinRTApplicationPackageIdentity.Packaged,
                ),
            ),
        )
    }

    @Test
    fun packaged_bootstrap_uses_the_package_identity_noop_option() {
        assertEquals(0, WinRTApplicationPackageIdentity.Unpackaged.mddBootstrapInitializeOptions)
        assertEquals(0x0010, WinRTApplicationPackageIdentity.Packaged.mddBootstrapInitializeOptions)
    }

    @Test
    fun cleanup_preserves_first_failure_and_suppresses_later_failures() {
        val first = IllegalStateException("first cleanup failure")
        val second = IllegalArgumentException("second cleanup failure")

        val failure = assertFailsWith<IllegalStateException> {
            closeAllAutoCloseables(
                listOf(
                    AutoCloseable { throw first },
                    AutoCloseable { throw second },
                ),
            )
        }

        assertSame(first, failure)
    }

    @Test
    fun externally_initialized_deployment_has_one_releasable_owner() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val configuration = WinRTWindowsAppSdkDeploymentConfiguration(
            mode = WinRTWindowsAppSdkDeploymentMode.ExternallyInitialized,
        )
        val first = WinRTWindowsAppSdkDeployment.initialize(configuration)
        checkNotNull(first)
        try {
            assertFailsWith<IllegalStateException> {
                WinRTWindowsAppSdkDeployment.initialize(configuration)
            }
        } finally {
            first.close()
            first.close()
        }

        WinRTWindowsAppSdkDeployment.initialize(configuration)?.close()
    }
}
