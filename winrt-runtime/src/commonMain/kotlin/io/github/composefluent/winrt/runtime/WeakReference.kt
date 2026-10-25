package io.github.composefluent.winrt.runtime

/**
 * Combines a platform managed weak reference with a shared IWeakReference RCW.
 * CsWinRT 3 delegates RCW resurrection to ComWrappers; Kotlin keeps the equivalent
 * managed-first lookup and native fallback here, with platform-owned weak storage.
 */
class WeakReference<T : Any>(
    target: T? = null,
) {
    private val lock = PlatformWeakReferenceLock()
    private var managedWeakReference = target?.let(::PlatformManagedWeakReference)
    private var nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)

    fun setTarget(target: T?) {
        val previous = lock.withWeakReferenceLock {
            managedWeakReference = target?.let(::PlatformManagedWeakReference)
            nativeWeakReference.also {
                nativeWeakReference = target?.let(WeakReferenceInterop::tryCreateNativeWeakReference)
            }
        }
        previous?.close()
    }

    /** CsWinRT EventSourceCache resolves IUnknown directly for identity/liveness.
     * Comparing a live projection does not require resurrecting another typed RCW. */
    internal fun matchesTarget(target: Any): Boolean = lock.withWeakReferenceLock {
        if (managedWeakReference?.get() === target) return@withWeakReferenceLock true
        val current = ComWrappersSupport.tryUnwrapObject(target) ?: return@withWeakReferenceLock false
        current.use { reference ->
            nativeWeakReference?.resolve(IID.IUnknown)?.use { it.sameIdentity(reference) } == true
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun tryGetTarget(): T? =
        lock.withWeakReferenceLock {
            managedWeakReference?.get()?.let { return@withWeakReferenceLock it }
            val resolved = nativeWeakReference
                ?.let(WeakReferenceInterop::resolveNativeWeakReference) as? T
            if (resolved != null) {
                managedWeakReference = PlatformManagedWeakReference(resolved)
            }
            resolved
        }

}

// CsWinRT 3 ManagedWeakReference stores the platform weak reference directly.
// Mutability belongs to the owner; immutable handles need no extra wrapper on either target.
internal expect class PlatformManagedWeakReference<T : Any>(referred: T) {
    fun get(): T?
}

internal expect class PlatformLock() {
    fun enter()

    fun exit()
}

/** Allocates a platform lock only when a weak-reference owner is first read or retargeted. */
internal class PlatformWeakReferenceLock {
    @kotlin.concurrent.Volatile
    private var delegate: PlatformLock? = null

    fun enter() {
        (delegate ?: initialize()).enter()
    }

    fun exit() {
        checkNotNull(delegate).exit()
    }

    private fun initialize(): PlatformLock = Initialization.lock.withLock {
        delegate ?: PlatformLock().also { delegate = it }
    }

    private object Initialization {
        val lock = PlatformLock()
    }
}

internal inline fun <R> PlatformLock.withLock(block: () -> R): R {
    enter()
    try {
        return block()
    } finally {
        exit()
    }
}

internal inline fun <R> PlatformWeakReferenceLock.withWeakReferenceLock(block: () -> R): R {
    enter()
    try {
        return block()
    } finally {
        exit()
    }
}

internal class NativeWeakReferenceHandle internal constructor(
    owner: IWinRTObject,
) : AutoCloseable {
    // CsWinRT WeakReference stores a shared IWeakReference RCW. This handle owns only
    // its managed reference; the RCW's ComPtr releases after its last managed owner dies.
    private var sharedOwner: IWinRTObject? = owner

    fun resolve(interfaceId: Guid): IUnknownReference? =
        sharedOwner?.resolveProjectedWeakReference(interfaceId)

    override fun close() {
        sharedOwner = null
    }
}

internal object WeakReferenceInterop {
    fun tryCreateNativeWeakReference(target: Any): NativeWeakReferenceHandle? {
        val winrtObject = target as? IWinRTObject
        if (winrtObject?.hasUnwrappableNativeObject == true) {
            return try {
                winrtObject.nativeObject.tryGetProjectedWeakReference()?.let(::NativeWeakReferenceHandle)
            } finally {
                winRTKeepAlive(target)
            }
        }

        val unwrapped = ComWrappersSupport.tryUnwrapObject(target) ?: return null
        return unwrapped.use { reference ->
            reference.tryGetProjectedWeakReference()?.let(::NativeWeakReferenceHandle)
        }
    }

    fun resolveNativeWeakReference(reference: NativeWeakReferenceHandle): Any? =
        reference.resolve(IID.IUnknown)?.use { resolved ->
            ComWrappersSupport.createRcwForComObject(resolved.pointer.asRawAddress())
        }
}
