package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeakIdentityStorageTest {
    @Test
    fun reclaimed_burst_releases_peak_buckets_without_losing_surviving_identities() {
        // CsWinRT delegates identity storage to CLR ComWrappers. Exercise the Kotlin
        // table directly with deterministic weak notifications on both targets.
        val entries = mutableListOf<Entry>()
        val evicted = mutableListOf<Int>()
        val core = WeakKeyStateMapCore(
            object : WeakKeyStateEntryFactory<Any, Int> {
                override fun create(key: Any, value: Int, identityHashCode: Int): Entry =
                    Entry(key, value, identityHashCode).also(entries::add)
            },
            evicted::add,
        )
        val keys = List(4096) { Any() }
        keys.forEachIndexed { index, key -> core.getOrPut(key, index) { index } }
        // These hash values collide only once the bucket array returns to 16 slots.
        assertNull(entries[0].bucketNext)
        assertNull(entries[16].bucketNext)
        keys.indices.filter { it != 0 && it != 16 }.forEach { index ->
            if (index % 2 == 0) {
                assertEquals(index, core.remove(keys[index], index))
            } else {
                entries[index].key = null
                assertFalse(core.sweep(entries[index]))
                assertFalse(core.sweep(entries[index]))
            }
        }
        assertEquals(4094, evicted.size)
        assertEquals(4094, evicted.toSet().size)
        assertTrue(entries[0].bucketNext === entries[16] || entries[16].bucketNext === entries[0])
        assertEquals(0, core.value(keys[0], 0))
        assertEquals(16, core.value(keys[16], 16))
        assertEquals(16, core.fastValue(keys[16], 16))
        assertTrue(core.sweep(entries[16]))

        // Grow again and remove through collision chains; survivors must remain unique.
        (4096 until 8192).forEach { index -> core.getOrPut(Any(), index) { index } }
        assertEquals(16, core.remove(keys[16], 16))
        assertEquals(0, core.value(keys[0], 0))
        core.clear()
        assertEquals(8192, evicted.size)
        assertEquals(8192, evicted.toSet().size)
        assertNull(core.fastValue(keys[0], 0))
    }

    private class Entry(
        var key: Any?,
        override val value: Int,
        override val identityHashCode: Int,
    ) : WeakKeyStateEntry<Any, Int> {
        override var bucketNext: WeakKeyStateEntry<Any, Int>? = null
        override fun keyOrNull(): Any? = key
    }
}
