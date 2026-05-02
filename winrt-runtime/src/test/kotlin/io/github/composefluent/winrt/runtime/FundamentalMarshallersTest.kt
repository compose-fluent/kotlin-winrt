package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FundamentalMarshallersTest {
    @Test
    fun boolean_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(1)
            BooleanMarshaller.copyTo(true, memory)
            assertTrue(BooleanMarshaller.readFrom(memory))

            BooleanMarshaller.copyTo(false, memory)
            assertFalse(BooleanMarshaller.readFrom(memory))
        }
    }

    @Test
    fun char_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(2)
            CharMarshaller.copyTo('K', memory)
            assertEquals('K', CharMarshaller.readFrom(memory))
        }
    }

    @Test
    fun guid_marshaller_round_trips() {
        Arena.ofConfined().use { arena ->
            val memory = arena.allocate(NativeLayoutsJvmCompat.GUID)
            val guid = Guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
            GuidMarshaller.copyTo(guid, memory)
            assertEquals(guid, GuidMarshaller.readFrom(memory))
        }
    }

    @Test
    fun abi_layouts_match_expected_primitive_sizes() {
        assertEquals(NativeLayoutsJvmCompat.GUID_SIZE_BYTES, NativeLayoutsJvmCompat.GUID.byteSize())
        assertEquals(24L, NativeLayoutsJvmCompat.HSTRING_HEADER_SIZE_BYTES)
        assertEquals(24L, NativeLayoutsJvmCompat.IUNKNOWN_VFTBL_SIZE_BYTES)
    }

    @Test
    fun referenced_hstring_round_trips_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        HString.createReference("kotlin-winrt").use { referenced ->
            assertEquals("kotlin-winrt", referenced.toKString())
        }
    }

    @Test
    fun referenced_empty_hstring_round_trips_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        HString.createReference("").use { referenced ->
            assertEquals("", referenced.toKString())
        }
    }

    @Test
    fun string_marshaler_matches_cswinrt_empty_and_non_empty_rules_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        assertNull(StringMarshaller.createMarshaler(null))
        assertNull(StringMarshaller.createMarshaler(""))

        val marshaler = StringMarshaller.createMarshaler("kotlin-winrt")
        try {
            val abi = StringMarshaller.getAbi(marshaler)
            assertEquals("kotlin-winrt", StringMarshaller.fromAbi(abi))
        } finally {
            StringMarshaller.disposeMarshaler(marshaler)
        }
    }

    @Test
    fun string_marshaler_can_round_trip_owned_hstring_handles_on_windows() {
        assumeTrue(PlatformRuntime.isWindows)

        val handle = StringMarshaller.fromManaged("projection-runtime")
        try {
            assertEquals("projection-runtime", StringMarshaller.fromAbi(StringMarshaller.getAbi(handle)))
        } finally {
            handle?.close()
        }
    }

    @Test
    fun string_marshaler_reads_null_hstring_as_empty_string() {
        assertEquals("", StringMarshaller.fromAbi(MemorySegment.NULL))
    }

    @Test
    fun string_marshaler_reads_hstring_pointer_from_memory() {
        assumeTrue(PlatformRuntime.isWindows)

        val handle = StringMarshaller.fromManaged("memory-read")
        try {
            Arena.ofConfined().use { arena ->
                val memory = arena.allocate(ValueLayout.ADDRESS)
                memory.set(ValueLayout.ADDRESS, 0, StringMarshaller.getAbi(handle))
                assertEquals("memory-read", StringMarshaller.readFrom(memory))
            }
        } finally {
            handle?.close()
        }
    }

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
