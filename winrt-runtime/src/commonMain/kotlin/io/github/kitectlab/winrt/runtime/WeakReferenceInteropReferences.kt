package io.github.kitectlab.winrt.runtime

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
internal class WeakReferenceSourceReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IWeakReferenceSource,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun getWeakReference(): WeakReferenceReference? =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(comPtr.raw, WeakReferenceSourceVftblSlots.GetWeakReference, resultOut),
            ).requireSuccess("IWeakReferenceSource.GetWeakReference")
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                WeakReferenceReference(resolvedPointer, IID.IWeakReference)
            }
        }
}

internal fun ComObjectReference.tryGetWeakReference(): WeakReferenceReference? =
    tryQueryInterface(IID.IWeakReferenceSource)?.use { weakReferenceSource ->
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
            weakReferenceSource.comPtr.throwIfDisposed()
            HResult(
                ComVtableInvoker.invokeArgs(
                    weakReferenceSource.comPtr.raw,
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
    }

internal class WeakReferenceReference(
    pointer: RawAddress,
    interfaceId: Guid = IID.IWeakReference,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId) {
    fun resolve(interfaceId: Guid): IUnknownReference? =
        PlatformAbi.confinedScope().use { scope ->
            val iidMemory = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            interfaceId.writeTo(iidMemory)
            val resultOut = PlatformAbi.allocatePointerSlot(scope)
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
