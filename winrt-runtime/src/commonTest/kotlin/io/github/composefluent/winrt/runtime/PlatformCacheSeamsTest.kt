package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformCacheSeamsTest {
    @Test
    fun concurrent_cache_map_supports_put_if_absent_and_compute() {
        val cache = ConcurrentCacheMap<String, Int>()

        assertNull(cache.putIfAbsent("answer", 41))
        assertEquals(41, cache.putIfAbsent("answer", 42))
        assertEquals(42, cache.computeIfAbsent("computed") { 42 })
        assertEquals(43, cache.compute("answer") { _, current -> (current ?: 0) + 2 })
        assertEquals(43, cache["answer"])
        assertEquals(42, cache["computed"])
    }

    @Test
    fun weak_value_and_weak_key_seams_expose_common_lookup_contracts() {
        val weakValues = WeakValueCache<String, Holder>()
        val weakKeys = WeakKeyStateMap<Holder, String>()
        val holder = Holder("value")

        weakValues["key"] = holder
        assertEquals(holder, weakValues["key"])
        assertEquals("state", weakKeys.getOrPut(holder) { "state" })
        assertEquals("state", weakKeys[holder])
    }

    @Test
    fun weak_value_and_weak_key_seams_do_not_retain_cached_objects() {
        val weakValues = WeakValueCache<String, Holder>()
        val valueReference = cacheWeakValue(weakValues)

        drainUntilCleared(valueReference)
        assertNull(weakValues["key"])

        val weakKeys = WeakKeyStateMap<Holder, String>()
        val keyReference = cacheWeakKey(weakKeys)

        drainUntilCleared(keyReference)
        assertEquals("new-state", weakKeys.getOrPut(Holder("key")) { "new-state" })
    }

    @Test
    fun snapshot_list_and_finalization_hook_support_manual_cleanup() {
        val registrations = SnapshotList<String>()
        registrations += "first"
        registrations += "second"

        assertEquals("second", registrations.firstNotNullOfOrNull { entry -> entry.takeIf { it.startsWith("s") } })

        val cleaned = mutableListOf<String>()
        val closeable = FinalizationHook().register(Any()) { cleaned += "done" }
        closeable.close()

        assertEquals(listOf("done"), cleaned)
    }

    private data class Holder(
        val value: String,
    )

    private fun cacheWeakValue(cache: WeakValueCache<String, Holder>): PlatformManagedWeakReference<Holder> {
        val holder = Holder("value")
        cache["key"] = holder
        assertEquals(holder, cache["key"])
        return PlatformManagedWeakReference(holder)
    }

    private fun cacheWeakKey(cache: WeakKeyStateMap<Holder, String>): PlatformManagedWeakReference<Holder> {
        val holder = Holder("key")
        assertEquals("state", cache.getOrPut(holder) { "state" })
        return PlatformManagedWeakReference(holder)
    }

    private fun drainUntilCleared(reference: PlatformManagedWeakReference<Holder>) {
        repeat(10) {
            PlatformFinalization.drain()
            if (reference.get() == null) {
                return
            }
            val pressure = List(128) { ByteArray(1024) }
            assertEquals(128, pressure.size)
        }
        assertNull(reference.get())
    }
}
