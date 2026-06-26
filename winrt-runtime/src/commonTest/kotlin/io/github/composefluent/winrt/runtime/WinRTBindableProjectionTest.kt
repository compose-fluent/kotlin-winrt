package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinRTBindableProjectionTest {
    @Test
    fun bindable_iterable_helpers_round_trip_non_generic_values() {
        val values = listOf("one", 2, null)
        val marshaler = WinRTBindableIterableProjection.createMarshaler(values)
            ?: error("IBindableIterable marshaler should not be null.")

        try {
            ComObjectReference(
                marshaler.abi.asRawComPtr(),
                WinRTBindableInterfaceIds.IBindableIterable,
                preventReleaseOnDispose = true,
            ).use { borrowed ->
                WinRTBindableIterableProjection.fromAbi(borrowed.getRefPointer().asRawAddress())!!.use { projected ->
                    assertEquals(values, projected.toList())
                    assertEquals(
                        WinRTTypeHandle("kotlin.collections.Iterable<kotlin.Any?>", WinRTBindableInterfaceIds.IBindableIterable),
                        projected.primaryTypeHandle,
                    )
                }
            }
        } finally {
            marshaler.close()
        }
    }

    @Test
    fun bindable_vector_helpers_mutate_managed_list_and_project_back() {
        val managed = mutableListOf<Any?>("one", 2, null)
        val abi = WinRTBindableVectorProjection.fromManaged(managed)

        ComObjectReference(
            abi.asRawComPtr(),
            WinRTBindableInterfaceIds.IBindableVector,
            preventReleaseOnDispose = true,
        ).use { borrowed ->
            WinRTBindableVectorProjection.fromAbi(borrowed.getRefPointer().asRawAddress())!!.use { projected ->
                projected[1] = "two"
                projected.add("three")
                assertEquals(listOf("one", "two", null, "three"), projected.toList())
            }
        }

        assertEquals(listOf<Any?>("one", "two", null, "three"), managed)
    }

    @Test
    fun bindable_vector_hosts_expose_reference_ccw_suffix_interfaces() {
        val abi = WinRTBindableVectorProjection.fromManaged(mutableListOf<Any?>("one"))
        try {
            ComObjectReference(
                abi.asRawComPtr(),
                WinRTBindableInterfaceIds.IBindableVector,
                preventReleaseOnDispose = true,
            ).use { reference ->
                listOf(
                    IID.IStringable,
                    IID.IWeakReferenceSource,
                    IID.IMarshal,
                    IID.IAgileObject,
                    IID.IInspectable,
                    IID.IReferenceTrackerTarget,
                ).forEach { iid ->
                    reference.queryInterface(iid).getOrThrow().use { queried ->
                        assertTrue(queried.sameIdentity(reference))
                    }
                }
            }
        } finally {
            IUnknownReference(abi.asRawComPtr(), WinRTBindableInterfaceIds.IBindableVector).close()
        }
    }

    @Test
    fun bindable_object_marshaller_round_trips_plain_managed_values_and_external_inspectables() {
        val boxedValue = WinRTBindableObjectMarshaller.fromOwnedAbi(WinRTBindableObjectMarshaller.fromManaged(42))
        assertEquals(42, boxedValue)

        val host = WinRTInspectableComObject(
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = Guid("D8D45091-15A2-46A3-9177-84FAD8A5DE8A"),
                    methods = emptyList(),
                ),
            ),
            runtimeClassName = "test.RuntimeClass",
        )
        val inspectablePointer = host.createReference(Guid("D8D45091-15A2-46A3-9177-84FAD8A5DE8A")).use { reference ->
            reference.asInspectable().use { inspectable ->
                inspectable.getRefPointer()
            }
        }

        val projected = WinRTBindableObjectMarshaller.fromOwnedAbi(inspectablePointer.asRawAddress())
        assertTrue(projected is IWinRTObject)
        val runtimeClassName = projected.nativeObject.asInspectable().use { it.getRuntimeClassName() }
        assertEquals("test.RuntimeClass", runtimeClassName)
        (projected as? AutoCloseable)?.close()
        host.releaseManagedReference()
    }

    @Test
    fun bindable_object_marshaller_projects_delegate_objects_with_reference_interface() {
        var callCount = 0
        val descriptor = WinRTDelegateDescriptor(
            interfaceId = Guid("ABABABAB-1111-2222-3333-ABABABABABAB"),
            parameterKinds = emptyList(),
            returnKind = WinRTDelegateValueKind.UNIT,
        )
        val handle = WinRTDelegateBridge.createUnitDelegate(
            iid = descriptor.interfaceId,
            parameterKinds = emptyList(),
        ) {
            callCount += 1
        }
        val projected = object : WinRTProjectedDelegate {
            override fun createWinRTDelegateHandle(): WinRTDelegateHandle = handle
        }
        val abi = WinRTBindableObjectMarshaller.fromManaged(projected)

        try {
            ComObjectReference(abi.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true).use { reference ->
                reference.queryInterface(descriptor.referenceInterfaceId()).getOrThrow().use { delegateReferenceValue ->
                    PlatformAbi.confinedScope().use { scope ->
                        val valueOut = PlatformAbi.allocatePointerSlot(scope)
                        val hr = ComVtableInvoker.invokeArgs(
                            instance = delegateReferenceValue.pointer,
                            slot = IInspectableVftblSlots.FirstCustom,
                            arg0 = valueOut,
                        )
                        HResult(hr).requireSuccess()
                        WinRTDelegateReference(PlatformAbi.readPointer(valueOut), descriptor).use { delegateReference ->
                            delegateReference.invoke(emptyList())
                        }
                    }
                }
            }
            assertEquals(1, callCount)
        } finally {
            try {
                IUnknownReference(abi.asRawComPtr(), IID.IInspectable).close()
            } finally {
                handle.close()
            }
        }
    }
}
