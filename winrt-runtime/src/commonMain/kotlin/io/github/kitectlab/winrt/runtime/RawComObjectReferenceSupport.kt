package io.github.kitectlab.winrt.runtime

internal class RawComObjectReferenceSupport(
    private val pointer: NativePointer,
    val interfaceId: Guid,
    private val preventReleaseOnDispose: Boolean = false,
) {
    private val state = ComObjectReferenceState()

    val isDisposed: Boolean
        get() = state.isDisposed

    val hasReferenceTracker: Boolean
        get() = state.hasReferenceTracker

    val referenceTrackerHandle: NativePointer
        get() = state.referenceTrackerHandle

    fun attachReferenceTracker(
        trackerPointer: NativePointer,
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (NativePointer) -> Unit,
        addRefFromTrackerSourceCallback: (NativePointer) -> Unit,
    ) {
        state.attachReferenceTracker(
            trackerPointer = trackerPointer,
            addRefFromTrackerSource = addRefFromTrackerSource,
            retainTrackerPointer = retainTrackerPointer,
            addRefFromTrackerSourceCallback = addRefFromTrackerSourceCallback,
        )
    }

    fun addRef(addRefFromTrackerSourceCallback: (NativePointer) -> Unit): UInt {
        throwIfDisposed()
        val count = WinRtPlatformApi.addRefRaw(pointer)
        state.addRefFromTrackerSourceIfNeeded(addRefFromTrackerSourceCallback)
        return count
    }

    fun release(releaseFromTrackerSourceCallback: (NativePointer) -> Unit): UInt {
        throwIfDisposed()
        state.releaseFromTrackerSourceIfNeeded(releaseFromTrackerSourceCallback)
        return WinRtPlatformApi.releaseRaw(pointer)
    }

    fun getRef(addRefFromTrackerSourceCallback: (NativePointer) -> Unit): NativePointer {
        addRef(addRefFromTrackerSourceCallback)
        return pointer
    }

    fun <T> tryQueryInterface(
        requestedInterfaceId: Guid,
        wrapReference: (NativePointer, Guid, NativePointer, Boolean) -> T,
    ): T? {
        throwIfDisposed()
        val result = WinRtPlatformApi.queryInterfaceRaw(pointer, requestedInterfaceId)
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || NativeInterop.isNull(result.pointer)) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return wrapReference(
            result.pointer,
            requestedInterfaceId,
            referenceTrackerHandle,
            preventReleaseOnDispose,
        )
    }

    fun <T> queryInterface(
        requestedInterfaceId: Guid,
        wrapReference: (NativePointer, Guid, NativePointer, Boolean) -> T,
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
        retainTrackerPointer: (NativePointer) -> Unit,
        addRefFromTrackerSourceCallback: (NativePointer) -> Unit,
    ): Boolean {
        if (hasReferenceTracker) {
            return true
        }

        val result = WinRtPlatformApi.queryInterfaceRaw(pointer, IID.IReferenceTracker)
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || NativeInterop.isNull(result.pointer)) {
            return false
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        try {
            attachReferenceTracker(
                trackerPointer = result.pointer,
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
            WinRtPlatformApi.releaseRaw(thisIdentity)
            throw error
        }

        return try {
            NativeInterop.samePointer(thisIdentity, otherIdentity)
        } finally {
            WinRtPlatformApi.releaseRaw(thisIdentity)
            WinRtPlatformApi.releaseRaw(otherIdentity)
        }
    }

    fun close(
        releaseFromTrackerSourceCallback: (NativePointer) -> Unit,
        releaseTrackerPointer: (NativePointer) -> Unit,
    ) {
        if (state.beginDispose()) {
            try {
                if (!preventReleaseOnDispose) {
                    state.releaseFromTrackerSourceIfNeeded(releaseFromTrackerSourceCallback)
                    WinRtPlatformApi.releaseRaw(pointer)
                }
            } finally {
                state.disposeReferenceTracker(releaseTrackerPointer)
            }
        }
    }

    fun throwIfDisposed() {
        if (state.isDisposed) {
            throw WinRtObjectDisposedException("Object reference is disposed.")
        }
    }

    private fun tryQueryIUnknown(target: NativePointer): NativePointer? {
        val result = WinRtPlatformApi.queryInterfaceRaw(target, IID.IUnknown)
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || NativeInterop.isNull(result.pointer)) {
            return null
        }
        WinRtPlatformApi.checkSucceededRaw(result.hResultValue)
        return result.pointer
    }
}
