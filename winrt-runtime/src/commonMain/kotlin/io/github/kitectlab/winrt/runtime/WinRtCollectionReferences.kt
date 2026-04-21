package io.github.kitectlab.winrt.runtime

open class WinRtCollectionReferenceBase(
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : IUnknownReference(pointer, interfaceId, preventReleaseOnDispose = preventReleaseOnDispose) {
    protected fun invokeNullableObjectMethod(slot: Int): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )

    protected fun invokeNullableObjectMethodWithUInt32Arg(slot: Int, value: UInt): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                    ),
                    value.toInt(),
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )

    protected fun invokeNullableObjectMethodWithObjectArg(slot: Int, value: ComObjectReference): IUnknownReference? =
        RawObjectAbiSupport.nullableObjectResult(
            invoke = { resultOut ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    ),
                    value.pointer,
                    resultOut,
                )
            },
            wrap = ::IUnknownReference,
        )

    protected fun invokeIndexOfObjectArg(slot: Int, value: ComObjectReference): Pair<Boolean, UInt> =
        RawObjectAbiSupport.indexOfResult { indexOut, foundOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                value.pointer,
                indexOut,
                foundOut,
            )
        }

    protected fun invokeUnitMethodWithUInt32ObjectArg(slot: Int, index: UInt, value: ComObjectReference) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
            ),
            index.toInt(),
            value.pointer,
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    protected fun invokeUnitMethodWithUInt32(slot: Int, value: UInt) {
        val hr = invokeIntMethod(
            slot = slot,
            descriptor = NativeFunctionDescriptor.of(
                NativeValueLayout.JAVA_INT,
                NativeValueLayout.ADDRESS,
                NativeValueLayout.JAVA_INT,
            ),
            value.toInt(),
        )
        WinRtPlatformApi.checkSucceededRaw(hr)
    }

    protected fun invokeObjectGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> =
        RawObjectAbiSupport.objectGetManyResult(
            capacity = capacity,
            invoke = { itemsOut, countOut ->
                val descriptor = if (startIndex == null) {
                    NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    )
                } else {
                    NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.ADDRESS,
                    )
                }
                if (startIndex == null) {
                    invokeIntMethod(
                        slot = slot,
                        descriptor = descriptor,
                        capacity,
                        itemsOut,
                        countOut,
                    )
                } else {
                    invokeIntMethod(
                        slot = slot,
                        descriptor = descriptor,
                        startIndex.toInt(),
                        capacity,
                        itemsOut,
                        countOut,
                    )
                }
            },
            wrap = { elementPointer ->
                if (NativeInterop.isNull(elementPointer)) {
                    null
                } else {
                    IUnknownReference(elementPointer)
                }
            },
        )

    protected fun invokeReplaceAllObjectArray(slot: Int, items: List<ComObjectReference>) {
        RawObjectAbiSupport.replaceAllObjectArray(
            items = items.map { it.pointer },
            invoke = { size, itemsAbi ->
                invokeIntMethod(
                    slot = slot,
                    descriptor = NativeFunctionDescriptor.of(
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                        NativeValueLayout.JAVA_INT,
                        NativeValueLayout.ADDRESS,
                    ),
                    size,
                    itemsAbi,
                )
            },
        )
    }

    protected fun invokeMapViewSplitPointers(slot: Int): Pair<NativePointer, NativePointer> =
        RawObjectAbiSupport.pointerPairResult { firstOut, secondOut ->
            invokeIntMethod(
                slot = slot,
                descriptor = NativeFunctionDescriptor.of(
                    NativeValueLayout.JAVA_INT,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                    NativeValueLayout.ADDRESS,
                ),
                firstOut,
                secondOut,
            )
        }
}

open class WinRtKeyValuePairReference(
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun key(): IUnknownReference? =
        invokeNullableObjectMethod(slot = 6)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    open fun value(): IUnknownReference? =
        invokeNullableObjectMethod(slot = 7)?.let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    protected open fun createUnknownReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtIterableReference(
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
    open fun first(iteratorInterfaceId: Guid): WinRtIteratorReference =
        invokeObjectMethod(slot = 6).let { reference -> createIteratorReference(reference.pointer, iteratorInterfaceId) }

    protected open fun createIteratorReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtIteratorReference = WinRtIteratorReference(pointer, interfaceId)
}

open class WinRtIteratorReference(
    pointer: NativePointer,
    interfaceId: Guid,
    preventReleaseOnDispose: Boolean = false,
) : WinRtCollectionReferenceBase(pointer, interfaceId, preventReleaseOnDispose) {
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
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun invokeGetMany(
        slot: Int,
        startIndex: UInt?,
        capacity: Int,
    ): List<IUnknownReference?> = invokeObjectGetMany(slot, startIndex, capacity)
}

open class WinRtVectorViewReference(
    pointer: NativePointer,
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
        pointer: NativePointer,
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
    pointer: NativePointer,
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
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createVectorViewReference(
        pointer: NativePointer,
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

    protected fun invokeUnitMethodWithUInt32Arg(slot: Int, value: UInt) {
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
    pointer: NativePointer,
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
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createMapViewReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)

    protected open fun createIterableReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtIterableReference = WinRtIterableReference(pointer, interfaceId)

    protected open fun invokeSplit(
        slot: Int,
        mapViewInterfaceId: Guid,
    ): Pair<WinRtMapViewReference?, WinRtMapViewReference?> {
        val (firstPointer, secondPointer) = invokeMapViewSplitPointers(slot)
        val first = if (NativeInterop.isNull(firstPointer)) null else createMapViewReference(firstPointer, mapViewInterfaceId)
        val second = if (NativeInterop.isNull(secondPointer)) null else createMapViewReference(secondPointer, mapViewInterfaceId)
        return first to second
    }
}

open class WinRtMapReference(
    pointer: NativePointer,
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
        pointer: NativePointer,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)

    protected open fun createMapViewReference(
        pointer: NativePointer,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)
}
