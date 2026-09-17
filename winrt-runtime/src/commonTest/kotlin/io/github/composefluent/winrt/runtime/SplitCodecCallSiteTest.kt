package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val SPLIT_VALUE = "io.github.composefluent.winrt.runtime.SplitCodecValue"
private const val SPLIT_ALIAS = "test.SplitCodecAlias"
private const val SPLIT_STRUCT = "io.github.composefluent.winrt.runtime.SplitOwnedString"

@WinRTProjectionAbiType(name = SPLIT_STRUCT, kind = WinRTProjectionAbiTypeKind.STRUCT, size = 8, alignment = 8)
private data class SplitOwnedString(val value: String)

private object SplitStructEncoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.COPY_TO_ABI, type = SPLIT_STRUCT)
    fun encode(value: SplitOwnedString, address: RawAddress) {
        PlatformAbi.writePointer(address, HString.create(value.value).handle)
    }
}

private object SplitStructDecoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.FROM_ABI, type = SPLIT_STRUCT)
    fun decode(address: RawAddress): SplitOwnedString =
        SplitOwnedString(NativeStringMarshaller.fromAbi(PlatformAbi.readPointer(address)))
}

private object SplitStructDisposer {
    var count = 0

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.DISPOSE_ABI, type = SPLIT_STRUCT)
    fun dispose(address: RawAddress) {
        NativeStringMarshaller.disposeAbi(PlatformAbi.readPointer(address))
        PlatformAbi.writePointer(address, PlatformAbi.nullPointer)
        count++
    }
}

@WinRTProjectionCallSite
private fun splitStructRoundTrip(receiver: ComObjectReference, slot: Int, value: SplitOwnedString): SplitOwnedString =
    TODO("Conversion and owned ABI cleanup have independent owners")

@WinRTProjectionAbiType(name = SPLIT_VALUE, kind = WinRTProjectionAbiTypeKind.ENUM, carrier = WinRTProjectionAbiCarrier.INT32)
private data class SplitCodecValue(val value: Int)

private object SplitCodecEncoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.TO_ABI, type = SPLIT_VALUE)
    fun encode(value: SplitCodecValue): Int = value.value
}

private object SplitCodecDecoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.FROM_ABI, type = SPLIT_VALUE)
    fun decode(value: Int): SplitCodecValue = SplitCodecValue(value)
}

@WinRTProjectionAbiType(name = SPLIT_ALIAS, kind = WinRTProjectionAbiTypeKind.ENUM, carrier = WinRTProjectionAbiCarrier.INT32)
private object SplitAliasEncoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.TO_ABI, type = SPLIT_ALIAS)
    fun encode(value: SplitCodecValue): Int = value.value + 100
}

private object SplitAliasDecoder {
    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.FROM_ABI, type = SPLIT_ALIAS)
    fun decode(value: Int): SplitCodecValue = SplitCodecValue(value - 100)
}

@WinRTProjectionCallSite
private fun splitCodecRoundTrip(receiver: ComObjectReference, slot: Int, value: SplitCodecValue): SplitCodecValue =
    TODO("Lowered from independent codec symbols")

@WinRTProjectionCallSite(returnAbiType = SPLIT_ALIAS)
private fun splitAliasRoundTrip(receiver: ComObjectReference, slot: Int,
    @WinRTProjectionParameter(abiType = SPLIT_ALIAS) value: SplitCodecValue): SplitCodecValue =
    TODO("The recipe cache must distinguish this explicit ABI identity")

@WinRTAbiCallSite
private fun legacyRawCall(receiver: RawComPtr, slot: Int, value: Int, result: RawAddress): Int =
    TODO("Legacy fixed ABI entry")

@WinRTProjectionCallSite(sourceGenerated = true)
private fun legacySourceCall(receiver: ComObjectReference, slot: Int, value: Int): Int =
    withWinRTAbiReference(receiver) { pointer ->
        withWinRTScalarResult { result ->
            HResult(legacyRawCall(pointer, slot, value, result)).requireSuccess()
            PlatformAbi.readInt32(result)
        }
    }

class SplitCodecCallSiteTest {
    @Test
    fun split_struct_codecs_release_inputs_and_outputs_on_success_and_failure() {
        // CsWinRT Marshalers.cs: copy the projected result, then dispose owned ABI storage.
        val iid = Guid("87c58bdb-8905-452e-bae1-82230c80df42")
        var fail = false
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer)) { args ->
            val input = NativeStringMarshaller.fromAbi(PlatformAbi.readPointer(args[0] as RawAddress))
            PlatformAbi.writePointer(args[1] as RawAddress, HString.create(input).handle)
            if (fail) KnownHResults.E_FAIL.value else 0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { receiver ->
                SplitStructDisposer.count = 0
                val value = SplitOwnedString("owned")
                assertEquals(value, splitStructRoundTrip(receiver, 6, value))
                assertEquals(2, SplitStructDisposer.count)
                fail = true
                assertFailsWith<WinRTRuntimeException> { splitStructRoundTrip(receiver, 6, value) }
                assertEquals(4, SplitStructDisposer.count)
            }
        }
    }

    @Test
    fun codec_roles_can_have_different_owners_and_cache_keeps_abi_identities_separate() {
        // CsWinRT marshalers own conversion operations independently of projected declaration placement.
        val iid = Guid("7f7322b8-ece6-4656-b826-6d2a9131d6e8")
        var received = 0
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer)) { args ->
            received = args[0] as Int
            PlatformAbi.writeInt32(args[1] as RawAddress, received)
            0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))), defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { receiver ->
                val value = SplitCodecValue(-39)
                repeat(2) {
                    assertEquals(value, splitCodecRoundTrip(receiver, 6, value))
                    assertEquals(-39, received)
                    assertEquals(value, splitAliasRoundTrip(receiver, 6, value))
                    assertEquals(61, received)
                }
                assertEquals(21, legacySourceCall(receiver, 6, 21))
            }
        }
    }
}
