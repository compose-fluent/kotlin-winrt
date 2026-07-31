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
    fun runtime_owned_scalar_getters_preserve_abi_values() {
        assertScalarGetter(true, { result -> PlatformAbi.writeInt8(result, 1) }) { reference ->
            WinRTProjectionIntrinsic.getBoolean(reference, slot = 6)
        }
        assertScalarGetter(UInt.MAX_VALUE, { result -> PlatformAbi.writeInt32(result, -1) }) { reference ->
            WinRTProjectionIntrinsic.getUInt32(reference, slot = 6)
        }
        assertScalarGetter(Long.MIN_VALUE, { result -> PlatformAbi.writeInt64(result, Long.MIN_VALUE) }) { reference ->
            WinRTProjectionIntrinsic.getInt64(reference, slot = 6)
        }
        assertScalarGetter(ULong.MAX_VALUE, { result -> PlatformAbi.writeInt64(result, -1L) }) { reference ->
            WinRTProjectionIntrinsic.getUInt64(reference, slot = 6)
        }
        assertScalarGetter(1.25f, { result -> PlatformAbi.writeFloat(result, 1.25f) }) { reference ->
            WinRTProjectionIntrinsic.getFloat(reference, slot = 6)
        }
        assertScalarGetter(-2.5, { result -> PlatformAbi.writeDouble(result, -2.5) }) { reference ->
            WinRTProjectionIntrinsic.getDouble(reference, slot = 6)
        }
    }

    @Test
    fun checked_runtime_owned_getter_propagates_hresult() {
        ScalarGetterComObject.create(hResult = KnownHResults.E_INVALIDARG.value).use { host ->
            assertFailsWith<WinRTIllegalArgumentException> {
                WinRTProjectionIntrinsic.getInt32(host.reference, slot = 6)
            }
        }
    }

    @Test
    fun no_exception_boolean_clears_reused_result_after_failure() {
        assertScalarGetter(true, { result -> PlatformAbi.writeInt8(result, 1) }) { reference ->
            WinRTProjectionIntrinsic.getNoExceptionBoolean(reference, slot = 6)
        }

        ScalarGetterComObject.create(hResult = KnownHResults.E_FAIL.value).use { host ->
            assertEquals(false, WinRTProjectionIntrinsic.getNoExceptionBoolean(host.reference, slot = 6))
        }
    }

    @Test
    fun nested_runtime_owned_getters_use_distinct_result_frames() {
        ScalarGetterComObject.create { result -> PlatformAbi.writeInt32(result, 41) }.use { inner ->
            ScalarGetterComObject.create { result ->
                val nested = WinRTProjectionIntrinsic.getInt32(inner.reference, slot = 6)
                PlatformAbi.writeInt32(result, nested + 1)
            }.use { outer ->
                assertEquals(42, WinRTProjectionIntrinsic.getInt32(outer.reference, slot = 6))
            }
        }
    }

    @Test
    fun runtime_owned_setters_preserve_string_and_integer_carriers() {
        assertScalarSetter(
            expected = "runtime-owned",
            parameterKind = ComAbiValueKind.Pointer,
            capture = { value -> HString.fromHandle(value as RawAddress, owner = false).toKString() },
        ) { reference ->
            WinRTProjectionIntrinsic.setString(reference, slot = 6, value = "runtime-owned")
        }
        assertScalarSetter(-1, ComAbiValueKind.Int32) { reference ->
            WinRTProjectionIntrinsic.setUInt32(reference, slot = 6, value = UInt.MAX_VALUE)
        }
        assertScalarSetter(Long.MIN_VALUE, ComAbiValueKind.Int64) { reference ->
            WinRTProjectionIntrinsic.setInt64(reference, slot = 6, value = Long.MIN_VALUE)
        }
        assertScalarSetter(-1L, ComAbiValueKind.Int64) { reference ->
            WinRTProjectionIntrinsic.setUInt64(reference, slot = 6, value = ULong.MAX_VALUE)
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

    private fun <T> assertScalarGetter(
        expected: T,
        write: (RawAddress) -> Unit,
        read: (ComObjectReference) -> T,
    ) {
        ScalarGetterComObject.create(write = write).use { host ->
            assertEquals(expected, read(host.reference))
        }
    }

    private fun assertScalarSetter(
        expected: Any?,
        parameterKind: ComAbiValueKind,
        capture: (Any?) -> Any? = { it },
        invoke: (ComObjectReference) -> Unit,
    ) {
        ScalarSetterComObject.create(parameterKind, capture).use { host ->
            invoke(host.reference)
            assertEquals(expected, host.capturedValue)
        }
    }

    private class ScalarGetterComObject private constructor(
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
            fun create(
                hResult: Int = KnownHResults.S_OK.value,
                write: (RawAddress) -> Unit = {},
            ): ScalarGetterComObject {
                val scope = PlatformAbi.confinedScope()
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                ) { args ->
                    write(args[1] as RawAddress)
                    hResult
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
                return ScalarGetterComObject(scope, callback, reference)
            }
        }
    }

    private class ScalarSetterComObject private constructor(
        private val scope: NativeScope,
        private val callback: NativeCallbackHandle,
        val reference: ComObjectReference,
    ) : AutoCloseable {
        var capturedValue: Any? = null
            private set

        override fun close() {
            reference.close()
            callback.close()
            scope.close()
        }

        companion object {
            fun create(
                parameterKind: ComAbiValueKind,
                capture: (Any?) -> Any?,
            ): ScalarSetterComObject {
                val scope = PlatformAbi.confinedScope()
                lateinit var host: ScalarSetterComObject
                val callback = ComAbiInteropBridge.createRawInt32Callback(
                    listOf(ComAbiValueKind.Pointer, parameterKind),
                ) { args ->
                    host.capturedValue = capture(args[1])
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
                host = ScalarSetterComObject(scope, callback, reference)
                return host
            }
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
