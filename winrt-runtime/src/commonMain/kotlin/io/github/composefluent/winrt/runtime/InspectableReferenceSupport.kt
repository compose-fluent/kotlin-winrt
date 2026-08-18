package io.github.composefluent.winrt.runtime

internal object ActivationFactoryReferenceSupport {
    fun activateInstance(comPtr: ComPtr): IInspectableReference =
        activateInstance(comPtr, IID.IInspectable, requireQueryInterface = false)

    fun activateInstance(
        comPtr: ComPtr,
        interfaceId: Guid,
    ): IInspectableReference =
        activateInstance(comPtr, interfaceId, requireQueryInterface = true)

    private fun activateInstance(
        comPtr: ComPtr,
        interfaceId: Guid,
        requireQueryInterface: Boolean,
    ): IInspectableReference =
        acquireNativeScalarScratchFrame().use { resultOut ->
            comPtr.throwIfDisposed()
            val hResult = ComVtableInvoker.invokeArgs(
                instance = comPtr.raw,
                slot = IActivationFactoryVftblSlots.ActivateInstance,
                arg0 = resultOut,
            )
            winRTKeepAlive(comPtr)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            val activatedPointer = resultOut.readPointer()
            if (!requireQueryInterface) {
                return@use wrapActivatedInstance(activatedPointer, interfaceId)
            }

            // Mirrors CsWinRT's generated ActivateInstanceUnsafe(factory, defaultInterfaceIid) path.
            try {
                val queryResult = WinRTPlatformApi.queryInterfaceRaw(activatedPointer, interfaceId)
                WinRTPlatformApi.checkSucceededRaw(queryResult.hResultValue)
                wrapActivatedInstance(queryResult.pointer, interfaceId)
            } finally {
                WinRTPlatformApi.releaseRaw(activatedPointer)
            }
        }

    private fun wrapActivatedInstance(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IInspectableReference =
        InspectableReference(
            ComPtr.create(
                pointer.asRawComPtr(),
                interfaceId,
            ),
        ).also { it.tryInitializeReferenceTracker() }
}

internal object InspectableReferenceSupport {
    fun getRuntimeClassName(
        noThrow: Boolean,
        invokeGetRuntimeClassName: (RawAddress) -> Int,
    ): String? =
        acquireNativeScalarScratchFrame().use { hStringOut ->
            val hResult = invokeGetRuntimeClassName(hStringOut.pointer)
            if (HResult(hResult).isFailure) {
                if (noThrow) {
                    return null
                }
                WinRTPlatformApi.checkSucceededRaw(hResult)
            }

            val handle = hStringOut.readPointer()
            if (PlatformAbi.isNull(handle)) {
                return null
            }
            return HString.fromHandle(handle, owner = true).use(HString::toKString)
        }
}
