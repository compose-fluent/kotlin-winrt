package io.github.composefluent.winrt.benchmarks

import io.github.composefluent.winrt.runtime.RuntimeScope
import kotlin.test.Test
import kotlin.test.assertEquals
import windows.`data`.json.JsonObject

class HStringFrameLoweringTest {
    @Test
    fun generated_call_releases_multiple_hstring_frames_in_reverse_order() {
        RuntimeScope.initializeMultithreaded().use {
            val json = JsonObject.parse("""{"name":"value"}""")

            assertEquals("fallback", json.getNamedString("missing", "fallback"))
            assertEquals("value", json.getNamedString("name", "fallback"))
        }
    }
}
