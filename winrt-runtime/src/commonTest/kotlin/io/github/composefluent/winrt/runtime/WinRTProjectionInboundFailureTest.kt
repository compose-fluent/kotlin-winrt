package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// cswinrt code_writers.h: write_managed_method_call/write_out_initialize.
// Invoke generated entry points through a real vtable with dirty caller-owned storage.
class WinRTProjectionInboundFailureTest {
    @Test
    fun failed_scalar_returns_zero_only_their_abi_width() {
        val iid = Guid("941ed80a-8dd7-4a81-9e41-11da315fc153")
        val target = FailingInboundTarget()
        val entries = listOf(
            1 to winRTProjectionInboundEntryPoint(::failedInboundBoolean),
            2 to winRTProjectionInboundEntryPoint(::failedInboundShort),
            4 to winRTProjectionInboundEntryPoint(::failedInboundInt),
            8 to winRTProjectionInboundEntryPoint(::failedInboundLong),
            4 to winRTProjectionInboundEntryPoint(::failedInboundFloat),
            8 to winRTProjectionInboundEntryPoint(::failedInboundDouble),
        )
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = iid,
                    methods = entries.map { (_, entry) ->
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = entry,
                        )
                    },
                ),
            ),
            defaultInterfaceId = iid,
            managedValue = target,
        ).use { host ->
            host.createPrimaryReference().use { receiver ->
                PlatformAbi.confinedScope().use { scope ->
                    val resultOut = PlatformAbi.allocateInt64Slot(scope)
                    target.resultSlot = resultOut
                    entries.forEachIndexed { index, (width, _) ->
                        PlatformAbi.writeInt64(resultOut, -1L)
                        target.bitsAtInvocation = null
                        val status = ComVtableInvoker.invokeArgs(
                            instance = receiver.pointer,
                            slot = IInspectableVftblSlots.FirstCustom + index,
                            arg0 = resultOut,
                        )
                        assertTrue(status < 0, "Entry $index must return a failing HRESULT")
                        val expectedBits = if (width == 8) 0L else (-1L shl (width * 8))
                        assertEquals(expectedBits, target.bitsAtInvocation, "Entry $index before managed failure")
                        assertEquals(expectedBits, PlatformAbi.readInt64(resultOut), "Entry $index")
                    }
                }
            }
        }
    }
}

private class FailingInboundTarget {
    var resultSlot: RawAddress = PlatformAbi.nullPointer
    var bitsAtInvocation: Long? = null

    fun <T> fail(): T {
        bitsAtInvocation = PlatformAbi.readInt64(resultSlot)
        error("Managed scalar getter failed")
    }
}

@WinRTProjectionInboundCallSite
private fun failedInboundBoolean(target: FailingInboundTarget): Boolean =
    target.fail<Boolean>().also { TODO("Lowered while compiling the shared inbound failure test") }

@WinRTProjectionInboundCallSite
private fun failedInboundShort(target: FailingInboundTarget): Short =
    target.fail<Short>().also { TODO("Lowered while compiling the shared inbound failure test") }

@WinRTProjectionInboundCallSite
private fun failedInboundInt(target: FailingInboundTarget): Int =
    target.fail<Int>().also { TODO("Lowered while compiling the shared inbound failure test") }

@WinRTProjectionInboundCallSite
private fun failedInboundLong(target: FailingInboundTarget): Long =
    target.fail<Long>().also { TODO("Lowered while compiling the shared inbound failure test") }

@WinRTProjectionInboundCallSite
private fun failedInboundFloat(target: FailingInboundTarget): Float =
    target.fail<Float>().also { TODO("Lowered while compiling the shared inbound failure test") }

@WinRTProjectionInboundCallSite
private fun failedInboundDouble(target: FailingInboundTarget): Double =
    target.fail<Double>().also { TODO("Lowered while compiling the shared inbound failure test") }
