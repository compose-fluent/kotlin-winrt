package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `reference tracker references keep host alive until tracker release`() {
        var cleaned = 0
        val state = ManagedComHostState { cleaned += 1 }

        assertEquals(1, state.addTrackerReference())
        assertEquals(1, state.releaseReference())
        assertEquals(0, cleaned)
        assertEquals(0, state.releaseTrackerReference())
        assertEquals(1, cleaned)
    }
}
