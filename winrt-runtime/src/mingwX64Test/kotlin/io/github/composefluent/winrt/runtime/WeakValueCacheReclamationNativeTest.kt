@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.native.runtime.NativeRuntimeApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class WeakValueCacheReclamationNativeTest {
    // CsWinRT 3 GetOrCreateObjectForComInstance delegates weak RCW identity to CLR
    // ComWrappers. Dead keys must be released without a cache lookup/size sweep,
    // while an independently live wrapper keeps its identity.
    @Test
    fun collection_releases_dead_identity_keys_without_cache_traffic() {
        val cache = WeakValueCache<Key, Any>().also { it.enableGcReclamation() }
        val liveKey = Key()
        val liveValue = Any()
        cache[liveKey] = liveValue
        val deadKey = abandonEntry(cache)

        repeat(6) { GC.collect() }

        assertNull(deadKey.get())
        assertSame(liveValue, cache[liveKey])
        winRTKeepAlive(cache)
    }

    @Test
    fun collection_notification_does_not_root_the_cache_itself() {
        val cache = abandonCache()
        repeat(6) { GC.collect() }
        assertNull(cache.get())
    }

    @Test
    fun collection_inside_key_hash_does_not_wait_for_the_cache_lock() {
        val cache = WeakValueCache<Key, Any>().also { it.enableGcReclamation() }
        val key = Key()
        val value = Any()
        cache[key] = value
        // The armed cleaner must return without blocking on the mutator's lock.
        // Keep the sole value alive so background removal never hashes this key.
        key.collectOnHash = true
        cache[key] = value
        repeat(3) { GC.collect() }
        assertSame(value, cache[key])
        winRTKeepAlive(cache)
    }

    private fun abandonEntry(cache: WeakValueCache<Key, Any>): WeakReference<Key> {
        val key = Key()
        cache[key] = Any()
        return WeakReference(key)
    }

    private fun abandonCache(): WeakReference<WeakValueCache<Key, Any>> {
        val cache = WeakValueCache<Key, Any>().also { it.enableGcReclamation() }
        cache[Key()] = Any()
        return WeakReference(cache)
    }

    private class Key {
        var collectOnHash = false

        override fun hashCode(): Int {
            if (collectOnHash) {
                collectOnHash = false
                GC.collect()
            }
            return 7
        }
    }
}
