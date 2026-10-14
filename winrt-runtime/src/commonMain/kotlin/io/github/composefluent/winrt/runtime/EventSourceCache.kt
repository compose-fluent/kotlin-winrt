package io.github.composefluent.winrt.runtime

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
        state: PlatformManagedWeakReference<Any>,
    ) {
        val target = obj.comPtr.tryWithQueryInterfacePointer(IID.IWeakReferenceSource, ::getWeakReferenceFromSource)
            ?: return
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
    ): PlatformManagedWeakReference<Any>? {
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

    /**
     * Resolves the cached publisher only when shutdown cleanup actually runs.
     *
     * Event registration already acquired and owns the publisher weak reference here. Keeping
     * shutdown bookkeeping keyed to this cache avoids repeating the weak-reference QI on every
     * subscription while preserving the ability to recover after the projected wrapper is gone.
     */
    fun resolveTarget(
        objectPointerKey: Long,
        interfaceId: Guid,
    ): ComObjectReference? =
        lock.withLock {
            caches[objectPointerKey]?.resolveTarget(interfaceId)
        }

    /**
     * Whether the cached publisher of [objectPointerKey] can be resolved, and released, on the
     * calling thread. A publisher without an entry needs no call at all.
     */
    fun isTargetCallableInCurrentContext(objectPointerKey: Long): Boolean =
        lock.withLock {
            caches[objectPointerKey]?.isTargetCallableInCurrentContext ?: true
        }

    fun remove(
        objectPointerKey: Long,
        index: Int,
        state: PlatformManagedWeakReference<Any>,
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
        private val states = mutableMapOf<Int, PlatformManagedWeakReference<Any>>()

        val isEmpty: Boolean
            get() = states.isEmpty()

        fun getState(index: Int): PlatformManagedWeakReference<Any>? = states[index]

        fun setState(
            index: Int,
            state: PlatformManagedWeakReference<Any>,
        ) {
            states[index] = state
        }

        fun removeState(
            index: Int,
            state: PlatformManagedWeakReference<Any>,
        ) {
            if (states[index] === state) {
                states.remove(index)
            }
        }

        val isTargetCallableInCurrentContext: Boolean
            get() = target.isCallableInCurrentContext

        fun isTargetAlive(): Boolean = target.isTargetAlive()

        fun resolveTarget(interfaceId: Guid): ComObjectReference? =
            runCatching { target.resolve(interfaceId) }.getOrNull()

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
