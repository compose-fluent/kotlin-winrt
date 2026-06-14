package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.ValueLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class FundamentalMarshallersJvmTest {
    @Test
    fun raw_vtable_helper_returns_expected_slot_entry() {
        Arena.ofConfined().use { arena ->
            val queryInterface = arena.allocate(1)
            val addRef = arena.allocate(1)
            val release = arena.allocate(1)
            val vtable = arena.allocate(MemoryLayout.sequenceLayout(3, ValueLayout.ADDRESS))
            vtable.setAtIndex(ValueLayout.ADDRESS, 0, queryInterface)
            vtable.setAtIndex(ValueLayout.ADDRESS, 1, addRef)
            vtable.setAtIndex(ValueLayout.ADDRESS, 2, release)
            val instance = arena.allocate(ValueLayout.ADDRESS)
            instance.set(ValueLayout.ADDRESS, 0, vtable)

            assertEquals(queryInterface, RawVtableCallJvmCompat.entry(instance, IUnknownVftblSlots.QueryInterface))
            assertEquals(addRef, RawVtableCallJvmCompat.entry(instance, IUnknownVftblSlots.AddRef))
            assertEquals(release, RawVtableCallJvmCompat.entry(instance, IUnknownVftblSlots.Release))
        }
    }
}
