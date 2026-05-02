package io.github.composefluent.winrt.runtime

import java.lang.foreign.Arena
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WinRtBindableInteropTest {
    @Test
    fun bindable_iterable_and_iterator_wrappers_use_expected_slots() {
        Arena.ofConfined().use { arena ->
            val firstValue = FakeBindableReference(arena, "first")
            val secondValue = FakeBindableReference(arena, "second")
            val iterator = FakeBindableIteratorReference(arena, listOf(firstValue, secondValue))
            val iterable = FakeBindableIterableReference(arena, iterator)

            val first = iterable.first()
            assertTrue(first.hasCurrent())
            assertEquals(firstValue.pointer, first.current().pointer)
            assertTrue(first.moveNext())
            assertEquals(secondValue.pointer, first.current().pointer)
            assertFalse(first.moveNext())
            assertEquals(listOf(6), iterable.objectSlots)
            assertEquals(listOf(7, 6, 8, 6, 8), iterator.slotCalls)
        }
    }

    @Test
    fun bindable_vector_view_wrapper_reads_size_and_indexof_slots() {
        Arena.ofConfined().use { arena ->
            val element = FakeBindableReference(arena, "element")
            val key = FakeBindableReference(arena, "key")
            val vectorView = FakeBindableVectorViewReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 3u,
                indexOfResult = true to 2u,
            )

            assertEquals(3u, vectorView.size())
            assertEquals(element.pointer, vectorView.getAt(1u).pointer)
            assertEquals(true to 2u, vectorView.indexOf(key.pointer.asRawAddress()))
            assertEquals(listOf(7), vectorView.uintSlots)
            assertEquals(listOf(6 to 1u), vectorView.uintArgSlots)
            assertEquals(listOf(8 to key.pointer.asRawAddress()), vectorView.indexOfSlots)
        }
    }

    @Test
    fun bindable_vector_wrapper_uses_expected_slots_for_mutation_and_views() {
        Arena.ofConfined().use { arena ->
            val element = FakeBindableReference(arena, "element")
            val replacement = FakeBindableReference(arena, "replacement")
            val vectorView = FakeBindableVectorViewReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
            )
            val vector = FakeBindableVectorReference(
                arena = arena,
                getAtResult = element,
                sizeResult = 5u,
                indexOfResult = true to 3u,
                vectorViewResult = vectorView,
                getAtResultsByIndex = mapOf(0u to element, 1u to replacement),
            )

            assertEquals(element.pointer, vector.getAt(0u).pointer)
            assertEquals(5u, vector.size())
            assertEquals(vectorView.pointer, vector.getView().pointer)
            assertEquals(true to 3u, vector.indexOf(element.pointer.asRawAddress()))
            vector.setAt(1u, replacement.pointer.asRawAddress())
            vector.insertAt(2u, replacement.pointer.asRawAddress())
            vector.removeAt(4u)
            vector.append(replacement.pointer.asRawAddress())
            vector.removeAtEnd()
            vector.clear()

            assertEquals(listOf(7), vector.uintSlots)
            assertEquals(listOf(6 to 0u), vector.uintArgSlots)
            assertEquals(listOf(8), vector.objectSlots)
            assertEquals(listOf(9 to element.pointer.asRawAddress()), vector.indexOfSlots)
            assertEquals(
                listOf(
                    10 to (1u to replacement.pointer.asRawAddress()),
                    11 to (2u to replacement.pointer.asRawAddress()),
                ),
                vector.uintObjectSlots,
            )
            assertEquals(listOf(12 to 4u), vector.removeAtSlots)
            assertEquals(listOf(13 to replacement.pointer.asRawAddress()), vector.appendSlots)
            assertEquals(listOf(14, 15), vector.unitSlots)
        }
    }

    private open class FakeBindableReference(
        arena: Arena,
        val label: String,
    ) : IUnknownReference(arena.allocate(8).asNativePointer().asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true) {
        override fun close() = Unit
    }

    private class FakeBindableIterableReference(
        arena: Arena,
        private val firstResult: WinRtBindableIteratorReference,
    ) : WinRtBindableIterableReference(arena.allocate(8).asNativePointer(), WinRtBindableInterfaceIds.IBindableIterable, preventReleaseOnDispose = true) {
        val objectSlots = mutableListOf<Int>()

        override fun first(): WinRtBindableIteratorReference {
            objectSlots += 6
            return firstResult
        }

        override fun close() = Unit
    }

    private class FakeBindableIteratorReference(
        arena: Arena,
        private val currentResults: List<IUnknownReference?>,
    ) : WinRtBindableIteratorReference(arena.allocate(8).asNativePointer(), WinRtBindableInterfaceIds.IBindableIterator, preventReleaseOnDispose = true) {
        private var currentIndex = 0
        val slotCalls = mutableListOf<Int>()

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

        override fun close() = Unit
    }

    private class FakeBindableVectorViewReference(
        arena: Arena,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
    ) : WinRtBindableVectorViewReference(arena.allocate(8).asNativePointer(), WinRtBindableInterfaceIds.IBindableVectorView, preventReleaseOnDispose = true) {
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val uintSlots = mutableListOf<Int>()
        val indexOfSlots = mutableListOf<Pair<Int, NativePointer>>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResult
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun indexOf(valuePointer: NativePointer): Pair<Boolean, UInt> {
            indexOfSlots += 8 to valuePointer
            return indexOfResult
        }

        override fun close() = Unit
    }

    private class FakeBindableVectorReference(
        arena: Arena,
        private val getAtResult: IUnknownReference?,
        private val sizeResult: UInt,
        private val indexOfResult: Pair<Boolean, UInt>,
        private val vectorViewResult: WinRtBindableVectorViewReference,
        private val getAtResultsByIndex: Map<UInt, IUnknownReference?> = emptyMap(),
    ) : WinRtBindableVectorReference(arena.allocate(8).asNativePointer(), WinRtBindableInterfaceIds.IBindableVector, preventReleaseOnDispose = true) {
        val uintArgSlots = mutableListOf<Pair<Int, UInt>>()
        val uintSlots = mutableListOf<Int>()
        val objectSlots = mutableListOf<Int>()
        val indexOfSlots = mutableListOf<Pair<Int, NativePointer>>()
        val uintObjectSlots = mutableListOf<Pair<Int, Pair<UInt, NativePointer>>>()
        val removeAtSlots = mutableListOf<Pair<Int, UInt>>()
        val appendSlots = mutableListOf<Pair<Int, NativePointer>>()
        val unitSlots = mutableListOf<Int>()

        override fun getAtOrNull(index: UInt): IUnknownReference? {
            uintArgSlots += 6 to index
            return getAtResultsByIndex[index] ?: getAtResult
        }

        override fun size(): UInt {
            uintSlots += 7
            return sizeResult
        }

        override fun getView(): WinRtBindableVectorViewReference {
            objectSlots += 8
            return vectorViewResult
        }

        override fun indexOf(valuePointer: NativePointer): Pair<Boolean, UInt> {
            indexOfSlots += 9 to valuePointer
            return indexOfResult
        }

        override fun setAt(index: UInt, valuePointer: NativePointer) {
            uintObjectSlots += 10 to (index to valuePointer)
        }

        override fun insertAt(index: UInt, valuePointer: NativePointer) {
            uintObjectSlots += 11 to (index to valuePointer)
        }

        override fun removeAt(index: UInt) {
            removeAtSlots += 12 to index
        }

        override fun append(valuePointer: NativePointer) {
            appendSlots += 13 to valuePointer
        }

        override fun removeAtEnd() {
            unitSlots += 14
        }

        override fun clear() {
            unitSlots += 15
        }

        override fun close() = Unit
    }
}
