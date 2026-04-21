package io.github.kitectlab.winrt.runtime

class WinRtProjectionMarshaler internal constructor(
    private val lease: AbiReferenceLease<ComObjectReference>,
) : AutoCloseable {
    val abi: NativePointer
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
                    abiOf = ComObjectReference::pointer,
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
                    abiOf = ComObjectReference::pointer,
                ),
            )

        internal fun owned(
            reference: ComObjectReference,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.create(
                    abi = reference.pointer,
                    ownedReference = reference,
                ),
            )
    }
}

internal fun borrowedProjectionAbi(
    value: Any,
    typeHandle: WinRtTypeHandle,
): NativePointer? = borrowedProjectionReference(value, typeHandle)?.useAndGetRef()

internal fun borrowedProjectionMarshaler(
    value: Any,
    typeHandle: WinRtTypeHandle,
): WinRtProjectionMarshaler? =
    borrowedProjectionReference(value, typeHandle)?.let { reference ->
        WinRtProjectionMarshaler.owned(reference)
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

internal fun <T : ComObjectReference> T.useAndGetRef(): NativePointer = use { it.getRefPointer() }

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
