package io.github.kitectlab.winrt.runtime

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
}
