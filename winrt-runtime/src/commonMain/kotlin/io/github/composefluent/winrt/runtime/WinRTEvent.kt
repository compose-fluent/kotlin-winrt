package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken

class WinRTEvent<T : Any>(
    private val subscribe: (T) -> EventRegistrationToken,
    private val unsubscribe: (EventRegistrationToken) -> Unit,
    private val unsubscribeHandler: ((T) -> Unit)? = null,
) {
    // CsWinRT 3 EventSourceState<T> stores its target delegate and token directly.
    // Kotlin also exposes token-based removal, so keep one registration directly
    // and create the two lookup maps only when multiple registrations need them.
    private var registrations: Registrations<T>? = null

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
        // Subscribe can reenter add/remove, so read the current state afterwards.
        when (val current = registrations) {
            null -> registrations = SingleRegistration(handler, token)
            is SingleRegistration -> {
                val multiple = MultipleRegistrations<T>()
                multiple.add(current.handler, current.token)
                multiple.add(handler, token)
                registrations = multiple
            }
            is MultipleRegistrations -> current.add(handler, token)
        }
        return token
    }

    operator fun plusAssign(handler: T?) {
        add(handler)
    }

    fun remove(token: EventRegistrationToken) {
        if (token == EventRegistrationToken()) return
        val handler = when (val current = registrations) {
            null -> null
            is SingleRegistration -> if (current.token == token) {
                registrations = null
                current.handler
            } else {
                null
            }
            is MultipleRegistrations -> current.remove(token).also {
                if (current.tokensByHandler.isEmpty()) registrations = null
            }
        }
        // CsWinRT EventSource.Unsubscribe drops the handler before native removal.
        // This also lets a reentrant unsubscribe install a fresh registration.
        if (handler != null && unsubscribeHandler != null) {
            unsubscribeHandler.invoke(handler)
        } else {
            unsubscribe(token)
        }
    }

    fun remove(handler: T?): Boolean {
        if (handler == null) return false
        val token = when (val current = registrations) {
            null -> null
            is SingleRegistration -> current.token.takeIf { handler == current.handler }
            is MultipleRegistrations -> current.tokensByHandler[handler]?.lastOrNull()
        } ?: return false
        remove(token)
        return true
    }

    operator fun minusAssign(handler: T?) {
        remove(handler)
    }

    private sealed interface Registrations<T : Any>

    private class SingleRegistration<T : Any>(
        val handler: T,
        val token: EventRegistrationToken,
    ) : Registrations<T>

    private class MultipleRegistrations<T : Any> : Registrations<T> {
        val tokensByHandler = mutableMapOf<T, MutableList<EventRegistrationToken>>()
        private val handlersByToken = mutableMapOf<EventRegistrationToken, T>()

        fun add(handler: T, token: EventRegistrationToken) {
            tokensByHandler.getOrPut(handler) { mutableListOf() }.add(token)
            handlersByToken[token] = handler
        }

        fun remove(token: EventRegistrationToken): T? =
            handlersByToken.remove(token)?.also { handler ->
                val tokens = tokensByHandler[handler]
                tokens?.remove(token)
                if (tokens.isNullOrEmpty()) tokensByHandler.remove(handler)
            }
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
