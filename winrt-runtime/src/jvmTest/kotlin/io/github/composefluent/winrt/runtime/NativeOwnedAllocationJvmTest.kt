package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class NativeOwnedAllocationJvmTest {
    @Test
    fun ownedAllocationUsesGlobalViewAndCanBeFreedFromAnotherThread() {
        val allocation = PlatformAbi.allocateBytesOwned(sizeBytes = 64L, alignmentBytes = 32L)
        assertEquals(0L, allocation.pointer.value % 32L)
        assertEquals(0L, PlatformAbi.readInt64(allocation.pointer))
        assertSame(Arena.global().scope(), allocation.memory.segment.scope())

        allocation.memory.writeInt64(offsetBytes = 8L, value = 0x1020304050607080L)
        assertEquals(
            0x1020304050607080L,
            PlatformAbi.readInt64(RawAddress(allocation.pointer.value + 8L)),
        )

        val closeFailure = AtomicReference<Throwable?>()
        val closeThread = Thread {
            closeFailure.set(runCatching(allocation::close).exceptionOrNull())
        }
        closeThread.start()
        closeThread.join()
        assertNull(closeFailure.get())
    }
}
