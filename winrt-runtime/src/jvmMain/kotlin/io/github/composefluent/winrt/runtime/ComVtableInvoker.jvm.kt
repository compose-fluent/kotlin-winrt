package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
actual object ComVtableInvoker {
    private val linker = Linker.nativeLinker()
    private val lookup = MethodHandles.lookup()
    private val sharedArena = Arena.global()
    private val genericDowncallHandles = ConcurrentCacheMap<ComMethodSignature, MethodHandle>()
    private val callbackEntries = ConcurrentCacheMap<Long, RegisteredCallback>()
    private val nextCallbackId = AtomicLong(1)
    private val pointerHandle =
        linker.downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS))
    private val hResultHandle = WinRTJvmFfmDowncallHandles.hResultNoArgs
    private val hResultPtrHandle = WinRTJvmFfmDowncallHandles.hResultAddress
    private val hResultInt32Handle = WinRTJvmFfmDowncallHandles.hResultInt32
    private val hResultInt64Handle = WinRTJvmFfmDowncallHandles.hResultInt64
    private val hResultPtrPtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressAddress
    private val hResultInt32PtrHandle = WinRTJvmFfmDowncallHandles.hResultInt32Address
    private val hResultInt32Int32Handle = WinRTJvmFfmDowncallHandles.hResultInt32Int32
    private val hResultPtrPtrPtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressAddressAddress
    private val hResultInt32PtrPtrHandle = WinRTJvmFfmDowncallHandles.hResultInt32AddressAddress
    private val hResultPtrInt32PtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressInt32Address
    private val hResultInt32Int32PtrPtrHandle = WinRTJvmFfmDowncallHandles.hResultInt32Int32AddressAddress
    private val hResultPtrPtrPtrPtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressAddressAddressAddress
    private val hResultPtrPtrInt32PtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressAddressInt32Address
    private val hResultPtrPtrPtrInt32PtrHandle = WinRTJvmFfmDowncallHandles.hResultAddressAddressAddressInt32Address
    private val hResultPtrPtrInt32PtrInt32PtrHandle =
        WinRTJvmFfmDowncallHandles.hResultAddressAddressInt32AddressInt32Address
    private val hResultPtrPtrPtrInt32PtrInt32Handle =
        WinRTJvmFfmDowncallHandles.hResultAddressAddressAddressInt32AddressInt32

    actual fun invokePointer(
        instance: RawComPtr,
        slot: Int,
    ): RawAddress {
        val instanceSegment = asSegment(instance)
        return (pointerHandle.invoke(vtableEntry(instanceSegment, slot), instanceSegment) as MemorySegment)
            .asRawAddress()
    }

    actual fun invoke(
        instance: RawComPtr,
        slot: Int,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultHandle.invoke(vtableEntry(instanceSegment, slot), instanceSegment) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrHandle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, asSegment(arg0)) as Int
    }

    internal actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: NativeScalarScratchFrame,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrHandle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, arg0.segment) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrHandle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, asSegment(arg0)) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Handle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, arg0) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Handle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, arg0.toInt()) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Long,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt64Handle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, arg0) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
        arg1: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0,
            asSegment(arg1),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawComPtr,
        arg2: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0,
            asSegment(arg1),
            asSegment(arg2),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0.toInt(),
            asSegment(arg1),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Int32Handle.invoke(vtableEntry(instanceSegment, slot), instanceSegment, arg0, arg1) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            asSegment(arg2),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0,
            asSegment(arg1),
            asSegment(arg2),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: Int,
        arg2: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            arg1,
            asSegment(arg2),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0.toInt(),
            asSegment(arg1),
            asSegment(arg2),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Int32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0,
            arg1,
            asSegment(arg2),
            asSegment(arg3),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Int32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0.toInt(),
            arg1,
            asSegment(arg2),
            asSegment(arg3),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: UInt,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultInt32Int32PtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            arg0.toInt(),
            arg1.toInt(),
            asSegment(arg2),
            asSegment(arg3),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            arg2,
            asSegment(arg3),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrPtrPtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            asSegment(arg2),
            asSegment(arg3),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrPtrInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            asSegment(arg2),
            arg3,
            asSegment(arg4),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
        arg4: Int,
        arg5: RawAddress,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrInt32PtrInt32PtrHandle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            arg2,
            asSegment(arg3),
            arg4,
            asSegment(arg5),
        ) as Int
    }

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
        arg5: Int,
    ): Int {
        val instanceSegment = asSegment(instance)
        return hResultPtrPtrPtrInt32PtrInt32Handle.invoke(
            vtableEntry(instanceSegment, slot),
            instanceSegment,
            asSegment(arg0),
            asSegment(arg1),
            asSegment(arg2),
            arg3,
            asSegment(arg4),
            arg5,
        ) as Int
    }

    internal actual fun invokeGeneric(
        instance: RawComPtr,
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int = invokeCore(instance, slot, signature, args)

    internal actual fun createComMethodCallback(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle {
        val callbackKinds = listOf(ComAbiValueKind.Pointer) + signature.explicitParameterKinds
        return createCallback(
            key = CallbackSignature(signature.resultKind, callbackKinds),
            callback = callback,
        )
    }

    internal actual fun createRawWordComMethodCallback(
        signature: ComMethodSignature,
        callback: ComRawWordCallback,
    ): NativeCallbackHandle =
        createRawWordCallback(
            key = CallbackSignature(
                resultKind = signature.resultKind,
                parameterKinds = listOf(ComAbiValueKind.Pointer) + signature.explicitParameterKinds,
            ),
            callback = callback,
        )

    internal actual fun createRawInt32Callback(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle =
        createCallback(
            key = CallbackSignature(ComAbiValueKind.Int32, parameterKinds),
            callback = callback,
        )

    private fun invokeCore(
        instance: RawComPtr,
        slot: Int,
        signature: ComMethodSignature,
        words: LongArray,
    ): Int {
        require(signature.resultKind == ComAbiValueKind.Int32) {
            "ComVtableInvoker currently supports HRESULT/int32 COM methods only."
        }
        require(words.size == signature.explicitParameterKinds.size) {
            "Argument word count ${words.size} must match COM signature arity ${signature.explicitParameterKinds.size}."
        }

        val instanceSegment = asSegment(instance)
        val function = vtableEntry(instanceSegment, slot)
        val handle = genericDowncallHandles.computeIfAbsent(signature) {
            linker.downcallHandle(it.asFunctionDescriptorWithThis())
        }

        return when (words.size) {
            0 -> handle.invokeWithArguments(function, instanceSegment) as Int
            1 -> {
                val arg0 = toCarrier(signature.explicitParameterKinds[0], words[0])
                handle.invokeWithArguments(function, instanceSegment, arg0) as Int
            }

            2 -> {
                val arg0 = toCarrier(signature.explicitParameterKinds[0], words[0])
                val arg1 = toCarrier(signature.explicitParameterKinds[1], words[1])
                handle.invokeWithArguments(function, instanceSegment, arg0, arg1) as Int
            }

            3 -> {
                val arg0 = toCarrier(signature.explicitParameterKinds[0], words[0])
                val arg1 = toCarrier(signature.explicitParameterKinds[1], words[1])
                val arg2 = toCarrier(signature.explicitParameterKinds[2], words[2])
                handle.invokeWithArguments(function, instanceSegment, arg0, arg1, arg2) as Int
            }

            4 -> {
                val arg0 = toCarrier(signature.explicitParameterKinds[0], words[0])
                val arg1 = toCarrier(signature.explicitParameterKinds[1], words[1])
                val arg2 = toCarrier(signature.explicitParameterKinds[2], words[2])
                val arg3 = toCarrier(signature.explicitParameterKinds[3], words[3])
                handle.invokeWithArguments(function, instanceSegment, arg0, arg1, arg2, arg3) as Int
            }

            else -> {
                val convertedArgs =
                    buildList(words.size + 2) {
                        add(function)
                        add(instanceSegment)
                        words.indices.forEach { index ->
                            add(toCarrier(signature.explicitParameterKinds[index], words[index]))
                        }
                    }
                handle.invokeWithArguments(convertedArgs) as Int
            }
        }
    }

    private fun createCallback(
        key: CallbackSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle {
        require(key.resultKind == ComAbiValueKind.Int32) {
            "Only int32-return callbacks are supported."
        }

        val callbackId = nextCallbackId()
        callbackEntries[callbackId] = RegisteredCallback(key, callback)

        val baseHandle =
            lookup.findStatic(
                ComVtableInvoker::class.java,
                "invokeCallbackBridge",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Array<Any?>::class.java,
                ),
            )
        val boundHandle = MethodHandles.insertArguments(baseHandle, 0, callbackId)
        val collectedHandle = boundHandle.asCollector(Array<Any?>::class.java, key.parameterKinds.size)
        val exactHandle =
            collectedHandle.asType(
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    key.parameterKinds.map(::carrierClass),
                ),
            )
        val stub = linker.upcallStub(exactHandle, key.asFunctionDescriptor(), sharedArena)
        return NativeCallbackHandle(
            pointer = stub.asRawAddress(),
            onClose = { callbackEntries.remove(callbackId) },
        )
    }

    private fun createRawWordCallback(
        key: CallbackSignature,
        callback: ComRawWordCallback,
    ): NativeCallbackHandle {
        require(key.resultKind == ComAbiValueKind.Int32) {
            "Only int32-return callbacks are supported."
        }
        require(key.parameterKinds.size <= 7) {
            "Raw-word COM callbacks support at most seven carriers, got ${key.parameterKinds}."
        }
        require(key.parameterKinds.none { it is ComAbiValueKind.Struct }) {
            "Raw-word callbacks do not materialize by-value struct carriers: ${key.parameterKinds}."
        }

        val callbackArena = Arena.ofShared()
        var target =
            lookup.findStatic(
                ComVtableInvoker::class.java,
                "invokeRawWordCallbackDirect",
                MethodType.methodType(
                    Int::class.javaPrimitiveType,
                    ComRawWordCallback::class.java,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                ),
            )
        target = MethodHandles.insertArguments(target, 0, callback)
        while (target.type().parameterCount() > key.parameterKinds.size) {
            target = MethodHandles.insertArguments(target, key.parameterKinds.size, 0L)
        }
        target = MethodHandles.filterArguments(
            target,
            0,
            *key.parameterKinds.map(::rawWordFilter).toTypedArray(),
        )
        val exactHandle = target.asType(
            MethodType.methodType(
                Int::class.javaPrimitiveType,
                key.parameterKinds.map(::carrierClass),
            ),
        )
        return try {
            val stub = linker.upcallStub(exactHandle, key.asFunctionDescriptor(), callbackArena)
            NativeCallbackHandle(
                pointer = stub.asRawAddress(),
                onClose = callbackArena::close,
            )
        } catch (failure: Throwable) {
            callbackArena.close()
            throw failure
        }
    }

    private fun nextCallbackId(): Long {
        while (true) {
            val current = nextCallbackId.load()
            val updated = current + 1
            if (nextCallbackId.compareAndSet(current, updated)) {
                return current
            }
        }
    }

    @JvmStatic
    private fun invokeCallbackBridge(
        callbackId: Long,
        rawArguments: Array<Any?>,
    ): Int {
        val registered = callbackEntries[callbackId] ?: return KnownHResults.E_POINTER.value
        return try {
            val converted =
                registered.signature.parameterKinds.zip(rawArguments.asList()).map { (kind, value) ->
                    fromCarrier(kind, value)
                }
            registered.callback(converted)
        } catch (error: Throwable) {
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }
    }

    @JvmStatic
    private fun invokeRawWordCallbackDirect(
        callback: ComRawWordCallback,
        arg0: Long,
        arg1: Long,
        arg2: Long,
        arg3: Long,
        arg4: Long,
        arg5: Long,
        arg6: Long,
    ): Int {
        return try {
            callback.invoke(arg0, arg1, arg2, arg3, arg4, arg5, arg6)
        } catch (error: Throwable) {
            platformSetErrorInfo(error)
            platformHResultFromThrowable(error).value
        }
    }

    private fun rawWordFilter(kind: ComAbiValueKind): MethodHandle =
        when (kind) {
            ComAbiValueKind.Pointer -> rawWordFilter("rawPointerWord", MemorySegment::class.java)
            ComAbiValueKind.Int8 -> rawWordFilter("rawInt8Word", Byte::class.javaPrimitiveType!!)
            ComAbiValueKind.Int16 -> rawWordFilter("rawInt16Word", Short::class.javaPrimitiveType!!)
            ComAbiValueKind.Int32 -> rawWordFilter("rawInt32Word", Int::class.javaPrimitiveType!!)
            ComAbiValueKind.Int64 -> MethodHandles.identity(Long::class.javaPrimitiveType!!)
            ComAbiValueKind.Float -> rawWordFilter("rawFloatWord", Float::class.javaPrimitiveType!!)
            ComAbiValueKind.Double -> rawWordFilter("rawDoubleWord", Double::class.javaPrimitiveType!!)
            is ComAbiValueKind.Struct -> error("Struct callbacks use the compatibility callback path.")
        }

    private fun rawWordFilter(name: String, carrier: Class<*>): MethodHandle =
        lookup.findStatic(
            ComVtableInvoker::class.java,
            name,
            MethodType.methodType(Long::class.javaPrimitiveType, carrier),
        )

    @JvmStatic
    private fun rawPointerWord(value: MemorySegment): Long = value.address()

    @JvmStatic
    private fun rawInt8Word(value: Byte): Long = value.toLong()

    @JvmStatic
    private fun rawInt16Word(value: Short): Long = value.toLong()

    @JvmStatic
    private fun rawInt32Word(value: Int): Long = value.toLong()

    @JvmStatic
    private fun rawFloatWord(value: Float): Long = value.toRawBits().toLong()

    @JvmStatic
    private fun rawDoubleWord(value: Double): Long = value.toRawBits()
}

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
): Int = ComVtableInvoker.invokeArgs(instance, slot, arg0)

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultAddressAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
    arg1: RawAddress,
): Int = ComVtableInvoker.invokeArgs(instance, slot, arg0, arg1)

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultUInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: RawAddress,
): Int = ComVtableInvoker.invokeArgs(instance, slot, arg0, arg1)

@PublishedApi
internal actual inline fun winRTDirectInvokeHResultInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: RawAddress,
): Int = ComVtableInvoker.invokeArgs(instance, slot, arg0, arg1)

@PublishedApi
internal actual fun winRTCreateHResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = error("Native recipe thunks are only available on mingwX64.")

@PublishedApi
internal actual fun winRTCreatePackedScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = error("Native recipe thunks are only available on mingwX64.")

@PublishedApi
internal actual fun winRTCreateScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = error("Native recipe thunks are only available on mingwX64.")

@PublishedApi
internal actual fun winRTCreateWideScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress = error("Native recipe thunks are only available on mingwX64.")

@PublishedApi
internal actual fun winRTScalarResultRecord(): RawAddress =
    error("Scalar native-result transport is only available on mingwX64.")

@PublishedApi
internal actual inline fun winRTScalarResultHResult(record: RawAddress): Int =
    error("Scalar native-result transport is only available on mingwX64.")

@PublishedApi
internal actual inline fun winRTScalarResultValue(record: RawAddress): RawAddress =
    error("Scalar native-result transport is only available on mingwX64.")

private data class CallbackSignature(
    val resultKind: ComAbiValueKind,
    val parameterKinds: List<ComAbiValueKind>,
)

private data class RegisteredCallback(
    val signature: CallbackSignature,
    val callback: (List<Any?>) -> Int,
)

private fun CallbackSignature.asFunctionDescriptor(): FunctionDescriptor =
    FunctionDescriptor.of(
        toJavaLayout(resultKind),
        *parameterKinds.map(::toJavaLayout).toTypedArray(),
    )

private fun ComMethodSignature.asFunctionDescriptorWithThis(): FunctionDescriptor =
    FunctionDescriptor.of(
        toJavaLayout(resultKind),
        ValueLayout.ADDRESS,
        *explicitParameterKinds.map(::toJavaLayout).toTypedArray(),
    )

private fun toJavaLayout(kind: ComAbiValueKind) =
    when (kind) {
        ComAbiValueKind.Pointer -> ValueLayout.ADDRESS
        ComAbiValueKind.Int8 -> ValueLayout.JAVA_BYTE
        ComAbiValueKind.Int16 -> ValueLayout.JAVA_SHORT
        ComAbiValueKind.Int32 -> ValueLayout.JAVA_INT
        ComAbiValueKind.Int64 -> ValueLayout.JAVA_LONG
        ComAbiValueKind.Float -> ValueLayout.JAVA_FLOAT
        ComAbiValueKind.Double -> ValueLayout.JAVA_DOUBLE
        is ComAbiValueKind.Struct -> opaqueStructLayout(kind.layout)
    }

private fun opaqueStructLayout(layout: NativeAbiLayout): MemoryLayout {
    require(layout.byteSize > 0) {
        "Struct ABI layout size must be positive."
    }
    val elementLayout = when (layout.byteAlignment) {
        8L -> ValueLayout.JAVA_LONG
        4L -> ValueLayout.JAVA_INT
        2L -> ValueLayout.JAVA_SHORT
        1L -> ValueLayout.JAVA_BYTE
        else -> error("Unsupported struct ABI alignment: ${layout.byteAlignment}.")
    }
    require(layout.byteSize % layout.byteAlignment == 0L) {
        "Struct ABI size ${layout.byteSize} must be a multiple of alignment ${layout.byteAlignment}."
    }
    return MemoryLayout.structLayout(
        *Array((layout.byteSize / layout.byteAlignment).toInt()) { elementLayout },
    )
}

private fun carrierClass(kind: ComAbiValueKind): Class<*> =
    when (kind) {
        ComAbiValueKind.Pointer -> MemorySegment::class.java
        ComAbiValueKind.Int8 -> Byte::class.javaPrimitiveType!!
        ComAbiValueKind.Int16 -> Short::class.javaPrimitiveType!!
        ComAbiValueKind.Int32 -> Int::class.javaPrimitiveType!!
        ComAbiValueKind.Int64 -> Long::class.javaPrimitiveType!!
        ComAbiValueKind.Float -> Float::class.javaPrimitiveType!!
        ComAbiValueKind.Double -> Double::class.javaPrimitiveType!!
        is ComAbiValueKind.Struct -> MemorySegment::class.java
    }

private fun toCarrier(
    kind: ComAbiValueKind,
    word: Long,
): Any =
    when (kind) {
        ComAbiValueKind.Pointer -> asSegment(RawComPtr(word))
        ComAbiValueKind.Int8 -> word.toByte()
        ComAbiValueKind.Int16 -> word.toShort()
        ComAbiValueKind.Int32 -> word.toInt()
        ComAbiValueKind.Int64 -> word
        ComAbiValueKind.Float -> Float.fromBits(word.toInt())
        ComAbiValueKind.Double -> Double.fromBits(word)
        is ComAbiValueKind.Struct -> asSegment(RawAddress(word)).reinterpret(kind.layout.byteSize)
    }

private fun fromCarrier(
    kind: ComAbiValueKind,
    value: Any?,
): Any? =
    when (kind) {
        ComAbiValueKind.Pointer -> (value as MemorySegment).reinterpret(Long.MAX_VALUE).asRawAddress()
        ComAbiValueKind.Int8,
        ComAbiValueKind.Int16,
        ComAbiValueKind.Int32,
        ComAbiValueKind.Int64,
        ComAbiValueKind.Float,
        ComAbiValueKind.Double,
        -> value
        is ComAbiValueKind.Struct -> (value as MemorySegment).reinterpret(kind.layout.byteSize).asRawAddress()
    }

private fun asSegment(pointer: RawComPtr): MemorySegment =
    if (pointer.value == 0L) {
        MemorySegment.NULL
    } else {
        MemorySegment.ofAddress(pointer.value)
    }

private fun asSegment(pointer: RawAddress): MemorySegment =
    if (pointer.value == 0L) {
        MemorySegment.NULL
    } else {
        MemorySegment.ofAddress(pointer.value)
    }
