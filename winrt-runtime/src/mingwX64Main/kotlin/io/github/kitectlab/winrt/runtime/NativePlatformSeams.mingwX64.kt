package io.github.kitectlab.winrt.runtime

actual class NativePointer

actual class NativeScope : AutoCloseable {
    actual override fun close() {}
}

actual class NativeCallbackHandle : AutoCloseable {
    actual val pointer: NativePointer = NativePointer()

    actual override fun close() {}
}

actual object NativeInterop {
    actual val nullPointer: NativePointer = NativePointer()

    actual val hStringHeaderSizeBytes: Long = TODO()

    actual fun confinedScope(): NativeScope = NativeScope()

    actual fun sharedScope(): NativeScope = NativeScope()

    actual fun isNull(pointer: NativePointer): Boolean = pointer === nullPointer

    actual fun samePointer(first: NativePointer, second: NativePointer): Boolean = first === second

    actual fun allocatePointerSlot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateInt8Slot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateInt32Slot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateInt64Slot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateDoubleSlot(scope: NativeScope): NativePointer = TODO()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long): NativePointer = TODO()

    actual fun allocateBytes(scope: NativeScope, sizeBytes: Long, alignmentBytes: Long): NativePointer = TODO()

    actual fun allocatePointerArray(scope: NativeScope, size: Int): NativePointer = TODO()

    actual fun allocateUtf16(scope: NativeScope, value: String, nulTerminated: Boolean): NativePointer = TODO()

    actual fun slice(pointer: NativePointer, offsetBytes: Long, sizeBytes: Long): NativePointer = TODO()

    actual fun readPointer(slot: NativePointer): NativePointer = TODO()

    actual fun readPointerAt(array: NativePointer, index: Int): NativePointer = TODO()

    actual fun readInt8(slot: NativePointer): Byte = TODO()

    actual fun readInt16(slot: NativePointer): Short = TODO()

    actual fun readInt32(slot: NativePointer): Int = TODO()

    actual fun readInt64(slot: NativePointer): Long = TODO()

    actual fun readDouble(slot: NativePointer): Double = TODO()

    actual fun readFloat(slot: NativePointer): Float = TODO()

    actual fun readChar16(slot: NativePointer): Char = TODO()

    actual fun readUtf16(pointer: NativePointer, length: Int): String = TODO()

    actual fun readGuid(pointer: NativePointer): Guid = TODO()

    actual fun writePointer(slot: NativePointer, value: NativePointer): Unit = TODO()

    actual fun writePointer(slot: NativePointer, offsetBytes: Long, value: NativePointer): Unit = TODO()

    actual fun writeInt8(slot: NativePointer, value: Byte): Unit = TODO()

    actual fun writeInt16(slot: NativePointer, value: Short): Unit = TODO()

    actual fun writeInt32(slot: NativePointer, value: Int): Unit = TODO()

    actual fun writeInt32(slot: NativePointer, offsetBytes: Long, value: Int): Unit = TODO()

    actual fun writeInt64(slot: NativePointer, value: Long): Unit = TODO()

    actual fun writeDouble(slot: NativePointer, value: Double): Unit = TODO()

    actual fun writeFloat(slot: NativePointer, value: Float): Unit = TODO()

    actual fun writeChar16(slot: NativePointer, value: Char): Unit = TODO()

    actual fun writeGuid(pointer: NativePointer, value: Guid): Unit = TODO()

    actual fun writeGuid(pointer: NativePointer, offsetBytes: Long, value: Guid): Unit = TODO()

    actual fun writePointerAt(array: NativePointer, index: Int, value: NativePointer): Unit = TODO()

    actual fun pointerKey(pointer: NativePointer): Long = TODO()

    actual fun invokeVtableInt32(
        instance: NativePointer,
        slot: Int,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int = TODO()

    actual fun invokeFunctionInt32(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Int = TODO()

    actual fun invokeFunctionVoid(
        function: NativePointer,
        descriptor: NativeFunctionDescriptor,
        vararg args: Any?,
    ): Unit = TODO()

    actual fun createCallback(
        descriptor: NativeFunctionDescriptor,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = TODO()

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation = TODO()

    actual fun zeroBytes(pointer: NativePointer, sizeBytes: Long): Unit = TODO()
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

    actual fun mddBootstrapInitialize2Raw(
        initializeProc: NativePointer,
        majorMinorVersion: Int,
        versionTag: String,
        minVersion: Long,
    ): Int = TODO()

    actual fun mddBootstrapShutdownRaw(shutdownProc: NativePointer): Unit = TODO()

    actual fun tryFormatMessageRaw(hResultValue: Int): String? = TODO()

    actual fun lastErrorAsHResultRaw(): Int = TODO()

    actual fun checkSucceededRaw(result: Int): Unit = TODO()

    actual fun resolveModulePathRaw(fileName: String): String = TODO()
}
