@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.test.assertEquals

class WeakKeyStateReclamationNativeTest {
    @Test
    fun collection_from_a_factory_does_not_wait_for_the_identity_lock() {
        // Native adapts the GC-owned CCW identity lifetime of CLR ComWrappers.
        // A factory can allocate/collect while holding the identity-cache lock.
        val evicted = AtomicInt(0)
        val cache = WeakKeyStateMap<Any, String> { evicted.fetchAndAdd(1) }
        cache.enableGcReclamation()
        fun abandonOwner() {
            cache.getOrPut(Any()) { "dead" }
        }
        abandonOwner()
        val live = Any()
        assertEquals("live", cache.getOrPut(live) {
            GC.collect()
            "live"
        })
        repeat(6) { GC.collect() }
        assertEquals(1, evicted.load())
        assertEquals("live", cache[live])
        winRTKeepAlive(cache)
        winRTKeepAlive(live)
    }
}
