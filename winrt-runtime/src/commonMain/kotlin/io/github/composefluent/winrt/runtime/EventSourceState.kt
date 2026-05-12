package io.github.composefluent.winrt.runtime

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
    private var referenceTrackerTargetPointer: RawAddress = PlatformAbi.nullPointer

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

    internal fun initializeReferenceTracking(pointer: RawAddress) {
        val trackerTarget = queryReferenceTrackerTarget(pointer)
        lock.withLock {
            eventInvokePointer = pointer
            referenceTrackerTargetPointer = trackerTarget
        }
    }

    internal fun hasComReferences(): Boolean {
        val pointers =
            lock.withLock {
                eventInvokePointer to referenceTrackerTargetPointer
            }
        val eventPointer = pointers.first
        if (!PlatformAbi.isNull(eventPointer)) {
            WinRtPlatformApi.addRefRaw(eventPointer)
            val countAfterRelease = WinRtPlatformApi.releaseRaw(eventPointer)
            if (countAfterRelease > managedReferenceCount) {
                return true
            }
        }

        val trackerTargetPointer = pointers.second
        if (!PlatformAbi.isNull(trackerTargetPointer)) {
            ComVtableInvoker.invoke(
                trackerTargetPointer.asRawComPtr(),
                ReferenceTrackerTargetVftblSlots.AddRefFromReferenceTracker,
            )
            val countAfterRelease = ComVtableInvoker.invoke(
                trackerTargetPointer.asRawComPtr(),
                ReferenceTrackerTargetVftblSlots.ReleaseFromReferenceTracker,
            ).toUInt()
            if (countAfterRelease != 0u) {
                return true
            }
        }
        return false
    }

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
                eventInvokePointer = PlatformAbi.nullPointer
                referenceTrackerTargetPointer = PlatformAbi.nullPointer
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
        private val managedReferenceCount = 1u

        private fun queryReferenceTrackerTarget(pointer: RawAddress): RawAddress {
            if (PlatformAbi.isNull(pointer)) {
                return PlatformAbi.nullPointer
            }
            val result = WinRtPlatformApi.queryInterfaceRaw(pointer, IID.IReferenceTrackerTarget)
            if (result.hResultValue != KnownHResults.S_OK.value || PlatformAbi.isNull(result.pointer)) {
                return PlatformAbi.nullPointer
            }
            WinRtPlatformApi.releaseRaw(result.pointer)
            return result.pointer
        }
    }
}
