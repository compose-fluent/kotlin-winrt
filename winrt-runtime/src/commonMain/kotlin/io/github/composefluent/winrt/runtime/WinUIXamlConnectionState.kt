package io.github.composefluent.winrt.runtime

/**
 * Compiler-owned ordinary XAML event subscriptions, separate from binding tracking.
 * CsWinRT EventSource.Subscribe/Unsubscribe pairs the original source and ABI token;
 * generated typed removers preserve that pairing without reflection or ABI policy here.
 * Access belongs to the component's UI thread.
 */
class WinUIXamlConnectionState {
    private data class Subscription(val connectionId: Int, val remove: () -> Unit)
    private val subscriptions = mutableListOf<Subscription>()
    private var failure: Throwable? = null

    fun <Source : Any, Registration> trackEvent(
        connectionId: Int,
        source: Source,
        registration: Registration,
        remove: (Source, Registration) -> Unit,
    ) {
        failure?.let { error ->
            try { remove(source, registration) } catch (cleanup: Throwable) {
                if (cleanup !== error) error.addSuppressed(cleanup)
            }
            throw IllegalStateException("XAML event cleanup failed; restart the component", error)
        }
        subscriptions += Subscription(connectionId) { remove(source, registration) }
    }

    /** Detach before reconnecting a slot; a failed removal must never add another handler. */
    fun disconnect(connectionId: Int) = release { it.connectionId == connectionId }

    fun disconnectAll() = release { true }

    private fun release(matches: (Subscription) -> Boolean) {
        failure?.let { throw IllegalStateException("XAML event cleanup failed; restart the component", it) }
        val pending = subscriptions.filter(matches)
        subscriptions.removeAll(pending.toSet())
        var error: Throwable? = null
        for (subscription in pending.asReversed()) {
            try { subscription.remove() } catch (caught: Throwable) {
                if (error == null) error = caught else if (error !== caught) error.addSuppressed(caught)
            }
        }
        failure = error
        error?.let { throw it }
    }
}
