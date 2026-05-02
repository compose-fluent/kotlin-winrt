package io.github.composefluent.winrt.runtime

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

expect class WeakValueCache<K, V : Any>() {
    operator fun get(key: K): V?

    operator fun set(
        key: K,
        value: V,
    )

    fun remove(key: K): V?

    fun clear()
}

expect class WeakKeyStateMap<K : Any, V : Any>() {
    operator fun get(key: K): V?

    fun getOrPut(
        key: K,
        defaultValue: () -> V,
    ): V

    fun clear()
}

expect class SnapshotList<T>() {
    fun add(value: T)

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
