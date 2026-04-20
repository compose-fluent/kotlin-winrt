package io.github.kitectlab.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class ComObjectReferenceState {
    private val disposed = AtomicInt(0)
    private var referenceTrackerPointer: NativePointer = NativeInterop.nullPointer
    private var releaseFromTrackerSourceOnDispose: Boolean = false

    val isDisposed: Boolean
        get() = disposed.load() != 0

    val hasReferenceTracker: Boolean
        get() = !NativeInterop.isNull(referenceTrackerPointer)

    val referenceTrackerHandle: NativePointer
        get() = referenceTrackerPointer

    fun beginDispose(): Boolean = disposed.compareAndSet(0, 1)

    fun attachReferenceTracker(
        trackerPointer: NativePointer,
        addRefFromTrackerSource: Boolean,
        retainTrackerPointer: (NativePointer) -> Unit,
        addRefFromTrackerSourceCallback: (NativePointer) -> Unit,
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

    fun addRefFromTrackerSourceIfNeeded(addRefFromTrackerSourceCallback: (NativePointer) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        addRefFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun releaseFromTrackerSourceIfNeeded(releaseFromTrackerSourceCallback: (NativePointer) -> Unit) {
        if (!hasReferenceTracker || !releaseFromTrackerSourceOnDispose) {
            return
        }
        releaseFromTrackerSourceCallback(referenceTrackerPointer)
    }

    fun disposeReferenceTracker(releaseTrackerPointer: (NativePointer) -> Unit) {
        if (!hasReferenceTracker) {
            return
        }
        releaseTrackerPointer(referenceTrackerPointer)
        referenceTrackerPointer = NativeInterop.nullPointer
        releaseFromTrackerSourceOnDispose = false
    }
}
