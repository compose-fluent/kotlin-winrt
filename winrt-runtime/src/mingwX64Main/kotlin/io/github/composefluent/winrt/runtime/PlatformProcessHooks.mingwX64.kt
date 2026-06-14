@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.composefluent.winrt.runtime

import kotlinx.cinterop.staticCFunction
import platform.posix.atexit

internal actual object PlatformProcessHooks {
    actual fun registerShutdownHook(cleanup: () -> Unit): AutoCloseable? =
        PlatformProcessShutdownHooks.register(cleanup)
}

private object PlatformProcessShutdownHooks {
    private val lock = PlatformLock()
    private val cleanups = linkedMapOf<Long, () -> Unit>()
    private var nextId = 1L
    private var processHookRegistered = false

    fun register(cleanup: () -> Unit): AutoCloseable? {
        val id = lock.withLock {
            if (!processHookRegistered) {
                if (atexit(staticCFunction(::runRegisteredShutdownHooks)) != 0) {
                    null
                } else {
                    processHookRegistered = true
                    nextId.also { registeredId ->
                        nextId += 1
                        cleanups[registeredId] = cleanup
                    }
                }
            } else {
                nextId.also { registeredId ->
                    nextId += 1
                    cleanups[registeredId] = cleanup
                }
            }
        } ?: return null
        return AutoCloseable {
            lock.withLock {
                cleanups.remove(id)
            }
        }
    }

    fun runAll() {
        val snapshot = lock.withLock {
            cleanups.values.toList().also {
                cleanups.clear()
            }
        }
        snapshot.forEach { cleanup ->
            runCatching {
                cleanup()
            }
        }
    }
}

private fun runRegisteredShutdownHooks() {
    PlatformProcessShutdownHooks.runAll()
}
