package io.github.composefluent.winrt.runtime

private object WinRTCollectionSlots {
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

open class WinRTCollectionReferenceBase(
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

    protected fun invokeNullableAbiMethod(slot: Int): RawAddress? =
        RawObjectAbiSupport.nullableAbiResult { resultOut ->
            invokeSlot(slot, resultOut)
        }

    protected fun invokeNullableObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeSlot(slot, value, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )

    protected fun invokeNullableAbiMethodWithUInt32Arg(slot: Int, value: UInt): RawAddress? =
        RawObjectAbiSupport.nullableAbiResult { resultOut ->
            invokeSlot(slot, value, resultOut)
        }

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(slot, value.pointer.asRawAddress())

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: RawAddress): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeSlot(slot, value, resultOut)
            },
            wrap = { pointer -> IUnknownReference(pointer.asRawComPtr()) },
        )

    protected fun invokeNullableAbiMethodWithObjectArg(slot: Int, value: RawAddress): RawAddress? =
        RawObjectAbiSupport.nullableAbiResult { resultOut ->
            invokeSlot(slot, value, resultOut)
        }

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

open class WinRTKeyValuePairReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun key(): IUnknownReference? =
        invokeNullableObjectMethod(WinRTCollectionSlots.KeyValuePairKey)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun keyAbiOrNull(): RawAddress? =
        invokeNullableAbiMethod(WinRTCollectionSlots.KeyValuePairKey)

    open fun value(): IUnknownReference? =
        invokeNullableObjectMethod(WinRTCollectionSlots.KeyValuePairValue)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun valueAbiOrNull(): RawAddress? =
        invokeNullableAbiMethod(WinRTCollectionSlots.KeyValuePairValue)

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)
}

open class WinRTIterableReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(iteratorInterfaceId: Guid): WinRTIteratorReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRTCollectionSlots.IterableFirst, resultOut) },
            wrap = { pointer -> createIteratorReference(pointer, iteratorInterfaceId) },
        )

    protected open fun createIteratorReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTIteratorReference = WinRTIteratorReference(pointer, interfaceId)
}

open class WinRTIteratorReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun current(): IUnknownReference =
        currentOrNull() ?: throw WinRTIllegalStateException(
            "IIterator.Current returned a null COM reference.",
            KnownHResults.E_BOUNDS,
        )

    open fun currentOrNull(): IUnknownReference? =
        invokeNullableObjectMethod(WinRTCollectionSlots.IteratorCurrent)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun currentAbiOrNull(): RawAddress? =
        invokeNullableAbiMethod(WinRTCollectionSlots.IteratorCurrent)

    open fun hasCurrent(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTCollectionSlots.IteratorHasCurrent, resultOut)
        }

    open fun moveNext(): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTCollectionSlots.IteratorMoveNext, resultOut)
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

open class WinRTVectorViewReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRTIllegalStateException(
                "IVectorView.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(WinRTCollectionSlots.VectorGetAt, index)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun getAtAbiOrNull(index: UInt): RawAddress? =
        invokeNullableAbiMethodWithUInt32Arg(WinRTCollectionSlots.VectorGetAt, index)

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTCollectionSlots.VectorSize, resultOut)
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

open class WinRTVectorReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun getAt(index: UInt): IUnknownReference =
        getAtOrNull(index)
            ?: throw WinRTIllegalStateException(
                "IVector.GetAt returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun getAtOrNull(index: UInt): IUnknownReference? =
        invokeNullableObjectMethodWithUInt32Arg(WinRTCollectionSlots.VectorGetAt, index)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun getAtAbiOrNull(index: UInt): RawAddress? =
        invokeNullableAbiMethodWithUInt32Arg(WinRTCollectionSlots.VectorGetAt, index)

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTCollectionSlots.VectorSize, resultOut)
        }

    open fun getView(vectorViewInterfaceId: Guid): WinRTVectorViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRTCollectionSlots.VectorGetView, resultOut) },
            wrap = { pointer -> createVectorViewReference(pointer, vectorViewInterfaceId) },
        )

    open fun indexOf(value: ComObjectReference): Pair<Boolean, UInt> =
        invokeIndexOfMethodWithObjectArg(slot = 9, value = value)

    open fun setAt(index: UInt, value: ComObjectReference) {
        setAt(index, value.pointer.asRawAddress())
    }

    open fun setAt(index: UInt, valuePointer: RawAddress) {
        val hr = invokeSlot(10, index, valuePointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun insertAt(index: UInt, value: ComObjectReference) {
        insertAt(index, value.pointer.asRawAddress())
    }

    open fun insertAt(index: UInt, valuePointer: RawAddress) {
        val hr = invokeSlot(11, index, valuePointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAt(index: UInt) {
        val hr = invokeSlot(WinRTCollectionSlots.VectorRemoveAt, index)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun append(value: ComObjectReference) {
        append(value.pointer.asRawAddress())
    }

    open fun append(valuePointer: RawAddress) {
        val hr = invokeSlot(WinRTCollectionSlots.VectorAppend, valuePointer)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun removeAtEnd() {
        val hr = invokeSlot(WinRTCollectionSlots.VectorRemoveAtEnd)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRTCollectionSlots.VectorClear)
        WinRTPlatformApi.checkSucceededRaw(hr)
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
    ): WinRTVectorViewReference = WinRTVectorViewReference(pointer, interfaceId)

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

open class WinRTMapViewReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRTIllegalStateException(
                "IMapView.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        lookupOrNull(key.pointer.asRawAddress())

    open fun lookupOrNull(key: RawAddress): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(WinRTCollectionSlots.MapViewLookup, key)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun lookupAbiOrNull(key: RawAddress): RawAddress? =
        invokeNullableAbiMethodWithObjectArg(WinRTCollectionSlots.MapViewLookup, key)

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTCollectionSlots.MapViewSize, resultOut)
        }

    open fun hasKey(key: ComObjectReference): Boolean =
        hasKey(key.pointer.asRawAddress())

    open fun hasKey(key: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTCollectionSlots.MapViewHasKey, key, resultOut)
        }

    open fun split(mapViewInterfaceId: Guid): Pair<WinRTMapViewReference?, WinRTMapViewReference?> =
        invokeSplit(WinRTCollectionSlots.MapViewSplit, mapViewInterfaceId)

    open fun asIterable(iterableInterfaceId: Guid): WinRTIterableReference =
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
    ): WinRTMapViewReference = WinRTMapViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTIterableReference = WinRTIterableReference(pointer, interfaceId)

    protected open fun invokeSplit(
        slot: Int,
        mapViewInterfaceId: Guid,
    ): Pair<WinRTMapViewReference?, WinRTMapViewReference?> {
        val (firstPointer, secondPointer) = invokeMapViewSplitPointers(slot)
        val first = if (PlatformAbi.isNull(firstPointer)) null else createMapViewReference(firstPointer, mapViewInterfaceId)
        val second = if (PlatformAbi.isNull(secondPointer)) null else createMapViewReference(secondPointer, mapViewInterfaceId)
        return first to second
    }
}

open class WinRTMapReference(
    pointer: RawAddress,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRTCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun lookup(key: ComObjectReference): IUnknownReference =
        lookupOrNull(key)
            ?: throw WinRTIllegalStateException(
                "IMap.Lookup returned a null COM reference.",
                KnownHResults.E_BOUNDS,
            )

    open fun lookupOrNull(key: ComObjectReference): IUnknownReference? =
        lookupOrNull(key.pointer.asRawAddress())

    open fun lookupOrNull(key: RawAddress): IUnknownReference? =
        invokeNullableObjectMethodWithObjectArg(WinRTCollectionSlots.MapLookup, key)?.let { reference ->
            createUnknownReference(reference.pointer.asRawAddress(), reference.interfaceId)
        }

    open fun lookupAbiOrNull(key: RawAddress): RawAddress? =
        invokeNullableAbiMethodWithObjectArg(WinRTCollectionSlots.MapLookup, key)

    open fun size(): UInt =
        RawAbiResultSupport.uint32Result { resultOut ->
            invokeSlot(WinRTCollectionSlots.MapSize, resultOut)
        }

    open fun hasKey(key: ComObjectReference): Boolean =
        hasKey(key.pointer.asRawAddress())

    open fun hasKey(key: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTCollectionSlots.MapHasKey, key, resultOut)
        }

    open fun getView(mapViewInterfaceId: Guid): WinRTMapViewReference =
        RawAbiResultSupport.objectResult(
            invoke = { resultOut -> invokeSlot(WinRTCollectionSlots.MapGetView, resultOut) },
            wrap = { pointer -> createMapViewReference(pointer, mapViewInterfaceId) },
        )

    open fun insert(key: ComObjectReference, value: ComObjectReference): Boolean =
        insert(key.pointer.asRawAddress(), value.pointer.asRawAddress())

    open fun insert(key: RawAddress, value: RawAddress): Boolean =
        RawAbiResultSupport.booleanResult { resultOut ->
            invokeSlot(WinRTCollectionSlots.MapInsert, key, value, resultOut)
        }

    open fun remove(key: ComObjectReference) {
        remove(key.pointer.asRawAddress())
    }

    open fun remove(key: RawAddress) {
        val hr = invokeSlot(WinRTCollectionSlots.MapRemove, key)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    open fun clear() {
        val hr = invokeSlot(WinRTCollectionSlots.MapClear)
        WinRTPlatformApi.checkSucceededRaw(hr)
    }

    protected open fun createUnknownReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer.asRawComPtr(), interfaceId)

    protected open fun createMapViewReference(
        pointer: RawAddress,
        interfaceId: Guid,
    ): WinRTMapViewReference = WinRTMapViewReference(pointer, interfaceId)
}
