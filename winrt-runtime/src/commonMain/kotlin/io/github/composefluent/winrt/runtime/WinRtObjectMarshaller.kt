package io.github.composefluent.winrt.runtime

interface WinRtProjectedDelegate {
    fun createWinRtDelegateHandle(): WinRtDelegateHandle
}

class WinRtObjectMarshaler internal constructor(
    val abi: RawAddress,
    private val cleanup: () -> Unit = {},
) : AutoCloseable {
    override fun close() {
        cleanup()
    }
}

object WinRtObjectMarshaller {
    fun createMarshaler(value: Any?): WinRtObjectMarshaler =
        when (value) {
            null -> WinRtObjectMarshaler(PlatformAbi.nullPointer)
            else -> ComWrappersSupport.tryUnwrapObject(value)?.let(::createUnwrappedInspectableMarshaler)
                ?: createMarshalerCore(value)
        }

    private fun createMarshalerCore(value: Any): WinRtObjectMarshaler =
        when (value) {
            is WinRtProjectedDelegate -> createDelegateMarshaler(value)
            is RawAddress -> WinRtObjectMarshaler(value)
            is RawComPtr -> WinRtObjectMarshaler(value.asRawAddress())
            is ComObjectReference -> createInspectableMarshaler(value)
            is IWinRTObject -> createInspectableMarshaler(value.nativeObject)
            else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).let { reference ->
                ProjectedObjectValueRoots.retain(reference)
                WinRtObjectMarshaler(reference.pointer.asRawAddress())
            }
        }

    fun fromAbi(pointer: RawAddress): Any? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            WinRtInspectableComObject.findManagedValue(pointer)
                ?: tryProjectBorrowedInspectableValue(pointer)
                ?: ComWrappersSupport.createRcwForComObject(pointer)
        }

    fun fromManaged(value: Any?): RawAddress =
        when (value) {
            null -> PlatformAbi.nullPointer
            else -> ComWrappersSupport.tryUnwrapObject(value)?.use { reference ->
                reference.asInspectable().useAndGetRef()
            } ?: fromManagedCore(value)
        }

    private fun fromManagedCore(value: Any): RawAddress =
        when (value) {
            is WinRtProjectedDelegate -> fromManagedDelegate(value)
            is RawAddress -> value
            is RawComPtr -> value.asRawAddress()
            is ComObjectReference -> value.asInspectable().useAndGetRef()
            is IWinRTObject -> value.nativeObject.asInspectable().useAndGetRef()
            else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef()
        }

    private fun createInspectableMarshaler(reference: ComObjectReference): WinRtObjectMarshaler {
        val inspectableReference = reference.asInspectable()
        return WinRtObjectMarshaler(inspectableReference.pointer.asRawAddress(), inspectableReference::close)
    }

    private fun createUnwrappedInspectableMarshaler(reference: ComObjectReference): WinRtObjectMarshaler =
        reference.use(::createInspectableMarshaler)

    private fun createDelegateMarshaler(value: WinRtProjectedDelegate): WinRtObjectMarshaler {
        val reference = ProjectedDelegateCcwCache.createReference(value)
        val inspectableReference = try {
            reference.asInspectable()
        } catch (throwable: Throwable) {
            reference.close()
            throw throwable
        }
        return WinRtObjectMarshaler(inspectableReference.pointer.asRawAddress()) {
            try {
                inspectableReference.close()
            } finally {
                reference.close()
            }
        }
    }

    private fun fromManagedDelegate(value: WinRtProjectedDelegate): RawAddress {
        val reference = ProjectedDelegateCcwCache.createReference(value)
        return try {
            reference.asInspectable().useAndGetRef()
        } finally {
            reference.close()
        }
    }
}

internal object ProjectedDelegateCcwCache {
    private val handles = WeakKeyStateMap<WinRtProjectedDelegate, WinRtDelegateHandle>()

    fun createReference(value: WinRtProjectedDelegate): WinRtDelegateReference {
        val handle = getOrCreate(value)
        return handle.createReference().also {
            handle.releaseManagedReferenceForNativeOwnership()
        }
    }

    private fun getOrCreate(value: WinRtProjectedDelegate): WinRtDelegateHandle =
        handles.getOrPut(value) {
            value.createWinRtDelegateHandle().also { handle ->
                ProjectedDelegateObjectRoots.retain(handle)
                handle.addCleanupAction {
                    handles.remove(value)
                    ProjectedDelegateObjectRoots.release(handle)
                    handle.markClosedAfterNativeCleanup()
                }
            }
        }

    fun clearForTests() {
        handles.clear()
    }
}

internal object ProjectedDelegateObjectRoots {
    private val roots = SnapshotList<WinRtDelegateHandle>()

    fun retain(handle: WinRtDelegateHandle) {
        roots.add(handle)
    }

    fun release(handle: WinRtDelegateHandle) {
        roots.remove(handle)
    }

    fun clearForTests() {
        roots.clear()
    }
}

private object ProjectedObjectValueRoots {
    private val roots = SnapshotList<ComObjectReference>()

    fun retain(reference: ComObjectReference) {
        // XAML dependency properties can return the same object pointer after the setter call returns.
        roots.add(reference)
    }
}

fun winRtObjectMarshaler(value: Any?): WinRtObjectMarshaler =
    WinRtObjectMarshaller.createMarshaler(value)
