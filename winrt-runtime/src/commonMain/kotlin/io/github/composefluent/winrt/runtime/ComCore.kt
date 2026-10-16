package io.github.composefluent.winrt.runtime

internal enum class ComOwnershipMode {
    Owned,
    Borrowed,
}

/**
 * CsWinRT 3.0 WindowsRuntimeUnknownMarshaller.Free: consume an owned ABI reference
 * in the caller's COM context. This is synchronous ABI cleanup, not RCW disposal;
 * it must not create a new context-aware wrapper or a Cleaner registration.
 */
internal fun disposeComAbi(pointer: RawAddress) {
    if (!PlatformAbi.isNull(pointer)) {
        WinRTPlatformApi.releaseRaw(pointer)
    }
}

@PublishedApi
internal class ComPtr private constructor(
    referenceTrackerPointer: RawComPtr,
    @PublishedApi internal val support: RawComObjectReferenceSupport,
) : AutoCloseable {
    // Own the native-to-managed edges on the RCW itself. Putting these on `support`
    // would root them through its cleaner and make a cross-heap cycle permanent.
    @kotlin.concurrent.Volatile
    private var trackerSource: ReferenceTrackerSource? = null

    internal fun getOrCreateTrackerSource(): ReferenceTrackerSource {
        trackerSource?.let { return it }
        // Publish exactly one Kotlin dependent-handle source. The short initialization lock
        // only creates managed graph state; COM attach and Dispose keep their existing contract.
        return ComPtrTrackerSourceInitialization.lock.withLock {
            trackerSource ?: ReferenceTrackerSource().also { trackerSource = it }
        }
    }

    @Suppress("unused")
    private val finalizationRegistration = createComPtrFinalizationRegistration(
        target = this,
        support = support,
    )

    init {
        if (!PlatformAbi.isNull(referenceTrackerPointer)) {
            support.attachReferenceTracker(
                trackerPointer = referenceTrackerPointer,
                trackerSource = getOrCreateTrackerSource(),
                addRefForObjectReference = false,
                releaseTrackerSourceOnDispose = true,
                retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
                addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
            )
        }
    }

    val interfaceId: Guid
        get() = support.interfaceId

    /** Checks lifetime and resolves a borrowed interface address in the current COM context. */
    @Suppress("NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun resolvePointer(): RawComPtr {
        if (support.isDisposed) {
            throw WinRTObjectDisposedException("Object reference is disposed.")
        }
        return support.pointerForCurrentContext()
    }

    val isDisposed: Boolean
        get() = support.isDisposed

    internal inline fun <T> withCallPointer(block: (RawComPtr) -> T): T =
        try {
            support.withCall(block)
        } finally {
            winRTKeepAlive(this)
        }

    internal val isCallableInCurrentContext: Boolean
        get() = support.isCallableInCurrentContext

    val hasReferenceTracker: Boolean
        get() = support.hasReferenceTracker

    val isAggregated: Boolean
        get() = support.isAggregated

    internal val referenceTrackerHandle: RawComPtr
        get() = support.referenceTrackerHandle

    fun acquireOwnedPointer(): RawComPtr = try {
        support.acquireOwnedPointer()
    } finally {
        winRTKeepAlive(this)
    }

    /** Mirrors CsWinRT's IObjectReference.AsKnownPtr ownership transfer. */
    internal fun attachKnownPointer(
        pointer: RawComPtr,
        interfaceId: Guid = IID.IUnknown,
    ): ComPtr = withCallPointer { activePointer ->
        attachKnownPointerForActiveCall(pointer, activePointer, interfaceId)
    }

    /** Caller holds one uninterrupted lease from pointer acquisition through ownership transfer. */
    internal fun attachKnownPointerForActiveCall(
        pointer: RawComPtr,
        activePointer: RawComPtr,
        interfaceId: Guid = IID.IUnknown,
    ): ComPtr {
        require(!PlatformAbi.isNull(pointer)) {
            "Known COM object reference cannot wrap a null pointer."
        }

        val tracker = referenceTrackerHandle
        WinRTPlatformApi.addRefRaw(activePointer.asRawAddress())
        if (!PlatformAbi.isNull(tracker)) invokeReferenceTrackerAddRefOnPointer(tracker)
        try {
            return create(
                raw = pointer,
                interfaceId = interfaceId,
                ownershipMode =
                    if (isAggregated) {
                        ComOwnershipMode.Borrowed
                    } else {
                        ComOwnershipMode.Owned
                    },
                referenceTrackerPointer = tracker,
                isAggregated = isAggregated,
                nativeObjectLifetime = support.nativeObjectLifetime,
                knownFreeThreaded = support.callsAreFreeThreaded,
            )
        } catch (error: Throwable) {
            if (!PlatformAbi.isNull(tracker)) invokeReferenceTrackerReleaseOnPointer(tracker)
            WinRTPlatformApi.releaseRaw(activePointer.asRawAddress())
            throw error
        }
    }

    fun tryQueryInterface(requestedInterfaceId: Guid): ComPtr? =
        try {
            support.tryQueryInterface(
                requestedInterfaceId,
                ::invokeReferenceTrackerAddRefOnPointer,
                ::wrapQueriedReference,
            )
        } finally {
            winRTKeepAlive(this)
        }

    fun queryInterface(requestedInterfaceId: Guid): Result<ComPtr> =
        try {
            support.queryInterface(
                requestedInterfaceId,
                ::invokeReferenceTrackerAddRefOnPointer,
                ::wrapQueriedReference,
            )
        } finally {
            winRTKeepAlive(this)
        }

    /**
     * Scoped counterpart of CsWinRT IObjectReference.AsValue(Guid)/ObjectReferenceValue.Dispose.
     * The pointer is valid only during [block], in the caller's current COM context. Keep the
     * parent (and its tracker registration) alive instead of registering a temporary RCW.
     * Like EventSourceCache.Create's TryAs in CsWinRT, a failed optional QI returns null;
     * exceptions from the caller's block still propagate after releasing the reference.
     */
    internal fun <T> tryWithQueryInterfacePointer(interfaceId: Guid, block: (RawComPtr) -> T): T? {
        return withCallPointer { activePointer ->
            try {
                val result = WinRTPlatformApi.queryInterfaceRaw(activePointer.asRawAddress(), interfaceId)
                if (result.hResultValue != KnownHResults.S_OK.value) {
                    return null
                }
                if (PlatformAbi.isNull(result.pointer)) {
                    return null
                }
                val aggregated = isAggregated
                if (aggregated) {
                    WinRTPlatformApi.releaseRaw(result.pointer)
                }
                val tracker = referenceTrackerHandle
                var trackerSourceAdded = false
                try {
                    if (!PlatformAbi.isNull(tracker)) {
                        invokeReferenceTrackerAddRefOnPointer(tracker)
                        trackerSourceAdded = true
                    }
                    return block(result.pointer.asRawComPtr())
                } finally {
                    try {
                        if (trackerSourceAdded) {
                            invokeReferenceTrackerReleaseOnPointer(tracker)
                        }
                    } finally {
                        if (!aggregated) {
                            WinRTPlatformApi.releaseRaw(result.pointer)
                        }
                    }
                }
            } finally {
                winRTKeepAlive(this)
            }
        }
    }

    /**
     * CsWinRT CreateMarshaler2/AsValue(Guid) for an explicitly free-threaded, untracked parent.
     * Keep a single GC registration for public marshalers that are abandoned without close.
     * Non-agile, tracked, aggregated, or context-unclassified references keep their ComPtr path.
     */
    internal fun tryAcquireScopedQueryInterfaceLease(interfaceId: Guid): AbiReferenceLease<AutoCloseable>? {
        return withCallPointer { activePointer ->
            if (!support.canUseScopedQueryInterfaceLease) return null
            try {
                val result = WinRTPlatformApi.queryInterfaceRaw(activePointer.asRawAddress(), interfaceId)
                val scopedPointer = result.pointer
                if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(scopedPointer)) {
                    throw WinRTUnsupportedOperationException(
                        "QueryInterface failed for $interfaceId with " + KnownHResults.E_NOINTERFACE,
                        KnownHResults.E_NOINTERFACE,
                    )
                }
                WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
                var cleanup: ScopedComReferenceCleanup? = null
                var transferred = false
                try {
                    val releaseState = ScopedComReferenceCleanup(scopedPointer)
                    cleanup = releaseState
                    var registration: AutoCloseable? = null
                    val lease = AbiReferenceLeaseSupport.create<AutoCloseable>(
                        abi = scopedPointer,
                        cleanup = {
                            try {
                                registration?.close() ?: releaseState.close()
                            } finally {
                                winRTKeepAlive(this)
                            }
                        },
                    )
                    // The Cleaner action owns only releaseState, never its lease target/registration.
                    registration = scopedReferenceFinalizationHook.create(releaseState::close)
                    transferred = true
                    return lease
                } finally {
                    if (!transferred) {
                        cleanup?.close() ?: WinRTPlatformApi.releaseRaw(scopedPointer)
                    }
                }
            } finally {
                winRTKeepAlive(this)
            }
        }
    }

    fun tryInitializeReferenceTracker(addRefFromTrackerSource: Boolean = true): Boolean =
        try {
            // CsWinRT ComWrappersHelper.Init creates tracker state only after the tracker QI
            // succeeds. Passing this owner temporarily preserves its Kotlin graph source.
            support.tryInitializeReferenceTracker(
                trackerSourceOwner = this,
                addRefFromTrackerSource = addRefFromTrackerSource,
                retainTrackerPointer = ::invokeIUnknownAddRefOnPointer,
                addRefFromTrackerSourceCallback = ::invokeReferenceTrackerAddRefOnPointer,
            )
        } finally {
            winRTKeepAlive(this)
        }

    fun sameIdentity(other: ComPtr): Boolean = support.sameIdentity(other.support)

    fun invokeGeneric(
        slot: Int,
        signature: ComMethodSignature,
        args: LongArray,
    ): Int {
        return this.withCallPointer { activePointer -> ComVtableInvoker.invokeGeneric(activePointer, slot, signature, args) }
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
            nativeObjectLifetime = support.nativeObjectLifetime,
            knownFreeThreaded = support.callsAreFreeThreaded,
        )

    companion object {
        // FinalizationHook owns a JVM Cleaner; share it across all scoped ABI leases.
        private val scopedReferenceFinalizationHook = FinalizationHook()

        fun create(
            raw: RawComPtr,
            interfaceId: Guid,
            ownershipMode: ComOwnershipMode = ComOwnershipMode.Owned,
            referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr,
            isAggregated: Boolean = false,
            trackContext: Boolean = true,
            managedCcwReleaseIdentity: RawAddress = RawAddress.Null,
            nativeObjectLifetime: WinRTNativeObjectLifetime? = null,
            knownFreeThreaded: Boolean = false,
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
            nativeObjectLifetime = nativeObjectLifetime,
            knownFreeThreaded = knownFreeThreaded,
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
            nativeObjectLifetime: WinRTNativeObjectLifetime? = null,
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
            nativeObjectLifetime = nativeObjectLifetime,
        )

        /**
         * Consumes one owned ABI reference on success or construction failure.
         * Unlike create(), callers must not release the input again after an exception.
         * This is the runtime's explicit counterpart of CsWinRT Attach plus caller cleanup.
         */
        internal fun createForOwnedAbi(
            raw: RawComPtr,
            interfaceId: Guid,
            marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
        ): ComPtr = create(
            raw = raw,
            interfaceIdLowBits = interfaceId.abiLowBits,
            interfaceIdHighBits = interfaceId.abiHighBits,
            knownInterfaceId = interfaceId,
            ownershipMode = ComOwnershipMode.Owned,
            referenceTrackerPointer = PlatformAbi.nullComPtr,
            isAggregated = false,
            trackContext = true,
            managedCcwReleaseIdentity = RawAddress.Null,
            nativeObjectLifetime = null,
            consumeOnFailure = true,
            marshalingType = marshalingType,
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
            nativeObjectLifetime: WinRTNativeObjectLifetime?,
            consumeOnFailure: Boolean = false,
            knownFreeThreaded: Boolean = false,
            marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
        ): ComPtr {
            require(!PlatformAbi.isNull(raw)) {
                "COM object reference cannot wrap a null pointer."
            }
            var createdSupport: RawComObjectReferenceSupport? = null
            try {
                val support = RawComObjectReferenceSupport(
                    pointer = raw,
                    interfaceIdLowBits = interfaceIdLowBits,
                    interfaceIdHighBits = interfaceIdHighBits,
                    knownInterfaceId = knownInterfaceId,
                    preventReleaseOnDispose = ownershipMode == ComOwnershipMode.Borrowed,
                    isAggregated = isAggregated,
                    trackContext = trackContext,
                    managedCcwReleaseIdentity = managedCcwReleaseIdentity,
                    nativeObjectLifetime = nativeObjectLifetime,
                    knownFreeThreaded = knownFreeThreaded,
                    marshalingType = marshalingType,
                )
                createdSupport = support
                return ComPtr(
                    referenceTrackerPointer = referenceTrackerPointer,
                    support = support,
                )
            } catch (error: Throwable) {
                if (consumeOnFailure || nativeObjectLifetime != null) {
                    try {
                        val support = createdSupport
                        if (support == null) {
                            // Construction has not published a support/cleaner owner yet.
                            if (consumeOnFailure) WinRTPlatformApi.releaseRaw(raw.asRawAddress())
                        } else {
                            // The constructor may already have registered its cleaner. Both
                            // paths share support's dispose-once guard, so do not raw-Release.
                            closeComPtrSupport(support)
                        }
                    } catch (cleanupError: Throwable) {
                        error.addSuppressed(cleanupError)
                    }
                }
                throw error
            }
        }
    }
}

private object ComPtrTrackerSourceInitialization {
    val lock = PlatformLock()
}

/** Cleaner state holds only its owned ABI pointer, never the parent RCW or a managed source. */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
private class ScopedComReferenceCleanup(
    private val pointer: RawAddress,
) : AutoCloseable {
    private val closed = kotlin.concurrent.atomics.AtomicInt(0)

    override fun close() {
        // Also guards a register() failure after a Cleaner was already created.
        if (!closed.compareAndSet(0, 1)) return
        // The owned QI +1 alone keeps the native object alive until this release runs.
        val release: () -> Unit = { WinRTPlatformApi.releaseRaw(pointer) }
        // Preserve the existing ComPtr cleanup rule during a XAML reference-tracker walk.
        if (!ReferenceTrackerManager.deferFinalizerRelease(release)) {
            release()
        }
    }
}

internal fun closeComPtrSupport(support: RawComObjectReferenceSupport) {
    support.close()
}

internal fun closeComPtrSupportFromFinalizer(support: RawComObjectReferenceSupport) {
    if (ReferenceTrackerManager.deferFinalizerRelease { closeComPtrSupportFromFinalizer(support) }) {
        return
    }
    if (WaitedFinalizerReleases.defer { closeComPtrSupportFromFinalizer(support) }) {
        return
    }
    support.close(deferContextRelease = true)
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

internal fun invokeIUnknownReleaseOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = IUnknownVftblSlots.Release,
    ).toUInt()

private fun invokeReferenceTrackerAddRefOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = ReferenceTrackerVftblSlots.AddRefFromTrackerSource,
    ).toUInt()

internal fun invokeReferenceTrackerReleaseOnPointer(targetPointer: RawComPtr): UInt =
    ComVtableInvoker.invoke(
        instance = targetPointer,
        slot = ReferenceTrackerVftblSlots.ReleaseFromTrackerSource,
    ).toUInt()
