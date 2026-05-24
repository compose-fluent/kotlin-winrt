package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Tracks native event subscriptions whose callbacks are backed by JVM FFM upcall stubs.
 *
 * The reference projection owns event registrations through EventSource/EventSourceState and removes the native
 * handler when the managed event source is torn down. Kotlin also needs a process-shutdown path:
 * if WinUI keeps a native event registration alive while the JVM is exiting, a late native callback
 * can enter an FFM upcall stub after thread attachment is no longer possible.
 */
internal object EventSourceShutdownRegistry {
    private val lock = PlatformLock()
    private val registrations = mutableMapOf<Long, Registration>()
    private var nextRegistrationId = 1L
    private val shutdownHook = PlatformProcessHooks.registerShutdownHook(::closeAll)

    fun register(closeNativeRegistration: () -> Unit): AutoCloseable {
        val registration =
            lock.withLock {
                val id = nextRegistrationId++
                Registration(id, closeNativeRegistration).also { registration ->
                    registrations[id] = registration
                }
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
        lock.withLock {
            registrations.clear()
            nextRegistrationId = 1L
        }
    }

    private fun unregister(registration: Registration) {
        lock.withLock {
            if (registrations[registration.id] === registration) {
                registrations.remove(registration.id)
            }
        }
    }

    private fun closeAll() {
        val snapshot =
            lock.withLock {
                registrations.values.toList().also {
                    registrations.clear()
                }
            }
        snapshot.forEach { registration ->
            registration.close()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class Registration(
        val id: Long,
        private val closeNativeRegistration: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicInt(0)

        override fun close() {
            if (closed.compareAndSet(0, 1)) {
                runCatching {
                    closeNativeRegistration()
                }
            }
        }
    }
}
