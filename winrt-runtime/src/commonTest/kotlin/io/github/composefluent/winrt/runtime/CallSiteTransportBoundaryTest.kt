package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

@WinRTProjectionCallSite
private fun sendManyStrings(receiver: ComObjectReference, slot: Int,
    a: String, b: String, c: String, d: String, e: String, f: String,
    g: String, h: String, i: String, j: String, k: String,
): Unit = TODO()

@WinRTAbiCallSite
private fun sendFloatBits(receiver: RawComPtr, slot: Int, f: Float, d: Double): Int = TODO()

internal expect fun largeStringTestMethod(): WinRTInspectableMethodDefinition

class CallSiteTransportBoundaryTest {
    @Test
    fun strings_fall_back_when_expanded_transport_exceeds_native_invoke_arity() {
        // CsWinRT pins/marshals each input independently; a transport optimization must not limit the ABI.
        val values = List(11) { if (it == 4) "" else "字符串-$it" }
        val iid = Guid("6c434478-647a-4a53-8287-15d8a820cb88")
        var calls = 0
        val handler: (List<Any?>) -> Int = { args ->
            calls++
            assertEquals(values, args.map { NativeStringMarshaller.fromAbi(it as RawAddress) })
            0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(largeStringTestMethod()))),
            defaultInterfaceId = iid, managedValue = handler).use { host ->
            host.createPrimaryReference().use { receiver ->
                repeat(2) {
                    sendManyStrings(receiver, 6, values[0], values[1], values[2], values[3], values[4],
                        values[5], values[6], values[7], values[8], values[9], values[10])
                }
                assertEquals(2, calls)
            }
        }
    }

    @Test
    fun floating_point_abi_preserves_nan_payload_bits() {
        // CsWinRT passes floating-point ABI values directly, without normalizing their NaN payload.
        val floatBits = 0x7fc12345
        val doubleBits = 0x7ff8123456789abcL
        val iid = Guid("931c4497-d1d2-40ae-8585-66d07b8d27b1")
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Float, ComAbiValueKind.Double)) { args ->
            assertEquals(floatBits, (args[0] as Float).toRawBits())
            assertEquals(doubleBits, (args[1] as Double).toRawBits())
            0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))),
            defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { receiver ->
                assertEquals(0, sendFloatBits(receiver.pointer, 6, Float.fromBits(floatBits), Double.fromBits(doubleBits)))
            }
        }
    }
}
