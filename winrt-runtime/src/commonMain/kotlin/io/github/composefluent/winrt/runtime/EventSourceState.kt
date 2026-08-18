package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import windows.foundation.EventRegistrationToken

/**
 * Immutable handler state published at registration/removal time.
 *
 * The one-handler representation is deliberately separate from the ordered list used for
 * multicast dispatch. Generated and runtime-owned event invokes can therefore call the common
 * single-handler path without constructing an iterator on every callback.
 */
@PublishedApi
internal class EventSourceHandlerSnapshot<T : Any>(
    @PublishedApi
    internal val singleHandler: T? = null,
    @PublishedApi
    internal val manyHandlers: List<T>? = null,
)

/**
 * Kotlin event-state owner corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventSourceState{TDelegate}.cs`.
 *
 * `.cswinrt` stores a combined CLR multicast delegate. Kotlin does not have a matching
 * `MulticastDelegate` abstraction, so this owner keeps an ordered immutable handler list
 * and lets subclasses expose an event-invoke delegate that iterates over snapshots.
 */
@OptIn(ExperimentalAtomicApi::class)
abstract class EventSourceState<T : Any> protected constructor(
    thisPtr: RawAddress,
    private val index: Int,
) : AutoCloseable {
    private val lock = PlatformLock()
    private val objectPointerKey = PlatformAbi.pointerKey(thisPtr)
    private val cacheEntry = WeakReference<Any>(this)
    private val cacheCleanupRegistration = finalizationHook.register(this, CacheCleanup(objectPointerKey, index, cacheEntry)::run)
    private var disposed = false
    @PublishedApi
    internal val handlerSnapshot = AtomicReference(EventSourceHandlerSnapshot<T>())
    private var eventInvokePointer: RawAddress = PlatformAbi.nullPointer
    private var managedReferenceCount = 1u
    private var shutdownRegistration: AutoCloseable? = null

    internal var token: EventRegistrationToken = EventRegistrationToken()
    internal var eventInvokeHandle: WinRTDelegateHandle? = null
    internal val eventInvoke: T by lazy(LazyThreadSafetyMode.NONE, ::createEventInvoke)

    protected abstract fun createEventInvoke(): T

    /**
     * Compatibility view for older hand-written subclasses. New generated/runtime-owned event
     * invokes use [forEachHandler] so the common one-handler branch remains iterator-free.
     */
    protected fun snapshotHandlers(): List<T> {
        val snapshot = handlerSnapshot.load()
        snapshot.manyHandlers?.let { return it }
        snapshot.singleHandler?.let { return listOf(it) }
        return emptyList()
    }

    /**
     * Traverses one immutable event snapshot. The inline body is intentionally shared by
     * generated intrinsic and runtime-owned call sites so their hot dispatch shape stays equal.
     */
    protected inline fun forEachHandler(action: (T) -> Unit) {
        val snapshot = handlerSnapshot.load()
        val singleHandler = snapshot.singleHandler
        if (singleHandler != null) {
            action(singleHandler)
            return
        }

        val manyHandlers = snapshot.manyHandlers ?: return
        val count = manyHandlers.size
        var handlerIndex = 0
        while (handlerIndex < count) {
            action(manyHandlers[handlerIndex])
            handlerIndex += 1
        }
    }

    internal fun addHandler(handler: T) {
        lock.withLock {
            val current = handlerSnapshot.load()
            val next =
                current.singleHandler?.let { singleHandler ->
                    EventSourceHandlerSnapshot(manyHandlers = listOf(singleHandler, handler))
                } ?: current.manyHandlers?.let { manyHandlers ->
                    EventSourceHandlerSnapshot(manyHandlers = manyHandlers + handler)
                } ?: EventSourceHandlerSnapshot(singleHandler = handler)
            handlerSnapshot.store(next)
        }
    }

    internal fun removeHandler(handler: T): Boolean {
        var removed = false
        lock.withLock {
            val current = handlerSnapshot.load()
            val singleHandler = current.singleHandler
            if (singleHandler != null) {
                if (singleHandler == handler) {
                    handlerSnapshot.store(EventSourceHandlerSnapshot())
                    removed = true
                }
            } else {
                val manyHandlers = current.manyHandlers
                if (manyHandlers != null) {
                    val index = manyHandlers.indexOfLast { it == handler }
                    if (index >= 0) {
                        if (manyHandlers.size == 2) {
                            val remaining = if (index == 0) manyHandlers[1] else manyHandlers[0]
                            handlerSnapshot.store(EventSourceHandlerSnapshot(singleHandler = remaining))
                        } else {
                            handlerSnapshot.store(
                                EventSourceHandlerSnapshot(
                                    manyHandlers = buildList(manyHandlers.size - 1) {
                                        manyHandlers.forEachIndexed { handlerIndex, value ->
                                            if (handlerIndex != index) {
                                                add(value)
                                            }
                                        }
                                    },
                                ),
                            )
                        }
                        removed = true
                    }
                }
            }
        }
        return removed
    }

    internal fun hasHandlers(): Boolean {
        val snapshot = handlerSnapshot.load()
        return snapshot.singleHandler != null || snapshot.manyHandlers?.isNotEmpty() == true
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
                handlerSnapshot.store(EventSourceHandlerSnapshot())
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
