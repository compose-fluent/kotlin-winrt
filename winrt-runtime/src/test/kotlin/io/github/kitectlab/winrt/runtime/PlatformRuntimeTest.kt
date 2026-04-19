package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformRuntimeTest {
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
