package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

interface WinRTProjectedDelegate {
    fun createWinRTDelegateHandle(): WinRTDelegateHandle
}

class WinRTObjectMarshaler internal constructor(
    val abi: RawAddress,
    private val cleanup: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        cleanup()
    }
}

object WinRTObjectMarshaller {
    /**
     * Most inbound object callbacks repeat the same borrowed COM identity (notably an event
     * sender). Keep one weak, lock-free entry so the steady path does not re-enter the general
     * RCW identity map. The entry is populated only after the normal managed-CCW probe, so a
     * managed pointer always keeps its existing identity precedence.
     */
    @kotlin.concurrent.Volatile
    private var hotInboundRcw: HotInboundRcw? = null

    private fun isLiveCachedValue(value: Any): Boolean =
        when (value) {
            is WinRTObjectBase<*> -> value.tryGetInitializedNativeObject()?.isDisposed == false
            is IWinRTObject -> !value.nativeObject.isDisposed
            else -> true
        }

    fun createMarshaler(
        value: Any?,
        declaredReferenceArrayElementType: KClass<*>? = null,
    ): WinRTObjectMarshaler =
        when (value) {
            null -> WinRTObjectMarshaler(PlatformAbi.nullPointer)
            is WinRTProjectedDelegate -> if (value is IWinRTObject) {
                ComWrappersSupport.tryUnwrapObject(value)?.let(::createUnwrappedInspectableMarshaler)
                    ?: createMarshalerCore(value, declaredReferenceArrayElementType)
            } else {
                createDelegateMarshaler(value)
            }
            else -> ComWrappersSupport.tryUnwrapObject(value)?.let(::createUnwrappedInspectableMarshaler)
                ?: createManagedInspectableLeaseMarshaler(value)
                ?: createMarshalerCore(value, declaredReferenceArrayElementType)
        }

    /**
     * Generic object parameters are frequently sent back synchronously from a delegate. When the
     * object already owns a weak-cache CCW, its inspectable interface is stable for the duration
     * of the call and the cache can provide the same borrowed lease used by compiler-lowered
     * projected calls. Keep this probe limited to non-projected values; projected wrappers and
     * delegates retain their existing unwrapping and identity rules above.
     */
    private fun createManagedInspectableLeaseMarshaler(value: Any): WinRTObjectMarshaler? {
        if (value is IWinRTObject) return null
        val lease = ComWrappersSupport.tryAcquireCachedCCWCallLease(value, IID.IInspectable)
            ?: return null
        return WinRTObjectMarshaler(lease.abi) { lease.close() }
    }

    private fun createMarshalerCore(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>?,
    ): WinRTObjectMarshaler =
        when (value) {
            is WinRTProjectedDelegate -> createDelegateMarshaler(value)
            is RawAddress -> WinRTObjectMarshaler(value)
            is RawComPtr -> WinRTObjectMarshaler(value.asRawAddress())
            is ComObjectReference -> createInspectableMarshaler(value)
            is IWinRTObject -> createInspectableMarshaler(value.nativeObject)
            else -> ComWrappersSupport.createCCWForObjectForMarshaling(
                value = value,
                interfaceId = IID.IInspectable,
                declaredReferenceArrayElementType = declaredReferenceArrayElementType,
            ).let { marshaler ->
                WinRTObjectMarshaler(marshaler.abi, marshaler::close)
            }
        }

    fun fromAbi(pointer: RawAddress): Any? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val pointerKey = PlatformAbi.pointerKey(pointer)
        hotInboundRcw?.let { hot ->
            if (hot.pointerKey == pointerKey) {
                hot.reference.get()?.let { cached ->
                    if (isLiveCachedValue(cached)) {
                        return cached
                    }
                }
            }
        }

        // Preserve the managed CCW identity probe before creating or reusing an RCW.
        WinRTInspectableComObject.findManagedValue(pointer)?.let { return it }
        return ComWrappersSupport.createRcwForComObject(pointer)?.also { rcw ->
            hotInboundRcw = HotInboundRcw(pointerKey, PlatformManagedWeakReference(rcw))
        }
    }

    /**
     * Decodes an ABI-owned `System.Object` result and consumes its reference exactly once.
     * Borrowed callback/event arguments must continue using [fromAbi].
     */
    fun fromOwnedAbi(pointer: RawAddress): Any? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }

        val pointerKey = PlatformAbi.pointerKey(pointer)
        hotInboundRcw?.let { hot ->
            if (hot.pointerKey == pointerKey) {
                hot.reference.get()?.let { cached ->
                    if (isLiveCachedValue(cached)) {
                        WinRTPlatformApi.releaseRaw(pointer)
                        return cached
                    }
                }
            }
        }

        // A native object returned repeatedly by an owned System.Object slot normally already has
        // an RCW identity. Consume the duplicate ABI reference before probing the managed CCW
        // registry; the latter is only needed when the direct cache misses.
        ComWrappersSupport.tryConsumeCachedRcwForOwnedComObject(pointer)?.let { return it }

        // Preserve the managed CCW identity probe before creating or reusing an RCW.
        WinRTInspectableComObject.findManagedValue(pointer)?.let { managed ->
            WinRTPlatformApi.releaseRaw(pointer)
            return managed
        }
        return ComWrappersSupport.createRcwForOwnedComObject(pointer)?.also { rcw ->
            hotInboundRcw = HotInboundRcw(pointerKey, PlatformManagedWeakReference(rcw))
        }
    }

    private class HotInboundRcw(
        val pointerKey: Long,
        val reference: PlatformManagedWeakReference<Any>,
    )

    fun fromManaged(
        value: Any?,
        declaredReferenceArrayElementType: KClass<*>? = null,
    ): RawAddress =
        when (value) {
            null -> PlatformAbi.nullPointer
            is WinRTProjectedDelegate -> if (value is IWinRTObject) {
                ComWrappersSupport.tryUnwrapObject(value)?.use { reference ->
                    reference.asInspectable().useAndGetRef()
                } ?: fromManagedCore(value, declaredReferenceArrayElementType)
            } else {
                fromManagedDelegate(value)
            }
            else -> ComWrappersSupport.tryUnwrapObject(value)?.use { reference ->
                reference.asInspectable().useAndGetRef()
            } ?: fromManagedCore(value, declaredReferenceArrayElementType)
        }

    private fun fromManagedCore(
        value: Any,
        declaredReferenceArrayElementType: KClass<*>?,
    ): RawAddress =
        when (value) {
            is WinRTProjectedDelegate -> fromManagedDelegate(value)
            is RawAddress -> value
            is RawComPtr -> value.asRawAddress()
            is ComObjectReference -> value.asInspectable().useAndGetRef()
            is IWinRTObject -> value.nativeObject.asInspectable().useAndGetRef()
            else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable, declaredReferenceArrayElementType).useAndGetRef()
        }

    private fun createInspectableMarshaler(reference: ComObjectReference): WinRTObjectMarshaler {
        val inspectableReference = reference.asInspectable()
        return WinRTObjectMarshaler(inspectableReference.pointer.asRawAddress(), inspectableReference::close)
    }

    private fun createUnwrappedInspectableMarshaler(reference: ComObjectReference): WinRTObjectMarshaler =
        reference.use(::createInspectableMarshaler)

    private fun createDelegateMarshaler(value: WinRTProjectedDelegate): WinRTObjectMarshaler {
        val reference = ProjectedDelegateCcwCache.createReference(value)
        val inspectableReference = try {
            reference.asInspectable()
        } catch (throwable: Throwable) {
            reference.close()
            throw throwable
        }
        return WinRTObjectMarshaler(inspectableReference.pointer.asRawAddress()) {
            try {
                inspectableReference.close()
            } finally {
                reference.close()
            }
        }
    }

    private fun fromManagedDelegate(value: WinRTProjectedDelegate): RawAddress {
        val reference = ProjectedDelegateCcwCache.createReference(value)
        return try {
            reference.asInspectable().useAndGetRef()
        } finally {
            reference.close()
        }
    }
}

internal object ProjectedDelegateCcwCache {
    private val handles = WeakKeyStateMap<WinRTProjectedDelegate, WinRTDelegateHandle>()

    /**
     * Acquires the raw reference owned only for one synchronous ABI call. This is the Kotlin
     * equivalent of CsWinRT's `ObjectReferenceValue`: deterministic marshaling does not need a
     * context-capturing object-reference wrapper, while a callee that retains the pointer must
     * still AddRef it before the call returns.
     */
    fun acquireMarshalingReference(value: WinRTProjectedDelegate): RawAddress {
        while (true) {
            val handle = getOrCreate(value)
            handle.tryAcquireMarshalingReference()?.let { reference ->
                try {
                    handle.releaseManagedReferenceForNativeOwnership()
                } catch (failure: Throwable) {
                    WinRTPlatformApi.releaseRaw(reference)
                    throw failure
                }
                return reference
            }
            handles.remove(value, handle)
        }
    }

    fun createReference(value: WinRTProjectedDelegate): WinRTDelegateReference {
        while (true) {
            val handle = getOrCreate(value)
            handle.tryCreateReference()?.let { reference ->
                handle.releaseManagedReferenceForNativeOwnership()
                return reference
            }
            handles.remove(value, handle)
        }
    }

    private fun getOrCreate(value: WinRTProjectedDelegate): WinRTDelegateHandle =
        handles.getOrPut(value) {
            value.createWinRTDelegateHandle().also { handle ->
                ProjectedDelegateObjectRoots.retain(handle)
                handle.addCleanupAction {
                    handle.markClosedAfterNativeCleanup()
                    handles.remove(value, handle)
                    ProjectedDelegateObjectRoots.release(handle)
                }
            }
        }

    fun clearForTests() {
        handles.clear()
    }
}

internal object ProjectedDelegateObjectRoots {
    private val roots = SnapshotList<WinRTDelegateHandle>()

    fun retain(handle: WinRTDelegateHandle) {
        roots.add(handle)
    }

    fun release(handle: WinRTDelegateHandle) {
        roots.remove(handle)
    }

    fun clearForTests() {
        roots.clear()
    }
}

fun winRTObjectMarshaler(value: Any?): WinRTObjectMarshaler =
    WinRTObjectMarshaller.createMarshaler(value)
