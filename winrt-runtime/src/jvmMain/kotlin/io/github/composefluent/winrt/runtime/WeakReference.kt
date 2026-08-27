package io.github.composefluent.winrt.runtime

import java.util.concurrent.locks.ReentrantLock

internal actual class PlatformManagedWeakReference<T : Any> actual constructor(target: T?) {
    private var delegate = java.lang.ref.WeakReference(target)

    actual fun get(): T? = delegate.get()

    actual fun set(target: T?) {
        delegate = java.lang.ref.WeakReference(target)
    }
}

internal actual class PlatformLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun enter() {
        lock.lock()
    }

    actual fun exit() {
        lock.unlock()
    }
}
