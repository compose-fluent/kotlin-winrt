package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class Guid(val value: UUID) {
    constructor(value: String) : this(UUID.fromString(value))

    override fun toString(): String = value.toString().uppercase()

    fun writeTo(memory: MemorySegment) {
        val buffer = memory.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(value.mostSignificantBits.ushr(32).toInt())
        buffer.putShort(value.mostSignificantBits.ushr(16).toShort())
        buffer.putShort(value.mostSignificantBits.toShort())
        buffer.order(ByteOrder.BIG_ENDIAN).putLong(8, value.leastSignificantBits)
    }

    companion object {
        fun readFrom(memory: MemorySegment): Guid {
            val buffer = memory.reinterpret(AbiLayouts.GUID_SIZE_BYTES).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
            val data1 = buffer.getInt(0).toLong() and 0xFFFF_FFFFL
            val data2 = buffer.getShort(4).toLong() and 0xFFFFL
            val data3 = buffer.getShort(6).toLong() and 0xFFFFL
            val leastSignificantBits = buffer.order(ByteOrder.BIG_ENDIAN).getLong(8)
            val mostSignificantBits = (data1 shl 32) or (data2 shl 16) or data3
            return Guid(UUID(mostSignificantBits, leastSignificantBits))
        }
    }
}

fun guidOf(value: String): Guid = Guid(value)
