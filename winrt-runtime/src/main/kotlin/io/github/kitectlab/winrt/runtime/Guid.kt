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
}

fun guidOf(value: String): Guid = Guid(value)
