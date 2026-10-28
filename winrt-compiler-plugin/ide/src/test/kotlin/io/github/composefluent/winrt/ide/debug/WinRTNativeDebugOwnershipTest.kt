package io.github.composefluent.winrt.ide.debug

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WinRTNativeDebugOwnershipTest {
    @Test fun concurrent_sessions_cannot_reserve_the_same_process_lifetime() {
        val pool = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        try {
            val attempts = (0..1).map {
                pool.submit<Boolean> {
                    start.await()
                    val result = runCatching { WinRTNativeDebugOwnership.acquire(100001, Instant.EPOCH) }
                    entered.countDown()
                    release.await()
                    result.getOrNull()?.close()
                    result.isSuccess
                }
            }
            start.countDown()
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertTrue(WinRTNativeDebugOwnership.contains(100001))
            release.countDown()
            assertEquals(1, attempts.count { it.get(5, TimeUnit.SECONDS) })
            assertFalse(WinRTNativeDebugOwnership.contains(100001))
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun late_close_does_not_release_a_reused_pid() {
        val old = WinRTNativeDebugOwnership.acquire(100002, Instant.EPOCH)
        val current = WinRTNativeDebugOwnership.acquire(100002, Instant.EPOCH.plusSeconds(1))
        try {
            old.close(); old.close()
            assertTrue(WinRTNativeDebugOwnership.contains(100002))
        } finally { current.close(); old.close() }
        assertFalse(WinRTNativeDebugOwnership.contains(100002))
    }
}
