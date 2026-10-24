package io.github.composefluent.winrt.ide.hotreload

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Gradle owns compilation and cancellation. Publication timeout starts after
 * launch finishes. Poll during builds too: run tasks can live as long as the host. */
internal suspend fun <T : Any> awaitDevelopmentPublication(
    building: () -> Boolean,
    timeoutMillis: Long = 30_000,
    nanoTime: () -> Long = System::nanoTime,
    discover: suspend () -> T?,
): T {
    val waitingAt = nanoTime()
    var finishedAt: Long? = null
    while (true) {
        currentCoroutineContext().ensureActive()
        discover()?.let { return it }
        if (!building()) {
            val now = nanoTime()
            val start = finishedAt ?: now.also { finishedAt = it }
            check(now - start < timeoutMillis * 1_000_000) {
                "The Gradle launch finished, but the application did not publish a development session. See Gradle Run output."
            }
        }
        // Cached hosts normally publish within the first second. Avoid adding
        // a full 100 ms polling interval to that path; keep long builds cheap.
        delay(if (nanoTime() - waitingAt < 2_000_000_000L) 25 else 100)
    }
}
