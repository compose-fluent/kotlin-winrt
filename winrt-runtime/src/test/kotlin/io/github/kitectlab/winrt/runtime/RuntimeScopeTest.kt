package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeScopeTest {
    @Test
    fun multithreaded_scope_initializes_runtime() {
        RuntimeScope.initializeMultithreaded().use { scope ->
            assertTrue(scope.comInitialized)
            assertTrue(scope.winRtInitialized || !PlatformRuntime.isWindows)
        }
    }

    @Test
    fun non_windows_runtime_initialization_is_a_no_op_success() {
        if (PlatformRuntime.isWindows) {
            return
        }

        assertEquals(KnownHResults.S_OK, JvmComRuntime.initializeMultithreaded())
        assertEquals(KnownHResults.S_OK, JvmWinRtRuntime.initializeMultithreaded())
    }
}
