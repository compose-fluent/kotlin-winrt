package io.github.kitectlab.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ComObjectReferenceState {
    private val disposed = AtomicInt(0)
    private var referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr
    private var releaseFromTrackerSourceOnDispose: Boolean = false

    val isDisposed: Boolean
        get() = disposed.load() != 0

    val hasReferenceTracker: Boolean
        get() = !PlatformAbi.isNull(referenceTrackerPointer)

    val referenceTrackerHandle: RawComPtr
        get() = referenceTrackerPointer

    fun beginDispose(): Boolean = disposed.compareAndSet(0, 1)

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
            releaseFromTrackerSourceOnDispose = true
        }
    }

    fun addRefFromTrackerSourceIfNeeded(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        addRefFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun releaseFromTrackerSourceIfNeeded(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (!hasReferenceTracker || !releaseFromTrackerSourceOnDispose) {
            return
        }
        releaseFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun disposeReferenceTracker(releaseTrackerPointer: (RawComPtr) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        releaseTrackerPointer(referenceTrackerPointer)
        referenceTrackerPointer = PlatformAbi.nullComPtr
        releaseFromTrackerSourceOnDispose = false
    }
}
