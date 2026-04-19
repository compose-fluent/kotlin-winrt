package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.foreign.ValueLayout

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
                val instance = HString.create("""{"name":"codex","kind":"winrt"}""").use { hString ->
                    Arena.ofConfined().use { arena ->
                        val resultOut = arena.allocate(ValueLayout.ADDRESS)
                        val hr = statics.invokeAbi(
                            slot = 6,
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                            hString.handle,
                            resultOut,
                        )
                        HResult(hr).requireSuccess()
                        IUnknownReference(resultOut.get(ValueLayout.ADDRESS, 0))
                    }
                }
                try {
                    val parsedName = HString.create("name").use { hString ->
                        Arena.ofConfined().use { arena ->
                            val resultOut = arena.allocate(ValueLayout.ADDRESS)
                            val hr = instance.invokeAbi(
                                slot = 10,
                                descriptor = FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                ),
                                hString.handle,
                                resultOut,
                            )
                            HResult(hr).requireSuccess()
                            HString.fromHandle(resultOut.get(ValueLayout.ADDRESS, 0), owner = true).use { it.toKString() }
                        }
                    }
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
