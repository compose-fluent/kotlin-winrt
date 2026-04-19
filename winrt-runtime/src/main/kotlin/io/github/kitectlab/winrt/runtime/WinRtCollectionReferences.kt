package io.github.kitectlab.winrt.runtime

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemoryLayout
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

open class WinRtCollectionReferenceBase(
    pointer: MemorySegment,
    interfaceId: Guid,
) : IUnknownReference(pointer, interfaceId) {
    protected fun invokeNullableObjectMethod(slot: Int): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            val resultPointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (resultPointer == MemorySegment.NULL) null else IUnknownReference(resultPointer)
        }
    }

    protected fun invokeNullableObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.toInt(),
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            val resultPointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (resultPointer == MemorySegment.NULL) null else IUnknownReference(resultPointer)
        }
    }

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference? {
        Arena.ofConfined().use { arena ->
            val resultOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.pointer,
                resultOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            val resultPointer = resultOut.get(ValueLayout.ADDRESS, 0)
            return if (resultPointer == MemorySegment.NULL) null else IUnknownReference(resultPointer)
        }
    }

    protected fun invokeIndexOfObjectArg(slot: Int, value: ComObjectReference): Pair<Boolean, UInt> {
        Arena.ofConfined().use { arena ->
            val indexOut = arena.allocate(ValueLayout.JAVA_INT)
            val foundOut = arena.allocate(ValueLayout.JAVA_BYTE)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                value.pointer,
                indexOut,
                foundOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return (foundOut.get(ValueLayout.JAVA_BYTE, 0).toInt() != 0) to indexOut.get(ValueLayout.JAVA_INT, 0).toUInt()
        }
    }

    protected fun invokeUnitMethodWithUInt32ObjectArg(slot: Int, index: UInt, value: ComObjectReference) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
            ),
            pointer,
            index.toInt(),
            value.pointer,
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    protected fun invokeUnitMethodWithUInt32(slot: Int, value: UInt) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = FunctionDescriptor.of(
                ValueLayout.JAVA_INT,
                ValueLayout.ADDRESS,
                ValueLayout.JAVA_INT,
            ),
            pointer,
            value.toInt(),
        )
        WindowsRuntimePlatform.checkSucceeded(hr)
    }

    protected fun invokeObjectGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> {
        Arena.ofConfined().use { arena ->
            val itemsLayout = MemoryLayout.sequenceLayout(capacity.toLong(), ValueLayout.ADDRESS)
            val itemsOut = arena.allocate(itemsLayout)
            val resultOut = arena.allocate(ValueLayout.JAVA_INT)
            val descriptor = if (startIndex == null) {
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                )
            } else {
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                )
            }
            val hr = if (startIndex == null) {
                invokeIntMethod(
                    slot = slot,
                    descriptor = descriptor,
                    pointer,
                    capacity,
                    itemsOut,
                    resultOut,
                )
            } else {
                invokeIntMethod(
                    slot = slot,
                    descriptor = descriptor,
                    pointer,
                    startIndex.toInt(),
                    capacity,
                    itemsOut,
                    resultOut,
                )
            }
            WindowsRuntimePlatform.checkSucceeded(hr)
            val actualCount = resultOut.get(ValueLayout.JAVA_INT, 0)
            return (0 until actualCount).map { index ->
                val elementPointer = itemsOut.getAtIndex(ValueLayout.ADDRESS, index.toLong())
                if (elementPointer == MemorySegment.NULL) null else IUnknownReference(elementPointer)
            }
        }
    }

    protected fun invokeReplaceAllObjectArray(slot: Int, items: List<ComObjectReference>) {
        Arena.ofConfined().use { arena ->
            val itemsLayout = MemoryLayout.sequenceLayout(items.size.toLong(), ValueLayout.ADDRESS)
            val itemsMemory = arena.allocate(itemsLayout)
            items.forEachIndexed { index, item ->
                itemsMemory.setAtIndex(ValueLayout.ADDRESS, index.toLong(), item.pointer)
            }
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                items.size,
                itemsMemory,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
        }
    }

    protected fun invokeMapViewSplitPointers(slot: Int): Pair<MemorySegment, MemorySegment> {
        Arena.ofConfined().use { arena ->
            val firstOut = arena.allocate(ValueLayout.ADDRESS)
            val secondOut = arena.allocate(ValueLayout.ADDRESS)
            val hr = invokeIntMethod(
                slot = slot,
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
                pointer,
                firstOut,
                secondOut,
            )
            WindowsRuntimePlatform.checkSucceeded(hr)
            return firstOut.get(ValueLayout.ADDRESS, 0) to secondOut.get(ValueLayout.ADDRESS, 0)
        }
    }
}

open class WinRtKeyValuePairReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun key(): IUnknownReference? =
        invokeNullableObjectMethod(slot = 6)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun value(): IUnknownReference? =
        invokeNullableObjectMethod(slot = 7)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtIterableReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun first(iteratorInterfaceId: Guid): WinRtIteratorReference =
        invokeObjectMethod(slot = 6).let { reference -> createIteratorReference(reference.pointer, iteratorInterfaceId) }

    protected open fun createIteratorReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtIteratorReference = WinRtIteratorReference(pointer, interfaceId)
}

open class WinRtIteratorReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun current(): IUnknownReference =
        currentOrNull() ?: throw WinRtIllegalStateException(
            "IIterator.Current returned a null COM reference.",
            KnownHResults.E_BOUNDS,
        )

    open fun currentOrNull(): IUnknownReference? =
        invokeNullableObjectMethod(slot = 6)?.let { reference -> createUnknownReference(reference.pointer, reference.interfaceId) }

    open fun hasCurrent(): Boolean = invokeBooleanMethod(slot = 7)

    open fun moveNext(): Boolean = invokeBooleanMethod(slot = 8)

    open fun getMany(capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 9, startIndex = null, capacity = capacity)
    }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun invokeGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> = invokeObjectGetMany(slot, startIndex, capacity)
}

open class WinRtVectorViewReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IVectorView.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(slot = 6, value = index)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> =
        invokeIndexOfMethodWithObjectArg(slot = 8, value = value)

    open fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 9, startIndex = startIndex, capacity = capacity)
    }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun invokeIndexOfMethodWithObjectArg(
        slot: Int,
        value: ComObjectReference,
    ): Pair<Boolean, UInt> = invokeIndexOfObjectArg(slot, value)

    protected open fun invokeGetMany(
        slot: Int,
        startIndex: UInt,
        capacity: Int,
    ): List<IUnknownReference?> = invokeObjectGetMany(slot, startIndex, capacity)
}

open class WinRtVectorReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IVector.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(slot = 6, value = index)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun getView(vectorViewInterfaceId: Guid): WinRtVectorViewReference =
        invokeObjectMethod(slot = 8).let { reference -> createVectorViewReference(reference.pointer, vectorViewInterfaceId) }

    open fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> =
        invokeIndexOfMethodWithObjectArg(slot = 9, value = value)

    open fun setAt(index: UInt, value: ComObjectReference) {
        invokeUnitMethodWithUInt32AndObjectArg(slot = 10, index = index, value = value)
    }

    open fun insertAt(index: UInt, value: ComObjectReference) {
        invokeUnitMethodWithUInt32AndObjectArg(slot = 11, index = index, value = value)
    }

    open fun removeAt(index: UInt) {
        invokeUnitMethodWithUInt32Arg(slot = 12, value = index)
    }

    open fun append(value: ComObjectReference) {
        invokeUnitMethodWithObjectArg(slot = 13, value = value)
    }

    open fun removeAtEnd() {
        invokeUnitMethod(slot = 14)
    }

    open fun clear() {
        invokeUnitMethod(slot = 15)
    }

    open fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 16, startIndex = startIndex, capacity = capacity)
    }

    open fun replaceAll(items: List<ComObjectReference>) {
        invokeReplaceAll(slot = 17, items = items)
    }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createVectorViewReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtVectorViewReference = WinRtVectorViewReference(pointer, interfaceId)

    protected open fun invokeIndexOfMethodWithObjectArg(
        slot: Int,
        value: ComObjectReference,
    ): Pair<Boolean, UInt> = invokeIndexOfObjectArg(slot, value)

    protected open fun invokeUnitMethodWithUInt32AndObjectArg(
        slot: Int,
        index: UInt,
        value: ComObjectReference,
    ) {
        invokeUnitMethodWithUInt32ObjectArg(slot, index, value)
    }

    override fun invokeUnitMethodWithUInt32Arg(slot: Int, value: UInt) {
        invokeUnitMethodWithUInt32(slot, value)
    }

    protected open fun invokeGetMany(
        slot: Int,
        startIndex: UInt,
        capacity: Int,
    ): List<IUnknownReference?> = invokeObjectGetMany(slot, startIndex, capacity)

    protected open fun invokeReplaceAll(slot: Int, items: List<ComObjectReference>) {
        invokeReplaceAllObjectArray(slot, items)
    }
}

open class WinRtMapViewReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRtIllegalStateException(
                "IMapView.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(slot = 6, value = key)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun hasKey(key: ComObjectReference): Boolean = invokeBooleanMethodWithObjectArg(slot = 8, value = key)

    open fun split(mapViewInterfaceId: Guid): Pair<WinRtMapViewReference?, WinRtMapViewReference?> =
        invokeSplit(slot = 9, mapViewInterfaceId = mapViewInterfaceId)

    open fun asIterable(iterableInterfaceId: Guid): WinRtIterableReference =
        queryInterface(iterableInterfaceId).getOrThrow().let { reference ->
            createIterableReference(reference.pointer, iterableInterfaceId)
        }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createMapViewReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtIterableReference = WinRtIterableReference(pointer, interfaceId)

    protected open fun invokeSplit(
        slot: Int,
        mapViewInterfaceId: Guid,
    ): Pair<WinRtMapViewReference?, WinRtMapViewReference?> {
        val (firstPointer, secondPointer) = invokeMapViewSplitPointers(slot)
        val first = if (firstPointer == MemorySegment.NULL) null else createMapViewReference(firstPointer, mapViewInterfaceId)
        val second = if (secondPointer == MemorySegment.NULL) null else createMapViewReference(secondPointer, mapViewInterfaceId)
        return first to second
    }
}

open class WinRtMapReference(
    pointer: MemorySegment,
    interfaceId: Guid,
) : WinRtCollectionReferenceBase(pointer, interfaceId) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRtIllegalStateException(
                "IMap.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(slot = 6, value = key)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun size(): UInt = invokeUInt32Method(slot = 7)

    open fun hasKey(key: ComObjectReference): Boolean = invokeBooleanMethodWithObjectArg(slot = 8, value = key)

    open fun getView(mapViewInterfaceId: Guid): WinRtMapViewReference =
        invokeObjectMethod(slot = 9).let { reference -> createMapViewReference(reference.pointer, mapViewInterfaceId) }

    open fun insert(key: ComObjectReference, value: ComObjectReference): Boolean =
        invokeBooleanMethodWithTwoObjectArgs(slot = 10, first = key, second = value)

    open fun remove(key: ComObjectReference) {
        invokeUnitMethodWithObjectArg(slot = 11, value = key)
    }

    open fun clear() {
        invokeUnitMethod(slot = 12)
    }

    protected open fun createUnknownReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createMapViewReference(
        pointer: MemorySegment,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)
}
