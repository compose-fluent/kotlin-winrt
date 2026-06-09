@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.COpaquePointerVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.rawValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.value
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

actual object ComVtableInvoker {
    actual fun invokePointer(
        instance: RawComPtr,
        slot: Int,
    ): RawAddress = invokePointerResult(instance, slot)

    actual fun invoke(
        instance: RawComPtr,
        slot: Int,
    ): Int = invokeHResult(instance, slot)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
    ): Int = invokeHResult(instance, slot, arg0.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
    ): Int = invokeHResult(instance, slot, arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
    ): Int = invokeHResult(instance, slot, arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Long,
    ): Int = invokeHResult(instance, slot, arg0)

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0.toOpaquePointer(), arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
        arg1: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0.toOpaquePointer(), arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0, arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawComPtr,
        arg2: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0, arg1.toOpaquePointer(), arg2.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0, arg1.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0.toOpaquePointer(), arg1.toOpaquePointer(), arg2.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0, arg1.toOpaquePointer(), arg2.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: Int,
        arg2: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int = invokeHResult(instance, slot, arg0, arg1, arg2.toOpaquePointer(), arg3.toOpaquePointer())

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: UInt,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int = invokeHResult(
        instance,
        slot,
        arg0.toOpaquePointer(),
        arg1.toOpaquePointer(),
        arg2.toOpaquePointer(),
        arg3.toOpaquePointer(),
    )

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
        arg4: Int,
        arg5: RawAddress,
    ): Int = invokeHResult(
        instance,
        slot,
        arg0.toOpaquePointer(),
        arg1.toOpaquePointer(),
        arg2,
        arg3.toOpaquePointer(),
        arg4,
        arg5.toOpaquePointer(),
    )

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
        arg5: Int,
    ): Int = TODO()

    @Deprecated(
        message = "Legacy no-support generator fallback. Support-file generation must use descriptor intrinsics or direct overloads.",
        level = DeprecationLevel.ERROR,
    )
    actual fun invokeGenericArgs(
        instance: RawComPtr,
        slot: Int,
        vararg args: Any,
    ): Int = TODO()

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
        val kinds = signature.explicitParameterKinds
        return when {
            kinds.isEmpty() -> invoke(instance, slot)
            kinds.size == 1 && kinds[0] == ComAbiValueKind.Pointer ->
                invokeArgs(instance, slot, RawAddress(args[0]))
            kinds.size == 2 && kinds[0] == ComAbiValueKind.Pointer && kinds[1] == ComAbiValueKind.Pointer -> {
                invokeHResult(instance, slot, RawAddress(args[0]).toOpaquePointer(), RawAddress(args[1]).toOpaquePointer())
            }
            kinds.size == 2 && kinds[0] == ComAbiValueKind.Pointer && kinds[1] == ComAbiValueKind.Int32 -> {
                invokeHResult(instance, slot, RawAddress(args[0]).toOpaquePointer(), args[1].toInt())
            }
            kinds.size == 2 && kinds[0] == ComAbiValueKind.Int32 && kinds[1] == ComAbiValueKind.Pointer -> {
                invokeHResult(instance, slot, args[0].toInt(), RawAddress(args[1]).toOpaquePointer())
            }
            kinds.size == 2 && kinds[0] is ComAbiValueKind.Struct && kinds[1] == ComAbiValueKind.Pointer -> {
                invokeHResult(instance, slot, RawAddress(args[0]).toOpaquePointer(), RawAddress(args[1]).toOpaquePointer())
            }
            else -> error("Unsupported mingw COM generic signature: $kinds.")
        }
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
    private val callbacks = ConcurrentCacheMap<Int, RegisteredNativeCallback>()
    private var nextId = 1

    fun register(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle {
        require(parameterKinds.size in 1..maxCallbackWordCount) {
            "mingw COM callback ABI supports at most $maxCallbackWordCount raw words, got $parameterKinds."
        }
        val id = nextId++
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
        return runCatching {
            registered.callback(
                registered.parameterKinds.mapIndexed { index, kind ->
                    abiWordToCallbackValue(kind, words[index])
                },
            )
        }.getOrElse { error ->
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }
    }

    private fun abiWordToCallbackValue(kind: ComAbiValueKind, word: Long): Any =
        when (kind) {
            ComAbiValueKind.Pointer -> RawAddress(word)
            ComAbiValueKind.Int8 -> word.toByte()
            ComAbiValueKind.Int16 -> word.toShort()
            ComAbiValueKind.Int32 -> word.toInt()
            ComAbiValueKind.Int64 -> word
            ComAbiValueKind.Float -> Float.fromBits(word.toInt())
            ComAbiValueKind.Double -> Double.fromBits(word)
            is ComAbiValueKind.Struct -> RawAddress(word)
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
private typealias HResultInt1 = CFunction<(COpaquePointer?, Int) -> Int>
private typealias HResultUInt1 = CFunction<(COpaquePointer?, UInt) -> Int>
private typealias HResultLong1 = CFunction<(COpaquePointer?, Long) -> Int>
private typealias HResultPointer2 = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultPointerInt = CFunction<(COpaquePointer?, COpaquePointer?, Int) -> Int>
private typealias HResultIntPointer = CFunction<(COpaquePointer?, Int, COpaquePointer?) -> Int>
private typealias HResultUIntPointer = CFunction<(COpaquePointer?, UInt, COpaquePointer?) -> Int>
private typealias HResultPointer3 = CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultIntPointer2 = CFunction<(COpaquePointer?, Int, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultUIntIntPointer2 =
    CFunction<(COpaquePointer?, UInt, Int, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultPointer4 =
    CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?) -> Int>
private typealias HResultPointer2IntPointerIntPointer =
    CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, Int, COpaquePointer?, Int, COpaquePointer?) -> Int>

private fun invokePointerResult(
    instance: RawComPtr,
    slot: Int,
): RawAddress {
    val method = vtableEntry(instance, slot).reinterpret<PointerResult0>()
    return method.invoke(instance.toOpaquePointer()).asRawAddress()
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResult0>()
    return method.invoke(instance.toOpaquePointer())
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointer1>()
    return method.invoke(instance.toOpaquePointer(), arg0)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultInt1>()
    return method.invoke(instance.toOpaquePointer(), arg0)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultUInt1>()
    return method.invoke(instance.toOpaquePointer(), arg0)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: Long,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultLong1>()
    return method.invoke(instance.toOpaquePointer(), arg0)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointer2>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: Int,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointerInt>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultIntPointer>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultUIntPointer>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: COpaquePointer?,
    arg2: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointer3>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1, arg2)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: COpaquePointer?,
    arg2: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultIntPointer2>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1, arg2)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: Int,
    arg2: COpaquePointer?,
    arg3: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultUIntIntPointer2>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1, arg2, arg3)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: COpaquePointer?,
    arg2: COpaquePointer?,
    arg3: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointer4>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1, arg2, arg3)
}

private fun invokeHResult(
    instance: RawComPtr,
    slot: Int,
    arg0: COpaquePointer?,
    arg1: COpaquePointer?,
    arg2: Int,
    arg3: COpaquePointer?,
    arg4: Int,
    arg5: COpaquePointer?,
): Int {
    val method = vtableEntry(instance, slot).reinterpret<HResultPointer2IntPointerIntPointer>()
    return method.invoke(instance.toOpaquePointer(), arg0, arg1, arg2, arg3, arg4, arg5)
}

private fun vtableEntry(instance: RawComPtr, slot: Int): COpaquePointer {
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

private fun COpaquePointer?.asRawAddress(): RawAddress =
    RawAddress(this?.rawValue?.toLong() ?: 0L)
