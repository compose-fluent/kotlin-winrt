package io.github.kitectlab.winrt.runtime

class WinRtEvent<T : Any>(
    private val subscribe: (T) -> EventRegistrationToken,
    private val unsubscribe: (EventRegistrationToken) -> Unit,
) {
    private val tokensByHandler = mutableMapOf<T, MutableList<EventRegistrationToken>>()

    fun add(handler: T): EventRegistrationToken {
        val token = subscribe(handler)
        tokensByHandler.getOrPut(handler) { mutableListOf() }.add(token)
        return token
    }

    operator fun plusAssign(handler: T) {
        add(handler)
    }

    fun remove(token: EventRegistrationToken) {
        unsubscribe(token)
        tokensByHandler.values.forEach { it.remove(token) }
    }

    fun remove(handler: T): Boolean {
        val tokens = tokensByHandler[handler] ?: return false
        val token = tokens.removeLastOrNull() ?: return false
        if (tokens.isEmpty()) {
            tokensByHandler.remove(handler)
        }
        unsubscribe(token)
        return true
    }

    operator fun minusAssign(handler: T) {
        remove(handler)
    }
}
