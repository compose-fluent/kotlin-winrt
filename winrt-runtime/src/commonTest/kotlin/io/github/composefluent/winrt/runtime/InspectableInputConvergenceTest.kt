package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private const val TRACKED_OBJECT = "test.TrackedInspectableInput"

@WinRTProjectionAbiType(
    name = TRACKED_OBJECT,
    kind = WinRTProjectionAbiTypeKind.PROJECTION,
    carrier = WinRTProjectionAbiCarrier.ADDRESS,
    reference = WinRTProjectionAbiReferenceKind.INSPECTABLE,
)
internal object TrackedInspectableInputCodec {
    val events = mutableListOf<String>()
    var failOn: Any? = null

    @WinRTProjectionAbiCodec(role = WinRTProjectionAbiCodecRole.CREATE_MARSHALER, type = TRACKED_OBJECT)
    fun createMarshaler(value: Any?): WinRTObjectMarshaler {
        if (value === failOn) error("input preparation failed")
        val inner = WinRTObjectMarshaller.createMarshaler(value)
        events += "create"
        return WinRTObjectMarshaler(inner.abi) {
            events += "close"
            inner.close()
        }
    }
}

@WinRTProjectionCallSite
private fun consumeTrackedInputs(
    receiver: ComObjectReference,
    slot: Int,
    @WinRTProjectionParameter(abiType = TRACKED_OBJECT) first: Any?,
    @WinRTProjectionParameter(abiType = TRACKED_OBJECT) second: Any?,
    @WinRTProjectionParameter(abiType = TRACKED_OBJECT) third: Any?,
): Unit = TODO("lower tracked inspectable inputs")

class InspectableInputConvergenceTest {
    @Test
    fun converged_inputs_preserve_borrowing_and_cleanup_on_all_exit_paths() {
        // CsWinRT code_writers.h: prepare marshalers, invoke once, dispose in finally.
        val iid = Guid("4271cf6c-dcef-44f1-bda1-b208ef29605e")
        var calls = 0
        var fail = false
        val method = WinRTInspectableMethodDefinition(ComMethodSignature.of(
            ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer,
        )) { args ->
            calls++
            assertTrue(PlatformAbi.isNull(args[2] as RawAddress))
            if (fail) KnownHResults.E_FAIL.value else 0
        }
        val managed = Any()
        WinRTInspectableComObject.inspectableBox(managed).use { argumentHost ->
            argumentHost.createPrimaryReference().use { argument ->
                WinRTInspectableComObject(listOf(WinRTInspectableInterfaceDefinition(iid, listOf(method))),
                    defaultInterfaceId = iid).use { host ->
                    host.createPrimaryReference().use { receiver ->
                        val events = TrackedInspectableInputCodec.events
                        val pointer = argument.pointer.asRawAddress()
                        val before = WinRTInspectableComObject.tryProbeReferenceCount(pointer)
                        val cached = ComWrappersSupport.createCCWForObjectForMarshaling(managed, IID.IInspectable)
                        try {
                            assertTrue(!PlatformAbi.isNull(tryBorrowWinRTManagedInspectableAbi(managed)))
                            // ComObjectReference requires an owned factory; managed identity can borrow its CCW.
                            events.clear()
                            consumeTrackedInputs(receiver, 6, argument, managed, null)
                            assertEquals(listOf("create", "close"), events)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            assertEquals(1, calls)

                            events.clear()
                            fail = true
                            assertFailsWith<WinRTRuntimeException> {
                                consumeTrackedInputs(receiver, 6, argument, managed, null)
                            }
                            assertEquals(listOf("create", "close"), events)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            assertEquals(2, calls)

                            events.clear()
                            TrackedInspectableInputCodec.failOn = receiver
                            assertFailsWith<IllegalStateException> {
                                consumeTrackedInputs(receiver, 6, argument, receiver, null)
                            }
                            assertEquals(listOf("create", "close"), events)
                            assertEquals(before, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
                            assertEquals(2, calls)
                        } finally {
                            cached.close()
                            TrackedInspectableInputCodec.failOn = null
                            events.clear()
                        }
                    }
                }
            }
        }
    }
}
