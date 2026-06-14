package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
