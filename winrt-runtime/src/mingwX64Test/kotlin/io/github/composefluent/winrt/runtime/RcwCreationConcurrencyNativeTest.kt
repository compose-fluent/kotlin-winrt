@file:OptIn(kotlin.native.concurrent.ObsoleteWorkersApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import platform.windows.GetTickCount64
import platform.windows.Sleep

class RcwCreationConcurrencyNativeTest {
    // CsWinRT RuntimeWrapperCache: independent identities must not share a factory lock.
    @Test
    fun distinct_identity_creation_overlaps_on_native_workers() {
        val locks = RcwCreationLocks()
        val entered = AtomicInt(0)
        val workers = List(2) { Worker.start() }
        try {
            val futures = workers.mapIndexed { index, worker ->
                worker.execute(TransferMode.SAFE, { Triple(locks, entered, index.toLong()) }) { (locks, entered, key) ->
                    locks.withIdentity(key) {
                        entered.fetchAndAdd(1)
                        val deadline = GetTickCount64() + 10_000uL
                        while (entered.load() < 2 && GetTickCount64() < deadline) Sleep(1u)
                        check(entered.load() == 2) { "Unrelated Native RCW factories were serialized" }
                        locks.withIdentity(key) { key }
                    }
                }
            }
            assertEquals(listOf(0L, 1L), futures.map { it.result })
        } finally {
            workers.forEach { it.requestTermination().result }
        }
    }
}
