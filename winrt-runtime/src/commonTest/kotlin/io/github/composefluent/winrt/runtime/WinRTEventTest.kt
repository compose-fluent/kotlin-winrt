package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import windows.foundation.EventRegistrationToken

/** Kotlin token bookkeeping around CsWinRT EventSource's subscribe/unsubscribe contract. */
class WinRTEventTest {
    @Test
    fun null_handler_returns_zero_without_subscribing_or_allocating_a_token() {
        // CsWinRT EventRegistrationTokenTable<T>.AddEventHandler(null) returns default.
        var subscriptions = 0
        val event = WinRTEvent<String>(
            subscribe = { EventRegistrationToken((++subscriptions).toLong()) },
            unsubscribe = { error("Null registrations must not reach the native event") },
        )
        assertEquals(EventRegistrationToken(), event.add(null))
        event += null
        event -= null
        assertFalse(event.remove(null))
        event.remove(EventRegistrationToken())
        assertEquals(0, subscriptions)
        assertEquals(EventRegistrationToken(1), event.add("handler"))
    }

    @Test
    fun empty_removal_then_duplicate_subscriptions_preserve_token_order() {
        var nextToken = 1L
        val removed = mutableListOf<EventRegistrationToken>()
        val event = WinRTEvent<String>(
            subscribe = { EventRegistrationToken(nextToken++) },
            unsubscribe = removed::add,
        )
        assertFalse(event.remove("handler"))
        val unknown = EventRegistrationToken(99L)
        event.remove(unknown)
        val first = event.add("handler")
        val second = event.add("handler")
        assertTrue(event.remove("handler"))
        event.remove(first)
        assertFalse(event.remove("handler"))
        assertEquals(listOf(unknown, second, first), removed)
    }

    @Test
    fun reentrant_first_subscription_keeps_inner_and_outer_registrations() {
        var nextToken = 1L
        val removed = mutableListOf<EventRegistrationToken>()
        lateinit var event: WinRTEvent<String>
        event = WinRTEvent(
            subscribe = { handler ->
                if (handler == "outer") event.add("inner")
                EventRegistrationToken(nextToken++)
            },
            unsubscribe = removed::add,
        )
        event.add("outer")
        assertTrue(event.remove("inner"))
        assertTrue(event.remove("outer"))
        assertEquals(listOf(EventRegistrationToken(1L), EventRegistrationToken(2L)), removed)
    }

    @Test
    fun multiple_handlers_and_duplicates_preserve_last_registration_removal_after_promotion() {
        var nextToken = 1L
        val removed = mutableListOf<EventRegistrationToken>()
        val event = WinRTEvent<String>(
            subscribe = { EventRegistrationToken(nextToken++) },
            unsubscribe = removed::add,
        )
        val first = event.add("a")
        val second = event.add("b")
        val duplicate = event.add("a")
        assertTrue(event.remove("a"))
        event.remove(second)
        assertTrue(event.remove("a"))
        assertFalse(event.remove("b"))
        val next = event.add("c")
        assertTrue(event.remove("c"))
        assertEquals(listOf(duplicate, second, first, next), removed)
    }

    @Test
    fun reentrant_removal_preserves_the_new_subscription_for_single_and_multiple_handlers() {
        for (multiple in listOf(false, true)) {
            var nextToken = 1L
            val removed = mutableListOf<EventRegistrationToken>()
            lateinit var event: WinRTEvent<String>
            event = WinRTEvent(
                subscribe = { EventRegistrationToken(nextToken++) },
                unsubscribe = { token ->
                    removed += token
                    if (token.value == 1L) event.add("next")
                },
            )
            val first = event.add("first")
            val other = if (multiple) event.add("other") else null
            event.remove(first)
            assertFalse(event.remove("first"))
            assertTrue(event.remove("next"))
            if (other != null) assertTrue(event.remove("other"))
            assertEquals(if (multiple) listOf(1L, 3L, 2L) else listOf(1L, 2L), removed.map { it.value })
        }
    }
}
