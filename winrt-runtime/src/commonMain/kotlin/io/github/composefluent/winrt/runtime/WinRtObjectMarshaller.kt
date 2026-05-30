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

    private fun createDelegateMarshaler(value: WinRtProjectedDelegate): WinRtObjectMarshaler {
        val handle = value.createWinRtDelegateHandle()
        ProjectedDelegateObjectRoots.retain(handle)
        val reference = handle.createReference()
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
        val handle = value.createWinRtDelegateHandle()
        ProjectedDelegateObjectRoots.retain(handle)
        val reference = handle.createReference()
        return try {
            reference.asInspectable().useAndGetRef()
        } finally {
            reference.close()
        }
    }
}

internal object ProjectedDelegateObjectRoots {
    private val roots = SnapshotList<WinRtDelegateHandle>()

    fun retain(handle: WinRtDelegateHandle) {
        roots.add(handle)
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
