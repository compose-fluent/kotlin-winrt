package io.github.kitectlab.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class GuidTest {
    @Test
    fun guid_round_trips_little_endian_abi_bytes() {
        val guid = Guid("00112233-4455-6677-8899-AABBCCDDEEFF")

        val littleEndian =
            byteArrayOf(
                0x33.toByte(),
                0x22.toByte(),
                0x11.toByte(),
                0x00.toByte(),
                0x55.toByte(),
                0x44.toByte(),
                0x77.toByte(),
                0x66.toByte(),
                0x88.toByte(),
                0x99.toByte(),
                0xAA.toByte(),
                0xBB.toByte(),
                0xCC.toByte(),
                0xDD.toByte(),
                0xEE.toByte(),
                0xFF.toByte(),
            )

        assertContentEquals(littleEndian, guid.toLittleEndianBytes())
        assertEquals(guid, Guid.fromLittleEndianBytes(littleEndian))
    }
}
