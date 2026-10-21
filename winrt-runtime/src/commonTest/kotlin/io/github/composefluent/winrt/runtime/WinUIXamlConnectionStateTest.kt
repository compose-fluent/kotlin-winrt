package io.github.composefluent.winrt.runtime

import kotlin.test.*

class WinUIXamlConnectionStateTest {
    @Test
    fun disconnect_pairs_original_sources_and_tokens_without_affecting_other_slots() {
        // CsWinRT EventSource.Unsubscribe passes the token captured by Subscribe.
        val state = WinUIXamlConnectionState()
        val first = Any()
        val second = Any()
        val removed = mutableListOf<Pair<Any, Long>>()
        state.trackEvent(1, first, 7L) { source, token -> removed += source to token }
        state.trackEvent(2, second, 8L) { source, token -> removed += source to token }
        state.trackEvent(1, first, 9L) { source, token -> removed += source to token }
        state.disconnect(1)
        assertEquals(listOf(first to 9L, first to 7L), removed)
        state.disconnect(1)
        state.disconnectAll()
        assertEquals(listOf(first to 9L, first to 7L, second to 8L), removed)
    }

    @Test
    fun removal_failure_cleans_remaining_subscriptions_and_blocks_reconnection() {
        val state = WinUIXamlConnectionState()
        val cause = IllegalStateException("remove failed")
        var cleaned = 0
        state.trackEvent(1, Any(), 1) { _, _ -> cleaned++ }
        state.trackEvent(1, Any(), 2) { _, _ -> throw cause }
        assertSame(cause, assertFailsWith<IllegalStateException> { state.disconnect(1) })
        assertEquals(1, cleaned)
        assertSame(cause, assertFailsWith<IllegalStateException> { state.disconnect(1) }.cause)
        assertSame(cause, assertFailsWith<IllegalStateException> {
            state.trackEvent(1, Any(), 3) { _, _ -> cleaned++ }
        }.cause)
        assertEquals(2, cleaned)
    }
}
