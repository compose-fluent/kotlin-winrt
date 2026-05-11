package io.github.composefluent.winrt.runtime

class WinRtProjectionMarshaler internal constructor(
    private val lease: AbiReferenceLease<ComObjectReference>,
) : AutoCloseable {
    val abi: RawAddress
        get() = lease.abi

    override fun close() {
        lease.close()
    }

    companion object {
        internal fun borrowed(
            reference: ComObjectReference,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.borrowed(
                    reference = reference,
                    cloneReference = ::cloneComReference,
                    abiOf = { it.pointer.asRawAddress() },
                ),
            )

        internal fun hosted(
            host: ManagedReferenceHost,
            interfaceId: Guid,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = ManagedReferenceHostSupport.createLease(
                    createReference = { host.createReference(interfaceId) },
                    releaseManagedReference = host::releaseManagedReference,
                    abiOf = { it.pointer.asRawAddress() },
                ),
            )

        internal fun owned(
            reference: ComObjectReference,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.create(
                    abi = reference.pointer.asRawAddress(),
                    ownedReference = reference,
                ),
            )
    }
}

internal fun borrowedProjectionAbi(
    value: Any,
    typeHandle: WinRtTypeHandle,
): RawAddress? = borrowedProjectionReference(value, typeHandle)?.useAndGetRef()

internal fun borrowedProjectionMarshaler(
    value: Any,
    typeHandle: WinRtTypeHandle,
): WinRtProjectionMarshaler? =
    borrowedProjectionReference(value, typeHandle)?.let { reference ->
        WinRtProjectionMarshaler.owned(reference)
    }

fun winRtProjectionMarshaler(
    value: Any?,
    projectedTypeName: String,
    interfaceId: Guid,
): WinRtProjectionMarshaler {
    if (value == null) {
        return WinRtProjectionMarshaler(
            lease = AbiReferenceLeaseSupport.create(PlatformAbi.nullPointer),
        )
    }
    val typeHandle = WinRtTypeHandle(projectedTypeName, interfaceId)
    return borrowedProjectionMarshaler(value, typeHandle)
        ?: throw WinRtUnsupportedOperationException(
            "Object cannot be marshaled as '$projectedTypeName'.",
            KnownHResults.E_NOINTERFACE,
        )
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
    typeHandle: WinRtTypeHandle,
): ComObjectReference? =
    WinRtBorrowedReferenceSupport.tryBorrowReference(
        value = value,
        interfaceType = typeHandle,
        unwrapWinRtObject = ::borrowableWinRtObject,
        cloneReference = ::cloneComReference,
    )

internal fun borrowableWinRtObject(value: Any): BorrowableWinRtObject<ComObjectReference>? {
    val winrtObject = value as? IWinRTObject ?: return null
    return BorrowableWinRtObject(
        hasUnwrappableNativeObject = winrtObject.hasUnwrappableNativeObject,
        nativeObject = winrtObject.nativeObject,
        isInterfaceImplemented = { interfaceType ->
            winrtObject.isInterfaceImplemented(interfaceType, false)
        },
        getObjectReferenceForType = winrtObject::getObjectReferenceForType,
    )
}
