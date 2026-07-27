package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

internal data class ManagedComQueryResult<T>(
    val hResult: HResult,
    val target: T?,
)

@OptIn(ExperimentalAtomicApi::class)
internal class ManagedComHostState(
    private val cleanup: () -> Unit,
) {
    private val cleanedUp = AtomicInt(0)
    private val referenceCount = AtomicInt(1)
    private val trackerReferenceCount = AtomicInt(0)

    fun addReference(): Int = updateReferenceCount(1)

    fun tryAddReference(): Int? {
        while (true) {
            val current = referenceCount.load()
            if (current == 0) {
                return null
            }
            val updated = current + 1
            check(updated > 0) { "Managed COM host reference count overflowed." }
            if (referenceCount.compareAndSet(current, updated)) {
                return updated
            }
        }
    }

    fun releaseReference(): Int {
        val updated = updateReferenceCount(-1)
        if (updated == 0) {
            cleanupOnce()
        }
        return updated
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
        addReference()
        return ManagedComQueryResult(KnownHResults.S_OK, target)
    }

    private fun cleanupOnce() {
        if (cleanedUp.compareAndSet(0, 1)) {
            cleanup()
        }
    }

    private fun updateReferenceCount(delta: Int): Int {
        while (true) {
            val current = referenceCount.load()
            val updated = current + delta
            check(updated >= 0) { "Managed COM host reference count cannot become negative." }
            if (referenceCount.compareAndSet(current, updated)) {
                return updated
            }
        }
    }
}
