package io.github.composefluent.winrt.runtime

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinRTObjectReferenceCacheJvmTest {
    @Test
    fun concurrent_initialization_closes_the_unpublished_reference() {
        val created = ConcurrentLinkedQueue<TestReference>()
        val creatorsReady = CountDownLatch(2)
        val releaseCreators = CountDownLatch(1)
        val cache = WinRTObjectReferenceCache {
            TestReference().also(created::add).also {
                creatorsReady.countDown()
                assertTrue(releaseCreators.await(10, TimeUnit.SECONDS))
            }
        }
        val results = ConcurrentLinkedQueue<TestReference>()
        val workers = List(2) {
            thread(start = true) {
                results += cache.value
            }
        }

        assertTrue(creatorsReady.await(10, TimeUnit.SECONDS))
        releaseCreators.countDown()
        workers.forEach(Thread::join)

        assertEquals(2, created.size)
        assertEquals(2, results.size)
        assertSame(results.first(), results.last())
        assertEquals(1, created.sumOf(TestReference::closeCount))
    }

    private class TestReference : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }
}
