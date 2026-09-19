package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativePointerMemoryTest {
    @Test
    fun pointer_slots_preserve_all_bits_and_neighboring_memory() {
        // CsWinRT's unmanaged CCW interface records store pointer bits directly. A null
        // pointer value is valid; a null destination remains an invalid dereference.
        PlatformAbi.allocateBytesOwned(24, 8).use { allocation ->
            val before = 0x123456789abcdef0L
            val after = 0x76543210fedcba98L
            allocation.memory.writeInt64(0, before)
            allocation.memory.writeInt64(16, after)
            for (word in listOf(0L, 1L, Long.MIN_VALUE, -1L, allocation.pointer.value)) {
                allocation.memory.writePointer(8, RawAddress(word))
                assertEquals(word, PlatformAbi.readPointerAt(allocation.pointer, 1).value)
                assertEquals(word, PlatformAbi.readInt64(RawAddress(allocation.pointer.value + 8)))
                assertEquals(before, PlatformAbi.readInt64(allocation.pointer))
                assertEquals(after, PlatformAbi.readInt64(RawAddress(allocation.pointer.value + 16)))
            }
        }
    }

    @Test
    fun null_memory_view_base_is_rejected() {
        assertFailsWith<IllegalStateException> { NativeMemoryView(RawAddress.Null) }
    }
}
