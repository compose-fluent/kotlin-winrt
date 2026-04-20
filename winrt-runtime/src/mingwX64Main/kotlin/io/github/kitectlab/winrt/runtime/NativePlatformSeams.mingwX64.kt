package io.github.kitectlab.winrt.runtime

actual class NativePointer

actual class NativeScope : AutoCloseable {
    actual override fun close() {}
}

actual object NativeInterop {
    actual val nullPointer: NativePointer = NativePointer()

    actual val hStringHeaderSizeBytes: Long = TODO()

    actual fun confinedScope(): NativeScope = NativeScope()

    actual fun isNull(pointer: NativePointer): Boolean = pointer === nullPointer

    actual fun samePointer(first: NativePointer, second: NativePointer): Boolean = first === second

    actual fun allocatePointerSlot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateInt32Slot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer = TODO()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): NativePointer = TODO()

    actual fun readPointer(slot: NativePointer): NativePointer = TODO()

    actual fun readInt32(slot: NativePointer): Int = TODO()

    actual fun readUtf16(pointer: NativePointer, length: Int): String = TODO()
}

actual object WinRtPlatformApi {
    actual fun roGetActivationFactoryRaw(runtimeClassId: NativePointer, interfaceId: Guid): NativePointerResult = TODO()

    actual fun queryInterfaceRaw(unknown: NativePointer, interfaceId: Guid): NativePointerResult = TODO()

    actual fun addRefRaw(unknown: NativePointer): UInt = TODO()

    actual fun releaseRaw(unknown: NativePointer): UInt = TODO()

    actual fun dllGetActivationFactoryRaw(
        getActivationFactoryProc: NativePointer,
        runtimeClassId: NativePointer,
    ): NativePointerResult = TODO()

    actual fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int): NativePointerResult = TODO()

    actual fun coInitializeExRaw(apartmentType: ApartmentType): Int = TODO()

    actual fun coUninitializeRaw(): Unit = TODO()

    actual fun roInitializeRaw(apartmentType: ApartmentType): Int = TODO()

    actual fun roUninitializeRaw(): Unit = TODO()

    actual fun coIncrementMtaUsageRaw(): NativePointerResult = TODO()

    actual fun coDecrementMtaUsageRaw(cookie: NativePointer): Int = TODO()

    actual fun roGetAgileReferenceRaw(unknown: NativePointer, interfaceId: Guid): NativePointerResult = TODO()

    actual fun coGetContextTokenRaw(): NativePointerResult = TODO()

    actual fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult = TODO()

    actual fun setErrorInfoRaw(errorInfo: NativePointer): Int = TODO()

    actual fun borrowRestrictedErrorInfoRaw(): NativePointer? = TODO()

    actual fun reportUnhandledErrorRaw(errorInfo: NativePointer): Int? = TODO()

    actual fun sysAllocStringRaw(value: String?): NativePointer = TODO()

    actual fun sysFreeStringRaw(value: NativePointer): Unit = TODO()

    actual fun readAndFreeBstrRaw(value: NativePointer): String = TODO()

    actual fun coCreateFreeThreadedMarshalerRaw(outer: NativePointer): NativePointerResult = TODO()

    actual fun windowsCreateStringRaw(utf16Chars: NativePointer, length: Int, outHandle: NativePointer): Int = TODO()

    actual fun windowsCreateStringReferenceRaw(
        utf16Chars: NativePointer,
        length: Int,
        header: NativePointer,
        outHandle: NativePointer,
    ): Int = TODO()

    actual fun windowsDeleteStringRaw(handle: NativePointer): Unit = TODO()

    actual fun windowsGetStringRawBufferRaw(handle: NativePointer, lengthOut: NativePointer): NativePointer = TODO()

    actual fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer = TODO()

    actual fun loadLibraryExWRaw(absolutePath: String, flags: Int): NativePointer = TODO()

    actual fun tryGetProcAddressRaw(moduleHandle: NativePointer, procedureName: String): NativePointer = TODO()

    actual fun getProcAddressRaw(moduleHandle: NativePointer, procedureName: String): NativePointer = TODO()

    actual fun freeLibraryRaw(moduleHandle: NativePointer): Boolean = TODO()

    actual fun tryFormatMessageRaw(hResultValue: Int): String? = TODO()

    actual fun lastErrorAsHResultRaw(): Int = TODO()

    actual fun checkSucceededRaw(result: Int): Unit = TODO()

    actual fun resolveModulePathRaw(fileName: String): String = TODO()
}
