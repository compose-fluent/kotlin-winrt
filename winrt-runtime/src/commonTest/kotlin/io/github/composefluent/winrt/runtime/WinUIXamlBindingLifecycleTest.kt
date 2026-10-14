package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** CSharpPagePass2 Connect/Initialize/ProcessBindings/Recycle/ReleaseAllListeners;
 * delegate/token ownership remains in CsWinRT's EventSource and the runtime event bridge.
 */
class WinUIXamlBindingLifecycleTest {
    @Test
    fun initialization_is_per_instance_and_reloading_installs_one_subscription() {
        val first = WinUIXamlBindingState()
        val second = WinUIXamlBindingState()
        var listeners = 0
        val firstUpdates = mutableListOf<Boolean>()
        val secondUpdates = mutableListOf<Boolean>()
        fun update(state: WinUIXamlBindingState, calls: MutableList<Boolean>, initial: Boolean) {
            calls += initial
            listeners++
            state.trackEvent(Unit, Unit) { _, _ -> listeners-- }
        }
        val updateFirst: (Boolean) -> Unit = { update(first, firstUpdates, it) }
        val updateSecond: (Boolean) -> Unit = { update(second, secondUpdates, it) }
        first.initialize(updateFirst)
        first.initialize(updateFirst)
        second.initialize(updateSecond)
        assertEquals(2, listeners)
        first.update(updateFirst)
        assertEquals(listOf(true, false), firstUpdates)
        assertEquals(listOf(true), secondUpdates)
        first.stopTracking()
        first.update(updateFirst)
        assertEquals(1, listeners)
        first.initialize(updateFirst)
        first.updateAll(updateFirst)
        assertEquals(listOf(true, false, true, true), firstUpdates)
        assertEquals(2, listeners)
        first.stopTracking()
        second.stopTracking()
        assertEquals(0, listeners)
    }

    @Test
    fun deferred_values_are_latest_per_member_and_never_write_back_during_connection() {
        val state = WinUIXamlBindingState()
        state.initialize { }
        var text = ""
        var writes = 0
        var updates = 0
        state.defer(1, "Text") { text = "old" }
        state.defer(1, "Text") {
            text = "latest"
            state.changeTarget({ writes++ }, { updates++ })
        }
        state.defer(2, "Text") { text = "other" }
        assertTrue(state.connected(1))
        assertEquals("latest", text)
        assertEquals(0, writes)
        assertEquals(0, updates)
        assertFalse(state.connected(1))
        state.changeTarget({ writes++ }, { updates++ })
        assertEquals(1, writes)
        assertEquals(1, updates)
        assertTrue(state.connected(2))
        assertEquals("other", text)
        state.stopTracking()
    }

    @Test
    fun a_failed_deferred_setter_clears_all_work_and_drains_failing_removals() {
        val state = WinUIXamlBindingState()
        val failure = IllegalArgumentException("setter")
        val cleanup = IllegalStateException("remove")
        val removed = mutableListOf<Int>()
        state.initialize {
            for (id in 1..3) state.trackEvent(Unit, id) { _, token ->
                removed += token
                if (token == 3) throw failure
                if (token == 2) throw cleanup
            }
        }
        state.defer(1, "Text") { throw failure }
        state.defer(2, "Text") { error("failed work must be discarded") }
        assertSame(failure, assertFailsWith<IllegalArgumentException> { state.connected(1) })
        assertEquals(listOf(3, 2, 1), removed)
        assertSame(cleanup, failure.suppressedExceptions.single())
        assertFalse(state.connected(2))
        state.update { error("failed bindings must be inactive") }
        state.stopTracking()
        assertEquals(listOf(3, 2, 1), removed)
    }

    @Test
    fun a_failed_two_way_setter_stops_tracking_before_it_propagates() {
        val state = WinUIXamlBindingState()
        val failure = IllegalArgumentException("ConvertBack")
        var removed = 0
        state.initialize { state.trackProperty(Unit, Unit, 7L) { _, _, token ->
            assertEquals(7L, token)
            removed++
        } }
        state.defer(1, "Text") { error("failed work must be discarded") }
        assertSame(failure, assertFailsWith<IllegalArgumentException> {
            state.changeTarget({ throw failure }, { error("must not refresh after failure") })
        })
        assertEquals(1, removed)
        assertFalse(state.connected(1))
        state.changeTarget({ error("must remain inactive") }, { error("must remain inactive") })
        state.initialize { }
        var writes = 0
        state.changeTarget({ writes++ }, { })
        assertEquals(1, writes)
        state.stopTracking()
    }

    @Test
    fun template_phases_keep_earlier_listeners_and_recycling_releases_the_old_item() {
        val active = mutableMapOf<WinUIXamlBindingScope, MutableSet<Int>>()
        val calls = mutableListOf<Pair<Any, Boolean>>()
        val owner = object : WinUIXamlBindingScopeOwner {
            override fun _kotlinXamlUpdateScope(scope: WinUIXamlBindingScope, initial: Boolean) {
                calls += requireNotNull(scope.dataRoot) to initial
                for (phase in listOf(0, 2)) if (scope.isPhaseActive(phase)) {
                    scope.state.trackPhase(phase)
                    check(active.getOrPut(scope) { mutableSetOf() }.add(phase))
                    scope.state.trackEvent(scope, phase) { source, token -> active.getValue(source).remove(token) }
                }
            }
            override fun _kotlinXamlConnectScope(scope: WinUIXamlBindingScope, connectionId: Int, target: Any?) { }
            override fun _kotlinXamlWriteBackScope(scope: WinUIXamlBindingScope, bindingId: Int) { }
            override fun _kotlinXamlCreateScopeConnector(connectionId: Int, target: Any?): Any? = null
        }
        val first = WinUIXamlBindingScope(owner, 1)
        val second = WinUIXamlBindingScope(owner, 1)
        for (scope in listOf(first, second)) { scope.registerPhase(1, 0); scope.registerPhase(2, 2) }
        val item = Any()
        val other = Any()
        first.setDataRoot(item)
        assertSame(item, first.dataRoot)
        assertTrue(calls.isEmpty())
        first.changed(null, null)
        assertTrue(calls.isEmpty())
        assertEquals(2, first.processBindings(item, 0))
        assertEquals(-1, first.processBindings(item, 2))
        assertEquals(setOf(0, 2), active.getValue(first).toSet())
        assertEquals(2, second.processBindings(other, 0))
        first.changed(null, null)
        assertEquals(setOf(0, 2), active.getValue(first).toSet())
        assertEquals(setOf(0), active.getValue(second).toSet())
        first.recycle()
        assertNull(first.dataRoot)
        assertTrue(active.getValue(first).isEmpty())
        first.changed(null, null)
        val before = calls.size
        assertEquals(2, first.processBindings(other, 0))
        assertEquals(before + 1, calls.size)
        assertSame(other, first.dataRoot)
        assertEquals(setOf(0), active.getValue(first).toSet())
        // Repeated phase zero refreshes OneTime values even for the same item.
        first.processBindings(other, 0)
        assertTrue(calls.last().second)
        first.recycle()
        second.recycle()
        assertTrue(active.values.all { it.isEmpty() })
    }
}
