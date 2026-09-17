package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class PartialCallSiteStruct

@WinRTProjectionAbiType(name = "io.github.composefluent.winrt.runtime.PartialCallSiteStruct",
    kind = WinRTProjectionAbiTypeKind.STRUCT, size = 16, alignment = 8)
private object PartialCallSiteStructCodec {
    var cleanups = 0

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.COPY_TO_ABI,
        type = "io.github.composefluent.winrt.runtime.PartialCallSiteStruct")
    fun copy(value: PartialCallSiteStruct, destination: RawAddress) {
        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(destination)))
        PlatformAbi.writePointer(destination, HString.create("partial input").handle)
        error("copy failed")
    }

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.FROM_ABI,
        type = "io.github.composefluent.winrt.runtime.PartialCallSiteStruct")
    fun read(source: RawAddress): PartialCallSiteStruct = PartialCallSiteStruct()

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.DISPOSE_ABI,
        type = "io.github.composefluent.winrt.runtime.PartialCallSiteStruct")
    fun dispose(source: RawAddress) {
        cleanups++
        assertTrue(PlatformAbi.isNull(PlatformAbi.readPointer(PlatformAbi.slice(source, 8, 8))))
        HString.fromHandle(PlatformAbi.readPointer(source), owner = true).close()
    }
}

@WinRTProjectionCallSite
private fun partialStructIn(receiver: ComObjectReference, slot: Int, value: PartialCallSiteStruct): Unit = TODO()

@WinRTProjectionCallSite
private fun partialStructRef(receiver: ComObjectReference, slot: Int,
    @WinRTProjectionParameter(direction = WinRTCallSiteParameterDirection.REF) value: PartialCallSiteStruct,
): Unit = TODO()

class PartialStructCallSiteTest {
    @Test
    fun initialization_failure_disposes_partial_input_and_ref_storage() {
        // CsWinRT struct CreateMarshaler disposes the successfully initialized fields on failure.
        val iid = Guid("1b231bf4-ffbb-4db8-9db1-7517617e70b0")
        var calls = 0
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(ComAbiValueKind.Pointer)) {
            calls++
            0
        }
        WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))),
            defaultInterfaceId = iid).use { host ->
            host.createPrimaryReference().use { receiver ->
                PartialCallSiteStructCodec.cleanups = 0
                repeat(2) {
                    assertEquals("copy failed", assertFailsWith<IllegalStateException> {
                        partialStructIn(receiver, 6, PartialCallSiteStruct())
                    }.message)
                    assertEquals("copy failed", assertFailsWith<IllegalStateException> {
                        partialStructRef(receiver, 6, PartialCallSiteStruct())
                    }.message)
                }
                assertEquals(4, PartialCallSiteStructCodec.cleanups)
                assertEquals(0, calls)
            }
        }
    }
}
