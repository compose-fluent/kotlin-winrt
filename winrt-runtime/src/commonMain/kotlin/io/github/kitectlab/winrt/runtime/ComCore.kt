package io.github.kitectlab.winrt.runtime

internal enum class ComOwnershipMode {
    Owned,
    Borrowed,
}

internal class ComIdentity internal constructor(
    private val support: RawComObjectReferenceSupport,
) {
    val hasReferenceTracker: Boolean
        get() = support.hasReferenceTracker

    internal val referenceTrackerHandle: RawComPtr
        get() = support.referenceTrackerHandle

    fun sameIdentity(other: ComIdentity): Boolean = support.sameIdentity(other.support)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        support.tryInitializeReferenceTracker(
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
            addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
        )
}

internal class ComPtr private constructor(
    val raw: RawComPtr,
    val interfaceId: Guid,
    val ownershipMode: ComOwnershipMode,
    private val support: RawComObjectReferenceSupport,
) : AutoCloseable {
    val pointer: RawComPtr
        get() = raw

    val identity: ComIdentity = ComIdentity(support)

    val isDisposed: Boolean
        get() = support.isDisposed

    val hasReferenceTracker: Boolean
        get() = identity.hasReferenceTracker

    internal val referenceTrackerHandle: RawComPtr
        get() = identity.referenceTrackerHandle

    fun addRef(): UInt =
        support.addRef(::invokeReferenceTrackerAddRefOnPointer)

    fun release(): UInt =
        support.release(::invokeReferenceTrackerReleaseOnPointer)

    fun getRefPointer(): RawComPtr = support.getRef(::invokeReferenceTrackerAddRefOnPointer)

    fun tryQueryInterface(requestedInterfaceId: Guid): ComPtr? =
        support.tryQueryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComPtr> =
        support.queryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        identity.tryInitializeReferenceTracker(addRefFromTrackerSource)

    fun sameIdentity(other: ComPtr): Boolean = identity.sameIdentity(other.identity)

    fun invokeGeneric(
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeGeneric(raw, slot, signature, args)
    }

    fun throwIfDisposed() {
        support.throwIfDisposed()
    }

    override fun close() {
        support.close(
            releaseFromTrackerSourceCallback = ::invokeReferenceTrackerReleaseOnPointer,
            releaseTrackerPointer = ::invokeIUnknownReleaseOnPointer,
        )
    }

    private fun wrapQueriedReference(
        queriedPointer: RawComPtr,
        queriedInterfaceId: Guid,
        trackerHandle: RawComPtr,
        queriedPreventReleaseOnDispose: Boolean,
    ): ComPtr =
        create(
            raw = queriedPointer,
            interfaceId = queriedInterfaceId,
            ownershipMode =
                if (queriedPreventReleaseOnDispose) {
                    ComOwnershipMode.Borrowed
                } else {
                    ComOwnershipMode.Owned
                },
            referenceTrackerPointer = trackerHandle,
        )

    companion object {
        fun create(
            raw: RawComPtr,
            interfaceId: Guid,
            ownershipMode: ComOwnershipMode = ComOwnershipMode.Owned,
            referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
        ): ComPtr {
            require(!PlatformAbi.isNull(raw)) {
                "COM object reference cannot wrap a null pointer."
            }
            val support = RawComObjectReferenceSupport(raw, interfaceId, ownershipMode == ComOwnershipMode.Borrowed)
            if (!PlatformAbi.isNull(referenceTrackerPointer)) {
                support.attachReferenceTracker(
                    trackerPointer = referenceTrackerPointer,
                    addRefFromTrackerSource = true,
                    retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
                    addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
                )
            }
            return ComPtr(raw, interfaceId, ownershipMode, support)
        }
    }
}

private fun invokeIUnknownAddRefOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = IUnknownVftblSlots.AddRef,
    ).toUInt()

private fun invokeIUnknownReleaseOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = IUnknownVftblSlots.Release,
    ).toUInt()

private fun invokeReferenceTrackerAddRefOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = ReferenceTrackerVftblSlots.AddRefFromTrackerSource,
    ).toUInt()

private fun invokeReferenceTrackerReleaseOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = ReferenceTrackerVftblSlots.ReleaseFromTrackerSource,
    ).toUInt()
