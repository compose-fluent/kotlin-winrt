@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObjectReferenceSynchronizationTest {
    @Test
    fun concurrent_close_and_interface_registration_leave_no_owned_child_open() {
        // Kotlin's explicit-close graph extends the CsWinRT RCW ownership graph:
        // registration either transfers ownership before close, or closes its rejected result.
        val owners = Array(100) { reference() }
        val children = Array(3) { Array(owners.size) { reference() } }
        val ready = Array(owners.size) { AtomicInt(0) }
        try {
            runReferenceContentionWorkers(4) { worker ->
                owners.forEachIndexed { i, owner ->
                    ready[i].fetchAndAdd(1)
                    while (ready[i].load() != 4) referenceContentionYield()
                    if (worker == 0) owner.close() else {
                        try {
                            owner.ownInterfaceReference(children[worker - 1][i])
                        } catch (_: WinRTObjectDisposedException) {
                            // Closing won the race; the rejected child must already be closed.
                        }
                    }
                }
            }
            assertTrue(owners.all { it.isDisposed })
            assertTrue(children.all { group -> group.all { it.isDisposed } })
        } finally {
            owners.forEach { it.close() }
            children.forEach { group -> group.forEach { it.close() } }
        }
    }

    @Test
    fun concurrent_initialization_publishes_one_state_and_cache_per_owner() {
        // CsWinRT WindowsRuntimeObject.TypeHandleCache publishes one winner per object.
        val owner = reference()
        val states = arrayOfNulls<WinRTObjectState<ComObjectReference>>(8)
        val caches = arrayOfNulls<ConcurrentCacheMap<WinRTTypeHandle, ComObjectReference>>(8)
        val ready = AtomicInt(0)
        try {
            runReferenceContentionWorkers(8) { index ->
                ready.fetchAndAdd(1)
                while (ready.load() != 8) referenceContentionYield()
                states[index] = owner.projectedObjectState
                caches[index] = states[index]!!.queryInterfaceCache
            }
            states.forEach { assertSame(states[0], it) }
            caches.forEach { assertSame(caches[0], it) }
        } finally {
            owner.close()
        }
    }

    private fun reference() = ComObjectReference(
        ComPtr.create(RawComPtr(1), IID.IUnknown, ComOwnershipMode.Borrowed, trackContext = false),
    )
}
