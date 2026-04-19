package io.github.kitectlab.winrt.runtime

import java.lang.foreign.MemorySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtDelegateBridgeTest {
    @Test
    fun delegate_handle_invokes_callback_with_matching_arguments() {
        var captured: List<Any?> = emptyList()
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(
                WinRtDelegateValueKind.OBJECT,
                WinRtDelegateValueKind.INT64,
                WinRtDelegateValueKind.HSTRING,
            ),
        ) { args ->
            captured = args
        }

        handle.use {
            it.invokeForTesting(listOf("sender", 42L, "payload"))
        }

        assertEquals(listOf("sender", 42L, "payload"), captured)
    }

    @Test
    fun delegate_handle_decodes_abi_arguments() {
        var captured: List<Any?> = emptyList()
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(
                WinRtDelegateValueKind.OBJECT,
                WinRtDelegateValueKind.INT64,
                WinRtDelegateValueKind.HSTRING,
            ),
        ) { args ->
            captured = args
        }

        HString.create("payload").use { hString ->
            handle.use {
                it.invokeAbiForTesting(listOf(MemorySegment.NULL, 42L, hString.handle))
            }
        }

        assertNull(captured[0])
        assertEquals(42L, captured[1])
        assertEquals("payload", captured[2])
    }

    @Test
    fun delegate_descriptor_can_render_parameterized_typed_event_signature() {
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(
                WinRtDelegateValueKind.OBJECT,
                WinRtDelegateValueKind.OBJECT,
            ),
        )

        val signature = descriptor.typedEventHandlerSignature(
            genericDelegateIid = descriptor.interfaceId,
            WinRtTypeSignature.object_(),
            WinRtTypeSignature.runtimeClass(
                "Microsoft.UI.Xaml.ResourceManagerRequestedEventArgs",
                WinRtTypeSignature.guid("c35f4cf1-fcd6-5c6b-9be2-4cfaefb68b2a"),
            ),
        )

        assertTrue(signature.render().startsWith("pinterface("))
    }

    @Test(expected = IllegalStateException::class)
    fun closed_delegate_handle_rejects_invocation() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.close()
        handle.invokeForTesting(listOf("sender"))
    }
}
