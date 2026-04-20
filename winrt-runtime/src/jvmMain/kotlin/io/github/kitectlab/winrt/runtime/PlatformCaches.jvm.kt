package io.github.kitectlab.winrt.runtime

import java.lang.ref.Cleaner
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

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
    private val delegate = ConcurrentHashMap<K, WeakReference<V>>()

    actual operator fun get(key: K): V? {
        val value = delegate[key]?.get()
        if (value == null) {
            delegate.remove(key)
        }
        return value
    }

    actual operator fun set(
        key: K,
        value: V,
    ) {
        delegate[key] = WeakReference(value)
    }

    actual fun remove(key: K): V? = delegate.remove(key)?.get()

    actual fun clear() {
        delegate.clear()
    }
}

actual class WeakKeyStateMap<K : Any, V : Any> actual constructor() {
    private val delegate = WeakHashMap<K, V>()

    actual operator fun get(key: K): V? = synchronized(delegate) { delegate[key] }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V = synchronized(delegate) {
        delegate[key] ?: defaultValue().also { value -> delegate[key] = value }
    }

    actual fun clear() {
        synchronized(delegate) {
            delegate.clear()
        }
    }
}

actual class SnapshotList<T> actual constructor() {
    private val delegate = CopyOnWriteArrayList<T>()

    actual fun add(value: T) {
        delegate.add(value)
    }

    actual fun clear() {
        delegate.clear()
    }

    actual fun <R : Any> firstNotNullOfOrNull(transform: (T) -> R?): R? =
        delegate.firstNotNullOfOrNull(transform)

    actual fun toList(): List<T> = delegate.toList()
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
