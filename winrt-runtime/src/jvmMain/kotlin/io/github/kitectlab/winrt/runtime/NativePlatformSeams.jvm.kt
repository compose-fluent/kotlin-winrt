package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets

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

    actual val hStringHeaderSizeBytes: Long
        get() = AbiLayouts.HSTRING_HEADER.byteSize()

    actual fun confinedScope(): NativeScope = NativeScope(Arena.ofConfined())

    actual fun isNull(pointer: NativePointer): Boolean = pointer.segment == MemorySegment.NULL

    actual fun allocatePointerSlot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.ADDRESS).asNativePointer()

    actual fun allocateInt32Slot(scope: NativeScope): NativePointer =
        scope.arena.allocate(ValueLayout.JAVA_INT).asNativePointer()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer =
        scope.arena.allocate(sizeBytes).asNativePointer()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): NativePointer =
        if (nulTerminated) {
            scope.arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE).asNativePointer()
        } else {
            scope.arena.allocateFrom(ValueLayout.JAVA_CHAR, *value.toCharArray()).asNativePointer()
        }

    actual fun readPointer(slot: NativePointer): NativePointer =
        slot.segment.get(ValueLayout.ADDRESS, 0).asNativePointer()

    actual fun readInt32(slot: NativePointer): Int =
        slot.segment.get(ValueLayout.JAVA_INT, 0)

    actual fun readUtf16(pointer: NativePointer, length: Int): String {
        if (length == 0) {
            return ""
        }
        val sized = pointer.segment.reinterpret(length.toLong() * ValueLayout.JAVA_CHAR.byteSize())
        val bytes = sized.toArray(ValueLayout.JAVA_BYTE)
        return String(bytes, StandardCharsets.UTF_16LE)
    }
}

internal fun NativePointer.asMemorySegment(): MemorySegment = segment

internal fun MemorySegment.asNativePointer(): NativePointer = NativePointer(this)
