package io.github.composefluent.winrt.runtime

private object WinRTBindableSlots {
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
open class WinRTBindableIterableReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRTBindableInterfaceIds.IBindableIterable,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(): WinRTBindableIteratorReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRTBindableSlots.IterableFirst, resultOut) },
            wrap = { pointer -> createIteratorReference(pointer, WinRTBindableInterfaceIds.IBindableIterator) },
        )

    protected open fun createIteratorReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTBindableIteratorReference = WinRTBindableIteratorReference(pointer, interfaceId)
}

open class WinRTBindableIteratorReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRTBindableInterfaceIds.IBindableIterator,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun current(): IUnknownReference =
        currentOrNull()
            ?: throw WinRTIllegalStateException(
                "IBindableIterator.Current returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun currentOrNull(): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRTBindableSlots.IteratorCurrent, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun hasCurrent(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTBindableSlots.IteratorHasCurrent, resultOut)
        }

    open fun moveNext(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTBindableSlots.IteratorMoveNext, resultOut)
        }

    /**
     * `.cswinrt` keeps GetMany for WinUI compatibility only and throws NotImplementedException when called.
     * The Kotlin runtime keeps the slot surfaced for parity but does not use it in helpers.
     */
    open fun getMany(capacity: Int): Nothing {
        require(capacity >= 0) { "capacity must be non-negative." }
        throw WinRTUnsupportedOperationException(
            "IBindableIterator.GetMany is not implemented for bindable iterator projections.",
            KnownHResults.E_NOTIMPL,
        )
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)
}

open class WinRTBindableVectorViewReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRTBindableInterfaceIds.IBindableVectorView,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRTIllegalStateException(
                "IBindableVectorView.GetAt returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRTBindableSlots.VectorGetAt, index, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTBindableSlots.VectorSize, resultOut)
        }

    open fun indexOf(valuePointer: RawAddress): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeSlot(WinRTBindableSlots.VectorViewIndexOf, valuePointer, indexOut, foundOut)
        }

    open fun asIterable(): WinRTBindableIterableReference =
        queryInterface(WinRTBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer().asRawAddress(), WinRTBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTBindableIterableReference = WinRTBindableIterableReference(pointer, interfaceId)
}

open class WinRTBindableVectorReference(
    pointer: RawAddress,
    interfaceId: Guid = WinRTBindableInterfaceIds.IBindableVector,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRTIllegalStateException(
                "IBindableVector.GetAt returned a null inspectable reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut -> invokeSlot(WinRTBindableSlots.VectorGetAt, index, resultOut) },
            wrap = { pointer -> createUnknownReference(pointer, IID.IInspectable) },
        )

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTBindableSlots.VectorSize, resultOut)
        }

    open fun getView(): WinRTBindableVectorViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRTBindableSlots.VectorGetView, resultOut) },
            wrap = { pointer -> createVectorViewReference(pointer, WinRTBindableInterfaceIds.IBindableVectorView) },
        )

    open fun indexOf(valuePointer: RawAddress): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeSlot(WinRTBindableSlots.VectorIndexOf, valuePointer, indexOut, foundOut)
        }

    open fun setAt(index: UInt, valuePointer: RawAddress) {
        invokeNullableInspectableMethod(slot = 10, index = index, valuePointer = valuePointer)
    }

    open fun insertAt(index: UInt, valuePointer: RawAddress) {
        invokeNullableInspectableMethod(slot = 11, index = index, valuePointer = valuePointer)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeSlot(WinRTBindableSlots.VectorRemoveAt, index)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun append(valuePointer: RawAddress) {
        val hr = invokeSlot(WinRTBindableSlots.VectorAppend, valuePointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAtEnd() {
        val hr = invokeSlot(WinRTBindableSlots.VectorRemoveAtEnd)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRTBindableSlots.VectorClear)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun asIterable(): WinRTBindableIterableReference =
        queryInterface(WinRTBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer().asRawAddress(), WinRTBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createVectorViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTBindableVectorViewReference = WinRTBindableVectorViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTBindableIterableReference = WinRTBindableIterableReference(pointer, interfaceId)

    private fun invokeNullableInspectableMethod(
        slot: Int,
        index: UInt,
        valuePointer: RawAddress,
    ) {
        val hr = invokeSlot(slot, index, valuePointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }
}
