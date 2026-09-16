package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class WinRTAbiFloatingCallbackTest {
    @Test
    fun floating_registers_and_stack_survive_the_callback_context_shift() {
        // CsWinRT typed unmanaged entry points receive Float/Double in their positional XMM
        // registers. The Native generic callback adapter must preserve those bits in words.
        val iid = Guid("dd8344aa-9c6a-4d53-a57a-082108b9f40c")
        var received: List<Any?>? = null
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(WinRTInspectableInterfaceDefinition(
                iid,
                listOf(WinRTInspectableMethodDefinition(
                    ComMethodSignature.of(ComAbiValueKind.Float, ComAbiValueKind.Double, ComAbiValueKind.Float, ComAbiValueKind.Double),
                    handler = { arguments -> received = arguments; 0 },
                )),
            )),
            defaultInterfaceId = iid,
        ).use { host ->
            host.createPrimaryReference().use { reference ->
                assertEquals(0, invokeFloatingCallback(reference.pointer, 6, 1.25f, -2.5, -0.0f, 456.125))
                val values = requireNotNull(received)
                assertEquals(1.25f, values[0])
                assertEquals(-2.5, values[1])
                assertEquals((-0.0f).toBits(), (values[2] as Float).toBits())
                assertEquals(456.125, values[3])
            }
        }
    }
}

@WinRTAbiCallSite
private fun invokeFloatingCallback(
    instance: RawComPtr,
    slot: Int,
    first: Float,
    second: Double,
    third: Float,
    fourth: Double,
): Int = TODO("Fixed WinRT ABI call")
