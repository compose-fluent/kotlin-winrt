package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class ComVtableInvokerCallbackBridgeJvmTest {
    @Test
    fun closed_raw_callback_bridge_returns_e_pointer() {
        val handle = ComAbiInteropBridge.createRawInt32Callback(
            parameterKinds = listOf(ComAbiValueKind.Pointer),
        ) {
            KnownHResults.S_OK.value
        }
        val callbackPointer = handle.pointer

        handle.close()

        assertEquals(
            KnownHResults.E_POINTER.value,
            invokeInt32PointerCallbackForTest(callbackPointer, PlatformAbi.nullPointer),
        )
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

        assertEquals(
            KnownHResults.E_POINTER.value,
            invokeInt32PointerCallbackForTest(callbackPointer, PlatformAbi.nullPointer),
        )
    }
}
