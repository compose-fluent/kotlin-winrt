package io.github.composefluent.winrt.runtime

private object WinRtCollectionSlots {
    const val KeyValuePairKey: Int = 6
    const val KeyValuePairValue: Int = 7
    const val IterableFirst: Int = 6
    const val IteratorCurrent: Int = 6
    const val IteratorHasCurrent: Int = 7
    const val IteratorMoveNext: Int = 8
    const val VectorGetAt: Int = 6
    const val VectorSize: Int = 7
    const val VectorGetView: Int = 8
    const val VectorRemoveAt: Int = 12
    const val VectorAppend: Int = 13
    const val VectorRemoveAtEnd: Int = 14
    const val VectorClear: Int = 15
    const val MapViewLookup: Int = 6
    const val MapViewSize: Int = 7
    const val MapViewHasKey: Int = 8
    const val MapViewSplit: Int = 9
    const val MapLookup: Int = 6
    const val MapSize: Int = 7
    const val MapHasKey: Int = 8
    const val MapGetView: Int = 9
    const val MapInsert: Int = 10
    const val MapRemove: Int = 11
    const val MapClear: Int = 12
}

open class WinRtCollectionReferenceBase(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer.asRawComPtr(), interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    protected fun invokeNullableObjectMethod(slot: Int): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeSlot(slot, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )

    protected fun invokeNullableObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeSlot(slot, value, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(slot, value.pointer.asRawAddress())

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: RawAddress): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeSlot(slot, value, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )

    protected fun invokeIndexOfObjectArg(slot: Int, value: ComObjectReference): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeSlot(slot, value.pointer.asRawAddress(), indexOut, foundOut)
        }

    protected fun invokeObjectGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> =
        RawObjectAbiSupport.objectGetManyResult(
            capacity = capacity,
            invoke = { itemsOut, countOut ->
                if (startIndex == null) {
                    invokeSlot(slot, capacity, itemsOut, countOut)
                } else {
                    invokeSlot(slot, startIndex, capacity, itemsOut, countOut)
                }
            },
            wrap = { elementPointer ->
                if (PlatformAbi.isNull(elementPointer)) {
                    null
                } else {
                    IUnknownReference(elementPointer.asRawComPtr())
                }
            },
        )

    protected fun invokeReplaceAllObjectArray(slot: Int, items: List<ComObjectReference>) {
        RawObjectAbiSupport.replaceAllObjectArray(
            items = items.map { it.pointer.asRawAddress() },
            invoke = { size, itemsAbi ->
                invokeSlot(slot, size, itemsAbi)
            },
        )
    }

    protected fun invokeMapViewSplitPointers(slot: Int): Pair<RawAddress, RawAddress> =
        RawObjectAbiSupport.pointerPairResult { firstOut, secondOut ->
            invokeSlot(slot, firstOut, secondOut)
        }

    protected fun invokeSlot(slot: Int): Int {
        throwIfDisposed()
        return ComVtableInvoker.invoke(comPtr.raw, slot)
    }

    protected fun invokeSlot(slot: Int, arg0: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0)
    }

    protected fun invokeSlot(slot: Int, arg0: UInt): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0)
    }

    protected fun invokeSlot(slot: Int, arg0: RawAddress, arg1: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1)
    }

    protected fun invokeSlot(slot: Int, arg0: Int, arg1: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1)
    }

    protected fun invokeSlot(slot: Int, arg0: UInt, arg1: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1)
    }

    protected fun invokeSlot(slot: Int, arg0: RawAddress, arg1: RawAddress, arg2: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1, arg2)
    }

    protected fun invokeSlot(slot: Int, arg0: Int, arg1: RawAddress, arg2: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1, arg2)
    }

    protected fun invokeSlot(slot: Int, arg0: UInt, arg1: Int, arg2: RawAddress, arg3: RawAddress): Int {
        throwIfDisposed()
        return ComVtableInvoker.invokeArgs(comPtr.raw, slot, arg0, arg1, arg2, arg3)
    }
}

open class WinRtKeyValuePairReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun key(): IUnknownReference? =
        invokeNullableObjectMethod(WinRtCollectionSlots.KeyValuePairKey)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun value(): IUnknownReference? =
        invokeNullableObjectMethod(WinRtCollectionSlots.KeyValuePairValue)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)
}

open class WinRtIterableReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(iteratorInterfaceId: Guid): WinRtIteratorReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRtCollectionSlots.IterableFirst, resultOut) },
            wrap = { pointer -> createIteratorReference(pointer, iteratorInterfaceId) },
        )

    protected open fun createIteratorReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtIteratorReference = WinRtIteratorReference(pointer, interfaceId)
}

open class WinRtIteratorReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun current(): IUnknownReference =
        currentOrNull() ?: throw WinRtIllegalStateException(
            "IIterator.Current returned a null COM reference.",
            KnownHResults.E_BOUNDS,
        )

    open fun currentOrNull(): IUnknownReference? =
        invokeNullableObjectMethod(WinRtCollectionSlots.IteratorCurrent)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun hasCurrent(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtCollectionSlots.IteratorHasCurrent, resultOut)
        }

    open fun moveNext(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtCollectionSlots.IteratorMoveNext, resultOut)
        }

    open fun getMany(capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 9, startIndex = null, capacity = capacity)
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun invokeGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> = invokeObjectGetMany(slot, startIndex, capacity)
}

open class WinRtVectorViewReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IVectorView.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(WinRtCollectionSlots.VectorGetAt, index)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtCollectionSlots.VectorSize, resultOut)
        }

    open fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> =
        invokeIndexOfMethodWithObjectArg(slot = 8, value = value)

    open fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 9, startIndex = startIndex, capacity = capacity)
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

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
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRtIllegalStateException(
                "IVector.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(WinRtCollectionSlots.VectorGetAt, index)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtCollectionSlots.VectorSize, resultOut)
        }

    open fun getView(vectorViewInterfaceId: Guid): WinRtVectorViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRtCollectionSlots.VectorGetView, resultOut) },
            wrap = { pointer -> createVectorViewReference(pointer, vectorViewInterfaceId) },
        )

    open fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> =
        invokeIndexOfMethodWithObjectArg(slot = 9, value = value)

    open fun setAt(index: UInt, value: ComObjectReference) {
        setAt(index, value.pointer.asRawAddress())
    }

    open fun setAt(index: UInt, valuePointer: RawAddress) {
        val hr = invokeSlot(10, index, valuePointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun insertAt(index: UInt, value: ComObjectReference) {
        insertAt(index, value.pointer.asRawAddress())
    }

    open fun insertAt(index: UInt, valuePointer: RawAddress) {
        val hr = invokeSlot(11, index, valuePointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeSlot(WinRtCollectionSlots.VectorRemoveAt, index)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun append(value: ComObjectReference) {
        append(value.pointer.asRawAddress())
    }

    open fun append(valuePointer: RawAddress) {
        val hr = invokeSlot(WinRtCollectionSlots.VectorAppend, valuePointer)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAtEnd() {
        val hr = invokeSlot(WinRtCollectionSlots.VectorRemoveAtEnd)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRtCollectionSlots.VectorClear)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun getMany(startIndex: UInt, capacity: Int): List<IUnknownReference?> {
        require(capacity >= 0) { "capacity must be non-negative." }
        return invokeGetMany(slot = 16, startIndex = startIndex, capacity = capacity)
    }

    open fun replaceAll(items: List<ComObjectReference>) {
        invokeReplaceAll(slot = 17, items = items)
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createVectorViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtVectorViewReference = WinRtVectorViewReference(pointer, interfaceId)

    protected open fun invokeIndexOfMethodWithObjectArg(
        slot: Int,
        value: ComObjectReference,
    ): Pair<Boolean, UInt> = invokeIndexOfObjectArg(slot, value)

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
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRtIllegalStateException(
                "IMapView.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        lookupOrNull(key.pointer.asRawAddress())

    open fun lookupOrNull(key: RawAddress): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(WinRtCollectionSlots.MapViewLookup, key)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtCollectionSlots.MapViewSize, resultOut)
        }

    open fun hasKey(key: ComObjectReference): Boolean =
        hasKey(key.pointer.asRawAddress())

    open fun hasKey(key: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtCollectionSlots.MapViewHasKey, key, resultOut)
        }

    open fun split(mapViewInterfaceId: Guid): Pair<WinRtMapViewReference?, WinRtMapViewReference?> =
        invokeSplit(WinRtCollectionSlots.MapViewSplit, mapViewInterfaceId)

    open fun asIterable(iterableInterfaceId: Guid): WinRtIterableReference =
        queryInterface(iterableInterfaceId).getOrThrow().let { reference ->
            createIterableReference(reference.pointer.asRawAddress(), iterableInterfaceId)
        }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createMapViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtIterableReference = WinRtIterableReference(pointer, interfaceId)

    protected open fun invokeSplit(
        slot: Int,
        mapViewInterfaceId: Guid,
    ): Pair<WinRtMapViewReference?, WinRtMapViewReference?> {
        val (firstPointer, secondPointer) = invokeMapViewSplitPointers(slot)
        val first = if (PlatformAbi.isNull(firstPointer)) null else createMapViewReference(firstPointer, mapViewInterfaceId)
        val second = if (PlatformAbi.isNull(secondPointer)) null else createMapViewReference(secondPointer, mapViewInterfaceId)
        return first to second
    }
}

open class WinRtMapReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRtIllegalStateException(
                "IMap.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        lookupOrNull(key.pointer.asRawAddress())

    open fun lookupOrNull(key: RawAddress): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(WinRtCollectionSlots.MapLookup, key)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRtCollectionSlots.MapSize, resultOut)
        }

    open fun hasKey(key: ComObjectReference): Boolean =
        hasKey(key.pointer.asRawAddress())

    open fun hasKey(key: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtCollectionSlots.MapHasKey, key, resultOut)
        }

    open fun getView(mapViewInterfaceId: Guid): WinRtMapViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRtCollectionSlots.MapGetView, resultOut) },
            wrap = { pointer -> createMapViewReference(pointer, mapViewInterfaceId) },
        )

    open fun insert(key: ComObjectReference, value: ComObjectReference): Boolean =
        insert(key.pointer.asRawAddress(), value.pointer.asRawAddress())

    open fun insert(key: RawAddress, value: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRtCollectionSlots.MapInsert, key, value, resultOut)
        }

    open fun remove(key: ComObjectReference) {
        remove(key.pointer.asRawAddress())
    }

    open fun remove(key: RawAddress) {
        val hr = invokeSlot(WinRtCollectionSlots.MapRemove, key)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRtCollectionSlots.MapClear)
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createMapViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)
}
