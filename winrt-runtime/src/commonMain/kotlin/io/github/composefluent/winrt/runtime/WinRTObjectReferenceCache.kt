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
    internal fun initialize(): T = getOrCreateWinRTObjectReference(cachedReference, createReference)
}

/**
 * Publishes a candidate reference into a previously empty cache field.
 *
 * The generated projection follows the CsWinRT shape: its hot getter performs
 * one load and calls a separate cold Make function only on a miss. Keeping the
 * compare/exchange body here makes that Make function shared across generated
 * projections while preserving exactly-once ownership transfer for losers.
 */
public fun <T : AutoCloseable> publishWinRTObjectReference(
    cachedReference: AtomicReference<T?>,
    candidate: T,
): T {
    if (cachedReference.compareAndSet(null, candidate)) {
        return candidate
    }
    val published = checkNotNull(cachedReference.load())
    candidate.close()
    return published
}

@PublishedApi
internal val generatedObjectReferencePublicationLock = WinRTProjectionLock()

/**
 * Publishes a generated projection's candidate into a volatile nullable field.
 *
 * Generated runtime classes keep the hot cache value directly on the object, matching
 * CsWinRT's nullable `IObjectReference` fields without allocating one atomic box per
 * implemented interface. Candidate creation remains outside the shared cold lock so a
 * reentrant QI cannot deadlock; a racing loser is closed under the same ownership rule
 * as the atomic-cache overload above.
 */
public inline fun <T : AutoCloseable> publishGeneratedWinRTObjectReference(
    candidate: T,
    crossinline readCachedReference: () -> T?,
    crossinline writeCachedReference: (T) -> Unit,
): T {
    generatedObjectReferencePublicationLock.enter()
    return try {
        val published = readCachedReference()
        if (published != null) {
            candidate.close()
            published
        } else {
            writeCachedReference(candidate)
            candidate
        }
    } finally {
        generatedObjectReferencePublicationLock.exit()
    }
}

/**
 * Publishes a generated collection adapter into a volatile nullable field.
 *
 * Collection adapters are ordinary managed values, so a losing candidate can be
 * discarded without ownership cleanup. The publication protocol otherwise matches
 * the generated object-reference cache: candidate construction stays outside the
 * lock and the field is written exactly once under the shared cold-path lock.
 */
public inline fun <T : Any> publishGeneratedWinRTValue(
    candidate: T,
    crossinline readCachedValue: () -> T?,
    crossinline writeCachedValue: (T) -> Unit,
): T {
    generatedObjectReferencePublicationLock.enter()
    return try {
        readCachedValue() ?: run {
            writeCachedValue(candidate)
            candidate
        }
    } finally {
        generatedObjectReferencePublicationLock.exit()
    }
}

/**
 * Lazily creates and publishes one owned reference for the runtime cache
 * wrapper retained by non-generated callers.
 */
public inline fun <T : AutoCloseable> getOrCreateWinRTObjectReference(
    cachedReference: AtomicReference<T?>,
    createReference: () -> T,
): T {
    cachedReference.load()?.let { return it }
    return publishWinRTObjectReference(cachedReference, createReference())
}
