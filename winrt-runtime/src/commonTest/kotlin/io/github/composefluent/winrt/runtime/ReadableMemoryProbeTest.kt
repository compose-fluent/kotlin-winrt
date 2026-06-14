package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadableMemoryProbeTest {
    @Test
    fun readable_memory_probe_accepts_allocated_memory() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        PlatformAbi.confinedScope().use { scope ->
            val memory = PlatformAbi.allocateBytes(scope, 16)

            assertTrue(WinRtPlatformApi.isReadableMemoryRaw(memory, 16))
        }
    }

    @Test
    fun readable_memory_probe_rejects_null_and_empty_ranges() {
        if (!PlatformRuntime.isWindows) {
            return
        }
        PlatformAbi.confinedScope().use { scope ->
            val memory = PlatformAbi.allocateBytes(scope, 16)

            assertFalse(WinRtPlatformApi.isReadableMemoryRaw(PlatformAbi.nullPointer, 1))
            assertFalse(WinRtPlatformApi.isReadableMemoryRaw(memory, 0))
        }
    }
}
