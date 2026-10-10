package io.github.composefluent.winrt.runtime

open class ComObjectReference internal constructor(
    @PublishedApi
    internal val comPtr: ComPtr,
) : AutoCloseable {
    private var ownedInterfaces: MutableList<ComObjectReference>? = null
    private var closing = false

    @kotlin.concurrent.Volatile
    private var objectState: WinRTObjectState<ComObjectReference>? = null

    internal val projectedObjectState: WinRTObjectState<ComObjectReference>
        get() = objectState ?: WinRTObjectStateInitialization.lock.withLock {
            objectState ?: WinRTObjectState<ComObjectReference>().also { objectState = it }
        }

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
        inline get() = comPtr.checkedPointer()

    val interfaceId: Guid
        get() = comPtr.interfaceId

    val isDisposed: Boolean
        get() = comPtr.isDisposed

    /**
     * Whether a call through this reference runs on the calling thread instead of entering the
     * apartment that the reference was created in.
     */
    internal val isCallableInCurrentContext: Boolean
        get() = comPtr.isCallableInCurrentContext

    val hasReferenceTracker: Boolean
        get() = comPtr.hasReferenceTracker

    internal val isAggregated: Boolean
        get() = comPtr.isAggregated

    internal open val wrapperKind: ComReferenceWrapperKind
        get() = ComReferenceWrapperKind.Unknown

    internal val referenceTrackerHandle: RawComPtr
        get() = comPtr.referenceTrackerHandle

    fun addRef(): UInt =
        comPtr.addRef()

    fun release(): UInt =
        comPtr.release()

    fun getRefPointer(): RawComPtr =
        comPtr.getRefPointer()

    open fun tryQueryInterface(requestedInterfaceId: Guid): ComObjectReference? =
        comPtr.tryQueryInterface(requestedInterfaceId)?.let(::wrapQueriedReference)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        comPtr.tryInitializeReferenceTracker(addRefFromTrackerSource)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComObjectReference> =
        comPtr.queryInterface(requestedInterfaceId).map(::wrapQueriedReference)

    fun getDefaultInterfaceObjectReference(vtableSlot: Int): IUnknownReference {
        throwIfDisposed()
        val pointer = ComVtableInvoker.invokePointer(comPtr.raw, vtableSlot)
        if (PlatformAbi.isNull(pointer)) {
            throw WinRTUnsupportedOperationException(
                "Fast ABI default-interface object reference returned a null pointer from vtable slot $vtableSlot",
                KnownHResults.E_POINTER,
            )
        }
        return IUnknownReference(comPtr.attachKnownPointer(pointer.asRawComPtr()))
    }

    fun tryAsInspectable(): IInspectableReference? =
        comPtr.tryQueryInterface(IID.IInspectable)?.let(::InspectableReference)

    fun asInspectable(): IInspectableReference =
        tryAsInspectable()
            ?: throw WinRTUnsupportedOperationException(
                "QueryInterface failed for ${IID.IInspectable} with ${KnownHResults.E_NOINTERFACE}",
                KnownHResults.E_NOINTERFACE,
            )

    fun sameIdentity(other: ComObjectReference): Boolean =
        comPtr.sameIdentity(other.comPtr)

    override fun close() {
        val children = ownedInterfacesLock.withLock {
            if (closing) return
            closing = true
            ownedInterfaces.also { ownedInterfaces = null }
        }
        var failure: Throwable? = null
        fun closeOwned(reference: AutoCloseable) {
            try {
                reference.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
            }
        }
        children?.forEach(::closeOwned)
        closeOwned(comPtr)
        failure?.let { throw it }
    }

    /**
     * CsWinRT keeps lazy QI references in the RCW's managed graph (IInspectable/IWinRTObject).
     * Kotlin additionally exposes deterministic close on its native owner. References used by
     * those lazy interface views must follow that owner on both JVM and Native.
     * Independent queryInterface results keep their existing independent lifetime.
     */
    internal fun <T : ComObjectReference> ownInterfaceReference(reference: T): T {
        val accepted = ownedInterfacesLock.withLock {
            if (closing || comPtr.isDisposed) {
                false
            } else {
                (ownedInterfaces ?: mutableListOf<ComObjectReference>().also { ownedInterfaces = it }).add(reference)
                true
            }
        }
        if (!accepted) {
            val failure = WinRTObjectDisposedException("Object reference is disposed.")
            try {
                reference.close()
            } catch (error: Throwable) {
                failure.addSuppressed(error)
            }
            throw failure
        }
        return reference
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

    private companion object {
        // Protect only registration/detachment; native Release runs outside the lock.
        // A shared lock avoids allocating an OS lock for each projected pointer wrapper.
        val ownedInterfacesLock = PlatformLock()
    }
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
    instance.ownInterfaceReference(IUnknownReference(instance.comPtr.queryInterface(iid).getOrThrow()))

/**
 * Transfers a wrapper constructor's owned reference to its declared WinRT interface.
 * CsWinRT code_writers.h write_class uses objRef.As(defaultInterface.IID)
 * before storing _inner; IInspectable identity need not have that interface's vtable.
 */
fun castOwnedInspectableReference(instance: IInspectableReference, iid: Guid): IInspectableReference {
    if (instance.interfaceId == iid) return instance
    return try {
        InspectableReference(instance.comPtr.queryInterface(iid).getOrThrow())
    } finally {
        instance.close()
    }
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
        ActivationFactoryReferenceSupport.activateInstance(comPtr)

    fun activateInstance(interfaceId: Guid): IInspectableReference =
        ActivationFactoryReferenceSupport.activateInstance(comPtr, interfaceId)
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
