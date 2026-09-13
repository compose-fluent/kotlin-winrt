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
            WindowsAppSdkDeployment.initialize(
                WindowsAppSdkDeploymentConfiguration(
                    mode = WindowsAppSdkDeploymentMode.None,
                    packageIdentity = WinAppPackageIdentity.Packaged,
                ),
            ),
        )
    }

    @Test
    fun packaged_bootstrap_uses_the_package_identity_noop_option() {
        assertEquals(0, WinAppPackageIdentity.Unpackaged.mddBootstrapInitializeOptions)
        assertEquals(0x0010, WinAppPackageIdentity.Packaged.mddBootstrapInitializeOptions)
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

        val configuration = WindowsAppSdkDeploymentConfiguration(
            mode = WindowsAppSdkDeploymentMode.ExternallyInitialized,
            packageIdentity = WinAppPackageIdentity.Unpackaged,
        )
        val first = WindowsAppSdkDeployment.initialize(configuration)
        checkNotNull(first)
        try {
            assertFailsWith<IllegalStateException> {
                WindowsAppSdkDeployment.initialize(configuration)
            }
        } finally {
            first.close()
            first.close()
        }

        WindowsAppSdkDeployment.initialize(configuration)?.close()
    }
}
