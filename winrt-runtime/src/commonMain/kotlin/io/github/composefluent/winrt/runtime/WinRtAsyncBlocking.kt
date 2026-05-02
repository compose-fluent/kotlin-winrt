package io.github.composefluent.winrt.runtime

import kotlinx.coroutines.runBlocking

/**
 * Shared blocking adapters for WinRT async references.
 *
 * `.cswinrt` exposes synchronous result retrieval above the async ABI owner; on Kotlin the
 * shared coroutine-based `await()` owner already exists in `commonMain`, so the blocking bridge
 * belongs here as part of the coroutine-facing runtime surface rather than a JVM-only future facade.
 */
fun WinRtAsyncActionReference.join() {
    runBlocking {
        await()
    }
}

fun <T> WinRtAsyncOperationReference<T>.join(): T =
    runBlocking {
        await()
    }
