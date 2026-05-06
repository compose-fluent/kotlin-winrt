package io.github.composefluent.winrt.runtime

object WinRtStaticProjectionInterop {
    fun callUnit(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ) {
        val hr = ComVtableInvoker.invokeGenericArgs(
            instance = reference.pointer,
            slot = slot,
            args = args,
        )
        HResult(hr).requireSuccess()
    }

    fun callBoolean(
        reference: IUnknownReference,
        slot: Int,
        vararg args: Any,
    ): Boolean =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt8Slot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(*args, resultOut),
            )
            HResult(hr).requireSuccess()
            PlatformAbi.readInt8(resultOut).toInt() != 0
        }

    fun <T> callProjectedRuntimeClass(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IInspectableReference) -> T,
        vararg args: Any,
    ): T =
        getInspectablePointer(reference, slot, args) { result ->
            wrap(result.asInspectable())
        }

    fun <T> callProjectedInterface(
        reference: IUnknownReference,
        slot: Int,
        wrap: (IUnknownReference) -> T,
        vararg args: Any,
    ): T =
        getInspectablePointer(reference, slot, args, wrap)

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
        getInspectablePointer(reference, slot, wrap = wrap)

    private fun <T> getInspectablePointer(
        reference: IUnknownReference,
        slot: Int,
        args: Array<out Any> = emptyArray(),
        wrap: (IUnknownReference) -> T,
    ): T =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            val hr = ComVtableInvoker.invokeGenericArgs(
                instance = reference.pointer,
                slot = slot,
                args = arrayOf(*args, resultOut),
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
