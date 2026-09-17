package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class DirectCallSiteDelegate(val iid: Guid) : WinRTProjectedDelegate {
    var created = 0
    override fun createWinRTDelegateHandle(): WinRTDelegateHandle {
        created++
        return WinRTDelegateBridge.createUnitDelegate(iid, emptyList()) {}
    }
}

@WinRTProjectionCallSite
private fun passDirectDelegate(receiver: ComObjectReference, slot: Int, value: DirectCallSiteDelegate?): Unit =
    TODO("Lowered to the existing projected delegate factory")

class DirectDelegateCallSiteTest {
    @Test
    fun direct_factory_preserves_null_iid_and_failure_cleanup() {
        // CsWinRT MarshalDelegate.CreateMarshaler: exact delegate IID and call-scoped ownership.
        ComWrappersSupport.clearRegistriesForTests()
        val iid = Guid("0c45fd17-71db-4922-9b5d-69af276774ac")
        val delegate = DirectCallSiteDelegate(Guid("0155127c-e287-4c03-b49f-787601d0658b"))
        var receivedNull = false
        var fail = false
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) { args ->
            val pointer = args[0] as RawAddress
            receivedNull = PlatformAbi.isNull(pointer)
            if (!receivedNull) {
                IUnknownReference(PlatformAbi.toRawComPtr(pointer), preventReleaseOnDispose = true).use { reference ->
                    reference.queryInterface(delegate.iid).getOrThrow().use { queried -> assertTrue(!queried.isDisposed) }
                }
            }
            if (fail) KnownHResults.E_FAIL.value else 0
        }
        try {
            WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
                host.createPrimaryReference().use { receiver ->
                    passDirectDelegate(receiver, 6, null)
                    assertTrue(receivedNull)
                    passDirectDelegate(receiver, 6, delegate)
                    fail = true
                    assertFailsWith<WinRTRuntimeException> { passDirectDelegate(receiver, 6, delegate) }
                    fail = false
                    passDirectDelegate(receiver, 6, delegate)
                    assertEquals(1, delegate.created)
                }
            }
        } finally {
            ComWrappersSupport.clearRegistriesForTests()
        }
    }
}
