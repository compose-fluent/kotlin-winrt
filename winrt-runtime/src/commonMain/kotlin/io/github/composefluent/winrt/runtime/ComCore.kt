package io.github.composefluent.winrt.runtime

internal enum class ComOwnershipMode {
    Owned,
    Borrowed,
}

@PublishedApi
internal class ComPtr private constructor(
    val raw: RawComPtr,
    val interfaceId: Guid,
    val ownershipMode: ComOwnershipMode,
    referenceTrackerPointer: RawComPtr,
    isAggregated: Boolean,
    @PublishedApi internal val support: RawComObjectReferenceSupport,
) : AutoCloseable {
    @Suppress("unused")
    private val finalizationRegistration = createComPtrFinalizationRegistration(
        target = this,
        support = support,
    )

    init {
        if (!PlatformAbi.isNull(referenceTrackerPointer)) {
            support.attachReferenceTracker(
                trackerPointer = referenceTrackerPointer,
                addRefFromTrackerSource = true,
                retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
                addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
            )
        }
    }

    val pointer: RawComPtr
        get() = raw

    /** Combines the lifetime check and raw-pointer load for generated hot call sites. */
    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun checkedPointer(): RawComPtr {
        if (support.isDisposed) {
            throw WinRTObjectDisposedException("Object reference is disposed.")
        }
        return raw
    }

    val isDisposed: Boolean
        get() = support.isDisposed

    val hasReferenceTracker: Boolean
        get() = support.hasReferenceTracker

    val isAggregated: Boolean
        get() = support.isAggregated

    internal val referenceTrackerHandle: RawComPtr
        get() = support.referenceTrackerHandle

    fun addRef(): UInt =
        support.addRef(::invokeReferenceTrackerAddRefOnPointer)

    fun release(): UInt =
        support.release(::invokeReferenceTrackerReleaseOnPointer)

    fun getRefPointer(): RawComPtr = support.getRef()

    fun tryQueryInterface(requestedInterfaceId: Guid): ComPtr? =
        support.tryQueryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun queryInterface(requestedInterfaceId: Guid): Result<ComPtr> =
        support.queryInterface(requestedInterfaceId, ::wrapQueriedReference)

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        support.tryInitializeReferenceTracker(
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
            addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
        )

    fun sameIdentity(other: ComPtr): Boolean = support.sameIdentity(other.support)

    fun invokeGeneric(
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeGeneric(raw, slot, signature, args)
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun throwIfDisposed() {
        if (isDisposed) {
            throw WinRTObjectDisposedException("Object reference is disposed.")
        }
    }

    override fun close() {
        closeComPtrFinalizationRegistration(finalizationRegistration, support)
    }

    private fun wrapQueriedReference(
        queriedPointer: RawComPtr,
        queriedInterfaceId: Guid,
        trackerHandle: RawComPtr,
        queriedPreventReleaseOnDispose: Boolean,
        queriedIsAggregated: Boolean,
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
            isAggregated = queriedIsAggregated,
        )

    companion object {
        fun create(
            raw: RawComPtr,
            interfaceId: Guid,
            ownershipMode: ComOwnershipMode = ComOwnershipMode.Owned,
            referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
            isAggregated: Boolean = false,
        ): ComPtr {
            require(!PlatformAbi.isNull(raw)) {
                "COM object reference cannot wrap a null pointer."
            }
            val support = RawComObjectReferenceSupport(
                pointer = raw,
                interfaceId = interfaceId,
                preventReleaseOnDispose = ownershipMode == ComOwnershipMode.Borrowed,
                isAggregated = isAggregated,
            )
            return ComPtr(
                raw = raw,
                interfaceId = interfaceId,
                ownershipMode = ownershipMode,
                referenceTrackerPointer = referenceTrackerPointer,
                isAggregated = isAggregated,
                support = support,
            )
        }

    }
}

internal fun closeComPtrSupport(support: RawComObjectReferenceSupport) {
    support.close(
        releaseFromTrackerSourceCallback = ::invokeReferenceTrackerReleaseOnPointer,
        releaseTrackerPointer = ::invokeIUnknownReleaseOnPointer,
    )
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
