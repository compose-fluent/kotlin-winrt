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
