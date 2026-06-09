package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ComVtableInvokerGenericDescriptorTest {
    @Test
    fun generic_int32_pointer_pointer_pointer_descriptor_forwards_generated_array_shape() {
        GenericDescriptorComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val arrayData = PlatformAbi.allocatePointerSlot(scope)
                val countOut = PlatformAbi.allocateInt32Slot(scope)
                val dataOut = PlatformAbi.allocatePointerSlot(scope)
                val hr = ComVtableInvoker.invokeGeneric(
                    host.reference.pointer,
                    slot = 6,
                    signature = ComMethodSignature.of(
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                    args = longArrayOf(2, arrayData.value, countOut.value, dataOut.value),
                )

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(2, host.capturedFirstInt)
                assertEquals(PlatformAbi.pointerKey(arrayData), host.capturedFirstPointer)
                assertEquals(PlatformAbi.pointerKey(countOut), host.capturedSecondPointer)
                assertEquals(PlatformAbi.pointerKey(dataOut), host.capturedThirdPointer)
            }
        }
    }

    @Test
    fun generic_int32_pointer_int32_pointer_pointer_descriptor_forwards_generated_array_shape() {
        GenericDescriptorComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val arrayData = PlatformAbi.allocatePointerSlot(scope)
                val countOut = PlatformAbi.allocateInt32Slot(scope)
                val dataOut = PlatformAbi.allocatePointerSlot(scope)
                val hr = ComVtableInvoker.invokeGeneric(
                    host.reference.pointer,
                    slot = 7,
                    signature = ComMethodSignature.of(
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                    args = longArrayOf(3, arrayData.value, 4, countOut.value, dataOut.value),
                )

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(3, host.capturedFirstInt)
                assertEquals(4, host.capturedSecondInt)
                assertEquals(PlatformAbi.pointerKey(arrayData), host.capturedFirstPointer)
                assertEquals(PlatformAbi.pointerKey(countOut), host.capturedSecondPointer)
                assertEquals(PlatformAbi.pointerKey(dataOut), host.capturedThirdPointer)
            }
        }
    }

    private class GenericDescriptorComObject private constructor(
        private val scope: NativeScope,
        private val intPointer3Callback: NativeCallbackHandle,
        private val intPointerIntPointer2Callback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        var capturedFirstInt: Int? = null
            private set
        var capturedSecondInt: Int? = null
            private set
        var capturedFirstPointer: Long? = null
            private set
        var capturedSecondPointer: Long? = null
            private set
        var capturedThirdPointer: Long? = null
            private set

        override fun close() {
            intPointerIntPointer2Callback.close()
            intPointer3Callback.close()
            scope.close()
        }

        companion object {
            fun create(): GenericDescriptorComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: GenericDescriptorComObject
                val intPointer3Callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedFirstInt = args[1] as Int
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    host.capturedSecondPointer = PlatformAbi.pointerKey(args[3] as RawAddress)
                    host.capturedThirdPointer = PlatformAbi.pointerKey(args[4] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val intPointerIntPointer2Callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedFirstInt = args[1] as Int
                    host.capturedFirstPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    host.capturedSecondInt = args[3] as Int
                    host.capturedSecondPointer = PlatformAbi.pointerKey(args[4] as RawAddress)
                    host.capturedThirdPointer = PlatformAbi.pointerKey(args[5] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 8)
                PlatformAbi.writePointerAt(vtable, 6, intPointer3Callback.pointer)
                PlatformAbi.writePointerAt(vtable, 7, intPointerIntPointer2Callback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = ComObjectReference(
                    pointer = objectMemory.asRawComPtr(),
                    interfaceId = IID.IUnknown,
                    preventReleaseOnDispose = true,
                )
                host = GenericDescriptorComObject(scope, intPointer3Callback, intPointerIntPointer2Callback, reference)
                return host
            }
        }
    }
}
