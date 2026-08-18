package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManagedComHostStateTest {
    @Test
    fun `queryInterface success increments ref count`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        val result = state.queryInterface(IID.IInspectable) { requested ->
            if (requested == IID.IInspectable) "inspectable" else null
        }

        assertEquals(KnownHResults.S_OK, result.hResult)
        assertEquals("inspectable", result.target)
        assertEquals(1, state.releaseReference())
        assertEquals(0, state.releaseReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `queryInterface failure does not increment ref count`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        val result = state.queryInterface(IID.IActivationFactory) { null }

        assertEquals(KnownHResults.E_NOINTERFACE, result.hResult)
        assertNull(result.target)
        assertEquals(0, state.releaseReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `cleanup only runs once`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        assertEquals(2, state.addReference())
        assertEquals(1, state.releaseReference())
        assertEquals(0, state.releaseReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `try add reference does not resurrect a cleaned host`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        assertEquals(0, state.releaseReference())
        assertNull(state.tryAddReference())
        assertEquals(0, state.addReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `root is pinned and unpinned only across the one-reference boundary`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root)

        assertEquals(2, state.addReference())
        assertEquals(1, root.pinCalls)
        assertEquals(3, state.addReference())
        assertEquals(1, root.pinCalls)
        assertEquals(2, state.releaseReference())
        assertEquals(0, root.unpinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(0, state.releaseReference())
    }

    @Test
    fun `failed root pin restores the live baseline reference`() {
        val root = RecordingRootReference(canPin = false)
        val state = ManagedComHostState(cleanup = {}, rootReference = root)

        assertEquals(0, state.addReference())
        assertEquals(1, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)

        root.canPin = true
        assertEquals(2, state.addReference())
        assertEquals(2, root.pinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(0, state.releaseReference())
    }

    @Test
    fun `queryInterface does not resurrect a cleaned host`() {
        val state = ManagedComHostState {}

        assertEquals(0, state.releaseReference())
        val result = state.queryInterface(IID.IInspectable) { "stale" }

        assertEquals(KnownHResults.E_POINTER, result.hResult)
        assertNull(result.target)
    }

    @Test
    fun `tracker add does not resurrect a cleaned host`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        assertEquals(0, state.releaseReference())
        assertEquals(0, state.addTrackerReference())
        assertEquals(0, state.releaseTrackerReference())
        assertNull(state.tryAddReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `managed host reference probe pins only a live host`() {
        val host = WinRTInspectableComObject.inspectableBox("value", "test.ManagedHost")
        val reference = host.createPrimaryReference()
        val pointer = reference.pointer.asRawAddress()
        host.close()

        try {
            assertEquals(1u, WinRTInspectableComObject.tryProbeReferenceCount(pointer))
        } finally {
            reference.close()
        }

        assertNull(WinRTInspectableComObject.tryProbeReferenceCount(pointer))
    }

    @Test
    fun `reference tracker references keep host alive until tracker release`() {
        var cleaned = 0
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = { cleaned += 1 }, rootReference = root)

        assertEquals(1, state.addTrackerReference())
        assertEquals(1, root.pinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(0, cleaned)
        assertEquals(0, state.releaseTrackerReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(1, cleaned)
    }

    @Test
    fun `transient borrowed query does not pin the managed root`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()

        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.addBorrowedReference())
        assertEquals(1, state.releaseReference())
        state.endBorrowedCall(managedValue)

        assertEquals(1, state.currentReferenceCount())
        assertEquals(0, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(0, state.releaseBaselineReference())
    }

    @Test
    fun `escaped borrowed query pins until its final release`() {
        var cleaned = 0
        val root = RecordingRootReference()
        val state = ManagedComHostState(
            cleanup = { cleaned += 1 },
            rootReference = root,
            borrowReady = true,
        )
        val managedValue = Any()

        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.addBorrowedReference())
        state.endBorrowedCall(managedValue)

        assertEquals(2, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(0, state.releaseBaselineReference())
        assertEquals(1, cleaned)
    }

    @Test
    fun `overlapping balanced borrowed calls return to the unpinned baseline`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()

        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.addBorrowedReference())
        assertEquals(3, state.addBorrowedReference())
        assertEquals(2, state.releaseReference())
        assertEquals(1, state.releaseReference())
        state.endBorrowedCall(managedValue)
        state.endBorrowedCall(managedValue)

        assertEquals(1, state.currentReferenceCount())
        assertEquals(0, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(0, state.releaseBaselineReference())
    }

    @Test
    fun `an overlapping call is conservatively pinned when another call finishes first`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()

        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.addBorrowedReference())
        assertEquals(3, state.addBorrowedReference())
        assertEquals(2, state.releaseReference())
        state.endBorrowedCall(managedValue)

        assertEquals(2, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        state.endBorrowedCall(managedValue)
        assertEquals(1, state.currentReferenceCount())
        assertEquals(0, state.releaseBaselineReference())
    }

    @Test
    fun `borrowed call entered from pinned state keeps the existing root`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()

        assertEquals(2, state.addReference(managedValue))
        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.currentReferenceCount())
        state.endBorrowedCall(managedValue)

        assertEquals(2, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(0, state.releaseBaselineReference())
    }

    @Test
    fun `external release during a borrowed call returns to its borrow-ready baseline`() {
        val root = RecordingRootReference()
        val state = ManagedComHostState(cleanup = {}, rootReference = root, borrowReady = true)
        val managedValue = Any()

        assertEquals(2, state.addReference(managedValue))
        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(1, state.releaseReference())
        assertEquals(1, root.unpinCalls)

        state.endBorrowedCall(managedValue)

        assertEquals(1, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)
        assertEquals(1, root.unpinCalls)
        assertEquals(0, state.releaseBaselineReference())
    }

    @Test
    fun `baseline release during borrowed call preserves escaped ownership`() {
        var cleaned = 0
        val root = RecordingRootReference()
        val state = ManagedComHostState(
            cleanup = { cleaned += 1 },
            rootReference = root,
            borrowReady = true,
        )
        val managedValue = Any()

        assertTrue(state.tryBeginBorrowedCall(managedValue))
        assertEquals(2, state.addBorrowedReference())
        assertEquals(1, state.releaseBaselineReference())
        state.endBorrowedCall(managedValue)

        assertEquals(1, state.currentReferenceCount())
        assertEquals(1, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(0, state.releaseReference())
        assertEquals(1, root.unpinCalls)
        assertEquals(1, cleaned)
    }

    @Test
    fun `quiescent borrow-ready baseline release cleans up immediately`() {
        var cleaned = 0
        val root = RecordingRootReference()
        val state = ManagedComHostState(
            cleanup = { cleaned += 1 },
            rootReference = root,
            borrowReady = true,
        )

        assertEquals(0, state.releaseBaselineReference())

        assertEquals(0, state.currentReferenceCount())
        assertEquals(0, root.pinCalls)
        assertEquals(0, root.unpinCalls)
        assertEquals(1, cleaned)

        assertEquals(0, state.releaseBaselineReference())
        assertEquals(1, cleaned)
    }

    private class RecordingRootReference(
        var canPin: Boolean = true,
    ) : ManagedComRootReference {
        var pinCalls: Int = 0
            private set
        var unpinCalls: Int = 0
            private set

        override fun tryPin(knownManagedValue: Any?): Boolean {
            pinCalls += 1
            return canPin
        }

        override fun unpin() {
            unpinCalls += 1
        }
    }
}
