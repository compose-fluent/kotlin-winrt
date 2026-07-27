package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken

/**
 * Kotlin event-state owner corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventSourceState{TDelegate}.cs`.
 *
 * `.cswinrt` stores a combined CLR multicast delegate. Kotlin does not have a matching
 * `MulticastDelegate` abstraction, so this owner keeps an ordered immutable handler list
 * and lets subclasses expose an event-invoke delegate that iterates over snapshots.
 */
abstract class EventSourceState<T : Any> protected constructor(
    thisPtr: RawAddress,
    private val index: Int,
) : AutoCloseable {
    private val lock = PlatformLock()
    private val objectPointerKey = PlatformAbi.pointerKey(thisPtr)
    private val cacheEntry = WeakReference<Any>(this)
    private val cacheCleanupRegistration = finalizationHook.register(this, CacheCleanup(objectPointerKey, index, cacheEntry)::run)
    private var disposed = false
    private var handlers: List<T> = emptyList()
    private var eventInvokePointer: RawAddress = PlatformAbi.nullPointer
    private var managedReferenceCount = 1u
    private var shutdownRegistration: AutoCloseable? = null

    internal var token: EventRegistrationToken = EventRegistrationToken()
    internal var eventInvokeHandle: WinRTDelegateHandle? = null
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

    internal fun initializeReferenceTracking(pointer: RawAddress) {
        lock.withLock {
            eventInvokePointer = pointer
        }
    }

    internal fun transferDelegateToNativeOwnership() {
        val handle =
            lock.withLock {
                managedReferenceCount = 0u
                eventInvokeHandle
            } ?: return
        handle.releaseManagedReferenceForNativeOwnership()
    }

    internal fun installShutdownRegistration(registration: AutoCloseable) {
        val previous =
            lock.withLock {
                shutdownRegistration.also {
                    shutdownRegistration = registration
                }
            }
        previous?.close()
    }

    internal fun clearShutdownRegistration() {
        val previous =
            lock.withLock {
                shutdownRegistration.also {
                    shutdownRegistration = null
                }
            }
        previous?.close()
    }

    internal fun hasComReferences(): Boolean {
        val reference =
            lock.withLock {
                eventInvokePointer to managedReferenceCount
            }
        if (PlatformAbi.isNull(reference.first)) {
            return false
        }

        // Kotlin tracker references also increment ManagedComHostState's normal COM count,
        // so one pinned managed-host probe covers both checks without dereferencing stale CCW pointers.
        val countAfterRelease = WinRTInspectableComObject.tryProbeReferenceCount(reference.first) ?: return false
        return countAfterRelease > reference.second
    }

    override fun close() {
        var alreadyDisposed = false
        val resourcesToClose =
            lock.withLock {
                if (disposed) {
                    alreadyDisposed = true
                    return@withLock null
                }
                disposed = true
                handlers = emptyList()
                EventSourceCache.remove(objectPointerKey, index, cacheEntry)
                cacheCleanupRegistration.close()
                eventInvokePointer = PlatformAbi.nullPointer
                val handle = eventInvokeHandle.also {
                    eventInvokeHandle = null
                }
                val registration = shutdownRegistration.also {
                    shutdownRegistration = null
                }
                handle to registration
            }
        if (alreadyDisposed) {
            return
        }
        resourcesToClose?.second?.close()
        resourcesToClose?.first?.close()
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
