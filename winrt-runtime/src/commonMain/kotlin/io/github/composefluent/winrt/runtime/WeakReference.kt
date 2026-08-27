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

    fun setTarget(target: T?) = ownerState.setTarget(target)

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? = ownerState.tryGetTarget()
}

private class WeakReferenceOwnerState<T : Any>(
    target: T?,
) {
    private val lock = PlatformLock()
    private val managedWeakReference = PlatformManagedWeakReference(target)
    private var nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)

    fun setTarget(target: T?) {
        val previous = lock.withLock {
            managedWeakReference.set(target)
            nativeWeakReference.also {
                nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)
            }
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
    fun enter()

    fun exit()
}

internal inline fun <R> PlatformLock.withLock(block: () -> R): R {
    enter()
    try {
        return block()
    } finally {
        exit()
    }
}

internal class NativeWeakReferenceHandle internal constructor(
    internal val reference: WeakReferenceReference,
) : AutoCloseable {
    override fun close() {
        reference.close()
    }
}

internal object WeakReferenceInterop {
    fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle? {
        val winrtObject = target as? IWinRTObject
        if (winrtObject?.hasUnwrappableNativeObject == true) {
            return try {
                winrtObject.nativeObject.tryGetWeakReference()?.let(::NativeWeakReferenceHandle)
            } finally {
                winRTKeepAlive(target)
            }
        }

        val unwrapped = ComWrappersSupport.tryUnwrapObject(target) ?: return null
        return unwrapped.use { reference ->
            reference.tryGetWeakReference()?.let(::NativeWeakReferenceHandle)
        }
    }

    fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asRawAddress())
        }
}
