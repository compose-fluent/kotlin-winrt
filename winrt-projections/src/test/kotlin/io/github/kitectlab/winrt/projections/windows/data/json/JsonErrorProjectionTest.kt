package io.github.kitectlab.winrt.projections.windows.data.json

import io.github.kitectlab.winrt.runtime.JvmComRuntime
import io.github.kitectlab.winrt.runtime.JvmWinRtRuntime
import io.github.kitectlab.winrt.runtime.KnownHResults
import io.github.kitectlab.winrt.runtime.PlatformRuntime
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class JsonErrorProjectionTest {
    @Test
    fun can_map_web_json_hresult_to_json_error_status() {
        assumeTrue(PlatformRuntime.isWindows)

        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            assertEquals(
                JsonErrorStatus.JsonValueNotFound,
                JsonError.getJsonStatus(KnownHResults.WEB_E_JSON_VALUE_NOT_FOUND.value),
            )
        } finally {
            if (shouldUninitializeRo) {
                JvmWinRtRuntime.uninitialize()
            }
            if (shouldUninitializeCom && roResult != KnownHResults.RPC_E_CHANGED_MODE) {
                JvmComRuntime.uninitialize()
            }
        }
    }
}