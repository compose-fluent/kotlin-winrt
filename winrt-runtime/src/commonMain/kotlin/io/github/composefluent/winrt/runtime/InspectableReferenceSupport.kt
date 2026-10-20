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

    fun activateDefaultInstance(
        comPtr: ComPtr,
        defaultInterfaceId: Guid,
        marshalingType: WinRTObjectReferenceMarshalingType,
    ): IInspectableReference =
        // CsWinRT 3's fast attachment path assumes the returned activation pointer can
        // serve its generated default-interface ABI. Our ordinary interface-slot calls
        // need the queried interface: WinUI TextBlock returns a different IInspectable
        // pointer from IActivationFactory than QI(ITextBlock). Merely tagging the former
        // with the default IID skips the constructor cast and dispatches the wrong vtable.
        // Keep this adaptation at activation, shared by JVM and Native, as in CsWinRT's
        // code_writers.h constructor objRef.As(defaultInterface.IID) ownership boundary.
        activateInstance(comPtr, defaultInterfaceId, requireQueryInterface = true, marshalingType = marshalingType)

    private fun activateInstance(
        comPtr: ComPtr,
        interfaceId: Guid,
        requireQueryInterface: Boolean,
        marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
    ): IInspectableReference =
        acquireNativeScalarScratchFrame().use { resultOut ->
            val hResult = comPtr.withCallPointer { activePointer -> ComVtableInvoker.invokeArgs(
                instance = activePointer,
                slot = IActivationFactoryVftblSlots.ActivateInstance,
                arg0 = resultOut,
            ) }
            winRTKeepAlive(comPtr)
            WinRTPlatformApi.checkSucceededRaw(hResult)
            val activatedPointer = resultOut.readPointer()
            if (!requireQueryInterface) {
                return@use wrapActivatedInstance(activatedPointer, interfaceId, marshalingType)
            }

            // Both generic and generated typed activation acquire the requested vtable,
            // as in C++/WinRT IActivationFactory.ActivateInstance<T>. Untyped activation
            // retains the IInspectable result through the branch above.
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
    inline fun getRuntimeClassName(
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
            // CsWinRT 3 CreateObject scopes the returned HSTRING to type resolution.
            // Reuse the owned-result consumer instead of allocating a temporary owner.
            return consumeOwnedHString(handle)
        }
}
