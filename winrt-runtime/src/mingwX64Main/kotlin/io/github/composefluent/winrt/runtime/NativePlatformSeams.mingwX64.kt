@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

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
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.windows.COINIT_APARTMENTTHREADED
import platform.windows.COINIT_MULTITHREADED
import platform.windows.GetLastError
import platform.windows.GetProcAddress
import platform.windows.LoadLibraryA

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

actual class NativeCallbackHandle internal constructor(
    actual val pointer: RawAddress,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private var closed: Boolean = false

    actual override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
}

actual object PlatformAbi {
    actual val nullPointer: RawAddress = RawAddress.Null
    actual val nullComPtr: RawComPtr = RawComPtr.Null

    actual val hStringHeaderSizeBytes: Long = 24L

    actual fun confinedScope(): NativeScope = NativeScope(ownsAllocations = true)

    actual fun sharedScope(): NativeScope = NativeScope(ownsAllocations = false)

    actual fun isNull(pointer: RawAddress): Boolean = pointer.value == 0L

    actual fun isNull(pointer: RawComPtr): Boolean = pointer.value == 0L

    actual fun samePointer(first: RawAddress, second: RawAddress): Boolean = first.value == second.value

    actual fun samePointer(first: RawComPtr, second: RawComPtr): Boolean = first.value == second.value

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
        value.forEachIndexed { index, char ->
            chars[index] = char.code.toUShort()
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
        val chars = pointer.asCPointer<UShortVar>()
        return CharArray(length) { index -> chars[index].toInt().toChar() }.concatToString()
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
        pointer.writeBytes(value.toLittleEndianBytes())
    }

    actual fun writeGuid(pointer: RawAddress, offsetBytes: Long, value: Guid) {
        writeGuid(slice(pointer, offsetBytes, Guid.BYTE_SIZE.toLong()), value)
    }

    actual fun writePointerAt(array: RawAddress, index: Int, value: RawAddress) {
        array.asCPointer<COpaquePointerVar>()[index] = value.toOpaquePointer()
    }

    actual fun pointerKey(pointer: RawAddress): Long = pointer.value

    actual fun pointerKey(pointer: RawComPtr): Long = pointer.value

    actual fun allocateBytesOwned(sizeBytes: Long, alignmentBytes: Long): OwnedNativeAllocation {
        val pointer = nativeHeap.allocArray<ByteVar>(sizeBytes.toInt()).reinterpret<COpaque>()
        val raw = pointer.asRawAddress()
        zeroBytes(raw, sizeBytes)
        return OwnedNativeAllocation(pointer = raw, onClose = { nativeHeap.free(pointer.rawValue) })
    }

    actual fun zeroBytes(pointer: RawAddress, sizeBytes: Long) {
        val bytes = pointer.asCPointer<ByteVar>()
        repeat(sizeBytes.toInt()) { index ->
            bytes[index] = 0
        }
    }
}

private fun COpaquePointer?.asRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)

private fun RawAddress.toOpaquePointer(): COpaquePointer? =
    if (value == 0L) {
        null
    } else {
        value.toCPointer<COpaque>()
    }

private inline fun <reified T : CPointed> RawAddress.asCPointer(): CPointer<T> =
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

actual object WinRtPlatformApi {
    private const val roInitSingleThreaded = 0
    private const val roInitMultithreaded = 1

    private val combaseModule by lazy {
        LoadLibraryA("combase.dll")
    }

    private val ole32Module by lazy {
        LoadLibraryA("ole32.dll")
    }

    private val coInitializeExProc: CPointer<CFunction<(COpaquePointer?, UInt) -> Int>>? by lazy {
        GetProcAddress(ole32Module, "CoInitializeEx")?.reinterpret()
    }

    private val coUninitializeProc: CPointer<CFunction<() -> Unit>>? by lazy {
        GetProcAddress(ole32Module, "CoUninitialize")?.reinterpret()
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

    private val coTaskMemAllocProc: CPointer<CFunction<(ULong) -> COpaquePointer?>>? by lazy {
        GetProcAddress(ole32Module, "CoTaskMemAlloc")?.reinterpret()
    }

    private val coTaskMemFreeProc: CPointer<CFunction<(COpaquePointer?) -> Unit>>? by lazy {
        GetProcAddress(ole32Module, "CoTaskMemFree")?.reinterpret()
    }

    private val windowsCreateStringProc: CPointer<CFunction<(COpaquePointer?, UInt, COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(combaseModule, "WindowsCreateString")?.reinterpret()
    }

    private val windowsCreateStringReferenceProc:
        CPointer<CFunction<(COpaquePointer?, UInt, COpaquePointer?, COpaquePointer?) -> Int>>? by lazy {
            GetProcAddress(combaseModule, "WindowsCreateStringReference")?.reinterpret()
        }

    private val windowsDeleteStringProc: CPointer<CFunction<(COpaquePointer?) -> Int>>? by lazy {
        GetProcAddress(combaseModule, "WindowsDeleteString")?.reinterpret()
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
        PlatformAbi.confinedScope().use { scope ->
            val interfaceIdPointer = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writeGuid(interfaceIdPointer, interfaceId)
            val hResult = ComVtableInvoker.invokeArgs(
                instance = unknown.asRawComPtr(),
                slot = IUnknownVftblSlots.QueryInterface,
                arg0 = interfaceIdPointer,
                arg1 = resultOut,
            )
            NativePointerResult(hResult, PlatformAbi.readPointer(resultOut))
        }

    actual fun addRefRaw(unknown: RawAddress): UInt =
        ComVtableInvoker.invoke(unknown.asRawComPtr(), IUnknownVftblSlots.AddRef).toUInt()

    actual fun releaseRaw(unknown: RawAddress): UInt =
        ComVtableInvoker.invoke(unknown.asRawComPtr(), IUnknownVftblSlots.Release).toUInt()

    actual fun dllGetActivationFactoryRaw(
        getActivationFactoryProc: RawAddress,
        runtimeClassId: RawAddress,
    ): NativePointerResult = TODO()

    actual fun coCreateInstanceRaw(classId: Guid, interfaceId: Guid, classContext: Int): NativePointerResult = TODO()

    actual fun coInitializeExRaw(apartmentType: ApartmentType): Int {
        val flags = when (apartmentType) {
            ApartmentType.SingleThreaded -> COINIT_APARTMENTTHREADED
            ApartmentType.MultiThreaded -> COINIT_MULTITHREADED
        }
        return coInitializeExProc?.invoke(null, flags) ?: KnownHResults.E_NOTIMPL.value
    }

    actual fun coUninitializeRaw() {
        coUninitializeProc?.invoke()
    }

    actual fun roInitializeRaw(apartmentType: ApartmentType): Int {
        val initType = when (apartmentType) {
            ApartmentType.SingleThreaded -> roInitSingleThreaded
            ApartmentType.MultiThreaded -> roInitMultithreaded
        }
        return roInitializeProc?.invoke(initType) ?: KnownHResults.E_NOTIMPL.value
    }

    actual fun roUninitializeRaw() {
        roUninitializeProc?.invoke()
    }

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
        windowsDeleteStringProc?.invoke(handle.toOpaquePointer())
    }

    actual fun windowsGetStringRawBufferRaw(handle: RawAddress, lengthOut: RawAddress): RawAddress =
        windowsGetStringRawBufferProc?.invoke(handle.toOpaquePointer(), lengthOut.toOpaquePointer()).asRawAddress()

    actual fun tryLoadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress = TODO()

    actual fun loadLibraryExWRaw(absolutePath: String, flags: Int): RawAddress = TODO()

    actual fun tryGetProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress = TODO()

    actual fun getProcAddressRaw(moduleHandle: RawAddress, procedureName: String): RawAddress = TODO()

    actual fun freeLibraryRaw(moduleHandle: RawAddress): Boolean = TODO()

    actual fun tryGetModuleHandleExFromAddressRaw(address: RawAddress): RawAddress = RawAddress.Null

    actual fun isReadableMemoryRaw(address: RawAddress, sizeBytes: Long): Boolean = false

    actual fun tryFormatMessageRaw(hResultValue: Int): String? = null

    actual fun lastErrorAsHResultRaw(): Int =
        ExceptionHelpers.hResultFromWin32(GetLastError().toInt()).value

    actual fun checkSucceededRaw(result: Int) {
        val hResult = HResult(result)
        if (hResult.isFailure) {
            throw WinRtExceptionTranslator.exceptionFor(hResult)
        }
    }

    actual fun resolveModulePathRaw(fileName: String): String = TODO()
}
