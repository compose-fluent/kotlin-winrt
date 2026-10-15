@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:Suppress("DEPRECATION")

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NativeCallbackHandleTest {
    @Test
    fun concurrent_close_releases_callback_resources_once() {
        // CsWinRT WindowsRuntimeObjectReference.DisposeUnsafe claims disposal atomically;
        // the Kotlin callback owner has the same once-only obligation as its JVM actual.
        val releases = AtomicInt(0)
        val handle = NativeCallbackHandle(RawAddress.Null) { releases.fetchAndAdd(1) }
        val workers = List(4) { Worker.start() }
        try {
            val jobs = workers.map { worker ->
                worker.execute(TransferMode.SAFE, { handle }) { shared ->
                    repeat(1_000) { shared.close() }
                }
            }
            jobs.forEach { it.result }
            handle.close()
            assertEquals(1, releases.load())
        } finally {
            workers.forEach { it.requestTermination().result }
            handle.close()
        }
    }

    @Test
    fun reentrant_cleanup_that_throws_remains_closed() {
        var releases = 0
        lateinit var handle: NativeCallbackHandle
        handle = NativeCallbackHandle(RawAddress.Null) {
            releases++
            handle.close()
            error("cleanup failed")
        }
        assertFailsWith<IllegalStateException> { handle.close() }
        handle.close()
        assertEquals(1, releases)
    }
}
