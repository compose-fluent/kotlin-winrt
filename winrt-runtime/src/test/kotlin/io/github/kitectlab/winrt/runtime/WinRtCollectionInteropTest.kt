package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment

class WinRtCollectionInteropTest {
    @Test
    fun iterable_and_iterator_wrappers_use_expected_slots() {
        Arena.ofConfined().use { arena ->
            val current = FakeReference(arena)
            val iterator = FakeIteratorReference(
                arena = arena,
                currentResult = current,
                hasCurrentResult = false,
                moveNextResult = false,
            )
            val iterable = FakeIterableReference(
                arena = arena,
                firstResult = iterator,
            )

            val first = iterable.first(Guid("00000000-0000-0000-0000-000000000101"))
            assertFalse(first.hasCurrent())
            assertFalse(first.moveNext())
            assertSame(current.pointer, first.current().pointer)
            assertEquals(listOf(6), iterable.objectSlots)
            assertEquals(listOf(7, 8, 6), iterator.booleanAndObjectSlots)
        }
    }

    @Test
    fun vector_view_wrapper_reads_size_and_object_slots() {
        Arena.ofConfined().use { arena ->
            val element = FakeReference(arena)
            val vectorView = FakeVectorViewReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 3u,
            )

            assertEquals(3u, vectorView.size())
            assertSame(element.pointer, vectorView.getAt(1u).pointer)
            assertEquals(listOf(7), vectorView.uintSlots)
            assertEquals(listOf(6 to 1u), vectorView.uintArgSlots)
        }
    }

    @Test
    fun map_and_map_view_wrappers_use_expected_slots() {
        Arena.ofConfined().use { arena ->
            val key = FakeReference(arena)
            val value = FakeReference(arena)
            val lookedUp = FakeReference(arena)
            val mapView = FakeMapViewReference(
                arena = arena,
                lookupResult = lookedUp,
                sizeResult = 5u,
                hasKeyResult = true,
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
            assertTrue(map.insert(key, value))
            map.remove(key)
            map.clear()
            assertSame(mapView.pointer, map.getView(Guid("00000000-0000-0000-0000-000000000301")).pointer)

            assertEquals(listOf(6 to key), map.lookupSlots)
            assertEquals(listOf(8 to key), map.hasKeySlots)
            assertEquals(listOf(10 to (key to value)), map.insertSlots)
            assertEquals(listOf(11 to key), map.removeSlots)
            assertEquals(listOf(12), map.clearSlots)
            assertEquals(listOf(9), map.getViewSlots)
            assertEquals(listOf(7), map.uintSlots)

            assertSame(lookedUp.pointer, mapView.lookup(key).pointer)
            assertEquals(5u, mapView.size())
            assertTrue(mapView.hasKey(key))
            assertEquals(listOf(6 to key), mapView.lookupSlots)
            assertEquals(listOf(8 to key), mapView.hasKeySlots)
            assertEquals(listOf(7), mapView.uintSlots)
        }
    }

    private open class FakeReference(arena: Arena) : IUnknownReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000201")) {
        override fun close() = Unit
    }

    private class FakeIterableReference(
        arena: Arena,
        private val firstResult: WinRtIteratorReference,
    ) : WinRtIterableReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000202")) {
        val objectSlots = mutableListOf<Int>()

        override fun invokeObjectMethod(slot: Int): IUnknownReference {
            objectSlots += slot
            return firstResult
        }

        override fun createIteratorReference(pointer: MemorySegment, interfaceId: Guid): WinRtIteratorReference = firstResult

        override fun close() = Unit
    }

    private class FakeIteratorReference(
        arena: Arena,
        private val currentResult: IUnknownReference,
        private val hasCurrentResult: Boolean,
        private val moveNextResult: Boolean,
    ) : WinRtIteratorReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000203")) {
        val booleanAndObjectSlots = mutableListOf<Int>()

        override fun invokeObjectMethod(slot: Int): IUnknownReference {
            booleanAndObjectSlots += slot
            return currentResult
        }

        override fun invokeBooleanMethod(slot: Int): Boolean {
            booleanAndObjectSlots += slot
            return when (slot) {
                7 -> hasCurrentResult
                8 -> moveNextResult
                else -> error("Unexpected boolean slot: $slot")
            }
        }

        override fun createUnknownReference(pointer: MemorySegment, interfaceId: Guid): IUnknownReference = currentResult

        override fun close() = Unit
    }

    private class FakeVectorViewReference(
        arena: Arena,
        private val getAtResult: IUnknownReference,
        private val sizeResult: UInt,
    ) : WinRtVectorViewReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000204")) {
        val uintSlots = mutableListOf<Int>()
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()

        override fun invokeObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference {
            uintArgSlots += slot to value
            return getAtResult
        }

        override fun invokeUInt32Method(slot: Int): UInt {
            uintSlots += slot
            return sizeResult
        }

        override fun createUnknownReference(pointer: MemorySegment, interfaceId: Guid): IUnknownReference = getAtResult

        override fun close() = Unit
    }

    private open class FakeMapViewReference(
        arena: Arena,
        private val lookupResult: IUnknownReference,
        private val sizeResult: UInt,
        private val hasKeyResult: Boolean,
    ) : WinRtMapViewReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000205")) {
        val lookupSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val hasKeySlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintSlots = mutableListOf<Int>()

        override fun invokeObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference {
            lookupSlots += slot to value
            return lookupResult
        }

        override fun invokeBooleanMethodWithObjectArg(slot: Int, value: ComObjectReference): Boolean {
            hasKeySlots += slot to value
            return hasKeyResult
        }

        override fun invokeUInt32Method(slot: Int): UInt {
            uintSlots += slot
            return sizeResult
        }

        override fun createUnknownReference(pointer: MemorySegment, interfaceId: Guid): IUnknownReference = lookupResult

        override fun close() = Unit
    }

    private class FakeMapReference(
        arena: Arena,
        lookupResult: IUnknownReference,
        sizeResult: UInt,
        hasKeyResult: Boolean,
        private val insertResult: Boolean,
        private val mapViewResult: WinRtMapViewReference,
    ) : WinRtMapReference(arena.allocate(8), Guid("00000000-0000-0000-0000-000000000206")) {
        val lookupSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val hasKeySlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val uintSlots = mutableListOf<Int>()
        val insertSlots = mutableListOf<Pair<Int, Pair<ComObjectReference, ComObjectReference>>>()
        val removeSlots = mutableListOf<Pair<Int, ComObjectReference>>()
        val clearSlots = mutableListOf<Int>()
        val getViewSlots = mutableListOf<Int>()

        private val delegate = FakeMapViewReference(arena, lookupResult, sizeResult, hasKeyResult)
        private val lookedUpReference = lookupResult

        override fun invokeObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference {
            lookupSlots += slot to value
            return delegate.invokeObjectMethodWithObjectArg(slot, value)
        }

        override fun invokeBooleanMethodWithObjectArg(slot: Int, value: ComObjectReference): Boolean {
            hasKeySlots += slot to value
            return delegate.invokeBooleanMethodWithObjectArg(slot, value)
        }

        override fun invokeUInt32Method(slot: Int): UInt {
            uintSlots += slot
            return delegate.invokeUInt32Method(slot)
        }

        override fun invokeBooleanMethodWithTwoObjectArgs(
            slot: Int,
            first: ComObjectReference,
            second: ComObjectReference,
        ): Boolean {
            insertSlots += slot to (first to second)
            return insertResult
        }

        override fun invokeUnitMethodWithObjectArg(slot: Int, value: ComObjectReference) {
            removeSlots += slot to value
        }

        override fun invokeUnitMethod(slot: Int) {
            clearSlots += slot
        }

        override fun invokeObjectMethod(slot: Int): IUnknownReference {
            getViewSlots += slot
            return mapViewResult
        }

        override fun createUnknownReference(pointer: MemorySegment, interfaceId: Guid): IUnknownReference =
            lookedUpReference

        override fun createMapViewReference(pointer: MemorySegment, interfaceId: Guid): WinRtMapViewReference = mapViewResult

        override fun close() = Unit
    }
}
