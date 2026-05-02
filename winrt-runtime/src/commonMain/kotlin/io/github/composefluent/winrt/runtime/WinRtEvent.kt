package io.github.composefluent.winrt.runtime

class WinRtEvent<T : Any>(
    private val subscribe: (T) -> EventRegistrationToken,
    private val unsubscribe: (EventRegistrationToken) -> Unit,
    private val unsubscribeHandler: ((T) -> Unit)? = null,
) {
    private val tokensByHandler = mutableMapOf<T, MutableList<EventRegistrationToken>>()
    private val handlersByToken = mutableMapOf<EventRegistrationToken, T>()

    constructor(eventSource: EventSource<T>) : this(EventSourceSubscription(eventSource))

    private constructor(subscription: EventSourceSubscription<T>) : this(
        subscribe = subscription::subscribe,
        unsubscribe = {},
        unsubscribeHandler = subscription::unsubscribe,
    )

    fun add(handler: T): EventRegistrationToken {
        val token = subscribe(handler)
        tokensByHandler.getOrPut(handler) { mutableListOf() }.add(token)
        handlersByToken[token] = handler
        return token
    }

    operator fun plusAssign(handler: T) {
        add(handler)
    }

    fun remove(token: EventRegistrationToken) {
        val handler = handlersByToken.remove(token)
        if (handler != null && unsubscribeHandler != null) {
            unsubscribeHandler.invoke(handler)
        } else {
            unsubscribe(token)
        }
        tokensByHandler.values.forEach { it.remove(token) }
    }

    fun remove(handler: T): Boolean {
        val tokens = tokensByHandler[handler] ?: return false
        val token = tokens.removeLastOrNull() ?: return false
        if (tokens.isEmpty()) {
            tokensByHandler.remove(handler)
        }
        handlersByToken.remove(token)
        unsubscribeHandler?.invoke(handler) ?: unsubscribe(token)
        return true
    }

    operator fun minusAssign(handler: T) {
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
