package io.github.composefluent.winrt.runtime

internal class RawComObjectReferenceSupport(
    private val pointer: RawComPtr,
    val interfaceId: Guid,
    private val preventReleaseOnDispose: Boolean = false,
    val isAggregated: Boolean = false,
) {
    private val state = ComObjectReferenceState()

    val isDisposed: Boolean
        get() = state.isDisposed

    val hasReferenceTracker: Boolean
        get() = state.hasReferenceTracker

    val referenceTrackerHandle: RawComPtr
        get() = state.referenceTrackerHandle

    fun attachReferenceTracker(
        trackerPointer: RawComPtr,
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
    ) {
        state.attachReferenceTracker(
            trackerPointer = trackerPointer,
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = retainTrackerPointer,
            addRefFromTrackerSourceCallback = addRefFromTrackerSourceCallback,
        )
    }

    fun addRef(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        val count = WinRtPlatformApi.addRefRaw(pointer.asNativePointer())
        state.addRefFromTrackerSource(addRefFromTrackerSourceCallback)
        return count
    }

    fun release(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        state.releaseFromTrackerSource(releaseFromTrackerSourceCallback)
        return WinRtPlatformApi.releaseRaw(pointer.asNativePointer())
    }

    fun getRef(): RawComPtr {
        throwIfDisposed()
        WinRtPlatformApi.addRefRaw(pointer.asNativePointer())
        return pointer
    }

    fun <T> tryQueryInterface(
        requestedInterfaceId: Guid,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): T? {
        throwIfDisposed()
        val result = WinRtPlatformApi.queryInterfaceRaw(pointer.asNativePointer(), requestedInterfaceId)
        val queriedPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        if (isAggregated) {
            WinRtPlatformApi.releaseRaw(result.pointer)
        }
        return wrapReference(
            queriedPointer,
            requestedInterfaceId,
            referenceTrackerHandle,
            preventReleaseOnDispose || isAggregated,
            isAggregated,
        )
    }

    fun <T> queryInterface(
        requestedInterfaceId: Guid,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): Result<T> =
        runCatching {
            tryQueryInterface(requestedInterfaceId, wrapReference)
                ?: throw WinRtUnsupportedOperationException(
                    "QueryInterface failed for $requestedInterfaceId with ${KnownHResults.E_NOINTERFACE}",
                    KnownHResults.E_NOINTERFACE,
                )
        }

    fun tryInitializeReferenceTracker(
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
    ): Boolean {
        if (hasReferenceTracker) {
            return true
        }

        val result = WinRtPlatformApi.queryInterfaceRaw(pointer.asNativePointer(), IID.IReferenceTracker)
        val trackerPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(trackerPointer)) {
            return false
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        try {
            attachReferenceTracker(
                trackerPointer = trackerPointer,
                addRefFromTrackerSource = addRefFromTrackerSource,
                retainTrackerPointer = retainTrackerPointer,
                addRefFromTrackerSourceCallback = addRefFromTrackerSourceCallback,
            )
        } finally {
            WinRtPlatformApi.releaseRaw(result.pointer)
        }
        return true
    }

    fun sameIdentity(other: RawComObjectReferenceSupport): Boolean {
        throwIfDisposed()
        other.throwIfDisposed()

        val thisIdentity = tryQueryIUnknown(pointer) ?: return false
        val otherIdentity = try {
            tryQueryIUnknown(other.pointer) ?: return false
        } catch (error: Throwable) {
            WinRtPlatformApi.releaseRaw(thisIdentity.asNativePointer())
            throw error
        }

        return try {
            PlatformAbi.samePointer(thisIdentity, otherIdentity)
        } finally {
            WinRtPlatformApi.releaseRaw(thisIdentity.asNativePointer())
            WinRtPlatformApi.releaseRaw(otherIdentity.asNativePointer())
        }
    }

    fun close(
        releaseFromTrackerSourceCallback: (RawComPtr) -> Unit,
        releaseTrackerPointer: (RawComPtr) -> Unit,
    ) {
        if (state.beginDispose()) {
            try {
                if (!preventReleaseOnDispose) {
                    state.releaseFromTrackerSource(releaseFromTrackerSourceCallback)
                    WinRtPlatformApi.releaseRaw(pointer.asNativePointer())
                }
            } finally {
                state.disposeReferenceTracker(releaseFromTrackerSourceCallback, releaseTrackerPointer)
            }
        }
    }

    fun throwIfDisposed() {
        if (state.isDisposed) {
            throw WinRtObjectDisposedException("Object reference is disposed.")
        }
    }

    private fun tryQueryIUnknown(target: RawComPtr): RawComPtr? {
        val result = WinRtPlatformApi.queryInterfaceRaw(target.asNativePointer(), IID.IUnknown)
        val unknownPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(unknownPointer)) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return unknownPointer
    }

    private fun RawComPtr.asNativePointer(): RawAddress = PlatformAbi.fromRawComPtr(this)

    private fun RawAddress.asRawComPtr(): RawComPtr = PlatformAbi.toRawComPtr(this)
}
