package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@WinRTProjectionCallSite
private fun callWithReentrantClose(receiver: ComObjectReference, slot: Int): Int =
    TODO("Lowered to a leased projected ABI call")

@WinRTProjectionCallSite
private fun callWithReentrantInputClose(receiver: ComObjectReference, slot: Int, input: IUnknownReference): Int =
    TODO("Lowered to a projected ABI call with a leased COM input")

class ComCallLifetimeTest {
    @Test
    fun generated_call_leases_direct_input_through_failed_callback() {
        val iid = Guid("1581c4af-8941-41be-a594-38e9a8cbb71f")
        lateinit var receiver: ComObjectReference
        lateinit var input: IUnknownReference
        var countBeforeClose = 0u
        var pointer = RawAddress.Null
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)) { args ->
            assertEquals(pointer, args[0])
            countBeforeClose = WinRTInspectableComObject.tryProbeReferenceCount(pointer)!!
            receiver.close()
            input.close()
            assertEquals(countBeforeClose, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
            KnownHResults.E_FAIL.value
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            receiver = host.createPrimaryReference()
            input = IUnknownReference(host.acquireReference(iid).asRawComPtr(), iid)
            pointer = input.pointer.asRawAddress()
            try {
                assertFailsWith<WinRTRuntimeException> { callWithReentrantInputClose(receiver, 6, input) }
                assertEquals(countBeforeClose - 2u, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
            } finally {
                input.close()
                receiver.close()
            }
        }
    }

    @Test
    fun generated_call_keeps_receiver_owned_during_reentrant_close() {
        val iid = Guid("1e909d50-9a47-49b4-9b9e-a3e52621c227")
        lateinit var reference: ComObjectReference
        var countBeforeClose = 0u
        var pointer = RawAddress.Null
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
            countBeforeClose = WinRTInspectableComObject.tryProbeReferenceCount(pointer)!!
            reference.close()
            assertEquals(countBeforeClose, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
            PlatformAbi.writeInt32(args[0] as RawAddress, 42)
            KnownHResults.S_OK.value
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            reference = host.createPrimaryReference()
            pointer = reference.pointer.asRawAddress()
            try {
                assertEquals(42, callWithReentrantClose(reference, 6))
                assertEquals(countBeforeClose - 1u, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                assertFailsWith<WinRTObjectDisposedException> { callWithReentrantClose(reference, 6) }
            } finally {
                reference.close()
            }
        }
    }

    // CsWinRT 3 WindowsRuntimeObjectReference.Lifecycle.cs: closing rejects new
    // leases while the last existing lease performs native disposal exactly once.
    @Test
    fun close_defers_cleanup_until_all_active_calls_return() {
        var releases = 0
        val support = support { releases++ }
        support.beginCall()
        support.beginCall()
        support.close()
        support.close()
        assertTrue(support.isDisposed)
        assertEquals(0, releases)
        assertFailsWith<WinRTObjectDisposedException> { support.beginCall() }
        support.endCall()
        assertEquals(0, releases)
        support.endCall()
        assertEquals(1, releases)
        support.close()
        assertEquals(1, releases)
    }

    @Test
    fun reentrant_close_and_failed_call_release_the_lease() {
        var releases = 0
        val support = support { releases++ }
        assertFailsWith<IllegalArgumentException> {
            support.withCall { pointer ->
                assertEquals(RawComPtr(1), pointer)
                support.close()
                assertEquals(0, releases)
                throw IllegalArgumentException("callback failure")
            }
        }
        assertEquals(1, releases)
    }

    private fun support(afterRelease: () -> Unit) = RawComObjectReferenceSupport(
        pointer = RawComPtr(1),
        interfaceIdLowBits = IID.IUnknown.abiLowBits,
        interfaceIdHighBits = IID.IUnknown.abiHighBits,
        knownInterfaceId = IID.IUnknown,
        preventReleaseOnDispose = true,
        trackContext = false,
        afterRelease = afterRelease,
    )
}
