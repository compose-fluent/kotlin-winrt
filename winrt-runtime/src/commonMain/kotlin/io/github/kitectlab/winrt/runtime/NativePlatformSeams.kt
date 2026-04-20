package io.github.kitectlab.winrt.runtime

expect class NativePointer

expect class NativeScope : AutoCloseable {
    override fun close()
}

expect object NativeInterop {
    val nullPointer: NativePointer

    fun confinedScope(): NativeScope

    fun isNull(pointer: NativePointer): Boolean
}

expect object WinRtPlatformApi {
    fun roGetActivationFactoryRaw(runtimeClassId: NativePointer, interfaceId: Guid): NativePointerResult

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
