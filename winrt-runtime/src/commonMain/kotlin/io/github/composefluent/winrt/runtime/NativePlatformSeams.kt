package io.github.composefluent.winrt.runtime

expect class NativeScope : AutoCloseable {
    override fun close()
}

@PublishedApi
internal expect class NativeScalarScratchFrame : AutoCloseable {
    val pointer: RawAddress

    fun consumeOwnedHString(): String

    fun readPointer(): RawAddress

    fun readInt8(): Byte

    fun readInt16(): Short

    fun readInt32(): Int

    fun readInt64(): Long

    fun readFloat(): Float

    fun readDouble(): Double

    override fun close()
}

@PublishedApi
internal expect fun acquireNativeScalarScratchFrame(clear: Boolean = true): NativeScalarScratchFrame

@PublishedApi
internal expect class NativeStructScratchFrame : AutoCloseable {
    val pointer: RawAddress

    fun <T> read(adapter: NativeStructAdapter<T>): T

    fun <T> write(value: T, adapter: NativeStructAdapter<T>)

    fun disposeAbi(adapter: NativeStructAdapter<*>)

    fun readInt8Carrier(): Byte

    fun readInt16Carrier(): Short

    fun readInt32Carrier(): Int

    fun readInt64Carrier(): Long

    override fun close()
}

@PublishedApi
internal expect fun acquireNativeStructScratchFrame(
    sizeBytes: Long,
    alignmentBytes: Long,
    clear: Boolean = true,
): NativeStructScratchFrame

@PublishedApi
internal expect class NativeHStringReferenceFrame : AutoCloseable {
    var handle: RawAddress
    val utf16Chars: RawAddress
    val header: RawAddress
    val transientOut: RawAddress

    fun initializeReference(length: Int)

    override fun close()
}

internal expect fun acquireNativeHStringReferenceFrame(value: String): NativeHStringReferenceFrame

internal expect inline fun acquireScopedNativeHStringReferenceFrame(
    value: String,
    length: Int,
): RawAddress

internal expect inline fun scopedNativeHStringReferenceHandle(
    frame: RawAddress,
    length: Int,
): RawAddress

internal expect inline fun scopedNativeHStringReferenceOut(frame: RawAddress): RawAddress

internal expect inline fun releaseScopedNativeHStringReferenceFrame(frame: RawAddress)

internal fun interface RawAddressPairAction<R> {
    fun invoke(first: RawAddress, second: RawAddress): R
}

/**
 * Borrows a Windows HSTRING reference and a pointer-sized output slot for one synchronous ABI call.
 * Common code owns the call lifetime; targets only adapt storage, pinning, and raw addresses.
 */
internal inline fun <R> withNativeHStringReferenceAbi(
    value: String,
    action: RawAddressPairAction<R>,
): R {
    val length = winRTStringLength(value)
    val pinnedValue = winRTPinString(value, length)
    val frame = acquireScopedNativeHStringReferenceFrame(pinnedValue, length)
    return try {
        action.invoke(
            scopedNativeHStringReferenceHandle(frame, length),
            scopedNativeHStringReferenceOut(frame),
        )
    } finally {
        winRTKeepAlive(pinnedValue)
        releaseScopedNativeHStringReferenceFrame(frame)
    }
}

/**
 * An allocation-backed view used by runtime-owned construction paths.
 *
 * The common runtime keeps the ABI address as the public currency, while a target may retain a
 * native allocation view so repeated setup/teardown writes do not rebuild a platform wrapper for
 * the same owned memory. JVM stores the original FFM MemorySegment; mingwX64 stores the pointer
 * and delegates to its existing ABI seam.
 */
internal expect class NativeMemoryView {
    val pointer: RawAddress

    fun writePointer(offsetBytes: Long, value: RawAddress)

    fun writePointer(offsetBytes: Long, value: NativeMemoryView)

    fun writeInt64(offsetBytes: Long, value: Long)
}

/**
 * Low-level string views used by compiler-lowered direct HSTRING call sites. The returned
 * string is the lifetime owner for [winRTStringAddress]; lowering keeps it alive through the
 * native call with [winRTKeepAlive].
 */
@PublishedApi
internal expect inline fun winRTPinString(value: String, length: Int): String

@PublishedApi
internal expect inline fun winRTStringAddress(value: String, length: Int): RawAddress

@PublishedApi
internal expect inline fun winRTStringLength(value: String): Int

/**
 * A native allocation whose backing memory is owned and can be freed by closing this handle.
 * This is used when transferring ownership of heap allocations (e.g. array marshalling).
 */
class OwnedNativeAllocation internal constructor(
    val pointer: RawAddress,
    internal val memory: NativeMemoryView,
    private val onClose: () -> Unit,
) : AutoCloseable {
    override fun close() {
        onClose()
    }
}

expect class NativeCallbackHandle : AutoCloseable {
    val pointer: RawAddress

    override fun close()
}

expect object PlatformAbi {
    val nullPointer: RawAddress
    val nullComPtr: RawComPtr

    val hStringHeaderSizeBytes: Long

    fun confinedScope(): NativeScope

    fun sharedScope(): NativeScope

    fun isNull(pointer: RawAddress): Boolean
    fun isNull(pointer: RawComPtr): Boolean

    fun samePointer(first: RawAddress, second: RawAddress): Boolean
    fun samePointer(first: RawComPtr, second: RawComPtr): Boolean

    fun toRawComPtr(pointer: RawAddress): RawComPtr

    fun fromRawComPtr(pointer: RawComPtr): RawAddress

    fun allocatePointerSlot(scope: NativeScope): RawAddress

    fun allocateInt8Slot(scope: NativeScope): RawAddress

    fun allocateInt32Slot(scope: NativeScope): RawAddress

    fun allocateInt64Slot(scope: NativeScope): RawAddress

    fun allocateDoubleSlot(scope: NativeScope): RawAddress

    fun allocateBytes(scope: NativeScope, sizeBytes: Long): RawAddress

    fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): RawAddress

    fun allocatePointerArray(scope: NativeScope, size: Int): RawAddress

    fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean = false): RawAddress

    fun slice(pointer: RawAddress, offsetBytes: Long, sizeBytes: Long): RawAddress

    fun readPointer(slot: RawAddress): RawAddress

    fun readPointerAt(array: RawAddress, index: Int): RawAddress

    fun readInt8(slot: RawAddress): Byte

    fun readInt16(slot: RawAddress): Short

    fun readInt32(slot: RawAddress): Int

    fun readInt64(slot: RawAddress): Long

    fun readDouble(slot: RawAddress): Double

    fun readFloat(slot: RawAddress): Float

    fun readChar16(slot: RawAddress): Char

    fun readUtf16(pointer: RawAddress, length: Int): String

    fun readHString(handle: RawAddress): String

    fun readGuid(pointer: RawAddress): Guid

    fun writePointer(slot: RawAddress, value: RawAddress)

    fun writePointer(slot: RawAddress, offsetBytes: Long, value: RawAddress)

    fun writeInt8(slot: RawAddress, value: Byte)

    fun writeInt16(slot: RawAddress, value: Short)

    fun writeInt32(slot: RawAddress, value: Int)

    fun writeInt32(slot: RawAddress, offsetBytes: Long, value: Int)

    fun writeInt64(slot: RawAddress, value: Long)

    fun writeDouble(slot: RawAddress, value: Double)

    fun writeFloat(slot: RawAddress, value: Float)

    fun writeChar16(slot: RawAddress, value: Char)

    fun writeGuid(pointer: RawAddress, value: Guid)

    fun writeGuid(pointer: RawAddress, offsetBytes: Long, value: Guid)

    fun writePointerAt(array: RawAddress, index: Int, value: RawAddress)

    fun structArgumentWord(layout: NativeAbiLayout, address: RawAddress): Long

    fun pointerKey(pointer: RawAddress): Long
    fun pointerKey(pointer: RawComPtr): Long

    /**
     * Allocates [sizeBytes] bytes of zeroed, cross-thread native memory.
     * Closing the returned owner frees the backing memory. Use this when ownership of a heap
     * allocation must outlive one confined ABI call (e.g. CCWs and array marshalling).
     */
    fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation

    /** Fills [sizeBytes] bytes starting at [pointer] with zeros. */
    fun zeroBytes(pointer: RawAddress, sizeBytes: Long)
}

private const val queryInterfaceScratchSizeBytes = Guid.BYTE_SIZE + Long.SIZE_BYTES

internal fun queryInterfaceWithReusableScratch(
    unknown: RawAddress,
    interfaceId: Guid,
): NativePointerResult {
    if (PlatformAbi.isNull(unknown)) {
        return NativePointerResult(KnownHResults.E_POINTER.value, PlatformAbi.nullPointer)
    }
    return acquireNativeStructScratchFrame(
        sizeBytes = queryInterfaceScratchSizeBytes.toLong(),
        alignmentBytes = Long.SIZE_BYTES.toLong(),
    ).use { scratch ->
        val interfaceIdPointer = scratch.pointer
        val resultOut = PlatformAbi.slice(
            pointer = interfaceIdPointer,
            offsetBytes = Guid.BYTE_SIZE.toLong(),
            sizeBytes = Long.SIZE_BYTES.toLong(),
        )
        PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
        val hResult = ComVtableInvoker.invokeArgs(
            instance = PlatformAbi.toRawComPtr(unknown),
            slot = IUnknownVftblSlots.QueryInterface,
            arg0 = interfaceIdPointer,
            arg1 = resultOut,
        )
        NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
    }
}

expect object WinRTPlatformApi {
    fun roGetActivationFactoryRaw(runtimeClassId: RawAddress, interfaceId: Guid): NativePointerResult

    fun queryInterfaceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult

    fun addRefRaw(unknown: RawAddress): UInt

    inline fun releaseRaw(unknown: RawAddress): UInt

    fun dllGetActivationFactoryRaw(getActivationFactoryProc: RawAddress, runtimeClassId: RawAddress): NativePointerResult

    fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int = 1): NativePointerResult

    fun coInitializeExRaw(apartmentType: ApartmentType): Int

    fun coUninitializeRaw()

    fun roInitializeRaw(apartmentType: ApartmentType): Int

    fun roUninitializeRaw()

    fun coIncrementMtaUsageRaw(): NativePointerResult

    fun coDecrementMtaUsageRaw(cookie: RawAddress): Int

    fun roGetAgileReferenceRaw(unknown: RawAddress, interfaceId: Guid = IID.IUnknown): NativePointerResult

    fun coGetContextTokenRaw(): NativePointerResult

    fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult

    fun setErrorInfoRaw(errorInfo: RawAddress): Int

    fun setRestrictedErrorInfoRaw(errorInfo: RawAddress): Int?

    fun borrowRestrictedErrorInfoRaw(): RawAddress?

    fun reportUnhandledErrorRaw(errorInfo: RawAddress): Int?

    fun sysAllocStringRaw(value: String?): RawAddress

    fun sysFreeStringRaw(value: RawAddress)

    fun readAndFreeBstrRaw(value: RawAddress): String

    fun coCreateFreeThreadedMarshalerRaw(outer: RawAddress = PlatformAbi.nullPointer): NativePointerResult

    fun coTaskMemAllocRaw(sizeBytes: Long): RawAddress

    fun coTaskMemFreeRaw(pointer: RawAddress)

    fun windowsCreateStringRaw(utf16Chars: RawAddress, length: Int, outHandle: RawAddress): Int

    fun windowsCreateStringReferenceRaw(
        utf16Chars: RawAddress,
        length: Int,
        header: RawAddress,
        outHandle: RawAddress,
    ): Int

    fun windowsDeleteStringRaw(handle: RawAddress)

    fun windowsGetStringRawBufferRaw(handle: RawAddress, lengthOut: RawAddress): RawAddress

    fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress

    fun loadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress

    fun tryGetProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress

    fun getProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress

    fun freeLibraryRaw(moduleHandle: RawAddress): Boolean

    fun tryGetModuleHandleExFromAddressRaw(address: RawAddress): RawAddress

    fun isReadableMemoryRaw(address: RawAddress, sizeBytes: Long): Boolean

    fun tryFormatMessageRaw(hResultValue: Int): String?

    fun lastErrorAsHResultRaw(): Int

    fun checkSucceededRaw(result: Int)

    fun resolveModulePathRaw(fileName: String): String
}
