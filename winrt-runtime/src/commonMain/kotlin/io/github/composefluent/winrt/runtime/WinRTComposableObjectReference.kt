package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
class WinRTComposableObjectReference internal constructor(
    val instance: IInspectableReference,
    val inner: IInspectableReference?,
    private val composed: IInspectableReference?,
    val outer: ComObjectReference,
    val isAggregatedReferenceTrackerObject: Boolean,
    private val outerHost: WinRTInspectableComObject,
    cleanup: () -> Unit,
    private val managedValue: Any? = null,
) : AutoCloseable {
    private val cleanupState = ComposableObjectCleanup(
        listOfNotNull(instance.comPtr.support, inner?.comPtr?.support, composed?.comPtr?.support, outer.comPtr.support).distinct(),
        cleanup,
    )
    private val finalizationRegistration = registerComposableObjectFinalizer(
        cleanupState, ActiveComposableObjectReferences.register(this),
    )

    internal fun tryCreateStaticCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler? {
        val abi = outerHost.tryAcquireReference(interfaceId, identityVerifiedManagedValue) ?: return null
        return WinRTProjectionMarshaler.managed(abi, outerHost)
    }

    /**
     * Gets an owned reference for an interface implemented by the composed native
     * identity.  Derived projected classes must use this path for public base
     * interfaces; the managed outer host only exposes the authored override
     * interfaces and is not a valid substitute for those interfaces.
     */
    internal fun tryCreateNativeCallMarshaler(interfaceId: Guid): WinRTProjectionMarshaler? {
        // CsWinRT exposes the factory's inner object as NativeObject for an
        // aggregated composable.  Query that identity first when a projected
        // value crosses an ABI boundary.
        val source = instance
        val result = source.queryInterface(interfaceId)
        return result.getOrNull()?.let(WinRTProjectionMarshaler::owned)
    }

    /**
     * Gets an owned reference for a public interface implemented by the composed
     * native identity.  This is the reference counterpart of
     * [tryCreateNativeCallMarshaler] for APIs that need to return a COM object
     * reference rather than a call marshaler.
     */
    internal fun tryCreateNativeCallReference(interfaceId: Guid): ComObjectReference? {
        // Public interfaces on an aggregated composable are obtained from the
        // factory's inner/native identity, matching CsWinRT NativeObject.
        val source = instance
        val result = source.queryInterface(interfaceId)
        return result.getOrNull()
    }

    @PublishedApi
    internal fun tryBorrowStaticCallAbi(interfaceId: Guid): RawAddress =
        // The host's cached table contains the authored override interfaces. For
        // inherited public interfaces (for example Layout on a custom
        // VirtualizingLayout), that table entry is the managed outer CCW and is
        // not the native composable instance that WinUI expects. Let the normal
        // unwrap/QI path resolve those interfaces from `instance` instead.
        if (interfaceId == outer.interfaceId) {
            outerHost.tryBorrowCachedInterfacePointer(interfaceId)
        } else {
            RawAddress.Null
        }

    override fun close() {
        try {
            cleanupState.close(fromFinalizer = false)
        } finally {
            try { finalizationRegistration.close() } finally { winRTKeepAlive(managedValue) }
        }
    }

    internal companion object {
        fun closeRuntimeReferences() {
            ActiveComposableObjectReferences.closeAll()
        }
    }
}

interface WinRTComposableObject {
    val winRTComposableObjectReference: WinRTComposableObjectReference?
}

private object ActiveComposableObjectReferences {
    private val lock = PlatformLock()
    private val references = mutableSetOf<PlatformManagedWeakReference<WinRTComposableObjectReference>>()

    fun register(reference: WinRTComposableObjectReference): PlatformManagedWeakReference<WinRTComposableObjectReference> =
        lock.withLock {
            references.removeAll { it.get() == null }
            PlatformManagedWeakReference(reference).also { references += it }
        }

    fun unregister(reference: PlatformManagedWeakReference<WinRTComposableObjectReference>) {
        lock.withLock {
            references -= reference
        }
    }

    fun closeAll() {
        val snapshot = lock.withLock {
            references.mapNotNull { it.get() }
        }
        closeAllAutoCloseables(snapshot.asReversed())
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class ComposableObjectCleanup(
    private val supports: List<RawComObjectReferenceSupport>,
    private val cleanup: () -> Unit,
) {
    private val closed = AtomicInt(0)

    fun close(fromFinalizer: Boolean) {
        if (!closed.compareAndSet(0, 1)) return
        var failure: Throwable? = null
        supports.forEach { support ->
            try {
                if (fromFinalizer) closeComPtrSupportFromFinalizer(support) else closeComPtrSupport(support)
            } catch (error: Throwable) {
                failure?.addSuppressed(error) ?: run { failure = error }
            }
        }
        try {
            cleanup()
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
        failure?.let { throw it }
    }
}

private val composableObjectFinalizationHook = FinalizationHook()

// This function's cleaner closure captures only native support states and a weak
// registry token. Capturing a property through `this` would root its own target.
private fun registerComposableObjectFinalizer(
    cleanup: ComposableObjectCleanup,
    registration: PlatformManagedWeakReference<WinRTComposableObjectReference>,
): AutoCloseable = composableObjectFinalizationHook.create {
    try {
        cleanup.close(fromFinalizer = true)
    } finally {
        ActiveComposableObjectReferences.unregister(registration)
    }
}

/** Closes every resource while preserving the first failure and later failures as suppressed. */
internal fun closeAllAutoCloseables(resources: Iterable<AutoCloseable>) {
    var failure: Throwable? = null
    resources.forEach { resource ->
        try {
            resource.close()
        } catch (error: Throwable) {
            failure?.addSuppressed(error) ?: run { failure = error }
        }
    }
    failure?.let { throw it }
}
