package io.github.composefluent.winrt.runtime

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
    fun createMarshaler(value: Any?): WinRTObjectMarshaler =
        when (value) {
            null -> WinRTObjectMarshaler(PlatformAbi.nullPointer)
            else -> ComWrappersSupport.tryUnwrapObject(value)?.let(::createUnwrappedInspectableMarshaler)
                ?: createMarshalerCore(value)
        }

    private fun createMarshalerCore(value: Any): WinRTObjectMarshaler =
        when (value) {
            is WinRTProjectedDelegate -> createDelegateMarshaler(value)
            is RawAddress -> WinRTObjectMarshaler(value)
            is RawComPtr -> WinRTObjectMarshaler(value.asRawAddress())
            is ComObjectReference -> createInspectableMarshaler(value)
            is IWinRTObject -> createInspectableMarshaler(value.nativeObject)
            else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).let { reference ->
                ProjectedObjectValueRoots.retain(reference)
                WinRTObjectMarshaler(reference.pointer.asRawAddress())
            }
        }

    fun fromAbi(pointer: RawAddress): Any? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            WinRTInspectableComObject.findManagedValue(pointer)
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
            is WinRTProjectedDelegate -> fromManagedDelegate(value)
            is RawAddress -> value
            is RawComPtr -> value.asRawAddress()
            is ComObjectReference -> value.asInspectable().useAndGetRef()
            is IWinRTObject -> value.nativeObject.asInspectable().useAndGetRef()
            else -> ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef()
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

    fun createReference(value: WinRTProjectedDelegate): WinRTDelegateReference {
        val handle = getOrCreate(value)
        return handle.createReference().also {
            handle.releaseManagedReferenceForNativeOwnership()
        }
    }

    private fun getOrCreate(value: WinRTProjectedDelegate): WinRTDelegateHandle =
        handles.getOrPut(value) {
            value.createWinRTDelegateHandle().also { handle ->
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

private object ProjectedObjectValueRoots {
    private val roots = SnapshotList<ComObjectReference>()

    fun retain(reference: ComObjectReference) {
        // XAML dependency properties can return the same object pointer after the setter call returns.
        roots.add(reference)
    }
}

fun winRTObjectMarshaler(value: Any?): WinRTObjectMarshaler =
    WinRTObjectMarshaller.createMarshaler(value)
