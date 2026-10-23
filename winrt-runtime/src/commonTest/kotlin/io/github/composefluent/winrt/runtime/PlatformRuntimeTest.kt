package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformRuntimeTest {
    @Test
    fun copies_native_byte_ranges_without_aliasing_the_owner() {
        // CsWinRT WindowsRuntimeBufferExtensions.CopyTo copies a span while the owner is leased.
        PlatformAbi.confinedScope().use { scope ->
            val source = PlatformAbi.allocateBytes(scope, 4096)
            repeat(4096) { PlatformAbi.writeInt8(PlatformAbi.slice(source, it.toLong(), 1), (it % 251).toByte()) }
            val copy = PlatformAbi.readBytes(PlatformAbi.slice(source, 13, 2000), 2000)
            kotlin.test.assertContentEquals(ByteArray(2000) { ((it + 13) % 251).toByte() }, copy)
            PlatformAbi.writeInt8(PlatformAbi.slice(source, 13, 1), 0)
            kotlin.test.assertEquals(13.toByte(), copy[0])
            kotlin.test.assertTrue(PlatformAbi.readBytes(source, 0).isEmpty())
            kotlin.test.assertFailsWith<IllegalArgumentException> { PlatformAbi.readBytes(source, -1) }
        }
    }

    @Test
    fun detects_single_host_os_family() {
        val matches = listOf(
            PlatformRuntime.isWindows,
            PlatformRuntime.isLinux,
            PlatformRuntime.isMacOs,
        ).count { it }

        assertTrue(matches in 0..1)
        assertFalse(PlatformRuntime.osName.isBlank())
    }
}
