package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

expect class ConcurrentCacheMap<K, V>() {
    operator fun get(key: K): V?

    operator fun set(
        key: K,
        value: V,
    )

    fun putIfAbsent(
        key: K,
        value: V,
    ): V?

    fun remove(key: K): V?

    fun containsKey(key: K): Boolean

    fun computeIfAbsent(
        key: K,
        defaultValue: (K) -> V,
    ): V

    fun compute(
        key: K,
        remapping: (K, V?) -> V?,
    ): V?

    fun clear()

    val size: Int
    val values: Collection<V>
    val entries: Set<Map.Entry<K, V>>
}

expect class ConcurrentCacheSet<T>() {
    fun add(value: T): Boolean

    operator fun contains(value: T): Boolean

    fun clear()
}

/**
 * Weak-value storage with lookup-local reads. Stale-entry reclamation is amortized by
 * mutations and explicit size inspection; a read only validates and removes its requested key.
 */
internal interface WeakValueCacheReference<out V : Any> {
    fun get(): V?
}

expect class WeakValueCache<K, V : Any>() {
    operator fun get(key: K): V?

    internal fun reference(key: K): WeakValueCacheReference<V>?

    operator fun set(
        key: K,
        value: V,
    )

    internal fun put(
        key: K,
        value: V,
    ): WeakValueCacheReference<V>

    fun remove(key: K): V?

    fun clear()

    internal val size: Int
}

internal interface WeakKeyStateEntry<K : Any, V : Any> {
    val value: V

    val identityHashCode: Int

    var bucketNext: WeakKeyStateEntry<K, V>?

    fun keyOrNull(): K?
}

internal interface WeakKeyStateEntryFactory<K : Any, V : Any> {
    fun create(
        key: K,
        value: V,
        identityHashCode: Int,
    ): WeakKeyStateEntry<K, V>
}

/**
 * Shared weak-identity bucket storage. Targets own only weak-reference notification and locking.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class WeakKeyStateMapCore<K : Any, V : Any>(
    private val entryFactory: WeakKeyStateEntryFactory<K, V>,
    private val onValueEvicted: (V) -> Unit,
) {
    private var buckets = weakKeyStateBuckets<K, V>(weakKeyStateInitialCapacity)
    private var entryCount = 0
    private val lastEntry = AtomicReference<WeakKeyStateEntry<K, V>?>(null)

    fun fastValue(
        key: K,
        identityHashCode: Int,
    ): V? {
        val entry = lastEntry.load() ?: return null
        return if (entry.matches(key, identityHashCode)) entry.value else null
    }

    fun value(
        key: K,
        identityHashCode: Int,
    ): V? {
        val entry = findEntry(key, identityHashCode) ?: return null
        lastEntry.store(entry)
        return entry.value
    }

    fun getOrPut(
        key: K,
        identityHashCode: Int,
        defaultValue: () -> V,
    ): V {
        findEntry(key, identityHashCode)?.let { entry ->
            lastEntry.store(entry)
            return entry.value
        }

        val value = defaultValue()
        ensureInsertCapacity()
        val entry = entryFactory.create(key, value, identityHashCode)
        val bucketIndex = bucketIndex(identityHashCode, buckets.size)
        entry.bucketNext = buckets[bucketIndex]
        buckets[bucketIndex] = entry
        entryCount += 1
        lastEntry.store(entry)
        return value
    }

    fun remove(
        key: K,
        identityHashCode: Int,
        expectedValue: V? = null,
    ): V? {
        val bucketIndex = bucketIndex(identityHashCode, buckets.size)
        var previous: WeakKeyStateEntry<K, V>? = null
        var entry = buckets[bucketIndex]
        while (entry != null) {
            if (
                entry.matches(key, identityHashCode) &&
                (expectedValue == null || entry.value === expectedValue)
            ) {
                unlink(bucketIndex, previous, entry)
                onValueEvicted(entry.value)
                return entry.value
            }
            previous = entry
            entry = entry.bucketNext
        }
        return null
    }

    /** Returns true only when [entry] is still present and its key is live. */
    fun sweep(entry: WeakKeyStateEntry<K, V>): Boolean {
        val bucketIndex = bucketIndex(entry.identityHashCode, buckets.size)
        var previous: WeakKeyStateEntry<K, V>? = null
        var candidate = buckets[bucketIndex]
        while (candidate != null) {
            if (candidate === entry) {
                if (candidate.keyOrNull() != null) {
                    return true
                }
                unlink(bucketIndex, previous, candidate)
                onValueEvicted(candidate.value)
                return false
            }
            previous = candidate
            candidate = candidate.bucketNext
        }
        return false
    }

    fun clear() {
        lastEntry.store(null)
        buckets.forEachIndexed { index, first ->
            var entry = first
            while (entry != null) {
                val next = entry.bucketNext
                entry.bucketNext = null
                onValueEvicted(entry.value)
                entry = next
            }
            buckets[index] = null
        }
        entryCount = 0
    }

    private fun findEntry(
        key: K,
        identityHashCode: Int,
    ): WeakKeyStateEntry<K, V>? {
        var entry = buckets[bucketIndex(identityHashCode, buckets.size)]
        while (entry != null) {
            if (entry.matches(key, identityHashCode)) {
                return entry
            }
            entry = entry.bucketNext
        }
        return null
    }

    private fun unlink(
        bucketIndex: Int,
        previous: WeakKeyStateEntry<K, V>?,
        entry: WeakKeyStateEntry<K, V>,
    ) {
        val next = entry.bucketNext
        if (previous == null) {
            buckets[bucketIndex] = next
        } else {
            previous.bucketNext = next
        }
        entry.bucketNext = null
        entryCount -= 1
        lastEntry.compareAndSet(entry, null)
    }

    private fun ensureInsertCapacity() {
        if (
            (entryCount + 1L) * weakKeyStateLoadDenominator <=
            buckets.size.toLong() * weakKeyStateLoadNumerator ||
            buckets.size >= weakKeyStateMaximumCapacity
        ) {
            return
        }

        val resized = weakKeyStateBuckets<K, V>(buckets.size shl 1)
        buckets.forEach { first ->
            var entry = first
            while (entry != null) {
                val next = entry.bucketNext
                val bucketIndex = bucketIndex(entry.identityHashCode, resized.size)
                entry.bucketNext = resized[bucketIndex]
                resized[bucketIndex] = entry
                entry = next
            }
        }
        buckets = resized
    }
}

private fun <K : Any, V : Any> WeakKeyStateEntry<K, V>.matches(
    key: K,
    identityHashCode: Int,
): Boolean = this.identityHashCode == identityHashCode && keyOrNull() === key

private fun bucketIndex(
    identityHashCode: Int,
    capacity: Int,
): Int = (identityHashCode xor (identityHashCode ushr 16)) and (capacity - 1)

@Suppress("UNCHECKED_CAST")
private fun <K : Any, V : Any> weakKeyStateBuckets(
    capacity: Int,
): Array<WeakKeyStateEntry<K, V>?> =
    arrayOfNulls<WeakKeyStateEntry<*, *>>(capacity) as Array<WeakKeyStateEntry<K, V>?>

private const val weakKeyStateInitialCapacity = 16
private const val weakKeyStateMaximumCapacity = 1 shl 30
private const val weakKeyStateLoadNumerator = 3
private const val weakKeyStateLoadDenominator = 4

expect class WeakKeyStateMap<K : Any, V : Any>(
    onValueEvicted: (V) -> Unit = {},
) {
    operator fun get(key: K): V?

    fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V

    fun remove(key: K): V?

    fun remove(
        key: K,
        value: V,
    ): Boolean

    fun clear()
}

expect class SnapshotList<T>() {
    fun add(value: T)

    fun remove(value: T): Boolean

    fun clear()

    fun <R : Any> firstNotNullOfOrNull(transform: (T) -> R?): R?

    fun toList(): List<T>
}

operator fun <T> SnapshotList<T>.plusAssign(value: T) {
    add(value)
}

expect class FinalizationHook() {
    fun register(
        target: Any,
        cleanup: () -> Unit,
    ): AutoCloseable
}

internal expect fun createComPtrFinalizationRegistration(
    target: Any,
    support: RawComObjectReferenceSupport,
): Any

internal expect fun closeComPtrFinalizationRegistration(
    registration: Any,
    support: RawComObjectReferenceSupport,
)
