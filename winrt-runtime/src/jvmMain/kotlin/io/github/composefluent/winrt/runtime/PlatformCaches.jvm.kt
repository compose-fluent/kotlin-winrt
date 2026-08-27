package io.github.composefluent.winrt.runtime

import java.lang.ref.Cleaner
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

actual class ConcurrentCacheMap<K, V> actual constructor() {
    private val delegate = ConcurrentHashMap<K, V>()

    actual operator fun get(key: K): V? = delegate[key]

    actual operator fun set(
        key: K,
        value: V,
    ) {
        delegate[key] = value
    }

    actual fun putIfAbsent(
        key: K,
        value: V,
    ): V? = delegate.putIfAbsent(key, value)

    actual fun remove(key: K): V? = delegate.remove(key)

    actual fun containsKey(key: K): Boolean = delegate.containsKey(key)

    actual fun computeIfAbsent(
        key: K,
        defaultValue: (K) -> V,
    ): V = delegate.computeIfAbsent(key, defaultValue)

    actual fun compute(
        key: K,
        remapping: (K, V?) -> V?,
    ): V? = delegate.compute(key, remapping)

    actual fun clear() {
        delegate.clear()
    }

    actual val size: Int
        get() = delegate.size

    actual val values: Collection<V>
        get() = delegate.values

    actual val entries: Set<Map.Entry<K, V>>
        get() = delegate.entries
}

actual class ConcurrentCacheSet<T> actual constructor() {
    private val delegate = ConcurrentHashMap.newKeySet<T>()

    actual fun add(value: T): Boolean = delegate.add(value)

    actual operator fun contains(value: T): Boolean = delegate.contains(value)

    actual fun clear() {
        delegate.clear()
    }
}

actual class WeakValueCache<K, V : Any> actual constructor() {
    private val referenceQueue = ReferenceQueue<V>()
    private val delegate = ConcurrentHashMap<K, WeakValueCacheEntry<K, V>>()

    actual operator fun get(key: K): V? = reference(key)?.get()

    internal actual fun reference(key: K): WeakValueCacheReference<V>? {
        val entry = delegate[key] ?: return null
        if (entry.get() == null) {
            delegate.remove(key, entry)
            return null
        }
        return entry
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
    ): WeakValueCacheReference<V> {
        drainClearedValues()
        return WeakValueCacheEntry(key, value, referenceQueue).also { entry ->
            delegate[key] = entry
        }
    }

    actual fun remove(key: K): V? {
        drainClearedValues()
        return delegate.remove(key)?.get()
    }

    actual fun clear() {
        delegate.clear()
        while (referenceQueue.poll() != null) {
            // Drain stale queue entries so they cannot accumulate across cache resets.
        }
    }

    internal actual val size: Int
        get() {
            drainClearedValues()
            return delegate.size
        }

    @Suppress("UNCHECKED_CAST")
    private fun drainClearedValues() {
        while (true) {
            val entry = referenceQueue.poll() as? WeakValueCacheEntry<K, V> ?: return
            delegate.remove(entry.key, entry)
        }
    }
}

private class WeakValueCacheEntry<K, V : Any>(
    val key: K,
    value: V,
    referenceQueue: ReferenceQueue<V>,
) : WeakReference<V>(value, referenceQueue), WeakValueCacheReference<V>

actual class WeakKeyStateMap<K : Any, V : Any> actual constructor(
    private val onValueEvicted: (V) -> Unit,
) {
    private val lock = Any()
    private val referenceQueue = ReferenceQueue<K>()
    private val core = WeakKeyStateMapCore<K, V>(
        entryFactory = object : WeakKeyStateEntryFactory<K, V> {
            override fun create(
                key: K,
                value: V,
                identityHashCode: Int,
            ): WeakKeyStateEntry<K, V> =
                JvmWeakKeyStateEntry(key, value, identityHashCode, referenceQueue)
        },
        onValueEvicted = onValueEvicted,
    )

    actual operator fun get(key: K): V? {
        val identityHashCode = System.identityHashCode(key)
        core.fastValue(key, identityHashCode)?.let { return it }
        return synchronized(lock) {
            drainClearedKeys()
            core.value(key, identityHashCode)
        }
    }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V {
        val identityHashCode = System.identityHashCode(key)
        core.fastValue(key, identityHashCode)?.let { return it }
        return synchronized(lock) {
            drainClearedKeys()
            core.getOrPut(key, identityHashCode, defaultValue)
        }
    }

    actual fun remove(key: K): V? =
        synchronized(lock) {
            drainClearedKeys()
            val identityHashCode = System.identityHashCode(key)
            core.remove(key, identityHashCode)
        }

    actual fun remove(
        key: K,
        value: V,
    ): Boolean =
        synchronized(lock) {
            drainClearedKeys()
            val identityHashCode = System.identityHashCode(key)
            core.remove(key, identityHashCode, value) != null
        }

    actual fun clear() {
        synchronized(lock) {
            core.clear()
            while (referenceQueue.poll() != null) {
                // Drain stale queue entries so they cannot accumulate across test/runtime resets.
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun drainClearedKeys() {
        while (true) {
            val entry = referenceQueue.poll() as? JvmWeakKeyStateEntry<K, V> ?: return
            core.sweep(entry)
        }
    }
}

private class JvmWeakKeyStateEntry<K : Any, V : Any>(
    key: K,
    override val value: V,
    override val identityHashCode: Int,
    referenceQueue: ReferenceQueue<K>,
) : WeakReference<K>(key, referenceQueue), WeakKeyStateEntry<K, V> {
    override var bucketNext: WeakKeyStateEntry<K, V>? = null

    override fun keyOrNull(): K? = get()
}

actual class FinalizationHook actual constructor() {
    private val cleaner = Cleaner.create()

    actual fun register(
        target: Any,
        cleanup: () -> Unit,
    ): AutoCloseable {
        val cleanable = cleaner.register(target, cleanup)
        return AutoCloseable {
            cleanable.clean()
        }
    }
}

internal actual fun createComPtrFinalizationRegistration(
    target: Any,
    support: RawComObjectReferenceSupport,
): Any = comPtrCleaner.register(target, ComPtrCleanupAction(support))

private class ComPtrCleanupAction(
    private val support: RawComObjectReferenceSupport,
) : Runnable {
    override fun run() {
        closeComPtrSupport(support)
    }
}

internal actual fun closeComPtrFinalizationRegistration(
    registration: Any,
    @Suppress("UNUSED_PARAMETER") support: RawComObjectReferenceSupport,
) {
    (registration as Cleaner.Cleanable).clean()
}

private val comPtrCleaner: Cleaner = Cleaner.create()
