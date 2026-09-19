package io.github.composefluent.winrt.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import windows.foundation.EventRegistrationToken

/** Kotlin token bookkeeping around CsWinRT EventSource's subscribe/unsubscribe contract. */
class WinRTEventTest {
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
}
