package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Runtime object-reference wrappers corresponding to `.cswinrt/src/WinRT.Runtime/Projections/Bindable.net5.cs`.
 *
 * These wrappers own IBindable* slot layouts and keep the non-generic `object` ABI path explicit in
 * `winrt-runtime`, instead of pushing object marshalling policy into the generator.
 */
open class WinRtBindableIterableReference(
    pointer: MemorySegment,
    interfaceId: Guid = WinRtBindableInterfaceIds.IBindableIterable,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(): WinRtBindableIteratorReference =
        invokeObjectMethod(slot = 6).use { reference ->
            createIteratorReference(reference.getRef(), WinRtBindableInterfaceIds.IBindableIterator)
        }

    protected open fun createIteratorReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtBindableIteratorReference = WinRtBindableIteratorReference(pointer, interfaceId)
}

open class WinRtBindableIteratorReference(
    pointer: MemorySegment,
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
            createUnknownReference(reference.getRef(), IID.IInspectable)
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
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtBindableVectorViewReference(
    pointer: MemorySegment,
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
            createUnknownReference(reference.getRef(), IID.IInspectable)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun indexOf(valuePointer: MemorySegment): Pair<Boolean, UInt> {
        Arena.ofConfined().use { arena ->
            val indexOut = arena.allocate(ValueLayout.JAVA_INT)
            val foundOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeAbi(
                slot = 8,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                valuePointer,
                indexOut,
                foundOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return (foundOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0) to indexOut.get(ValueLayout.JAVA_INT, 0).toUInt()
        }
    }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRef(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)
}

open class WinRtBindableVectorReference(
    pointer: MemorySegment,
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
            createUnknownReference(reference.getRef(), IID.IInspectable)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun getView(): WinRtBindableVectorViewReference =
        invokeObjectMethod(slot = 8).use { reference ->
            createVectorViewReference(reference.getRef(), WinRtBindableInterfaceIds.IBindableVectorView)
        }

    open fun indexOf(valuePointer: MemorySegment): Pair<Boolean, UInt> {
        Arena.ofConfined().use { arena ->
            val indexOut = arena.allocate(ValueLayout.JAVA_INT)
            val foundOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeAbi(
                slot = 9,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                valuePointer,
                indexOut,
                foundOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return (foundOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0) to indexOut.get(ValueLayout.JAVA_INT, 0).toUInt()
        }
    }

    open fun setAt(index: UInt, valuePointer: MemorySegment) {
        invokeNullableInspectableMethod(slot = 10, index = index, valuePointer = valuePointer)
    }

    open fun insertAt(index: UInt, valuePointer: MemorySegment) {
        invokeNullableInspectableMethod(slot = 11, index = index, valuePointer = valuePointer)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeAbi(
            slot = 12,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
            index.toInt(),
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    open fun append(valuePointer: MemorySegment) {
        val hr = invokeAbi(
            slot = 13,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.ADDRESS,
            ),
            valuePointer,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    open fun removeAtEnd() {
        invokeUnitMethod(slot = 14)
    }

    open fun clear() {
        invokeUnitMethod(slot = 15)
    }

    open fun asIterable(): WinRtBindableIterableReference =
        queryInterface(WinRtBindableInterfaceIds.IBindableIterable).getOrThrow().use { reference ->
            createIterableReference(reference.getRef(), WinRtBindableInterfaceIds.IBindableIterable)
        }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createVectorViewReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtBindableVectorViewReference = WinRtBindableVectorViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtBindableIterableReference = WinRtBindableIterableReference(pointer, interfaceId)

    private fun invokeNullableInspectableMethod(
        slot: Int,
        index: UInt,
        valuePointer: MemorySegment,
    ) {
        val hr = invokeAbi(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            index.toInt(),
            valuePointer,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }
}
