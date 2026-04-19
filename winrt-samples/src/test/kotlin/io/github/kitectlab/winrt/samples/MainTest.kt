package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.PlatformRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainTest {
    @Test
    fun sample_module_is_wired() {
        assertEquals("io.github.kitectlab.winrt.samples", Main::class.java.`package`.name)
        assertFalse(PlatformRuntime.osName.isBlank())
    }
}

private object Main
