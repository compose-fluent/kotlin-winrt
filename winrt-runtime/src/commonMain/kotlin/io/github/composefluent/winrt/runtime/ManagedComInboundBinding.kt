@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicReference

/**
 * Common managed state reached from every ABI interface exposed by one CCW.
 *
 * This is the Kotlin equivalent of CsWinRT's `ComInterfaceDispatch.GetInstance` binding:
 * generated inbound entry points recover the managed value directly, while compatibility
 * callbacks can reach the owning host without maintaining a second platform registry shape.
 */
@PublishedApi
internal class ManagedComInboundBinding(
    internal val host: WinRTInspectableComObject,
    value: Any?,
    internal val weak: Boolean,
    canonicalObjectMemory: RawAddress,
) : ManagedComRootReference {
    @PublishedApi
    internal val weakReference = value
        .takeIf { weak }
        ?.let(::PlatformManagedComInboundWeakReference)

    @PublishedApi
    @kotlin.concurrent.Volatile
    internal var strongValue = value.takeUnless { weak }
        private set

    private val platformHandle =
        platformCreateManagedComInboundBindingHandle(this, canonicalObjectMemory)

    @PublishedApi
    internal inline fun get(): Any? = strongValue ?: weakReference?.get()

    @PublishedApi
    internal fun publishHotEntry(thisWord: Long): Boolean {
        if (!host.state.tryBeginInboundCachePublication()) {
            return false
        }
        try {
            managedComInboundHotEntry.store(ManagedComInboundHotEntry(thisWord, this))
        } finally {
            host.state.endInboundCachePublication()
        }
        return true
    }

    private fun pin(): Boolean {
        if (!weak || strongValue != null) {
            return true
        }
        val value = weakReference?.get() ?: return false
        strongValue = value
        return true
    }

    private fun pinKnownValue(value: Any): Boolean {
        if (!weak) {
            return true
        }
        val pinnedValue = strongValue
        if (pinnedValue != null) {
            return pinnedValue === value
        }
        strongValue = value
        return true
    }

    override fun unpin() {
        if (weak) {
            strongValue = null
        }
    }

    override fun tryPin(knownManagedValue: Any?): Boolean =
        if (knownManagedValue == null) pin() else pinKnownValue(knownManagedValue)

    internal fun attach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView? = null,
        objectMemoryOffsetBytes: Long = 0L,
    ) {
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComInboundBindingSlot * Long.SIZE_BYTES.toLong(),
                platformHandle,
            )
        } else {
            PlatformAbi.writePointerAt(objectMemory, managedComInboundBindingSlot, platformHandle)
        }
    }

    internal fun detach(
        objectMemory: RawAddress,
        objectMemoryView: NativeMemoryView? = null,
        objectMemoryOffsetBytes: Long = 0L,
    ) {
        if (objectMemoryView != null) {
            objectMemoryView.writePointer(
                objectMemoryOffsetBytes + managedComInboundBindingSlot * Long.SIZE_BYTES.toLong(),
                PlatformAbi.nullPointer,
            )
        } else {
            PlatformAbi.writePointerAt(
                objectMemory,
                managedComInboundBindingSlot,
                PlatformAbi.nullPointer,
            )
        }
    }

    internal fun close() {
        weakReference?.close()
        platformDisposeManagedComInboundBindingHandle(this, platformHandle)
    }
}

@PublishedApi
internal class ManagedComInboundHotEntry(
    @PublishedApi internal val thisWord: Long,
    @PublishedApi internal val binding: ManagedComInboundBinding,
)

@PublishedApi
internal val managedComInboundHotEntry = AtomicReference<ManagedComInboundHotEntry?>(null)

internal fun clearManagedComInboundHotEntry(binding: ManagedComInboundBinding) {
    while (true) {
        val hot = managedComInboundHotEntry.load() ?: return
        if (hot.binding !== binding || managedComInboundHotEntry.compareAndSet(hot, null)) {
            return
        }
    }
}

/**
 * Platform weak storage dedicated to managed CCW inbound dispatch.
 *
 * Common code owns weak-versus-strong transitions. The target implementation only
 * supplies the cheapest GC-correct weak read and deterministic handle disposal.
 */
@PublishedApi
internal expect class PlatformManagedComInboundWeakReference(value: Any) : AutoCloseable {
    @PublishedApi
    internal inline fun get(): Any?

    override fun close()
}

internal expect fun platformCreateManagedComInboundBindingHandle(
    binding: ManagedComInboundBinding,
    canonicalObjectMemory: RawAddress,
): RawAddress

internal expect fun platformDisposeManagedComInboundBindingHandle(
    binding: ManagedComInboundBinding,
    platformHandle: RawAddress,
)

internal expect fun platformCreateInspectableQueryInterfaceCallback(): NativeCallbackHandle

internal expect fun platformCreateInspectableAddRefCallback(): NativeCallbackHandle

internal expect fun platformCreateInspectableReleaseCallback(): NativeCallbackHandle

@PublishedApi
internal inline fun winRTProjectionInboundManagedValue(thisWord: Long): Any? {
    return winRTProjectionInboundBinding(thisWord)?.get()
}

/**
 * Recovers the common inbound binding carried by a managed CCW interface pointer.
 *
 * The common exact-pointer hot entry mirrors the direct managed-instance recovery performed by
 * `ComInterfaceDispatch.GetInstance`. On a miss, Native reads the binding handle embedded in the
 * CCW object while JVM resolves the canonical pointer through its platform registry.
 */
@PublishedApi
internal inline fun winRTProjectionInboundBinding(thisWord: Long): ManagedComInboundBinding? {
    if (thisWord == 0L) return null
    val hot = managedComInboundHotEntry.load()
    if (hot != null && hot.thisWord == thisWord) {
        return hot.binding
    }
    val binding = platformWinRTProjectionInboundBinding(thisWord) ?: return null
    return if (binding.publishHotEntry(thisWord)) binding else null
}

/** Converts one borrowed inbound COM pointer into the owned reference consumed by projection code. */
@PublishedApi
internal inline fun winRTProjectionInboundRetainAddress(address: RawAddress): RawAddress {
    if (!PlatformAbi.isNull(address)) {
        WinRTPlatformApi.addRefRaw(address)
    }
    return address
}

@PublishedApi
internal expect inline fun platformWinRTProjectionInboundBinding(thisWord: Long): ManagedComInboundBinding?

/**
 * Returns the inbound binding only when [thisWord] is known to use this runtime's CCW layout.
 *
 * Native verifies the IUnknown vtable prefix before reading the private binding slot. JVM verifies
 * that the vtable was allocated by this runtime before reading the canonical-address slot.
 */
internal expect fun platformTryWinRTProjectionInboundBinding(thisWord: Long): ManagedComInboundBinding?

/** Releases a reference that was created directly from this runtime's own CCW interface table. */
internal fun tryReleaseManagedCcwReference(canonicalObjectMemory: RawAddress): Boolean {
    if (PlatformAbi.isNull(canonicalObjectMemory)) {
        return false
    }
    val binding = platformWinRTProjectionInboundBinding(canonicalObjectMemory.value) ?: return false
    binding.host.releaseKnownLocalReference()
    return true
}

@PublishedApi
internal const val managedComInboundBindingSlot = 1
internal const val managedComReferenceCounterSlot = 2
internal const val managedComQueryInterfaceTableSlot = 3
