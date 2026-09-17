package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

private class InboundIdentityTarget {
    var signed = 0
    var unsigned = 0u
}

@WinRTProjectionInboundCallSite
private fun consumeIdentity(
    target: InboundIdentityTarget,
    @WinRTProjectionParameter(abiType = "System.Int32") value: Int,
) {
    target.signed = value
    TODO("lower signed inbound entry")
}

@WinRTProjectionInboundCallSite
private fun consumeIdentity(
    target: InboundIdentityTarget,
    @WinRTProjectionParameter(abiType = "System.UInt32") value: UInt,
) {
    target.unsigned = value
    TODO("lower unsigned inbound entry")
}

class InboundEntryIdentityTest {
    @Test
    fun semantic_function_without_abi_entry_remains_callable() {
        val target = InboundIdentityTarget()
        consumeManagedOnly(target)
        assertEquals(9, target.signed)
    }

    @Test
    fun equal_abi_overloads_and_immutable_aliases_preserve_managed_dispatch() {
        // CsWinRT Do_Abi entries identify semantic methods, even when ABI carriers agree.
        val signed: (InboundIdentityTarget, Int) -> Unit = ::consumeIdentity
        val signedAlias = signed
        val unsigned: (InboundIdentityTarget, UInt) -> Unit = ::consumeIdentity
        val target = InboundIdentityTarget()
        val iid = Guid("723801ca-a091-44e5-a301-432f624580b5")
        val methods = listOf(
            WinRTInspectableMethodDefinition(
                ComMethodSignature.of(ComAbiValueKind.Int32),
                abiEntryPoint = winRTProjectionInboundEntryPoint(signedAlias),
            ),
            WinRTInspectableMethodDefinition(
                ComMethodSignature.of(ComAbiValueKind.Int32),
                abiEntryPoint = winRTProjectionInboundEntryPoint(unsigned),
            ),
        )
        WinRTInspectableComObject(
            listOf(WinRTInspectableInterfaceDefinition(iid, methods)),
            defaultInterfaceId = iid,
            managedValue = target,
        ).use { host ->
            host.createPrimaryReference().use { receiver ->
                HResult(ComVtableInvoker.invokeArgs(receiver.pointer, 6, -7)).requireSuccess()
                HResult(ComVtableInvoker.invokeArgs(receiver.pointer, 7, -1)).requireSuccess()
                assertEquals(-7, target.signed)
                assertEquals(UInt.MAX_VALUE, target.unsigned)
            }
        }
    }
}

@WinRTProjectionInboundCallSite
private fun consumeManagedOnly(target: InboundIdentityTarget) {
    target.signed = 9
    TODO("inbound")
}
