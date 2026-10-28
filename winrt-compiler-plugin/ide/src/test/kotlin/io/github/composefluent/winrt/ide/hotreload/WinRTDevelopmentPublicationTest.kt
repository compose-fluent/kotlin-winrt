package io.github.composefluent.winrt.ide.hotreload

import junit.framework.TestCase
import kotlinx.coroutines.*

/** IDE lifecycle, independent of projection/runtime behavior. */
class WinRTDevelopmentPublicationTest : TestCase() {
    fun testColdBuildCanExceedTenMinutesAndConnectBeforeGradleRunEnds() = runBlocking {
        var time = 0L
        var polls = 0
        val result = awaitDevelopmentPublication({ true }, nanoTime = { time }) {
            time += 11 * 60_000_000_000L
            if (++polls == 3) "native host" else null
        }
        assertEquals("native host", result)
        assertTrue(time > 10 * 60_000_000_000L)
    }
    fun testPublicationGetsItsOwnDeadlineAfterLongBuild() = runBlocking {
        var polls = 0
        var time = 0L
        val result = awaitDevelopmentPublication({ polls < 3 }, timeoutMillis = 500, nanoTime = { time }) {
            polls++
            time += if (polls < 3) 11 * 60_000_000_000L else 100_000_000L
            if (polls == 5) "host after task completion" else null
        }
        assertEquals("host after task completion", result)
    }
    fun testDirectCachedHostHasTheFullPublicationDeadlineDuringSlowIdeStartup() = runBlocking {
        var time = 0L
        var polls = 0
        val result = awaitDevelopmentPublication({ false }, nanoTime = { time }) {
            time += 6_000_000_000L
            if (++polls == 4) "cached SDK host" else null
        }
        assertEquals("cached SDK host", result)
        assertTrue("A valid cached JVM/WinUI host can take longer than ten seconds", time > 10_000_000_000L)
    }

    fun testFinishedLaunchWithoutHostStillTimesOut() = runBlocking {
        var time = 0L
        val failure = runCatching {
            awaitDevelopmentPublication<String>({ false }, timeoutMillis = 100, nanoTime = { time }) {
                time += 100_000_000L; null
            }
        }.exceptionOrNull()
        assertTrue(failure?.message.orEmpty(), failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("launch finished"))
    }
    fun testWaitingForBuildRemainsCancellable() = runBlocking {
        val polled = CompletableDeferred<Unit>()
        val job = launch {
            awaitDevelopmentPublication<String>({ true }) { polled.complete(Unit); null }
        }
        polled.await(); job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
