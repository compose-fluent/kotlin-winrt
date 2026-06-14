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

    private class GeneratedShapeComObject private constructor(
        private val scope: NativeScope,
        private val int2Callback: NativeCallbackHandle,
        private val int2Pointer2Callback: NativeCallbackHandle,
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

        override fun close() {
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
                val vtable = PlatformAbi.allocatePointerArray(scope, 8)
                PlatformAbi.writePointerAt(vtable, 6, int2Callback.pointer)
                PlatformAbi.writePointerAt(vtable, 7, int2Pointer2Callback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = ComObjectReference(
                    pointer = objectMemory.asRawComPtr(),
                    interfaceId = IID.IUnknown,
                    preventReleaseOnDispose = true,
                )
                host = GeneratedShapeComObject(scope, int2Callback, int2Pointer2Callback, reference)
                return host
            }
        }
    }
}
