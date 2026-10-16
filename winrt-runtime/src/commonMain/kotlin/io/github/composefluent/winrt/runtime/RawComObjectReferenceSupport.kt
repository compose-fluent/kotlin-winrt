package io.github.composefluent.winrt.runtime

import kotlinx.atomicfu.atomic

@PublishedApi
internal class RawComObjectReferenceSupport(
    private val pointer: RawComPtr,
    private val interfaceIdLowBits: Long,
    private val interfaceIdHighBits: Long,
    private val knownInterfaceId: Guid?,
    private val preventReleaseOnDispose: Boolean = false,
    val isAggregated: Boolean = false,
    trackContext: Boolean = true,
    internal val managedCcwReleaseIdentity: RawAddress = RawAddress.Null,
    internal val nativeObjectLifetime: WinRTNativeObjectLifetime? = null,
    private val afterRelease: (() -> Unit)? = null,
    knownFreeThreaded: Boolean = false,
    marshalingType: WinRTObjectReferenceMarshalingType = WinRTObjectReferenceMarshalingType.Unknown,
) {
    // CsWinRT 3 WindowsRuntimeObjectReference.Lifecycle: close bit + active calls.
    // The next bit preserves finalizer context routing until the last call returns.
    private val lifetime = atomic(0)

    // Keep the atomic container private so AtomicFU can lower it to a field.
    // Published inline call leases expose only primitive operations across modules.
    @PublishedApi
    internal fun loadLifetime(): Int = lifetime.value

    @PublishedApi
    internal fun compareAndSetLifetime(expected: Int, updated: Int): Boolean = lifetime.compareAndSet(expected, updated)

    @PublishedApi
    internal fun decrementLifetime(): Int = lifetime.addAndGet(-1)
    private var referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr
    private var referenceTrackerRegistrationKey: Long = 0L
    private var releaseTrackerSourceOnDispose: Boolean = false
    private var ownsTrackerPointer = true
    // Retain the existing agility/FTM probe result; a null context alone does not prove agility.
    // CsWinRT 3.0 FreeThreadedObjectReference.DerivedTryAsNative preserves agility
    // across QI on the same object. Initialization uses known class metadata first;
    // unknown ABI pointers must still be probed.
    internal val callsAreFreeThreaded = trackContext &&
        (knownFreeThreaded || when (marshalingType) {
            WinRTObjectReferenceMarshalingType.Agile -> true
            WinRTObjectReferenceMarshalingType.Standard -> false
            WinRTObjectReferenceMarshalingType.Unknown -> ComThreadingSupport.isFreeThreaded(pointer)
        })
    private var objectContext =
        if (trackContext) {
            ObjectReferenceContext.capture(
                pointer = pointer,
                interfaceIdLowBits = interfaceIdLowBits,
                interfaceIdHighBits = interfaceIdHighBits,
                knownInterfaceId = knownInterfaceId,
                callsAreFreeThreaded = callsAreFreeThreaded,
            )
        } else {
            null
        }
    private val nativeLifetimeLease = nativeObjectLifetime?.retain()

    internal val canUseScopedQueryInterfaceLease: Boolean
        get() = callsAreFreeThreaded && objectContext == null && !hasReferenceTracker && !isAggregated

    val interfaceId: Guid
        get() = knownInterfaceId ?: Guid.fromAbiWords(interfaceIdLowBits, interfaceIdHighBits)

    val isDisposed: Boolean
        get() = lifetime.value < 0

    val hasReferenceTracker: Boolean
        get() = !PlatformAbi.isNull(referenceTrackerPointer)

    val referenceTrackerHandle: RawComPtr
        get() = referenceTrackerPointer

    fun pointerForCurrentContext(): RawComPtr =
        objectContext?.pointerForCurrentContext() ?: pointer

    /** See [ObjectReferenceContext.isCallableInCurrentContext]. */
    val isCallableInCurrentContext: Boolean
        get() = objectContext?.isCallableInCurrentContext() ?: true

    fun attachReferenceTracker(
        trackerPointer: RawComPtr,
        trackerSource: ReferenceTrackerSource,
        addRefForObjectReference: Boolean,
        releaseTrackerSourceOnDispose: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
        ownsTrackerPointer: Boolean = true,
    ) {
        if (hasReferenceTracker) {
            return
        }
        if (objectContext == null) {
            objectContext = ObjectReferenceContext.captureForReferenceTrackerRelease(
                pointer = pointer,
                interfaceIdLowBits = interfaceIdLowBits,
                interfaceIdHighBits = interfaceIdHighBits,
                knownInterfaceId = knownInterfaceId,
            )
        }
        referenceTrackerRegistrationKey = ReferenceTrackerManager.attach(
            trackerPointer,
            trackerSource.weakReference,
        )
        referenceTrackerPointer = trackerPointer
        this.ownsTrackerPointer = ownsTrackerPointer
        retainTrackerPointer(trackerPointer)
        addRefFromTrackerSourceCallback(trackerPointer)
        if (addRefForObjectReference) {
            addRefFromTrackerSourceCallback(trackerPointer)
        }
        this.releaseTrackerSourceOnDispose = releaseTrackerSourceOnDispose
    }

    fun acquireOwnedPointer(): RawComPtr {
        return withCall { activePointer ->
            val currentPointer = activePointer
            WinRTPlatformApi.addRefRaw(currentPointer.asRawAddress())
            return currentPointer
        }
    }

    fun <T> tryQueryInterface(
        requestedInterfaceId: Guid,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): T? {
        return withCall { activePointer ->
            val result = WinRTPlatformApi.queryInterfaceRaw(activePointer.asRawAddress(), requestedInterfaceId)
            val queriedPointer = result.pointer.asRawComPtr()
            if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
                return null
            }
            WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
            if (isAggregated) {
                WinRTPlatformApi.releaseRaw(result.pointer)
            }
            addRefFromTrackerSource(addRefFromTrackerSourceCallback)
            return wrapReference(
                queriedPointer,
                requestedInterfaceId,
                referenceTrackerHandle,
                isAggregated,
                isAggregated,
            )
        }
    }

    fun <T> queryInterface(
        requestedInterfaceId: Guid,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): Result<T> =
        runCatching {
            tryQueryInterface(requestedInterfaceId, addRefFromTrackerSourceCallback, wrapReference)
                ?: throw WinRTUnsupportedOperationException(
                    "QueryInterface failed for $requestedInterfaceId with ${KnownHResults.E_NOINTERFACE}",
                    KnownHResults.E_NOINTERFACE,
                )
        }

    fun tryInitializeReferenceTracker(
        trackerSourceOwner: ComPtr,
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
        ownsTrackerPointer: Boolean = true,
    ): Boolean {
        return withCall { activePointer ->
            if (hasReferenceTracker) {
                return true
            }

            val trackerPointer = tryQueryComInterface(
                activePointer.asRawAddress(),
                IID.IReferenceTracker,
            ).asRawComPtr()
            if (PlatformAbi.isNull(trackerPointer)) {
                return false
            }
            try {
                attachReferenceTracker(
                    trackerPointer = trackerPointer,
                    trackerSource = trackerSourceOwner.getOrCreateTrackerSource(),
                    addRefForObjectReference = addRefFromTrackerSource,
                    releaseTrackerSourceOnDispose = addRefFromTrackerSource,
                    retainTrackerPointer = retainTrackerPointer,
                    addRefFromTrackerSourceCallback = addRefFromTrackerSourceCallback,
                    ownsTrackerPointer = ownsTrackerPointer,
                )
            } finally {
                WinRTPlatformApi.releaseRaw(trackerPointer.asRawAddress())
            }
            return true
        }
    }

    fun sameIdentity(other: RawComObjectReferenceSupport): Boolean = withCall { thisPointer ->
        other.withCall { otherPointer ->
            val thisIdentity = tryQueryIUnknown(thisPointer) ?: return false
            try {
                val otherIdentity = tryQueryIUnknown(otherPointer) ?: return false
                try {
                    PlatformAbi.samePointer(thisIdentity, otherIdentity)
                } finally {
                    WinRTPlatformApi.releaseRaw(otherIdentity.asRawAddress())
                }
            } finally {
                WinRTPlatformApi.releaseRaw(thisIdentity.asRawAddress())
            }
        }
    }

    // CsWinRT marks AddRefUnsafe/TryAddRefUnsafe aggressively inline. Keep the
    // hot state transition in the caller instead of adding Native managed frames.
    @Suppress("NOTHING_TO_INLINE")
    inline fun beginCall() {
        while (true) {
            val current = loadLifetime()
            if (current < 0) throw WinRTObjectDisposedException("Object reference is disposed.")
            check(current < CALL_COUNT_MASK) { "COM call lease count overflowed." }
            if (compareAndSetLifetime(current, current + 1)) return
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline fun endCall() {
        val remaining = decrementLifetime()
        if (remaining < 0 && remaining and CALL_COUNT_MASK == 0) {
            disposeNativeResources(remaining and DEFER_CONTEXT_RELEASE != 0)
        }
    }

    inline fun <T> withCall(block: (RawComPtr) -> T): T {
        beginCall()
        try {
            return block(pointerForCurrentContext())
        } finally {
            endCall()
        }
    }

    fun close(deferContextRelease: Boolean = false) {
        while (true) {
            val current = lifetime.value
            if (current < 0) return
            val closed = current or Int.MIN_VALUE or (if (deferContextRelease) DEFER_CONTEXT_RELEASE else 0)
            if (lifetime.compareAndSet(current, closed)) {
                if (current == 0) disposeNativeResources(deferContextRelease)
                return
            }
        }
    }

    @PublishedApi
    internal fun disposeNativeResources(deferContextRelease: Boolean) {
        val context = objectContext
        // CsWinRT 3 NativeDisposeUnsafe directly releases free-threaded objects.
        // Do not allocate the context/tracker continuation graph for this case.
        if (context == null && !hasReferenceTracker) {
            ReferenceTrackerManager.runOrDeferFinalizerRelease {
                try {
                    if (!preventReleaseOnDispose && !tryReleaseManagedCcwReference(managedCcwReleaseIdentity)) {
                        WinRTPlatformApi.releaseRaw(pointer.asRawAddress())
                    }
                } finally {
                    try {
                        nativeLifetimeLease?.release(deferContextRelease)
                    } finally {
                        afterRelease?.invoke()
                    }
                }
            }
            return
        }
        val releaseReferences = {
            if (!preventReleaseOnDispose) {
                releaseFromTrackerSource(::invokeReferenceTrackerReleaseOnPointer)
                if (!tryReleaseManagedCcwReference(managedCcwReleaseIdentity)) {
                    WinRTPlatformApi.releaseRaw(pointer.asRawAddress())
                }
            }
            releaseReferenceTracker(::invokeReferenceTrackerReleaseOnPointer, ::invokeIUnknownReleaseOnPointer)
        }
        val releaseReferencesAndContext = {
            try {
                context?.callInOriginalContext(releaseReferences, releaseReferences) ?: releaseReferences()
            } finally {
                try {
                    nativeLifetimeLease?.release(deferContextRelease)
                } finally {
                    try {
                        context?.close()
                    } finally {
                        afterRelease?.invoke()
                    }
                }
            }
        }
        val disconnectAndRelease = {
            if (!detachReferenceTracker(releaseReferencesAndContext)) {
                releaseReferencesAndContext()
            }
        }
        val releaseInContext = {
            if (deferContextRelease && context != null) {
                context.deferToOriginalContext(disconnectAndRelease)
            } else {
                context?.callInOriginalContext(disconnectAndRelease, disconnectAndRelease) ?: disconnectAndRelease()
            }
        }
        if (!ReferenceTrackerManager.deferFinalizerRelease(releaseInContext)) {
            releaseInContext()
        }
    }

    @PublishedApi
    internal companion object {
        const val CALL_COUNT_MASK = 0x3fffffff
        const val DEFER_CONTEXT_RELEASE = 0x40000000
    }

    fun throwIfDisposed() {
        if (isDisposed) {
            throw WinRTObjectDisposedException("Object reference is disposed.")
        }
    }

    private fun addRefFromTrackerSource(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (hasReferenceTracker) {
            addRefFromTrackerSourceCallback(referenceTrackerPointer)
        }
    }

    private fun releaseFromTrackerSource(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (hasReferenceTracker) {
            releaseFromTrackerSourceCallback(referenceTrackerPointer)
        }
    }

    private fun detachReferenceTracker(disconnectedRelease: () -> Unit): Boolean {
        if (!hasReferenceTracker) {
            return false
        }
        val registrationKey = referenceTrackerRegistrationKey
        referenceTrackerRegistrationKey = 0L
        return ReferenceTrackerManager.detach(registrationKey, disconnectedRelease)
    }

    private fun releaseReferenceTracker(
        releaseFromTrackerSourceCallback: (RawComPtr) -> Unit,
        releaseTrackerPointer: (RawComPtr) -> Unit,
    ) {
        if (!hasReferenceTracker) {
            return
        }
        if (releaseTrackerSourceOnDispose) {
            releaseFromTrackerSource(releaseFromTrackerSourceCallback)
        }
        if (ownsTrackerPointer) releaseTrackerPointer(referenceTrackerPointer)
        referenceTrackerPointer = PlatformAbi.nullComPtr
        releaseTrackerSourceOnDispose = false
    }

    private fun tryQueryIUnknown(target: RawComPtr): RawComPtr? {
        val result = WinRTPlatformApi.queryInterfaceRaw(target.asRawAddress(), IID.IUnknown)
        val unknownPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(unknownPointer)) {
            return null
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return unknownPointer
    }

}
