package io.github.composefluent.winrt.runtime

/**
 * KMP equivalent of `.cswinrt/src/WinRT.Runtime/WeakReference.netstandard2.0.cs`.
 *
 * The public weak-reference owner is common. Managed weak-reference storage and
 * native weak-reference interop remain platform-owned behind explicit seams.
 */
class WeakReference<T : Any>(
    target: T? = null,
) {
    private val ownerState = WeakReferenceOwnerState(target)

    @Suppress("unused")
    private val finalizationRegistration = ownerState.registerFinalization(this)

    fun setTarget(target: T?) = ownerState.setTarget(target)

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? = ownerState.tryGetTarget()
}

private val weakReferenceFinalizationHook = FinalizationHook()

private class WeakReferenceOwnerState<T : Any>(
    target: T?,
) {
    private val lock = PlatformLock()
    private val managedWeakReference = PlatformManagedWeakReference(target)
    private var nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)
    private var closed = false

    fun registerFinalization(target: Any): AutoCloseable =
        weakReferenceFinalizationHook.register(target) { close() }

    fun setTarget(target: T?) {
        val previous = lock.withLock {
            if (closed) {
                null
            } else {
                managedWeakReference.set(target)
                nativeWeakReference.also {
                    nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)
                }
            }
        }
        previous?.close()
    }

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? =
        lock.withLock {
            if (closed) {
                return@withLock null
            }
            managedWeakReference.get()?.let { return@withLock it }
            val resolved = nativeWeakReference
                ?.let(WeakReferenceInterop::resolveNativeWeakReference) as? T
            if (resolved != null) {
                managedWeakReference.set(resolved)
            }
            resolved
        }

    private fun close() {
        val current = lock.withLock {
            if (closed) {
                null
            } else {
                closed = true
                managedWeakReference.set(null)
                nativeWeakReference.also { nativeWeakReference = null }
            }
        }
        current?.close()
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
