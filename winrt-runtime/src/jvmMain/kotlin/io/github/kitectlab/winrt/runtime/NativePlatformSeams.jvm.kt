package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

actual class NativePointer internal constructor(
    internal val segment: MemorySegment,
)

actual class NativeScope internal constructor(
    internal val arena: Arena,
) : AutoCloseable {
    actual override fun close() {
        arena.close()
    }
}

actual object NativeInterop {
    actual val nullPointer: NativePointer
        get() = NativePointer(MemorySegment.NULL)

    actual fun confinedScope(): NativeScope = NativeScope(Arena.ofConfined())

    actual fun isNull(pointer: NativePointer): Boolean = pointer.segment == MemorySegment.NULL
}

internal fun NativePointer.asMemorySegment(): MemorySegment = segment

internal fun MemorySegment.asNativePointer(): NativePointer = NativePointer(this)
