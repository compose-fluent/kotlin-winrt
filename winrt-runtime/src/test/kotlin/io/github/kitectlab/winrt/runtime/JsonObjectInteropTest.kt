package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class JsonObjectInteropTest {
    private val iidIJsonObjectStatics = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

    @Test
    fun can_parse_json_via_static_interface_and_read_property() {
        assumeTrue(PlatformRuntime.isWindows)

        RuntimeScope.initializeMultithreaded().use {
            val statics = JvmWinRtRuntime.getActivationFactory(
                runtimeClassName = "Windows.Data.Json.JsonObject",
                interfaceId = iidIJsonObjectStatics,
            ).getOrThrow()
            try {
                val instance = statics.invokeObjectMethodWithStringArg(6, """{"name":"codex","kind":"winrt"}""")
                try {
                    val parsedName = instance.invokeHStringMethodWithStringArg(10, "name").use { it.toKString() }
                    val runtimeClass = instance.asInspectable().use { it.getRuntimeClassName() }
                    assertEquals("codex", parsedName)
                    assertEquals("Windows.Data.Json.JsonObject", runtimeClass)
                } finally {
                    instance.close()
                }
            } finally {
                statics.close()
            }
        }
    }
}
