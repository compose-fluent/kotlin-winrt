package io.github.composefluent.winrt.runtime

internal object ActivationFactoryReferenceSupport {
    fun activateInstance(comPtr: ComPtr): IInspectableReference =
        acquireNativeScalarScratchFrame().use { resultOut ->
            comPtr.throwIfDisposed()
            val hResult = ComVtableInvoker.invokeArgs(
                instance = comPtr.raw,
                slot = IActivationFactoryVftblSlots.ActivateInstance,
                arg0 = resultOut,
            )
            winRTKeepAlive(comPtr)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            InspectableReference(
                ComPtr.create(
                    resultOut.readPointer().asRawComPtr(),
                    IID.IInspectable,
                ),
            ).also { it.tryInitializeReferenceTracker() }
        }
}

internal object InspectableReferenceSupport {
    fun getRuntimeClassName(
        noThrow: Boolean,
        invokeGetRuntimeClassName: (RawAddress) -> Int,
    ): String? =
        PlatformAbi.confinedScope().use { scope ->
            val hStringOut = PlatformAbi.allocatePointerSlot(scope)
            val hResult = invokeGetRuntimeClassName(hStringOut)
            if (HResult(hResult).isFailure) {
                if (noThrow) {
                    return null
                }
                WinRTPlatformApi.checkSucceededRaw(hResult)
            }

            val handle = PlatformAbi.readPointer(hStringOut)
            if (PlatformAbi.isNull(handle)) {
                return null
            }
            return HString.fromHandle(handle, owner = true).use(HString::toKString)
        }
}
