package io.github.composefluent.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.ValueLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class ComVtableInvokerCallbackBridgeTest {
    @Test
    fun raw_callback_bridge_returns_callback_hresult_for_winrt_exception() {
        ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) {
            throw WinRtAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)
        }.use { handle ->
            assertEquals(KnownHResults.E_ACCESSDENIED.value, invokeInt32PointerCallback(handle))
        }
    }

    @Test
    fun raw_callback_bridge_maps_unexpected_callback_failure_to_e_fail() {
        ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) {
            error("boom")
        }.use { handle ->
            assertEquals(KnownHResults.E_FAIL.value, invokeInt32PointerCallback(handle))
        }
    }

    @Test
    fun raw_callback_bridge_converts_pointer_argument() {
        PlatformAbi.confinedScope().use { scope ->
            val argument = PlatformAbi.allocateInt32Slot(scope)
            var captured = PlatformAbi.nullPointer
            ComAbiInteropBridge.createRawInt32Callback(
                parameterKinds = listOf(ComAbiValueKind.Pointer),
            ) { arguments ->
                captured = arguments.single() as RawAddress
                KnownHResults.S_FALSE.value
            }.use { handle ->
                assertEquals(KnownHResults.S_FALSE.value, invokeInt32PointerCallback(handle, argument))
            }
            assertEquals(PlatformAbi.pointerKey(argument), PlatformAbi.pointerKey(captured))
        }
    }

    @Test
    fun closed_raw_callback_bridge_returns_e_pointer() {
        val handle = ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) {
            KnownHResults.S_OK.value
        }
        val callbackPointer = handle.pointer

        handle.close()

        assertEquals(KnownHResults.E_POINTER.value, invokeInt32PointerCallback(callbackPointer))
    }

    @Test
    fun raw_callback_handle_close_is_idempotent() {
        val handle = ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) {
            KnownHResults.S_OK.value
        }
        val callbackPointer = handle.pointer

        handle.close()
        handle.close()

        assertEquals(KnownHResults.E_POINTER.value, invokeInt32PointerCallback(callbackPointer))
    }

    private fun invokeInt32PointerCallback(
        handle: NativeCallbackHandle,
        argument: RawAddress = PlatformAbi.nullPointer,
    ): Int = invokeInt32PointerCallback(handle.pointer, argument)

    private fun invokeInt32PointerCallback(
        callbackPointer: RawAddress,
        argument: RawAddress = PlatformAbi.nullPointer,
    ): Int {
        val downcall = linker.downcallHandle(
            callbackPointer.asMemorySegment(),
            int32PointerCallbackDescriptor,
        )
        return downcall.invokeWithArguments(argument.asMemorySegment()) as Int
    }

    private companion object {
        val linker: Linker = Linker.nativeLinker()
        val int32PointerCallbackDescriptor: FunctionDescriptor =
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    }
}
