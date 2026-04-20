package io.github.kitectlab.winrt.runtime

expect class NativePointer

expect class NativeScope : AutoCloseable {
    override fun close()
}

expect object NativeInterop {
    val nullPointer: NativePointer

    val hStringHeaderSizeBytes: Long

    fun confinedScope(): NativeScope

    fun isNull(pointer: NativePointer): Boolean

    fun samePointer(first: NativePointer, second: NativePointer): Boolean

    fun allocatePointerSlot(scope: NativeScope): NativePointer

    fun allocateInt8Slot(scope: NativeScope): NativePointer

    fun allocateInt32Slot(scope: NativeScope): NativePointer

    fun allocateDoubleSlot(scope: NativeScope): NativePointer

    fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer

    fun allocatePointerArray(scope: NativeScope, size: Int): NativePointer

    fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean = false): NativePointer

    fun readPointer(slot: NativePointer): NativePointer

    fun readPointerAt(array: NativePointer, index: Int): NativePointer

    fun readInt8(slot: NativePointer): Byte

    fun readInt32(slot: NativePointer): Int

    fun readDouble(slot: NativePointer): Double

    fun readUtf16(pointer: NativePointer, length: Int): String

    fun writePointerAt(array: NativePointer, index: Int, value: NativePointer)
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

    fun tryFormatMessageRaw(hResultValue: Int): String?

    fun lastErrorAsHResultRaw(): Int

    fun checkSucceededRaw(result: Int)

    fun resolveModulePathRaw(fileName: String): String
}
