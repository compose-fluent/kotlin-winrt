package io.github.composefluent.winrt.runtime

internal enum class ComOwnershipMode {
    Owned,
    Borrowed,
}

@PublishedApi
internal class ComPtr private constructor(
    referenceTrackerPointer: RawComPtr,
    @PublishedApi internal val support: RawComObjectReferenceSupport,
) : AutoCloseable {
    @PublishedApi
    internal val raw: RawComPtr
        get() = support.pointerForCurrentContext()

    @Suppress("unused")
    private val finalizationRegistration = createComPtrFinalizationRegistration(
        target = this,
        support = support,
    )

    init {
        if (!PlatformAbi.isNull(referenceTrackerPointer)) {
            support.attachReferenceTracker(
                trackerPointer = referenceTrackerPointer,
                addRefForObjectReference = false,
                releaseTrackerSourceOnDispose = true,
                retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
                addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
            )
        }
    }

    val pointer: RawComPtr
        get() = raw

    val interfaceId: Guid
        get() = support.interfaceId

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

    /** Mirrors CsWinRT's IObjectReference.AsKnownPtr ownership transfer. */
    internal fun attachKnownPointer(
        pointer: RawComPtr,
        interfaceId: Guid = IID.IUnknown,
    ): ComPtr {
        throwIfDisposed()
        require(!PlatformAbi.isNull(pointer)) {
            "Known COM object reference cannot wrap a null pointer."
        }

        addRef()
        return try {
            create(
                raw = pointer,
                interfaceId = interfaceId,
                ownershipMode =
                    if (isAggregated) {
                        ComOwnershipMode.Borrowed
                    } else {
                        ComOwnershipMode.Owned
                    },
                referenceTrackerPointer = referenceTrackerHandle,
                isAggregated = isAggregated,
            )
        } catch (error: Throwable) {
            release()
            throw error
        }
    }

    fun tryQueryInterface(requestedInterfaceId: Guid): ComPtr? =
        support.tryQueryInterface(
            requestedInterfaceId,
            ::invokeReferenceTrackerAddRefOnPointer,
            ::wrapQueriedReference,
        )

    fun queryInterface(requestedInterfaceId: Guid): Result<ComPtr> =
        support.queryInterface(
            requestedInterfaceId,
            ::invokeReferenceTrackerAddRefOnPointer,
            ::wrapQueriedReference,
        )

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
            trackContext: Boolean = true,
            managedCcwReleaseIdentity: RawAddress = RawAddress.Null,
        ): ComPtr = create(
            raw = raw,
            interfaceIdLowBits = interfaceId.abiLowBits,
            interfaceIdHighBits = interfaceId.abiHighBits,
            knownInterfaceId = interfaceId,
            ownershipMode = ownershipMode,
            referenceTrackerPointer = referenceTrackerPointer,
            isAggregated = isAggregated,
            trackContext = trackContext,
            managedCcwReleaseIdentity = managedCcwReleaseIdentity,
        )

        internal fun create(
            raw: RawComPtr,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            ownershipMode: ComOwnershipMode = ComOwnershipMode.Owned,
            referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
            isAggregated: Boolean = false,
            trackContext: Boolean = true,
            managedCcwReleaseIdentity: RawAddress = RawAddress.Null,
        ): ComPtr = create(
            raw = raw,
            interfaceIdLowBits = interfaceIdLowBits,
            interfaceIdHighBits = interfaceIdHighBits,
            knownInterfaceId = null,
            ownershipMode = ownershipMode,
            referenceTrackerPointer = referenceTrackerPointer,
            isAggregated = isAggregated,
            trackContext = trackContext,
            managedCcwReleaseIdentity = managedCcwReleaseIdentity,
        )

        private fun create(
            raw: RawComPtr,
            interfaceIdLowBits: Long,
            interfaceIdHighBits: Long,
            knownInterfaceId: Guid?,
            ownershipMode: ComOwnershipMode,
            referenceTrackerPointer: RawComPtr,
            isAggregated: Boolean,
            trackContext: Boolean,
            managedCcwReleaseIdentity: RawAddress,
        ): ComPtr {
            require(!PlatformAbi.isNull(raw)) {
                "COM object reference cannot wrap a null pointer."
            }
            val support = RawComObjectReferenceSupport(
                pointer = raw,
                interfaceIdLowBits = interfaceIdLowBits,
                interfaceIdHighBits = interfaceIdHighBits,
                knownInterfaceId = knownInterfaceId,
                preventReleaseOnDispose = ownershipMode == ComOwnershipMode.Borrowed,
                isAggregated = isAggregated,
                trackContext = trackContext,
                managedCcwReleaseIdentity = managedCcwReleaseIdentity,
            )
            return ComPtr(
                referenceTrackerPointer = referenceTrackerPointer,
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

internal fun closeComPtrSupportFromFinalizer(support: RawComObjectReferenceSupport) {
    support.close(
        releaseFromTrackerSourceCallback = ::invokeReferenceTrackerReleaseOnPointer,
        releaseTrackerPointer = ::invokeIUnknownReleaseOnPointer,
        deferContextRelease = true,
    )
}

private fun invokeIUnknownAddRefOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = IUnknownVftblSlots.AddRef,
    ).toUInt()

internal fun retainBorrowedComPointer(targetPointer: RawComPtr): RawComPtr {
    invokeIUnknownAddRefOnPointer(targetPointer)
    return targetPointer
}

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
