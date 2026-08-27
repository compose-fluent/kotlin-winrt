@file:OptIn(
    kotlin.concurrent.atomics.ExperimentalAtomicApi::class,
    kotlin.experimental.ExperimentalNativeApi::class,
)

package io.github.composefluent.winrt.runtime

import kotlin.native.ref.createCleaner
import kotlin.native.ref.WeakReference as NativeWeakReference
import kotlin.native.identityHashCode

actual class ConcurrentCacheMap<K, V> actual constructor() {
    private val lock = PlatformLock()
    private val delegate = linkedMapOf<K, V>()

    actual operator fun get(key: K): V? =
        lock.withLock {
            delegate[key]
        }

    actual operator fun set(
        key: K,
        value: V,
    ) {
        lock.withLock {
            delegate[key] = value
        }
    }

    actual fun putIfAbsent(
        key: K,
        value: V,
    ): V? {
        return lock.withLock {
            val existing = delegate[key]
            if (existing == null) {
                delegate[key] = value
            }
            existing
        }
    }

    actual fun remove(key: K): V? =
        lock.withLock {
            delegate.remove(key)
        }

    actual fun containsKey(key: K): Boolean =
        lock.withLock {
            delegate.containsKey(key)
        }

    actual fun computeIfAbsent(
        key: K,
        defaultValue: (K) -> V,
    ): V =
        lock.withLock {
            delegate[key] ?: defaultValue(key).also { value -> delegate[key] = value }
        }

    actual fun compute(
        key: K,
        remapping: (K, V?) -> V?,
    ): V? {
        return lock.withLock {
            val value = remapping(key, delegate[key])
            if (value == null) {
                delegate.remove(key)
            } else {
                delegate[key] = value
            }
            value
        }
    }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
        }
    }

    actual val size: Int
        get() =
            lock.withLock {
                delegate.size
            }

    actual val values: Collection<V>
        get() =
            lock.withLock {
                delegate.values.toList()
            }

    actual val entries: Set<Map.Entry<K, V>>
        get() =
            lock.withLock {
                delegate.toMap().entries
            }
}

actual class ConcurrentCacheSet<T> actual constructor() {
    private val lock = PlatformLock()
    private val delegate = linkedSetOf<T>()

    actual fun add(value: T): Boolean =
        lock.withLock {
            delegate.add(value)
        }

    actual operator fun contains(value: T): Boolean =
        lock.withLock {
            delegate.contains(value)
        }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
        }
    }
}

actual class WeakValueCache<K, V : Any> actual constructor() {
    private val lock = PlatformLock()
    private val delegate = linkedMapOf<K, WeakValueCacheEntry<K, V>>()
    private val sweepQueue = ArrayDeque<WeakValueCacheEntry<K, V>>()

    actual operator fun get(key: K): V? = reference(key)?.get()

    internal actual fun reference(key: K): WeakValueCacheReference<V>? =
        lock.withLock {
            val entry = delegate[key] ?: return@withLock null
            val value = entry.value.get()
            if (value == null) {
                if (delegate[key] === entry) {
                    delegate.remove(key)
                }
                null
            } else {
                entry
            }
        }

    actual operator fun set(
        key: K,
        value: V,
    ) {
        put(key, value)
    }

    internal actual fun put(
        key: K,
        value: V,
    ): WeakValueCacheReference<V> =
        lock.withLock {
            sweepOneEntry()
            WeakValueCacheEntry(key, value).also { entry ->
                delegate[key] = entry
                sweepQueue.addLast(entry)
            }
        }

    actual fun remove(key: K): V? =
        lock.withLock {
            sweepOneEntry()
            delegate.remove(key)?.value?.get()
        }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
            sweepQueue.clear()
        }
    }

    internal actual val size: Int
        get() =
            lock.withLock {
                sweepAllEntries()
                delegate.size
            }

    private fun sweepOneEntry() {
        if (sweepQueue.isEmpty()) {
            return
        }
        val entry = sweepQueue.removeFirst()
        if (delegate[entry.key] !== entry) {
            return
        }
        if (entry.value.get() == null) {
            delegate.remove(entry.key)
        } else {
            sweepQueue.addLast(entry)
        }
    }

    private fun sweepAllEntries() {
        val entriesToCheck = sweepQueue.size
        repeat(entriesToCheck) {
            sweepOneEntry()
        }
    }

}

private class WeakValueCacheEntry<K, V : Any>(
    val key: K,
    value: V,
) : WeakValueCacheReference<V> {
    val value: NativeWeakReference<V> = NativeWeakReference(value)

    override fun get(): V? = value.get()
}

actual class WeakKeyStateMap<K : Any, V : Any> actual constructor(
    private val onValueEvicted: (V) -> Unit,
) {
    private val lock = PlatformLock()
    private val sweepQueue = ArrayDeque<WeakKeyStateEntry<K, V>>()
    private val core = WeakKeyStateMapCore<K, V>(
        entryFactory = object : WeakKeyStateEntryFactory<K, V> {
            override fun create(
                key: K,
                value: V,
                identityHashCode: Int,
            ): WeakKeyStateEntry<K, V> =
                NativeWeakKeyStateEntry(key, value, identityHashCode).also(sweepQueue::addLast)
        },
        onValueEvicted = onValueEvicted,
    )

    actual operator fun get(key: K): V? {
        val identityHashCode = key.identityHashCode()
        core.fastValue(key, identityHashCode)?.let { return it }
        return lock.withLock {
            sweepOneEntry()
            core.value(key, identityHashCode)
        }
    }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V {
        val identityHashCode = key.identityHashCode()
        core.fastValue(key, identityHashCode)?.let { return it }
        return lock.withLock {
            sweepOneEntry()
            core.getOrPut(key, identityHashCode, defaultValue)
        }
    }

    actual fun remove(key: K): V? =
        lock.withLock {
            sweepOneEntry()
            val identityHashCode = key.identityHashCode()
            core.remove(key, identityHashCode)
        }

    actual fun remove(
        key: K,
        value: V,
    ): Boolean =
        lock.withLock {
            sweepOneEntry()
            val identityHashCode = key.identityHashCode()
            core.remove(key, identityHashCode, value) != null
        }

    actual fun clear() {
        lock.withLock {
            core.clear()
            sweepQueue.clear()
        }
    }

    private fun sweepOneEntry() {
        if (sweepQueue.isEmpty()) {
            return
        }
        val entry = sweepQueue.removeFirst()
        if (core.sweep(entry)) {
            sweepQueue.addLast(entry)
        }
    }
}

private class NativeWeakKeyStateEntry<K : Any, V : Any>(
    key: K,
    override val value: V,
    override val identityHashCode: Int,
) : WeakKeyStateEntry<K, V> {
    private val key = NativeWeakReference(key)

    override var bucketNext: WeakKeyStateEntry<K, V>? = null

    override fun keyOrNull(): K? = key.get()
}

actual class FinalizationHook actual constructor() {
    @Suppress("UNUSED_PARAMETER")
    actual fun register(
        target: Any,
        cleanup: () -> Unit,
    ): AutoCloseable = NativeFinalizationRegistration(cleanup)
}

internal actual fun createComPtrFinalizationRegistration(
    @Suppress("UNUSED_PARAMETER") target: Any,
    support: RawComObjectReferenceSupport,
): Any = createCleaner(support, ::closeComPtrSupportFromFinalizer)

internal actual fun closeComPtrFinalizationRegistration(
    @Suppress("UNUSED_PARAMETER") registration: Any,
    support: RawComObjectReferenceSupport,
) {
    closeComPtrSupport(support)
}

private class NativeFinalizationRegistration(
    cleanup: () -> Unit,
) : AutoCloseable {
    private val state = NativeFinalizationState(cleanup)

    @Suppress("unused")
    private val cleaner = createCleaner(state) { it.runOnce() }

    override fun close() {
        state.runOnce()
    }
}

private class NativeFinalizationState(
    private val cleanup: () -> Unit,
) {
    private val cleaned = kotlin.concurrent.atomics.AtomicInt(0)

    fun runOnce() {
        if (cleaned.compareAndSet(0, 1)) {
            cleanup()
        }
    }
}
