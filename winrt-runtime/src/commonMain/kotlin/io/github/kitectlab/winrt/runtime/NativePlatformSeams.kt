package io.github.kitectlab.winrt.runtime

expect class NativePointer

expect class NativeScope : AutoCloseable {
    override fun close()
}

/**
 * A native allocation whose backing memory is owned and can be freed by closing this handle.
 * This is used when transferring ownership of heap allocations (e.g. array marshalling).
 */
class OwnedNativeAllocation(
    val pointer: NativePointer,
    private val onClose: () -> Unit,
) : AutoCloseable {
    override fun close() {
        onClose()
    }
}

enum class NativeValueLayout {
    ADDRESS,
    JAVA_BYTE,
    JAVA_INT,
    JAVA_LONG,
    JAVA_DOUBLE,
}

data class NativeFunctionDescriptor(
    val returnLayout: NativeValueLayout?,
    val argumentLayouts: List<NativeValueLayout>,
) {
    companion object {
        fun of(
            returnLayout: NativeValueLayout,
            vararg argumentLayouts: NativeValueLayout,
        ): NativeFunctionDescriptor = NativeFunctionDescriptor(returnLayout, argumentLayouts.toList())

        fun ofVoid(
            vararg argumentLayouts: NativeValueLayout,
        ): NativeFunctionDescriptor = NativeFunctionDescriptor(null, argumentLayouts.toList())
    }
}

expect class NativeCallbackHandle : AutoCloseable {
    val pointer: NativePointer

    override fun close()
}

expect object NativeInterop {
    val nullPointer: NativePointer

    val hStringHeaderSizeBytes: Long

    fun confinedScope(): NativeScope

    fun sharedScope(): NativeScope

    fun isNull(pointer: NativePointer): Boolean

    fun samePointer(first: NativePointer, second: NativePointer): Boolean

    fun allocatePointerSlot(scope: NativeScope): NativePointer

    fun allocateInt8Slot(scope: NativeScope): NativePointer

    fun allocateInt32Slot(scope: NativeScope): NativePointer

    fun allocateInt64Slot(scope: NativeScope): NativePointer

    fun allocateDoubleSlot(scope: NativeScope): NativePointer

    fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer

    fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): NativePointer

    fun allocatePointerArray(scope: NativeScope, size: Int): NativePointer

    fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean = false): NativePointer

    fun slice(pointer: NativePointer, offsetBytes: Long, sizeBytes: Long): NativePointer

    fun readPointer(slot: NativePointer): NativePointer

    fun readPointerAt(array: NativePointer, index: Int): NativePointer

    fun readInt8(slot: NativePointer): Byte

    fun readInt16(slot: NativePointer): Short

    fun readInt32(slot: NativePointer): Int

    fun readInt64(slot: NativePointer): Long

    fun readDouble(slot: NativePointer): Double

    fun readFloat(slot: NativePointer): Float

    fun readChar16(slot: NativePointer): Char

    fun readUtf16(pointer: NativePointer, length: Int): String

    fun readGuid(pointer: NativePointer): Guid

    fun writePointer(slot: NativePointer, value: NativePointer)

    fun writePointer(slot: NativePointer, offsetBytes: Long, value: NativePointer)

    fun writeInt8(slot: NativePointer, value: Byte)

    fun writeInt16(slot: NativePointer, value: Short)

    fun writeInt32(slot: NativePointer, value: Int)

    fun writeInt32(slot: NativePointer, offsetBytes: Long, value: Int)

    fun writeInt64(slot: NativePointer, value: Long)

    fun writeDouble(slot: NativePointer, value: Double)

    fun writeFloat(slot: NativePointer, value: Float)

    fun writeChar16(slot: NativePointer, value: Char)

    fun writeGuid(pointer: NativePointer, value: Guid)

    fun writeGuid(pointer: NativePointer, offsetBytes: Long, value: Guid)

    fun writePointerAt(array: NativePointer, index: Int, value: NativePointer)

    fun pointerKey(pointer: NativePointer): Long

    fun invokeVtableInt32(
        instance: NativePointer,
        slot: Int,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int

    fun invokeFunctionInt32(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int

    fun invokeFunctionVoid(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    )

    fun createCallback(
        descriptor: NativeFunctionDescriptor,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle

    /**
     * Allocates [sizeBytes] bytes in a shared scope that is returned as an [AutoCloseable].
     * Closing the returned scope frees the backing memory.  Use this when you need to
     * transfer ownership of a heap allocation (e.g. array marshalling).
     */
    fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation

    /** Fills [sizeBytes] bytes starting at [pointer] with zeros. */
    fun zeroBytes(pointer: NativePointer, sizeBytes: Long)
}

expect object WinRtPlatformApi {
    fun roGetActivationFactoryRaw(runtimeClassId: NativePointer, interfaceId: Guid): NativePointerResult

    fun queryInterfaceRaw(unknown: NativePointer, interfaceId: Guid): NativePointerResult

    fun addRefRaw(unknown: NativePointer): UInt

    fun releaseRaw(unknown: NativePointer): UInt

    fun dllGetActivationFactoryRaw(getActivationFactoryProc: NativePointer, runtimeClassId: NativePointer): NativePointerResult

    fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int = 1): NativePointerResult

    fun coInitializeExRaw(apartmentType: ApartmentType): Int

    fun coUninitializeRaw()

    fun roInitializeRaw(apartmentType: ApartmentType): Int

    fun roUninitializeRaw()

    fun coIncrementMtaUsageRaw(): NativePointerResult

    fun coDecrementMtaUsageRaw(cookie: NativePointer): Int

    fun roGetAgileReferenceRaw(unknown: NativePointer, interfaceId: Guid = IID.IUnknown): NativePointerResult

    fun coGetContextTokenRaw(): NativePointerResult

    fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult

    fun setErrorInfoRaw(errorInfo: NativePointer): Int

    fun setRestrictedErrorInfoRaw(errorInfo: NativePointer): Int?

    fun borrowRestrictedErrorInfoRaw(): NativePointer?

    fun reportUnhandledErrorRaw(errorInfo: NativePointer): Int?

    fun sysAllocStringRaw(value: String?): NativePointer

    fun sysFreeStringRaw(value: NativePointer)

    fun readAndFreeBstrRaw(value: NativePointer): String

    fun coCreateFreeThreadedMarshalerRaw(outer: NativePointer = NativeInterop.nullPointer): NativePointerResult

    fun windowsCreateStringRaw(utf16Chars: NativePointer, length: Int, outHandle: NativePointer): Int

    fun windowsCreateStringReferenceRaw(
        utf16Chars: NativePointer,
        length: Int,
        header: NativePointer,
        outHandle: NativePointer,
    ): Int

    fun windowsDeleteStringRaw(handle: NativePointer)

    fun windowsGetStringRawBufferRaw(handle: NativePointer, lengthOut: NativePointer): NativePointer

    fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer

    fun loadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer

    fun tryGetProcAddressRaw(moduleHandle: NativePointer, procedureName: String): NativePointer

    fun getProcAddressRaw(moduleHandle: NativePointer, procedureName: String): NativePointer

    fun freeLibraryRaw(moduleHandle: NativePointer): Boolean

    fun mddBootstrapInitialize2Raw(
        initializeProc: NativePointer,
        majorMinorVersion: Int,
        versionTag: String,
        minVersion: Long,
    ): Int

    fun mddBootstrapShutdownRaw(shutdownProc: NativePointer)

    fun tryFormatMessageRaw(hResultValue: Int): String?

    fun lastErrorAsHResultRaw(): Int

    fun checkSucceededRaw(result: Int)

    fun resolveModulePathRaw(fileName: String): String
}
