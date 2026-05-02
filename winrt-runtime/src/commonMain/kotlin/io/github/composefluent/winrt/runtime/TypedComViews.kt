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

internal class IUnknownView(
    private val comPtr: ComPtr,
) {
    fun tryQueryInterface(requestedInterfaceId: Guid): ComPtr? = comPtr.tryQueryInterface(requestedInterfaceId)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComPtr> = comPtr.queryInterface(requestedInterfaceId)

    fun addRef(): UInt = comPtr.addRef()

    fun release(): UInt = comPtr.release()
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
        ActivationFactoryReferenceSupport.activateInstance(
            invokeActivate = { instanceOut ->
                comPtr.throwIfDisposed()
                ComVtableInvoker.invokeArgs(
                    instance = comPtr.raw,
                    slot = IActivationFactoryVftblSlots.ActivateInstance,
                    arg0 = instanceOut,
                )
            },
            wrapInspectable = { inspectedPointer ->
                InspectableReference(ComPtr.create(inspectedPointer.asRawComPtr(), IID.IInspectable))
            },
            initializeReferenceTracker = { it.tryInitializeReferenceTracker() },
        )
}
