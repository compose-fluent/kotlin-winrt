package io.github.composefluent.winrt.runtime

internal object ActivationFactoryReferenceSupport {
    fun activateInstance(comPtr: ComPtr): IInspectableReference =
        activateInstance(comPtr, IID.IInspectable, requireQueryInterface = false)

    fun activateInstance(
        comPtr: ComPtr,
        interfaceId: Guid,
        marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
    ): IInspectableReference =
        activateInstance(comPtr, interfaceId, requireQueryInterface = true, marshalingType = marshalingType)

    private fun activateInstance(
        comPtr: ComPtr,
        interfaceId: Guid,
        requireQueryInterface: Boolean,
        marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
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
                return@use wrapActivatedInstance(activatedPointer, interfaceId, marshalingType)
            }

            // Mirrors CsWinRT's generated ActivateInstanceUnsafe(factory, defaultInterfaceIid) path.
            try {
                val queryResult = WinRTPlatformApi.queryInterfaceRaw(activatedPointer, interfaceId)
                WinRTPlatformApi.checkSucceededRaw(queryResult.hResultValue)
                wrapActivatedInstance(queryResult.pointer, interfaceId, marshalingType)
            } finally {
                WinRTPlatformApi.releaseRaw(activatedPointer)
            }
        }

    private fun wrapActivatedInstance(
        pointer: RawAddress,
        interfaceId: Guid,
        marshalingType: WinRTObjectReferenceMarshalingType,
    ): IInspectableReference =
        InspectableReference(
            ComPtr.createForOwnedAbi(
                pointer.asRawComPtr(),
                interfaceId,
                marshalingType,
            ),
        ).also { reference ->
            try {
                reference.tryInitializeReferenceTracker()
            } catch (failure: Throwable) {
                reference.close()
                throw failure
            }
        }
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
