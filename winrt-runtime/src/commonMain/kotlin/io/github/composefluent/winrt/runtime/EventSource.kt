package io.github.composefluent.winrt.runtime

import windows.foundation.EventRegistrationToken

/**
 * Common event-source owner corresponding to `.cswinrt/src/WinRT.Runtime/Interop/EventSource{TDelegate}.cs`.
 *
 * Kotlin keeps the same ownership split and cache/token behavior as `.cswinrt`, while the
 * CLR-specific function-pointer and multicast-delegate details are narrowed to explicit
 * Kotlin lambdas plus `WinRTDelegateHandle`.
 */
abstract class EventSource<T : Any> protected constructor(
    private val objectReference: ComObjectReference,
    private val addHandler: (ComObjectReference, ComObjectReference) -> EventRegistrationToken,
    private val removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
    private val index: Int = 0,
) {
    private val lock = PlatformLock()
    private var state: WeakReference<Any>? = EventSourceCache.getState(objectReference, index)

    protected constructor(
        objectReference: ComObjectReference,
        vtableIndexForAddHandler: Int,
    ) : this(
        objectReference = objectReference,
        addHandler = { reference, handler ->
            StandardDelegates.addEventHandler(reference, vtableIndexForAddHandler, handler)
        },
        removeHandler = { reference, token ->
            StandardDelegates.removeEventHandler(reference, vtableIndexForAddHandler + 1, token)
        },
        index = vtableIndexForAddHandler,
    )

    protected val nativeObjectReference: ComObjectReference
        get() = objectReference

    protected val eventIndex: Int
        get() = index

    protected abstract fun createMarshaler(handler: T): WinRTDelegateHandle

    protected abstract fun createEventSourceState(): EventSourceState<T>

    fun subscribe(handler: T) {
        lock.withLock {
            var state = getStateUnsafe()
            val shouldRegister = state == null || !state.hasComReferences()
            if (shouldRegister) {
                state?.close()
                state = createEventSourceState()
            }
            state.addHandler(handler)
            if (!shouldRegister) {
                this.state = state.getWeakReferenceForCache()
                return@withLock
            }
            val eventInvokeHandle = createMarshaler(state.eventInvoke)
            try {
                eventInvokeHandle.createReference().use { reference ->
                    state.initializeReferenceTracking(PlatformAbi.fromRawComPtr(reference.pointer))
                    state.token = addHandler(objectReference, reference)
                }
                state.eventInvokeHandle = eventInvokeHandle
                state.installShutdownRegistration(
                    EventSourceShutdownRegistry.register {
                        try {
                            removeHandler(objectReference, state.token)
                        } finally {
                            state.close()
                        }
                    },
                )
                val stateReference = state.getWeakReferenceForCache()
                this.state = stateReference
                EventSourceCache.create(objectReference, index, stateReference)
            } catch (error: Throwable) {
                eventInvokeHandle.close()
                state.close()
                this.state = null
                throw error
            }
        }
    }

    fun unsubscribe(handler: T) {
        lock.withLock {
            val resolvedState = getStateUnsafe() ?: return@withLock
            if (!resolvedState.removeHandler(handler) || resolvedState.hasHandlers()) {
                this.state = resolvedState.getWeakReferenceForCache()
                return@withLock
            }
            removeHandler(objectReference, resolvedState.token)
            resolvedState.clearShutdownRegistration()
            resolvedState.close()
            this.state = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getStateUnsafe(): EventSourceState<T>? {
        val local = state?.tryGetTarget() as? EventSourceState<T>
        if (local != null) {
            return local
        }
        val cached = EventSourceCache.getState(objectReference, index)
        state = cached
        return cached?.tryGetTarget() as? EventSourceState<T>
    }
}
