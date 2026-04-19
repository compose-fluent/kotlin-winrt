package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.util.UUID

object BooleanMarshaller {
    fun toAbi(value: Boolean): Byte = if (value) 1 else 0

    fun fromAbi(value: Byte): Boolean = value.toInt() != 0

    fun copyTo(value: Boolean, destination: MemorySegment) {
        destination.set(ValueLayout.JAVA_BYTE, 0, toAbi(value))
    }

    fun readFrom(source: MemorySegment): Boolean = fromAbi(source.get(ValueLayout.JAVA_BYTE, 0))
}

object CharMarshaller {
    fun toAbi(value: Char): Short = value.code.toShort()

    fun fromAbi(value: Short): Char = value.toInt().toChar()

    fun copyTo(value: Char, destination: MemorySegment) {
        destination.set(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 0, toAbi(value))
    }

    fun readFrom(source: MemorySegment): Char =
        fromAbi(source.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), 0))
}

object GuidMarshaller {
    fun copyTo(value: Guid, destination: MemorySegment) {
        value.writeTo(destination)
    }

    fun readFrom(source: MemorySegment): Guid {
        val buffer = source.asSlice(0, 16).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val data1 = buffer.int.toLong() and 0xFFFFFFFFL
        val data2 = buffer.short.toLong() and 0xFFFFL
        val data3 = buffer.short.toLong() and 0xFFFFL

        buffer.order(ByteOrder.BIG_ENDIAN)
        val data4 = ByteArray(8)
        buffer.get(data4)

        val msb = (data1 shl 32) or (data2 shl 16) or data3
        var lsb = 0L
        for (byte in data4) {
            lsb = (lsb shl 8) or (byte.toLong() and 0xFF)
        }

        return Guid(UUID(msb, lsb))
    }
}
