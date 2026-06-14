package io.github.composefluent.winrt.runtime

/**
 * Public projection-support wrapper around the runtime platform lock.
 */
class WinRtProjectionLock {
    private val lock = PlatformLock()

    fun <R> withLock(block: () -> R): R =
        lock.withLock(block)
}
