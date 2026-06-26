package io.github.composefluent.winrt.runtime

class WinRTProjectionMarshaler internal constructor(
    private val lease: AbiReferenceLease<out AutoCloseable>,
) : AutoCloseable {
    val abi: RawAddress
        get() = lease.abi

    override fun close() {
        lease.close()
    }

    companion object {
        internal fun borrowed(
            reference: ComObjectReference,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.borrowed(
                    reference = reference,
                    cloneReference = ::cloneComReference,
                    abiOf = { it.pointer.asRawAddress() },
                ),
            )

        internal fun hosted(
            host: ManagedReferenceHost,
            interfaceId: Guid,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                lease = ManagedReferenceHostSupport.createLease(
                    createReference = { host.createReference(interfaceId) },
                    releaseManagedReference = host::releaseManagedReference,
                    abiOf = { it.pointer.asRawAddress() },
                ),
            )

        internal fun owned(
            reference: ComObjectReference,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.create(
                    abi = reference.pointer.asRawAddress(),
                    ownedReference = reference,
                ),
            )

        internal fun objectMarshaler(
            marshaler: WinRTObjectMarshaler,
        ): WinRTProjectionMarshaler =
            WinRTProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.create(
                    abi = marshaler.abi,
                    ownedReference = marshaler,
                ),
            )
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
): WinRTProjectionMarshaler {
    if (value == null) {
        return WinRTProjectionMarshaler(
            lease = AbiReferenceLeaseSupport.create(PlatformAbi.nullPointer),
        )
    }
    val typeHandle = WinRTTypeHandle(projectedTypeName, interfaceId)
    return borrowedProjectionMarshaler(value, typeHandle)
        ?: WinRTProjectionMarshaler.owned(ComWrappersSupport.createCCWForObject(value, interfaceId))
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
