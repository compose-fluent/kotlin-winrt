package io.github.kitectlab.winrt.runtime

/**
 * KMP equivalent of `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs`.
 *
 * The public weak-reference owner is common. Managed weak-reference storage and
 * native weak-reference interop remain platform-owned behind explicit seams.
 */
class WeakReference<T : Any>(
    target: T? = null,
) {
    private val lock = PlatformLock()
    private val managedWeakReference = PlatformManagedWeakReference(target)
    private var nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)

    fun setTarget(target: T?) {
        val previous = lock.withLock {
            managedWeakReference.set(target)
            val old = nativeWeakReference
            nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)
            old
        }
        previous?.close()
    }

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? =
        lock.withLock {
            managedWeakReference.get()?.let { return@withLock it }
            val resolved = nativeWeakReference
                ?.let(WeakReferenceInterop::resolveNativeWeakReference) as? T
            if (resolved != null) {
                managedWeakReference.set(resolved)
            }
            resolved
        }
}

internal expect class PlatformManagedWeakReference<T : Any>(target: T? = null) {
    fun get(): T?

    fun set(target: T?)
}

internal expect class PlatformLock() {
    fun <R> withLock(block: () -> R): R
}

internal expect class NativeWeakReferenceHandle : AutoCloseable {
    override fun close()
}

internal expect object WeakReferenceInterop {
    fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle?

    fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any?
}
