package io.github.composefluent.winrt.runtime

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

    actual fun remove(key: K): V? =
        lock.withLock {
            delegate.remove(key)
        }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
        }
    }
}

actual class WeakKeyStateMap<K : Any, V : Any> actual constructor() {
    private val lock = PlatformLock()
    private val delegate = linkedMapOf<K, V>()

    actual operator fun get(key: K): V? =
        lock.withLock {
            delegate[key]
        }

    actual fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V =
        lock.withLock {
            delegate[key] ?: defaultValue().also { value -> delegate[key] = value }
        }

    actual fun remove(key: K): V? =
        lock.withLock {
            delegate.remove(key)
        }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
        }
    }
}

actual class SnapshotList<T> actual constructor() {
    private val lock = PlatformLock()
    private val delegate = mutableListOf<T>()

    actual fun add(value: T) {
        lock.withLock {
            delegate += value
        }
    }

    actual fun remove(value: T): Boolean =
        lock.withLock {
            delegate.remove(value)
        }

    actual fun clear() {
        lock.withLock {
            delegate.clear()
        }
    }

    actual fun <R : Any> firstNotNullOfOrNull(transform: (T) -> R?): R? =
        toList().firstNotNullOfOrNull(transform)

    actual fun toList(): List<T> =
        lock.withLock {
            delegate.toList()
        }
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
