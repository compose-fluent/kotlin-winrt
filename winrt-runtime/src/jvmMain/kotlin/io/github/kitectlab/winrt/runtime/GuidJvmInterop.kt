package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

fun Guid.writeTo(memory: MemorySegment) {
    val buffer = memory.reinterpret(Guid.BYTE_SIZE.toLong()).asByteBuffer()
    buffer.put(toLittleEndianBytes())
}

fun Guid.Companion.readFrom(memory: MemorySegment): Guid {
    val buffer = memory.reinterpret(Guid.BYTE_SIZE.toLong()).asByteBuffer()
    val bytes = ByteArray(Guid.BYTE_SIZE)
    buffer.get(bytes)
    return fromLittleEndianBytes(bytes)
}
