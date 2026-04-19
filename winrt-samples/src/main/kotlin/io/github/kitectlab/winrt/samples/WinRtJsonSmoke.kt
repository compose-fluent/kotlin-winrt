package io.github.kitectlab.winrt.samples

import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.JvmComRuntime
import io.github.kitectlab.winrt.runtime.JvmWinRtRuntime
import io.github.kitectlab.winrt.runtime.KnownHResults

data class WinRtJsonSmokeResult(
    val runtimeClass: String,
    val parsedName: String,
)

object WinRtJsonSmoke {
    private const val jsonObjectRuntimeClass = "Windows.Data.Json.JsonObject"
    private val iidIJsonObjectStatics = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

    fun run(): WinRtJsonSmokeResult {
        val comResult = JvmComRuntime.initializeMultithreaded()
        val roResult = JvmWinRtRuntime.initializeMultithreaded()
        val shouldUninitializeCom = comResult.isSuccess
        val shouldUninitializeRo = roResult.isSuccess

        try {
            val factory = JvmWinRtRuntime.getActivationFactory(
                runtimeClassName = jsonObjectRuntimeClass,
                interfaceId = iidIJsonObjectStatics,
            ).getOrThrow()
            try {
                val instance = factory.invokeObjectMethodWithStringArg(6, """{"name":"codex","kind":"winrt"}""")
                try {
                    val inspectable = instance.asInspectable()
                    val value = instance.invokeHStringMethodWithStringArg(10, "name")
                    return inspectable.use { typedInstance ->
                        value.use {
                            WinRtJsonSmokeResult(
                                runtimeClass = typedInstance.getRuntimeClassName().orEmpty(),
                                parsedName = it.toKString(),
                            )
                        }
                    }
                } finally {
                    instance.close()
                }
            } finally {
                factory.close()
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
