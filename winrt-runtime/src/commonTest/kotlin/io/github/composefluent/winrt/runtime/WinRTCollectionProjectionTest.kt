package io.github.composefluent.winrt.runtime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WinRTCollectionProjectionTest {
    @Test
    fun string_collection_helpers_round_trip_hstring_elements() {
        val adapter = WinRTReferenceValueAdapters.string
        val iterableAbi = WinRTIterableProjection.fromManaged(listOf("one", "two"), adapter)
        val map = linkedMapOf("existing" to "value")
        val mapAbi = WinRTDictionaryProjection.fromManaged(map, adapter, adapter)

        try {
            ComObjectReference(iterableAbi.asRawComPtr(), iterableInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    iterableInterfaceIdFor(adapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRTIterableProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
                        assertEquals(listOf("one", "two"), projected.toList())
                    }
                }
            }

            ComObjectReference(mapAbi.asRawComPtr(), mapInterfaceIdFor(adapter, adapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapInterfaceIdFor(adapter, adapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRTDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        adapter,
                        adapter,
                    )!!.use { projected ->
                        assertTrue(projected.containsKey("existing"))
                        assertEquals("value", projected["existing"])
                        projected["added"] = "new"
                        assertEquals(linkedMapOf("existing" to "value", "added" to "new"), projected.toMap())
                    }
                }
            }
        } finally {
            if (!PlatformAbi.isNull(iterableAbi)) {
                IUnknownReference(iterableAbi.asRawComPtr(), iterableInterfaceIdFor(adapter)).close()
            }
            if (!PlatformAbi.isNull(mapAbi)) {
                IUnknownReference(mapAbi.asRawComPtr(), mapInterfaceIdFor(adapter, adapter)).close()
            }
        }
    }

    @Test
    fun iterable_helpers_round_trip_through_from_managed_and_from_abi() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val marshaler = WinRTIterableProjection.createMarshaler(listOf("one", "two"), adapter)
            ?: error("Iterable marshaler should not be null.")

        try {
            ComObjectReference(
                marshaler.abi.asRawComPtr(),
                iterableInterfaceIdFor(adapter),
                preventReleaseOnDispose = true,
            ).use { borrowed ->
                WinRTIterableProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
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
        val abi = WinRTReadOnlyListProjection.fromManaged(listOf("one", "two"), adapter)

        try {
            ComObjectReference(abi.asRawComPtr(), vectorViewInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorViewInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTVectorViewReference(borrowed.getRefPointer().asRawAddress(), vectorViewInterfaceIdFor(adapter)).use { vectorView ->
                        assertEquals(2u, vectorView.size())
                        assertEquals("one", projectBorrowedForTest(vectorView.getAtOrNull(0u), adapter))
                        val iterable = vectorView.queryInterface(iterableInterfaceIdFor(adapter)).getOrThrow()
                        WinRTIterableReference(iterable.pointer.asRawAddress(), iterableInterfaceIdFor(adapter)).use { base ->
                            val iterator = base.first(iteratorInterfaceIdFor(adapter))
                            iterator.use {
                                assertTrue(it.hasCurrent())
                                assertEquals("one", projectBorrowedForTest(it.currentOrNull(), adapter))
                            }
                        }
                    }
                    WinRTReadOnlyListProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
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
        val adapter = WinRTReferenceValueAdapters.object_
        val listAbi = WinRTReadOnlyListProjection.fromManaged(listOf<Any?>(null), adapter)
        val iterableAbi = WinRTIterableProjection.fromManaged(listOf<Any?>(null), adapter)

        try {
            ComObjectReference(listAbi.asRawComPtr(), vectorViewInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorViewInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTVectorViewReference(borrowed.getRefPointer().asRawAddress(), vectorViewInterfaceIdFor(adapter)).use { vectorView ->
                        assertEquals(null, vectorView.getAtOrNull(0u))
                        assertEquals(listOf(null), vectorView.getMany(0u, 1))
                    }
                }
            }
            ComObjectReference(iterableAbi.asRawComPtr(), iterableInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, iterableInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTIterableReference(borrowed.getRefPointer().asRawAddress(), iterableInterfaceIdFor(adapter)).use { iterable ->
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
    fun object_dictionary_ccw_distinguishes_null_values_from_missing_keys() {
        val allocated = mutableListOf<AutoCloseable>()
        val keyAdapter = labelAdapter(allocated)
        val valueAdapter = WinRTReferenceValueAdapters.object_
        val readOnlyAbi = WinRTReadOnlyDictionaryProjection.fromManaged(
            linkedMapOf<String, Any?>("present" to null),
            keyAdapter,
            valueAdapter,
        )
        val mutableAbi = WinRTDictionaryProjection.fromManaged(
            linkedMapOf<String, Any?>("present" to null),
            keyAdapter,
            valueAdapter,
        )
        val key = LabelInspectableBox.create("present").also(allocated::add).reference

        try {
            ComObjectReference(readOnlyAbi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(owner.pointer, mapViewInterfaceIdFor(keyAdapter, valueAdapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTMapViewReference(borrowed.getRefPointer().asRawAddress(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).use { mapView ->
                        assertTrue(mapView.hasKey(key))
                        assertEquals(null, mapView.lookupOrNull(key))
                    }
                }
            }
            ComObjectReference(mutableAbi.asRawComPtr(), mapInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(owner.pointer, mapInterfaceIdFor(keyAdapter, valueAdapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTMapReference(borrowed.getRefPointer().asRawAddress(), mapInterfaceIdFor(keyAdapter, valueAdapter)).use { map ->
                        assertTrue(map.hasKey(key))
                        assertEquals(null, map.lookupOrNull(key))
                    }
                }
            }
        } finally {
            if (!PlatformAbi.isNull(readOnlyAbi)) {
                IUnknownReference(readOnlyAbi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).close()
            }
            if (!PlatformAbi.isNull(mutableAbi)) {
                IUnknownReference(mutableAbi.asRawComPtr(), mapInterfaceIdFor(keyAdapter, valueAdapter)).close()
            }
            allocated.closeAll()
        }
    }

    @Test
    fun object_dictionary_rcw_and_ccw_preserve_null_keys_and_values() {
        val adapter = WinRTReferenceValueAdapters.object_
        val readOnlyManaged = linkedMapOf<Any?, Any?>(null to null)
        val mutableManaged = linkedMapOf<Any?, Any?>(null to null)
        val readOnlyAbi = WinRTReadOnlyDictionaryProjection.fromManaged(readOnlyManaged, adapter, adapter)
        val mutableAbi = WinRTDictionaryProjection.fromManaged(mutableManaged, adapter, adapter)

        try {
            ComObjectReference(readOnlyAbi.asRawComPtr(), mapViewInterfaceIdFor(adapter, adapter)).use { owner ->
                ComObjectReference(owner.pointer, mapViewInterfaceIdFor(adapter, adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTMapViewReference(borrowed.getRefPointer().asRawAddress(), mapViewInterfaceIdFor(adapter, adapter)).use { mapView ->
                        assertTrue(mapView.hasKey(PlatformAbi.nullPointer))
                        assertEquals(null, mapView.lookupOrNull(PlatformAbi.nullPointer))
                    }
                    WinRTReadOnlyDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        adapter,
                        adapter,
                    )!!.use { projected ->
                        assertTrue(projected.containsKey(null))
                        assertEquals(null, projected[null])
                    }
                }
            }
            ComObjectReference(mutableAbi.asRawComPtr(), mapInterfaceIdFor(adapter, adapter)).use { owner ->
                ComObjectReference(owner.pointer, mapInterfaceIdFor(adapter, adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        adapter,
                        adapter,
                    )!!.use { projected ->
                        assertTrue(projected.containsKey(null))
                        assertEquals(null, projected[null])
                        projected[null] = null
                        assertTrue(mutableManaged.containsKey(null))
                        assertEquals(null, mutableManaged[null])
                        projected.remove(null)
                        assertTrue(!mutableManaged.containsKey(null))
                    }
                }
            }
        } finally {
            if (!PlatformAbi.isNull(readOnlyAbi)) {
                IUnknownReference(readOnlyAbi.asRawComPtr(), mapViewInterfaceIdFor(adapter, adapter)).close()
            }
            if (!PlatformAbi.isNull(mutableAbi)) {
                IUnknownReference(mutableAbi.asRawComPtr(), mapInterfaceIdFor(adapter, adapter)).close()
            }
        }
    }

    @Test
    fun mutable_list_helpers_mutate_managed_list_and_project_back() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val managed = mutableListOf("one", "two")
        val abi = WinRTListProjection.fromManaged(managed, adapter)

        try {
            ComObjectReference(abi.asRawComPtr(), vectorInterfaceIdFor(adapter)).use { owner ->
                ComObjectReference(owner.pointer, vectorInterfaceIdFor(adapter), preventReleaseOnDispose = true).use { borrowed ->
                    WinRTVectorReference(borrowed.getRefPointer().asRawAddress(), vectorInterfaceIdFor(adapter)).use { vector ->
                        adapter.marshaller("updated").use { value ->
                            vector.setAt(0u, value)
                        }
                        adapter.marshaller("three").use { value ->
                            vector.append(value)
                        }
                        vector.removeAt(1u)
                    }

                    assertEquals(listOf("updated", "three"), managed)

                    WinRTListProjection.fromAbi(borrowed.getRefPointer().asRawAddress(), adapter)!!.use { projected ->
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
    fun vector_hosts_expose_reference_ccw_suffix_interfaces() {
        val allocated = mutableListOf<AutoCloseable>()
        val adapter = labelAdapter(allocated)
        val abi = WinRTListProjection.fromManaged(mutableListOf("one"), adapter)

        try {
            ComObjectReference(
                abi.asRawComPtr(),
                vectorInterfaceIdFor(adapter),
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
            IUnknownReference(abi.asRawComPtr(), vectorInterfaceIdFor(adapter)).close()
            allocated.closeAll()
        }
    }

    @Test
    fun read_only_dictionary_helpers_round_trip_through_from_managed_and_from_abi() {
        val allocated = mutableListOf<AutoCloseable>()
        val keyAdapter = labelAdapter(allocated)
        val valueAdapter = labelAdapter(allocated)
        val managed = linkedMapOf("one" to "two", "three" to "four")
        val abi = WinRTReadOnlyDictionaryProjection.fromManaged(managed, keyAdapter, valueAdapter)

        try {
            ComObjectReference(abi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapViewInterfaceIdFor(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRTReadOnlyDictionaryProjection.fromAbi(
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
    fun map_view_entries_compose_key_value_pair_and_nullable_value_helpers() {
        val keyAdapter = WinRTReferenceValueAdapters.string
        val valueAdapter = WinRTReferenceValueAdapters.valueType(
            projectedType = Int::class,
            projectedTypeName = "Int32",
            typeSignature = WinRTTypeSignature.int32(),
            nullableInterfaceId = IID.NullableInt,
        )
        val managed = linkedMapOf("one" to 1, "two" to 2)
        val abi = WinRTReadOnlyDictionaryProjection.fromManaged(managed, keyAdapter, valueAdapter)

        try {
            ComObjectReference(abi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapViewInterfaceIdFor(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRTReadOnlyDictionaryProjection.fromAbi(
                        borrowed.getRefPointer().asRawAddress(),
                        keyAdapter,
                        valueAdapter,
                    )!!.use { projected ->
                        assertEquals(managed, projected.toMap())
                        assertEquals(setOf("one" to 1, "two" to 2), projected.entries.map { it.key to it.value }.toSet())
                    }
                }
            }
        } finally {
            if (!PlatformAbi.isNull(abi)) {
                IUnknownReference(abi.asRawComPtr(), mapViewInterfaceIdFor(keyAdapter, valueAdapter)).close()
            }
        }
    }

    @Test
    fun mutable_dictionary_helpers_mutate_managed_map_and_project_back() {
        val allocated = mutableListOf<AutoCloseable>()
        val keyAdapter = labelAdapter(allocated)
        val valueAdapter = labelAdapter(allocated)
        val managed = linkedMapOf("one" to "two")
        val abi = WinRTDictionaryProjection.fromManaged(managed, keyAdapter, valueAdapter)

        try {
            ComObjectReference(abi.asRawComPtr(), mapInterfaceIdFor(keyAdapter, valueAdapter)).use { owner ->
                ComObjectReference(
                    owner.pointer,
                    mapInterfaceIdFor(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                ).use { borrowed ->
                    WinRTDictionaryProjection.fromAbi(
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

private fun labelAdapter(allocated: MutableList<AutoCloseable>): WinRTReferenceValueAdapter<String> =
    WinRTReferenceValueAdapter(
        projectedTypeName = "test.LabelInspectable",
        typeSignature = WinRTTypeSignature.object_(),
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
    private val host: WinRTInspectableComObject,
    val reference: ComObjectReference,
) : AutoCloseable {
    override fun close() {
        reference.close()
        host.releaseManagedReference()
    }

    companion object {
        private val interfaceId = Guid("D8D45091-15A2-46A3-9177-84FAD8A5DE8A")

        fun create(label: String): LabelInspectableBox {
            val host = WinRTInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
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

private fun iterableTypeHandleFor(adapter: WinRTReferenceValueAdapter<*>): WinRTTypeHandle =
    WinRTTypeHandle("kotlin.collections.Iterable<${adapter.projectedTypeName}>", iterableInterfaceIdFor(adapter))

private fun iterableInterfaceIdFor(adapter: WinRTReferenceValueAdapter<*>): Guid =
    WinRTCollectionInterfaceIds.iterable(adapter.typeSignature)

private fun iteratorInterfaceIdFor(adapter: WinRTReferenceValueAdapter<*>): Guid =
    WinRTCollectionInterfaceIds.iterator(adapter.typeSignature)

private fun vectorViewInterfaceIdFor(adapter: WinRTReferenceValueAdapter<*>): Guid =
    WinRTCollectionInterfaceIds.vectorView(adapter.typeSignature)

private fun vectorInterfaceIdFor(adapter: WinRTReferenceValueAdapter<*>): Guid =
    WinRTCollectionInterfaceIds.vector(adapter.typeSignature)

private fun mapViewInterfaceIdFor(
    keyAdapter: WinRTReferenceValueAdapter<*>,
    valueAdapter: WinRTReferenceValueAdapter<*>,
): Guid = WinRTCollectionInterfaceIds.mapView(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun mapInterfaceIdFor(
    keyAdapter: WinRTReferenceValueAdapter<*>,
    valueAdapter: WinRTReferenceValueAdapter<*>,
): Guid = WinRTCollectionInterfaceIds.map(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun mapViewTypeHandleFor(
    keyAdapter: WinRTReferenceValueAdapter<*>,
    valueAdapter: WinRTReferenceValueAdapter<*>,
): WinRTTypeHandle = WinRTTypeHandle(
    "kotlin.collections.Map<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
    mapViewInterfaceIdFor(keyAdapter, valueAdapter),
)

private fun mapTypeHandleFor(
    keyAdapter: WinRTReferenceValueAdapter<*>,
    valueAdapter: WinRTReferenceValueAdapter<*>,
): WinRTTypeHandle = WinRTTypeHandle(
    "kotlin.collections.MutableMap<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
    mapInterfaceIdFor(keyAdapter, valueAdapter),
)

private fun <T> projectBorrowedForTest(
    reference: IUnknownReference?,
    adapter: WinRTReferenceValueAdapter<T>,
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
