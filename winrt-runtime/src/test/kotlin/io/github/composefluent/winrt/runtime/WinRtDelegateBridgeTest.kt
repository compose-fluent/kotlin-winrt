package io.github.composefluent.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtDelegateBridgeTest {
    private data class DelegateObjectPayload(val value: String)

    private data class TestPoint(val x: Float, val y: Float)

    private object TestPointAdapter : NativeStructAdapter<TestPoint> {
        override val layout: NativeStructLayout = NativeStructLayout.sequential(
            NativeScalarFieldSpec("x", NativeStructScalarKind.FLOAT32),
            NativeScalarFieldSpec("y", NativeStructScalarKind.FLOAT32),
        )

        override fun read(source: RawAddress): TestPoint =
            TestPoint(
                x = PlatformAbi.readFloat(layout.slice(source, "x")),
                y = PlatformAbi.readFloat(layout.slice(source, "y")),
            )

        override fun write(value: TestPoint, destination: RawAddress) {
            PlatformAbi.writeFloat(layout.slice(destination, "x"), value.x)
            PlatformAbi.writeFloat(layout.slice(destination, "y"), value.y)
        }
    }

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
    fun delegate_handle_decodes_object_argument_through_object_marshaller() {
        val payload = DelegateObjectPayload("argument")
        var captured: Any? = null
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { args ->
            captured = args.single()
        }

        ComWrappersSupport.createCCWForObject(payload, IID.IInspectable).use { objectReference ->
            handle.use {
                it.createReference().use { reference ->
                    assertEquals(
                        KnownHResults.S_OK,
                        reference.invokeAbi(listOf(objectReference.pointer.asRawAddress())),
                    )
                }
            }
        }

        assertSame(payload, captured)
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
    fun delegate_reference_supports_agile_object_query_interface() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IAgileObject).getOrThrow().use { agile ->
                    assertTrue(agile.sameIdentity(reference))
                }
            }
        }
    }

    @Test
    fun delegate_reference_supports_inspectable_object_query_interface() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    assertTrue(inspectable.sameIdentity(reference))
                }
            }
        }
    }

    @Test
    fun delegate_ccw_iunknown_uses_default_delegate_interface_pointer() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IUnknown).getOrThrow().use { unknown ->
                    assertEquals(
                        PlatformAbi.pointerKey(reference.pointer.asRawAddress()),
                        PlatformAbi.pointerKey(unknown.pointer.asRawAddress()),
                    )
                }
            }
        }
    }

    @Test
    fun delegate_reference_exposes_standard_ccw_interfaces() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                listOf(IID.IWeakReferenceSource, IID.IMarshal, IID.IAgileObject, IID.IInspectable, IID.IPropertyValue).forEach { iid ->
                    reference.queryInterface(iid).getOrThrow().use { queried ->
                        assertTrue(queried.sameIdentity(reference))
                    }
                }
                reference.queryInterface(IID.IPropertyValue).getOrThrow().use { propertyValue ->
                    assertEquals(
                        PropertyType.OtherType,
                        WinRtPropertyValueReference(propertyValue.pointer.asRawAddress(), preventReleaseOnDispose = true).type(),
                    )
                }
            }
        }
    }

    @Test
    fun delegate_reference_supports_hidden_reference_tracker_target_query_interface() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IReferenceTrackerTarget).getOrThrow().use { trackerTarget ->
                    assertTrue(trackerTarget.sameIdentity(reference))
                }
            }
        }
    }

    @Test
    fun delegate_reference_supports_nullable_delegate_reference_query_interface() {
        var invocationCount = 0
        val delegateIid = Guid("b60074f3-125b-534e-8f9c-9769bd3f0f64")
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) {
            invocationCount += 1
        }

        handle.use {
            it.createReference().use { reference ->
                val nullableDelegateIid = it.descriptor.referenceInterfaceId()
                assertEquals(Guid("dea1e123-12ea-5cb3-b923-abe74e426d9e"), nullableDelegateIid)
                reference.queryInterface(nullableDelegateIid).getOrThrow().use { nullableDelegate ->
                    assertTrue(nullableDelegate.sameIdentity(reference))
                    PlatformAbi.confinedScope().use { scope ->
                        val valueOut = PlatformAbi.allocatePointerSlot(scope)
                        val hr = ComVtableInvoker.invokeArgs(
                            instance = nullableDelegate.pointer,
                            slot = IInspectableVftblSlots.FirstCustom,
                            arg0 = valueOut,
                        )
                        HResult(hr).requireSuccess()
                        val delegatePointer = PlatformAbi.readPointer(valueOut)
                        WinRtDelegateReference(delegatePointer, it.descriptor).use { value ->
                            value.invokeAbi(listOf(PlatformAbi.nullPointer)).requireSuccess()
                        }
                    }
                }
            }
        }

        assertEquals(1, invocationCount)
    }

    @Test
    fun delegate_inspectable_get_iids_returns_com_task_allocated_interfaces() {
        val delegateIid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f")
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    PlatformAbi.confinedScope().use { scope ->
                        val firstCountOut = PlatformAbi.allocateInt32Slot(scope)
                        val firstIdsOut = PlatformAbi.allocatePointerSlot(scope)
                        val firstHr = ComVtableInvoker.invokeArgs(
                            instance = inspectable.pointer,
                            slot = IInspectableVftblSlots.GetIids,
                            arg0 = firstCountOut,
                            arg1 = firstIdsOut,
                        )
                        HResult(firstHr).requireSuccess()
                        val firstIds = PlatformAbi.readPointer(firstIdsOut)
                        try {
                            val count = PlatformAbi.readInt32(firstCountOut)
                            val iids = (0 until count).map { index ->
                                PlatformAbi.readGuid(
                                    PlatformAbi.slice(
                                        firstIds,
                                        index.toLong() * Guid.BYTE_SIZE,
                                        Guid.BYTE_SIZE.toLong(),
                                    ),
                                )
                            }
                            // Mirrors .cswinrt/src/WinRT.Runtime/ComWrappersSupport.cs GetInterfaceTableEntries:
                            // delegate IID, IPropertyValue, IReference<TDelegate>, then the standard CCW suffix.
                            assertEquals(
                                listOf(
                                    delegateIid,
                                    IID.IPropertyValue,
                                    ParameterizedInterfaceId.createFromParameterizedInterface(
                                        IID.IReference,
                                        WinRtTypeSignature.delegate(delegateIid),
                                    ),
                                    IID.IStringable,
                                    IID.IWeakReferenceSource,
                                    IID.IMarshal,
                                    IID.IAgileObject,
                                    IID.IInspectable,
                                    IID.IUnknown,
                                ),
                                iids,
                            )
                        } finally {
                            WinRtPlatformApi.coTaskMemFreeRaw(firstIds)
                        }

                        val secondCountOut = PlatformAbi.allocateInt32Slot(scope)
                        val secondIdsOut = PlatformAbi.allocatePointerSlot(scope)
                        val secondHr = ComVtableInvoker.invokeArgs(
                            instance = inspectable.pointer,
                            slot = IInspectableVftblSlots.GetIids,
                            arg0 = secondCountOut,
                            arg1 = secondIdsOut,
                        )
                        HResult(secondHr).requireSuccess()
                        val secondIds = PlatformAbi.readPointer(secondIdsOut)
                        try {
                            assertNotEquals(0L, PlatformAbi.pointerKey(secondIds))
                        } finally {
                            WinRtPlatformApi.coTaskMemFreeRaw(secondIds)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun delegate_inspectable_query_returns_explicit_inspectable_entry() {
        val delegateIid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53e")
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = delegateIid,
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IInspectable).getOrThrow().use { inspectable ->
                    reference.queryInterface(IID.IStringable).getOrThrow().use { stringable ->
                        assertNotEquals(
                            PlatformAbi.pointerKey(stringable.pointer),
                            PlatformAbi.pointerKey(inspectable.pointer),
                        )
                    }
                }
            }
        }
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
    fun delegate_reference_tracker_target_tracks_reference_tracker_refs() {
        val handle = WinRtDelegateBridge.createUnitDelegate(
            iid = Guid("9de1c534-6ae1-11e0-84e1-18a905bcc53f"),
            parameterKinds = listOf(WinRtDelegateValueKind.OBJECT),
        ) { }

        handle.use {
            it.createReference().use { reference ->
                reference.queryInterface(IID.IReferenceTrackerTarget).getOrThrow().use { trackerTarget ->
                    assertEquals(
                        1,
                        ComVtableInvoker.invoke(
                            trackerTarget.pointer,
                            ReferenceTrackerTargetVftblSlots.AddRefFromReferenceTracker,
                        ),
                    )
                    assertEquals(
                        2,
                        ComVtableInvoker.invoke(
                            trackerTarget.pointer,
                            ReferenceTrackerTargetVftblSlots.AddRefFromReferenceTracker,
                        ),
                    )
                    assertEquals(
                        1,
                        ComVtableInvoker.invoke(
                            trackerTarget.pointer,
                            ReferenceTrackerTargetVftblSlots.ReleaseFromReferenceTracker,
                        ),
                    )
                    assertEquals(
                        0,
                        ComVtableInvoker.invoke(
                            trackerTarget.pointer,
                            ReferenceTrackerTargetVftblSlots.ReleaseFromReferenceTracker,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun delegate_reference_decodes_object_return_value_through_object_marshaller() {
        val payload = DelegateObjectPayload("return")
        val handle = WinRtDelegateBridge.createDelegate(
            iid = Guid("abababab-abab-abab-abab-abababababab"),
            parameterKinds = emptyList(),
            returnKind = WinRtDelegateValueKind.OBJECT,
        ) {
            payload
        }

        handle.use {
            it.createReference().use { reference ->
                assertSame(payload, reference.invoke(emptyList()))
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

    @Test
    fun delegate_descriptor_supports_guid_and_struct_abi_shapes() {
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = Guid("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            parameterKinds = listOf(WinRtDelegateValueKind.GUID, WinRtDelegateValueKind.STRUCT),
            returnKind = WinRtDelegateValueKind.STRUCT,
            parameterStructAdapters = listOf(null, TestPointAdapter),
            returnStructAdapter = TestPointAdapter,
        )

        val signature = WinRtDelegateAbiMarshaller.functionSignature(descriptor)
        assertEquals(
            listOf(
                ComAbiValueKind.Struct(NativeAbiLayout.GUID),
                ComAbiValueKind.Struct(TestPointAdapter.layout.abiLayout),
                ComAbiValueKind.Pointer,
            ),
            signature.explicitParameterKinds,
        )

        val id = Guid("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val point = TestPoint(1.5f, 2.5f)
        PlatformAbi.confinedScope().use { scope ->
            val idAbi = PlatformAbi.allocateBytes(scope, Guid.BYTE_SIZE.toLong())
            PlatformAbi.writeGuid(idAbi, id)
            val pointAbi = PlatformAbi.allocateBytes(scope, TestPointAdapter.layout.sizeBytes)
            TestPointAdapter.write(point, pointAbi)

            assertEquals(listOf(id, point), WinRtDelegateAbiMarshaller.decodeArguments(descriptor, listOf(idAbi, pointAbi)))

            val resultOut = WinRtDelegateAbiMarshaller.allocateReturnOut(descriptor, scope)
            WinRtDelegateAbiMarshaller.writeReturnValue(descriptor, TestPoint(3.5f, 4.5f), resultOut)

            assertEquals(TestPoint(3.5f, 4.5f), WinRtDelegateAbiMarshaller.decodeReturnValue(descriptor, resultOut))
        }
    }

    @Test
    fun delegate_reference_invokes_struct_signature_with_natural_ffm_alignment() {
        val handle = WinRtDelegateBridge.createDelegate(
            iid = Guid("f0f0f0f0-f0f0-f0f0-f0f0-f0f0f0f0f0f0"),
            parameterKinds = listOf(WinRtDelegateValueKind.STRUCT),
            returnKind = WinRtDelegateValueKind.STRUCT,
            parameterStructAdapters = listOf(TestPointAdapter),
            returnStructAdapter = TestPointAdapter,
        ) { args ->
            val point = args.single() as TestPoint
            TestPoint(point.x + 1.0f, point.y + 1.0f)
        }

        handle.use {
            it.createReference().use { reference ->
                assertEquals(TestPoint(2.5f, 3.5f), reference.invoke(listOf(TestPoint(1.5f, 2.5f))))
            }
        }
    }

    @Test
    fun delegate_descriptor_expands_uint8_array_parameters() {
        val descriptor = WinRtDelegateDescriptor(
            interfaceId = Guid("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
            parameterKinds = listOf(WinRtDelegateValueKind.UINT8_ARRAY),
            returnKind = WinRtDelegateValueKind.UNIT,
        )

        assertEquals(
            listOf(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
            WinRtDelegateAbiMarshaller.functionSignature(descriptor).explicitParameterKinds,
        )

        val values = arrayOf(1.toUByte(), 2.toUByte(), 255.toUByte())
        WinRtDelegateAbiMarshaller.encodeArgumentsLease(descriptor, listOf(values)).use { encoded ->
            assertEquals(2, encoded.values.size)
            assertEquals(3, encoded.values[0])
            assertEquals(values.toList(), (WinRtDelegateAbiMarshaller.decodeArguments(descriptor, encoded.values).single() as Array<*>).toList())
        }
    }
}
