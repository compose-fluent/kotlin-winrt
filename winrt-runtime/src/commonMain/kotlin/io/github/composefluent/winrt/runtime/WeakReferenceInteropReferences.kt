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

// CsWinRT's IWeakReferenceSourceMethods.GetWeakReference projects the owned out pointer
// through MarshalInterface<IWeakReference>.FromAbi and the existing RCW identity cache.
// IWeakReference is IUnknown-based; this must not use the untyped IInspectable path.
private val projectedWeakReferenceType = WinRTTypeHandle(
    projectedTypeName = "WinRT.Interop.IWeakReference",
    interfaceId = IID.IWeakReference,
)

internal fun ComObjectReference.tryGetProjectedWeakReference(): IWinRTObject? =
    comPtr.tryWithQueryInterfacePointer(IID.IWeakReferenceSource, ::getProjectedWeakReferenceFromSource)

private fun getProjectedWeakReferenceFromSource(source: RawComPtr): IWinRTObject? =
    ComWrappersSupport.createRcwForOwnedComObject<IWinRTObject>(
        pointer = getWeakReferenceAbiFromSource(source),
        staticallyDeterminedType = projectedWeakReferenceType,
        factory = { reference, type -> SingleInterfaceOptimizedObject(type, reference) },
    )

// EventSourceCache and low-level ABI callers continue to own exclusive wrappers.
internal fun getWeakReferenceFromSource(source: RawComPtr): WeakReferenceReference? {
    val pointer = getWeakReferenceAbiFromSource(source)
    return if (PlatformAbi.isNull(pointer)) null else {
        createReferenceForOwnedWeakAbi(pointer, IID.IWeakReference) { WeakReferenceReference(it) }
    }
}

private fun getWeakReferenceAbiFromSource(source: RawComPtr): RawAddress =
    // CsWinRT IWeakReferenceSourceMethods uses a cleared stack-local out pointer.
    // Reuse the runtime's reentrant scratch storage instead of allocating an owning scope.
    withWinRTScalarResult { resultOut ->
        val hResult = HResult(
            ComVtableInvoker.invokeArgs(
                source,
                WeakReferenceSourceVftblSlots.GetWeakReference,
                resultOut,
            ),
        )
        val pointer = PlatformAbi.readPointer(resultOut)
        try {
            hResult.requireSuccess("IWeakReferenceSource.GetWeakReference")
        } catch (error: Throwable) {
            // CsWinRT disposes its cleared ABI out even when GetWeakReference fails.
            // Capture the original error before Release can change thread error info.
            releaseFailedWeakAbiOutput(pointer, error)
            throw error
        }
        pointer
    }

internal fun IWinRTObject.resolveProjectedWeakReference(interfaceId: Guid): IUnknownReference? =
    try {
        // A cached RCW may hold another primary interface. Retrieve its IWeakReference
        // reference before calling slot 3, as CsWinRT's typed interface method does.
        getObjectReferenceForType(projectedWeakReferenceType).resolveWeakReference(interfaceId)
    } finally {
        // Keep the shared RCW reachable through the ABI call, including cache reentrancy.
        winRTKeepAlive(this)
    }

internal class WeakReferenceReference internal constructor(
    comPtr: ComPtr,
) : IUnknownReference(comPtr) {
    constructor(
        pointer: RawAddress,
        interfaceId: Guid = IID.IWeakReference,
    ) : this(ComPtr.create(pointer.asRawComPtr(), interfaceId))

    fun resolve(interfaceId: Guid): IUnknownReference? = resolveWeakReference(interfaceId)
}

// These ABI outputs each carry one owned reference. CsWinRT's weak methods keep caller-side
// DisposeAbi active until projection succeeds; use the explicit consuming ComPtr adoption here.
private inline fun <T : ComObjectReference> createReferenceForOwnedWeakAbi(
    pointer: RawAddress,
    interfaceId: Guid,
    createReference: (ComPtr) -> T,
): T {
    val comPtr = ComPtr.createForOwnedAbi(pointer.asRawComPtr(), interfaceId)
    return try {
        createReference(comPtr)
    } catch (error: Throwable) {
        try {
            // Adoption succeeded and may already have registered a cleaner. Close its shared
            // dispose-once owner; a raw Release here could consume the same reference twice.
            comPtr.close()
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
        }
        throw error
    }
}

private fun releaseFailedWeakAbiOutput(pointer: RawAddress, error: Throwable) {
    if (!PlatformAbi.isNull(pointer)) {
        try {
            WinRTPlatformApi.releaseRaw(pointer)
        } catch (cleanupError: Throwable) {
            error.addSuppressed(cleanupError)
        }
    }
}

private fun ComObjectReference.resolveWeakReference(interfaceId: Guid): IUnknownReference? =
    try {
        withWinRTStructStorage(
            sizeBytes = (Guid.BYTE_SIZE + Long.SIZE_BYTES).toLong(),
            alignmentBytes = Long.SIZE_BYTES.toLong(),
        ) { iidMemory ->
            interfaceId.writeTo(iidMemory)
            val resultOut = PlatformAbi.slice(iidMemory, Guid.BYTE_SIZE.toLong(), Long.SIZE_BYTES.toLong())
            comPtr.throwIfDisposed()
            val hResult = HResult(
                ComVtableInvoker.invokeArgs(comPtr.raw, WeakReferenceVftblSlots.Resolve, iidMemory, resultOut),
            )
            val resolvedPointer = PlatformAbi.readPointer(resultOut)
            try {
                hResult.requireSuccess("IWeakReference.Resolve")
            } catch (error: Throwable) {
                // No ComPtr owner has adopted this cleared ABI out yet. A failed native call
                // may still have written an owned reference, so consume it before throwing.
                releaseFailedWeakAbiOutput(resolvedPointer, error)
                throw error
            }
            if (PlatformAbi.isNull(resolvedPointer)) {
                null
            } else {
                createReferenceForOwnedWeakAbi(resolvedPointer, interfaceId) { IUnknownReference(it) }
            }
        }
    } finally {
        // This reference owns the COM pointer even when the shared RCW has another primary type.
        winRTKeepAlive(this)
    }
