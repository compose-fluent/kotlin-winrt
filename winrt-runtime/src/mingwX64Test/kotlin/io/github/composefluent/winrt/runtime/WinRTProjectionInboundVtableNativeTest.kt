@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaque
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.LongVar
import kotlinx.cinterop.ShortVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WinRTProjectionInboundVtableNativeTest {
    @Test
    fun managed_binding_probe_checks_the_iunknown_vtable_before_reading_private_slots() {
        PlatformAbi.confinedScope().use { scope ->
            val externalVtable = PlatformAbi.allocatePointerArray(scope, 3)
            val externalPointer = PlatformAbi.allocatePointerSlot(scope)
            PlatformAbi.writePointer(externalPointer, externalVtable)

            assertNull(platformTryWinRTProjectionInboundBinding(externalPointer.value))
        }

        val managedValue = Any()
        WinRTInspectableComObject.inspectableBox(managedValue).use { host ->
            host.createPrimaryReference().use { reference ->
                val binding = platformTryWinRTProjectionInboundBinding(reference.pointer.value)

                assertSame(managedValue, binding?.get())
            }
        }
    }

    @Test
    fun fixed_release_stub_does_not_consume_the_borrowed_baseline_reference() {
        var cleanupCount = 0
        val managedValue = Any()
        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = IID.IInspectable,
                    methods = emptyList(),
                ),
            ),
            defaultInterfaceId = IID.IInspectable,
            managedValue = managedValue,
            weakManagedValue = true,
            cleanupAction = { cleanupCount += 1 },
        )
        val lease = checkNotNull(host.tryAcquireStaticCallLease(IID.IInspectable, managedValue))

        try {
            assertEquals(
                0,
                ComVtableInvoker.invoke(
                    instance = PlatformAbi.toRawComPtr(lease.abi),
                    slot = IUnknownVftblSlots.Release,
                ),
            )
        } finally {
            host.endStaticCallLease(managedValue)
            host.close()
        }

        assertEquals(1, cleanupCount)
    }

    @Test
    fun generated_scalar_getters_publish_results_through_the_vtable() {
        val interfaceId = Guid("cc7d9011-96a4-42fc-8fd0-b45c1c39b08f")
        val managedValue = InboundScalarValues(
            booleanValue = true,
            int32Value = 0x1234_5678,
            int64Value = 0x1234_5678_7654_3210L,
            float32Value = 123.25f,
            float64Value = 456.5,
        )
        WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = interfaceId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = winRTProjectionInboundEntryPoint(::readInboundBoolean),
                        ),
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = winRTProjectionInboundEntryPoint(::readInboundInt32),
                        ),
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = winRTProjectionInboundEntryPoint(::readInboundInt64),
                        ),
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = winRTProjectionInboundEntryPoint(::readInboundFloat32),
                        ),
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            abiEntryPoint = winRTProjectionInboundEntryPoint(::readInboundFloat64),
                        ),
                    ),
                ),
            ),
            defaultInterfaceId = interfaceId,
            managedValue = managedValue,
            weakManagedValue = true,
        ).use { host ->
            host.createPrimaryReference().use { reference ->
                assertEquals(true, WinRTProjectionIntrinsic.getBoolean(reference, slot = 6))
                assertEquals(0x1234_5678, WinRTProjectionIntrinsic.getInt32(reference, slot = 7))
                assertEquals(0x1234_5678_7654_3210L, WinRTProjectionIntrinsic.getInt64(reference, slot = 8))
                assertEquals(123.25f, WinRTProjectionIntrinsic.getFloat(reference, slot = 9))
                assertEquals(456.5, WinRTProjectionIntrinsic.getDouble(reference, slot = 10))
            }
        }
    }

    @Test
    fun primitive_result_writers_preserve_width_and_bits() = memScoped {
        val address = alloc<LongVar>()
        val int8 = alloc<ByteVar>()
        val int16 = alloc<ShortVar>()
        val int32 = alloc<IntVar>()
        val int64 = alloc<LongVar>()
        val float32 = alloc<FloatVar>()
        val float64 = alloc<DoubleVar>()

        winRTProjectionInboundWriteAddress(address.ptr.reinterpret<COpaque>(), RawAddress(0x1234_5678_7654_3210L))
        winRTProjectionInboundWriteInt8(int8.ptr.reinterpret<COpaque>(), (-0x12).toByte())
        winRTProjectionInboundWriteInt16(int16.ptr.reinterpret<COpaque>(), (-0x1234).toShort())
        winRTProjectionInboundWriteInt32(int32.ptr.reinterpret<COpaque>(), -0x1234_567)
        winRTProjectionInboundWriteInt64(int64.ptr.reinterpret<COpaque>(), -0x1234_5678_7654_321L)
        winRTProjectionInboundWriteFloat32(float32.ptr.reinterpret<COpaque>(), -123.25f)
        winRTProjectionInboundWriteFloat64(float64.ptr.reinterpret<COpaque>(), -456.5)

        assertEquals(0x1234_5678_7654_3210L, address.value)
        assertEquals((-0x12).toByte(), int8.value)
        assertEquals((-0x1234).toShort(), int16.value)
        assertEquals(-0x1234_567, int32.value)
        assertEquals(-0x1234_5678_7654_321L, int64.value)
        assertEquals(-123.25f, float32.value)
        assertEquals(-456.5, float64.value)
    }
}

private class InboundScalarValues(
    val booleanValue: Boolean,
    val int32Value: Int,
    val int64Value: Long,
    val float32Value: Float,
    val float64Value: Double,
)

@WinRTProjectionInboundCallSite
private fun readInboundBoolean(value: InboundScalarValues): Boolean =
    value.booleanValue.also {
        TODO("Lowered while compiling the Native inbound CallSite test")
    }

@WinRTProjectionInboundCallSite
private fun readInboundInt32(value: InboundScalarValues): Int =
    value.int32Value.also {
        TODO("Lowered while compiling the Native inbound CallSite test")
    }

@WinRTProjectionInboundCallSite
private fun readInboundInt64(value: InboundScalarValues): Long =
    value.int64Value.also {
        TODO("Lowered while compiling the Native inbound CallSite test")
    }

@WinRTProjectionInboundCallSite
private fun readInboundFloat32(value: InboundScalarValues): Float =
    value.float32Value.also {
        TODO("Lowered while compiling the Native inbound CallSite test")
    }

@WinRTProjectionInboundCallSite
private fun readInboundFloat64(value: InboundScalarValues): Double =
    value.float64Value.also {
        TODO("Lowered while compiling the Native inbound CallSite test")
    }
