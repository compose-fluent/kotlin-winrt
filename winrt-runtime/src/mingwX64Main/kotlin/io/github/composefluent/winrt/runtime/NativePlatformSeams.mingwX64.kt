@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.native.internal.InternalForKotlinNative::class,
)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.Vector128
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.io.files.Path
import kotlin.native.concurrent.ThreadLocal
import kotlin.native.internal.GCUnsafeCall
import platform.posix.getenv
import platform.posix.memset
import platform.windows.FreeLibrary
import platform.windows.FormatMessageW
import platform.windows.FlsAlloc
import platform.windows.FlsSetValue
import platform.windows.GetLastError
import platform.windows.GetProcAddress
import platform.windows.HINSTANCE__
import platform.windows.LoadLibraryA
import platform.windows.LoadLibraryExW
import platform.windows.MEMORY_BASIC_INFORMATION
import platform.windows.MEM_COMMIT
import platform.windows.PAGE_GUARD
import platform.windows.PAGE_NOACCESS
import platform.windows.VirtualQuery

private const val readablePageMask = 0x0Eu
private const val getModuleHandleExFlagFromAddress = 0x00000004u

actual class NativeScope internal constructor(
    private val ownsAllocations: Boolean,
) : AutoCloseable {
    private val allocations = mutableListOf<COpaquePointer>()

    internal fun allocate(sizeBytes: Long): RawAddress {
        require(sizeBytes >= 0) { "Native allocation size must be non-negative." }
        val pointer = nativeHeap.allocArray<ByteVar>(sizeBytes.toInt()).reinterpret<COpaque>()
        if (ownsAllocations) {
            allocations += pointer
        }
        return pointer.asRawAddress()
    }

    actual override fun close() {
        if (!ownsAllocations) {
            return
        }
        allocations.asReversed().forEach { pointer -> nativeHeap.free(pointer.rawValue) }
        allocations.clear()
    }
}

internal actual class NativeMemoryView internal constructor(
    private val rawPointer: RawAddress,
) {
    actual val pointer: RawAddress
        get() = rawPointer

    actual fun writePointer(offsetBytes: Long, value: RawAddress) {
        PlatformAbi.writePointer(rawPointer, offsetBytes, value)
    }

    actual fun writePointer(offsetBytes: Long, value: NativeMemoryView) {
        PlatformAbi.writePointer(rawPointer, offsetBytes, value.pointer)
    }

    actual fun writeInt64(offsetBytes: Long, value: Long) {
        PlatformAbi.writeInt64(RawAddress(rawPointer.value + offsetBytes), value)
    }
}

@PublishedApi
internal actual class NativeScalarScratchFrame internal constructor(
    @PublishedApi internal val storage: CPointer<LongVar>,
    private val release: (NativeScalarScratchFrame) -> Unit,
) : AutoCloseable {
    actual val pointer: RawAddress = storage.reinterpret<COpaque>().asRawAddress()

    private var active: Boolean = false

    internal fun acquire(clear: Boolean): NativeScalarScratchFrame {
        check(!active) { "Native scalar scratch frame is already active." }
        if (clear) {
            storage.pointed.value = 0L
        }
        active = true
        return this
    }

    actual fun consumeOwnedHString(): String {
        val handle = storage.reinterpret<COpaquePointerVar>().pointed.value.asRawAddress()
        if (PlatformAbi.isNull(handle)) {
            return ""
        }
        try {
            return nativeReadHString(handle)
        } finally {
            try {
                WinRTPlatformApi.windowsDeleteStringRaw(handle)
            } finally {
                storage.pointed.value = 0L
            }
        }
    }

    actual fun readPointer(): RawAddress =
        storage.reinterpret<COpaquePointerVar>().pointed.value.asRawAddress()

    actual fun readInt8(): Byte =
        storage.reinterpret<ByteVar>().pointed.value

    actual fun readInt16(): Short =
        storage.reinterpret<ShortVar>().pointed.value

    actual fun readInt32(): Int =
        storage.reinterpret<IntVar>().pointed.value

    actual fun readInt64(): Long =
        storage.pointed.value

    actual fun readFloat(): Float =
        storage.reinterpret<FloatVar>().pointed.value

    actual fun readDouble(): Double =
        storage.reinterpret<DoubleVar>().pointed.value

    actual override fun close() {
        if (active) {
            release(this)
            active = false
        }
    }
}

private class MingwNativeScalarScratchFramePool {
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
            storage = nativeHeap.alloc<LongVar>().ptr,
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

@ThreadLocal
private object NativeScalarScratchFrames {
    val pool = MingwNativeScalarScratchFramePool()
}

@PublishedApi
internal actual fun acquireNativeScalarScratchFrame(clear: Boolean): NativeScalarScratchFrame =
    NativeScalarScratchFrames.pool.acquire(clear)

@PublishedApi
internal actual class NativeStructScratchFrame internal constructor(
    private val release: (NativeStructScratchFrame) -> Unit,
) : AutoCloseable {
    private var allocation: COpaquePointer? = null
    private var capacityBytes: Long = 0L
    private var active: Boolean = false

    actual val pointer: RawAddress
        get() = checkNotNull(allocation) { "Native struct scratch frame has no active allocation." }.asRawAddress()

    internal fun acquire(
        sizeBytes: Long,
        alignmentBytes: Long,
        clear: Boolean,
    ): NativeStructScratchFrame {
        check(!active) { "Native struct scratch frame is already active." }
        require(sizeBytes > 0L) { "Native struct scratch size must be positive." }
        require(alignmentBytes > 0L) { "Native struct scratch alignment must be positive." }
        require(alignmentBytes <= 16L) {
            "Native struct scratch alignment $alignmentBytes exceeds the Windows x64 heap alignment."
        }
        if (capacityBytes < sizeBytes) {
            allocation?.let { pointer -> nativeHeap.free(pointer.rawValue) }
            allocation = nativeHeap.allocArray<ByteVar>(sizeBytes.toInt()).reinterpret<COpaque>()
            capacityBytes = sizeBytes
        }
        if (clear) {
            memset(checkNotNull(allocation), 0, sizeBytes.toULong())
        }
        active = true
        return this
    }

    actual fun readInt8Carrier(): Byte =
        checkNotNull(allocation).reinterpret<ByteVar>().pointed.value

    actual fun readInt16Carrier(): Short =
        checkNotNull(allocation).reinterpret<ShortVar>().pointed.value

    actual fun readInt32Carrier(): Int =
        checkNotNull(allocation).reinterpret<IntVar>().pointed.value

    actual fun readInt64Carrier(): Long =
        checkNotNull(allocation).reinterpret<LongVar>().pointed.value

    actual override fun close() {
        if (active) {
            release(this)
            active = false
        }
    }
}

private class MingwNativeStructScratchFramePool {
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

@ThreadLocal
private object NativeStructScratchFrames {
    val pool = MingwNativeStructScratchFramePool()
}

@PublishedApi
internal actual fun acquireNativeStructScratchFrame(
    sizeBytes: Long,
    alignmentBytes: Long,
    clear: Boolean,
): NativeStructScratchFrame =
    NativeStructScratchFrames.pool.acquire(sizeBytes, alignmentBytes, clear)

@PublishedApi
internal actual class NativeHStringReferenceFrame internal constructor(
    private val release: (NativeHStringReferenceFrame) -> Unit,
) : AutoCloseable {
    private var allocation: COpaquePointer? = null
    private var base: RawAddress = RawAddress.Null
    private var capacityBytes: Long = 0L
    private var pinnedValue: String? = null
    private var active: Boolean = false

    actual var handle: RawAddress = RawAddress.Null

    actual val utf16Chars: RawAddress
        get() = pinnedValue?.nativeAddressOf(0).asRawAddress()

    actual val header: RawAddress
        get() = RawAddress(base.value + hStringHeaderOffsetBytes)

    actual val transientOut: RawAddress
        get() = base

    internal fun acquire(value: String): NativeHStringReferenceFrame {
        check(!active) { "Native HSTRING reference frame is already active." }
        handle = RawAddress.Null
        ensureCapacity(hStringFrameSizeBytes)
        // Mirrors CsWinRT MarshalString.Pinnable: the HSTRING borrows pinned UTF-16 storage for this call.
        pinnedValue = if (value.isEmpty()) null else value.toNativePinnable()
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
            val headerFields = header.asCPointer<IntVar>()
            headerFields[0] = hStringReferenceFlag
            headerFields[1] = length
            RawAddress(header.value + hStringBufferOffsetBytes)
                .asCPointer<COpaquePointerVar>()
                .pointed.value = utf16Chars.toOpaquePointer()
            handle = header
        }
        base.asCPointer<COpaquePointerVar>().pointed.value = null
    }

    actual override fun close() {
        if (active) {
            release(this)
            pinnedValue = null
            active = false
        }
    }

    private fun ensureCapacity(requiredBytes: Long) {
        if (capacityBytes >= requiredBytes) {
            return
        }
        require(requiredBytes <= Int.MAX_VALUE.toLong()) {
            "Native HSTRING reference frame exceeds the supported allocation size."
        }
        var newCapacity = maxOf(hStringInitialFrameSizeBytes, capacityBytes)
        while (newCapacity < requiredBytes) {
            newCapacity = minOf(newCapacity * 2L, Int.MAX_VALUE.toLong())
        }
        allocation?.let { nativeHeap.free(it.rawValue) }
        allocation = nativeHeap.allocArray<ByteVar>(newCapacity.toInt()).reinterpret<COpaque>()
        base = requireNotNull(allocation).asRawAddress()
        capacityBytes = newCapacity
        memset(base.toOpaquePointer(), 0, capacityBytes.toULong())
    }
}

private class MingwNativeHStringReferenceFramePool {
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

@ThreadLocal
private object NativeHStringReferenceFrames {
    val pool = MingwNativeHStringReferenceFramePool()
}

internal actual fun acquireNativeHStringReferenceFrame(value: String): NativeHStringReferenceFrame =
    NativeHStringReferenceFrames.pool.acquire(value)

internal actual inline fun acquireScopedNativeHStringReferenceFrame(
    value: String,
    length: Int,
): RawAddress {
    val frame = acquireScopedNativeHStringFrame()
    return try {
        initializeScopedNativeHStringFrame(
            frame = frame,
            chars = winRTStringAddress(value, length),
            length = length,
        )
        frame
    } catch (error: Throwable) {
        releaseScopedNativeHStringFrame(frame)
        throw error
    }
}

internal actual inline fun scopedNativeHStringReferenceHandle(
    frame: RawAddress,
    length: Int,
): RawAddress =
    if (length == 0) RawAddress.Null else RawAddress(frame.value + scopedNativeHStringHeaderOffsetBytes)

internal actual inline fun scopedNativeHStringReferenceOut(frame: RawAddress): RawAddress =
    RawAddress(frame.value + scopedNativeHStringResultOffsetBytes)

internal actual inline fun releaseScopedNativeHStringReferenceFrame(frame: RawAddress) {
    releaseScopedNativeHStringFrame(frame)
}

@PublishedApi
internal fun acquireScopedNativeHStringFrame(): RawAddress {
    val index = ScopedNativeHStringFrames.index
    val pool = scopedNativeHStringFlsGetValue(index).let { current ->
        if (current != 0L) current.toCPointer<LongVar>() else createScopedNativeHStringPool(index)
    } ?: error("Native HSTRING scope has no frame pool.")
    val depth = pool[scopedNativeHStringPoolDepthWord].toInt()
    val frame = if (depth < scopedNativeHStringEmbeddedFrameCount) {
        RawAddress(
            pool.rawValue.toLong() +
                (scopedNativeHStringEmbeddedFramesWord + depth * scopedNativeHStringFrameWordCount) *
                Long.SIZE_BYTES,
        )
    } else {
        val overflowIndex = depth - scopedNativeHStringEmbeddedFrameCount
        val frames = ensureScopedNativeHStringOverflowCapacity(pool, overflowIndex)
        val overflowFrame = frames[overflowIndex]
            ?: nativeHeap.allocArray<LongVar>(scopedNativeHStringFrameWordCount).also { created ->
            memset(created, 0, scopedNativeHStringFrameSizeBytes.toULong())
            created[scopedNativeHStringFrameOwnerWord] = pool.rawValue.toLong()
            frames[overflowIndex] = created.reinterpret<COpaque>()
        }.reinterpret<COpaque>()
        RawAddress(overflowFrame.rawValue.toLong())
    }
    pool[scopedNativeHStringPoolDepthWord] = (depth + 1).toLong()
    return frame
}

@PublishedApi
internal fun initializeScopedNativeHStringFrame(
    frame: RawAddress,
    chars: RawAddress,
    length: Int,
): RawAddress {
    val frameWords = frame.value.toCPointer<LongVar>()
        ?: error("Native HSTRING scope has no frame storage.")
    frameWords[scopedNativeHStringFrameResultWord] = 0L
    if (length == 0) {
        return RawAddress.Null
    }
    frameWords[scopedNativeHStringFrameHeaderWord] =
        (length.toLong() shl Int.SIZE_BITS) or hStringReferenceFlag.toLong()
    frameWords[scopedNativeHStringFrameHeaderWord + 1] = 0L
    frameWords[scopedNativeHStringFrameHeaderWord + 2] = chars.value
    return RawAddress(frame.value + scopedNativeHStringHeaderOffsetBytes)
}

@PublishedApi
internal fun releaseScopedNativeHStringFrame(frame: RawAddress) {
    val frameWords = frame.value.toCPointer<LongVar>()
        ?: error("Native HSTRING scope has no frame storage.")
    val pool = frameWords[scopedNativeHStringFrameOwnerWord].toCPointer<LongVar>()
        ?: error("Native HSTRING scope has no owning pool.")
    val depth = pool[scopedNativeHStringPoolDepthWord].toInt()
    check(depth > 0) { "Native HSTRING scope depth is invalid." }
    val expectedFrame = if (depth <= scopedNativeHStringEmbeddedFrameCount) {
        pool.rawValue.toLong() +
            (scopedNativeHStringEmbeddedFramesWord + (depth - 1) * scopedNativeHStringFrameWordCount) *
            Long.SIZE_BYTES
    } else {
        val frames = pool[scopedNativeHStringPoolOverflowFramesWord]
            .toCPointer<COpaquePointerVar>()
            ?: error("Native HSTRING scope has no overflow frame index.")
        frames[depth - 1 - scopedNativeHStringEmbeddedFrameCount]?.rawValue?.toLong()
    }
    check(expectedFrame == frame.value) {
        "Native HSTRING scopes must close in reverse acquisition order."
    }
    pool[scopedNativeHStringPoolDepthWord] = (depth - 1).toLong()
}

@GCUnsafeCall("FlsGetValue")
private external fun scopedNativeHStringFlsGetValue(index: UInt): Long

private fun createScopedNativeHStringPool(index: UInt): CPointer<LongVar> {
    val pool = nativeHeap.allocArray<LongVar>(scopedNativeHStringPoolWordCount)
    memset(pool, 0, scopedNativeHStringPoolSizeBytes.toULong())
    repeat(scopedNativeHStringEmbeddedFrameCount) { frameIndex ->
        pool[
            scopedNativeHStringEmbeddedFramesWord +
                frameIndex * scopedNativeHStringFrameWordCount +
                scopedNativeHStringFrameOwnerWord
        ] = pool.rawValue.toLong()
    }
    if (FlsSetValue(index, pool.reinterpret<COpaque>()) == 0) {
        nativeHeap.free(pool.rawValue)
        error("FlsSetValue failed for a Native HSTRING scope pool.")
    }
    return pool
}

private fun ensureScopedNativeHStringOverflowCapacity(
    pool: CPointer<LongVar>,
    requiredIndex: Int,
): CPointer<COpaquePointerVar> {
    val currentCapacity = pool[scopedNativeHStringPoolOverflowCapacityWord].toInt()
    val currentFrames = pool[scopedNativeHStringPoolOverflowFramesWord]
        .toCPointer<COpaquePointerVar>()
    if (requiredIndex < currentCapacity) {
        return currentFrames ?: error("Native HSTRING scope has no overflow frame index.")
    }
    var newCapacity = maxOf(currentCapacity, scopedNativeHStringInitialOverflowCapacity)
    while (requiredIndex >= newCapacity) {
        check(newCapacity <= Int.MAX_VALUE / 2) { "Native HSTRING scope nesting is too deep." }
        newCapacity *= 2
    }
    val newFrames = nativeHeap.allocArray<COpaquePointerVar>(newCapacity)
    memset(newFrames, 0, (newCapacity * Long.SIZE_BYTES).toULong())
    if (currentFrames != null) {
        repeat(currentCapacity) { index -> newFrames[index] = currentFrames[index] }
        nativeHeap.free(currentFrames.rawValue)
    }
    pool[scopedNativeHStringPoolOverflowCapacityWord] = newCapacity.toLong()
    pool[scopedNativeHStringPoolOverflowFramesWord] = newFrames.rawValue.toLong()
    return newFrames
}

private fun releaseScopedNativeHStringPool(storage: COpaquePointer?) {
    val pool = storage?.reinterpret<LongVar>() ?: return
    val capacity = pool[scopedNativeHStringPoolOverflowCapacityWord].toInt()
    val frames = pool[scopedNativeHStringPoolOverflowFramesWord].toCPointer<COpaquePointerVar>()
    if (frames != null) {
        repeat(capacity) { index ->
            frames[index]?.let { frame -> nativeHeap.free(frame.rawValue) }
        }
        nativeHeap.free(frames.rawValue)
    }
    nativeHeap.free(pool.rawValue)
}

private object ScopedNativeHStringFrames {
    val index: UInt = FlsAlloc(staticCFunction(::releaseScopedNativeHStringPool)).also { value ->
        check(value != UInt.MAX_VALUE) { "FlsAlloc failed for Native HSTRING scope pools." }
    }
}

@PublishedApi
internal actual inline fun winRTPinString(value: String, length: Int): String =
    if (length == 0) value else value.toNativePinnable()

@PublishedApi
internal actual inline fun winRTStringAddress(value: String, length: Int): RawAddress =
    if (length == 0) {
        RawAddress.Null
    } else {
        RawAddress(value.nativeAddressOf(0).rawValue.toLong())
    }

private const val hStringFrameSizeBytes: Long = hStringHeaderOffsetBytes + hStringHeaderSizeBytes
private const val hStringInitialFrameSizeBytes: Long = hStringFrameSizeBytes
private const val scopedNativeHStringPoolDepthWord: Int = 0
private const val scopedNativeHStringPoolOverflowCapacityWord: Int = 1
private const val scopedNativeHStringPoolOverflowFramesWord: Int = 2
private const val scopedNativeHStringEmbeddedFramesWord: Int = 3
private const val scopedNativeHStringEmbeddedFrameCount: Int = 4
private const val scopedNativeHStringInitialOverflowCapacity: Int = 4
private const val scopedNativeHStringFrameOwnerWord: Int = 0
private const val scopedNativeHStringFrameResultWord: Int = 1
private const val scopedNativeHStringFrameHeaderWord: Int = 2
private const val scopedNativeHStringFrameWordCount: Int = 5
private const val scopedNativeHStringFrameSizeBytes: Int = scopedNativeHStringFrameWordCount * Long.SIZE_BYTES
private const val scopedNativeHStringPoolWordCount: Int =
    scopedNativeHStringEmbeddedFramesWord +
        scopedNativeHStringEmbeddedFrameCount * scopedNativeHStringFrameWordCount
private const val scopedNativeHStringPoolSizeBytes: Int = scopedNativeHStringPoolWordCount * Long.SIZE_BYTES
private const val scopedNativeHStringResultOffsetBytes: Long = 8L
private const val scopedNativeHStringHeaderOffsetBytes: Long = 16L

actual class NativeCallbackHandle internal constructor(
    actual val pointer: RawAddress,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val lock = PlatformLock()
    private var closed: Boolean = false

    actual override fun close() {
        val shouldClose = lock.withLock {
            if (closed) {
                false
            } else {
                closed = true
                true
            }
        }
        if (shouldClose) {
            onClose()
        }
    }
}

actual object PlatformAbi {
    actual val nullPointer: RawAddress = RawAddress.Null
    actual val nullComPtr: RawComPtr = RawComPtr.Null

    actual fun confinedScope(): NativeScope = NativeScope(ownsAllocations = true)

    actual fun sharedScope(): NativeScope = NativeScope(ownsAllocations = false)

    actual fun isNull(pointer: RawAddress): Boolean = pointer.value == 0L

    actual fun isNull(pointer: RawComPtr): Boolean = pointer.value == 0L

    actual fun toRawComPtr(pointer: RawAddress): RawComPtr = pointer.asRawComPtr()

    actual fun fromRawComPtr(pointer: RawComPtr): RawAddress = pointer.asRawAddress()

    actual fun allocatePointerSlot(scope: NativeScope): RawAddress =
        allocateBytes(scope, sizeOf<COpaquePointerVar>())

    actual fun allocateInt8Slot(scope: NativeScope): RawAddress =
        allocateBytes(scope, sizeOf<ByteVar>())

    actual fun allocateInt32Slot(scope: NativeScope): RawAddress =
        allocateBytes(scope, sizeOf<IntVar>())

    actual fun allocateInt64Slot(scope: NativeScope): RawAddress =
        allocateBytes(scope, sizeOf<LongVar>())

    actual fun allocateDoubleSlot(scope: NativeScope): RawAddress =
        allocateBytes(scope, sizeOf<DoubleVar>())

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): RawAddress =
        scope.allocate(sizeBytes).also { zeroBytes(it, sizeBytes) }

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): RawAddress =
        allocateBytes(scope, sizeBytes)

    actual fun allocatePointerArray(scope: NativeScope, size: Int): RawAddress =
        allocateBytes(scope, size.toLong() * sizeOf<COpaquePointerVar>())

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): RawAddress {
        val length = value.length + if (nulTerminated) 1 else 0
        val pointer = allocateBytes(scope, length.toLong() * sizeOf<UShortVar>())
        val chars = pointer.asCPointer<UShortVar>()
        var index = 0
        while (index < value.length) {
            chars[index] = value[index].code.toUShort()
            index += 1
        }
        if (nulTerminated) {
            chars[value.length] = 0u
        }
        return pointer
    }

    actual fun slice(pointer: RawAddress, offsetBytes: Long, sizeBytes: Long): RawAddress =
        RawAddress(pointer.value + offsetBytes)

    actual fun readPointer(slot: RawAddress): RawAddress =
        slot.asCPointer<COpaquePointerVar>().pointed.value.asRawAddress()

    actual fun readPointerAt(array: RawAddress, index: Int): RawAddress =
        array.asCPointer<COpaquePointerVar>()[index].asRawAddress()

    actual fun readInt8(slot: RawAddress): Byte =
        slot.asCPointer<ByteVar>().pointed.value

    actual fun readInt16(slot: RawAddress): Short =
        slot.asCPointer<ShortVar>().pointed.value

    actual fun readInt32(slot: RawAddress): Int =
        slot.asCPointer<IntVar>().pointed.value

    actual fun readInt64(slot: RawAddress): Long =
        slot.asCPointer<LongVar>().pointed.value

    actual fun readDouble(slot: RawAddress): Double =
        slot.asCPointer<DoubleVar>().pointed.value

    actual fun readFloat(slot: RawAddress): Float =
        slot.asCPointer<FloatVar>().pointed.value

    actual fun readChar16(slot: RawAddress): Char =
        slot.asCPointer<UShortVar>().pointed.value.toInt().toChar()

    actual fun readUtf16(pointer: RawAddress, length: Int): String {
        if (length == 0) {
            return ""
        }
        return pointer.asCPointer<UShortVar>().toLengthAwareKString(length)
    }

    actual fun readHString(handle: RawAddress): String {
        return nativeReadHString(handle)
    }

    actual fun readGuid(pointer: RawAddress): Guid =
        Guid.fromLittleEndianBytes(pointer.readBytes(Guid.BYTE_SIZE))

    actual fun writePointer(slot: RawAddress, value: RawAddress) {
        slot.asCPointer<COpaquePointerVar>().pointed.value = value.toOpaquePointer()
    }

    actual fun writePointer(slot: RawAddress, offsetBytes: Long, value: RawAddress) {
        writePointer(slice(slot, offsetBytes, sizeOf<COpaquePointerVar>()), value)
    }

    actual fun writeInt8(slot: RawAddress, value: Byte) {
        slot.asCPointer<ByteVar>().pointed.value = value
    }

    actual fun writeInt16(slot: RawAddress, value: Short) {
        slot.asCPointer<ShortVar>().pointed.value = value
    }

    actual fun writeInt32(slot: RawAddress, value: Int) {
        slot.asCPointer<IntVar>().pointed.value = value
    }

    actual fun writeInt32(slot: RawAddress, offsetBytes: Long, value: Int) {
        writeInt32(slice(slot, offsetBytes, sizeOf<IntVar>()), value)
    }

    actual fun writeInt64(slot: RawAddress, value: Long) {
        slot.asCPointer<LongVar>().pointed.value = value
    }

    actual fun writeDouble(slot: RawAddress, value: Double) {
        slot.asCPointer<DoubleVar>().pointed.value = value
    }

    actual fun writeFloat(slot: RawAddress, value: Float) {
        slot.asCPointer<FloatVar>().pointed.value = value
    }

    actual fun writeChar16(slot: RawAddress, value: Char) {
        slot.asCPointer<UShortVar>().pointed.value = value.code.toUShort()
    }

    actual fun writeGuid(pointer: RawAddress, value: Guid) {
        val words = pointer.asCPointer<LongVar>()
        words[0] = value.abiLowBits
        words[1] = value.abiHighBits
    }

    actual fun writeGuid(pointer: RawAddress, offsetBytes: Long, value: Guid) {
        writeGuid(slice(pointer, offsetBytes, Guid.BYTE_SIZE.toLong()), value)
    }

    actual fun writePointerAt(array: RawAddress, index: Int, value: RawAddress) {
        array.asCPointer<COpaquePointerVar>()[index] = value.toOpaquePointer()
    }

    actual fun structArgumentWord(layout: NativeAbiLayout, address: RawAddress): Long {
        if (layout.byteSize > 8L) {
            return address.value
        }
        val bytes = address.readBytes(layout.byteSize.toInt())
        var word = 0L
        bytes.forEachIndexed { index, value ->
            word = word or ((value.toLong() and 0xFFL) shl (index * 8))
        }
        return word
    }

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation {
        val pointer = nativeHeap.allocArray<ByteVar>(sizeBytes.toInt()).reinterpret<COpaque>()
        val raw = pointer.asRawAddress()
        zeroBytes(raw, sizeBytes)
        return OwnedNativeAllocation(
            pointer = raw,
            memory = NativeMemoryView(raw),
            onClose = { nativeHeap.free(pointer.rawValue) },
        )
    }

    actual fun zeroBytes(pointer: RawAddress, sizeBytes: Long) {
        memset(pointer.toOpaquePointer(), 0, sizeBytes.toULong())
    }
}

@PublishedApi
internal inline fun nativeReadHString(handle: RawAddress): String {
    if (handle.value == 0L) {
        return ""
    }
    val length = RawAddress(handle.value + hStringLengthOffsetBytes)
        .asCPointer<IntVar>()
        .pointed.value
    require(length >= 0) { "HSTRING length exceeds the supported Kotlin String size." }
    if (length == 0) {
        return ""
    }
    val utf16 = RawAddress(handle.value + hStringBufferOffsetBytes)
        .asCPointer<COpaquePointerVar>()
        .pointed.value
        ?.reinterpret<UShortVar>()
        ?: error("Non-empty HSTRING has a null UTF-16 buffer.")
    return utf16.toLengthAwareKString(length)
}

@PublishedApi
internal actual fun consumeOwnedHString(handle: RawAddress): String {
    if (handle.value == 0L) {
        return ""
    }
    try {
        return nativeReadHString(handle)
    } finally {
        WinRTPlatformApi.windowsDeleteStringRaw(handle)
    }
}

@PublishedApi
internal actual inline fun winRTConsumeOwnedHStringScalarResult(
    handleBits: Long,
    hResult: Int,
    checkHResult: Boolean,
): String {
    val handle = RawAddress(handleBits)
    if (checkHResult && hResult < 0) {
        if (handle.value != 0L) {
            windowsDeleteStringDirect(handleBits)
        }
        HResult(hResult).requireSuccess("WinRT call")
    }
    if (handleBits == 0L) {
        return ""
    }
    try {
        return nativeReadHString(handle)
    } finally {
        windowsDeleteStringDirect(handleBits)
    }
}

@GCUnsafeCall("CreateStringFromUtf16")
@PublishedApi
internal external fun CPointer<UShortVar>.toLengthAwareKString(length: Int): String

@GCUnsafeCall("WindowsDeleteString")
@PublishedApi
internal external fun windowsDeleteStringDirect(handle: Long): Int

@GCUnsafeCall("Kotlin_Interop_pinnable")
@PublishedApi
internal external fun <T> T.toNativePinnable(): T

@GCUnsafeCall("Kotlin_Arrays_getStringAddressOfElement")
@PublishedApi
internal external fun String.nativeAddressOf(index: Int): CPointer<COpaque>

private fun COpaquePointer?.asRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)

private fun RawAddress.toOpaquePointer(): COpaquePointer? =
    if (value == 0L) {
        null
    } else {
        value.toCPointer<COpaque>()
    }

@PublishedApi
internal inline fun <reified T : CPointed> RawAddress.asCPointer(): CPointer<T> =
    value.toCPointer<T>() ?: error("Cannot dereference a null native pointer.")

private fun RawAddress.readBytes(size: Int): ByteArray {
    val bytes = asCPointer<ByteVar>()
    return ByteArray(size) { index -> bytes[index] }
}

private fun RawAddress.writeBytes(values: ByteArray) {
    val bytes = asCPointer<ByteVar>()
    values.forEachIndexed { index, value ->
        bytes[index] = value
    }
}

actual object WinRTPlatformApi {
    private val combaseModule by lazy {
        LoadLibraryA("combase.dll")
    }

    private val ole32Module by lazy {
        LoadLibraryA("ole32.dll")
    }

    private val oleaut32Module by lazy {
        LoadLibraryA("oleaut32.dll")
    }

    private val kernel32Module by lazy {
        LoadLibraryA("kernel32.dll")
    }

    private val coInitializeExProc: CPointer<CFunction<(COpaquePointer?, UInt) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoInitializeEx")?.reinterpret()
    }

    private val coUninitializeProc: CPointer<CFunction<() -> Unit>>? by lazy {
        GetProcAddress(ole32Module, "CoUninitialize")?.reinterpret()
    }

    private val coIncrementMtaUsageProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoIncrementMTAUsage")?.reinterpret()
            ?: GetProcAddress(combaseModule, "CoIncrementMTAUsage")?.reinterpret()
    }

    private val coDecrementMtaUsageProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoDecrementMTAUsage")?.reinterpret()
            ?: GetProcAddress(combaseModule, "CoDecrementMTAUsage")?.reinterpret()
    }

    private val coGetContextTokenProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoGetContextToken")?.reinterpret()
            ?: GetProcAddress(combaseModule, "CoGetContextToken")?.reinterpret()
    }

    private val coGetObjectContextProc: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoGetObjectContext")?.reinterpret()
            ?: GetProcAddress(combaseModule, "CoGetObjectContext")?.reinterpret()
    }

    private val coCreateFreeThreadedMarshalerProc: CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoCreateFreeThreadedMarshaler")?.reinterpret()
            ?: GetProcAddress(combaseModule, "CoCreateFreeThreadedMarshaler")?.reinterpret()
    }

    private val coCreateInstanceProc:
        CPointer<CFunction<(COpaquePointer?, COpaquePointer?, UInt, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
            GetProcAddress(ole32Module, "CoCreateInstance")?.reinterpret()
                ?: GetProcAddress(combaseModule, "CoCreateInstance")?.reinterpret()
        }

    private val setErrorInfoProc: CPointer<CFunction<(UInt, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(oleaut32Module, "SetErrorInfo")?.reinterpret()
    }

    private val sysAllocStringLenProc: CPointer<CFunction<(COpaquePointer?, UInt) -> COpaquePointer?>>? by lazy {
        GetProcAddress(oleaut32Module, "SysAllocStringLen")?.reinterpret()
    }

    private val sysFreeStringProc: CPointer<CFunction<(COpaquePointer?) -> Unit>>? by lazy {
        GetProcAddress(oleaut32Module, "SysFreeString")?.reinterpret()
    }

    private val sysStringLenProc: CPointer<CFunction<(COpaquePointer?) -> UInt>>? by lazy {
        GetProcAddress(oleaut32Module, "SysStringLen")?.reinterpret()
    }

    private val winRTErrorModule by lazy {
        sequenceOf(
            "api-ms-win-core-winrt-error-l1-1-1.dll",
            "api-ms-win-core-winrt-error-l1-1-0.dll",
        ).mapNotNull { moduleName -> LoadLibraryA(moduleName) }
            .firstOrNull()
    }

    private val getRestrictedErrorInfoProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(winRTErrorModule, "GetRestrictedErrorInfo")?.reinterpret()
            ?: GetProcAddress(combaseModule, "GetRestrictedErrorInfo")?.reinterpret()
    }

    private val setRestrictedErrorInfoProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(winRTErrorModule, "SetRestrictedErrorInfo")?.reinterpret()
            ?: GetProcAddress(combaseModule, "SetRestrictedErrorInfo")?.reinterpret()
    }

    private val roReportUnhandledErrorProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(winRTErrorModule, "RoReportUnhandledError")?.reinterpret()
            ?: GetProcAddress(combaseModule, "RoReportUnhandledError")?.reinterpret()
    }

    private val roInitializeProc: CPointer<CFunction<(Int) -> Int>>? by lazy {
        GetProcAddress(combaseModule, "RoInitialize")?.reinterpret()
    }

    private val roUninitializeProc: CPointer<CFunction<() -> Unit>>? by lazy {
        GetProcAddress(combaseModule, "RoUninitialize")?.reinterpret()
    }

    private val roGetActivationFactoryProc: CPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(combaseModule, "RoGetActivationFactory")?.reinterpret()
    }

    private val roGetAgileReferenceProc:
        CPointer<CFunction<(Int, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
            GetProcAddress(combaseModule, "RoGetAgileReference")?.reinterpret()
        }

    private val coTaskMemAllocProc: CPointer<CFunction<(ULong) -> COpaquePointer?>>? by lazy {
        GetProcAddress(ole32Module, "CoTaskMemAlloc")?.reinterpret()
    }

    private val coTaskMemFreeProc: CPointer<CFunction<(COpaquePointer?) -> Unit>>? by lazy {
        GetProcAddress(ole32Module, "CoTaskMemFree")?.reinterpret()
    }

    private val getModuleHandleExWProc: CPointer<CFunction<(UInt, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(kernel32Module, "GetModuleHandleExW")?.reinterpret()
    }

    private val windowsCreateStringProc: CPointer<CFunction<(COpaquePointer?, UInt, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(combaseModule, "WindowsCreateString")?.reinterpret()
    }

    private val windowsCreateStringReferenceProc:
        CPointer<CFunction<(COpaquePointer?, UInt, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
            GetProcAddress(combaseModule, "WindowsCreateStringReference")?.reinterpret()
        }

    private val windowsGetStringRawBufferProc:
        CPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>? by lazy {
            GetProcAddress(combaseModule, "WindowsGetStringRawBuffer")?.reinterpret()
        }

    actual fun roGetActivationFactoryRaw(runtimeClassId: RawAddress, interfaceId: Guid): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
            val hResult = roGetActivationFactoryProc?.invoke(
                runtimeClassId.toOpaquePointer(),
                interfaceIdPointer.toOpaquePointer(),
                resultOut.toOpaquePointer(),
            ) ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun queryInterfaceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult =
        queryInterfaceWithReusableScratch(unknown, interfaceId)

    actual fun addRefRaw(unknown: RawAddress): UInt =
        invokeUnknownRefCountMethod(unknown, IUnknownVftblSlots.AddRef)

    actual inline fun releaseRaw(unknown: RawAddress): UInt =
        invokeUnknownRefCountMethod(unknown, IUnknownVftblSlots.Release)

    actual fun dllGetActivationFactoryRaw(
        getActivationFactoryProc: RawAddress,
        runtimeClassId: RawAddress,
    ): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val proc = getActivationFactoryProc.asCFunction<(COpaquePointer?, COpaquePointer?) -> Int>()
            val hResult = proc.invoke(runtimeClassId.toOpaquePointer(), resultOut.toOpaquePointer())
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val classIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val interfaceIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeGuid(classIdPointer, classId)
            PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
            val hResult = coCreateInstanceProc?.invoke(
                classIdPointer.toOpaquePointer(),
                null,
                classContext.toUInt(),
                interfaceIdPointer.toOpaquePointer(),
                resultOut.toOpaquePointer(),
            ) ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun coInitializeExRaw(apartmentType: ApartmentType): Int {
        return coInitializeExProc?.invoke(null, apartmentType.coInitializeFlags.toUInt())
            ?: KnownHResults.E_NOTIMPL.value
    }

    actual fun coUninitializeRaw() {
        coUninitializeProc?.invoke()
    }

    actual fun roInitializeRaw(apartmentType: ApartmentType): Int {
        return roInitializeProc?.invoke(apartmentType.roInitializeType) ?: KnownHResults.E_NOTIMPL.value
    }

    actual fun roUninitializeRaw() {
        roUninitializeProc?.invoke()
    }

    actual fun coIncrementMtaUsageRaw(): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val cookieOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = coIncrementMtaUsageProc?.invoke(cookieOut.toOpaquePointer())
                ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(cookieOut))
        }

    actual fun coDecrementMtaUsageRaw(cookie: RawAddress): Int =
        coDecrementMtaUsageProc?.invoke(cookie.toOpaquePointer()) ?: KnownHResults.E_NOTIMPL.value

    actual fun roGetAgileReferenceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
            val hResult = roGetAgileReferenceProc?.invoke(
                0,
                interfaceIdPointer.toOpaquePointer(),
                unknown.toOpaquePointer(),
                resultOut.toOpaquePointer(),
            ) ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun coGetContextTokenRaw(): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val tokenOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = coGetContextTokenProc?.invoke(tokenOut.toOpaquePointer())
                ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(tokenOut))
        }

    actual fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
            val hResult = coGetObjectContextProc?.invoke(interfaceIdPointer.toOpaquePointer(), resultOut.toOpaquePointer())
                ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun setErrorInfoRaw(errorInfo: RawAddress): Int =
        setErrorInfoProc?.invoke(0u, errorInfo.toOpaquePointer()) ?: KnownHResults.E_NOTIMPL.value

    actual fun setRestrictedErrorInfoRaw(errorInfo: RawAddress): Int? =
        setRestrictedErrorInfoProc?.invoke(errorInfo.toOpaquePointer())

    actual fun borrowRestrictedErrorInfoRaw(): RawAddress? {
        val getErrorInfo = getRestrictedErrorInfoProc ?: return null
        return PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = getErrorInfo.invoke(resultOut.toOpaquePointer())
            HResult(hResult).requireSuccess("GetRestrictedErrorInfo")
            val errorInfo = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(errorInfo)) {
                null
            } else {
                setRestrictedErrorInfoProc?.invoke(errorInfo.toOpaquePointer())
                errorInfo
            }
        }
    }

    actual fun reportUnhandledErrorRaw(errorInfo: RawAddress): Int? =
        roReportUnhandledErrorProc?.invoke(errorInfo.toOpaquePointer())

    actual fun sysAllocStringRaw(value: String?): RawAddress {
        val allocator = sysAllocStringLenProc ?: return PlatformAbi.nullPointer
        if (value.isNullOrEmpty()) {
            return allocator.invoke(null, 0u).asRawAddress()
        }
        return PlatformAbi.confinedScope().use { scope ->
            val utf16 = PlatformAbi.allocateUtf16(scope, value, nulTerminated = true)
            allocator.invoke(utf16.toOpaquePointer(), value.length.toUInt()).asRawAddress()
        }
    }

    actual fun sysFreeStringRaw(value: RawAddress) {
        if (!PlatformAbi.isNull(value)) {
            sysFreeStringProc?.invoke(value.toOpaquePointer())
        }
    }

    actual fun readAndFreeBstrRaw(value: RawAddress): String {
        if (PlatformAbi.isNull(value)) {
            return ""
        }
        return try {
            val charCount = sysStringLenProc?.invoke(value.toOpaquePointer())?.toInt() ?: 0
            PlatformAbi.readUtf16(value, charCount)
        } finally {
            sysFreeStringRaw(value)
        }
    }

    actual fun coCreateFreeThreadedMarshalerRaw(outer: RawAddress): NativePointerResult =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = coCreateFreeThreadedMarshalerProc?.invoke(
                outer.toOpaquePointer(),
                resultOut.toOpaquePointer(),
            ) ?: KnownHResults.E_NOTIMPL.value
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun coTaskMemAllocRaw(sizeBytes: Long): RawAddress =
        coTaskMemAllocProc?.invoke(sizeBytes.toULong()).asRawAddress()

    actual fun coTaskMemFreeRaw(pointer: RawAddress) {
        coTaskMemFreeProc?.invoke(pointer.toOpaquePointer())
    }

    actual fun windowsCreateStringRaw(utf16Chars: RawAddress, length: Int, outHandle: RawAddress): Int =
        windowsCreateStringProc?.invoke(
            utf16Chars.toOpaquePointer(),
            length.toUInt(),
            outHandle.toOpaquePointer(),
        ) ?: KnownHResults.E_NOTIMPL.value

    actual fun windowsCreateStringReferenceRaw(
        utf16Chars: RawAddress,
        length: Int,
        header: RawAddress,
        outHandle: RawAddress,
    ): Int =
        windowsCreateStringReferenceProc?.invoke(
            utf16Chars.toOpaquePointer(),
            length.toUInt(),
            header.toOpaquePointer(),
            outHandle.toOpaquePointer(),
        ) ?: KnownHResults.E_NOTIMPL.value

    actual fun windowsDeleteStringRaw(handle: RawAddress) {
        windowsDeleteStringDirect(handle.value)
    }

    actual fun windowsGetStringRawBufferRaw(handle: RawAddress, lengthOut: RawAddress): RawAddress =
        windowsGetStringRawBufferProc?.invoke(handle.toOpaquePointer(), lengthOut.toOpaquePointer()).asRawAddress()

    actual fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress =
        LoadLibraryExW(absolutePath, null, flags.toUInt()).asRawAddress()

    actual fun loadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress =
        tryLoadLibraryExWRaw(absolutePath, flags).also { handle ->
            if (PlatformAbi.isNull(handle)) {
                checkSucceededRaw(lastErrorAsHResultRaw())
            }
        }

    actual fun tryGetProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress =
        GetProcAddress(moduleHandle.asModuleHandle(), procedureName).asRawAddress()

    actual fun getProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress =
        tryGetProcAddressRaw(moduleHandle, procedureName).also { address ->
            if (PlatformAbi.isNull(address)) {
                checkSucceededRaw(lastErrorAsHResultRaw())
            }
        }

    actual fun freeLibraryRaw(moduleHandle: RawAddress): Boolean =
        FreeLibrary(moduleHandle.asModuleHandle()) != 0

    actual fun tryGetModuleHandleExFromAddressRaw(address: RawAddress): RawAddress {
        if (PlatformAbi.isNull(address)) {
            return RawAddress.Null
        }
        val proc = getModuleHandleExWProc ?: return RawAddress.Null
        return memScoped {
            val moduleOut = alloc<COpaquePointerVar>()
            val ok = proc.invoke(
                getModuleHandleExFlagFromAddress,
                address.toOpaquePointer(),
                moduleOut.ptr.reinterpret(),
            )
            if (ok == 0) {
                RawAddress.Null
            } else {
                moduleOut.value.asRawAddress()
            }
        }
    }

    actual fun isReadableMemoryRaw(address: RawAddress, sizeBytes: Long): Boolean {
        if (PlatformAbi.isNull(address) || sizeBytes <= 0L) {
            return false
        }
        return memScoped {
            val info = alloc<MEMORY_BASIC_INFORMATION>()
            val bytes = VirtualQuery(
                address.toOpaquePointer(),
                info.ptr,
                sizeOf<MEMORY_BASIC_INFORMATION>().toULong(),
            )
            if (bytes == 0uL) {
                false
            } else {
                val protect = info.Protect
                info.State == MEM_COMMIT.toUInt() &&
                    protect and PAGE_NOACCESS.toUInt() == 0u &&
                    protect and PAGE_GUARD.toUInt() == 0u &&
                    protect and readablePageMask != 0u &&
                    info.RegionSize.toLong() >= sizeBytes
            }
        }
    }

    actual fun tryFormatMessageRaw(hResultValue: Int): String? {
        val capacity = 2048
        val buffer = nativeHeap.allocArray<UShortVar>(capacity)
        return try {
            val charCount = FormatMessageW(
                0x12FFu,
                null,
                hResultValue.toUInt(),
                0u,
                buffer,
                capacity.toUInt(),
                null,
            ).toInt()
            if (charCount <= 0) {
                null
            } else {
                PlatformAbi.readUtf16(buffer.asRawAddress(), charCount)
            }
        } finally {
            nativeHeap.free(buffer.rawValue)
        }
    }

    actual fun lastErrorAsHResultRaw(): Int =
        ExceptionHelpers.hResultFromWin32(GetLastError().toInt()).value

    actual fun resolveModulePathRaw(fileName: String): String =
        nativeRuntimeAssetCandidates(fileName)
            .firstOrNull { candidate -> Path(candidate).isRegularFile() }
            ?.let(::absolutePath)
            ?: fileName
}

@Suppress("NOTHING_TO_INLINE")
@PublishedApi
internal inline fun invokeUnknownRefCountMethod(
    unknown: RawAddress,
    slot: Int,
): UInt {
    val objectMemory = unknown.value.toCPointer<COpaquePointerVar>()
        ?: error("COM object pointer is null.")
    val vtable = objectMemory.pointed.value ?: error("COM object has a null vtable.")
    val function = vtable.reinterpret<COpaquePointerVar>()[slot]
        ?.reinterpret<CFunction<(COpaquePointer?) -> UInt>>()
        ?: error("COM vtable slot $slot is null.")
    return function.invoke(objectMemory.reinterpret<COpaque>())
}

private const val runtimeAssetsDirectoryName = "kotlin-winrt-runtime-assets"
private const val runtimeAssetsRootEnvironmentVariableName = "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT"

private fun nativeRuntimeAssetCandidates(fileName: String): Sequence<String> = sequence {
    getenv(runtimeAssetsRootEnvironmentVariableName)?.toKString()?.takeIf { it.isNotBlank() }?.let { root ->
        yield("$root/$fileName")
    }
    yield(fileName)
    yield("$runtimeAssetsDirectoryName/$fileName")
    yield("kotlin-winrt/runtime-assets/$fileName")
    yield("build/kotlin-winrt/runtime-assets/$fileName")
}

private fun <T : Function<Int>> RawAddress.asCFunction(): CPointer<CFunction<T>> =
    value.toCPointer<CFunction<T>>() ?: error("Cannot call a null native function pointer.")

private fun RawAddress.asModuleHandle(): CPointer<HINSTANCE__>? =
    if (value == 0L) null else value.toCPointer()
