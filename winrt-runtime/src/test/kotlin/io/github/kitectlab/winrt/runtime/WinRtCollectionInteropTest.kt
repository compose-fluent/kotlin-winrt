package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtCollectionInteropTest {
    @Test
    fun iterable_and_iterator_wrappers_use_expected_slots() {
        Arena.ofConfined().use { arena ->
            val firstValue = FakeReference(arena, "first")
            val secondValue = FakeReference(arena, "second")
            val iterator = FakeIteratorReference(
                arena = arena,
                currentResults = listOf(firstValue, secondValue),
                getManyResults = listOf(firstValue, null, secondValue),
            )
            val iterable = FakeIterableReference(arena, iterator)

            val first = iterable.first(Guid("00000000-0000-0000-0000-000000000101"))
            assertTrue(first.hasCurrent())
            assertSame(firstValue.pointer, first.current().pointer)
            assertTrue(first.moveNext())
            assertSame(secondValue.pointer, first.current().pointer)
            assertFalse(first.moveNext())
            assertEquals(listOf(firstValue.pointer, MemorySegment.NULL, secondValue.pointer), first.getMany(3).map { it?.pointer ?: MemorySegment.NULL })
            assertEquals(listOf(6), iterable.objectSlots)
            assertEquals(listOf(7, 6, 8, 6, 8), iterator.slotCalls)
            assertEquals(listOf(9 to 3), iterator.getManySlots)
        }
    }

    @Test
    fun vector_view_wrapper_reads_size_indexof_and_getmany_slots() {
        Arena.ofConfined().use { arena ->
            val element = FakeReference(arena, "element")
            val key = FakeReference(arena, "key")
            val vectorView = FakeVectorViewReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 3u,
                indexOfResult = true to 2u,
                getManyResults = listOf(element, null),
            )

            assertEquals(3u, vectorView.size())
            assertSame(element.pointer, vectorView.getAt(1u).pointer)
            assertEquals(true to 2u, vectorView.indexOf(key))
            assertEquals(listOf(element.pointer, MemorySegment.NULL), vectorView.getMany(1u, 2).map { it?.pointer ?: MemorySegment.NULL })
            assertEquals(listOf(7), vectorView.uintSlots)
            assertEquals(listOf(6 to 1u), vectorView.uintArgSlots)
            assertEquals(listOf(8 to key), vectorView.indexOfSlots)
            assertEquals(listOf(9 to (1u to 2)), vectorView.getManySlots)
        }
    }

    @Test
    fun vector_wrapper_uses_expected_slots_for_mutation_and_views() {
        Arena.ofConfined().use { arena ->
            val element = FakeReference(arena, "element")
            val replacement = FakeReference(arena, "replacement")
            val vectorView = FakeVectorViewReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
                getManyResults = listOf(element),
            )
            val vector = FakeVectorReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
                getManyResults = listOf(element, replacement),
                vectorViewResult = vectorView,
                getAtResultsByIndex = mapOf(0u to element, 1u to replacement),
            )

            assertSame(element.pointer, vector.getAt(0u).pointer)
            assertEquals(5u, vector.size())
            assertSame(vectorView.pointer, vector.getView(Guid("00000000-0000-0000-0000-000000000301")).pointer)
            assertEquals(true to 3u, vector.indexOf(element))
            vector.setAt(1u, replacement)
            vector.insertAt(2u, replacement)
            vector.removeAt(4u)
            vector.append(replacement)
            vector.removeAtEnd()
            vector.clear()
            vector.replaceAll(listOf(element, replacement))
            assertEquals(listOf(element.pointer, replacement.pointer), vector.getMany(1u, 2).map { it?.pointer ?: MemorySegment.NULL })

            assertEquals(listOf(7), vector.uintSlots)
            assertEquals(listOf(6 to 0u), vector.uintArgSlots)
            assertEquals(listOf(8), vector.objectSlots)
            assertEquals(listOf(9 to element), vector.indexOfSlots)
            assertEquals(listOf(10 to (1u to replacement), 11 to (2u to replacement)), vector.uintObjectSlots)
            assertEquals(listOf(12 to 4u), vector.removeAtSlots)
            assertEquals(listOf(13 to replacement), vector.appendSlots)
            assertEquals(listOf(14, 15), vector.unitSlots)
            assertEquals(listOf(listOf(element, replacement)), vector.replaceAllSlots)
            assertEquals(listOf(16 to (1u to 2)), vector.getManySlots)
        }
    }

    @Test
    fun map_and_map_view_wrappers_use_expected_slots() {
        Arena.ofConfined().use { arena ->
            val key = FakeReference(arena, "key")
            val value = FakeReference(arena, "value")
            val lookedUp = FakeReference(arena, "lookup")
            val firstPart = FakeMapViewReference(
                arena = arena,
                lookupResult = lookedUp,
                sizeResult = 2u,
                hasKeyResult = true,
                splitResult = null to null,
            )
            val secondPart = FakeMapViewReference(
                arena = arena,
                lookupResult = value,
                sizeResult = 1u,
                hasKeyResult = false,
                splitResult = null to null,
            )
            val mapView = FakeMapViewReference(
                arena = arena,
                lookupResult = lookedUp,
                sizeResult = 5u,
                hasKeyResult = true,
                splitResult = firstPart to secondPart,
            )
            val map = FakeMapReference(
                arena = arena,
                lookupResult = lookedUp,
                sizeResult = 5u,
                hasKeyResult = true,
                insertResult = true,
                mapViewResult = mapView,
            )

            assertSame(lookedUp.pointer, map.lookup(key).pointer)
            assertEquals(5u, map.size())
            assertTrue(map.hasKey(key))
            assertSame(mapView.pointer, map.getView(Guid("00000000-0000-0000-0000-000000000401")).pointer)
            assertTrue(map.insert(key, value))
            map.remove(key)
            map.clear()

            assertSame(lookedUp.pointer, mapView.lookup(key).pointer)
            assertEquals(5u, mapView.size())
            assertTrue(mapView.hasKey(key))

            val split = mapView.split(Guid("00000000-0000-0000-0000-000000000402"))
            assertSame(firstPart.pointer, split.first?.pointer)
            assertSame(secondPart.pointer, split.second?.pointer)

            assertEquals(listOf(6 to key), map.lookupSlots)
            assertEquals(listOf(8 to key), map.hasKeySlots)
            assertEquals(listOf(9), map.objectSlots)
            assertEquals(listOf(10 to (key to value)), map.insertSlots)
            assertEquals(listOf(11 to key), map.removeSlots)
            assertEquals(listOf(12), map.unitSlots)
            assertEquals(listOf(7), map.uintSlots)

            assertEquals(listOf(6 to key), mapView.lookupSlots)
            assertEquals(listOf(8 to key), mapView.hasKeySlots)
            assertEquals(listOf(9), mapView.splitSlots)
            assertEquals(listOf(7), mapView.uintSlots)
        }
    }

    @Test
    fun collection_adapters_project_vector_and_map_surfaces() {
        Arena.ofConfined().use { arena ->
            val one = FakeReference(arena, "one")
            val two = FakeReference(arena, "two")
            val three = FakeReference(arena, "three")
            val vector = FakeVectorReference(
                arena = arena,
                getAtResult = one,
                sizeResult = 2u,
                indexOfResult = true to 1u,
                getManyResults = listOf(one, two),
                vectorViewResult = FakeVectorViewReference(
                    arena = arena,
                    getAtResult = one,
                    sizeResult = 2u,
                    indexOfResult = true to 1u,
                    getManyResults = listOf(one, two),
                ),
                getAtResultsByIndex = mapOf(0u to one, 1u to two, 2u to three),
            )

            WinRtVectorListAdapter(
                vector = vector,
                elementProjector = { (it as FakeReference?)?.label ?: "<null>" },
                elementMarshaller = { label -> FakeReference(arena, label) },
            ).use { list ->
                assertEquals(listOf("one", "two"), list.toList())
                list.add("three")
                assertEquals(listOf(13 to "three"), vector.appendSlots.map { it.first to (it.second as FakeReference).label })
                assertEquals("one", list.removeAt(0))
                assertEquals(listOf(12 to 0u), vector.removeAtSlots)
            }

            val pairOne = FakeKeyValuePairReference(arena, one, two)
            val pairTwo = FakeKeyValuePairReference(arena, two, three)
            val iterable = FakeIterableReference(
                arena = arena,
                firstResult = FakeIteratorReference(
                    arena = arena,
                    currentResults = listOf(pairOne, pairTwo),
                    getManyResults = emptyList(),
                ),
                iteratorFactory = {
                    FakeIteratorReference(
                        arena = arena,
                        currentResults = listOf(pairOne, pairTwo),
                        getManyResults = emptyList(),
                    )
                },
            )
            val mapView = FakeMapViewReference(
                arena = arena,
                lookupResult = two,
                sizeResult = 2u,
                hasKeyResult = true,
                splitResult = null to null,
                iterableResult = iterable,
            )

            WinRtMapViewAdapter(
                mapView = mapView,
                iterableInterfaceId = Guid("00000000-0000-0000-0000-000000000777"),
                iteratorInterfaceId = Guid("00000000-0000-0000-0000-000000000778"),
                keyValuePairInterfaceId = Guid("00000000-0000-0000-0000-000000000779"),
                keyProjector = { (it as FakeReference?)?.label ?: "<null>" },
                valueProjector = { (it as FakeReference?)?.label ?: "<null>" },
                keyMarshaller = { label -> FakeReference(arena, label) },
            ).use { projected ->
                assertEquals(2, projected.size)
                assertEquals("two", projected["one"])
                assertTrue(projected.containsKey("two"))
                assertEquals(mapOf("one" to "two", "two" to "three"), projected.toMap())
            }
        }
    }

    private open class FakeReference(
        arena: Arena,
        val label: String,
    ) : IUnknownReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000201")) {
        override fun close() = Unit
    }

    private class FakeKeyValuePairReference(
        arena: Arena,
        private val keyResult: IUnknownReference?,
        private val valueResult: IUnknownReference?,
    ) : WinRtKeyValuePairReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000210")) {
        override fun key(): IUnknownReference? = keyResult

        override fun value(): IUnknownReference? = valueResult

        override fun close() = Unit
    }

    private class FakeIterableReference(
        arena: Arena,
        private val firstResult: WinRtIteratorReference,
        private val iteratorFactory: (() -> WinRtIteratorReference)? = null,
    ) : WinRtIterableReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000202")) {
        val objectSlots = mutableListOf<Int>()

        override fun first(iteratorInterfaceId: Guid): WinRtIteratorReference {
            objectSlots += 6
            return iteratorFactory?.invoke() ?: firstResult
        }

        override fun close() = Unit
    }

    private class FakeIteratorReference(
        arena: Arena,
        private val currentResults: List<IUnknownReference?>,
        private val getManyResults: List<IUnknownReference?>,
    ) : WinRtIteratorReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000203")) {
        private var currentIndex = 0
        val slotCalls = mutableListOf<Int>()
        val getManySlots = mutableListOf<Pair<Int, Int>>()

        override fun currentOrNull(): IUnknownReference? {
            slotCalls += 6
            return currentResults.getOrNull(currentIndex)
        }

        override fun hasCurrent(): Boolean {
            slotCalls += 7
            return currentIndex < currentResults.size
        }

        override fun moveNext(): Boolean {
            slotCalls += 8
            currentIndex += 1
            return currentIndex < currentResults.size
        }

        override fun invokeGetMany(slot: Int, startIndex: UInt?, capacity: Int): List<IUnknownReference?> {
            getManySlots += slot to capacity
            return getManyResults.take(capacity)
        }

        override fun close() = Unit
    }

    private class FakeVectorViewReference(
        arena: Arena,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
        private val getManyResults: List<IUnknownReference?>,
    ) : WinRtVectorViewReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000204")) {
        val uintSlots = mutableListOf<Int>()
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val indexOfSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val getManySlots = mutableListOf<Pair<Int, Pair<UInt, Int>>>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResult
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
        arena: Arena,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
        private val getManyResults: List<IUnknownReference?>,
        private val vectorViewResult: WinRtVectorViewReference,
        private val getAtResultsByIndex: Map<UInt, IUnknownReference?> = emptyMap(),
    ) : WinRtVectorReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000205")) {
        val uintSlots = mutableListOf<Int>()
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val objectSlots = mutableListOf<Int>()
        val indexOfSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintObjectSlots = mutableListOf<Pair<Int, Pair<UInt, ComObjectReference>>>()
        val removeAtSlots = mutableListOf<Pair<Int, UInt>>()
        val appendSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val unitSlots = mutableListOf<Int>()
        val getManySlots = mutableListOf<Pair<Int, Pair<UInt, Int>>>()
        val replaceAllSlots = mutableListOf<List<ComObjectReference>>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResultsByIndex[index] ?: getAtResult
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun getView(vectorViewInterfaceId: Guid): WinRtVectorViewReference {
            objectSlots += 8
            return vectorViewResult
        }

        override fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> {
            indexOfSlots += 9 to value
            return indexOfResult
        }

        override fun setAt(index: UInt, value: ComObjectReference) {
            uintObjectSlots += 10 to (index to value)
        }

        override fun insertAt(index: UInt, value: ComObjectReference) {
            uintObjectSlots += 11 to (index to value)
        }

        override fun removeAt(index: UInt) {
            removeAtSlots += 12 to index
        }

        override fun append(value: ComObjectReference) {
            appendSlots += 13 to value
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
        arena: Arena,
        private val lookupResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val hasKeyResult: Boolean,
        private val splitResult: Pair<WinRtMapViewReference?, WinRtMapViewReference?>,
        private val iterableResult: WinRtIterableReference? = null,
    ) : WinRtMapViewReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000206")) {
        val lookupSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val hasKeySlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintSlots = mutableListOf<Int>()
        val splitSlots = mutableListOf<Int>()

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

        override fun split(mapViewInterfaceId: Guid): Pair<WinRtMapViewReference?, WinRtMapViewReference?> {
            splitSlots += 9
            return splitResult
        }

        override fun asIterable(iterableInterfaceId: Guid): WinRtIterableReference =
            iterableResult ?: error("No iterable result configured for $iterableInterfaceId")

        override fun close() = Unit
    }

    private class FakeMapReference(
        arena: Arena,
        private val lookupResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val hasKeyResult: Boolean,
        private val insertResult: Boolean,
        private val mapViewResult: WinRtMapViewReference,
    ) : WinRtMapReference(arena.allocate(8).asNativePointer(), Guid("00000000-0000-0000-0000-000000000207")) {
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

        override fun getView(mapViewInterfaceId: Guid): WinRtMapViewReference {
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
