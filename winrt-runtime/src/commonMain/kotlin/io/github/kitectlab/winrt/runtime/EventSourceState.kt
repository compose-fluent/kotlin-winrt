package io.github.kitectlab.winrt.runtime

/**
 * Kotlin event-state owner corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventSourceState{TDelegate}.cs`.
 *
 * `.cswinrt` stores a combined CLR multicast delegate. Kotlin does not have a matching
 * `MulticastDelegate` abstraction, so this owner keeps an ordered immutable handler list
 * and lets subclasses expose an event-invoke delegate that iterates over snapshots.
 */
abstract class EventSourceState<T : Any> protected constructor(
    thisPtr: NativePointer,
    private val index: Int,
) : AutoCloseable {
    private val lock = PlatformLock()
    private val objectPointerKey = NativeInterop.pointerKey(thisPtr)
    private val cacheEntry = WeakReference<Any>(this)
    private val cacheCleanupRegistration = finalizationHook.register(this, CacheCleanup(objectPointerKey, index, cacheEntry)::run)
    private var disposed = false
    private var handlers: List<T> = emptyList()

    internal var token: EventRegistrationToken = EventRegistrationToken()
    internal var eventInvokeHandle: WinRtDelegateHandle? = null
    internal val eventInvoke: T by lazy(LazyThreadSafetyMode.NONE, ::createEventInvoke)

    protected abstract fun createEventInvoke(): T

    protected fun snapshotHandlers(): List<T> =
        lock.withLock {
            handlers
        }

    internal fun addHandler(handler: T) {
        lock.withLock {
            handlers = handlers + handler
        }
    }

    internal fun removeHandler(handler: T): Boolean {
        var removed = false
        lock.withLock {
            val index = handlers.indexOfLast { it == handler }
            if (index >= 0) {
                handlers =
                    buildList(handlers.size - 1) {
                        handlers.forEachIndexed { handlerIndex, value ->
                            if (handlerIndex != index) {
                                add(value)
                            }
                        }
                    }
                removed = true
            }
        }
        return removed
    }

    internal fun hasHandlers(): Boolean =
        lock.withLock {
            handlers.isNotEmpty()
        }

    internal fun getWeakReferenceForCache(): WeakReference<Any> = cacheEntry

    override fun close() {
        var alreadyDisposed = false
        val handleToClose =
            lock.withLock {
                if (disposed) {
                    alreadyDisposed = true
                    return@withLock null
                }
                disposed = true
                handlers = emptyList()
                EventSourceCache.remove(objectPointerKey, index, cacheEntry)
                cacheCleanupRegistration.close()
                eventInvokeHandle.also {
                    eventInvokeHandle = null
                }
            }
        if (alreadyDisposed) {
            return
        }
        handleToClose?.close()
    }

    private data class CacheCleanup(
        private val objectPointerKey: Long,
        private val index: Int,
        private val cacheEntry: WeakReference<Any>,
    ) {
        fun run() {
            EventSourceCache.remove(objectPointerKey, index, cacheEntry)
        }
    }

    companion object {
        private val finalizationHook = FinalizationHook()
    }
}
