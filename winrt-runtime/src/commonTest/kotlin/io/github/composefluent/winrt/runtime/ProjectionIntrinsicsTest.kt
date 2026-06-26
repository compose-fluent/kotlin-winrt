package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectionIntrinsicsTest {
    @Test
    fun set_int32_invokes_int32_vtable_shape() {
        Int32SetterComObject.create().use { host ->
            WinRTProjectionIntrinsic.setInt32(host.reference, slot = 6, value = -42)

            assertEquals(-42, host.capturedValue)
        }
    }

    private class Int32SetterComObject private constructor(
        private val scope: NativeScope,
        private val callback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        var capturedValue: Int? = null
            private set

        override fun close() {
            callback.close()
            scope.close()
        }

        companion object {
            fun create(): Int32SetterComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: Int32SetterComObject
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Int32),
                ) { args ->
                    host.capturedValue = args[1] as Int
                    KnownHResults.S_OK.value
                }
                val vtable = PlatformAbi.allocatePointerArray(scope, 7)
                PlatformAbi.writePointerAt(vtable, 6, callback.pointer)
                val objectMemory = PlatformAbi.allocatePointerSlot(scope)
                PlatformAbi.writePointer(objectMemory, vtable)
                val reference = ComObjectReference(
                    pointer = objectMemory.asRawComPtr(),
                    interfaceId = IID.IInspectable,
                    preventReleaseOnDispose = true,
                )
                host = Int32SetterComObject(scope, callback, reference)
                return host
            }
        }
    }
}
