package io.github.composefluent.winrt.runtime

import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

        runOnFreshPlatformThread {
            val scope = RuntimeScope.initializeMultithreaded()
            val failure = AtomicReference<Throwable?>()
            val otherThread = Thread {
                failure.set(runCatching { scope.close() }.exceptionOrNull())
            }
            otherThread.start()
            otherThread.join()

            assertIs<IllegalStateException>(failure.get())
            activateJsonObject()
            scope.close()
        }
    }

    @Test
    fun fresh_mta_thread_activates_winrt_and_closes_its_apartment() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        // C++/WinRT init_apartment succeeds before its caller makes ordinary WinRT calls.
        runOnFreshPlatformThread {
            RuntimeScope.initializeMultithreaded().use {
                activateJsonObject()
            }
        }
    }

    @Test
    fun fresh_sta_thread_activates_winrt_and_closes_its_apartment() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        runOnFreshPlatformThread {
            RuntimeScope.initializeSingleThreaded().use {
                activateJsonObject()
            }
        }
    }

    @Test
    fun mta_to_sta_conflict_preserves_the_existing_apartment() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        runOnFreshPlatformThread {
            RuntimeScope.initializeMultithreaded().use {
                activateJsonObject()
                assertChangedMode { RuntimeScope.initializeSingleThreaded() }
                activateJsonObject()
            }
        }
    }

    @Test
    fun sta_to_mta_conflict_preserves_the_existing_apartment() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        runOnFreshPlatformThread {
            RuntimeScope.initializeSingleThreaded().use {
                activateJsonObject()
                assertChangedMode { RuntimeScope.initializeMultithreaded() }
                activateJsonObject()
            }
        }
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
                    packageIdentity = WinRTApplicationPackageIdentity.Unpackaged,
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

    private fun activateJsonObject() {
        WinRTRuntime.activateInstance("Windows.Data.Json.JsonObject").getOrThrow().use { instance ->
            assertEquals("Windows.Data.Json.JsonObject", instance.getRuntimeClassName())
        }
    }

    private fun assertChangedMode(initialize: () -> RuntimeScope) {
        val result = runCatching(initialize)
        val failure = result.exceptionOrNull()
        if (failure == null) {
            result.getOrThrow().close()
            throw AssertionError("CoInitializeEx unexpectedly accepted a conflicting apartment type.")
        }
        val error = assertIs<WinRTIllegalStateException>(failure)
        assertEquals(KnownHResults.RPC_E_CHANGED_MODE, error.hResult)
    }

    private fun runOnFreshPlatformThread(block: () -> Unit) {
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try {
                block()
            } catch (error: Throwable) {
                failure.set(error)
            }
        }
        worker.start()
        worker.join(30_000)
        assertFalse(worker.isAlive, "Timed out while running a WinRT apartment test on a platform thread.")
        failure.get()?.let { error ->
            throw AssertionError("WinRT apartment test failed on its platform thread.", error)
        }
    }
}
