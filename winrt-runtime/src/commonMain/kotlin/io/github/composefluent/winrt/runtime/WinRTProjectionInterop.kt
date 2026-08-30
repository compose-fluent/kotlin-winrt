package io.github.composefluent.winrt.runtime

class WinRTProjectionMarshaler internal constructor(
    val abi: RawAddress,
    private val ownedReference: AutoCloseable? = null,
    private val managedHost: ManagedReferenceHost? = null,
    private val managedCallLease: WinRTProjectionMarshaler? = null,
    private val managedCallValue: Any? = null,
    @PublishedApi internal val managedCallHost: WinRTInspectableComObject? = null,
) : AutoCloseable {
    override fun close() {
        managedCallLease?.let { lease ->
            lease.releaseManagedCall(managedCallValue)
            return
        }
        managedCallHost?.let { host ->
            host.endStaticCallLease(null)
            return
        }
        try {
            ownedReference?.close()
        } finally {
            managedHost?.releaseManagedReference()
        }
    }

    companion object {
        internal fun borrowed(
            reference: ComObjectReference,
        ): WinRTProjectionMarshaler =
            owned(cloneComReference(reference))

        internal fun hosted(
            host: ManagedReferenceHost,
            interfaceId: Guid,
        ): WinRTProjectionMarshaler =
            managed(
                abi = host.detachReference(interfaceId),
                host = host,
            )

        internal fun managed(
            abi: RawAddress,
            host: ManagedReferenceHost,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                abi = abi,
                managedHost = host,
            )

        internal fun cachedCallLease(
            abi: RawAddress,
            host: WinRTInspectableComObject,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                abi = abi,
                managedCallHost = host,
            )

        internal fun guardedCallLease(
            lease: WinRTProjectionMarshaler,
            managedValue: Any,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                abi = lease.abi,
                managedCallLease = lease,
                managedCallValue = managedValue,
            )

        internal fun owned(
            reference: ComObjectReference,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                abi = reference.pointer.asRawAddress(),
                ownedReference = reference,
            )

        internal fun objectMarshaler(
            marshaler: WinRTObjectMarshaler,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                abi = marshaler.abi,
                ownedReference = marshaler,
            )
    }

    @PublishedApi
    internal inline fun releaseManagedCall(knownManagedValue: Any?) {
        val host = managedCallHost
            ?: error("WinRT projection marshaler is not a managed call lease.")
        host.endStaticCallLease(knownManagedValue)
    }

}

internal fun borrowedProjectionAbi(
    value: Any,
    typeHandle: WinRTTypeHandle,
): RawAddress? = borrowedProjectionReference(value, typeHandle)?.useAndGetRef()

internal fun borrowedProjectionMarshaler(
    value: Any,
    typeHandle: WinRTTypeHandle,
): WinRTProjectionMarshaler? =
    borrowedProjectionReference(value, typeHandle)?.let { reference ->
        WinRTProjectionMarshaler.owned(reference)
    }

fun winRTProjectionMarshaler(
    value: Any?,
    projectedTypeName: String,
    interfaceId: Guid,
): WinRTProjectionMarshaler =
    winRTProjectionMarshaler(value, WinRTTypeHandle(projectedTypeName, interfaceId))

fun winRTProjectionMarshaler(
    value: Any?,
    typeHandle: WinRTTypeHandle,
): WinRTProjectionMarshaler {
    if (value == null) {
        return WinRTProjectionMarshaler(PlatformAbi.nullPointer)
    }
    return borrowedProjectionMarshaler(value, typeHandle)
        ?: ComWrappersSupport.createCCWForObjectForMarshaling(value, typeHandle.interfaceId)
}

fun acquireBorrowedInterfaceReference(
    pointer: RawAddress,
    interfaceId: Guid,
): IUnknownReference? {
    if (PlatformAbi.isNull(pointer)) return null
    return IUnknownReference(
        retainBorrowedComPointer(PlatformAbi.toRawComPtr(pointer)),
        interfaceId,
    )
}

@PublishedApi
internal fun acquireBorrowedInterfaceReference(
    pointer: RawAddress,
    interfaceIdLowBits: Long,
    interfaceIdHighBits: Long,
): IUnknownReference? {
    if (PlatformAbi.isNull(pointer)) return null
    return IUnknownReference(
        ComPtr.create(
            raw = retainBorrowedComPointer(PlatformAbi.toRawComPtr(pointer)),
            interfaceIdLowBits = interfaceIdLowBits,
            interfaceIdHighBits = interfaceIdHighBits,
        ),
    )
}

fun acquireBorrowedInspectableReference(pointer: RawAddress): IInspectableReference? {
    if (PlatformAbi.isNull(pointer)) return null
    return IUnknownReference(
        PlatformAbi.toRawComPtr(pointer),
        preventReleaseOnDispose = true,
    ).tryAsInspectable()
}

public inline fun winRTManagedProjectionMarshalerOrNull(
    value: Any?,
    typeHandle: WinRTTypeHandle,
): WinRTProjectionMarshaler? =
    if (value == null || (value as? IWinRTObject)?.hasUnwrappableNativeObject == true) {
        null
    } else {
        ComWrappersSupport.createCCWForObjectForMarshaling(value, typeHandle.interfaceId)
    }

/**
 * Acquires the cached common-runtime scope used by compiler-expanded projected-interface calls.
 * Composable objects keep their existing raw outer-ABI path; native wrappers and cold CCWs return
 * null so the caller can use the normal projection marshaler.
 */
public inline fun tryAcquireWinRTManagedProjectionCallLease(
    value: Any?,
    typeHandle: WinRTTypeHandle,
): WinRTProjectionMarshaler? {
    if (value == null) return null

    val stateOwner = value as? WinRTManagedProjectionStateOwner
    if (stateOwner != null) {
        stateOwner.winRTManagedProjectionState()?.let { state ->
            return state.tryAcquireCallLease(value, typeHandle.interfaceId)
        }
    }

    return tryAcquireWinRTManagedProjectionCallLease(value, null, typeHandle)
}

/** Uses statically dispatched projected-interface state without classifying [knownManagedValue]. */
public inline fun tryAcquireWinRTManagedProjectionCallLease(
    knownManagedValue: Any,
    managedState: WinRTManagedProjectionState?,
    typeHandle: WinRTTypeHandle,
): WinRTProjectionMarshaler? {
    if (managedState != null) {
        return managedState.tryAcquireCallLease(knownManagedValue, typeHandle.interfaceId)
    }

    if (
        knownManagedValue is WinRTComposableObject ||
        (knownManagedValue as? IWinRTObject)?.hasUnwrappableNativeObject == true
    ) {
        return null
    }
    return ComWrappersSupport.tryAcquireCachedCCWCallLease(knownManagedValue, typeHandle.interfaceId)
}

public inline fun releaseWinRTManagedProjectionCallLease(
    lease: WinRTProjectionMarshaler,
    knownManagedValue: Any,
) {
    lease.releaseManagedCall(knownManagedValue)
}

/**
 * Borrows a CCW pointer for one projected call while [value] remains strongly reachable.
 * A null pointer means the caller must use the normal owned-marshaler or RCW path.
 */
public inline fun tryBorrowWinRTManagedProjectionAbi(
    value: Any?,
    typeHandle: WinRTTypeHandle,
): RawAddress {
    if (value == null || (value as? IWinRTObject)?.hasUnwrappableNativeObject == true) {
        return PlatformAbi.nullPointer
    }

    val composableAbi = (value as? WinRTComposableObject)
        ?.winRTComposableObjectReference
        ?.tryBorrowStaticCallAbi(typeHandle.interfaceId)
        ?: PlatformAbi.nullPointer
    if (!PlatformAbi.isNull(composableAbi)) {
        return composableAbi
    }

    val stateOwner = value as? WinRTManagedProjectionStateOwner
    if (stateOwner != null) {
        stateOwner.winRTManagedProjectionState()?.let { state ->
            return state.tryBorrowAbi(typeHandle.interfaceId)
        }
    }

    return ComWrappersSupport.tryBorrowCachedCCWForObjectForMarshaling(value, typeHandle.interfaceId)
}

/** Uses statically dispatched projected-interface state without classifying [knownManagedValue]. */
public inline fun tryBorrowWinRTManagedProjectionAbi(
    knownManagedValue: Any,
    managedState: WinRTManagedProjectionState?,
    typeHandle: WinRTTypeHandle,
): RawAddress {
    if ((knownManagedValue as? IWinRTObject)?.hasUnwrappableNativeObject == true) {
        return PlatformAbi.nullPointer
    }

    val composableAbi = (knownManagedValue as? WinRTComposableObject)
        ?.winRTComposableObjectReference
        ?.tryBorrowStaticCallAbi(typeHandle.interfaceId)
        ?: PlatformAbi.nullPointer
    if (!PlatformAbi.isNull(composableAbi)) {
        return composableAbi
    }

    if (managedState != null) {
        return managedState.tryBorrowAbi(typeHandle.interfaceId)
    }

    return ComWrappersSupport.tryBorrowCachedCCWForObjectForMarshaling(
        knownManagedValue,
        typeHandle.interfaceId,
    )
}

/**
 * Borrows the inspectable pointer used by a metadata-described `System.Object` input.
 *
 * This is intentionally narrower than [tryBorrowWinRTManagedProjectionAbi]: object marshaling
 * has identity rules for projected wrappers, delegates, and raw COM carriers that must remain on
 * [WinRTObjectMarshaller]'s owned path.  Ordinary managed values can use the CCW pointer already
 * retained by the common cache while the caller keeps [value] strongly reachable.  A null pointer
 * means that the caller must use its owned marshaler fallback.
 */
public inline fun tryBorrowWinRTManagedInspectableAbi(value: Any?): RawAddress {
    if (value == null) {
        return PlatformAbi.nullPointer
    }

    // Compiler-injected state is the managed projected-object fast path.  The lowering only adds
    // this owner to classes that do not implement IWinRTObject, so a populated state is enough to
    // select the cached inspectable ABI without first paying the projected-wrapper carrier tests.
    val stateOwner = value as? WinRTManagedProjectionStateOwner
    if (stateOwner != null) {
        stateOwner.winRTManagedProjectionState()?.let { state ->
            val abi = state.tryBorrowAbi(IID.IInspectable)
            if (!PlatformAbi.isNull(abi)) {
                return abi
            }
        }
    }

    if (
        value is WinRTProjectedDelegate ||
        value is IWinRTObject ||
        value is WinRTComposableObject ||
        value is RawAddress ||
        value is RawComPtr ||
        value is ComObjectReference
    ) {
        return PlatformAbi.nullPointer
    }

    return ComWrappersSupport.tryBorrowCachedCCWForObjectForMarshaling(value, IID.IInspectable)
}

internal fun cloneComReference(reference: ComObjectReference): ComObjectReference =
    ComReferenceWrapperSupport.wrap(
        kind = reference.wrapperKind,
        pointer = reference.getRefPointer(),
        interfaceId = reference.interfaceId,
        wrapUnknown = ::IUnknownReference,
        wrapInspectable = ::IInspectableReference,
        wrapActivationFactory = ::ActivationFactoryReference,
    )

internal fun <T : ComObjectReference> T.useAndGetRef(): RawAddress = use { it.getRefPointer().asRawAddress() }

private fun borrowedProjectionReference(
    value: Any,
    typeHandle: WinRTTypeHandle,
): ComObjectReference? =
    WinRTBorrowedReferenceSupport.tryBorrowReference(
        value = value,
        interfaceType = typeHandle,
        unwrapWinRTObject = ::borrowableWinRTObject,
        cloneReference = ::cloneComReference,
    )

internal fun borrowableWinRTObject(value: Any): BorrowableWinRTObject<ComObjectReference>? {
    val winrtObject = value as? IWinRTObject ?: return null
    return BorrowableWinRTObject(
        hasUnwrappableNativeObject = winrtObject.hasUnwrappableNativeObject,
        nativeObject = winrtObject.nativeObject,
        isInterfaceImplemented = { interfaceType ->
            winrtObject.isInterfaceImplemented(interfaceType, false)
        },
        getObjectReferenceForType = winrtObject::getObjectReferenceForType,
    )
}
