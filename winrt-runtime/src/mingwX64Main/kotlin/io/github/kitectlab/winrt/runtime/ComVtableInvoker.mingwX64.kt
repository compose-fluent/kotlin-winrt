package io.github.kitectlab.winrt.runtime

actual object ComVtableInvoker {
    actual fun invokePointer(
        instance: RawComPtr,
        slot: Int,
    ): RawAddress = TODO()

    actual fun invoke(
        instance: RawComPtr,
        slot: Int,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Byte,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Short,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Long,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Double,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Float,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawComPtr,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Byte,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawComPtr,
        arg2: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: UInt,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: Int,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Double,
        arg1: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Double,
        arg1: Double,
        arg2: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Byte,
        arg1: Short,
        arg2: Float,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: RawAddress,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int = TODO()

    actual fun invokeArgs(
        instance: RawComPtr,
        slot: Int,
        arg0: Int,
        arg1: RawAddress,
        arg2: RawAddress,
    ): Int = TODO()

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
    ): Int = TODO()

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
    ): Int = TODO()

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
    ): Int = TODO()

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

    internal actual fun invokeGeneric(
        instance: RawComPtr,
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int = TODO()

    internal actual fun createComMethodCallback(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = TODO()

    internal actual fun createRawInt32Callback(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = TODO()
}
