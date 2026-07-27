package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import windows.foundation.EventRegistrationToken

/**
 * Tracks native event subscriptions whose callbacks are backed by JVM FFM upcall stubs.
 *
 * A successful add transfers delegate ownership to the native publisher. This registry is only a weak,
 * best-effort process-shutdown safeguard: if WinUI keeps a registration alive while the JVM is exiting,
 * a late native callback can enter an FFM upcall stub after thread attachment is no longer possible.
 */
internal object EventSourceShutdownRegistry {
    private val lock = PlatformLock()
    private val registrations = mutableMapOf<Long, Registration>()
    private var nextRegistrationId = 1L
    private val shutdownHook = PlatformProcessHooks.registerShutdownHook(::closeAll)

    fun registerCallback(
        objectReference: ComObjectReference,
        state: WeakReference<Any>,
        token: EventRegistrationToken,
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
    ): AutoCloseable =
        register(
            objectReference = objectReference,
            state = state,
            token = token,
            removal = CallbackRemoval(removeHandler),
        )

    fun registerVtable(
        objectReference: ComObjectReference,
        state: WeakReference<Any>,
        token: EventRegistrationToken,
        removeHandlerSlot: Int,
    ): AutoCloseable =
        register(
            objectReference = objectReference,
            state = state,
            token = token,
            removal = VtableRemoval(removeHandlerSlot),
        )

    private fun register(
        objectReference: ComObjectReference,
        state: WeakReference<Any>,
        token: EventRegistrationToken,
        removal: Removal,
    ): AutoCloseable {
        val publisher = PublisherReference(objectReference)
        val registration =
            try {
                lock.withLock {
                    val id = nextRegistrationId++
                    Registration(
                        id = id,
                        publisher = publisher,
                        state = state,
                        token = token,
                        removal = removal,
                    ).also { registration ->
                        registrations[id] = registration
                    }
                }
            } catch (error: Throwable) {
                publisher.close()
                throw error
            }
        return AutoCloseable {
            unregister(registration)
        }
    }

    internal fun closeAllActiveRegistrations() {
        closeAll()
    }

    internal fun closeAllForTests() {
        closeAllActiveRegistrations()
    }

    internal fun clearForTests() {
        val snapshot =
            lock.withLock {
                registrations.values.toList().also {
                    registrations.clear()
                    nextRegistrationId = 1L
                }
            }
        snapshot.forEach { registration ->
            registration.unregister()
        }
    }

    private fun unregister(registration: Registration) {
        lock.withLock {
            if (registrations[registration.id] === registration) {
                registrations.remove(registration.id)
            }
        }
        registration.unregister()
    }

    private fun closeAll() {
        val snapshot =
            lock.withLock {
                registrations.values.toList().also {
                    registrations.clear()
                }
            }
        snapshot.forEach { registration ->
            registration.closeForShutdown()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class Registration(
        val id: Long,
        private val publisher: PublisherReference,
        private val state: WeakReference<Any>,
        private val token: EventRegistrationToken,
        private val removal: Removal,
    ) {
        private val closed = AtomicInt(0)

        fun unregister() {
            if (closed.compareAndSet(0, 1)) {
                publisher.close()
            }
        }

        fun closeForShutdown() {
            if (!closed.compareAndSet(0, 1)) {
                return
            }
            try {
                runCatching {
                    val resolvedState = state.tryGetTarget() as? EventSourceState<*> ?: return@runCatching
                    try {
                        publisher.withResolvedReference { objectReference ->
                            removal.remove(objectReference, token)
                        }
                    } finally {
                        resolvedState.close()
                    }
                }
            } finally {
                publisher.close()
            }
        }
    }

    private class PublisherReference(
        objectReference: ComObjectReference,
    ) : AutoCloseable {
        private val managedReference = PlatformManagedWeakReference(objectReference)
        private val interfaceId = objectReference.interfaceId
        private val nativeWeakReference =
            runCatching {
                objectReference.tryGetWeakReference()
            }.getOrNull()

        fun withResolvedReference(action: (ComObjectReference) -> Unit) {
            managedReference.get()?.takeUnless { it.isDisposed }?.let { resolved ->
                action(resolved)
                return
            }
            val resolved =
                runCatching {
                    nativeWeakReference?.resolve(interfaceId)
                }.getOrNull() ?: return
            resolved.use(action)
        }

        override fun close() {
            nativeWeakReference?.close()
        }
    }

    private interface Removal {
        fun remove(
            objectReference: ComObjectReference,
            token: EventRegistrationToken,
        )
    }

    private class CallbackRemoval(
        removeHandler: (ComObjectReference, EventRegistrationToken) -> Unit,
    ) : Removal {
        private val removeHandler = PlatformManagedWeakReference(removeHandler)

        override fun remove(
            objectReference: ComObjectReference,
            token: EventRegistrationToken,
        ) {
            removeHandler.get()?.invoke(objectReference, token)
        }
    }

    private class VtableRemoval(
        private val removeHandlerSlot: Int,
    ) : Removal {
        override fun remove(
            objectReference: ComObjectReference,
            token: EventRegistrationToken,
        ) {
            StandardDelegates.removeEventHandler(objectReference, removeHandlerSlot, token)
        }
    }
}
