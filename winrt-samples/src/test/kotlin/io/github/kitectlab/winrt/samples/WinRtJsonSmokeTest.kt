package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.PlatformRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class WinRtJsonSmokeTest {
    @Test
    fun can_parse_json_through_real_winrt_api() {
        assumeTrue(PlatformRuntime.isWindows)

        val result = WinRtJsonSmoke.run()
        assertFalse(result.runtimeClass.isBlank())
        assertEquals("Windows.Data.Json.JsonObject", result.runtimeClass)
        assertEquals("codex", result.parsedName)
    }
}
