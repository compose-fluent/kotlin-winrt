package io.github.kitectlab.winrt.runtime

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

    fun addReference(): Int = updateReferenceCount(1)

    fun releaseReference(): Int {
        val updated = updateReferenceCount(-1)
        if (updated == 0) {
            cleanupOnce()
        }
        return updated
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
