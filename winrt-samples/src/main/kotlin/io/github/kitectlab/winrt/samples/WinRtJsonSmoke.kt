package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.projections.windows.data.json.JsonObject
import io.github.kitectlab.winrt.runtime.JvmComRuntime
import io.github.kitectlab.winrt.runtime.JvmWinRtRuntime
import io.github.kitectlab.winrt.runtime.KnownHResults

data class WinRtJsonSmokeResult(
    val runtimeClass: String,
    val parsedName: String,
)

object WinRtJsonSmoke {
    fun run(): WinRtJsonSmokeResult {
        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            JsonObject.parse("""{"name":"codex","kind":"winrt"}""").use { jsonObject ->
                return WinRtJsonSmokeResult(
                    runtimeClass = jsonObject.getRuntimeClassName().orEmpty(),
                    parsedName = jsonObject.getNamedString("name"),
                )
            }
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
