package io.github.composefluent.winrt.runtime

/**
 * Shared COM ABI bridge.
 *
 * Compatibility bridge for CCW callback creation. RCW call paths should call
 * `ComVtableInvoker.invoke` / `ComVtableInvoker.invokeArgs` directly, with
 * `ComVtableInvoker.invokeGeneric` reserved for explicit raw-word fallback.
 */
internal object ComAbiInteropBridge {
    fun createComMethodCallback(
        signature: ComMethodSignature,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = ComVtableInvoker.createComMethodCallback(signature, callback)

    fun createRawInt32Callback(
        parameterKinds: List<ComAbiValueKind>,
        callback: (List<Any?>) -> Int,
    ): NativeCallbackHandle = ComVtableInvoker.createRawInt32Callback(parameterKinds, callback)
}

internal object ComMethodSignatures {
    val HResult: ComMethodSignature = ComMethodSignature()
    val HResult_Ptr: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Pointer)
    val HResult_Int8: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Int8)
    val HResult_Int32: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Int32)
    val HResult_Int64: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Int64)
    val HResult_Double: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Double)
    val HResult_Int32_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
        )
    val HResult_Int32_Int32: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Int32,
            ComAbiValueKind.Int32,
        )
    val HResult_Ptr_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val HResult_Int32_Ptr_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val HResult_Ptr_Ptr_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val HResult_Ptr_Ptr_Ptr_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val HResult_Int32_Int32_Ptr_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Int32,
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val HResult_Ptr_Ptr_Ptr_Int32_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
        )
    val HResult_Ptr_Ptr_Int32_Ptr_Int32_Ptr: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
        )
    val HResult_Ptr_Ptr_Ptr_Int32_Ptr_Int32: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Int32,
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Int32,
        )
}
