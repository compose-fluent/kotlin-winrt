package io.github.kitectlab.winrt.runtime

/**
 * Runtime object-reference wrappers corresponding to `.cswinrt/src/WinRT.Runtime/Projections/Bindable.net5.cs`.
 *
 * These wrappers own IBindable* slot layouts and keep the non-generic `object` ABI path explicit in
 * `winrt-runtime`, instead of pushing object marshalling policy into the generator.
 */
open class WinRtBindableIterableReference(
    pointer: NativePointer,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableIterable,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(): WinRtBindableIteratorReference =
        invokeObjectMethod(slot = 6).use { reference ->
            createIteratorReference(reference.getRefPointer(), WinRtBindableInterfaceIds.IBindableIterator)
        }

    protected open fun createIteratorReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtBindableIteratorReference = WinRtBindableIteratorReference(pointer, interfaceId)
}

open class WinRtBindableIteratorReference(
    pointer: NativePointer,
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
        invokeNullableObjectMethod(slot = 6)?.use { reference ->
            createUnknownReference(reference.getRefPointer(), IID.IInspectable)
        }

    open fun hasCurrent(): Boolean = invokeBooleanMethod(slot = 7)

    open fun moveNext(): Boolean = invokeBooleanMethod(slot = 8)

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
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtBindableVectorViewReference(
    pointer: NativePointer,
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
        invokeNullableObjectMethodWithUInt32Arg(slot = 6, value = index)?.use { reference ->
            createUnknownReference(reference.getRefPointer(), IID.IInspectable)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun indexOf(valuePointer: NativePointer): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeAbi(
                slot = 8,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                valuePointer,
                indexOut,
                foundOut,
            )
        }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)
}

open class WinRtBindableVectorReference(
    pointer: NativePointer,
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
        invokeNullableObjectMethodWithUInt32Arg(slot = 6, value = index)?.use { reference ->
            createUnknownReference(reference.getRefPointer(), IID.IInspectable)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun getView(): WinRtBindableVectorViewReference =
        invokeObjectMethod(slot = 8).use { reference ->
            createVectorViewReference(reference.getRefPointer(), WinRtBindableInterfaceIds.IBindableVectorView)
        }

    open fun indexOf(valuePointer: NativePointer): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeAbi(
                slot = 9,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                valuePointer,
                indexOut,
                foundOut,
            )
        }

    open fun setAt(index: UInt, valuePointer: NativePointer) {
        invokeNullableInspectableMethod(slot = 10, index = index, valuePointer = valuePointer)
    }

    open fun insertAt(index: UInt, valuePointer: NativePointer) {
        invokeNullableInspectableMethod(slot = 11, index = index, valuePointer = valuePointer)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeAbi(
            slot = 12,
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.JAVA_INT,
            ),
            index.toInt(),
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun append(valuePointer: NativePointer) {
        val hr = invokeAbi(
            slot = 13,
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.ADDRESS,
            ),
            valuePointer,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAtEnd() {
        invokeUnitMethod(slot = 14)
    }

    open fun clear() {
        invokeUnitMethod(slot = 15)
    }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRefPointer(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createVectorViewReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtBindableVectorViewReference = WinRtBindableVectorViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)

    private fun invokeNullableInspectableMethod(
        slot: Int,
        index: UInt,
        valuePointer: NativePointer,
    ) {
        val hr = invokeAbi(
            slot = slot,
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
            index.toInt(),
            valuePointer,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }
}
