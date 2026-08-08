@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlin.native.internal.InternalForKotlinNative::class,
)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
import platform.windows.FlsAlloc
import platform.windows.FlsSetValue
import platform.windows.FlushInstructionCache
import platform.windows.GetCurrentProcess
import platform.windows.MEM_COMMIT
import platform.windows.MEM_RELEASE
import platform.windows.MEM_RESERVE
import platform.windows.PAGE_EXECUTE_READ
import platform.windows.PAGE_READWRITE
import platform.windows.VirtualAlloc
import platform.windows.VirtualFree
import platform.windows.VirtualProtect
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.native.internal.GCUnsafeCall

actual object ComVtableInvoker {
    actual fun invokePointer(
        instance: RawComPtr,
        slot: Int,
    ): RawAddress =
        instance.vtableMethod<PointerResult0>(slot).invoke(instance.toOpaquePointer()).asRawAddress()

    actual fun invoke(
        instance: RawComPtr,
        slot: Int,
    ): Int =
        instance.vtableMethod<HResult0>(slot).invoke(instance.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
    ): Int =
        nativeInvokeHResultPointer1(instance, slot, arg0.toOpaquePointer())

    internal actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: NativeScalarScratchFrame,
    ): Int =
        nativeInvokeHResultPointer1(instance, slot, arg0.storage.reinterpret<COpaque>())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
    ): Int =
        nativeInvokeHResultPointer1(instance, slot, arg0.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
    ): Int =
        instance.vtableMethod<HResultInt32>(slot).invoke(instance.toOpaquePointer(), arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
    ): Int =
        instance.vtableMethod<HResultUInt32>(slot).invoke(instance.toOpaquePointer(), arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Long,
    ): Int =
        instance.vtableMethod<HResultInt64>(slot).invoke(instance.toOpaquePointer(), arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int =
        nativeInvokeHResultPointer2(instance, slot, arg0.toOpaquePointer(), arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
        arg1: RawAddress,
    ): Int =
        nativeInvokeHResultPointer2(instance, slot, arg0.toOpaquePointer(), arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
    ): Int =
        nativeInvokeHResultInt32Pointer2(instance, slot, arg0, arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawComPtr,
        arg2: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.value, arg2.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
    ): Int =
        nativeInvokeHResultUInt32Pointer2(instance, slot, arg0, arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.toLong())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.value, arg2.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: Int,
        arg2: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.toLong(), arg2.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.value, arg2.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.toLong(), arg2.value, arg3.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.toLong(), arg2.value, arg3.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: UInt,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.toLong(), arg1.toLong(), arg2.value, arg3.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.toLong(), arg3.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.value, arg3.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.value, arg3.toLong(), arg4.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
        arg4: Int,
        arg5: RawAddress,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.toLong(), arg3.value, arg4.toLong(), arg5.value)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
        arg5: Int,
    ): Int =
        invokeHResultWords(instance, slot, arg0.value, arg1.value, arg2.value, arg3.toLong(), arg4.value, arg5.toLong())

    internal actual fun invokeGeneric(
        instance: RawComPtr,
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int {
        require(signature.resultKind == ComAbiValueKind.Int32) {
            "ComVtableInvoker currently supports HRESULT/int32 COM methods only."
        }
        require(args.size == signature.explicitParameterKinds.size) {
            "Argument word count ${args.size} must match COM signature arity ${signature.explicitParameterKinds.size}."
        }
        signature.explicitParameterKinds.forEach { kind ->
            require(kind.supportsHResultWordDispatch()) {
                "Unsupported mingw COM generic argument kind for HRESULT word dispatch: $kind."
            }
        }
        return invokeHResultWordArray(instance, slot, args)
    }

    internal actual fun createComMethodCallback(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle =
        NativeCallbackRegistry.register(
            parameterKinds = listOf(ComAbiValueKind.Pointer) + signature.explicitParameterKinds,
            callback = callback,
        )

    internal actual fun createRawInt32Callback(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle =
        NativeCallbackRegistry.register(parameterKinds, callback)
}

private object NativeCallbackRegistry {
    private val lock = PlatformLock()
    private val callbacks = ConcurrentCacheMap<Int, RegisteredNativeCallback>()
    private var nextId = 1

    fun register(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle {
        require(parameterKinds.size in 1..maxCallbackWordCount) {
            "mingw COM callback ABI supports at most $maxCallbackWordCount raw words, got $parameterKinds."
        }
        val id = lock.withLock {
            nextId.also { nextId += 1 }
        }
        val trampoline = Win64ComCallbackTrampoline.allocate(id, parameterKinds.size)
        callbacks[id] = RegisteredNativeCallback(parameterKinds, callback)
        return NativeCallbackHandle(
            pointer = trampoline.pointer,
            onClose = {
                callbacks.remove(id)
                trampoline.close()
            },
        )
    }

    fun invokeRaw(
        id: Int,
        words: LongArray,
    ): Int {
        val registered = callbacks[id] ?: return KnownHResults.E_POINTER.value
        PlatformAbi.confinedScope().use { scope ->
            return runCatching {
                registered.callback(
                    registered.parameterKinds.mapIndexed { index, kind ->
                        abiWordToCallbackValue(scope, kind, words[index])
                    },
                )
            }.getOrElse { error ->
                platformSetErrorInfo(error)
                platformHResultFromThrowable(error).value
            }
        }
    }

    private fun abiWordToCallbackValue(scope: NativeScope, kind: ComAbiValueKind, word: Long): Any =
        when (kind) {
            ComAbiValueKind.Pointer -> RawAddress(word)
            ComAbiValueKind.Int8 -> word.toByte()
            ComAbiValueKind.Int16 -> word.toShort()
            ComAbiValueKind.Int32 -> word.toInt()
            ComAbiValueKind.Int64 -> word
            ComAbiValueKind.Float -> Float.fromBits(word.toInt())
            ComAbiValueKind.Double -> Double.fromBits(word)
            is ComAbiValueKind.Struct -> materializeCallbackStruct(scope, kind.layout, word)
        }

    private fun materializeCallbackStruct(scope: NativeScope, layout: NativeAbiLayout, word: Long): RawAddress {
        if (layout.byteSize > 8L) {
            return RawAddress(word)
        }
        val pointer = PlatformAbi.allocateBytes(scope, layout.byteSize, layout.byteAlignment)
        writeLittleEndianWord(pointer, layout.byteSize, word)
        return pointer
    }

    private fun writeLittleEndianWord(pointer: RawAddress, byteSize: Long, word: Long) {
        val bytes = pointer.toOpaquePointer()?.reinterpret<ByteVar>()
            ?: error("Cannot materialize a callback struct into a null native pointer.")
        repeat(byteSize.toInt()) { index ->
            bytes[index] = ((word ushr (index * 8)) and 0xFF).toByte()
        }
    }
}

private data class RegisteredNativeCallback(
    val parameterKinds: List<ComAbiValueKind>,
    val callback: (List<Any?>) -> Int,
)

private class Win64ComCallbackTrampoline private constructor(
    val pointer: RawAddress,
) : AutoCloseable {
    override fun close() {
        VirtualFree(pointer.toOpaquePointer(), 0u, MEM_RELEASE.toUInt())
    }

    companion object {
        fun allocate(
            callbackId: Int,
            wordCount: Int,
        ): Win64ComCallbackTrampoline {
            val code = buildCode(callbackId, wordCount)
            val memory = VirtualAlloc(
                null,
                code.size.toULong(),
                MEM_COMMIT.toUInt() or MEM_RESERVE.toUInt(),
                PAGE_READWRITE.toUInt(),
            ) ?: error("VirtualAlloc failed for mingw COM callback trampoline.")
            val bytes = memory.reinterpret<ByteVar>()
            code.forEachIndexed { index, value -> bytes[index] = value }
            protectExecutable(memory, code.size.toULong())
            return Win64ComCallbackTrampoline(memory.asRawAddress())
        }

        private fun protectExecutable(
            memory: COpaquePointer,
            size: ULong,
        ) {
            memScoped {
                val oldProtect = alloc<UIntVar>()
                val protected = VirtualProtect(memory, size, PAGE_EXECUTE_READ.toUInt(), oldProtect.ptr)
                if (protected == 0) {
                    VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                    error("VirtualProtect failed for mingw COM callback trampoline.")
                }
                if (FlushInstructionCache(GetCurrentProcess(), memory, size) == 0) {
                    VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                    error("FlushInstructionCache failed for mingw COM callback trampoline.")
                }
            }
        }

        private fun buildCode(
            callbackId: Int,
            wordCount: Int,
        ): ByteArray {
            // Windows x64 ABI only: ARM64 must provide its own trampoline in an ARM64 source set.
            val code = mutableListOf<Byte>()
            val stackSize = 0x68
            code.emit(0x48, 0x83, 0xEC, stackSize)

            if (wordCount > 3) {
                code.emit(0x4C, 0x89, 0x4C, 0x24, 0x20)
            } else {
                code.emitZeroStackWord(0x20)
            }
            for (wordIndex in 4 until maxCallbackWordCount) {
                val destination = 0x20 + (wordIndex - 3) * 8
                if (wordCount > wordIndex) {
                    val source = stackSize + 0x28 + (wordIndex - 4) * 8
                    code.emit(0x48, 0x8B, 0x84, 0x24)
                    code.emitInt32(source)
                    code.emit(0x48, 0x89, 0x44, 0x24, destination)
                } else {
                    code.emitZeroStackWord(destination)
                }
            }

            code.emit(0x4D, 0x89, 0xC1)
            code.emit(0x49, 0x89, 0xD0)
            code.emit(0x48, 0x89, 0xCA)
            code.emit(0xB9)
            code.emitInt32(callbackId)
            code.emit(0x48, 0xB8)
            code.emitInt64(universalCallbackAddress())
            code.emit(0xFF, 0xD0)
            code.emit(0x48, 0x83, 0xC4, stackSize)
            code.emit(0xC3)
            return code.toByteArray()
        }

        private fun universalCallbackAddress(): Long =
            staticCFunction(::invokeNativeCallbackRaw).rawValue.toLong()
    }
}

private const val maxCallbackWordCount = 7

@PublishedApi
internal inline fun nativeInvokeHResultPointer1(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
): Int {
    val objectMemory = instance.value.toCPointer<COpaquePointerVar>()
        ?: nativeNullComObjectPointer()
    val vtable = objectMemory.pointed.value ?: nativeNullComVtable()
    val function = vtable.reinterpret<COpaquePointerVar>()[slot]
        ?.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?) -> Int>>()
        ?: nativeNullComVtableSlot(slot)
    return function.invoke(objectMemory.reinterpret<COpaque>(), arg0)
}

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
): Int = nativeInvokeHResultPointer1(instance, slot, winRTDirectRawAddressToOpaquePointer(arg0))

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultAddressAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
    arg1: RawAddress,
): Int = nativeInvokeHResultPointer2(
    instance,
    slot,
    winRTDirectRawAddressToOpaquePointer(arg0),
    winRTDirectRawAddressToOpaquePointer(arg1),
)

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultUInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: RawAddress,
): Int = nativeInvokeHResultUInt32Pointer2(
    instance,
    slot,
    arg0,
    winRTDirectRawAddressToOpaquePointer(arg1),
)

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: RawAddress,
): Int = nativeInvokeHResultInt32Pointer2(
    instance,
    slot,
    arg0,
    winRTDirectRawAddressToOpaquePointer(arg1),
)

@PublishedApi
internal actual fun winRTCreateHResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = Win64ComRecipeThunkCache.getOrCreate(
    transport = Win64ComRecipeThunkTransport.HRESULT,
    inputCount = inputCount,
    floatingPointKinds = floatingPointKinds,
)

@PublishedApi
internal actual fun winRTCreatePackedScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = Win64ComRecipeThunkCache.getOrCreate(
    transport = Win64ComRecipeThunkTransport.PACKED_SCALAR_RESULT,
    inputCount = inputCount,
    floatingPointKinds = floatingPointKinds,
)

@PublishedApi
internal actual fun winRTCreateScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = Win64ComRecipeThunkCache.getOrCreate(
    transport = Win64ComRecipeThunkTransport.SCALAR_RESULT_RECORD,
    inputCount = inputCount,
    floatingPointKinds = floatingPointKinds,
)

@PublishedApi
internal actual fun winRTCreateWideScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = Win64ComRecipeThunkCache.getOrCreate(
    transport = Win64ComRecipeThunkTransport.WIDE_SCALAR_RESULT,
    inputCount = inputCount,
    floatingPointKinds = floatingPointKinds,
)

@PublishedApi
internal actual fun winRTScalarResultRecord(): RawAddress {
    val index = Win64ComScalarResultRecords.index
    val current = winRTFlsGetValue(index)
    return if (current != 0L) RawAddress(current) else createWinRTScalarResultRecord(index)
}

@GCUnsafeCall("FlsGetValue")
private external fun winRTFlsGetValue(index: UInt): Long

private fun createWinRTScalarResultRecord(index: UInt): RawAddress {
    val storage = nativeHeap.allocArray<LongVar>(2).reinterpret<COpaque>()
    if (FlsSetValue(index, storage) == 0) {
        nativeHeap.free(storage.rawValue)
        error("FlsSetValue failed for a mingw COM scalar-result record.")
    }
    return RawAddress(storage.rawValue.toLong())
}

@PublishedApi
internal actual inline fun winRTScalarResultHResult(record: RawAddress): Int =
    checkNotNull(record.value.toCPointer<IntVar>()).pointed.value

@PublishedApi
internal actual inline fun winRTScalarResultValue(record: RawAddress): RawAddress =
    RawAddress(record.value + Long.SIZE_BYTES)

@PublishedApi
internal inline fun nativeInvokeHResultUInt32Pointer2(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: COpaquePointer?,
): Int {
    val objectMemory = instance.value.toCPointer<COpaquePointerVar>()
        ?: nativeNullComObjectPointer()
    val vtable = objectMemory.pointed.value ?: nativeNullComVtable()
    val function = vtable.reinterpret<COpaquePointerVar>()[slot]
        ?.reinterpret<CFunction<(COpaquePointer?, UInt, COpaquePointer?) -> Int>>()
        ?: nativeNullComVtableSlot(slot)
    return function.invoke(objectMemory.reinterpret<COpaque>(), arg0, arg1)
}

@PublishedApi
internal inline fun nativeInvokeHResultInt32Pointer2(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: COpaquePointer?,
): Int {
    val objectMemory = instance.value.toCPointer<COpaquePointerVar>()
        ?: nativeNullComObjectPointer()
    val vtable = objectMemory.pointed.value ?: nativeNullComVtable()
    val function = vtable.reinterpret<COpaquePointerVar>()[slot]
        ?.reinterpret<CFunction<(COpaquePointer?, Int, COpaquePointer?) -> Int>>()
        ?: nativeNullComVtableSlot(slot)
    return function.invoke(objectMemory.reinterpret<COpaque>(), arg0, arg1)
}

@PublishedApi
internal inline fun nativeInvokeHResultPointer2(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: COpaquePointer?,
): Int {
    val objectMemory = instance.value.toCPointer<COpaquePointerVar>()
        ?: nativeNullComObjectPointer()
    val vtable = objectMemory.pointed.value ?: nativeNullComVtable()
    val function = vtable.reinterpret<COpaquePointerVar>()[slot]
        ?.reinterpret<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>>()
        ?: nativeNullComVtableSlot(slot)
    return function.invoke(objectMemory.reinterpret<COpaque>(), arg0, arg1)
}

@PublishedApi
internal fun nativeNullComObjectPointer(): Nothing =
    error("Cannot call a null COM object pointer.")

@PublishedApi
internal fun nativeNullComVtable(): Nothing =
    error("COM object has a null vtable.")

@PublishedApi
internal fun nativeNullComVtableSlot(slot: Int): Nothing =
    error("COM vtable slot $slot is null.")

private fun invokeNativeCallbackRaw(
    callbackId: Int,
    arg0: Long,
    arg1: Long,
    arg2: Long,
    arg3: Long,
    arg4: Long,
    arg5: Long,
    arg6: Long,
): Int =
    NativeCallbackRegistry.invokeRaw(callbackId, longArrayOf(arg0, arg1, arg2, arg3, arg4, arg5, arg6))

private fun MutableList<Byte>.emit(vararg values: Int) {
    values.forEach { value -> add(value.toByte()) }
}

private fun MutableList<Byte>.emitInt32(value: Int) {
    repeat(Int.SIZE_BYTES) { shift -> add((value ushr (shift * Byte.SIZE_BITS)).toByte()) }
}

private fun MutableList<Byte>.emitInt64(value: Long) {
    repeat(Long.SIZE_BYTES) { shift -> add((value ushr (shift * Byte.SIZE_BITS)).toByte()) }
}

private fun MutableList<Byte>.emitZeroStackWord(stackOffset: Int) {
    emit(0x48, 0x31, 0xC0)
    emit(0x48, 0x89, 0x44, 0x24, stackOffset)
}

private typealias PointerResult0 = CFunction<(COpaquePointer?) -> COpaquePointer?>
private typealias HResult0 = CFunction<(COpaquePointer?) -> Int>
private typealias HResultPointer1 = CFunction<(COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultPointer2 = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultInt32 = CFunction<(COpaquePointer?, Int) -> Int>
private typealias HResultUInt32 = CFunction<(COpaquePointer?, UInt) -> Int>
private typealias HResultInt64 = CFunction<(COpaquePointer?, Long) -> Int>
private typealias HResultUniversal =
    CFunction<(Long, Long, Long, Long, Long, Long, Long, Long) -> Int>

private fun invokeHResultWords(
    instance: RawComPtr,
    slot: Int,
    arg0: Long = 0L,
    arg1: Long = 0L,
    arg2: Long = 0L,
    arg3: Long = 0L,
    arg4: Long = 0L,
    arg5: Long = 0L,
): Int {
    val target = vtableEntry(instance, slot).rawValue.toLong()
    return Win64ComHResultDispatcher.invoke(
        target,
        instance.value,
        arg0,
        arg1,
        arg2,
        arg3,
        arg4,
        arg5,
    )
}

private fun invokeHResultWordArray(
    instance: RawComPtr,
    slot: Int,
    args: LongArray,
): Int {
    require(args.size <= maxHResultArgumentWordCount) {
        "mingw COM HRESULT ABI supports at most $maxHResultArgumentWordCount explicit words, got ${args.size}."
    }
    return invokeHResultWords(
        instance,
        slot,
        args.getOrElse(0) { 0L },
        args.getOrElse(1) { 0L },
        args.getOrElse(2) { 0L },
        args.getOrElse(3) { 0L },
        args.getOrElse(4) { 0L },
        args.getOrElse(5) { 0L },
    )
}

private const val maxHResultArgumentWordCount = 6

private fun ComAbiValueKind.supportsHResultWordDispatch(): Boolean =
    when (this) {
        ComAbiValueKind.Pointer,
        ComAbiValueKind.Int8,
        ComAbiValueKind.Int16,
        ComAbiValueKind.Int32,
        ComAbiValueKind.Int64,
        is ComAbiValueKind.Struct,
        -> true

        ComAbiValueKind.Float,
        ComAbiValueKind.Double,
        -> false
    }

private object Win64ComHResultDispatcher {
    private val pointer: RawAddress by lazy(::allocate)

    fun invoke(
        target: Long,
        arg0: Long,
        arg1: Long,
        arg2: Long,
        arg3: Long,
        arg4: Long,
        arg5: Long,
        arg6: Long,
    ): Int {
        val method = pointer.toOpaquePointer()?.reinterpret<HResultUniversal>()
            ?: error("mingw COM HRESULT dispatcher is null.")
        return method.invoke(target, arg0, arg1, arg2, arg3, arg4, arg5, arg6)
    }

    private fun allocate(): RawAddress {
        val code = buildCode()
        val memory = VirtualAlloc(
            null,
            code.size.toULong(),
            MEM_COMMIT.toUInt() or MEM_RESERVE.toUInt(),
            PAGE_READWRITE.toUInt(),
        ) ?: error("VirtualAlloc failed for mingw COM HRESULT dispatcher.")
        val bytes = memory.reinterpret<ByteVar>()
        code.forEachIndexed { index, value -> bytes[index] = value }
        memScoped {
            val oldProtect = alloc<UIntVar>()
            if (VirtualProtect(memory, code.size.toULong(), PAGE_EXECUTE_READ.toUInt(), oldProtect.ptr) == 0) {
                VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                error("VirtualProtect failed for mingw COM HRESULT dispatcher.")
            }
            if (FlushInstructionCache(GetCurrentProcess(), memory, code.size.toULong()) == 0) {
                VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                error("FlushInstructionCache failed for mingw COM HRESULT dispatcher.")
            }
        }
        return memory.asRawAddress()
    }

    private fun buildCode(): ByteArray {
        // Windows x64 ABI only: ARM64 must provide its own dispatcher in an ARM64 source set.
        val code = mutableListOf<Byte>()
        val stackSize = 0x48
        code.emit(0x48, 0x89, 0xC8)
        code.emit(0x48, 0x89, 0xD1)
        code.emit(0x4C, 0x89, 0xC2)
        code.emit(0x4D, 0x89, 0xC8)
        code.emit(0x48, 0x83, 0xEC, stackSize)
        code.emit(0x4C, 0x8B, 0x8C, 0x24)
        code.emitInt32(stackSize + 0x28)
        code.emitStackArgument(sourceOffset = stackSize + 0x30, destinationOffset = 0x20)
        code.emitStackArgument(sourceOffset = stackSize + 0x38, destinationOffset = 0x28)
        code.emitStackArgument(sourceOffset = stackSize + 0x40, destinationOffset = 0x30)
        code.emit(0xFF, 0xD0)
        code.emit(0x48, 0x83, 0xC4, stackSize)
        code.emit(0xC3)
        return code.toByteArray()
    }

    private fun MutableList<Byte>.emitStackArgument(
        sourceOffset: Int,
        destinationOffset: Int,
    ) {
        emit(0x4C, 0x8B, 0x94, 0x24)
        emitInt32(sourceOffset)
        emit(0x4C, 0x89, 0x54, 0x24, destinationOffset)
    }
}

private const val maxRecipeThunkInputCount = 20

private enum class Win64ComRecipeThunkTransport {
    HRESULT,
    PACKED_SCALAR_RESULT,
    SCALAR_RESULT_RECORD,
    WIDE_SCALAR_RESULT,
}

private enum class Win64ComRecipeFloatingPointKind(
    val bits: Int,
) {
    INTEGER_OR_ADDRESS(0),
    FLOAT32(1),
    FLOAT64(2),
    HSTRING(3),
}

private data class Win64ComRecipeThunkKey(
    val transport: Win64ComRecipeThunkTransport,
    val inputCount: Int,
    val floatingPointKinds: Long,
)

private data class Win64ComRecipeThunkEntry(
    val key: Win64ComRecipeThunkKey,
    val address: RawAddress,
)

@OptIn(ExperimentalAtomicApi::class)
private object Win64ComRecipeThunkCache {
    private val entries = AtomicReference<List<Win64ComRecipeThunkEntry>>(emptyList())

    fun getOrCreate(
        transport: Win64ComRecipeThunkTransport,
        inputCount: Int,
        floatingPointKinds: Long,
    ): RawAddress {
        val kinds = decodeFloatingPointKinds(inputCount, floatingPointKinds)
        val key = Win64ComRecipeThunkKey(transport, inputCount, floatingPointKinds)
        while (true) {
            val current = entries.load()
            current.firstOrNull { entry -> entry.key == key }?.let { entry -> return entry.address }

            val allocated = allocateExecutableCode(buildCode(transport, kinds))
            val updated = current + Win64ComRecipeThunkEntry(key, allocated)
            if (entries.compareAndSet(current, updated)) return allocated

            VirtualFree(allocated.toOpaquePointer(), 0u, MEM_RELEASE.toUInt())
        }
    }

    private fun decodeFloatingPointKinds(
        inputCount: Int,
        encoded: Long,
    ): List<Win64ComRecipeFloatingPointKind> {
        require(inputCount in 0..maxRecipeThunkInputCount) {
            "mingw COM recipe thunks support at most $maxRecipeThunkInputCount input carriers, got $inputCount."
        }
        val usedBitCount = inputCount * 2
        val usedMask = if (usedBitCount == 0) 0L else (1L shl usedBitCount) - 1L
        require(encoded and usedMask.inv() == 0L) {
            "mingw COM recipe thunk has floating-point bits outside its $inputCount input carriers."
        }
        return List(inputCount) { index ->
            val bits = ((encoded ushr (index * 2)) and 0x3L).toInt()
            Win64ComRecipeFloatingPointKind.entries.singleOrNull { kind -> kind.bits == bits }
                ?: error("mingw COM recipe thunk input $index has invalid floating-point kind $bits.")
        }
    }

    private fun buildCode(
        transport: Win64ComRecipeThunkTransport,
        kinds: List<Win64ComRecipeFloatingPointKind>,
    ): ByteArray = when (transport) {
        Win64ComRecipeThunkTransport.HRESULT -> buildHResultCode(kinds)
        Win64ComRecipeThunkTransport.PACKED_SCALAR_RESULT -> buildPackedScalarResultCode(kinds)
        Win64ComRecipeThunkTransport.SCALAR_RESULT_RECORD -> buildScalarResultRecordCode(kinds)
        Win64ComRecipeThunkTransport.WIDE_SCALAR_RESULT -> buildWideScalarResultCode(kinds)
    }

    private fun buildHResultCode(kinds: List<Win64ComRecipeFloatingPointKind>): ByteArray {
        if (Win64ComRecipeFloatingPointKind.HSTRING !in kinds) {
            val code = mutableListOf<Byte>()
            code.emitLoadVtableEntryFromInstanceAndSlot()
            kinds.forEachIndexed { index, kind ->
                code.emitTailRecipeArgument(index, kind)
            }
            code.emit(0xFF, 0xE0) // jmp rax
            return code.toByteArray()
        }

        val targetArgumentCount = kinds.size + 1
        val headerStart = outgoingStackEnd(targetArgumentCount)
        val frameSize = alignedCallFrameSize(headerStart + kinds.hStringCount() * recipeHStringHeaderSizeBytes)
        val code = mutableListOf<Byte>()
        code.emitSubRsp(frameSize)
        code.emitLoadVtableEntryFromInstanceAndSlot()
        code.emitRecipeHStringHeaders(kinds, headerStart, frameSize)
        kinds.forEachIndexed { index, kind ->
            code.emitCallRecipeArgument(index, kind, frameSize, headerStart, kinds)
        }
        code.emit(0xFF, 0xD0) // call rax
        code.emitAddRsp(frameSize)
        code.emit(0xC3) // ret
        return code.toByteArray()
    }

    private fun buildPackedScalarResultCode(kinds: List<Win64ComRecipeFloatingPointKind>): ByteArray {
        val targetArgumentCount = kinds.size + 2
        val headerStart = outgoingStackEnd(targetArgumentCount)
        val outputLocal = headerStart + kinds.hStringCount() * recipeHStringHeaderSizeBytes
        val frameSize = alignedCallFrameSize(outputLocal + Long.SIZE_BYTES)
        val code = mutableListOf<Byte>()

        code.emitSubRsp(frameSize)
        code.emitZeroQwordRsp(outputLocal)
        code.emitLoadVtableEntryFromInstanceAndSlot()
        code.emitRecipeHStringHeaders(kinds, headerStart, frameSize)
        kinds.forEachIndexed { index, kind ->
            code.emitCallRecipeArgument(index, kind, frameSize, headerStart, kinds)
        }
        code.emitOutputArgument(kinds.size + 1, outputLocal)
        code.emit(0xFF, 0xD0) // call rax
        code.emitLoadDwordRspToRegister(registerCodeR10, outputLocal)
        code.emit(0x48, 0xC1, 0xE0, Int.SIZE_BITS) // shl rax, 32
        code.emit(0x4C, 0x09, 0xD0) // or rax, r10
        code.emitAddRsp(frameSize)
        code.emit(0xC3) // ret
        return code.toByteArray()
    }

    private fun buildScalarResultRecordCode(kinds: List<Win64ComRecipeFloatingPointKind>): ByteArray {
        val targetArgumentCount = kinds.size + 2
        val recordLocal = outgoingStackEnd(targetArgumentCount)
        val outputLocal = recordLocal + Long.SIZE_BYTES
        val headerStart = outputLocal + Long.SIZE_BYTES
        val frameSize = alignedCallFrameSize(headerStart + kinds.hStringCount() * recipeHStringHeaderSizeBytes)
        val code = mutableListOf<Byte>()

        code.emitSubRsp(frameSize)
        // The thunk caller expands each HSTRING into address + length words, while the target
        // COM call still receives one header pointer. Locate the record in the caller layout
        // using the expanded entry-word count, not the target parameter count.
        code.emitLoadEntryArgumentToRegister(kinds.entryWordStart(kinds.size) + 2, frameSize, registerCodeR11)
        code.emitStoreRspFromRegister(recordLocal, registerCodeR11)
        code.emitZeroQwordRsp(outputLocal)
        code.emitLoadVtableEntryFromInstanceAndSlot()
        code.emitRecipeHStringHeaders(kinds, headerStart, frameSize)
        kinds.forEachIndexed { index, kind ->
            code.emitCallRecipeArgument(index, kind, frameSize, headerStart, kinds)
        }
        code.emitOutputArgument(kinds.size + 1, outputLocal)
        code.emit(0xFF, 0xD0) // call rax

        code.emitLoadRspToRegister(registerCodeR10, recordLocal)
        code.emitStoreDwordFromEax(registerCodeR10, 0)
        code.emitLoadRspToRegister(registerCodeR11, outputLocal)
        code.emitStoreQwordFromRegister(registerCodeR10, Long.SIZE_BYTES, registerCodeR11)
        code.emitMoveRegister(registerCodeRaX, registerCodeR10)
        code.emitAddRsp(frameSize)
        code.emit(0xC3) // ret
        return code.toByteArray()
    }

    private fun buildWideScalarResultCode(kinds: List<Win64ComRecipeFloatingPointKind>): ByteArray {
        val targetArgumentCount = kinds.size + 2
        val outputLocal = outgoingStackEnd(targetArgumentCount)
        val headerStart = outputLocal + Long.SIZE_BYTES
        val frameSize = alignedCallFrameSize(headerStart + kinds.hStringCount() * recipeHStringHeaderSizeBytes)
        val code = mutableListOf<Byte>()

        code.emitSubRsp(frameSize)
        code.emitZeroQwordRsp(outputLocal)
        code.emitLoadVtableEntryFromInstanceAndSlot()
        code.emitRecipeHStringHeaders(kinds, headerStart, frameSize)
        kinds.forEachIndexed { index, kind ->
            code.emitCallRecipeArgument(index, kind, frameSize, headerStart, kinds)
        }
        code.emitOutputArgument(kinds.size + 1, outputLocal)
        code.emit(0xFF, 0xD0) // call rax

        // Return { valueBits, zeroExtendedHResult } in XMM0. Kotlin/Native models
        // Vector128 as the native 128-bit vector return register on Windows x64.
        code.emitLoadQwordRspToXmm0(outputLocal)
        code.emit(0x66, 0x0F, 0x6E, 0xC8) // movd xmm1, eax
        code.emit(0x66, 0x0F, 0x6C, 0xC1) // punpcklqdq xmm0, xmm1
        code.emitAddRsp(frameSize)
        code.emit(0xC3) // ret
        return code.toByteArray()
    }

    private fun allocateExecutableCode(code: ByteArray): RawAddress {
        val memory = VirtualAlloc(
            null,
            code.size.toULong(),
            MEM_COMMIT.toUInt() or MEM_RESERVE.toUInt(),
            PAGE_READWRITE.toUInt(),
        ) ?: error("VirtualAlloc failed for a mingw COM recipe thunk.")
        val bytes = memory.reinterpret<ByteVar>()
        code.forEachIndexed { index, value -> bytes[index] = value }
        memScoped {
            val oldProtect = alloc<UIntVar>()
            if (VirtualProtect(memory, code.size.toULong(), PAGE_EXECUTE_READ.toUInt(), oldProtect.ptr) == 0) {
                VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                error("VirtualProtect failed for a mingw COM recipe thunk.")
            }
            if (FlushInstructionCache(GetCurrentProcess(), memory, code.size.toULong()) == 0) {
                VirtualFree(memory, 0u, MEM_RELEASE.toUInt())
                error("FlushInstructionCache failed for a mingw COM recipe thunk.")
            }
        }
        return memory.asRawAddress()
    }
}

private fun outgoingStackEnd(argumentCount: Int): Int =
    0x20 + (argumentCount - 4).coerceAtLeast(0) * Long.SIZE_BYTES

private const val recipeHStringHeaderSizeBytes = 24

private fun List<Win64ComRecipeFloatingPointKind>.hStringCount(): Int =
    count { kind -> kind == Win64ComRecipeFloatingPointKind.HSTRING }

private fun List<Win64ComRecipeFloatingPointKind>.entryWordStart(targetIndex: Int): Int =
    take(targetIndex).sumOf { kind ->
        if (kind == Win64ComRecipeFloatingPointKind.HSTRING) 2 else 1
    }

private fun alignedCallFrameSize(minimumSize: Int): Int {
    val alignedToWord = (minimumSize + Long.SIZE_BYTES - 1) and -Long.SIZE_BYTES
    return if (alignedToWord and 0xF == 0x8) alignedToWord else alignedToWord + Long.SIZE_BYTES
}

private fun releaseWinRTScalarResultRecord(storage: COpaquePointer?) {
    if (storage != null) nativeHeap.free(storage.rawValue)
}

/** Owns one native record per Windows fiber without a Kotlin managed-TLS lookup on hot calls. */
private object Win64ComScalarResultRecords {
    val index: UInt = FlsAlloc(staticCFunction(::releaseWinRTScalarResultRecord)).also { value ->
        check(value != UInt.MAX_VALUE) { "FlsAlloc failed for mingw COM scalar-result records." }
    }
}

private const val registerCodeRaX = 0
private const val registerCodeRcX = 1
private const val registerCodeRdX = 2
private const val registerCodeR8 = 8
private const val registerCodeR9 = 9
private const val registerCodeR10 = 10
private const val registerCodeR11 = 11

private fun MutableList<Byte>.emitTailRecipeArgument(
    index: Int,
    kind: Win64ComRecipeFloatingPointKind,
) {
    val targetPosition = index + 1
    if (targetPosition < 4) {
        val source = when (index) {
            0 -> registerCodeR8
            1 -> registerCodeR9
            else -> {
                emitLoadRspToRegister(registerCodeR10, 0x28 + (index - 2) * Long.SIZE_BYTES)
                registerCodeR10
            }
        }
        emitRecipeRegisterArgument(targetPosition, kind, source)
    } else {
        // The target has one fewer leading argument than the thunk (the slot). Shift its
        // stack carriers down in place before tail-jumping to preserve the caller's return path.
        emitLoadRspToRegister(registerCodeR10, 0x28 + (index - 2) * Long.SIZE_BYTES)
        emitStoreRspFromRegister(0x28 + (index - 3) * Long.SIZE_BYTES, registerCodeR10)
    }
}

private fun MutableList<Byte>.emitRecipeHStringHeaders(
    kinds: List<Win64ComRecipeFloatingPointKind>,
    headerStart: Int,
    frameSize: Int,
) {
    var headerIndex = 0
    kinds.forEachIndexed { index, kind ->
        if (kind == Win64ComRecipeFloatingPointKind.HSTRING) {
            val entryStart = kinds.entryWordStart(index)
            val pointerPosition = entryStart + 2
            val lengthPosition = pointerPosition + 1
            emitLoadEntryArgumentToRegister(pointerPosition, frameSize, registerCodeR10)
            emitLoadEntryArgumentToRegister(lengthPosition, frameSize, registerCodeR11)

            val headerOffset = headerStart + headerIndex * recipeHStringHeaderSizeBytes
            emitStoreRspFromRegister(headerOffset + 16, registerCodeR10)
            emitMoveRegister(registerCodeR10, registerCodeR11)
            emitShiftLeftImmediate(registerCodeR10, Int.SIZE_BITS)
            emitOrRegisterImmediate(registerCodeR10, 1)
            emitStoreRspFromRegister(headerOffset, registerCodeR10)
            emitZeroRegister(registerCodeR11)
            emitStoreRspFromRegister(headerOffset + 8, registerCodeR11)
            headerIndex += 1
        }
    }
}

private fun MutableList<Byte>.emitCallRecipeArgument(
    index: Int,
    kind: Win64ComRecipeFloatingPointKind,
    frameSize: Int,
    headerStart: Int,
    kinds: List<Win64ComRecipeFloatingPointKind>,
) {
    val targetPosition = index + 1
    if (kind == Win64ComRecipeFloatingPointKind.HSTRING) {
        val headerIndex = kinds.take(index).count { value ->
            value == Win64ComRecipeFloatingPointKind.HSTRING
        }
        val headerOffset = headerStart + headerIndex * recipeHStringHeaderSizeBytes
        emitLoadEffectiveAddressRsp(registerCodeR10, headerOffset)
        emitLoadDwordRspToRegister(registerCodeR11, headerOffset + 4)
        emitTestRegister(registerCodeR11)
        emitConditionalMoveZero(registerCodeR10, registerCodeR11)
        if (targetPosition < 4) {
            emitMoveRegister(targetPosition.gprRegisterCode(), registerCodeR10)
        } else {
            emitStoreRspFromRegister(0x20 + (targetPosition - 4) * Long.SIZE_BYTES, registerCodeR10)
        }
        return
    }

    val entryPosition = kinds.entryWordStart(index) + 2
    val source = when (entryPosition) {
        2 -> registerCodeR8
        3 -> registerCodeR9
        else -> {
            emitLoadRspToRegister(
                registerCodeR10,
                frameSize + 0x28 + (entryPosition - 4) * Long.SIZE_BYTES,
            )
            registerCodeR10
        }
    }
    if (targetPosition < 4) {
        emitRecipeRegisterArgument(targetPosition, kind, source)
    } else {
        emitStoreRspFromRegister(0x20 + (targetPosition - 4) * Long.SIZE_BYTES, source)
    }
}

private fun MutableList<Byte>.emitRecipeRegisterArgument(
    targetPosition: Int,
    kind: Win64ComRecipeFloatingPointKind,
    sourceRegister: Int,
) {
    when (kind) {
        Win64ComRecipeFloatingPointKind.INTEGER_OR_ADDRESS ->
            emitMoveRegister(targetPosition.gprRegisterCode(), sourceRegister)
        Win64ComRecipeFloatingPointKind.FLOAT32 ->
            emitMoveRegisterToXmm(targetPosition, sourceRegister, isDouble = false)
        Win64ComRecipeFloatingPointKind.FLOAT64 ->
            emitMoveRegisterToXmm(targetPosition, sourceRegister, isDouble = true)
        Win64ComRecipeFloatingPointKind.HSTRING ->
            error("HSTRING recipe inputs must be lowered through a stack header.")
    }
}

private fun MutableList<Byte>.emitOutputArgument(
    targetPosition: Int,
    outputLocal: Int,
) {
    if (targetPosition < 4) {
        emitLoadEffectiveAddressRsp(targetPosition.gprRegisterCode(), outputLocal)
    } else {
        emitLoadEffectiveAddressRsp(registerCodeR10, outputLocal)
        emitStoreRspFromRegister(0x20 + (targetPosition - 4) * Long.SIZE_BYTES, registerCodeR10)
    }
}

private fun MutableList<Byte>.emitLoadEntryArgumentToRegister(
    position: Int,
    frameSize: Int,
    destinationRegister: Int,
) {
    when (position) {
        0 -> emitMoveRegister(destinationRegister, registerCodeRcX)
        1 -> emitMoveRegister(destinationRegister, registerCodeRdX)
        2 -> emitMoveRegister(destinationRegister, registerCodeR8)
        3 -> emitMoveRegister(destinationRegister, registerCodeR9)
        else -> emitLoadRspToRegister(
            destinationRegister,
            frameSize + 0x28 + (position - 4) * Long.SIZE_BYTES,
        )
    }
}

private fun Int.gprRegisterCode(): Int = when (this) {
    0 -> registerCodeRcX
    1 -> registerCodeRdX
    2 -> registerCodeR8
    3 -> registerCodeR9
    else -> error("Unsupported Windows x64 integer register position $this.")
}

private fun MutableList<Byte>.emitMoveRegisterToXmm(
    xmmRegister: Int,
    sourceRegister: Int,
    isDouble: Boolean,
) {
    require(xmmRegister in 0..15) { "Unsupported Windows x64 XMM register $xmmRegister." }
    val rex = 0x40 or
        (if (isDouble) 0x08 else 0) or
        (if (sourceRegister >= 8) 0x01 else 0) or
        (if (xmmRegister >= 8) 0x04 else 0)
    emit(0x66, rex, 0x0F, 0x6E, 0xC0 or ((xmmRegister and 7) shl 3) or (sourceRegister and 7))
}

private fun MutableList<Byte>.emitZeroQwordRsp(offset: Int) {
    emit(0x45, 0x31, 0xD2) // xor r10d, r10d
    emitStoreRspFromRegister(offset, registerCodeR10)
}

private fun MutableList<Byte>.emitLoadVtableEntryFromInstanceAndSlot() {
    emit(0x48, 0x8B, 0x01) // mov rax, [rcx]
    emit(0x48, 0x8B, 0x04, 0xD0) // mov rax, [rax + rdx * 8]
}

private fun MutableList<Byte>.emitSubRsp(value: Int) {
    if (value <= Byte.MAX_VALUE) {
        emit(0x48, 0x83, 0xEC, value)
    } else {
        emit(0x48, 0x81, 0xEC)
        emitInt32(value)
    }
}

private fun MutableList<Byte>.emitAddRsp(value: Int) {
    if (value <= Byte.MAX_VALUE) {
        emit(0x48, 0x83, 0xC4, value)
    } else {
        emit(0x48, 0x81, 0xC4)
        emitInt32(value)
    }
}

private fun MutableList<Byte>.emitStoreRspFromRegister(offset: Int, register: Int) {
    val rex = 0x48 or if (register >= 8) 0x04 else 0
    if (offset in -128..127) {
        emit(rex, 0x89, 0x44 or ((register and 7) shl 3), 0x24, offset)
    } else {
        emit(rex, 0x89, 0x84 or ((register and 7) shl 3), 0x24)
        emitInt32(offset)
    }
}

private fun MutableList<Byte>.emitLoadRspToRegister(register: Int, offset: Int) {
    val rex = 0x48 or if (register >= 8) 0x04 else 0
    if (offset in -128..127) {
        emit(rex, 0x8B, 0x44 or ((register and 7) shl 3), 0x24, offset)
    } else {
        emit(rex, 0x8B, 0x84 or ((register and 7) shl 3), 0x24)
        emitInt32(offset)
    }
}

private fun MutableList<Byte>.emitLoadDwordRspToRegister(register: Int, offset: Int) {
    val rex = 0x40 or if (register >= 8) 0x04 else 0
    if (offset in -128..127) {
        emit(rex, 0x8B, 0x44 or ((register and 7) shl 3), 0x24, offset)
    } else {
        emit(rex, 0x8B, 0x84 or ((register and 7) shl 3), 0x24)
        emitInt32(offset)
    }
}

private fun MutableList<Byte>.emitLoadQwordRspToXmm0(offset: Int) {
    require(offset in 0..Byte.MAX_VALUE) { "Unsupported x64 XMM stack offset $offset." }
    emit(0xF3, 0x0F, 0x7E, 0x44, 0x24, offset)
}

private fun MutableList<Byte>.emitShiftLeftImmediate(register: Int, bits: Int) {
    require(bits in 0..255) { "Unsupported x64 shift amount $bits." }
    val rex = 0x48 or if (register >= 8) 0x01 else 0
    emit(rex, 0xC1, 0xE0 or (register and 7), bits)
}

private fun MutableList<Byte>.emitOrRegisterImmediate(register: Int, value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Unsupported x64 immediate $value." }
    val rex = 0x48 or if (register >= 8) 0x01 else 0
    emit(rex, 0x83, 0xC8 or (register and 7), value)
}

private fun MutableList<Byte>.emitZeroRegister(register: Int) {
    val rex = 0x40 or if (register >= 8) 0x05 else 0
    emit(rex, 0x31, 0xC0 or ((register and 7) shl 3) or (register and 7))
}

private fun MutableList<Byte>.emitTestRegister(register: Int) {
    val rex = 0x40 or if (register >= 8) 0x05 else 0
    emit(rex, 0x85, 0xC0 or ((register and 7) shl 3) or (register and 7))
}

private fun MutableList<Byte>.emitConditionalMoveZero(destination: Int, source: Int) {
    val rex = 0x48 or
        (if (destination >= 8) 0x04 else 0) or
        (if (source >= 8) 0x01 else 0)
    emit(rex, 0x0F, 0x44, 0xC0 or ((destination and 7) shl 3) or (source and 7))
}

private fun MutableList<Byte>.emitLoadEffectiveAddressRsp(register: Int, offset: Int) {
    val rex = 0x48 or if (register >= 8) 0x04 else 0
    if (offset in -128..127) {
        emit(rex, 0x8D, 0x44 or ((register and 7) shl 3), 0x24, offset)
    } else {
        emit(rex, 0x8D, 0x84 or ((register and 7) shl 3), 0x24)
        emitInt32(offset)
    }
}

private fun MutableList<Byte>.emitStoreDwordFromEax(baseRegister: Int, offset: Int) {
    require(baseRegister >= 8) { "The scalar-result record base must use an extended register." }
    val rex = 0x40 or 0x01
    if (offset == 0) {
        emit(rex, 0x89, 0x02 or ((baseRegister and 7) shl 0))
    } else {
        emit(rex, 0x89, 0x42 or ((baseRegister and 7) shl 0), offset)
    }
}

private fun MutableList<Byte>.emitStoreQwordFromRegister(baseRegister: Int, offset: Int, sourceRegister: Int) {
    require(baseRegister >= 8) { "The scalar-result record base must use an extended register." }
    val rex = 0x48 or 0x01 or if (sourceRegister >= 8) 0x04 else 0
    if (offset in -128..127) {
        emit(rex, 0x89, 0x40 or ((sourceRegister and 7) shl 3) or (baseRegister and 7), offset)
    } else {
        emit(rex, 0x89, 0x80 or ((sourceRegister and 7) shl 3) or (baseRegister and 7))
        emitInt32(offset)
    }
}

private fun MutableList<Byte>.emitMoveRegister(destination: Int, source: Int) {
    val rex = 0x48 or
        (if (source >= 8) 0x04 else 0) or
        (if (destination >= 8) 0x01 else 0)
    emit(rex, 0x89, 0xC0 or ((source and 7) shl 3) or (destination and 7))
}

private inline fun <reified T : CPointed> RawComPtr.vtableMethod(
    slot: Int,
): CPointer<T> =
    vtableEntry(this, slot).reinterpret<T>()

@PublishedApi
internal fun vtableEntry(instance: RawComPtr, slot: Int): COpaquePointer {
    val objectMemory = instance.asCPointer<COpaquePointerVar>()
    val vtable = objectMemory.pointed.value ?: error("COM object has a null vtable.")
    return vtable.reinterpret<COpaquePointerVar>()[slot] ?: error("COM vtable slot $slot is null.")
}

private fun RawComPtr.toOpaquePointer(): COpaquePointer? =
    if (value == 0L) null else value.toCPointer<COpaque>()

private inline fun <reified T : CPointed> RawComPtr.asCPointer(): CPointer<T> =
    value.toCPointer<T>() ?: error("Cannot dereference a null COM pointer.")

private fun RawAddress.toOpaquePointer(): COpaquePointer? =
    if (value == 0L) null else value.toCPointer<COpaque>()

@PublishedApi
internal fun winRTDirectRawAddressToOpaquePointer(value: RawAddress): COpaquePointer? =
    if (value.value == 0L) null else value.value.toCPointer<COpaque>()

private fun COpaquePointer?.asRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)
