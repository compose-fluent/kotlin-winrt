package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

fun Guid.writeTo(memory: MemorySegment) {
    writeTo(memory.asNativePointer())
}

fun Guid.Companion.readFrom(memory: MemorySegment): Guid {
    return readFrom(memory.asNativePointer())
}
