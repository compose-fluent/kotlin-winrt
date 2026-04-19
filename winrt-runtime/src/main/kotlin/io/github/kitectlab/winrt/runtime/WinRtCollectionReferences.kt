package io.github.kitectlab.winrt.runtime

open class WinRtIterableReference(
    pointer: java.lang.foreign.MemorySegment,
    interfaceId: Guid,
) : IUnknownReference(pointer, interfaceId) {
    fun first(iteratorInterfaceId: Guid): WinRtIteratorReference =
        invokeObjectMethod(slot = 6).let { reference -> createIteratorReference(reference.pointer, iteratorInterfaceId) }

    protected open fun createIteratorReference(
        pointer: java.lang.foreign.MemorySegment,
        interfaceId: Guid,
    ): WinRtIteratorReference = WinRtIteratorReference(pointer, interfaceId)
}

open class WinRtIteratorReference(
    pointer: java.lang.foreign.MemorySegment,
    interfaceId: Guid,
) : IUnknownReference(pointer, interfaceId) {
    fun current(): IUnknownReference =
        invokeObjectMethod(slot = 6).let { reference -> createUnknownReference(reference.pointer, reference.interfaceId) }

    fun hasCurrent(): Boolean = invokeBooleanMethod(slot = 7)

    fun moveNext(): Boolean = invokeBooleanMethod(slot = 8)

    protected open fun createUnknownReference(
        pointer: java.lang.foreign.MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtVectorViewReference(
    pointer: java.lang.foreign.MemorySegment,
    interfaceId: Guid,
) : WinRtIterableReference(pointer, interfaceId) {
    fun getAt(index: UInt): IUnknownReference =
        invokeObjectMethodWithUInt32Arg(slot = 6, value = index).let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    fun size(): UInt = invokeUInt32Method(slot = 7)

    protected open fun createUnknownReference(
        pointer: java.lang.foreign.MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtMapViewReference(
    pointer: java.lang.foreign.MemorySegment,
    interfaceId: Guid,
) : WinRtIterableReference(pointer, interfaceId) {
    fun lookup(key: ComObjectReference): IUnknownReference =
        invokeObjectMethodWithObjectArg(slot = 6, value = key).let { reference ->
            createUnknownReference(reference.pointer, reference.interfaceId)
        }

    fun size(): UInt = invokeUInt32Method(slot = 7)

    fun hasKey(key: ComObjectReference): Boolean = invokeBooleanMethodWithObjectArg(slot = 8, value = key)

    protected open fun createUnknownReference(
        pointer: java.lang.foreign.MemorySegment,
        interfaceId: Guid,
    ): IUnknownReference = IUnknownReference(pointer, interfaceId)
}

open class WinRtMapReference(
    pointer: java.lang.foreign.MemorySegment,
    interfaceId: Guid,
) : WinRtMapViewReference(pointer, interfaceId) {
    fun getView(mapViewInterfaceId: Guid): WinRtMapViewReference =
        invokeObjectMethod(slot = 9).let { reference -> createMapViewReference(reference.pointer, mapViewInterfaceId) }

    fun insert(key: ComObjectReference, value: ComObjectReference): Boolean =
        invokeBooleanMethodWithTwoObjectArgs(slot = 10, first = key, second = value)

    fun remove(key: ComObjectReference) {
        invokeUnitMethodWithObjectArg(slot = 11, value = key)
    }

    fun clear() {
        invokeUnitMethod(slot = 12)
    }

    protected open fun createMapViewReference(
        pointer: java.lang.foreign.MemorySegment,
        interfaceId: Guid,
    ): WinRtMapViewReference = WinRtMapViewReference(pointer, interfaceId)
}
