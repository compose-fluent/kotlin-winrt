package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtBindableProjectionTest {
    @Test
    fun bindable_iterable_helpers_round_trip_non_generic_values() {
        val values = listOf("one", 2, null)
        val marshaler = WinRtBindableIterableProjection.createMarshaler(values)
            ?: error("IBindableIterable marshaler should not be null.")

        try {
            ComObjectReference(
                marshaler.abi.asRawComPtr(),
                WinRtBindableInterfaceIds.IBindableIterable,
                preventReleaseOnDispose = true,
            ).use { borrowed ->
                WinRtBindableIterableProjection.fromAbi(borrowed.getRefPointer().asRawAddress())!!.use { projected ->
                    assertEquals(values, projected.toList())
                    assertEquals(
                        WinRtTypeHandle("kotlin.collections.Iterable<kotlin.Any?>", WinRtBindableInterfaceIds.IBindableIterable),
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
        val abi = WinRtBindableVectorProjection.fromManaged(managed)

        ComObjectReference(
            abi.asRawComPtr(),
            WinRtBindableInterfaceIds.IBindableVector,
            preventReleaseOnDispose = true,
        ).use { borrowed ->
            WinRtBindableVectorProjection.fromAbi(borrowed.getRefPointer().asRawAddress())!!.use { projected ->
                projected[1] = "two"
                projected.add("three")
                assertEquals(listOf("one", "two", null, "three"), projected.toList())
            }
        }

        assertEquals(listOf("one", "two", null, "three"), managed)
    }

    @Test
    fun bindable_object_marshaller_round_trips_plain_managed_values_and_external_inspectables() {
        val boxedValue = WinRtBindableObjectMarshaller.fromOwnedAbi(WinRtBindableObjectMarshaller.fromManaged(42))
        assertEquals(42, boxedValue)

        val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
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

        val projected = WinRtBindableObjectMarshaller.fromOwnedAbi(inspectablePointer)
        assertTrue(projected is IWinRTObject)
        val runtimeClassName = ((projected as IWinRTObject).nativeObject.asInspectable()).use { it.getRuntimeClassName() }
        assertEquals("test.RuntimeClass", runtimeClassName)
        (projected as? AutoCloseable)?.close()
        host.releaseManagedReference()
    }
}
