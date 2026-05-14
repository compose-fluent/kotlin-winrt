package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object WinRtClipboardInterop {
    const val DefaultRetryAttempts: Int = 5
    const val DefaultInitialRetryDelayMillis: Long = 10
    const val DefaultMaxRetryDelayMillis: Long = 100

    fun isTransientClipboardOpenFailure(error: Throwable): Boolean =
        error is WinRtRuntimeException && error.hResult == KnownHResults.CLIPBRD_E_CANT_OPEN

    suspend fun <T> retryTransientClipboardAccess(
        attempts: Int = DefaultRetryAttempts,
        initialDelayMillis: Long = DefaultInitialRetryDelayMillis,
        maxDelayMillis: Long = DefaultMaxRetryDelayMillis,
        block: suspend () -> T,
    ): T {
        require(attempts > 0) { "attempts must be greater than zero." }
        require(initialDelayMillis >= 0) { "initialDelayMillis must be non-negative." }
        require(maxDelayMillis >= 0) { "maxDelayMillis must be non-negative." }

        var nextDelay = initialDelayMillis
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (error: Throwable) {
                if (!isTransientClipboardOpenFailure(error) || attempt >= attempts) {
                    throw error
                }
                if (nextDelay > 0) {
                    delay(nextDelay)
                }
                nextDelay = (nextDelay * 2).coerceAtMost(maxDelayMillis)
                attempt += 1
            }
        }
    }

    fun <T> retryTransientClipboardAccessBlocking(
        attempts: Int = DefaultRetryAttempts,
        initialDelayMillis: Long = DefaultInitialRetryDelayMillis,
        maxDelayMillis: Long = DefaultMaxRetryDelayMillis,
        block: () -> T,
    ): T =
        runBlocking {
            retryTransientClipboardAccess(attempts, initialDelayMillis, maxDelayMillis) {
                block()
            }
        }
}
