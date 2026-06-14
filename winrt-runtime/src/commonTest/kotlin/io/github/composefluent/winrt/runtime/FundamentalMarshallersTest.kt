package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FundamentalMarshallersTest {
    @Test
    fun boolean_marshaller_round_trips() {
        PlatformAbi.confinedScope().use { scope ->
            val memory = PlatformAbi.allocateInt8Slot(scope)
            BooleanMarshaller.copyTo(true, memory)
            assertTrue(BooleanMarshaller.readFrom(memory))

            BooleanMarshaller.copyTo(false, memory)
            assertFalse(BooleanMarshaller.readFrom(memory))
        }
    }

    @Test
    fun char_marshaller_round_trips() {
        PlatformAbi.confinedScope().use { scope ->
            val memory = PlatformAbi.allocateBytes(scope, 2)
            CharMarshaller.copyTo('K', memory)
            assertEquals('K', CharMarshaller.readFrom(memory))
        }
    }

    @Test
    fun guid_marshaller_round_trips() {
        PlatformAbi.confinedScope().use { scope ->
            val memory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val guid = Guid("AF86E2E0-B12D-4C6A-9C5A-D7AA65101E90")
            GuidMarshaller.copyTo(guid, memory)
            assertEquals(guid, GuidMarshaller.readFrom(memory))
        }
    }

    @Test
    fun abi_layouts_match_expected_foundational_sizes() {
        assertEquals(16, Guid.BYTE_SIZE)
        assertEquals(24L, PlatformAbi.hStringHeaderSizeBytes)
        assertEquals(0, IUnknownVftblSlots.QueryInterface)
        assertEquals(1, IUnknownVftblSlots.AddRef)
        assertEquals(2, IUnknownVftblSlots.Release)
    }
}
