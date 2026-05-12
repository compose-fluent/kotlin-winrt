package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

actual class NativeScope internal constructor(
    internal val arena: Arena,
    private val onClose: () -> Unit,
) : AutoCloseable {
    actual override fun close() {
        onClose()
    }
}

@OptIn(ExperimentalAtomicApi::class)
actual class NativeCallbackHandle internal constructor(
    actual val pointer: RawAddress,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicInt(0)

    actual override fun close() {
        if (closed.compareAndSet(0, 1)) {
            onClose()
        }
    }
}

actual object PlatformAbi {
    private val char16Layout = ValueLayout.JAVA_CHAR_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)

    actual val nullPointer: RawAddress
        get() = RawAddress.Null

    actual val nullComPtr: RawComPtr
        get() = RawComPtr.Null

    actual val hStringHeaderSizeBytes: Long
        get() = 24L

    actual fun confinedScope(): NativeScope =
        Arena.ofConfined().let { arena ->
            NativeScope(arena = arena, onClose = arena::close)
        }

    actual fun sharedScope(): NativeScope = NativeScope(arena = Arena.global(), onClose = {})

    actual fun isNull(pointer: RawAddress): Boolean = pointer.value == 0L

    actual fun isNull(pointer: RawComPtr): Boolean = pointer.value == 0L

    actual fun samePointer(first: RawAddress, second: RawAddress): Boolean = first.value == second.value

    actual fun samePointer(first: RawComPtr, second: RawComPtr): Boolean = first.value == second.value

    actual fun toRawComPtr(pointer: RawAddress): RawComPtr = pointer.asRawComPtr()

    actual fun fromRawComPtr(pointer: RawComPtr): RawAddress = pointer.asRawAddress()

    actual fun allocatePointerSlot(scope: NativeScope): RawAddress =
        scope.arena.allocate(ValueLayout.ADDRESS).asRawAddress()

    actual fun allocateInt8Slot(scope: NativeScope): RawAddress =
        scope.arena.allocate(ValueLayout.JAVA_BYTE).asRawAddress()

    actual fun allocateInt32Slot(scope: NativeScope): RawAddress =
        scope.arena.allocate(ValueLayout.JAVA_INT).asRawAddress()

    actual fun allocateInt64Slot(scope: NativeScope): RawAddress =
        scope.arena.allocate(ValueLayout.JAVA_LONG).asRawAddress()

    actual fun allocateDoubleSlot(scope: NativeScope): RawAddress =
        scope.arena.allocate(ValueLayout.JAVA_DOUBLE).asRawAddress()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): RawAddress =
        scope.arena.allocate(sizeBytes).asRawAddress()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): RawAddress =
        scope.arena.allocate(sizeBytes, alignmentBytes).asRawAddress()

    actual fun allocatePointerArray(scope: NativeScope, size: Int): RawAddress =
        scope.arena.allocate(ValueLayout.ADDRESS, size.toLong()).asRawAddress()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): RawAddress =
        if (nulTerminated) {
            scope.arena.allocateFrom("$value\u0000", StandardCharsets.UTF_16LE).asRawAddress()
        } else {
            scope.arena.allocateFrom(ValueLayout.JAVA_CHAR, *value.toCharArray()).asRawAddress()
        }

    actual fun slice(pointer: RawAddress, offsetBytes: Long, sizeBytes: Long): RawAddress =
        pointer.asMemorySegment()
            .reinterpret(offsetBytes + sizeBytes)
            .asSlice(offsetBytes, sizeBytes)
            .asRawAddress()

    actual fun readPointer(slot: RawAddress): RawAddress =
        slot.asMemorySegment().reinterpret(ValueLayout.ADDRESS.byteSize()).get(ValueLayout.ADDRESS, 0).asRawAddress()

    actual fun readPointerAt(array: RawAddress, index: Int): RawAddress {
        val requiredBytes = (index + 1L) * ValueLayout.ADDRESS.byteSize()
        return array.asMemorySegment()
            .reinterpret(requiredBytes)
            .getAtIndex(ValueLayout.ADDRESS, index.toLong())
            .asRawAddress()
    }

    actual fun readInt8(slot: RawAddress): Byte =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_BYTE.byteSize()).get(ValueLayout.JAVA_BYTE, 0)

    actual fun readInt16(slot: RawAddress): Short =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_SHORT.byteSize()).get(ValueLayout.JAVA_SHORT, 0)

    actual fun readInt32(slot: RawAddress): Int =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_INT.byteSize()).get(ValueLayout.JAVA_INT, 0)

    actual fun readInt64(slot: RawAddress): Long =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_LONG.byteSize()).get(ValueLayout.JAVA_LONG, 0)

    actual fun readDouble(slot: RawAddress): Double =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).get(ValueLayout.JAVA_DOUBLE, 0)

    actual fun readFloat(slot: RawAddress): Float =
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).get(ValueLayout.JAVA_FLOAT, 0)

    actual fun readChar16(slot: RawAddress): Char =
        slot.asMemorySegment().reinterpret(char16Layout.byteSize()).get(char16Layout, 0)

    actual fun readUtf16(pointer: RawAddress, length: Int): String {
        if (length == 0) {
            return ""
        }
        val sized = pointer.asMemorySegment().reinterpret(length.toLong() * ValueLayout.JAVA_CHAR.byteSize())
        val bytes = sized.toArray(ValueLayout.JAVA_BYTE)
        return String(bytes, StandardCharsets.UTF_16LE)
    }

    actual fun readGuid(pointer: RawAddress): Guid {
        val bytes = pointer.asMemorySegment().reinterpret(Guid.BYTE_SIZE.toLong()).toArray(ValueLayout.JAVA_BYTE)
        return Guid.fromLittleEndianBytes(bytes)
    }

    actual fun writePointer(slot: RawAddress, value: RawAddress) {
        slot.asMemorySegment().reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, value.asMemorySegment())
    }

    actual fun writePointer(slot: RawAddress, offsetBytes: Long, value: RawAddress) {
        slot.asMemorySegment().reinterpret(offsetBytes + ValueLayout.ADDRESS.byteSize()).set(
            ValueLayout.ADDRESS,
            offsetBytes,
            value.asMemorySegment(),
        )
    }

    actual fun writeInt8(slot: RawAddress, value: Byte) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, value)
    }

    actual fun writeInt16(slot: RawAddress, value: Short) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_SHORT.byteSize()).set(ValueLayout.JAVA_SHORT, 0, value)
    }

    actual fun writeInt32(slot: RawAddress, value: Int) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value)
    }

    actual fun writeInt32(slot: RawAddress, offsetBytes: Long, value: Int) {
        slot.asMemorySegment().reinterpret(offsetBytes + ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, offsetBytes, value)
    }

    actual fun writeInt64(slot: RawAddress, value: Long) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_LONG.byteSize()).set(ValueLayout.JAVA_LONG, 0, value)
    }

    actual fun writeDouble(slot: RawAddress, value: Double) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_DOUBLE.byteSize()).set(ValueLayout.JAVA_DOUBLE, 0, value)
    }

    actual fun writeFloat(slot: RawAddress, value: Float) {
        slot.asMemorySegment().reinterpret(ValueLayout.JAVA_FLOAT.byteSize()).set(ValueLayout.JAVA_FLOAT, 0, value)
    }

    actual fun writeChar16(slot: RawAddress, value: Char) {
        slot.asMemorySegment().reinterpret(char16Layout.byteSize()).set(char16Layout, 0, value)
    }

    actual fun writeGuid(pointer: RawAddress, value: Guid) {
        val bytes = value.toLittleEndianBytes()
        pointer.asMemorySegment().reinterpret(bytes.size.toLong()).copyFrom(MemorySegment.ofArray(bytes))
    }

    actual fun writeGuid(pointer: RawAddress, offsetBytes: Long, value: Guid) {
        writeGuid(
            pointer.asMemorySegment()
                .reinterpret(offsetBytes + Guid.BYTE_SIZE)
                .asSlice(offsetBytes, Guid.BYTE_SIZE.toLong())
                .asRawAddress(),
            value,
        )
    }

    actual fun writePointerAt(array: RawAddress, index: Int, value: RawAddress) {
        val requiredBytes = (index + 1L) * ValueLayout.ADDRESS.byteSize()
        array.asMemorySegment()
            .reinterpret(requiredBytes)
            .setAtIndex(ValueLayout.ADDRESS, index.toLong(), value.asMemorySegment())
    }

    actual fun pointerKey(pointer: RawAddress): Long = pointer.value

    actual fun pointerKey(pointer: RawComPtr): Long = pointer.value

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation {
        val arena = Arena.ofShared()
        val pointer = arena.allocate(sizeBytes, alignmentBytes).asRawAddress()
        return OwnedNativeAllocation(pointer = pointer, onClose = arena::close)
    }

    actual fun zeroBytes(pointer: RawAddress, sizeBytes: Long) {
        pointer.asMemorySegment().reinterpret(sizeBytes).fill(0)
    }
}

internal fun RawAddress.asMemorySegment(): MemorySegment =
    if (value == 0L) {
        MemorySegment.NULL
    } else {
        MemorySegment.ofAddress(value).reinterpret(Long.MAX_VALUE)
    }

internal const val IUNKNOWN_VFTBL_SIZE_BYTES: Long = 24

internal fun vtableEntry(pointer: MemorySegment, slot: Int): MemorySegment {
    val objectMemory = pointer.reinterpret(ValueLayout.ADDRESS.byteSize())
    val vtable = objectMemory.get(ValueLayout.ADDRESS, 0)
    val requiredBytes = maxOf(IUNKNOWN_VFTBL_SIZE_BYTES, (slot + 1L) * ValueLayout.ADDRESS.byteSize())
    return vtable.reinterpret(requiredBytes).getAtIndex(ValueLayout.ADDRESS, slot.toLong())
}

internal fun MemorySegment.asRawAddress(): RawAddress = RawAddress(address())
