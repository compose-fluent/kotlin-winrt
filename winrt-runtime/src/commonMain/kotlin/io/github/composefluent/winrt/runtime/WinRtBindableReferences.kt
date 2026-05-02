package io.github.composefluent.winrt.runtime

private object WinRtBindableSlots {
    const val IterableFirst: Int = 6
    const val IteratorCurrent: Int = 6
    const val IteratorHasCurrent: Int = 7
    const val IteratorMoveNext: Int = 8
    const val VectorGetAt: Int = 6
    const val VectorSize: Int = 7
    const val VectorViewIndexOf: Int = 8
    const val VectorGetView: Int = 8
    const val VectorIndexOf: Int = 9
    const val VectorRemoveAt: Int = 12
    const val VectorAppend: Int = 13
    const val VectorRemoveAtEnd: Int = 14
    const val VectorClear: Int = 15
}

/**
 * Runtime object-reference wrappers corresponding to `.cswinrt/src/WinRT.Runtime/Projections/Bindable.net5.cs`.
 *
 * These wrappers own IBindable* slot layouts and keep the non-generic `object` ABI path explicit in
 * `winrt-runtime`, instead of pushing object marshalling policy into the generator.
 */
open class WinRtBindableIterableReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableIterable,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(): WinRtBindableIteratorReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRtBindableSlots.IterableFirst, resultOut) },
            wrap = { pointer -> createIteratorReference(pointer, WinRtBindableInterfaceIds.IBindableIterator) },
        )

    protected open fun createIteratorReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtBindableIteratorReference = WinRtBindableIteratorReference(pointer, interfaceId)
}

open class WinRtBindableIteratorReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableIterator,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun current(): IUnknownReference =
        currentOrNull()
            ?: throw WinRtIllegalStateException(
                "IBindableIterator.Current returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun currentOrNull(): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRtBindableSlots.IteratorCurrent, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun hasCurrent(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtBindableSlots.IteratorHasCurrent, resultOut)
        }

    open fun moveNext(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtBindableSlots.IteratorMoveNext, resultOut)
        }

    /**
     * `.cswinrt` keeps GetMany for WinUI compatibility only and throws NotImplementedException when called.
     * The Kotlin runtime keeps the slot surfaced for parity but does not use it in helpers.
     */
    open fun getMany(capacity: Int): Nothing {
        require(capacity >= 0) { "capacity must be non-negative." }
        throw WinRtUnsupportedOperationException(
            "IBindableIterator.GetMany is not implemented for bindable iterator projections.",
            KnownHResults.E_NOTIMPL,
        )
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)
}

open class WinRtBindableVectorViewReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableVectorView,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IBindableVectorView.GetAt returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRtBindableSlots.VectorGetAt, index, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtBindableSlots.VectorSize, resultOut)
        }

    open fun indexOf(valuePointer: RawAddress): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeSlot(WinRtBindableSlots.VectorViewIndexOf, valuePointer, indexOut, foundOut)
        }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer().asRawAddress(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)
}

open class WinRtBindableVectorReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableVector,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IBindableVector.GetAt returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRtBindableSlots.VectorGetAt, index, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtBindableSlots.VectorSize, resultOut)
        }

    open fun getView(): WinRtBindableVectorViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRtBindableSlots.VectorGetView, resultOut) },
            wrap = { pointer -> createVectorViewReference(pointer, WinRtBindableInterfaceIds.IBindableVectorView) },
        )

    open fun indexOf(valuePointer: RawAddress): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeSlot(WinRtBindableSlots.VectorIndexOf, valuePointer, indexOut, foundOut)
        }

    open fun setAt(index: UInt, valuePointer: RawAddress) {
        invokeNullableInspectableMethod(slot = 10, index = index, valuePointer = valuePointer)
    }

    open fun insertAt(index: UInt, valuePointer: RawAddress) {
        invokeNullableInspectableMethod(slot = 11, index = index, valuePointer = valuePointer)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeSlot(WinRtBindableSlots.VectorRemoveAt, index)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun append(valuePointer: RawAddress) {
        val hr = invokeSlot(WinRtBindableSlots.VectorAppend, valuePointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAtEnd() {
        val hr = invokeSlot(WinRtBindableSlots.VectorRemoveAtEnd)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRtBindableSlots.VectorClear)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer().asRawAddress(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createVectorViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtBindableVectorViewReference = WinRtBindableVectorViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)

    private fun invokeNullableInspectableMethod(
        slot: Int,
        index: UInt,
        valuePointer: RawAddress,
    ) {
        val hr = invokeSlot(slot, index, valuePointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }
}
