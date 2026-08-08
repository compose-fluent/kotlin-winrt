package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@PublishedApi
internal class RawComObjectReferenceSupport(
    private val pointer: RawComPtr,
    val interfaceId: Guid,
    private val preventReleaseOnDispose: Boolean = false,
    val isAggregated: Boolean = false,
) {
    private val disposed = AtomicInt(0)
    private var referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr
    private var releaseInitialTrackerSourceOnDispose: Boolean = false

    val isDisposed: Boolean
        get() = disposed.load() != 0

    val hasReferenceTracker: Boolean
        get() = !PlatformAbi.isNull(referenceTrackerPointer)

    val referenceTrackerHandle: RawComPtr
        get() = referenceTrackerPointer

    fun attachReferenceTracker(
        trackerPointer: RawComPtr,
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
    ) {
        if (hasReferenceTracker) {
            return
        }
        referenceTrackerPointer = trackerPointer
        retainTrackerPointer(trackerPointer)
        if (addRefFromTrackerSource) {
            addRefFromTrackerSourceCallback(trackerPointer)
            releaseInitialTrackerSourceOnDispose = true
        }
    }

    fun addRef(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        val count = WinRTPlatformApi.addRefRaw(pointer.asNativePointer())
        addRefFromTrackerSource(addRefFromTrackerSourceCallback)
        return count
    }

    fun release(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        releaseFromTrackerSource(releaseFromTrackerSourceCallback)
        return WinRTPlatformApi.releaseRaw(pointer.asNativePointer())
    }

    fun getRef(): RawComPtr {
        throwIfDisposed()
        WinRTPlatformApi.addRefRaw(pointer.asNativePointer())
        return pointer
    }

    fun <T> tryQueryInterface(
        requestedInterfaceId: Guid,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): T? {
        throwIfDisposed()
        val result = WinRTPlatformApi.queryInterfaceRaw(pointer.asNativePointer(), requestedInterfaceId)
        val queriedPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(queriedPointer)) {
            return null
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        if (isAggregated) {
            WinRTPlatformApi.releaseRaw(result.pointer)
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
                ?: throw WinRTUnsupportedOperationException(
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

        val result = WinRTPlatformApi.queryInterfaceRaw(pointer.asNativePointer(), IID.IReferenceTracker)
        val trackerPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(trackerPointer)) {
            return false
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        try {
            attachReferenceTracker(
                trackerPointer = trackerPointer,
                addRefFromTrackerSource = addRefFromTrackerSource,
                retainTrackerPointer = retainTrackerPointer,
                addRefFromTrackerSourceCallback = addRefFromTrackerSourceCallback,
            )
        } finally {
            WinRTPlatformApi.releaseRaw(result.pointer)
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
            WinRTPlatformApi.releaseRaw(thisIdentity.asNativePointer())
            throw error
        }

        return try {
            PlatformAbi.samePointer(thisIdentity, otherIdentity)
        } finally {
            WinRTPlatformApi.releaseRaw(thisIdentity.asNativePointer())
            WinRTPlatformApi.releaseRaw(otherIdentity.asNativePointer())
        }
    }

    fun close(
        releaseFromTrackerSourceCallback: (RawComPtr) -> Unit,
        releaseTrackerPointer: (RawComPtr) -> Unit,
    ) {
        if (disposed.compareAndSet(0, 1)) {
            try {
                if (!preventReleaseOnDispose) {
                    releaseFromTrackerSource(releaseFromTrackerSourceCallback)
                    WinRTPlatformApi.releaseRaw(pointer.asNativePointer())
                }
            } finally {
                disposeReferenceTracker(releaseFromTrackerSourceCallback, releaseTrackerPointer)
            }
        }
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

    private fun disposeReferenceTracker(
        releaseFromTrackerSourceCallback: (RawComPtr) -> Unit,
        releaseTrackerPointer: (RawComPtr) -> Unit,
    ) {
        if (!hasReferenceTracker) {
            return
        }
        if (releaseInitialTrackerSourceOnDispose) {
            releaseFromTrackerSource(releaseFromTrackerSourceCallback)
        }
        releaseTrackerPointer(referenceTrackerPointer)
        referenceTrackerPointer = PlatformAbi.nullComPtr
        releaseInitialTrackerSourceOnDispose = false
    }

    private fun tryQueryIUnknown(target: RawComPtr): RawComPtr? {
        val result = WinRTPlatformApi.queryInterfaceRaw(target.asNativePointer(), IID.IUnknown)
        val unknownPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(unknownPointer)) {
            return null
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return unknownPointer
    }

    private fun RawComPtr.asNativePointer(): RawAddress = PlatformAbi.fromRawComPtr(this)

    private fun RawAddress.asRawComPtr(): RawComPtr = PlatformAbi.toRawComPtr(this)
}
