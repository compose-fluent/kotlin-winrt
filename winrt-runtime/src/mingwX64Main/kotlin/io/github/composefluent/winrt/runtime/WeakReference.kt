@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.native.ref.WeakReference as NativeManagedWeakReference

internal actual class PlatformManagedWeakReference<T : Any> actual constructor(target: T?) {
    private var delegate: NativeManagedWeakReference<T>? = target?.let(::NativeManagedWeakReference)

    actual fun get(): T? = delegate?.get()

    actual fun set(target: T?) {
        delegate = target?.let(::NativeManagedWeakReference)
    }
}

internal actual class PlatformLock actual constructor() {
    actual fun <R> withLock(block: () -> R): R = block()
}

internal actual class NativeWeakReferenceHandle : AutoCloseable {
    actual override fun close() {}
}

internal actual object WeakReferenceInterop {
    actual fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle? = null

    actual fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? = null
}
