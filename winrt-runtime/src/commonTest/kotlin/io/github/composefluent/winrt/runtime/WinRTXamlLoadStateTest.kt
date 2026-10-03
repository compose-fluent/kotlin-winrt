package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinRTXamlLoadStateTest {
    @Test
    fun reentry_and_repeat_do_not_duplicate_connections_but_instances_are_independent() {
        // XamlCompiler CSharpPagePass1.tt sets _contentLoaded before LoadComponent.
        val first = WinRTXamlLoadState()
        val second = WinRTXamlLoadState()
        var loads = 0
        first.load { loads++; first.load { error("reentrant load") } }
        first.load { error("duplicate load") }
        second.load { loads++ }
        assertEquals(2, loads)
    }

    @Test
    fun failure_preserves_original_cause_and_does_not_retry_partial_subscriptions() {
        val state = WinRTXamlLoadState()
        val cause = IllegalArgumentException("connection failed")
        assertSame(cause, assertFailsWith<IllegalArgumentException> { state.load { throw cause } })
        assertSame(cause, assertFailsWith<IllegalStateException> { state.load { error("must not retry") } }.cause)
        assertTrue(assertFailsWith<IllegalArgumentException> {
            requireXamlNamedElement<Any>(null, "Page", "myButton")
        }.message!!.contains("myButton"))
        val value = Any()
        assertSame(value, requireXamlNamedElement(value, "Page", "myButton"))
    }
}
