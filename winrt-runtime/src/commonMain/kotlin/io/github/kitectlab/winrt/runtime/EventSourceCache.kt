package io.github.kitectlab.winrt.runtime

/**
 * Shared event-source cache corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventSourceCache.cs`.
 *
 * The cache is keyed by the event owner's COM pointer and event index. As in `.cswinrt`,
 * entries are only created for objects that expose `IWeakReferenceSource`, so unsubscription
 * can survive wrapper churn without forcing a strong event-source lifetime.
 */
internal object EventSourceCache {
    private val lock = PlatformLock()
    private val caches = ConcurrentCacheMap<Long, CacheEntry>()

    fun create(
        obj: ComObjectReference,
        index: Int,
        state: WeakReference<Any>,
    ) {
        val target = obj.tryGetWeakReference() ?: return
        val cacheKey = PlatformAbi.pointerKey(obj.pointer)
        val staleTarget: WeakReferenceReference? =
            lock.withLock {
            val existing = caches[cacheKey]
            if (existing == null) {
                caches[cacheKey] = CacheEntry(target).also { it.setState(index, state) }
                null
            } else {
                val stale = existing.updateIfDead(target)
                existing.setState(index, state)
                stale
            }
            }
        staleTarget?.close()
    }

    fun getState(
        obj: ComObjectReference,
        index: Int,
    ): WeakReference<Any>? {
        val cacheKey = PlatformAbi.pointerKey(obj.pointer)
        return lock.withLock {
            val entry = caches[cacheKey] ?: return@withLock null
            if (!entry.isTargetAlive()) {
                caches.remove(cacheKey)?.close()
                return@withLock null
            }
            entry.getState(index)
        }
    }

    fun remove(
        objectPointerKey: Long,
        index: Int,
        state: WeakReference<Any>,
    ) {
        val staleTarget = lock.withLock {
            val entry = caches[objectPointerKey] ?: return@withLock null
            entry.removeState(index, state)
            if (entry.isEmpty) {
                caches.remove(objectPointerKey)
            } else {
                null
            }
        }
        staleTarget?.close()
    }

    internal fun clearForTests() {
        val entries = lock.withLock {
            val existing = caches.values.toList()
            caches.clear()
            existing
        }
        entries.forEach(CacheEntry::close)
    }

    private class CacheEntry(
        private var target: WeakReferenceReference,
    ) : AutoCloseable {
        private val states = mutableMapOf<Int, WeakReference<Any>>()

        val isEmpty: Boolean
            get() = states.isEmpty()

        fun getState(index: Int): WeakReference<Any>? = states[index]

        fun setState(
            index: Int,
            state: WeakReference<Any>,
        ) {
            states[index] = state
        }

        fun removeState(
            index: Int,
            state: WeakReference<Any>,
        ) {
            if (states[index] === state) {
                states.remove(index)
            }
        }

        fun isTargetAlive(): Boolean = target.resolve(IID.IUnknown)?.use { true } ?: false

        fun updateIfDead(newTarget: WeakReferenceReference): WeakReferenceReference? {
            if (isTargetAlive()) {
                return newTarget
            }
            val staleTarget = target
            target = newTarget
            states.clear()
            return staleTarget
        }

        override fun close() {
            target.close()
        }
    }
}
