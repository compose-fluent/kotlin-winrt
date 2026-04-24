package io.github.kitectlab.winrt.runtime

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
                it.invokeAbiForTesting(listOf(PlatformAbi.nullPointer, 42L, hString.handle))
            }
        }

        assertNull(captured[0])
        assertEquals(42L, captured[1])
        assertEquals("payload", captured[2])
    }

    @Test
    fun delegate_reference_invokes_callback_through_vtable_invoke_slot() {
        var invocationCount = 0
        var lastPayload: String? = null
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.HSTRING),
        ) { args ->
            invocationCount += 1
            lastPayload = args.single() as String?
        }

        HString.create("message").use { payload ->
            handle.use {
                it.createReference().use { reference ->
                    assertEquals(KnownHResults.S_OK, reference.invokeAbi(listOf(payload.handle)))
                }
            }
        }

        assertEquals(1, invocationCount)
        assertEquals("message", lastPayload)
    }

    @Test
    fun delegate_reference_outlives_handle_while_addrefed_reference_exists() {
        var invocationCount = 0
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) {
            invocationCount += 1
        }

        val reference = handle.createReference()
        handle.close()

        reference.use {
            assertEquals(KnownHResults.S_OK, it.invokeAbi(listOf(PlatformAbi.nullPointer)))
            assertTrue(it.queryInterface(IID.IUnknown).getOrThrow().sameIdentity(it))
        }

        assertEquals(1, invocationCount)
    }

    @Test
    fun delegate_invoke_returns_callback_hresult_instead_of_throwing_across_abi() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) {
            throw WinRtAccessDeniedException("denied", KnownHResults.E_ACCESSDENIED)
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals(KnownHResults.E_ACCESSDENIED, reference.invokeAbi(listOf(PlatformAbi.nullPointer)))
            }
        }
    }

    @Test
    fun delegate_invoke_maps_unexpected_callback_failure_to_e_fail() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) {
            error("boom")
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals(KnownHResults.E_FAIL, reference.invokeAbi(listOf(PlatformAbi.nullPointer)))
            }
        }
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

    @Test
    fun delegate_reference_decodes_string_return_value_from_native_delegate() {
        val handle = WinRtDelegateBridge.createDelegate(
            iid = Guid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            parameterKinds = listOf(WinRtDelegateValueKind.INT32),
            returnKind = WinRtDelegateValueKind.HSTRING,
        ) { args ->
            "value-${args.single() as Int}"
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals("value-42", reference.invoke(listOf(42)))
            }
        }
    }

    @Test
    fun delegate_reference_decodes_boolean_and_uint32_return_values_from_native_delegate() {
        val boolHandle = WinRtDelegateBridge.createDelegate(
            iid = Guid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            parameterKinds = listOf(WinRtDelegateValueKind.INT32),
            returnKind = WinRtDelegateValueKind.BOOLEAN,
        ) { args ->
            (args.single() as Int) > 0
        }
        val uintHandle = WinRtDelegateBridge.createDelegate(
            iid = Guid("cccccccc-cccc-cccc-cccc-cccccccccccc"),
            parameterKinds = listOf(WinRtDelegateValueKind.INT32),
            returnKind = WinRtDelegateValueKind.UINT32,
        ) { args ->
            ((args.single() as Int) + 10).toUInt()
        }

        boolHandle.use {
            it.createReference().use { reference ->
                assertEquals(true, reference.invoke(listOf(1)))
            }
        }
        uintHandle.use {
            it.createReference().use { reference ->
                assertEquals(15u, reference.invoke(listOf(5)))
            }
        }
    }
}
