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
    trackContext: Boolean = true,
    internal val managedCcwReleaseIdentity: RawAddress = RawAddress.Null,
) {
    private val disposed = AtomicInt(0)
    private var referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr
    private var referenceTrackerRegistrationKey: Long = 0L
    private var releaseTrackerSourceOnDispose: Boolean = false
    private var objectContext =
        if (trackContext) {
            ObjectReferenceContext.capture(pointer, interfaceId)
        } else {
            null
        }

    val isDisposed: Boolean
        get() = disposed.load() != 0

    val hasReferenceTracker: Boolean
        get() = !PlatformAbi.isNull(referenceTrackerPointer)

    val referenceTrackerHandle: RawComPtr
        get() = referenceTrackerPointer

    fun pointerForCurrentContext(): RawComPtr =
        objectContext?.pointerForCurrentContext() ?: pointer

    fun attachReferenceTracker(
        trackerPointer: RawComPtr,
        addRefForObjectReference: Boolean,
        releaseTrackerSourceOnDispose: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
    ) {
        if (hasReferenceTracker) {
            return
        }
        if (objectContext == null) {
            objectContext = ObjectReferenceContext.captureForReferenceTrackerRelease(pointer, interfaceId)
        }
        referenceTrackerRegistrationKey = ReferenceTrackerManager.attach(trackerPointer)
        referenceTrackerPointer = trackerPointer
        retainTrackerPointer(trackerPointer)
        addRefFromTrackerSourceCallback(trackerPointer)
        if (addRefForObjectReference) {
            addRefFromTrackerSourceCallback(trackerPointer)
        }
        this.releaseTrackerSourceOnDispose = releaseTrackerSourceOnDispose
    }

    fun addRef(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        val count = WinRTPlatformApi.addRefRaw(pointerForCurrentContext().asNativePointer())
        addRefFromTrackerSource(addRefFromTrackerSourceCallback)
        return count
    }

    fun release(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit): UInt {
        throwIfDisposed()
        releaseFromTrackerSource(releaseFromTrackerSourceCallback)
        return WinRTPlatformApi.releaseRaw(pointerForCurrentContext().asNativePointer())
    }

    fun getRef(): RawComPtr {
        throwIfDisposed()
        val currentPointer = pointerForCurrentContext()
        WinRTPlatformApi.addRefRaw(currentPointer.asNativePointer())
        return currentPointer
    }

    fun <T> tryQueryInterface(
        requestedInterfaceId: Guid,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
        wrapReference: (RawComPtr, Guid, RawComPtr, Boolean, Boolean) -> T,
    ): T? {
        throwIfDisposed()
        val result = WinRTPlatformApi.queryInterfaceRaw(pointerForCurrentContext().asNativePointer(), requestedInterfaceId)
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
            preventReleaseOnDispose || isAggregated,
            isAggregated,
        )
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
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (RawComPtr) -> Unit,
        addRefFromTrackerSourceCallback: (RawComPtr) -> Unit,
    ): Boolean {
        if (hasReferenceTracker) {
            return true
        }

        val result = WinRTPlatformApi.queryInterfaceRaw(pointerForCurrentContext().asNativePointer(), IID.IReferenceTracker)
        val trackerPointer = result.pointer.asRawComPtr()
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(trackerPointer)) {
            return false
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        try {
            attachReferenceTracker(
                trackerPointer = trackerPointer,
                addRefForObjectReference = addRefFromTrackerSource,
                releaseTrackerSourceOnDispose = addRefFromTrackerSource,
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

        val thisIdentity = tryQueryIUnknown(pointerForCurrentContext()) ?: return false
        val otherIdentity = try {
            tryQueryIUnknown(other.pointerForCurrentContext()) ?: return false
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
        deferContextRelease: Boolean = false,
    ) {
        if (disposed.compareAndSet(0, 1)) {
            val context = objectContext
            val releaseReferences = {
                if (!preventReleaseOnDispose) {
                    releaseFromTrackerSource(releaseFromTrackerSourceCallback)
                    if (!tryReleaseManagedCcwReference(managedCcwReleaseIdentity)) {
                        WinRTPlatformApi.releaseRaw(pointer.asNativePointer())
                    }
                }
                releaseReferenceTracker(releaseFromTrackerSourceCallback, releaseTrackerPointer)
            }
            val releaseReferencesAndContext = {
                try {
                    context?.callInOriginalContext(releaseReferences, releaseReferences) ?: releaseReferences()
                } finally {
                    context?.close()
                }
            }
            val disconnectAndRelease = {
                if (!detachReferenceTracker(releaseReferencesAndContext)) {
                    releaseReferencesAndContext()
                }
            }
            if (deferContextRelease && context != null) {
                context.deferToOriginalContext(disconnectAndRelease)
            } else {
                context?.callInOriginalContext(disconnectAndRelease, disconnectAndRelease) ?: disconnectAndRelease()
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
        releaseTrackerPointer(referenceTrackerPointer)
        referenceTrackerPointer = PlatformAbi.nullComPtr
        releaseTrackerSourceOnDispose = false
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
