package io.github.kitectlab.winrt.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
}
