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

    actual fun <R> withLock(block: () -> R): R {
        enter()
        try {
            return block()
        } finally {
            exit()
        }
    }
}

internal actual class NativeWeakReferenceHandle internal constructor(
    val reference: WeakReferenceReference,
) : AutoCloseable {
    actual override fun close() {
        reference.close()
    }
}

internal actual object WeakReferenceInterop {
    actual fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle? {
        val unwrapped = ComWrappersSupport.tryUnwrapObject(target) ?: return null
        return unwrapped.use { reference ->
            reference.tryGetWeakReference()?.let(::NativeWeakReferenceHandle)
        }
    }

    actual fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asRawAddress())
        }
}
