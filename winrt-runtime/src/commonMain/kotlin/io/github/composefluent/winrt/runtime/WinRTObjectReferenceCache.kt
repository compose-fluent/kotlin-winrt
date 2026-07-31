@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference

/**
 * Lazily publishes a projected object reference using the same single-field fast path as CsWinRT.
 */
class WinRTObjectReferenceCache<T : AutoCloseable>(
    private val createReference: () -> T,
) {
    @PublishedApi
    internal val cachedReference = AtomicReference<T?>(null)

    val value: T
        inline get() = cachedReference.load() ?: initialize()

    @PublishedApi
    internal fun initialize(): T {
        cachedReference.load()?.let { return it }
        val candidate = createReference()
        if (cachedReference.compareAndSet(null, candidate)) {
            return candidate
        }
        val published = checkNotNull(cachedReference.load())
        candidate.close()
        return published
    }
}
