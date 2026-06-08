package io.github.composefluent.winrt.runtime

actual class ConcurrentCacheMap<K, V> actual constructor() {
    private val delegate = linkedMapOf<K, V>()

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
    ): V? {
        val existing = delegate[key]
        if (existing == null) {
            delegate[key] = value
        }
        return existing
    }

    actual fun remove(key: K): V? = delegate.remove(key)

    actual fun containsKey(key: K): Boolean = delegate.containsKey(key)

    actual fun computeIfAbsent(
        key: K,
        defaultValue: (K) -> V,
    ): V = delegate[key] ?: defaultValue(key).also { value -> delegate[key] = value }

    actual fun compute(
        key: K,
        remapping: (K, V?) -> V?,
    ): V? {
        val value = remapping(key, delegate[key])
        if (value == null) {
            delegate.remove(key)
        } else {
            delegate[key] = value
        }
        return value
    }

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
    private val delegate = linkedSetOf<T>()

    actual fun add(value: T): Boolean = delegate.add(value)

    actual operator fun contains(value: T): Boolean = delegate.contains(value)

    actual fun clear() {
        delegate.clear()
    }
}

actual class WeakValueCache<K, V : Any> actual constructor() {
    private val delegate = linkedMapOf<K, V>()

    actual operator fun get(key: K): V? = delegate[key]

    actual operator fun set(
        key: K,
        value: V,
    ) {
        delegate[key] = value
    }

    actual fun remove(key: K): V? = delegate.remove(key)

    actual fun clear() {
        delegate.clear()
    }
}

actual class WeakKeyStateMap<K : Any, V : Any> actual constructor() {
    private val delegate = linkedMapOf<K, V>()

    actual operator fun get(key: K): V? = delegate[key]

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V = delegate[key] ?: defaultValue().also { value -> delegate[key] = value }

    actual fun remove(key: K): V? = delegate.remove(key)

    actual fun clear() {
        delegate.clear()
    }
}

actual class SnapshotList<T> actual constructor() {
    private val delegate = mutableListOf<T>()

    actual fun add(value: T) {
        delegate += value
    }

    actual fun clear() {
        delegate.clear()
    }

    actual fun <R : Any> firstNotNullOfOrNull(transform: (T) -> R?): R? =
        delegate.firstNotNullOfOrNull(transform)

    actual fun toList(): List<T> = delegate.toList()
}

actual class FinalizationHook actual constructor() {
    actual fun register(
        target: Any,
        cleanup: () -> Unit,
    ): AutoCloseable =
        AutoCloseable {
            cleanup()
        }
}
