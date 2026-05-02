package io.github.composefluent.winrt.runtime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtCollectionProjectionTest {
    @Test
    fun iterable_helpers_round_trip_through_from_managed_and_from_abi() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val marshaler = WinRtIterableProjection.createMarshaler(listOf("one", "two"), adapter)
            ?: error("Iterable marshaler should not be null.")

        try {
            ComObjectReference(
                marshaler.abi.asRawComPtr(),
                iterableInterfaceIdFor(adapter),
                preventReleaseOnDispose = true,
            ).use { borrowed ->
                WinRtIterableProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
                    assertEquals(listOf("one", "two"), projected.toList())
                    assertEquals(iterableTypeHandleFor(adapter), projected.primaryTypeHandle)
                }
            }
        } finally {
            marshaler.close()
            allocated.closeAll()
        }
    }

    @Test
    fun read_only_list_helpers_expose_vector_view_and_base_iterable() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val abi = WinRtReadOnlyListProjection.fromManaged(listOf("one", "two"), adapter)

        try {
            ComObjectReference(abi.asRawComPtr(), vectorViewInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorViewInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRtVectorViewReference(borrowed.getRefPointer().asRawAddress(), vectorViewInterfaceIdFor(adapter)).use { vectorView ->
                        assertEquals(2u, vectorView.size())
                        assertEquals("one", projectBorrowedForTest(vectorView.getAtOrNull(0u), adapter))
                        val iterable = vectorView.queryInterface(iterableInterfaceIdFor(adapter)).getOrThrow()
                        WinRtIterableReference(iterable.pointer.asRawAddress(), iterableInterfaceIdFor(adapter)).use { base ->
                            val iterator = base.first(iteratorInterfaceIdFor(adapter))
                            iterator.use {
                                assertTrue(it.hasCurrent())
                                assertEquals("one", projectBorrowedForTest(it.currentOrNull(), adapter))
                            }
                        }
                    }
                    WinRtReadOnlyListProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
                        assertEquals(listOf("one", "two"), projected.toList())
                    }
                }
            }
        } finally {
            allocated.closeAll()
        }
    }

    @Test
    fun object_collection_ccw_outputs_null_values_as_null_inspectable_pointers() {
        val adapter = WinRtReferenceValueAdapters.object_
        val listAbi = WinRtReadOnlyListProjection.fromManaged(listOf<Any?>(null), adapter)
        val iterableAbi = WinRtIterableProjection.fromManaged(listOf<Any?>(null), adapter)

        try {
            ComObjectReference(listAbi.asRawComPtr(), vectorViewInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorViewInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRtVectorViewReference(borrowed.getRefPointer().asRawAddress(), vectorViewInterfaceIdFor(adapter)).use { vectorView ->
                        assertEquals(null, vectorView.getAtOrNull(0u))
                        assertEquals(listOf(null), vectorView.getMany(0u, 1))
                    }
                }
            }
            ComObjectReference(iterableAbi.asRawComPtr(), iterableInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, iterableInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRtIterableReference(borrowed.getRefPointer().asRawAddress(), iterableInterfaceIdFor(adapter)).use { iterable ->
                        iterable.first(iteratorInterfaceIdFor(adapter)).use { iterator ->
                            assertTrue(iterator.hasCurrent())
                            assertEquals(null, iterator.currentOrNull())
                            assertEquals(listOf(null), iterator.getMany(1))
                        }
                    }
                }
            }
        } finally {
            if (!PlatformAbi.isNull(listAbi)) {
                IUnknownReference(listAbi.asRawComPtr()).close()
            }
            if (!PlatformAbi.isNull(iterableAbi)) {
                IUnknownReference(iterableAbi.asRawComPtr()).close()
            }
        }
    }

    @Test
    fun mutable_list_helpers_mutate_managed_list_and_project_back() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val managed = mutableListOf("one", "two")
        val abi = WinRtListProjection.fromManaged(managed, adapter)

        try {
            ComObjectReference(abi.asRawComPtr(), vectorInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRtVectorReference(borrowed.getRefPointer().asRawAddress(), vectorInterfaceIdFor(adapter)).use { vector ->
                        adapter.marshaller("updated").use { value ->
                            vector.setAt(0u, value)
                        }
                        adapter.marshaller("three").use { value ->
                            vector.append(value)
                        }
                        vector.removeAt(1u)
                    }

                    assertEquals(listOf("updated", "three"), managed)

                    WinRtListProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
                        projected.add("four")
                        assertEquals(listOf("updated", "three", "four"), projected.toList())
                    }
                    assertEquals(listOf("updated", "three", "four"), managed)
                }
            }
        } finally {
            allocated.closeAll()
        }
    }

    @Test
    fun read_only_dictionary_helpers_round_trip_through_from_managed_and_from_abi() {
        val allocated = mutableListOf<AutoCloseable>()
        val keyAdapter = labelAdapter(allocated)
        val valueAdapter = labelAdapter(allocated)
        val managed = linkedMapOf("one" to "two", "three" to "four")
        val abi = WinRtReadOnlyDictionaryProjection.fromManaged(managed, keyAdapter, valueAdapter)

        try {
            ComObjectReference(abi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapViewInterfaceIdFor(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRtReadOnlyDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        keyAdapter,
                        valueAdapter,
                    )!!.use { projected ->
                        assertEquals(managed, projected.toMap())
                        assertEquals(
                            mapViewTypeHandleFor(keyAdapter, valueAdapter),
                            projected.primaryTypeHandle,
                        )
                    }
                }
            }
        } finally {
            allocated.closeAll()
        }
    }

    @Test
    fun mutable_dictionary_helpers_mutate_managed_map_and_project_back() {
        val allocated = mutableListOf<AutoCloseable>()
        val keyAdapter = labelAdapter(allocated)
        val valueAdapter = labelAdapter(allocated)
        val managed = linkedMapOf("one" to "two")
        val abi = WinRtDictionaryProjection.fromManaged(managed, keyAdapter, valueAdapter)

        try {
            ComObjectReference(abi.asRawComPtr(), mapInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapInterfaceIdFor(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRtDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        keyAdapter,
                        valueAdapter,
                    )!!.use { projected ->
                        projected["three"] = "four"
                        projected.remove("one")
                        assertEquals(linkedMapOf("three" to "four"), projected.toMap())
                        assertEquals(
                            mapTypeHandleFor(keyAdapter, valueAdapter),
                            projected.primaryTypeHandle,
                        )
                    }
                }
            }
            assertEquals(linkedMapOf("three" to "four"), managed)
        } finally {
            allocated.closeAll()
        }
    }

}

private fun labelAdapter(allocated: MutableList<AutoCloseable>): WinRtReferenceValueAdapter<String> =
    WinRtReferenceValueAdapter(
        projectedTypeName = "test.LabelInspectable",
        typeSignature = WinRtTypeSignature.object_(),
        projector = { reference ->
            reference?.tryAsInspectable()?.use { inspectable ->
                inspectable.getRuntimeClassName()
            } ?: "<null>"
        },
        marshaller = { value ->
            LabelInspectableBox.create(value).also(allocated::add).reference
        },
    )

private class LabelInspectableBox private constructor(
    private val host: WinRtInspectableComObject,
    val reference: ComObjectReference,
) : AutoCloseable {
    override fun close() {
        reference.close()
        host.releaseManagedReference()
    }

    companion object {
        private val interfaceId = Guid("D8D45091-15A2-46A3-9177-84FAD8A5DE8A")

        fun create(label: String): LabelInspectableBox {
            val host = WinRtInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = interfaceId,
                        methods = emptyList(),
                    ),
                ),
                runtimeClassName = label,
            )
            return LabelInspectableBox(host, host.createReference(interfaceId))
        }
    }
}

private fun iterableTypeHandleFor(adapter: WinRtReferenceValueAdapter<*>): WinRtTypeHandle =
    WinRtTypeHandle("kotlin.collections.Iterable<${adapter.projectedTypeName}>", iterableInterfaceIdFor(adapter))

private fun iterableInterfaceIdFor(adapter: WinRtReferenceValueAdapter<*>): Guid =
    WinRtCollectionInterfaceIds.iterable(adapter.typeSignature)

private fun iteratorInterfaceIdFor(adapter: WinRtReferenceValueAdapter<*>): Guid =
    WinRtCollectionInterfaceIds.iterator(adapter.typeSignature)

private fun vectorViewInterfaceIdFor(adapter: WinRtReferenceValueAdapter<*>): Guid =
    WinRtCollectionInterfaceIds.vectorView(adapter.typeSignature)

private fun vectorInterfaceIdFor(adapter: WinRtReferenceValueAdapter<*>): Guid =
    WinRtCollectionInterfaceIds.vector(adapter.typeSignature)

private fun mapViewInterfaceIdFor(
    keyAdapter: WinRtReferenceValueAdapter<*>,
    valueAdapter: WinRtReferenceValueAdapter<*>,
): Guid = WinRtCollectionInterfaceIds.mapView(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun mapInterfaceIdFor(
    keyAdapter: WinRtReferenceValueAdapter<*>,
    valueAdapter: WinRtReferenceValueAdapter<*>,
): Guid = WinRtCollectionInterfaceIds.map(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun mapViewTypeHandleFor(
    keyAdapter: WinRtReferenceValueAdapter<*>,
    valueAdapter: WinRtReferenceValueAdapter<*>,
): WinRtTypeHandle = WinRtTypeHandle(
    "kotlin.collections.Map<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
    mapViewInterfaceIdFor(keyAdapter, valueAdapter),
)

private fun mapTypeHandleFor(
    keyAdapter: WinRtReferenceValueAdapter<*>,
    valueAdapter: WinRtReferenceValueAdapter<*>,
): WinRtTypeHandle = WinRtTypeHandle(
    "kotlin.collections.MutableMap<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
    mapInterfaceIdFor(keyAdapter, valueAdapter),
)

private fun <T> projectBorrowedForTest(
    reference: IUnknownReference?,
    adapter: WinRtReferenceValueAdapter<T>,
): T = try {
    adapter.projector(reference)
} finally {
    reference?.close()
}

private fun List<AutoCloseable>.closeAll() {
    asReversed().forEach { value ->
        runCatching { value.close() }
    }
}
