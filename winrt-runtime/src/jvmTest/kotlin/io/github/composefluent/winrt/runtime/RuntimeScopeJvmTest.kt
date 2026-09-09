package io.github.composefluent.winrt.runtime

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class RuntimeScopeJvmTest {
    @Test
    fun application_cleanup_preserves_later_failures_as_suppressed_on_jvm() {
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
        assertEquals(listOf(second), failure.suppressed.toList())
    }

    @Test
    fun close_on_another_thread_does_not_release_the_apartment() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val scope = RuntimeScope.initializeMultithreaded()
        val failure = AtomicReference<Throwable?>()
        val otherThread = Thread {
            failure.set(runCatching { scope.close() }.exceptionOrNull())
        }
        otherThread.start()
        otherThread.join()

        assertIs<IllegalStateException>(failure.get())
        scope.close()
    }

    @Test
    fun virtual_thread_is_rejected_before_com_initialization() {
        val failure = AtomicReference<Throwable?>()
        val virtualThread = Thread.ofVirtual().start {
            failure.set(runCatching { RuntimeScope.initializeMultithreaded() }.exceptionOrNull())
        }
        virtualThread.join()

        assertIs<IllegalStateException>(failure.get())
    }

    @Test
    fun application_host_is_unique_and_can_be_reacquired_after_close() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            var first: WinRTApplicationHostScope.Scope? = null
            try {
                val configuration = WinRTApplicationHostConfiguration(
                    windowsAppSdkDeployment = WinRTWindowsAppSdkDeploymentMode.None,
                )
                first = WinRTApplicationHostScope.initialize(configuration)
                assertFailsWith<IllegalStateException> {
                    WinRTApplicationHostScope.initialize(configuration)
                }
                first.close()
                first = null
                WinRTApplicationHostScope.initialize(configuration).close()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                first?.let { runCatching { it.close() } }
            }
        }
        worker.start()
        worker.join()

        assertNull(failure.get())
    }
}
