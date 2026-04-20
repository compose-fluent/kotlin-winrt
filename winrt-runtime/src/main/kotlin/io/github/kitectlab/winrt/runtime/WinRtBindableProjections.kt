package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMutableList

/**
 * Runtime helper layer corresponding to `.cswinrt/src/WinRT.Runtime/Projections/Bindable.net5.cs`.
 *
 * This slice owns IBindable* RCW/CCW helpers plus the non-generic inspectable/object marshalling needed by
 * bindable collections. Full value-boxing and `IPropertyValue` parity remain in Runtime 1.17.
 */
typealias WinRtBindableProjectionMarshaler = WinRtProjectionMarshaler

internal class WinRtBindableInspectableValue private constructor(
    private val inspectable: IInspectableReference,
) : IWinRTObject, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val cleanable = finalizationHook.register(this) { inspectable.close() }

    override val nativeObject: ComObjectReference
        get() = inspectable

    override val primaryTypeHandle: WinRtTypeHandle
        get() = bindableInspectableTypeHandle

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.close()
        }
    }

    override fun toString(): String = inspectable.tryGetRuntimeClassName() ?: "Inspectable(${inspectable.pointer})"

    companion object {
        private val finalizationHook = FinalizationHook()

        fun fromOwnedReference(reference: ComObjectReference): WinRtBindableInspectableValue {
            val inspectable = if (reference is IInspectableReference) {
                reference
            } else {
                try {
                    reference.asInspectable()
                } finally {
                    reference.close()
                }
            }
            return WinRtBindableInspectableValue(inspectable)
        }
    }
}

internal object WinRtBindableObjectMarshaller {
    fun createMarshaler(value: Any?): WinRtBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowInspectableReference(value)?.let { return WinRtBindableProjectionMarshaler.borrowed(it) }
        val reference = ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
        return WinRtBindableProjectionMarshaler.owned(reference)
    }

    fun fromManaged(value: Any?): MemorySegment {
        if (value == null) {
            return MemorySegment.NULL
        }
        borrowInspectableReference(value)?.use { return it.getRef() }
        return ComWrappersSupport.createCCWForObject(value, IID.IInspectable).useAndGetRef()
    }

    fun fromOwnedAbi(pointer: MemorySegment): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        return fromOwnedReference(IUnknownReference(pointer, IID.IInspectable))
    }

    fun fromOwnedReference(reference: IUnknownReference?): Any? {
        if (reference == null) {
            return null
        }
        findManagedValue(reference.pointer)?.let { managed ->
            reference.close()
            return managed
        }
        WinRtValueBoxing.tryProjectBorrowedInspectable(reference.pointer)?.let { propertyValue ->
            reference.close()
            return propertyValue
        }
        return WinRtBindableInspectableValue.fromOwnedReference(reference)
    }

    fun fromBorrowedAbi(pointer: MemorySegment): Any? {
        if (pointer == MemorySegment.NULL) {
            return null
        }
        findManagedValue(pointer)?.let { return it }
        WinRtValueBoxing.tryProjectBorrowedInspectable(pointer)?.let { return it }
        val borrowed = IUnknownReference(pointer, IID.IInspectable, preventReleaseOnDispose = true)
        val inspectable = try {
            borrowed.asInspectable()
        } finally {
            borrowed.close()
        }
        return WinRtBindableInspectableValue.fromOwnedReference(inspectable)
    }

    private fun findManagedValue(pointer: MemorySegment): Any? =
        WinRtInspectableComObject.findManagedValue(pointer)

    private fun borrowInspectableReference(value: Any?): IInspectableReference? =
        when (value) {
            null -> null
            is WinRtBindableInspectableValue -> IInspectableReference(value.inspectableRef().getRef(), IID.IInspectable)
            is IInspectableReference -> IInspectableReference(value.getRef(), IID.IInspectable)
            is IUnknownReference -> value.asInspectable()
            is ComObjectReference -> value.tryAsInspectable()
            is IWinRTObject -> if (value.hasUnwrappableNativeObject) value.nativeObject.tryAsInspectable() else null
            is MemorySegment ->
                if (value == MemorySegment.NULL) {
                    null
                } else {
                    IUnknownReference(value, IID.IInspectable, preventReleaseOnDispose = true).asInspectable()
                }

            else -> null
        }

    private fun WinRtBindableInspectableValue.inspectableRef(): IInspectableReference =
        nativeObject as IInspectableReference
}

object WinRtBindableIterableProjection {
    class FromAbiHelper internal constructor(
        private val iterable: WinRtBindableIterableReference,
    ) : Iterable<Any?>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

        override val primaryTypeHandle: WinRtTypeHandle
            get() = bindableIterableTypeHandle

        override fun iterator(): Iterator<Any?> =
            WinRtBindableIteratorProjection.FromAbiHelper(iterable.first())

        override fun close() {
            iterable.close()
        }
    }

    internal class ToAbiHelper(
        private val managed: Iterable<Any?>,
    ) {
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = WinRtBindableInterfaceIds.IBindableIterable,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as MemorySegment
                            resultOut.writeReturnedPointer(WinRtBindableIteratorProjection.detachReference(managed.iterator()))
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtBindableProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, WinRtBindableInterfaceIds.IBindableIterable)

        fun detachReference(): MemorySegment = host.detachReference(WinRtBindableInterfaceIds.IBindableIterable)
    }

    fun createMarshaler(value: Iterable<Any?>?): WinRtBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableIterableTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: Iterable<Any?>?): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, bindableIterableTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: MemorySegment): FromAbiHelper? =
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            FromAbiHelper(WinRtBindableIterableReference(pointer, WinRtBindableInterfaceIds.IBindableIterable))
        }
}

object WinRtBindableIteratorProjection {
    class FromAbiHelper internal constructor(
        private val iterator: WinRtBindableIteratorReference,
    ) : Iterator<Any?>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterator

        override val primaryTypeHandle: WinRtTypeHandle
            get() = bindableIteratorTypeHandle

        private val state = IteratorState(iterator)

        override fun hasNext(): Boolean = state.hasCurrent

        override fun next(): Any? = state.takeNext()

        override fun close() {
            iterator.close()
        }
    }

    internal class ToAbiHelper(
        managed: Iterator<Any?>,
    ) {
        private val state = ManagedIteratorState(managed)
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = WinRtBindableInterfaceIds.IBindableIterator,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as MemorySegment
                            if (!state.hasCurrent) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            resultOut.writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(state.currentValue))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            (rawArgs[0] as MemorySegment).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            state.moveNext()
                            (rawArgs[0] as MemorySegment).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) {
                            KnownHResults.E_NOTIMPL.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtBindableProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, WinRtBindableInterfaceIds.IBindableIterator)

        fun detachReference(): MemorySegment = host.detachReference(WinRtBindableInterfaceIds.IBindableIterator)
    }

    internal fun detachReference(managed: Iterator<Any?>): MemorySegment =
        ToAbiHelper(managed).detachReference()

    private class ManagedIteratorState(
        iterator: Iterator<Any?>,
    ) {
        private val iterator = iterator
        var hasCurrent: Boolean = iterator.hasNext()
            private set
        var currentValue: Any? = if (hasCurrent) iterator.next() else null
            private set

        fun moveNext(): Boolean {
            hasCurrent = iterator.hasNext()
            currentValue = if (hasCurrent) iterator.next() else null
            return hasCurrent
        }
    }

    private class IteratorState(
        private val iterator: WinRtBindableIteratorReference,
    ) {
        var hasCurrent: Boolean = iterator.hasCurrent()
            private set
        private var currentValue: Any? = if (hasCurrent) {
            WinRtBindableObjectMarshaller.fromOwnedReference(iterator.currentOrNull())
        } else {
            null
        }

        fun takeNext(): Any? {
            if (!hasCurrent) {
                throw NoSuchElementException("IBindableIterator has no remaining elements.")
            }
            val value = currentValue
            hasCurrent = iterator.moveNext()
            currentValue = if (hasCurrent) {
                WinRtBindableObjectMarshaller.fromOwnedReference(iterator.currentOrNull())
            } else {
                null
            }
            return value
        }
    }
}

object WinRtBindableVectorViewProjection {
    class FromAbiHelper internal constructor(
        private val vectorView: WinRtBindableVectorViewReference,
    ) : AbstractList<Any?>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vectorView

        override val primaryTypeHandle: WinRtTypeHandle
            get() = bindableVectorViewTypeHandle

        override val size: Int
            get() = vectorView.size().toIntChecked("IBindableVectorView.Size")

        override fun get(index: Int): Any? {
            require(index >= 0) { "index must be non-negative." }
            return WinRtBindableObjectMarshaller.fromOwnedReference(vectorView.getAtOrNull(index.toUInt()))
        }

        override fun close() {
            vectorView.close()
        }
    }

    internal class ToAbiHelper(
        private val managed: List<Any?>,
    ) {
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                bindableIterableDefinition { managed.iterator() },
                WinRtInspectableInterfaceDefinition(
                    interfaceId = WinRtBindableInterfaceIds.IBindableVectorView,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as MemorySegment
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(value))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            (rawArgs[0] as MemorySegment).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val value = WinRtBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as MemorySegment)
                            val indexOut = rawArgs[1] as MemorySegment
                            val foundOut = rawArgs[2] as MemorySegment
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtBindableProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, WinRtBindableInterfaceIds.IBindableVectorView)

        fun detachReference(): MemorySegment = host.detachReference(WinRtBindableInterfaceIds.IBindableVectorView)
    }

    fun createMarshaler(value: List<Any?>?): WinRtBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableVectorViewTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: List<Any?>?): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, bindableVectorViewTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: MemorySegment): FromAbiHelper? =
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            FromAbiHelper(WinRtBindableVectorViewReference(pointer, WinRtBindableInterfaceIds.IBindableVectorView))
        }
}

object WinRtBindableVectorProjection {
    class FromAbiHelper internal constructor(
        private val vector: WinRtBindableVectorReference,
    ) : AbstractMutableList<Any?>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vector

        override val primaryTypeHandle: WinRtTypeHandle
            get() = bindableVectorTypeHandle

        override val size: Int
            get() = vector.size().toIntChecked("IBindableVector.Size")

        override fun get(index: Int): Any? {
            require(index >= 0) { "index must be non-negative." }
            return WinRtBindableObjectMarshaller.fromOwnedReference(vector.getAtOrNull(index.toUInt()))
        }

        override fun set(index: Int, element: Any?): Any? {
            val previous = get(index)
            WinRtBindableObjectMarshaller.createMarshaler(element).use { marshaler ->
                vector.setAt(index.toUInt(), marshaler?.abi ?: MemorySegment.NULL)
            }
            return previous
        }

        override fun add(index: Int, element: Any?) {
            require(index >= 0) { "index must be non-negative." }
            WinRtBindableObjectMarshaller.createMarshaler(element).use { marshaler ->
                if (index == size) {
                    vector.append(marshaler?.abi ?: MemorySegment.NULL)
                } else {
                    vector.insertAt(index.toUInt(), marshaler?.abi ?: MemorySegment.NULL)
                }
            }
        }

        override fun removeAt(index: Int): Any? {
            val previous = get(index)
            vector.removeAt(index.toUInt())
            return previous
        }

        override fun clear() {
            vector.clear()
        }

        override fun close() {
            vector.close()
        }
    }

    internal class ToAbiHelper(
        private val managed: MutableList<Any?>,
    ) {
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                bindableIterableDefinition { managed.iterator() },
                WinRtInspectableInterfaceDefinition(
                    interfaceId = WinRtBindableInterfaceIds.IBindableVector,
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as MemorySegment
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeReturnedPointer(WinRtBindableObjectMarshaller.fromManaged(value))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            (rawArgs[0] as MemorySegment).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as MemorySegment
                            resultOut.writeReturnedPointer(WinRtBindableVectorViewProjection.ToAbiHelper(managed).detachReference())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val value = WinRtBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as MemorySegment)
                            val indexOut = rawArgs[1] as MemorySegment
                            val foundOut = rawArgs[2] as MemorySegment
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed[index.toInt()] = WinRtBindableObjectMarshaller.fromBorrowedAbi(rawArgs[1] as MemorySegment)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() > managed.size) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.add(index.toInt(), WinRtBindableObjectMarshaller.fromBorrowedAbi(rawArgs[1] as MemorySegment))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                            ),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.removeAt(index.toInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            managed.add(WinRtBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as MemorySegment))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) {
                            if (managed.isEmpty()) {
                                KnownHResults.E_BOUNDS.value
                            } else {
                                managed.removeAt(managed.lastIndex)
                                KnownHResults.S_OK.value
                            }
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                            ),
                        ) {
                            managed.clear()
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtBindableProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, WinRtBindableInterfaceIds.IBindableVector)

        fun detachReference(): MemorySegment = host.detachReference(WinRtBindableInterfaceIds.IBindableVector)
    }

    fun createMarshaler(value: MutableList<Any?>?): WinRtBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableVectorTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: MutableList<Any?>?): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, bindableVectorTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: MemorySegment): FromAbiHelper? =
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            FromAbiHelper(WinRtBindableVectorReference(pointer, WinRtBindableInterfaceIds.IBindableVector))
        }
}

private val bindableInspectableTypeHandle =
    WinRtTypeHandle("kotlin.Any?", IID.IInspectable)

private val bindableIterableTypeHandle =
    WinRtTypeHandle("kotlin.collections.Iterable<kotlin.Any?>", WinRtBindableInterfaceIds.IBindableIterable)

private val bindableIteratorTypeHandle =
    WinRtTypeHandle("kotlin.collections.Iterator<kotlin.Any?>", WinRtBindableInterfaceIds.IBindableIterator)

private val bindableVectorViewTypeHandle =
    WinRtTypeHandle("kotlin.collections.List<kotlin.Any?>", WinRtBindableInterfaceIds.IBindableVectorView)

private val bindableVectorTypeHandle =
    WinRtTypeHandle("kotlin.collections.MutableList<kotlin.Any?>", WinRtBindableInterfaceIds.IBindableVector)

private fun bindableIterableDefinition(
    iteratorFactory: () -> Iterator<Any?>,
): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = WinRtBindableInterfaceIds.IBindableIterable,
        methods = listOf(
            WinRtInspectableMethodDefinition(
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            ) { rawArgs ->
                val resultOut = rawArgs[0] as MemorySegment
                resultOut.writeReturnedPointer(WinRtBindableIteratorProjection.detachReference(iteratorFactory()))
                KnownHResults.S_OK.value
            },
        ),
    )

private fun UInt.toIntChecked(operation: String): Int {
    if (this > Int.MAX_VALUE.toUInt()) {
        throw IllegalStateException("$operation exceeded Int.MAX_VALUE.")
    }
    return toInt()
}

private fun MemorySegment.writeReturnedPointer(pointer: MemorySegment) {
    reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, pointer)
}

private fun MemorySegment.writeBoolean(value: Boolean) {
    reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, if (value) 1 else 0)
}

private fun MemorySegment.writeUInt32(value: UInt) {
    reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value.toInt())
}
