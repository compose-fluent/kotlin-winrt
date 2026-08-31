package io.github.composefluent.winrt.runtime

/** Fixed-carrier callback surface used by compiler-lowered projected-interface CCW entries. */
internal fun interface ComRawWordCallback {
    fun invoke(
        arg0: Long,
        arg1: Long,
        arg2: Long,
        arg3: Long,
        arg4: Long,
        arg5: Long,
        arg6: Long,
    ): Int
}

expect object ComVtableInvoker {
    fun invokePointer(
        instance: RawComPtr,
        slot: Int,
    ): RawAddress

    fun invoke(
        instance: RawComPtr,
        slot: Int,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
    ): Int

    internal fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: NativeScalarScratchFrame,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Long,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
        arg1: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawComPtr,
        arg2: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: Int,
        arg2: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: Int,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: UInt,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: Int,
        arg3: RawAddress,
        arg4: Int,
        arg5: RawAddress,
    ): Int

    fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
        arg3: Int,
        arg4: RawAddress,
        arg5: Int,
    ): Int

    internal fun invokeGeneric(
        instance: RawComPtr,
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int

    internal fun createComMethodCallback(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle

    internal fun createRawWordComMethodCallback(
        signature: ComMethodSignature,
        callback: ComRawWordCallback,
    ): NativeCallbackHandle

    internal fun createRawInt32Callback(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle
}

/** Fixed-carrier entry points used by handwritten runtime code and compiler-lowered call sites. */
@PublishedApi
internal expect inline fun winRTDirectInvokeHResultAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
): Int

@PublishedApi
internal expect inline fun winRTDirectInvokeHResultAddressAddress(
    instance: RawComPtr,
    slot: Int,
    arg0: RawAddress,
    arg1: RawAddress,
): Int

@PublishedApi
internal expect inline fun winRTDirectInvokeHResultUInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: UInt,
    arg1: RawAddress,
): Int

@PublishedApi
internal expect inline fun winRTDirectInvokeHResultInt32Address(
    instance: RawComPtr,
    slot: Int,
    arg0: Int,
    arg1: RawAddress,
): Int

/**
 * Invokes one borrowed HSTRING input with a pointer result without exposing target scratch storage.
 * Native keeps both temporaries on the recipe-thunk stack; JVM adapts the same contract to FFM.
 */
internal expect inline fun <R> winRTDirectInvokeHStringPointerResult(
    instance: RawComPtr,
    slot: Int,
    value: String,
    crossinline consume: (hResult: Int, result: RawAddress) -> R,
): R

/**
 * Native-only recipe thunk factories. [floatingPointKinds] uses two bits per input carrier:
 * zero for an integer/address word, one for Float, and two for Double. The compiler stores the
 * returned entry point in a call-site-owner field, so recipe lookup and thunk construction never
 * occur on the hot path.
 */
@PublishedApi
internal expect fun winRTCreateHResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress

@PublishedApi
internal expect fun winRTCreatePackedScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress

@PublishedApi
internal expect fun winRTCreateScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress

@PublishedApi
internal expect fun winRTCreateWideScalarResultRecipeThunk(
    inputCount: Int,
    floatingPointKinds: Long,
): RawAddress

@PublishedApi
internal inline fun winRTPackedScalarResultHResult(packed: Long): Int =
    (packed ushr Int.SIZE_BITS).toInt()

@PublishedApi
internal inline fun winRTPackedScalarResultInt8(packed: Long): Byte = packed.toByte()

@PublishedApi
internal inline fun winRTPackedScalarResultInt16(packed: Long): Short = packed.toShort()

@PublishedApi
internal inline fun winRTPackedScalarResultInt32(packed: Long): Int = packed.toInt()

@PublishedApi
internal inline fun winRTPackedScalarResultFloat32(packed: Long): Float = Float.fromBits(packed.toInt())

@PublishedApi
internal inline fun winRTWideScalarResultFloat64(value: Long): Double = Double.fromBits(value)

@PublishedApi
internal expect fun winRTScalarResultRecord(): RawAddress

@PublishedApi
internal expect inline fun winRTScalarResultHResult(record: RawAddress): Int

@PublishedApi
internal expect inline fun winRTScalarResultValue(record: RawAddress): RawAddress

@PublishedApi
internal expect inline fun winRTConsumeOwnedHStringScalarResult(
    handleBits: Long,
    hResult: Int,
    checkHResult: Boolean,
): String
