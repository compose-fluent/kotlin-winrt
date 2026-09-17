package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@WinRTProjectionCallSite
private fun callWithStrings(receiver: ComObjectReference, slot: Int, first: String?, second: String) {
    TODO("Pure block placeholder; empty inputs do not acquire HSTRING frames")
}

class EmptyHStringCallSiteTest {
    @Test
    fun empty_inputs_leave_the_next_hstring_frame_available_during_the_call() {
        // CsWinRT Marshalers.cs MarshalString.CreateMarshaler returns null for null/empty.
        // Probe the next available frame before and inside the ABI callback, without counters
        // or reflection into the pool. Native's direct string transport does not use this pool.
        val nextFrame = acquireInitializedNativeHStringReferenceFrame("probe").use { it.transientOut }
        var expectedFirst = ""
        var expectedSecond = ""
        val iid = Guid("e2e6dbb3-8962-40b2-973c-8909f4d515e3")
        val method = WinRTInspectableMethodDefinition(
            ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
        ) { args ->
            assertEquals(expectedFirst, NativeStringMarshaller.fromAbi(args[0] as RawAddress))
            assertEquals(expectedSecond, NativeStringMarshaller.fromAbi(args[1] as RawAddress))
            acquireInitializedNativeHStringReferenceFrame("probe").use { frame ->
                if (expectedFirst.isEmpty() && expectedSecond.isEmpty()) {
                    assertEquals(nextFrame, frame.transientOut)
                } else {
                    assertNotEquals(nextFrame, frame.transientOut)
                }
            }
            0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { receiver ->
                repeat(2) {
                    for ((first, second) in listOf(null to "", "" to "", "input" to "", null to "input")) {
                        expectedFirst = first.orEmpty()
                        expectedSecond = second
                        callWithStrings(receiver, 6, first, second)
                    }
                }
            }
        }
    }
}
