package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

class WinRtProjectionMarshaler internal constructor(
    private val lease: AbiReferenceLease<ComObjectReference>,
) : AutoCloseable {
    val abi: MemorySegment
        get() = lease.abi.asMemorySegment()

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
                    abiOf = { it.pointer.asNativePointer() },
                ),
            )

        internal fun hosted(
            host: WinRtInspectableComObject,
            interfaceId: Guid,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = ManagedReferenceHostSupport.createLease(
                    createReference = { host.createReference(interfaceId) },
                    releaseManagedReference = host::releaseManagedReference,
                    abiOf = { it.pointer.asNativePointer() },
                ),
            )

        internal fun owned(
            reference: ComObjectReference,
        ): WinRtProjectionMarshaler =
            WinRtProjectionMarshaler(
                lease = AbiReferenceLeaseSupport.create(
                    abi = reference.pointer.asNativePointer(),
                    ownedReference = reference,
                ),
            )
    }
}

internal fun borrowedProjectionAbi(
    value: Any,
    typeHandle: WinRtTypeHandle,
): MemorySegment? = borrowedProjectionReference(value, typeHandle)?.useAndGetRef()

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
        pointer = reference.getRef().asNativePointer(),
        interfaceId = reference.interfaceId,
        wrapUnknown = { pointer, interfaceId ->
            IUnknownReference(pointer.asMemorySegment(), interfaceId)
        },
        wrapInspectable = { pointer, interfaceId ->
            IInspectableReference(pointer.asMemorySegment(), interfaceId)
        },
        wrapActivationFactory = { pointer, interfaceId ->
            ActivationFactoryReference(pointer.asMemorySegment(), interfaceId)
        },
    )

internal fun <T : ComObjectReference> T.useAndGetRef(): MemorySegment = use { it.getRef() }

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
