package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken

class WinRTEvent<T : Any>(
    private val subscribe: (T) -> EventRegistrationToken,
    private val unsubscribe: (EventRegistrationToken) -> Unit,
    private val unsubscribeHandler: ((T) -> Unit)? = null,
) {
    // Like CsWinRT EventSource.Subscribe, allocate subscription bookkeeping on first use.
    private var tokensByHandler: MutableMap<T, MutableList<EventRegistrationToken>>? = null
    private var handlersByToken: MutableMap<EventRegistrationToken, T>? = null

    constructor(eventSource: EventSource<T>) : this(EventSourceSubscription(eventSource))

    private constructor(subscription: EventSourceSubscription<T>) : this(
        subscribe = subscription::subscribe,
        unsubscribe = {},
        unsubscribeHandler = subscription::unsubscribe,
    )

    fun add(handler: T?): EventRegistrationToken {
        // CsWinRT EventRegistrationTokenTable<T>.AddEventHandler(null) is a no-op.
        if (handler == null) return EventRegistrationToken()
        val token = subscribe(handler)
        val tokensByHandler = tokensByHandler
            ?: mutableMapOf<T, MutableList<EventRegistrationToken>>().also { this.tokensByHandler = it }
        val handlersByToken = handlersByToken
            ?: mutableMapOf<EventRegistrationToken, T>().also { this.handlersByToken = it }
        tokensByHandler.getOrPut(handler) { mutableListOf() }.add(token)
        handlersByToken[token] = handler
        return token
    }

    operator fun plusAssign(handler: T?) {
        add(handler)
    }

    fun remove(token: EventRegistrationToken) {
        if (token == EventRegistrationToken()) return
        val handler = handlersByToken?.remove(token)
        if (handler != null) {
            val tokens = tokensByHandler?.get(handler)
            tokens?.remove(token)
            if (tokens.isNullOrEmpty()) tokensByHandler?.remove(handler)
            // CsWinRT EventSource.Unsubscribe releases the delegate before native removal.
            // Drop empty bookkeeping as well, including handlers removed by native token.
            if (tokensByHandler.isNullOrEmpty()) tokensByHandler = null
            if (handlersByToken.isNullOrEmpty()) handlersByToken = null
        }
        if (handler != null && unsubscribeHandler != null) {
            unsubscribeHandler.invoke(handler)
        } else {
            unsubscribe(token)
        }
    }

    fun remove(handler: T?): Boolean {
        if (handler == null) return false
        val tokensByHandler = tokensByHandler ?: return false
        val tokens = tokensByHandler[handler] ?: return false
        val token = tokens.lastOrNull() ?: return false
        remove(token)
        return true
    }

    operator fun minusAssign(handler: T?) {
        remove(handler)
    }

    private class EventSourceSubscription<T : Any>(
        private val eventSource: EventSource<T>,
    ) {
        private var nextSyntheticToken = Long.MIN_VALUE

        fun subscribe(handler: T): EventRegistrationToken {
            eventSource.subscribe(handler)
            return EventRegistrationToken(nextSyntheticToken++)
        }

        fun unsubscribe(handler: T) {
            eventSource.unsubscribe(handler)
        }
    }
}
