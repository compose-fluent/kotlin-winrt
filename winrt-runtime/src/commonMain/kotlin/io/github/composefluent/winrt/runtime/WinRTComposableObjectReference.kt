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
    private val cleanup: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicInt(0)

    init {
        ActiveComposableObjectReferences.register(this)
    }

    internal fun tryCreateStaticCallLease(
        interfaceId: Guid,
        identityVerifiedManagedValue: Any,
    ): WinRTProjectionMarshaler? {
        val abi = outerHost.tryAcquireReference(interfaceId, identityVerifiedManagedValue) ?: return null
        return WinRTProjectionMarshaler.managed(abi, outerHost)
    }

    @PublishedApi
    internal fun tryBorrowStaticCallAbi(interfaceId: Guid): RawAddress =
        outerHost.tryBorrowCachedInterfacePointer(interfaceId)

    override fun close() {
        if (!closed.compareAndSet(0, 1)) {
            return
        }
        try {
            instance.close()
        } finally {
            try {
                if (!isAggregatedReferenceTrackerObject && inner !== instance) {
                    inner?.close()
                }
            } finally {
                try {
                    composed?.close()
                } finally {
                    try {
                        outer.close()
                    } finally {
                        try {
                            cleanup()
                        } finally {
                            ActiveComposableObjectReferences.unregister(this)
                        }
                    }
                }
            }
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
    private val references = mutableSetOf<WinRTComposableObjectReference>()

    fun register(reference: WinRTComposableObjectReference) {
        lock.withLock {
            references += reference
        }
    }

    fun unregister(reference: WinRTComposableObjectReference) {
        lock.withLock {
            references -= reference
        }
    }

    fun closeAll() {
        val snapshot = lock.withLock {
            references.toList()
        }
        snapshot.asReversed().forEach { reference ->
            runCatching { reference.close() }
        }
    }
}
