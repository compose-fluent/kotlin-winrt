package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.PlatformRuntime
import io.github.kitectlab.winrt.projections.windows.`data`.json.JsonValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MainTest {
    @Test
    fun sample_module_is_wired() {
        assertEquals("io.github.kitectlab.winrt.samples", Main::class.java.`package`.name)
        assertFalse(PlatformRuntime.osName.isBlank())
    }

    @Test
    fun json_sample_keeps_cswinrt_api_compat_shape() {
        assertTrue(JsonApiCompatSample.sampleText.contains("\"id\": \"1146217767\""))
        assertTrue(JsonApiCompatSample.sampleText.contains("\"phone\": null"))
        assertTrue(JsonApiCompatSample.sampleText.contains("\"education\""))
        assertTrue(JsonApiCompatSample.sampleText.contains("\"verified\": true"))
    }

    @Test
    fun net_projection_sample_keeps_cswinrt_simple_math_call_flow() {
        assertEquals(
            "io.github.kitectlab.winrt.projections.simplemathcomponent.SimpleMath",
            NetProjectionSample.componentRuntimeClass,
        )
        assertEquals(false, shouldRunComponentSmoke())
    }

    @Test
    fun windows_data_json_sample_matches_cswinrt_api_compat_call_flow() {
        assumeTrue(PlatformRuntime.isWindows && shouldRunNativeSmoke())

        val result = JsonApiCompatSample.run()

        assertEquals("1146217767", result.id)
        assertEquals(JsonValueType.Null, result.nullValueType)
        assertEquals(true, result.verified)
        assertEquals("High School", result.firstEducationType)
    }

    @Test
    fun net_projection_sample_consumes_simple_math_component_when_enabled() {
        assumeTrue(PlatformRuntime.isWindows && shouldRunComponentSmoke())

        val result = NetProjectionSample.add()

        assertEquals("5.5 + 6.5", result.expression)
        assertEquals(12.0, result.value, 0.0)
    }
}

private object Main
