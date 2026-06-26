package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinRTCollectionInteropTest {
    @Test
    fun iterable_and_iterator_wrappers_use_expected_slots() {
        PlatformAbi.confinedScope().use { scope ->
            val firstValue = FakeReference(scope, "first")
            val secondValue = FakeReference(scope, "second")
            val iterator = FakeIteratorReference(
                scope = scope,
                currentResults = listOf(firstValue, secondValue),
                getManyResults = listOf(firstValue, null, secondValue),
            )
            val iterable = FakeIterableReference(scope, iterator)

            val first = iterable.first(Guid("00000000-0000-0000-0000-000000000101"))
            assertTrue(first.hasCurrent())
            assertEquals(firstValue.pointer, first.current().pointer)
            assertTrue(first.moveNext())
            assertEquals(secondValue.pointer, first.current().pointer)
            assertFalse(first.moveNext())
            assertEquals(listOf(firstValue.pointer, PlatformAbi.nullComPtr, secondValue.pointer), first.getMany(3).map { it?.pointer ?: PlatformAbi.nullComPtr })
            assertEquals(listOf(6), iterable.objectSlots)
            assertEquals(listOf(7, 6, 8, 6, 8), iterator.slotCalls)
            assertEquals(listOf(9 to 3), iterator.getManySlots)
        }
    }

    @Test
    fun vector_view_wrapper_reads_size_indexof_and_getmany_slots() {
        PlatformAbi.confinedScope().use { scope ->
            val element = FakeReference(scope, "element")
            val key = FakeReference(scope, "key")
            val vectorView = FakeVectorViewReference(
                scope = scope,
                getAtResult = element,
                sizeResult = 3u,
                indexOfResult = true to 2u,
                getManyResults = listOf(element, null),
            )

            assertEquals(3u, vectorView.size())
            assertEquals(element.pointer, vectorView.getAt(1u).pointer)
            assertEquals(true to 2u, vectorView.indexOf(key))
            assertEquals(listOf(element.pointer, PlatformAbi.nullComPtr), vectorView.getMany(1u, 2).map { it?.pointer ?: PlatformAbi.nullComPtr })
            assertEquals(listOf(7), vectorView.uintSlots)
            assertEquals(listOf(6 to 1u), vectorView.uintArgSlots)
            assertEquals(listOf<Pair<Int, ComObjectReference>>(8 to key), vectorView.indexOfSlots)
            assertEquals(listOf(9 to (1u to 2)), vectorView.getManySlots)
        }
    }

    @Test
    fun vector_wrapper_uses_expected_slots_for_mutation_and_views() {
        PlatformAbi.confinedScope().use { scope ->
            val element = FakeReference(scope, "element")
            val replacement = FakeReference(scope, "replacement")
            val vectorView = FakeVectorViewReference(
                scope = scope,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
                getManyResults = listOf(element),
            )
            val vector = FakeVectorReference(
                scope = scope,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
                getManyResults = listOf(element, replacement),
                vectorViewResult = vectorView,
                getAtResultsByIndex = mapOf(0u to element, 1u to replacement),
            )

            assertEquals(element.pointer, vector.getAt(0u).pointer)
            assertEquals(5u, vector.size())
            assertEquals(vectorView.pointer, vector.getView(Guid("00000000-0000-0000-0000-000000000301")).pointer)
            assertEquals(true to 3u, vector.indexOf(element))
            vector.setAt(1u, replacement)
            vector.insertAt(2u, replacement)
            vector.removeAt(4u)
            vector.append(replacement)
            vector.removeAtEnd()
            vector.clear()
            vector.replaceAll(listOf(element, replacement))
            assertEquals(listOf(element.pointer, replacement.pointer), vector.getMany(1u, 2).map { it?.pointer ?: PlatformAbi.nullComPtr })

            assertEquals(listOf(7), vector.uintSlots)
            assertEquals(listOf(6 to 0u), vector.uintArgSlots)
            assertEquals(listOf(8), vector.objectSlots)
            assertEquals(listOf<Pair<Int, ComObjectReference>>(9 to element), vector.indexOfSlots)
            assertEquals(listOf(10 to (1u to replacement.pointer.asRawAddress()), 11 to (2u to replacement.pointer.asRawAddress())), vector.uintObjectSlots)
            assertEquals(listOf(12 to 4u), vector.removeAtSlots)
            assertEquals(listOf(13 to replacement.pointer.asRawAddress()), vector.appendSlots)
            assertEquals(listOf(14, 15), vector.unitSlots)
            assertEquals(listOf<List<ComObjectReference>>(listOf(element, replacement)), vector.replaceAllSlots)
            assertEquals(listOf(16 to (1u to 2)), vector.getManySlots)
        }
    }

    @Test
    fun object_vector_input_marshaling_accepts_null_values() {
        PlatformAbi.confinedScope().use { scope ->
            val vector = FakeVectorReference(
                scope = scope,
                getAtResult = null,
                sizeResult = 0u,
                indexOfResult = false to 0u,
                getManyResults = emptyList(),
                vectorViewResult = FakeVectorViewReference(
                    scope = scope,
                    getAtResult = null,
                    sizeResult = 0u,
                    indexOfResult = false to 0u,
                    getManyResults = emptyList(),
                ),
            )

            WinRTVectorListAdapter(
                vector = vector,
                elementAdapter = WinRTReferenceValueAdapters.object_,
                elementMarshaller = WinRTReferenceValueAdapters.object_::createInputMarshaler,
            ).add(null)

            assertEquals(listOf(13 to PlatformAbi.nullPointer), vector.appendSlots)
        }
    }

    @Test
    fun map_and_map_view_wrappers_use_expected_slots() {
        PlatformAbi.confinedScope().use { scope ->
            val key = FakeReference(scope, "key")
            val value = FakeReference(scope, "value")
            val lookedUp = FakeReference(scope, "lookup")
            val firstPart = FakeMapViewReference(
                scope = scope,
                lookupResult = lookedUp,
                sizeResult = 2u,
                hasKeyResult = true,
                splitResult = null to null,
            )
            val secondPart = FakeMapViewReference(
                scope = scope,
                lookupResult = value,
                sizeResult = 1u,
                hasKeyResult = false,
                splitResult = null to null,
            )
            val mapView = FakeMapViewReference(
                scope = scope,
                lookupResult = lookedUp,
                sizeResult = 5u,
                hasKeyResult = true,
                splitResult = firstPart to secondPart,
            )
            val map = FakeMapReference(
                scope = scope,
                lookupResult = lookedUp,
                sizeResult = 5u,
                hasKeyResult = true,
                insertResult = true,
                mapViewResult = mapView,
            )

            assertEquals(lookedUp.pointer, map.lookup(key).pointer)
            assertEquals(5u, map.size())
            assertTrue(map.hasKey(key))
            assertEquals(mapView.pointer, map.getView(Guid("00000000-0000-0000-0000-000000000401")).pointer)
            assertTrue(map.insert(key, value))
            map.remove(key)
            map.clear()

            assertEquals(lookedUp.pointer, mapView.lookup(key).pointer)
            assertEquals(5u, mapView.size())
            assertTrue(mapView.hasKey(key))

            val split = mapView.split(Guid("00000000-0000-0000-0000-000000000402"))
            assertEquals(firstPart.pointer, split.first?.pointer)
            assertEquals(secondPart.pointer, split.second?.pointer)

            assertEquals(listOf<Pair<Int, ComObjectReference>>(6 to key), map.lookupSlots)
            assertEquals(listOf<Pair<Int, ComObjectReference>>(8 to key), map.hasKeySlots)
            assertEquals(listOf(9), map.objectSlots)
            assertEquals(listOf<Pair<Int, Pair<ComObjectReference, ComObjectReference>>>(10 to (key to value)), map.insertSlots)
            assertEquals(listOf<Pair<Int, ComObjectReference>>(11 to key), map.removeSlots)
            assertEquals(listOf(12), map.unitSlots)
            assertEquals(listOf(7), map.uintSlots)

            assertEquals(listOf(6 to key.pointer.asRawAddress()), mapView.lookupSlots)
            assertEquals(listOf(8 to key.pointer.asRawAddress()), mapView.hasKeySlots)
            assertEquals(listOf(9), mapView.splitSlots)
            assertEquals(listOf(7), mapView.uintSlots)
        }
    }

    @Test
    fun collection_adapters_project_vector_and_map_surfaces() {
        PlatformAbi.confinedScope().use { scope ->
            val one = FakeReference(scope, "one")
            val two = FakeReference(scope, "two")
            val three = FakeReference(scope, "three")
            val labels = mutableMapOf<RawAddress, String>()
            val adapter = fakeLabelAdapter(scope, labels)
            listOf(one, two, three).forEach { reference ->
                labels[reference.pointer.asRawAddress()] = reference.label
            }
            val vector = FakeVectorReference(
                scope = scope,
                getAtResult = one,
                sizeResult = 2u,
                indexOfResult = true to 1u,
                getManyResults = listOf(one, two),
                vectorViewResult = FakeVectorViewReference(
                    scope = scope,
                    getAtResult = one,
                    sizeResult = 2u,
                    indexOfResult = true to 1u,
                    getManyResults = listOf(one, two),
                ),
                getAtResultsByIndex = mapOf(0u to one, 1u to two, 2u to three),
            )

            WinRTVectorListAdapter(
                vector = vector,
                elementAdapter = adapter,
                elementMarshaller = adapter::createInputMarshaler,
            ).use { list ->
                assertEquals(listOf("one", "two"), list.toList())
                list.add("three")
                assertEquals(13, vector.appendSlots.single().first)
                assertFalse(PlatformAbi.isNull(vector.appendSlots.single().second))
                assertEquals("one", list.removeAt(0))
                assertEquals(listOf(12 to 0u), vector.removeAtSlots)
            }

            val pairAdapter = winRTKeyValuePairAdapter(adapter, adapter)
            val iterable = FakeIterableReference(
                scope = scope,
                firstResult = FakeIteratorReference(
                    scope = scope,
                    currentResults = emptyList(),
                    currentAbiResults = listOf(
                        createPairAbi(pairAdapter, "one", "two"),
                        createPairAbi(pairAdapter, "two", "three"),
                    ),
                    getManyResults = emptyList(),
                ),
                iteratorFactory = {
                    FakeIteratorReference(
                        scope = scope,
                        currentResults = emptyList(),
                        currentAbiResults = listOf(
                            createPairAbi(pairAdapter, "one", "two"),
                            createPairAbi(pairAdapter, "two", "three"),
                        ),
                        getManyResults = emptyList(),
                    )
                },
            )
            val mapView = FakeMapViewReference(
                scope = scope,
                lookupResult = two,
                sizeResult = 2u,
                hasKeyResult = true,
                splitResult = null to null,
                iterableResult = iterable,
            )

            WinRTMapViewAdapter(
                mapView = mapView,
                iterableInterfaceId = Guid("00000000-0000-0000-0000-000000000777"),
                iteratorInterfaceId = Guid("00000000-0000-0000-0000-000000000778"),
                keyValuePairInterfaceId = Guid("00000000-0000-0000-0000-000000000779"),
                keyAdapter = adapter,
                valueAdapter = adapter,
                keyMarshaller = adapter::createInputMarshaler,
            ).use { projected ->
                assertEquals(2, projected.size)
                assertEquals("two", projected["one"])
                assertTrue(projected.containsKey("two"))
                assertEquals(mapOf("one" to "two", "two" to "three"), projected.toMap())
            }
        }
    }

    private open class FakeReference(
        scope: NativeScope,
        val label: String,
    ) : IUnknownReference(PlatformAbi.allocateBytes(scope, 8).asRawComPtr(), Guid("00000000-0000-0000-0000-000000000201")) {
        override fun close() = Unit
    }

    private fun fakeLabelAdapter(
        scope: NativeScope,
        labels: MutableMap<RawAddress, String>,
    ): WinRTReferenceValueAdapter<String> =
        object : WinRTReferenceValueAdapter<String>(
            projectedTypeName = "test.Label",
            typeSignature = WinRTTypeSignature.object_(),
            projector = { reference -> labels[reference?.pointer?.asRawAddress()] ?: "<null>" },
            marshaller = { value ->
                FakeReference(scope, value).also { reference ->
                    labels[reference.pointer.asRawAddress()] = value
                }
            },
        ) {
            override fun projectAbi(pointer: RawAddress): String = labels[pointer] ?: "<null>"

            override fun disposeAbi(pointer: RawAddress) = Unit

            override fun createInputMarshaler(value: String): WinRTObjectMarshaler =
                createLabelMarshaler(scope, labels, value)

            override fun createOutputMarshaler(value: String): WinRTObjectMarshaler =
                createLabelMarshaler(scope, labels, value)
        }

    private fun createLabelMarshaler(
        scope: NativeScope,
        labels: MutableMap<RawAddress, String>,
        value: String,
    ): WinRTObjectMarshaler {
        val pointer = PlatformAbi.allocateBytes(scope, 8)
        labels[pointer] = value
        return WinRTObjectMarshaler(pointer)
    }

    private fun createPairAbi(
        pairAdapter: WinRTReferenceValueAdapter<Map.Entry<String, String>>,
        key: String,
        value: String,
    ): RawAddress {
        val entry = object : Map.Entry<String, String> {
            override val key: String = key
            override val value: String = value
        }
        val marshaler = pairAdapter.createOutputMarshaler(entry)
        val abi = marshaler.abi
        marshaler.close()
        return abi
    }

    private class FakeKeyValuePairReference(
        scope: NativeScope,
        private val keyResult: IUnknownReference?,
        private val valueResult: IUnknownReference?,
    ) : WinRTKeyValuePairReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000210")) {
        override fun key(): IUnknownReference? = keyResult

        override fun keyAbiOrNull(): RawAddress? = keyResult?.pointer?.asRawAddress()

        override fun value(): IUnknownReference? = valueResult

        override fun valueAbiOrNull(): RawAddress? = valueResult?.pointer?.asRawAddress()

        override fun close() = Unit
    }

    private class FakeIterableReference(
        scope: NativeScope,
        private val firstResult: WinRTIteratorReference,
        private val iteratorFactory: (() -> WinRTIteratorReference)? = null,
    ) : WinRTIterableReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000202")) {
        val objectSlots = mutableListOf<Int>()

        override fun first(iteratorInterfaceId: Guid): WinRTIteratorReference {
            objectSlots += 6
            return iteratorFactory?.invoke() ?: firstResult
        }

        override fun close() = Unit
    }

    private class FakeIteratorReference(
        scope: NativeScope,
        private val currentResults: List<IUnknownReference?>,
        private val currentAbiResults: List<RawAddress?>? = null,
        private val getManyResults: List<IUnknownReference?>,
    ) : WinRTIteratorReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000203")) {
        private var currentIndex = 0
        val slotCalls = mutableListOf<Int>()
        val getManySlots = mutableListOf<Pair<Int, Int>>()

        override fun currentOrNull(): IUnknownReference? {
            slotCalls += 6
            return currentResults.getOrNull(currentIndex)
        }

        override fun currentAbiOrNull(): RawAddress? {
            slotCalls += 6
            currentAbiResults?.let { return it.getOrNull(currentIndex) }
            return currentResults.getOrNull(currentIndex)?.pointer?.asRawAddress()
        }

        override fun hasCurrent(): Boolean {
            slotCalls += 7
            return currentIndex < (currentAbiResults?.size ?: currentResults.size)
        }

        override fun moveNext(): Boolean {
            slotCalls += 8
            currentIndex += 1
            return currentIndex < (currentAbiResults?.size ?: currentResults.size)
        }

        override fun invokeGetMany(slot: Int, startIndex: UInt?, capacity: Int): List<IUnknownReference?> {
            getManySlots += slot to capacity
            return getManyResults.take(capacity)
        }

        override fun close() = Unit
    }

    private class FakeVectorViewReference(
        scope: NativeScope,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
        private val getManyResults: List<IUnknownReference?>,
    ) : WinRTVectorViewReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000204")) {
        val uintSlots = mutableListOf<Int>()
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val indexOfSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val getManySlots = mutableListOf<Pair<Int, Pair<UInt, Int>>>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResult
        }

        override fun getAtAbiOrNull(index: UInt): RawAddress? {
            uintArgSlots += 6 to index
            return getAtResult?.pointer?.asRawAddress()
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> {
            indexOfSlots += 8 to value
            return indexOfResult
        }

        override fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
            getManySlots += 9 to (startIndex to capacity)
            return getManyResults.take(capacity)
        }

        override fun close() = Unit
    }

    private class FakeVectorReference(
        scope: NativeScope,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
        private val getManyResults: List<IUnknownReference?>,
        private val vectorViewResult: WinRTVectorViewReference,
        private val getAtResultsByIndex: Map<UInt, IUnknownReference?> = emptyMap(),
    ) : WinRTVectorReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000205")) {
        val uintSlots = mutableListOf<Int>()
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val objectSlots = mutableListOf<Int>()
        val indexOfSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintObjectSlots = mutableListOf<Pair<Int, Pair<UInt, RawAddress>>>()
        val removeAtSlots = mutableListOf<Pair<Int, UInt>>()
        val appendSlots = mutableListOf<Pair<Int, RawAddress>>()
        val unitSlots = mutableListOf<Int>()
        val getManySlots = mutableListOf<Pair<Int, Pair<UInt, Int>>>()
        val replaceAllSlots = mutableListOf<List<ComObjectReference>>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResultsByIndex[index] ?: getAtResult
        }

        override fun getAtAbiOrNull(index: UInt): RawAddress? {
            uintArgSlots += 6 to index
            return (getAtResultsByIndex[index] ?: getAtResult)?.pointer?.asRawAddress()
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun getView(vectorViewInterfaceId: Guid): WinRTVectorViewReference {
            objectSlots += 8
            return vectorViewResult
        }

        override fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> {
            indexOfSlots += 9 to value
            return indexOfResult
        }

        override fun setAt(index: UInt, valuePointer: RawAddress) {
            uintObjectSlots += 10 to (index to valuePointer)
        }

        override fun insertAt(index: UInt, valuePointer: RawAddress) {
            uintObjectSlots += 11 to (index to valuePointer)
        }

        override fun removeAt(index: UInt) {
            removeAtSlots += 12 to index
        }

        override fun append(valuePointer: RawAddress) {
            appendSlots += 13 to valuePointer
        }

        override fun removeAtEnd() {
            unitSlots += 14
        }

        override fun clear() {
            unitSlots += 15
        }

        override fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
            getManySlots += 16 to (startIndex to capacity)
            return getManyResults.take(capacity)
        }

        override fun replaceAll(items: List<ComObjectReference>) {
            replaceAllSlots += items
        }

        override fun close() = Unit
    }

    private open class FakeMapViewReference(
        scope: NativeScope,
        private val lookupResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val hasKeyResult: Boolean,
        private val splitResult: Pair<WinRTMapViewReference?, WinRTMapViewReference?>,
        private val iterableResult: WinRTIterableReference? = null,
    ) : WinRTMapViewReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000206")) {
        val lookupSlots = mutableListOf<Pair<Int, RawAddress>>()
        val hasKeySlots = mutableListOf<Pair<Int, RawAddress>>()
        val uintSlots = mutableListOf<Int>()
        val splitSlots = mutableListOf<Int>()

        override fun lookupOrNull(key: ComObjectReference): IUnknownReference? {
            return lookupOrNull(key.pointer.asRawAddress())
        }

        override fun lookupOrNull(key: RawAddress): IUnknownReference? {
            lookupSlots += 6 to key
            return lookupResult
        }

        override fun lookupAbiOrNull(key: RawAddress): RawAddress? {
            lookupSlots += 6 to key
            return lookupResult?.pointer?.asRawAddress()
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun hasKey(key: ComObjectReference): Boolean {
            return hasKey(key.pointer.asRawAddress())
        }

        override fun hasKey(key: RawAddress): Boolean {
            hasKeySlots += 8 to key
            return hasKeyResult
        }

        override fun split(mapViewInterfaceId: Guid): Pair<WinRTMapViewReference?, WinRTMapViewReference?> {
            splitSlots += 9
            return splitResult
        }

        override fun asIterable(iterableInterfaceId: Guid): WinRTIterableReference =
            iterableResult ?: error("No iterable result configured for $iterableInterfaceId")

        override fun close() = Unit
    }

    private class FakeMapReference(
        scope: NativeScope,
        private val lookupResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val hasKeyResult: Boolean,
        private val insertResult: Boolean,
        private val mapViewResult: WinRTMapViewReference,
    ) : WinRTMapReference(PlatformAbi.allocateBytes(scope, 8), Guid("00000000-0000-0000-0000-000000000207")) {
        val lookupSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val hasKeySlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintSlots = mutableListOf<Int>()
        val objectSlots = mutableListOf<Int>()
        val insertSlots = mutableListOf<Pair<Int, Pair<ComObjectReference, ComObjectReference>>>()
        val removeSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val unitSlots = mutableListOf<Int>()

        override fun lookupOrNull(key: ComObjectReference): IUnknownReference? {
            lookupSlots += 6 to key
            return lookupResult
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun hasKey(key: ComObjectReference): Boolean {
            hasKeySlots += 8 to key
            return hasKeyResult
        }

        override fun getView(mapViewInterfaceId: Guid): WinRTMapViewReference {
            objectSlots += 9
            return mapViewResult
        }

        override fun insert(key: ComObjectReference, value: ComObjectReference): Boolean {
            insertSlots += 10 to (key to value)
            return insertResult
        }

        override fun remove(key: ComObjectReference) {
            removeSlots += 11 to key
        }

        override fun clear() {
            unitSlots += 12
        }

        override fun close() = Unit
    }
}
