package io.github.composefluent.winrt.runtime

/**
 * Reusable dispatcher callback owner corresponding to CsWinRT's projection-owned
 * `DispatcherQueueProxyHandler` responsibility.
 *
 * The generated DispatcherQueue additions provide the concrete handler type. The runtime owns only
 * the cross-delegate queue/drain mechanics so ordinary WinRT delegate marshaling stays generic.
 */
class WinRtDispatcherQueueScheduler<THandler : Any>(
    private val tryEnqueueHandler: (THandler) -> Boolean,
    handlerFactory: (() -> Unit) -> THandler,
) {
    private val lock = PlatformLock()
    private val pendingActions = mutableListOf<() -> Unit>()
    private var drainScheduled = false
    private val drainHandler: THandler = handlerFactory(::drain)

    fun enqueue(action: () -> Unit): Boolean {
        val shouldSchedule = lock.withLock {
            pendingActions += action
            if (drainScheduled) {
                false
            } else {
                drainScheduled = true
                true
            }
        }
        if (!shouldSchedule) {
            return true
        }
        if (tryEnqueueHandler(drainHandler)) {
            return true
        }
        lock.withLock {
            pendingActions.remove(action)
            drainScheduled = false
        }
        return false
    }

    internal fun pendingActionCountForTesting(): Int =
        lock.withLock {
            pendingActions.size
        }

    private fun drain() {
        while (true) {
            val action = lock.withLock {
                if (pendingActions.isEmpty()) {
                    drainScheduled = false
                    null
                } else {
                    pendingActions.removeAt(0)
                }
            } ?: return
            try {
                action()
            } catch (error: Throwable) {
                ExceptionHelpers.reportUnhandledError(error)
            }
        }
    }
}
