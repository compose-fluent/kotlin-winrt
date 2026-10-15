package io.github.composefluent.winrt.runtime

import java.util.concurrent.locks.ReentrantLock

internal actual typealias PlatformManagedWeakReference<T> = java.lang.ref.WeakReference<T>

internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun enter() {
        lock.lock()
    }

    actual fun exit() {
        lock.unlock()
    }
}
