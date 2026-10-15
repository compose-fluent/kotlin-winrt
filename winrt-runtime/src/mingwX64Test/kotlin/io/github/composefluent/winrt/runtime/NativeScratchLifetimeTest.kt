@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@file:Suppress("DEPRECATION")

package io.github.composefluent.winrt.runtime

import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeScratchLifetimeTest {
    @Test
    fun thread_local_scratch_storage_is_released_after_worker_exit() {
        // CsWinRT uses stack-local ABI outputs. Kotlin's reusable native buffers
        // must similarly stop owning memory when their thread-local frames die.
        val worker = Worker.start()
        val allocations = try {
            worker.execute(TransferMode.SAFE, { Unit }) {
                val scalar = acquireNativeScalarScratchFrame().use { frame ->
                    PlatformAbi.writeInt64(frame.pointer, 42L)
                    assertEquals(42L, frame.readInt64())
                    frame.allocationOwner
                }
                acquireNativeStructScratchFrame(4L, 4L).close()
                val struct = acquireNativeStructScratchFrame(128L * 1024L, 8L).use { frame ->
                    PlatformAbi.writeInt64(frame.pointer, 99L)
                    assertEquals(99L, frame.readInt64Carrier())
                    assertEquals(frame.pointer.value, frame.allocationOwner.address)
                    frame.allocationOwner
                }
                val string = acquireNativeHStringReferenceFrame("kept").use { frame ->
                    assertEquals("kept", PlatformAbi.readUtf16(frame.utf16Chars, 4))
                    frame.allocationOwner
                }
                listOf(scalar, struct, string).also { states ->
                    assertTrue(states.all { it.address != 0L })
                }
            }.result
        } finally {
            worker.requestTermination().result
        }
        repeat(20) {
            PlatformFinalization.drain()
            if (allocations.all { it.address == 0L }) return
        }
        assertTrue(allocations.all { it.address == 0L }, "Exited worker retained native scratch allocations")
    }
}
