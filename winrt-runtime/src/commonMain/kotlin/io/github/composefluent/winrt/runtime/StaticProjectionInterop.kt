package io.github.composefluent.winrt.runtime

object WinRtStaticProjectionInterop {
    fun <T> getProjectedRuntimeClass(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
    ): T =
        getInspectablePointer(reference, slot) { result ->
            wrap(result.asInspectable())
        }

    fun <T> getProjectedInterface(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        getInspectablePointer(reference, slot, wrap)

    private fun <T> getInspectablePointer(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeArgs(
                instance = reference.pointer,
                slot = slot,
                arg0 = resultOut,
            )
            HResult(hr).requireSuccess()
            val resultPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resultPointer)) {
                error("WINRT_E_NULL_ABI_RETURN")
            }
            val resultRef = IUnknownReference(PlatformAbi.toRawComPtr(resultPointer))
            wrap(resultRef)
        }
}
