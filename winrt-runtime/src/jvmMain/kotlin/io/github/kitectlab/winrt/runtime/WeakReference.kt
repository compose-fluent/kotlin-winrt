package io.github.kitectlab.winrt.runtime

internal actual class PlatformManagedWeakReference<T : Any> actual constructor(target: T?) {
    private var delegate = java.lang.ref.WeakReference(target)

    actual fun get(): T? = delegate.get()

    actual fun set(target: T?) {
        delegate = java.lang.ref.WeakReference(target)
    }
}

internal actual class PlatformLock actual constructor() {
    private val monitor = Any()

    actual fun <R> withLock(block: () -> R): R = synchronized(monitor) { block() }
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
        return unwrapped.use {
            val weakReferenceSource = unwrapped.queryInterface(IID.IWeakReferenceSource).getOrNull() ?: return null
            weakReferenceSource.use {
                WeakReferenceSourceReference(it.pointer, IID.IWeakReferenceSource)
                    .getWeakReference()
                    ?.let(::NativeWeakReferenceHandle)
            }
        }
    }

    actual fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer)
        }
}
