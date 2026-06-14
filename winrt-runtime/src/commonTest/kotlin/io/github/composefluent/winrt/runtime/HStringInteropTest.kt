package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HStringInteropTest {
    @Test
    fun referenced_hstring_round_trips() {
        HString.createReference("kotlin-winrt").use { referenced ->
            assertEquals("kotlin-winrt", referenced.toKString())
        }
    }

    @Test
    fun referenced_empty_hstring_round_trips() {
        HString.createReference("").use { referenced ->
            assertEquals("", referenced.toKString())
        }
    }

    @Test
    fun string_marshaler_matches_reference_empty_and_non_empty_rules() {
        assertNull(NativeStringMarshaller.createMarshaler(null))
        assertNull(NativeStringMarshaller.createMarshaler(""))

        val marshaler = NativeStringMarshaller.createMarshaler("kotlin-winrt")
        try {
            val abi = NativeStringMarshaller.getAbi(marshaler)
            assertEquals("kotlin-winrt", NativeStringMarshaller.fromAbi(abi))
        } finally {
            NativeStringMarshaller.disposeMarshaler(marshaler)
        }
    }

    @Test
    fun string_marshaler_can_round_trip_owned_hstring_handles() {
        val handle = NativeStringMarshaller.fromManaged("projection-runtime")
        try {
            assertEquals("projection-runtime", NativeStringMarshaller.fromAbi(NativeStringMarshaller.getAbi(handle)))
        } finally {
            handle?.close()
        }
    }

    @Test
    fun string_marshaler_reads_null_hstring_as_empty_string() {
        assertEquals("", NativeStringMarshaller.fromAbi(PlatformAbi.nullPointer))
    }
}
