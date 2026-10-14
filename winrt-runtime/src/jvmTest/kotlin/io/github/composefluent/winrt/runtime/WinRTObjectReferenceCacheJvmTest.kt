@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WinRTObjectReferenceCacheJvmTest {
    @Test
    fun independent_rcw_caches_publish_one_instance_under_concurrent_first_access() {
        // CsWinRT 3.0 code_writers.h publishes each RCW cache independently with
        // CompareExchange. Kotlin's shared cold lock must preserve the same identity.
        val state = WinRTObjectState<TestReference>()
        val start = CountDownLatch(1)
        val interfaces = ConcurrentLinkedQueue<ConcurrentCacheMap<WinRTTypeHandle, TestReference>>()
        val typeData = ConcurrentLinkedQueue<ConcurrentCacheMap<WinRTTypeHandle, Any>>()
        val workers = List(8) { index ->
            thread {
                assertTrue(start.await(10, TimeUnit.SECONDS))
                if (index % 2 == 0) {
                    interfaces += state.queryInterfaceCache
                    typeData += state.additionalTypeData
                } else {
                    typeData += state.additionalTypeData
                    interfaces += state.queryInterfaceCache
                }
            }
        }
        start.countDown()
        workers.forEach { it.join(10_000); assertTrue(!it.isAlive) }
        assertEquals(8, interfaces.size)
        assertEquals(8, typeData.size)
        interfaces.forEach { assertSame(state.queryInterfaceCache, it) }
        typeData.forEach { assertSame(state.additionalTypeData, it) }
    }

    @Test
    fun concurrent_initialization_closes_the_unpublished_reference() {
        val created = ConcurrentLinkedQueue<TestReference>()
        val creatorsReady = CountDownLatch(2)
        val releaseCreators = CountDownLatch(1)
        val slot = AtomicReference<TestReference?>(null)
        fun getReference(): TestReference = getOrCreateWinRTObjectReference(slot) {
            TestReference().also(created::add).also {
                creatorsReady.countDown()
                assertTrue(releaseCreators.await(10, TimeUnit.SECONDS))
            }
        }
        val results = ConcurrentLinkedQueue<TestReference>()
        val workers = List(2) {
            thread(start = true) {
                results += getReference()
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

    @Test
    fun concurrent_generated_value_publication_returns_one_winner() {
        val candidates = ConcurrentLinkedQueue<Any>()
        val creatorsReady = CountDownLatch(2)
        val releaseCreators = CountDownLatch(1)
        var cached: Any? = null
        fun getValue(): Any = cached ?: Any().also(candidates::add).also {
            creatorsReady.countDown()
            assertTrue(releaseCreators.await(10, TimeUnit.SECONDS))
        }.let { candidate ->
            publishGeneratedWinRTValue(candidate, { cached }, { cached = it })
        }
        val results = ConcurrentLinkedQueue<Any>()
        val workers = List(2) {
            thread(start = true) {
                results += getValue()
            }
        }

        assertTrue(creatorsReady.await(10, TimeUnit.SECONDS))
        releaseCreators.countDown()
        workers.forEach(Thread::join)

        assertEquals(2, candidates.size)
        assertEquals(2, results.size)
        assertSame(results.first(), results.last())
        assertSame(results.first(), cached)
    }

    private class TestReference : AutoCloseable {
        var closeCount: Int = 0
            private set

        override fun close() {
            closeCount += 1
        }
    }
}
