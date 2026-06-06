package io.github.composefluent.winrt.runtime

open class ComObjectReference internal constructor(
    internal val comPtr: ComPtr,
) : AutoCloseable {
    constructor(
        pointer: RawComPtr,
        interfaceId: Guid,
        referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
        preventReleaseOnDispose: Boolean = false,
        isAggregated: Boolean = false,
    ) : this(
        ComPtr.create(
            raw = pointer,
            interfaceId = interfaceId,
            ownershipMode =
                if (preventReleaseOnDispose) {
                    ComOwnershipMode.Borrowed
                } else {
                    ComOwnershipMode.Owned
                },
            referenceTrackerPointer = referenceTrackerPointer,
            isAggregated = isAggregated,
        ),
    )

    val pointer: RawComPtr
        get() = comPtr.pointer

    val interfaceId: Guid
        get() = comPtr.interfaceId

    val isDisposed: Boolean
        get() = comPtr.isDisposed

    val hasReferenceTracker: Boolean
        get() = comPtr.hasReferenceTracker

    internal val isAggregated: Boolean
        get() = comPtr.isAggregated

    internal open val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.Unknown

    internal val referenceTrackerHandle: RawComPtr
        get() = comPtr.referenceTrackerHandle

    internal fun asIUnknownView(): IUnknownView = IUnknownView(comPtr)

    fun addRef(): UInt =
        asIUnknownView().addRef()

    fun release(): UInt =
        asIUnknownView().release()

    fun getRefPointer(): RawComPtr =
        comPtr.getRefPointer()

    open fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? =
        asIUnknownView().tryQueryInterface(requestedInterfaceId)?.let(::wrapQueriedReference)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        comPtr.tryInitializeReferenceTracker(addRefFromTrackerSource)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComObjectReference> =
        asIUnknownView().queryInterface(requestedInterfaceId).map(::wrapQueriedReference)

    fun getDefaultInterfaceObjectReference(vtableSlot: Int): IUnknownReference {
        throwIfDisposed()
        val pointer = ComVtableInvoker.invokePointer(comPtr.raw, vtableSlot)
        if (PlatformAbi.isNull(pointer)) {
            throw WinRtUnsupportedOperationException(
                "Fast ABI default-interface object reference returned a null pointer from vtable slot $vtableSlot",
                KnownHResults.E_POINTER,
            )
        }
        return IUnknownReference(ComPtr.create(pointer.asRawComPtr(), IID.IUnknown))
    }

    fun tryAsInspectable(): IInspectableReference? =
        asIUnknownView().tryQueryInterface(IID.IInspectable)?.let(::InspectableReference)

    fun asInspectable(): IInspectableReference =
        tryAsInspectable()
            ?: throw WinRtUnsupportedOperationException(
                "QueryInterface failed for ${IID.IInspectable} with ${KnownHResults.E_NOINTERFACE}",
                KnownHResults.E_NOINTERFACE,
            )

    fun sameIdentity(other: ComObjectReference): Boolean =
        comPtr.sameIdentity(other.comPtr)

    override fun close() {
        comPtr.close()
    }

    protected fun throwIfDisposed() {
        comPtr.throwIfDisposed()
    }

    internal open fun wrapQueriedReference(queriedComPtr: ComPtr): ComObjectReference =
        ComReferenceWrapperSupport.wrap(
            kind = ComReferenceWrapperSupport.kindForInterfaceId(queriedComPtr.interfaceId),
            comPtr = queriedComPtr,
            wrapUnknown = ::IUnknownReference,
            wrapInspectable = ::InspectableReference,
            wrapActivationFactory = ::ActivationFactoryReference,
        )
}

open class IUnknownReference internal constructor(
    comPtr: ComPtr,
) : ComObjectReference(comPtr) {
    constructor(
        pointer: RawComPtr,
        interfaceId: Guid = IID.IUnknown,
        referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
        preventReleaseOnDispose: Boolean = false,
        isAggregated: Boolean = false,
    ) : this(
        ComPtr.create(
            raw = pointer,
            interfaceId = interfaceId,
            ownershipMode =
                if (preventReleaseOnDispose) {
                    ComOwnershipMode.Borrowed
                } else {
                    ComOwnershipMode.Owned
                },
            referenceTrackerPointer = referenceTrackerPointer,
            isAggregated = isAggregated,
        ),
    )

}

fun acquireInterfaceReference(instance: ComObjectReference, iid: Guid): IUnknownReference =
    instance.queryInterface(iid).getOrThrow().use { reference ->
        IUnknownReference(reference.getRefPointer(), iid)
    }

class ActivationFactoryReference internal constructor(
    comPtr: ComPtr,
) : IUnknownReference(comPtr) {
    constructor(
        pointer: RawComPtr,
        interfaceId: Guid = IID.IActivationFactory,
    ) : this(ComPtr.create(pointer, interfaceId))

    internal override val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.ActivationFactory

    internal fun asTypedView(): IActivationFactoryView = IActivationFactoryView(comPtr)

    fun activateInstance(): IInspectableReference =
        asTypedView().activateInstance()
}

class InspectableReference internal constructor(
    comPtr: ComPtr,
) : ComObjectReference(comPtr), IWinRTObject {
    constructor(
        pointer: RawComPtr,
        interfaceId: Guid = IID.IInspectable,
        preventReleaseOnDispose: Boolean = false,
        isAggregated: Boolean = false,
    ) : this(
        ComPtr.create(
            raw = pointer,
            interfaceId = interfaceId,
            ownershipMode =
                if (preventReleaseOnDispose) {
                    ComOwnershipMode.Borrowed
                } else {
                    ComOwnershipMode.Owned
                },
            isAggregated = isAggregated,
        ),
    )

    internal override val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.Inspectable

    override val nativeObject: ComObjectReference
        get() = this

    internal fun asTypedView(): IInspectableView = IInspectableView(comPtr)

    fun tryGetRuntimeClassName(): String? = asTypedView().tryGetRuntimeClassName()

    fun getRuntimeClassName(noThrow: Boolean = false): String? =
        asTypedView().getRuntimeClassName(noThrow)
}

typealias IInspectableReference = InspectableReference
