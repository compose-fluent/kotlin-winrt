package io.github.composefluent.winrt.runtime

expect class NativeScope : AutoCloseable {
    override fun close()
}

/**
 * A native allocation whose backing memory is owned and can be freed by closing this handle.
 * This is used when transferring ownership of heap allocations (e.g. array marshalling).
 */
class OwnedNativeAllocation(
    val pointer: RawAddress,
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

    fun pointerKey(pointer: RawAddress): Long
    fun pointerKey(pointer: RawComPtr): Long

    /**
     * Allocates [sizeBytes] bytes in a shared scope that is returned as an [AutoCloseable].
     * Closing the returned scope frees the backing memory.  Use this when you need to
     * transfer ownership of a heap allocation (e.g. array marshalling).
     */
    fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation

    /** Fills [sizeBytes] bytes starting at [pointer] with zeros. */
    fun zeroBytes(pointer: RawAddress, sizeBytes: Long)
}

expect object WinRtPlatformApi {
    fun roGetActivationFactoryRaw(runtimeClassId: RawAddress, interfaceId: Guid): NativePointerResult

    fun queryInterfaceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult

    fun addRefRaw(unknown: RawAddress): UInt

    fun releaseRaw(unknown: RawAddress): UInt

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

    fun tryFormatMessageRaw(hResultValue: Int): String?

    fun lastErrorAsHResultRaw(): Int

    fun checkSucceededRaw(result: Int)

    fun resolveModulePathRaw(fileName: String): String
}
