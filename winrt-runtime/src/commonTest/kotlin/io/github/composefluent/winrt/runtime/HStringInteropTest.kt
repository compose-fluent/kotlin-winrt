package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun hstring_reference_frame_reuses_and_clears_top_level_storage() {
        val firstFrameAddress = acquireNativeHStringReferenceFrame("first").use { frame ->
            PlatformAbi.writePointer(frame.transientOut, frame.utf16Chars)
            PlatformAbi.writeInt64(frame.header, 0x1122334455667788L)
            PlatformAbi.pointerKey(frame.transientOut)
        }

        acquireNativeHStringReferenceFrame("second").use { frame ->
            assertEquals(firstFrameAddress, PlatformAbi.pointerKey(frame.transientOut))
            assertEquals(0L, PlatformAbi.pointerKey(PlatformAbi.readPointer(frame.transientOut)))
            assertEquals(0L, PlatformAbi.readInt64(frame.header))
            assertEquals("second", PlatformAbi.readUtf16(frame.utf16Chars, "second".length))
        }
    }

    @Test
    fun nested_hstring_reference_frames_preserve_outer_storage() {
        acquireNativeHStringReferenceFrame("outer").use { outer ->
            acquireNativeHStringReferenceFrame("inner").use { inner ->
                assertNotEquals(
                    PlatformAbi.pointerKey(outer.transientOut),
                    PlatformAbi.pointerKey(inner.transientOut),
                )
                assertEquals("outer", PlatformAbi.readUtf16(outer.utf16Chars, "outer".length))
                assertEquals("inner", PlatformAbi.readUtf16(inner.utf16Chars, "inner".length))
            }

            assertEquals("outer", PlatformAbi.readUtf16(outer.utf16Chars, "outer".length))
        }
    }

    @Test
    fun nested_referenced_hstrings_preserve_outer_windows_reference() {
        HString.createReference("outer").use { outer ->
            HString.createReference("inner").use { inner ->
                assertEquals("outer", outer.toKString())
                assertEquals("inner", inner.toKString())
            }

            assertEquals("outer", outer.toKString())
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
