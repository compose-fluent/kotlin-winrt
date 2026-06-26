package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonObjectInteropTest {
    private val iidIJsonObjectStatics = Guid("2289F159-54DE-45D8-ABCC-22603FA066A0")

    @Test
    fun can_parse_json_via_static_interface_and_read_property() {
        if (!PlatformRuntime.isWindows) {
            return
        }

        RuntimeScope.initializeMultithreaded().use {
            val statics = WinRTRuntime.getActivationFactory(
                runtimeClassName = "Windows.Data.Json.JsonObject",
                interfaceId = iidIJsonObjectStatics,
            ).getOrThrow()
            try {
                val instance = HString.create("""{"name":"codex","kind":"winrt"}""").use { hString ->
                    PlatformAbi.confinedScope().use { scope ->
                        val resultOut = PlatformAbi.allocatePointerSlot(scope)
                        val hr = ComVtableInvoker.invokeArgs(statics.pointer, 6, hString.handle, resultOut)
                        HResult(hr).requireSuccess()
                        IUnknownReference(PlatformAbi.readPointer(resultOut).asRawComPtr())
                    }
                }
                try {
                    val parsedName = HString.create("name").use { hString ->
                        PlatformAbi.confinedScope().use { scope ->
                            val resultOut = PlatformAbi.allocatePointerSlot(scope)
                            val hr = ComVtableInvoker.invokeArgs(instance.pointer, 10, hString.handle, resultOut)
                            HResult(hr).requireSuccess()
                            HString.fromHandle(PlatformAbi.readPointer(resultOut), owner = true).use { it.toKString() }
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
