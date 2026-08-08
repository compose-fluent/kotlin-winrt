package io.github.composefluent.winrt.runtime

/**
 * Public projection-support wrapper around the runtime platform lock.
 */
class WinRTProjectionLock {
    private val lock = PlatformLock()

    @PublishedApi
    internal fun enter() {
        lock.enter()
    }

    @PublishedApi
    internal fun exit() {
        lock.exit()
    }

    fun <R> withLock(block: () -> R): R =
        lock.withLock(block)
}
