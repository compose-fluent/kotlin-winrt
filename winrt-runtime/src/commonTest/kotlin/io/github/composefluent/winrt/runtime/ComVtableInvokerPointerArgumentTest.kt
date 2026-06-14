package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ComVtableInvokerPointerArgumentTest {
    @Test
    fun raw_comptr_pointer_out_shape_forwards_as_pointer_arguments() {
        PointerArgumentComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val objectArg = PlatformAbi.allocatePointerSlot(scope).asRawComPtr()
                val resultOut = PlatformAbi.allocatePointerSlot(scope)

                val hr = ComVtableInvoker.invokeArgs(host.reference.pointer, slot = 6, objectArg, resultOut)

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(PlatformAbi.pointerKey(objectArg), host.capturedObjectPointer)
                assertEquals(PlatformAbi.pointerKey(resultOut), host.capturedResultOut)
            }
        }
    }

    @Test
    fun int_raw_comptr_pointer_out_shape_forwards_as_existing_pointer_shape() {
        PointerArgumentComObject.create().use { host ->
            PlatformAbi.confinedScope().use { scope ->
                val objectArg = PlatformAbi.allocatePointerSlot(scope).asRawComPtr()
                val resultOut = PlatformAbi.allocatePointerSlot(scope)

                val hr = ComVtableInvoker.invokeArgs(host.reference.pointer, slot = 7, 42, objectArg, resultOut)

                assertEquals(KnownHResults.S_OK.value, hr)
                assertEquals(42, host.capturedIndex)
                assertEquals(PlatformAbi.pointerKey(objectArg), host.capturedObjectPointer)
                assertEquals(PlatformAbi.pointerKey(resultOut), host.capturedResultOut)
            }
        }
    }

    private class PointerArgumentComObject private constructor(
        private val scope: NativeScope,
        private val pointerCallback: NativeCallbackHandle,
        private val intPointerCallback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        var capturedIndex: Int? = null
            private set
        var capturedObjectPointer: Long? = null
            private set
        var capturedResultOut: Long? = null
            private set

        override fun close() {
            intPointerCallback.close()
            pointerCallback.close()
            scope.close()
        }

        companion object {
            fun create(): PointerArgumentComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: PointerArgumentComObject
                val pointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args ->
                    host.capturedObjectPointer = PlatformAbi.pointerKey(args[1] as RawAddress)
                    host.capturedResultOut = PlatformAbi.pointerKey(args[2] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val intPointerCallback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Int32,
                        ComAbiValueKind.Pointer,
                        ComAbiValueKind.Pointer,
                    ),
                ) { args ->
                    host.capturedIndex = args[1] as Int
                    host.capturedObjectPointer = PlatformAbi.pointerKey(args[2] as RawAddress)
                    host.capturedResultOut = PlatformAbi.pointerKey(args[3] as RawAddress)
                    KnownHResults.S_OK.value
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 8)
                PlatformAbi.writePointerAt(vtable, 6, pointerCallback.pointer)
                PlatformAbi.writePointerAt(vtable, 7, intPointerCallback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = ComObjectReference(
                    pointer = objectMemory.asRawComPtr(),
                    interfaceId = IID.IUnknown,
                    preventReleaseOnDispose = true,
                )
                host = PointerArgumentComObject(scope, pointerCallback, intPointerCallback, reference)
                return host
            }
        }
    }
}
