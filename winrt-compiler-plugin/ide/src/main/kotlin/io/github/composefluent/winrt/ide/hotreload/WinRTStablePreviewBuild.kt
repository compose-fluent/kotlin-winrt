package io.github.composefluent.winrt.ide.hotreload

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Initial preview preparation has no live process to patch. Recheck user inputs
 * after Gradle exits, including failed semantic-compilation races. Stable failures
 * stay failures; cancellation and superseded launches must never start another build. */
internal suspend fun <T> buildStableWinRTPreview(
    inputs: suspend () -> T,
    build: suspend () -> Boolean,
    changed: () -> Unit = {},
): T {
    while (true) {
        currentCoroutineContext().ensureActive()
        val before = inputs()
        val success = build()
        currentCoroutineContext().ensureActive()
        if (before != inputs()) { changed(); continue }
        check(success) { "The Gradle preview build failed. See its Run output." }
        return before
    }
}
