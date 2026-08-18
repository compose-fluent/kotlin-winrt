package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class ManagedComQueryResult<T>(
    val hResult: HResult,
    val target: T?,
)

internal interface ManagedComRootReference {
    fun tryPin(knownManagedValue: Any?): Boolean

    fun unpin()
}

private object PermanentlyPinnedManagedComRootReference : ManagedComRootReference {
    override fun tryPin(knownManagedValue: Any?): Boolean = true

    override fun unpin() = Unit
}

@OptIn(ExperimentalAtomicApi::class)
@PublishedApi
internal class ManagedComHostState(
    private val cleanup: () -> Unit,
    private val rootReference: ManagedComRootReference,
    @PublishedApi internal val borrowReady: Boolean = false,
    referenceCounterStorage: RawAddress = RawAddress.Null,
    referenceCounterStorageView: NativeMemoryView? = null,
    referenceCounterStorageOffsetBytes: Long = 0L,
) {
    constructor(cleanup: () -> Unit) : this(cleanup, PermanentlyPinnedManagedComRootReference)

    private val cleanedUp = AtomicInt(0)

    @PublishedApi
    internal val baselineReleased = AtomicInt(0)

    @PublishedApi
    internal val unpinnedBaselineCount =
        if (borrowReady) managedComBorrowReadyReferenceFlag or 1L else 1L

    @PublishedApi
    internal val referenceCount = PlatformManagedComReferenceCounter(
        initialValue = unpinnedBaselineCount,
        initialStorage = referenceCounterStorage,
        initialStorageView = referenceCounterStorageView,
        initialStorageOffsetBytes = referenceCounterStorageOffsetBytes,
    )
    private val trackerReferenceCount = AtomicInt(0)

    fun addReference(knownManagedValue: Any? = null): Int =
        tryAddReference(knownManagedValue) ?: 0

    fun tryAddReference(knownManagedValue: Any? = null): Int? {
        while (true) {
            val current = referenceCount.load()
            when {
                current == 0L -> return null
                current == managedComRootTransitionCount -> continue
                current.isBorrowReadyReferenceCount() -> {
                    val count = current.managedComReferenceCount()
                    check(count in 1 until Int.MAX_VALUE) {
                        "Managed COM borrow-ready reference count is invalid: $count."
                    }
                    if (!referenceCount.compareAndSet(current, managedComRootTransitionCount)) {
                        continue
                    }
                    val pinned = try {
                        rootReference.tryPin(knownManagedValue)
                    } catch (failure: Throwable) {
                        referenceCount.store(current)
                        throw failure
                    }
                    if (!pinned) {
                        referenceCount.store(current)
                        return null
                    }
                    val updated = count + 1
                    val updatedState = updated.toLong()
                    referenceCount.store(updatedState)
                    return visibleReferenceCount(updatedState)
                }
            }
            val count = current.managedComReferenceCount()
            when (count) {
                1 -> {
                    if (!referenceCount.compareAndSet(current, managedComRootTransitionCount)) {
                        continue
                    }
                    val pinned = try {
                        rootReference.tryPin(knownManagedValue)
                    } catch (failure: Throwable) {
                        referenceCount.store(current)
                        throw failure
                    }
                    if (!pinned) {
                        referenceCount.store(current)
                        return null
                    }
                    val updatedState = 2L
                    referenceCount.store(updatedState)
                    return visibleReferenceCount(updatedState)
                }
            }
            check(count in 2 until Int.MAX_VALUE) {
                "Managed COM host reference count is invalid: $count."
            }
            val updatedState = current.withManagedComReferenceCount(count + 1)
            if (referenceCount.compareAndSet(current, updatedState)) {
                return visibleReferenceCount(updatedState)
            }
        }
    }

    fun addBorrowedReference(): Int {
        while (true) {
            val current = referenceCount.load()
            when {
                current == 0L -> return 0
                current == managedComRootTransitionCount -> continue
                current.isBorrowReadyReferenceCount() -> {
                    val count = current.managedComReferenceCount()
                    check(count in 1 until Int.MAX_VALUE) {
                        "Managed COM borrow-ready reference count overflowed."
                    }
                    val updatedState = current.withManagedComReferenceCount(count + 1)
                    if (referenceCount.compareAndSet(current, updatedState)) {
                        return visibleReferenceCount(updatedState)
                    }
                }
                else -> return addReference()
            }
        }
    }

    fun releaseReference(): Int {
        while (true) {
            val current = referenceCount.load()
            when {
                current == managedComRootTransitionCount -> continue
                current.isBorrowReadyReferenceCount() -> {
                    val count = current.managedComReferenceCount()
                    check(count > 1) { "Managed COM release consumed its borrow-ready baseline reference." }
                    val updatedState = current.withManagedComReferenceCount(count - 1)
                    if (referenceCount.compareAndSet(current, updatedState)) {
                        return visibleReferenceCount(updatedState)
                    }
                    continue
                }
                current.managedComReferenceCount() == 2 -> {
                    if (!referenceCount.compareAndSet(current, managedComRootTransitionCount)) {
                        continue
                    }
                    if (baselineReleased.load() != 0) {
                        try {
                            rootReference.unpin()
                        } finally {
                            referenceCount.store(0L)
                        }
                        cleanupOnce()
                        return 0
                    }
                    try {
                        rootReference.unpin()
                    } finally {
                        referenceCount.store(unpinnedBaselineCount)
                    }
                    return 1
                }
                current.managedComReferenceCount() == 1 -> {
                    if (!referenceCount.compareAndSet(current, 0L)) {
                        continue
                    }
                    cleanupOnce()
                    return 0
                }
            }
            val count = current.managedComReferenceCount()
            check(count > 2) { "Managed COM host reference count cannot be released from $count." }
            val updatedState = current.withManagedComReferenceCount(count - 1)
            if (referenceCount.compareAndSet(current, updatedState)) {
                return visibleReferenceCount(updatedState)
            }
        }
    }

    fun tryBeginBorrowedCall(knownManagedValue: Any): Boolean {
        if (!borrowReady) {
            return tryAddReference(knownManagedValue) != null
        }
        while (true) {
            val current = referenceCount.load()
            when (current) {
                0L -> return false
                managedComRootTransitionCount -> continue
                else -> return true
            }
        }
    }

    @PublishedApi
    internal inline fun endBorrowedCall(knownManagedValue: Any?) {
        if (
            borrowReady &&
            referenceCount.load() == unpinnedBaselineCount &&
            baselineReleased.load() == 0
        ) {
            return
        }
        completeBorrowedCallStateTransition(knownManagedValue)
    }

    @PublishedApi
    internal fun completeBorrowedCallStateTransition(knownManagedValue: Any?) {
        if (!borrowReady) {
            releaseReference()
            return
        }
        while (true) {
            val current = referenceCount.load()
            when {
                current == 0L -> return
                current == managedComRootTransitionCount -> continue
                !current.isBorrowReadyReferenceCount() -> return
                else -> {
                    val count = current.managedComReferenceCount()
                    check(count > 0) { "Managed COM borrow-ready reference count is invalid: $count." }
                    if (count == 1 && baselineReleased.load() == 0) {
                        return
                    }
                    if (!referenceCount.compareAndSet(current, managedComRootTransitionCount)) {
                        continue
                    }
                    if (count == 1) {
                        referenceCount.store(0L)
                        cleanupOnce()
                        return
                    }
                    val pinned = try {
                        rootReference.tryPin(knownManagedValue)
                    } catch (failure: Throwable) {
                        referenceCount.store(current)
                        throw failure
                    }
                    if (!pinned) {
                        referenceCount.store(current)
                        error("Managed COM escaped reference could not pin its managed root.")
                    }
                    referenceCount.store(count.toLong())
                    return
                }
            }
        }
    }

    fun releaseBaselineReference(): Int {
        if (!baselineReleased.compareAndSet(0, 1)) {
            return currentReferenceCount()
        }
        while (true) {
            val current = referenceCount.load()
            when {
                current == managedComRootTransitionCount -> continue
                current == 0L -> return 0
            }
            val count = current.managedComReferenceCount()
            check(count > 0) { "Managed COM baseline reference count is invalid: $count." }
            if (count == 1) {
                if (!referenceCount.compareAndSet(current, 0L)) {
                    continue
                }
                cleanupOnce()
                return 0
            }
            return visibleReferenceCount(current)
        }
    }

    fun addTrackerReference(): Int {
        while (true) {
            val current = trackerReferenceCount.load()
            if (current == Int.MAX_VALUE) {
                return current
            }

            // Pin the live host before publishing tracker ownership so a final Release
            // cannot clean up the CCW between the two reference-count updates.
            tryAddReference() ?: return 0
            val next = current + 1
            if (trackerReferenceCount.compareAndSet(current, next)) {
                return next
            }
            releaseReference()
        }
    }

    fun releaseTrackerReference(): Int {
        while (true) {
            val current = trackerReferenceCount.load()
            val next = if (current <= 0) 0 else current - 1
            if (trackerReferenceCount.compareAndSet(current, next)) {
                if (next != current) {
                    releaseReference()
                }
                return next
            }
        }
    }

    fun <T> queryInterface(
        requestedInterfaceId: Guid,
        resolveTarget: (Guid) -> T?,
    ): ManagedComQueryResult<T> {
        val target = resolveTarget(requestedInterfaceId)
            ?: return ManagedComQueryResult(KnownHResults.E_NOINTERFACE, null)
        if (addBorrowedReference() == 0) {
            return ManagedComQueryResult(KnownHResults.E_POINTER, null)
        }
        return ManagedComQueryResult(KnownHResults.S_OK, target)
    }

    fun currentReferenceCount(): Int {
        while (true) {
            val current = referenceCount.load()
            if (current != managedComRootTransitionCount) {
                return visibleReferenceCount(current)
            }
        }
    }

    fun attachReferenceCounter(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView? = null,
        objectMemoryOffsetBytes: Long = 0L,
    ) {
        referenceCount.attach(objectMemory, objectMemoryView, objectMemoryOffsetBytes)
    }

    fun detachReferenceCounter(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView? = null,
        objectMemoryOffsetBytes: Long = 0L,
    ) {
        referenceCount.detach(objectMemory, objectMemoryView, objectMemoryOffsetBytes)
    }

    private fun cleanupOnce() {
        if (cleanedUp.compareAndSet(0, 1)) {
            try {
                cleanup()
            } finally {
                referenceCount.close()
            }
        }
    }

    private fun visibleReferenceCount(state: Long): Int =
        (
            state.managedComReferenceCount() -
                baselineReleased.load()
        ).coerceAtLeast(0)

}

private fun Long.isBorrowReadyReferenceCount(): Boolean =
    this and managedComBorrowReadyReferenceFlag != 0L

private fun Long.managedComReferenceCount(): Int =
    (this and managedComReferenceCountMask).toInt()

private fun Long.withManagedComReferenceCount(count: Int): Long =
    (this and managedComReferenceCountMask.inv()) or count.toLong()
