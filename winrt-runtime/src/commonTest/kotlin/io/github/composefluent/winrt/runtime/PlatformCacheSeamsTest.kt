package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlatformCacheSeamsTest {
    @Test
    fun small_per_object_cache_grows_without_losing_entries_or_compute_semantics() {
        // CsWinRT's RCW dictionaries grow normally; the Kotlin capacity hint must
        // not turn the common one-interface case into a limit on supported interfaces.
        val cache = ConcurrentCacheMap<Int, String>(1)
        repeat(64) { index -> assertNull(cache.putIfAbsent(index, "value-$index")) }
        repeat(64) { index -> assertEquals("value-$index", cache[index]) }
        assertEquals(64, cache.size)
        assertEquals("value-0", cache.putIfAbsent(0, "replacement"))
        assertEquals("updated", cache.compute(0) { _, _ -> "updated" })
        assertNull(cache.compute(1) { _, _ -> null })
        assertEquals("new", cache.computeIfAbsent(1) { "new" })
        assertEquals(64, cache.entries.size)
        cache.clear()
        assertEquals(0, cache.size)
    }

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

        val weakValueReference = weakValues.put("key", holder)
        assertEquals("key", weakValueReference.key)
        assertSame(weakValueReference, weakValues.reference("key"))
        assertSame(holder, weakValueReference.get())
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
    fun weak_value_cache_reclaims_keys_without_revisiting_each_key() {
        val cache = WeakValueCache<Int, Holder>()
        val references = cacheWeakValues(cache, 256)

        drainUntilAllCleared(references)

        assertEquals(0, cache.size)
    }

    @Test
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    fun gc_enabled_weak_key_state_releases_values_without_cache_traffic() {
        // CsWinRT 3 delegates the CCW identity lifetime to CLR ComWrappers.
        // Neither a cache lookup nor clear() should be needed to release roots.
        val evicted = kotlin.concurrent.atomics.AtomicInt(0)
        val cache = WeakKeyStateMap<Holder, String> { evicted.fetchAndAdd(1) }
        cache.enableGcReclamation()
        val live = Holder("live")
        cache.getOrPut(live) { "live" }
        fun abandonKeys() {
            repeat(257) { cache.getOrPut(Holder("dead-$it")) { "dead" } }
        }
        abandonKeys()
        for (attempt in 0 until 30) {
            PlatformFinalization.drain()
            if (evicted.load() == 257) break
        }
        assertEquals(257, evicted.load())
        assertEquals("live", cache[live])
        cache.clear()
        assertEquals(258, evicted.load())
        winRTKeepAlive(live)
    }

    @Test
    fun weak_key_state_uses_object_identity() {
        val cache = WeakKeyStateMap<Holder, String>()
        val first = Holder("equal")
        val second = Holder("equal")

        assertEquals("first", cache.getOrPut(first) { "first" })
        assertEquals("second", cache.getOrPut(second) { "second" })
        assertEquals("first", cache[first])
        assertEquals("second", cache[second])
    }

    @Test
    fun weak_key_state_recent_entry_is_invalidated_by_remove_and_clear() {
        val evicted = mutableListOf<String>()
        val cache = WeakKeyStateMap<Holder, String>(evicted::add)
        val holder = Holder("cached")

        assertEquals("first", cache.getOrPut(holder) { "first" })
        assertEquals("first", cache[holder])
        assertTrue(cache.remove(holder, "first"))
        assertNull(cache[holder])

        assertEquals("second", cache.getOrPut(holder) { "second" })
        cache.clear()
        assertNull(cache[holder])
        assertEquals(listOf("first", "second"), evicted)
    }

    @Test
    fun weak_key_state_preserves_identity_entries_across_bucket_growth() {
        val evicted = mutableListOf<Int>()
        val cache = WeakKeyStateMap<Holder, Int>(evicted::add)
        val holders = List(128) { index -> Holder("holder-$index") }

        holders.forEachIndexed { index, holder ->
            assertEquals(index, cache.getOrPut(holder) { index })
        }
        holders.forEachIndexed { index, holder ->
            assertEquals(index, cache[holder])
        }
        holders.forEachIndexed { index, holder ->
            if (index % 2 == 0) {
                assertTrue(cache.remove(holder, index))
                assertNull(cache[holder])
            }
        }

        assertEquals((0 until 128 step 2).toList(), evicted)
    }

    @Test
    fun snapshot_list_and_finalization_hook_support_manual_cleanup() {
        val registrations = SnapshotList<String>()
        registrations += "first"
        registrations += "second"

        assertEquals("second", registrations.firstNotNullOfOrNull { entry -> entry.takeIf { it.startsWith("s") } })

        val cleaned = mutableListOf<String>()
        val closeable = FinalizationHook().create { cleaned += "done" }
        closeable.close()
        closeable.close()

        assertEquals(listOf("done"), cleaned)
    }

    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    @Test
    fun finalization_token_owns_cleanup_until_closed_or_collected() {
        // CsWinRT resource finalizers follow their owner. Native's Cleaner follows
        // its token, so the shared contract explicitly makes that token the owner.
        val cleaned = kotlin.concurrent.atomics.AtomicInt(0)
        val hook = FinalizationHook()
        val retained = hook.create { cleaned.fetchAndAdd(1) }
        repeat(3) { PlatformFinalization.drain() }
        assertEquals(0, cleaned.load())
        retained.close()
        retained.close()
        assertEquals(1, cleaned.load())

        fun abandonToken() {
            hook.create { cleaned.fetchAndAdd(1) }
        }
        abandonToken()
        repeat(20) {
            PlatformFinalization.drain()
            if (cleaned.load() == 2) return
        }
        assertEquals(2, cleaned.load())
    }

    @Test
    fun closed_finalization_token_does_not_retain_cleanup_captures() {
        // Like GC.SuppressFinalize/disposing the resource in CsWinRT, explicit
        // cleanup must also release the managed state captured by that cleanup.
        fun createToken(): Pair<AutoCloseable, PlatformManagedWeakReference<Holder>> {
            val captured = Holder("captured")
            return FinalizationHook().create { assertEquals("captured", captured.value) } to
                PlatformManagedWeakReference(captured)
        }
        val (token, captured) = createToken()
        token.close()
        drainUntilCleared(captured)
        token.close()
        winRTKeepAlive(token)
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

    private fun cacheWeakValues(
        cache: WeakValueCache<Int, Holder>,
        count: Int,
    ): List<PlatformManagedWeakReference<Holder>> =
        List(count) { index ->
            val holder = Holder("value-$index")
            cache[index] = holder
            PlatformManagedWeakReference(holder)
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

    private fun drainUntilAllCleared(references: List<PlatformManagedWeakReference<Holder>>) {
        repeat(20) {
            PlatformFinalization.drain()
            if (references.all { reference -> reference.get() == null }) {
                return
            }
            val pressure = List(256) { ByteArray(1024) }
            assertEquals(256, pressure.size)
        }
        assertTrue(references.all { reference -> reference.get() == null })
    }
}
