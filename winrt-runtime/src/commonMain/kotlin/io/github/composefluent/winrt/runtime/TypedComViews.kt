package io.github.composefluent.winrt.runtime

internal object IUnknownVftbl {
    val QueryInterface: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val AddRef: ComMethodSignature = ComMethodSignature.of()
    val Release: ComMethodSignature = ComMethodSignature.of()
}

internal object IInspectableVftbl {
    val GetIids: ComMethodSignature =
        ComMethodSignature.of(
            ComAbiValueKind.Pointer,
            ComAbiValueKind.Pointer,
        )
    val GetRuntimeClassName: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Pointer)
    val GetTrustLevel: ComMethodSignature = ComMethodSignature.of(ComAbiValueKind.Pointer)
}

internal class IInspectableView(
    private val comPtr: ComPtr,
) {
    fun tryGetRuntimeClassName(): String? = getRuntimeClassName(noThrow = true)

    fun getRuntimeClassName(noThrow: Boolean = false): String? =
        InspectableReferenceSupport.getRuntimeClassName(
            noThrow = noThrow,
            invokeGetRuntimeClassName = { hStringOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(
                    instance = comPtr.raw,
                    slot = IInspectableVftblSlots.GetRuntimeClassName,
                    arg0 = hStringOut,
                )
            },
        )
}

internal class IActivationFactoryView(
    private val comPtr: ComPtr,
) {
    fun activateInstance(): IInspectableReference =
        ActivationFactoryReferenceSupport.activateInstance(comPtr)
}
