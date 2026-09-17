package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ComVtableInvokerGeneratedShapeTest {
    @Test
    fun int32_int32_shape_used_by_generated_projection_forwards_scalars() {
        GeneratedShapeComObject.create().use { host ->
            val hr = ComVtableInvoker.invokeArgs(host.reference.pointer, slot = 6, 11, 22)

            assertEquals(KnownHResults.S_OK.value, hr)
            assertEquals(11, host.capturedFirst)
            assertEquals(22, host.capturedSecond)
        }
    }

    @Test
    fun int32_int32_pointer_pointer_shape_used_by_composable_factory_forwards_arguments() {
        GeneratedShapeComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val baseInterface = PlatformAbi.allocatePointerSlot(scope)
                val innerInterfaceOut = PlatformAbi.allocatePointerSlot(scope)

                val hr = ComVtableInvoker.invokeArgs(
                    host.reference.pointer,
                    slot = 7,
                    3,
                    4,
                    baseInterface,
                    innerInterfaceOut,
                )

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(3, host.capturedFirst)
                assertEquals(4, host.capturedSecond)
                assertEquals(PlatformAbi.pointerKey(baseInterface), host.capturedFirstPointer)
                assertEquals(PlatformAbi.pointerKey(innerInterfaceOut), host.capturedSecondPointer)
            }
        }
    }

    @Test
    fun raw_pointer_words_forward_without_materializing_pointer_wrappers() {
        GeneratedShapeComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val input = PlatformAbi.allocatePointerSlot(scope)
                val output = PlatformAbi.allocatePointerSlot(scope)

                val hr = RawCarrierCallSiteFixture.invoke(
                    host.reference.pointer,
                    slot = 8,
                    input.value,
                    output.value,
                )

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(input.value, host.capturedFirstPointer)
                assertEquals(output.value, host.capturedSecondPointer)
            }
        }
    }

    @Test
    fun raw_mixed_scalar_and_pointer_shape_preserves_carrier_order() {
        GeneratedShapeComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val output = PlatformAbi.allocatePointerSlot(scope)

                val hr = RawCarrierCallSiteFixture.invoke(
                    host.reference.pointer,
                    slot = 9,
                    37,
                    output.value,
                )

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(37, host.capturedFirst)
                assertEquals(output.value, host.capturedFirstPointer)
            }
        }
    }

    @Test
    fun raw_float_and_double_shapes_preserve_register_carriers() {
        GeneratedShapeComObject.create().use { host ->
            val floatResult = RawCarrierCallSiteFixture.invoke(host.reference.pointer, slot = 10, 1.25f)
            val doubleResult = RawCarrierCallSiteFixture.invoke(host.reference.pointer, slot = 11, 2.5)

            assertEquals(KnownHResults.S_OK.value, floatResult)
            assertEquals(KnownHResults.S_OK.value, doubleResult)
            assertEquals(1.25f, host.capturedFloat)
            assertEquals(2.5, host.capturedDouble)
        }
    }

    @Test
    fun raw_float_and_double_output_shapes_preserve_register_and_pointer_carriers() {
        GeneratedShapeComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val floatOutput = PlatformAbi.allocatePointerSlot(scope)
                val doubleOutput = PlatformAbi.allocatePointerSlot(scope)

                val floatResult = RawCarrierCallSiteFixture.invoke(
                    host.reference.pointer,
                    slot = 12,
                    1.25f,
                    floatOutput.value,
                )
                val doubleResult = RawCarrierCallSiteFixture.invoke(
                    host.reference.pointer,
                    slot = 13,
                    2.5,
                    doubleOutput.value,
                )

                assertEquals(KnownHResults.S_OK.value, floatResult)
                assertEquals(KnownHResults.S_OK.value, doubleResult)
                assertEquals(1.25f, host.capturedFloat)
                assertEquals(2.5, host.capturedDouble)
                assertEquals(floatOutput.value, host.capturedFirstPointer)
                assertEquals(doubleOutput.value, host.capturedSecondPointer)
            }
        }
    }

    @Test
    fun raw_mixed_narrow_scalars_and_float_preserve_carrier_order() {
        GeneratedShapeComObject.create().use { host ->
            val result = RawCarrierCallSiteFixture.invoke(
                host.reference.pointer,
                slot = 14,
                (-7).toByte(),
                1234.toShort(),
                (-2345).toShort(),
                1.25f,
            )

            assertEquals(KnownHResults.S_OK.value, result)
            assertEquals((-7).toByte(), host.capturedByte)
            assertEquals(1234.toShort(), host.capturedOffset)
            assertEquals((-2345).toShort(), host.capturedMarker)
            assertEquals(1.25f, host.capturedMixedFloat)
        }
    }

    @Test
    fun raw_fixed_shape_remains_stable_under_repeated_calls() {
        GeneratedShapeComObject.create().use { host ->
            repeat(10_000) { index ->
                val result = RawCarrierCallSiteFixture.invoke(
                    host.reference.pointer,
                    slot = 6,
                    index,
                    index + 1,
                )
                assertEquals(KnownHResults.S_OK.value, result)
            }

            assertEquals(9_999, host.capturedFirst)
            assertEquals(10_000, host.capturedSecond)
        }
    }

    private class GeneratedShapeComObject private constructor(
        private val scope: NativeScope,
        private val int2Callback: NativeCallbackHandle,
        private val int2Pointer2Callback: NativeCallbackHandle,
        private val rawPointerCallback: NativeCallbackHandle,
        private val rawIntPointerCallback: NativeCallbackHandle,
        private val rawFloatCallback: NativeCallbackHandle,
        private val rawDoubleCallback: NativeCallbackHandle,
        private val rawFloatPointerCallback: NativeCallbackHandle,
        private val rawDoublePointerCallback: NativeCallbackHandle,
        private val rawMixedNarrowFloatCallback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        var capturedFirst: Int? = null
            private set
        var capturedSecond: Int? = null
            private set
        var capturedFirstPointer: Long? = null
            private set
        var capturedSecondPointer: Long? = null
            private set
        var capturedFloat: Float? = null
            private set
        var capturedDouble: Double? = null
            private set
        var capturedByte: Byte? = null
            private set
        var capturedOffset: Short? = null
            private set
        var capturedMarker: Short? = null
            private set
        var capturedMixedFloat: Float? = null
            private set

        override fun close() {
            rawDoubleCallback.close()
            rawFloatCallback.close()
            rawDoublePointerCallback.close()
            rawFloatPointerCallback.close()
            rawMixedNarrowFloatCallback.close()
            rawIntPointerCallback.close()
            rawPointerCallback.close()
            int2Pointer2Callback.close()
            int2Callback.close()
            scope.close()
        }

        companion object {
            fun create(): GeneratedShapeComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: GeneratedShapeComObject
                val int2Callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Int32, ComAbiValueKind.Int32),
                ) { args ->
                    host.capturedFirst = args[1] as Int
                    host.capturedSecond = args[2] as Int
                    KnownHResults.S_OK.value
                }
                val int2Pointer2Callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedFirst = args[1] as Int
                    host.capturedSecond = args[2] as Int
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[3] as RawAddress)
                    host.capturedSecondPointer = PlatformAbi.pointerKey(args[4] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val rawPointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[1] as RawAddress)
                    host.capturedSecondPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val rawIntPointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedFirst = args[1] as Int
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val rawFloatCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Float),
                ) { args ->
                    host.capturedFloat = args[1] as Float
                    KnownHResults.S_OK.value
                }
                val rawDoubleCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Double),
                ) { args ->
                    host.capturedDouble = args[1] as Double
                    KnownHResults.S_OK.value
                }
                val rawFloatPointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Float, ComAbiValueKind.Pointer),
                ) { args ->
                    host.capturedFloat = args[1] as Float
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val rawDoublePointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Double, ComAbiValueKind.Pointer),
                ) { args ->
                    host.capturedDouble = args[1] as Double
                    host.capturedSecondPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val rawMixedNarrowFloatCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int8,
                        ComAbiValueKind.Int16,
                        ComAbiValueKind.Int16,
                        ComAbiValueKind.Float,
                    ),
                ) { args ->
                    host.capturedByte = args[1] as Byte
                    host.capturedOffset = args[2] as Short
                    host.capturedMarker = args[3] as Short
                    host.capturedMixedFloat = args[4] as Float
                    KnownHResults.S_OK.value
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 15)
                PlatformAbi.writePointerAt(vtable, 6, int2Callback.pointer)
                PlatformAbi.writePointerAt(vtable, 7, int2Pointer2Callback.pointer)
                PlatformAbi.writePointerAt(vtable, 8, rawPointerCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 9, rawIntPointerCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 10, rawFloatCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 11, rawDoubleCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 12, rawFloatPointerCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 13, rawDoublePointerCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 14, rawMixedNarrowFloatCallback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = ComObjectReference(
                    pointer = objectMemory.asRawComPtr(),
                    interfaceId = IID.IUnknown,
                    preventReleaseOnDispose = true,
                )
                host = GeneratedShapeComObject(
                    scope,
                    int2Callback,
                    int2Pointer2Callback,
                    rawPointerCallback,
                    rawIntPointerCallback,
                    rawFloatCallback,
                    rawDoubleCallback,
                    rawFloatPointerCallback,
                    rawDoublePointerCallback,
                    rawMixedNarrowFloatCallback,
                    reference,
                )
                return host
            }
        }
    }
}
