package io.github.composefluent.winrt.runtime

internal object WeakReferenceSourceVftblSlots {
    const val GetWeakReference: Int = 3
}

internal object WeakReferenceVftblSlots {
    const val Resolve: Int = 3
}

/**
 * Shared ABI-facing weak-reference wrappers corresponding to the COM reference flow in
 * `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs`.
 *
 * Managed weak-reference storage and object re-projection stay behind platform seams; the
 * raw `IWeakReferenceSource` / `IWeakReference` call shapes themselves are target-agnostic.
 */
internal fun ComObjectReference.tryGetWeakReference(): WeakReferenceReference? =
    // Match EventSourceCache: keep the QI result scoped instead of materializing a temporary
    // ComObjectReference and its finalization registration for this one ABI call.
    comPtr.tryWithQueryInterfacePointer(IID.IWeakReferenceSource, ::getWeakReferenceFromSource)

internal fun getWeakReferenceFromSource(source: RawComPtr): WeakReferenceReference? =
    // CsWinRT IWeakReferenceSourceMethods uses a cleared stack-local out pointer.
    // Reuse the runtime's reentrant scratch storage instead of allocating an owning scope.
    withWinRTScalarResult { resultOut ->
        HResult(
            ComVtableInvoker.invokeArgs(
                source,
                WeakReferenceSourceVftblSlots.GetWeakReference,
                resultOut,
            ),
        ).requireSuccess("IWeakReferenceSource.GetWeakReference")
        val resolvedPointer = PlatformAbi.readPointer(resultOut)
        if (PlatformAbi.isNull(resolvedPointer)) {
            null
        } else {
            WeakReferenceReference(resolvedPointer, IID.IWeakReference)
        }
    }

internal class WeakReferenceReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IWeakReference,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? =
        withWinRTStructStorage(
            sizeBytes = (Guid.BYTE_SIZE + Long.SIZE_BYTES).toLong(),
            alignmentBytes = Long.SIZE_BYTES.toLong(),
        ) { iidMemory ->
            interfaceId.writeTo(iidMemory)
            val resultOut = PlatformAbi.slice(iidMemory, Guid.BYTE_SIZE.toLong(), Long.SIZE_BYTES.toLong())
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(comPtr.raw, WeakReferenceVftblSlots.Resolve, iidMemory, resultOut),
            ).requireSuccess("IWeakReference.Resolve")
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                IUnknownReference(resolvedPointer.asRawComPtr(), interfaceId)
            }
        }
}
