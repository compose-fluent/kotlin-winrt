package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@PublishedApi
internal actual fun consumeOwnedHString(handle: RawAddress): String {
    if (PlatformAbi.isNull(handle)) {
        return ""
    }
    try {
        return PlatformAbi.readHString(handle)
    } finally {
        WinRTPlatformApi.windowsDeleteStringRaw(handle)
    }
}

@PublishedApi
internal actual inline fun winRTConsumeOwnedHStringScalarResult(
    handleBits: Long,
    hResult: Int,
    checkHResult: Boolean,
): String = error("Scalar native-result transport is only available on mingwX64.")

actual class NativeScope internal constructor(
    internal val arena: Arena,
    private val onClose: () -> Unit,
) : AutoCloseable {
    actual override fun close() {
        onClose()
    }
}

@PublishedApi
internal actual class NativeScalarScratchFrame internal constructor(
    @PublishedApi internal val segment: MemorySegment,
    private val release: (NativeScalarScratchFrame) -> Unit,
) : AutoCloseable {
    actual val pointer: RawAddress = segment.asRawAddress()

    private var active: Boolean = false

    internal fun acquire(clear: Boolean): NativeScalarScratchFrame {
        check(!active) { "Native scalar scratch frame is already active." }
        if (clear) {
            segment.set(ValueLayout.JAVA_LONG, 0, 0L)
        }
        active = true
        return this
    }

    actual fun consumeOwnedHString(): String {
        val handle = segment.get(ValueLayout.ADDRESS, 0)
        if (handle.address() == 0L) {
            return ""
        }
        try {
            return PlatformAbi.readHString(handle)
        } finally {
            try {
                WinRTPlatformApi.windowsDeleteString(handle)
            } finally {
                segment.set(ValueLayout.JAVA_LONG, 0, 0L)
            }
        }
    }

    actual fun readPointer(): RawAddress =
        segment.get(ValueLayout.ADDRESS, 0).asRawAddress()

    actual fun readInt8(): Byte =
        segment.get(ValueLayout.JAVA_BYTE, 0)

    actual fun readInt16(): Short =
        segment.get(ValueLayout.JAVA_SHORT, 0)

    actual fun readInt32(): Int =
        segment.get(ValueLayout.JAVA_INT, 0)

    actual fun readInt64(): Long =
        segment.get(ValueLayout.JAVA_LONG, 0)

    actual fun readFloat(): Float =
        segment.get(ValueLayout.JAVA_FLOAT, 0)

    actual fun readDouble(): Double =
        segment.get(ValueLayout.JAVA_DOUBLE, 0)

    actual override fun close() {
        if (active) {
            release(this)
            active = false
        }
    }
}

private class JvmNativeScalarScratchFramePool {
    private val primaryFrame = createFrame()
    private val nestedFrames = mutableListOf<NativeScalarScratchFrame>()
    private var depth: Int = 0

    fun acquire(clear: Boolean): NativeScalarScratchFrame {
        val frame = if (depth == 0) {
            primaryFrame
        } else {
            nestedFrames.getOrNull(depth - 1) ?: createNestedFrame()
        }
        frame.acquire(clear)
        depth += 1
        return frame
    }

    private fun createFrame(): NativeScalarScratchFrame =
        NativeScalarScratchFrame(
            segment = Arena.global().allocate(ValueLayout.JAVA_LONG),
            release = ::release,
        )

    private fun createNestedFrame(): NativeScalarScratchFrame =
        createFrame().also(nestedFrames::add)

    private fun release(frame: NativeScalarScratchFrame) {
        check(
            depth > 0 &&
                if (depth == 1) primaryFrame === frame else nestedFrames[depth - 2] === frame,
        ) {
            "Native scalar scratch frames must close in reverse acquisition order."
        }
        depth -= 1
    }
}

private val nativeScalarScratchFrames = ThreadLocal.withInitial(::JvmNativeScalarScratchFramePool)

@PublishedApi
internal actual fun acquireNativeScalarScratchFrame(clear: Boolean): NativeScalarScratchFrame =
    nativeScalarScratchFrames.get().acquire(clear)

@PublishedApi
internal actual class NativeStructScratchFrame internal constructor(
    private val release: (NativeStructScratchFrame) -> Unit,
) : AutoCloseable {
    private var arena: Arena = Arena.ofAuto()

    @PublishedApi
    internal var segment: MemorySegment = MemorySegment.NULL
        private set

    private var capacityBytes: Long = 0L
    private var alignmentBytes: Long = 0L
    private var active: Boolean = false

    actual val pointer: RawAddress
        get() = segment.asRawAddress()

    internal fun acquire(
        sizeBytes: Long,
        alignmentBytes: Long,
        clear: Boolean,
    ): NativeStructScratchFrame {
        check(!active) { "Native struct scratch frame is already active." }
        require(sizeBytes > 0L) { "Native struct scratch size must be positive." }
        require(alignmentBytes > 0L) { "Native struct scratch alignment must be positive." }
        if (capacityBytes < sizeBytes || this.alignmentBytes < alignmentBytes) {
            arena = Arena.ofAuto()
            segment = arena.allocate(sizeBytes, alignmentBytes)
            capacityBytes = sizeBytes
            this.alignmentBytes = alignmentBytes
        } else if (clear) {
            segment.asSlice(0L, sizeBytes).fill(0)
        }
        active = true
        return this
    }

    actual fun <T> read(adapter: NativeStructAdapter<T>): T =
        adapter.read(pointer)

    actual fun <T> write(value: T, adapter: NativeStructAdapter<T>) {
        adapter.write(value, pointer)
    }

    actual fun disposeAbi(adapter: NativeStructAdapter<*>) {
        adapter.disposeAbi(pointer)
    }

    actual fun readInt8Carrier(): Byte = segment.get(ValueLayout.JAVA_BYTE, 0)

    actual fun readInt16Carrier(): Short = segment.get(ValueLayout.JAVA_SHORT, 0)

    actual fun readInt32Carrier(): Int = segment.get(ValueLayout.JAVA_INT, 0)

    actual fun readInt64Carrier(): Long = segment.get(ValueLayout.JAVA_LONG, 0)

    actual override fun close() {
        if (active) {
            release(this)
            active = false
        }
    }
}

private class JvmNativeStructScratchFramePool {
    private val primaryFrame = createFrame()
    private val nestedFrames = mutableListOf<NativeStructScratchFrame>()
    private var depth: Int = 0

    fun acquire(
        sizeBytes: Long,
        alignmentBytes: Long,
        clear: Boolean,
    ): NativeStructScratchFrame {
        val frame = if (depth == 0) {
            primaryFrame
        } else {
            nestedFrames.getOrNull(depth - 1) ?: createNestedFrame()
        }
        frame.acquire(sizeBytes, alignmentBytes, clear)
        depth += 1
        return frame
    }

    private fun createFrame(): NativeStructScratchFrame =
        NativeStructScratchFrame(release = ::release)

    private fun createNestedFrame(): NativeStructScratchFrame =
        createFrame().also(nestedFrames::add)

    private fun release(frame: NativeStructScratchFrame) {
        check(
            depth > 0 &&
                if (depth == 1) primaryFrame === frame else nestedFrames[depth - 2] === frame,
        ) {
            "Native struct scratch frames must close in reverse acquisition order."
        }
        depth -= 1
    }
}

private val nativeStructScratchFrames = ThreadLocal.withInitial(::JvmNativeStructScratchFramePool)

@PublishedApi
internal actual fun acquireNativeStructScratchFrame(
    sizeBytes: Long,
    alignmentBytes: Long,
    clear: Boolean,
): NativeStructScratchFrame =
    nativeStructScratchFrames.get().acquire(sizeBytes, alignmentBytes, clear)

@PublishedApi
internal actual class NativeHStringReferenceFrame internal constructor(
    private val release: (NativeHStringReferenceFrame) -> Unit,
) : AutoCloseable {
    private var arena: Arena? = null
    private var segment: MemorySegment = MemorySegment.NULL
    private var headerSegment: MemorySegment = MemorySegment.NULL
    private var utf16Segment: MemorySegment = MemorySegment.NULL
    private var capacityBytes: Long = 0L
    private var chars: CharArray = CharArray(0)
    private var active: Boolean = false

    actual var handle: RawAddress = RawAddress.Null

    actual val utf16Chars: RawAddress
        get() = RawAddress(segment.address() + hStringCharsOffsetBytes)

    actual val header: RawAddress
        get() = RawAddress(segment.address() + hStringHeaderOffsetBytes)

    actual val transientOut: RawAddress
        get() = segment.asRawAddress()

    internal fun acquire(value: String): NativeHStringReferenceFrame {
        check(!active) { "Native HSTRING reference frame is already active." }
        handle = RawAddress.Null
        val charCount = value.length + 1
        ensureCapacity(hStringCharsOffsetBytes + charCount.toLong() * Char.SIZE_BYTES)
        ensureCharCapacity(charCount)
        value.toCharArray(chars, destinationOffset = 0, startIndex = 0, endIndex = value.length)
        chars[value.length] = '\u0000'
        MemorySegment.copy(
            MemorySegment.ofArray(chars),
            0L,
            segment,
            hStringCharsOffsetBytes,
            charCount.toLong() * Char.SIZE_BYTES,
        )
        active = true
        return this
    }

    actual fun initializeReference(length: Int) {
        check(active) { "Native HSTRING reference frame is not active." }
        require(length >= 0) { "HSTRING length must be non-negative." }
        if (length == 0) {
            handle = RawAddress.Null
        } else {
            // Matches the fast-pass HSTRING header used by Windows SDK C++/WinRT base.h.
            headerSegment.set(ValueLayout.JAVA_INT, hStringFlagsOffsetBytes, hStringReferenceFlag)
            headerSegment.set(ValueLayout.JAVA_INT, hStringLengthOffsetBytes, length)
            headerSegment.set(ValueLayout.ADDRESS, hStringBufferOffsetBytes, utf16Segment)
            handle = headerSegment.asRawAddress()
        }
        segment.set(ValueLayout.ADDRESS, 0, MemorySegment.NULL)
    }

    actual override fun close() {
        if (active) {
            release(this)
            active = false
        }
    }

    private fun ensureCapacity(requiredBytes: Long) {
        if (capacityBytes >= requiredBytes) {
            return
        }
        var newCapacity = maxOf(hStringInitialFrameSizeBytes, capacityBytes)
        while (newCapacity < requiredBytes) {
            newCapacity = Math.multiplyExact(newCapacity, 2L)
        }
        arena?.close()
        arena = Arena.ofConfined()
        segment = requireNotNull(arena).allocate(newCapacity, ValueLayout.JAVA_LONG.byteAlignment())
        headerSegment = segment.asSlice(hStringHeaderOffsetBytes, hStringHeaderSizeBytes)
        utf16Segment = segment.asSlice(hStringCharsOffsetBytes, newCapacity - hStringCharsOffsetBytes)
        capacityBytes = newCapacity
    }

    private fun ensureCharCapacity(requiredChars: Int) {
        if (chars.size >= requiredChars) {
            return
        }
        var newCapacity = maxOf(hStringInitialCharCapacity, chars.size)
        while (newCapacity < requiredChars) {
            newCapacity = Math.multiplyExact(newCapacity, 2)
        }
        chars = CharArray(newCapacity)
    }
}

private class JvmNativeHStringReferenceFramePool {
    private val primaryFrame = createFrame()
    private val nestedFrames = mutableListOf<NativeHStringReferenceFrame>()
    private var depth: Int = 0

    fun acquire(value: String): NativeHStringReferenceFrame {
        val frame = if (depth == 0) {
            primaryFrame
        } else {
            nestedFrames.getOrNull(depth - 1) ?: createNestedFrame()
        }
        frame.acquire(value)
        depth += 1
        return frame
    }

    private fun createFrame(): NativeHStringReferenceFrame =
        NativeHStringReferenceFrame(release = ::release)

    private fun createNestedFrame(): NativeHStringReferenceFrame =
        createFrame().also(nestedFrames::add)

    private fun release(frame: NativeHStringReferenceFrame) {
        check(
            depth > 0 &&
                if (depth == 1) primaryFrame === frame else nestedFrames[depth - 2] === frame,
        ) {
            "Native HSTRING reference frames must close in reverse acquisition order."
        }
        depth -= 1
    }
}

private val nativeHStringReferenceFrames = ThreadLocal.withInitial(::JvmNativeHStringReferenceFramePool)

internal actual fun acquireNativeHStringReferenceFrame(value: String): NativeHStringReferenceFrame =
    nativeHStringReferenceFrames.get().acquire(value)

@PublishedApi
internal actual inline fun winRTPinString(value: String, length: Int): String = value

@PublishedApi
internal actual inline fun winRTStringAddress(value: String, length: Int): RawAddress = RawAddress.Null

@PublishedApi
internal actual inline fun winRTStringLength(value: String): Int = value.length

private const val hStringHeaderOffsetBytes: Long = 8L
private const val hStringHeaderSizeBytes: Long = 24L
private const val hStringCharsOffsetBytes: Long = hStringHeaderOffsetBytes + hStringHeaderSizeBytes
private const val hStringFlagsOffsetBytes: Long = 0L
private const val hStringLengthOffsetBytes: Long = 4L
private const val hStringBufferOffsetBytes: Long = 16L
private const val hStringReferenceFlag: Int = 1
private const val hStringInitialFrameSizeBytes: Long = 64L
private const val hStringInitialCharCapacity: Int = 16

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
    private val guidWordLayout = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN)

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
        return String(sized.toArray(char16Layout))
    }

    actual fun readHString(handle: RawAddress): String = readHString(handle.asMemorySegment())

    internal fun readHString(handle: MemorySegment): String {
        if (handle.address() == 0L) {
            return ""
        }
        val header = handle.reinterpret(hStringHeaderSizeBytes)
        val length = header.get(ValueLayout.JAVA_INT, hStringLengthOffsetBytes)
        require(length >= 0) { "HSTRING length exceeds the supported Kotlin String size." }
        if (length == 0) {
            return ""
        }
        val utf16 = header.get(ValueLayout.ADDRESS, hStringBufferOffsetBytes)
            .reinterpret(length.toLong() * Char.SIZE_BYTES)
        return String(utf16.toArray(char16Layout))
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
        val target = pointer.asMemorySegment().reinterpret(Guid.BYTE_SIZE.toLong())
        target.set(guidWordLayout, 0, value.abiLowBits)
        target.set(guidWordLayout, Long.SIZE_BYTES.toLong(), value.abiHighBits)
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

    actual fun structArgumentWord(layout: NativeAbiLayout, address: RawAddress): Long =
        address.value

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
