package io.github.kitectlab.winrt.runtime

actual class NativeScope : AutoCloseable {
    actual override fun close() {}
}

actual class NativeCallbackHandle : AutoCloseable {
    actual val pointer: RawAddress = RawAddress.Null

    actual override fun close() {}
}

actual object PlatformAbi {
    actual val nullPointer: RawAddress = RawAddress.Null
    actual val nullComPtr: RawComPtr = RawComPtr.Null

    actual val hStringHeaderSizeBytes: Long = TODO()

    actual fun confinedScope(): NativeScope = NativeScope()

    actual fun sharedScope(): NativeScope = NativeScope()

    actual fun isNull(pointer: RawAddress): Boolean = pointer.value == 0L

    actual fun isNull(pointer: RawComPtr): Boolean = pointer.value == 0L

    actual fun samePointer(first: RawAddress, second: RawAddress): Boolean = first.value == second.value

    actual fun samePointer(first: RawComPtr, second: RawComPtr): Boolean = first.value == second.value

    actual fun toRawComPtr(pointer: RawAddress): RawComPtr = pointer.asRawComPtr()

    actual fun fromRawComPtr(pointer: RawComPtr): RawAddress = pointer.asRawAddress()

    actual fun allocatePointerSlot(scope: NativeScope): RawAddress = TODO()

    actual fun allocateInt8Slot(scope: NativeScope): RawAddress = TODO()

    actual fun allocateInt32Slot(scope: NativeScope): RawAddress = TODO()

    actual fun allocateInt64Slot(scope: NativeScope): RawAddress = TODO()

    actual fun allocateDoubleSlot(scope: NativeScope): RawAddress = TODO()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): RawAddress = TODO()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): RawAddress = TODO()

    actual fun allocatePointerArray(scope: NativeScope, size: Int): RawAddress = TODO()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): RawAddress = TODO()

    actual fun slice(pointer: RawAddress, offsetBytes: Long, sizeBytes: Long): RawAddress = TODO()

    actual fun readPointer(slot: RawAddress): RawAddress = TODO()

    actual fun readPointerAt(array: RawAddress, index: Int): RawAddress = TODO()

    actual fun readInt8(slot: RawAddress): Byte = TODO()

    actual fun readInt16(slot: RawAddress): Short = TODO()

    actual fun readInt32(slot: RawAddress): Int = TODO()

    actual fun readInt64(slot: RawAddress): Long = TODO()

    actual fun readDouble(slot: RawAddress): Double = TODO()

    actual fun readFloat(slot: RawAddress): Float = TODO()

    actual fun readChar16(slot: RawAddress): Char = TODO()

    actual fun readUtf16(pointer: RawAddress, length: Int): String = TODO()

    actual fun readGuid(pointer: RawAddress): Guid = TODO()

    actual fun writePointer(slot: RawAddress, value: RawAddress): Unit = TODO()

    actual fun writePointer(slot: RawAddress, offsetBytes: Long, value: RawAddress): Unit = TODO()

    actual fun writeInt8(slot: RawAddress, value: Byte): Unit = TODO()

    actual fun writeInt16(slot: RawAddress, value: Short): Unit = TODO()

    actual fun writeInt32(slot: RawAddress, value: Int): Unit = TODO()

    actual fun writeInt32(slot: RawAddress, offsetBytes: Long, value: Int): Unit = TODO()

    actual fun writeInt64(slot: RawAddress, value: Long): Unit = TODO()

    actual fun writeDouble(slot: RawAddress, value: Double): Unit = TODO()

    actual fun writeFloat(slot: RawAddress, value: Float): Unit = TODO()

    actual fun writeChar16(slot: RawAddress, value: Char): Unit = TODO()

    actual fun writeGuid(pointer: RawAddress, value: Guid): Unit = TODO()

    actual fun writeGuid(pointer: RawAddress, offsetBytes: Long, value: Guid): Unit = TODO()

    actual fun writePointerAt(array: RawAddress, index: Int, value: RawAddress): Unit = TODO()

    actual fun pointerKey(pointer: RawAddress): Long = pointer.value

    actual fun pointerKey(pointer: RawComPtr): Long = pointer.value

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation = TODO()

    actual fun zeroBytes(pointer: RawAddress, sizeBytes: Long): Unit = TODO()
}

actual object WinRtPlatformApi {
    actual fun roGetActivationFactoryRaw(runtimeClassId: RawAddress, interfaceId: Guid): NativePointerResult = TODO()

    actual fun queryInterfaceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult = TODO()

    actual fun addRefRaw(unknown: RawAddress): UInt = TODO()

    actual fun releaseRaw(unknown: RawAddress): UInt = TODO()

    actual fun dllGetActivationFactoryRaw(
        getActivationFactoryProc: RawAddress,
        runtimeClassId: RawAddress,
    ): NativePointerResult = TODO()

    actual fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int): NativePointerResult = TODO()

    actual fun coInitializeExRaw(apartmentType: ApartmentType): Int = TODO()

    actual fun coUninitializeRaw(): Unit = TODO()

    actual fun roInitializeRaw(apartmentType: ApartmentType): Int = TODO()

    actual fun roUninitializeRaw(): Unit = TODO()

    actual fun coIncrementMtaUsageRaw(): NativePointerResult = TODO()

    actual fun coDecrementMtaUsageRaw(cookie: RawAddress): Int = TODO()

    actual fun roGetAgileReferenceRaw(unknown: RawAddress, interfaceId: Guid): NativePointerResult = TODO()

    actual fun coGetContextTokenRaw(): NativePointerResult = TODO()

    actual fun coGetObjectContextRaw(interfaceId: Guid): NativePointerResult = TODO()

    actual fun setErrorInfoRaw(errorInfo: RawAddress): Int = TODO()

    actual fun setRestrictedErrorInfoRaw(errorInfo: RawAddress): Int? = TODO()

    actual fun borrowRestrictedErrorInfoRaw(): RawAddress? = TODO()

    actual fun reportUnhandledErrorRaw(errorInfo: RawAddress): Int? = TODO()

    actual fun sysAllocStringRaw(value: String?): RawAddress = TODO()

    actual fun sysFreeStringRaw(value: RawAddress): Unit = TODO()

    actual fun readAndFreeBstrRaw(value: RawAddress): String = TODO()

    actual fun coCreateFreeThreadedMarshalerRaw(outer: RawAddress): NativePointerResult = TODO()

    actual fun windowsCreateStringRaw(utf16Chars: RawAddress, length: Int, outHandle: RawAddress): Int = TODO()

    actual fun windowsCreateStringReferenceRaw(
        utf16Chars: RawAddress,
        length: Int,
        header: RawAddress,
        outHandle: RawAddress,
    ): Int = TODO()

    actual fun windowsDeleteStringRaw(handle: RawAddress): Unit = TODO()

    actual fun windowsGetStringRawBufferRaw(handle: RawAddress, lengthOut: RawAddress): RawAddress = TODO()

    actual fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress = TODO()

    actual fun loadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress = TODO()

    actual fun tryGetProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress = TODO()

    actual fun getProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress = TODO()

    actual fun freeLibraryRaw(moduleHandle: RawAddress): Boolean = TODO()

    actual fun tryFormatMessageRaw(hResultValue: Int): String? = TODO()

    actual fun lastErrorAsHResultRaw(): Int = TODO()

    actual fun checkSucceededRaw(result: Int): Unit = TODO()

    actual fun resolveModulePathRaw(fileName: String): String = TODO()
}
