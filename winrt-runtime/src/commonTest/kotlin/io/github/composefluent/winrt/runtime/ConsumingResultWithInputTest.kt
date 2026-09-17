package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class ConsumedInputResult(val pointerKey: Long)

@WinRTProjectionAbiType(
    name = "io.github.composefluent.winrt.runtime.ConsumedInputResult",
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
    reference = WinRTProjectionAbiReferenceKind.UNKNOWN,
)
private object ConsumedInputResultCodec {
    var failDecode = false
    var decodes = 0

    @WinRTProjectionAbiCodec(
        role = WinRTProjectionAbiCodecRole.FROM_ABI,
        type = "io.github.composefluent.winrt.runtime.ConsumedInputResult",
        consumesOwnedAbi = true,
    )
    fun fromAbi(abi: RawAddress): ConsumedInputResult = try {
        decodes++
        check(!failDecode) { "decode failure" }
        ConsumedInputResult(PlatformAbi.pointerKey(abi))
    } finally {
        if (!PlatformAbi.isNull(abi)) WinRTPlatformApi.releaseRaw(abi)
    }
}

@WinRTProjectionCallSite
private fun consumeAndReturn(
    receiver: ComObjectReference,
    slot: Int,
    @WinRTProjectionParameter(abiType = "System.Object") value: Any?,
): ConsumedInputResult = TODO("lower consuming result with live input")

@WinRTProjectionCallSite
private fun consumeWithOuts(
    receiver: ComObjectReference,
    slot: Int,
    @WinRTProjectionParameter(abiType = "System.Object") value: Any?,
    @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.OUT) first: WinRTOut<ConsumedInputResult>,
    @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.OUT) second: WinRTOut<Int>,
): Unit = TODO()

@WinRTCallerOwnedResult
private class ConsumedGroup(val first: ConsumedInputResult, val second: Int) {
    init { check(second != -1) { "assembly failure" } }
}

@WinRTProjectionCallSite(result = WinRTCallSiteResultKind.CALLER_OWNED)
private fun consumeWithGroup(
    receiver: ComObjectReference,
    slot: Int,
    @WinRTProjectionParameter(abiType = "System.Object") value: Any?,
): ConsumedGroup = TODO()

class ConsumingResultWithInputTest {
    @Test
    fun consuming_outs_transfer_before_decode_and_caller_assembly_can_fail() {
        // CsWinRT separates FromAbi ownership from subsequent managed result assembly.
        val iid = Guid("f6422dc0-d6fa-42a3-a368-9f6c9caf72d1")
        var number = 17
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(
            ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer,
        )) { args ->
            val pointer = args[0] as RawAddress
            WinRTPlatformApi.addRefRaw(pointer)
            PlatformAbi.writePointer(args[1] as RawAddress, pointer)
            PlatformAbi.writeInt32(args[2] as RawAddress, number)
            0
        }
        WinRTInspectableComObject.inspectableBox(Any()).use { inputHost ->
            inputHost.createPrimaryReference().use { input ->
                val pointer = input.pointer.asRawAddress()
                val before = WinRTInspectableComObject.tryProbeReferenceCount(pointer)
                WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))),
                    defaultInterfaceId = iid).use { host ->
                    host.createPrimaryReference().use { receiver ->
                        try {
                            val first = WinRTOut<ConsumedInputResult>()
                            val second = WinRTOut<Int>()
                            consumeWithOuts(receiver, 6, input, first, second)
                            assertEquals(17, second.value)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            ConsumedInputResultCodec.failDecode = true
                            assertFailsWith<IllegalStateException> {
                                consumeWithOuts(receiver, 6, input, WinRTOut(), WinRTOut())
                            }
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            assertFailsWith<IllegalStateException> { consumeWithGroup(receiver, 6, input) }
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            ConsumedInputResultCodec.failDecode = false
                            number = -1
                            assertFailsWith<IllegalStateException> { consumeWithGroup(receiver, 6, input) }
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                        } finally { ConsumedInputResultCodec.failDecode = false }
                    }
                }
            }
        }
    }

    @Test
    fun input_owner_does_not_change_consuming_result_ownership() {
        // CsWinRT code_writers.h keeps input lifetime separate from output marshaling.
        val iid = Guid("37e55af0-de44-4677-b336-e2e65b8c5b01")
        var failCall = false
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(
            ComAbiValueKind.Pointer, ComAbiValueKind.Pointer,
        )) { args ->
            val pointer = args[0] as RawAddress
            WinRTPlatformApi.addRefRaw(pointer)
            PlatformAbi.writePointer(args[1] as RawAddress, pointer)
            if (failCall) KnownHResults.E_FAIL.value else 0
        }
        WinRTInspectableComObject.inspectableBox(Any()).use { inputHost ->
            inputHost.createPrimaryReference().use { input ->
                val pointer = input.pointer.asRawAddress()
                val before = checkNotNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))),
                    defaultInterfaceId = iid).use { host ->
                    host.createPrimaryReference().use { receiver ->
                        ConsumedInputResultCodec.decodes = 0
                        try {
                            assertEquals(PlatformAbi.pointerKey(pointer), consumeAndReturn(receiver, 6, input).pointerKey)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            ConsumedInputResultCodec.failDecode = true
                            assertFailsWith<IllegalStateException> { consumeAndReturn(receiver, 6, input) }
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            failCall = true
                            assertFailsWith<WinRTRuntimeException> { consumeAndReturn(receiver, 6, input) }
                            assertEquals(2, ConsumedInputResultCodec.decodes)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                        } finally {
                            ConsumedInputResultCodec.failDecode = false
                        }
                    }
                }
            }
        }
    }
}
