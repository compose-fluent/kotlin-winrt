@file:OptIn(
    kotlin.experimental.ExperimentalNativeApi::class,
    kotlin.native.concurrent.ObsoleteWorkersApi::class,
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)
@file:Suppress("DEPRECATION")

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.windows.SwitchToThread

class NativeWeakReferenceConcurrencyTest {
    @Test
    fun concurrent_first_use_of_weak_reference_lock_is_mutually_exclusive() {
        // CsWinRT 3 uses platform weak storage. Kotlin's native fallback requires
        // serialized retargeting, including simultaneous first use of the owner lock.
        val reference = WeakReference<Any>()
        val targets = Array(4) { Any() }
        val ready = AtomicInt(0)
        val lock = PlatformWeakReferenceLock()
        val active = AtomicInt(0)
        val workers = List(4) { Worker.start() }
        try {
            val jobs = workers.mapIndexed { index, worker ->
                worker.execute(TransferMode.SAFE, { StressState(reference, targets, ready, index, lock, active) }) { shared ->
                    shared.ready.fetchAndAdd(1)
                    while (shared.ready.load() != shared.targets.size) SwitchToThread()
                    repeat(1_000) { iteration ->
                        // Weak storage alone is thread-safe, so also verify that first-use
                        // publication cannot give different workers independent owner locks.
                        shared.lock.withWeakReferenceLock {
                            val previouslyActive = shared.active.fetchAndAdd(1)
                            try {
                                assertEquals(0, previouslyActive)
                                SwitchToThread()
                            } finally {
                                shared.active.fetchAndAdd(-1)
                            }
                        }
                        shared.reference.setTarget(if (iteration % 4 == 0) null else shared.targets[shared.index])
                        val resolved = shared.reference.tryGetTarget()
                        assertTrue(resolved == null || shared.targets.any { it === resolved })
                    }
                }
            }
            jobs.forEach { it.result }
            reference.setTarget(null)
            assertNull(reference.tryGetTarget())
        } finally {
            workers.forEach { it.requestTermination().result }
        }
    }

    private class StressState(
        val reference: WeakReference<Any>,
        val targets: Array<Any>,
        val ready: AtomicInt,
        val index: Int,
        val lock: PlatformWeakReferenceLock,
        val active: AtomicInt,
    )
}
