package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment

class WinRtProjectionMarshaler internal constructor(
    val abi: MemorySegment,
    private val ownedReference: ComObjectReference? = null,
    private val cleanup: (() -> Unit)? = null,
) : AutoCloseable {
    override fun close() {
        ownedReference?.close()
        cleanup?.invoke()
    }

    companion object {
        internal fun borrowed(
            reference: ComObjectReference,
        ): WinRtProjectionMarshaler {
            val owned = cloneComReference(reference)
            return WinRtProjectionMarshaler(
                abi = owned.pointer,
                ownedReference = owned,
            )
        }

        internal fun hosted(
            host: WinRtInspectableComObject,
            interfaceId: Guid,
        ): WinRtProjectionMarshaler {
            val reference = host.createReference(interfaceId)
            return WinRtProjectionMarshaler(
                abi = reference.pointer,
                ownedReference = reference,
                cleanup = host::releaseManagedReference,
            )
        }
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
        WinRtProjectionMarshaler(
            abi = reference.pointer,
            ownedReference = reference,
        )
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
