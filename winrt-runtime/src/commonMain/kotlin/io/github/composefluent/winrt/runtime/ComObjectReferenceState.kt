package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ComObjectReferenceState {
    private val disposed = AtomicInt(0)
    private var referenceTrackerPointer: RawComPtr = PlatformAbi.nullComPtr
    private var releaseInitialTrackerSourceOnDispose: Boolean = false

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
            releaseInitialTrackerSourceOnDispose = true
        }
    }

    fun addRefFromTrackerSource(addRefFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        addRefFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun releaseFromTrackerSource(releaseFromTrackerSourceCallback: (RawComPtr) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        releaseFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun disposeReferenceTracker(
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
}
