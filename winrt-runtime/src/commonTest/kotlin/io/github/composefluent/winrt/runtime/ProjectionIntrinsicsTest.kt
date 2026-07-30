package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProjectionIntrinsicsTest {
    @Test
    fun get_int32_invokes_pointer_result_vtable_shape() {
        Int32GetterComObject.create().use { host ->
            assertEquals(-42, WinRTProjectionIntrinsic.getInt32(host.reference, slot = 6))
        }
    }

    @Test
    fun get_string_invokes_pointer_result_vtable_shape() {
        StringGetterComObject.create().use { host ->
            assertEquals("projection-runtime", WinRTProjectionIntrinsic.getString(host.reference, slot = 6))
        }
    }

    @Test
    fun set_int32_invokes_int32_vtable_shape() {
        Int32SetterComObject.create().use { host ->
            WinRTProjectionIntrinsic.setInt32(host.reference, slot = 6, value = -42)

            assertEquals(-42, host.capturedValue)
        }
    }

    @Test
    fun projection_intrinsic_rejects_disposed_reference_before_vtable_call() {
        Int32SetterComObject.create().use { host ->
            host.reference.close()

            assertFailsWith<WinRTObjectDisposedException> {
                WinRTProjectionIntrinsic.setInt32(host.reference, slot = 6, value = -42)
            }
            assertEquals(null, host.capturedValue)
        }
    }

    private class Int32GetterComObject private constructor(
        private val scope: NativeScope,
        private val callback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        override fun close() {
            reference.close()
            callback.close()
            scope.close()
        }

        companion object {
            fun create(): Int32GetterComObject {
                val scope = PlatformAbi.confinedScope()
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args ->
                    PlatformAbi.writeInt32(args[1] as RawAddress, -42)
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
                return Int32GetterComObject(scope, callback, reference)
            }
        }
    }

    private class StringGetterComObject private constructor(
        private val scope: NativeScope,
        private val callback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        override fun close() {
            reference.close()
            callback.close()
            scope.close()
        }

        companion object {
            fun create(): StringGetterComObject {
                val scope = PlatformAbi.confinedScope()
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args ->
                    val result = HString.create("projection-runtime")
                    PlatformAbi.writePointer(args[1] as RawAddress, result.handle)
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
                return StringGetterComObject(scope, callback, reference)
            }
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
            reference.close()
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
