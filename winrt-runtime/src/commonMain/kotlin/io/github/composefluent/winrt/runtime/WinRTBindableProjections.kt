@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package io.github.composefluent.winrt.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMutableList

/**
 * Runtime helper layer corresponding to `.cswinrt/src/WinRT.Runtime/Projections/Bindable.net5.cs`.
 *
 * This slice owns IBindable* RCW/CCW helpers plus the non-generic inspectable/object marshalling needed by
 * bindable collections. Full value-boxing and `IPropertyValue` parity remain in Runtime 1.17.
 */
typealias WinRTBindableProjectionMarshaler = WinRTProjectionMarshaler

internal class WinRTBindableInspectableValue private constructor(
    private val inspectable: IInspectableReference,
) : IWinRTObject, AutoCloseable {
    private val closed = AtomicInt(0)
    private val cleanable = finalizationHook.register(this) { inspectable.close() }

    override val nativeObject: ComObjectReference
        get() = inspectable

    override val primaryTypeHandle: WinRTTypeHandle
        get() = bindableInspectableTypeHandle

    override fun close() {
        if (closed.compareAndSet(0, 1)) {
            cleanable.close()
        }
    }

    override fun toString(): String = inspectable.tryGetRuntimeClassName() ?: "Inspectable(${inspectable.pointer.asRawAddress()})"

    companion object {
        private val finalizationHook = FinalizationHook()

        fun fromOwnedReference(reference: ComObjectReference): WinRTBindableInspectableValue {
            val inspectable = if (reference is IInspectableReference) {
                reference
            } else {
                try {
                    reference.asInspectable()
                } finally {
                    reference.close()
                }
            }
            return WinRTBindableInspectableValue(inspectable)
        }
    }
}

internal object WinRTBindableObjectMarshaller {
    fun createMarshaler(value: Any?): WinRTBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        return WinRTBindableProjectionMarshaler.objectMarshaler(WinRTObjectMarshaller.createMarshaler(value))
    }

    fun fromManaged(value: Any?): RawAddress {
        if (value == null) {
            return PlatformAbi.nullPointer
        }
        return WinRTObjectMarshaller.fromManaged(value)
    }

    fun fromOwnedAbi(pointer: RawAddress): Any? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }
        return fromOwnedReference(IUnknownReference(pointer.asRawComPtr(), IID.IInspectable))
    }

    fun fromOwnedReference(reference: IUnknownReference?): Any? {
        if (reference == null) {
            return null
        }
        findManagedValue(reference.pointer.asRawAddress())?.let { managed ->
            reference.close()
            return managed
        }
        platformTryProjectBindableInspectable(reference.pointer.asRawAddress())?.let { propertyValue ->
            reference.close()
            return propertyValue
        }
        return WinRTBindableInspectableValue.fromOwnedReference(reference)
    }

    fun fromBorrowedAbi(pointer: RawAddress): Any? {
        if (PlatformAbi.isNull(pointer)) {
            return null
        }
        findManagedValue(pointer)?.let { return it }
        platformTryProjectBindableInspectable(pointer)?.let { return it }
        val borrowed = IUnknownReference(pointer.asRawComPtr(), IID.IInspectable, preventReleaseOnDispose = true)
        val inspectable = try {
            borrowed.asInspectable()
        } finally {
            borrowed.close()
        }
        return WinRTBindableInspectableValue.fromOwnedReference(inspectable)
    }

    private fun findManagedValue(pointer: RawAddress): Any? =
        WinRTInspectableComObject.findManagedValue(pointer)

}

object WinRTBindableIterableProjection {
    class FromAbiHelper internal constructor(
        private val iterable: WinRTBindableIterableReference,
    ) : Iterable<Any?>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

        override val primaryTypeHandle: WinRTTypeHandle
            get() = bindableIterableTypeHandle

        override fun iterator(): Iterator<Any?> =
            WinRTBindableIteratorProjection.FromAbiHelper(iterable.first())

        override fun close() {
            iterable.close()
        }
    }

    internal class ToAbiHelper(
        private val managed: Iterable<Any?>,
    ) {
        private val host = createBindableHost(
            managedValue = managed,
            defaultInterfaceId = WinRTBindableInterfaceIds.IBindableIterable,
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = WinRTBindableInterfaceIds.IBindableIterable,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(WinRTBindableIteratorProjection.detachReference(managed.iterator()))
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRTBindableProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, WinRTBindableInterfaceIds.IBindableIterable)

        fun detachReference(): RawAddress = host.detachReference(WinRTBindableInterfaceIds.IBindableIterable)
    }

    fun createMarshaler(value: Iterable<Any?>?): WinRTBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableIterableTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: Iterable<Any?>?): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, bindableIterableTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: RawAddress): FromAbiHelper? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(WinRTBindableIterableReference(pointer, WinRTBindableInterfaceIds.IBindableIterable))
        }

    fun fromAbi(reference: IUnknownReference): FromAbiHelper =
        reference.queryInterface(WinRTBindableInterfaceIds.IBindableIterable).getOrThrow().use {
            FromAbiHelper(WinRTBindableIterableReference(it.getRefPointer().asRawAddress(), WinRTBindableInterfaceIds.IBindableIterable))
        }
}

object WinRTBindableIteratorProjection {
    class FromAbiHelper internal constructor(
        private val iterator: WinRTBindableIteratorReference,
    ) : Iterator<Any?>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterator

        override val primaryTypeHandle: WinRTTypeHandle
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
        private val host = createBindableHost(
            managedValue = state,
            defaultInterfaceId = WinRTBindableInterfaceIds.IBindableIterator,
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = WinRTBindableInterfaceIds.IBindableIterator,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            if (!state.hasCurrent) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            resultOut.writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(state.currentValue))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            state.moveNext()
                            (rawArgs[0] as RawAddress).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) {
                            KnownHResults.E_NOTIMPL.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRTBindableProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, WinRTBindableInterfaceIds.IBindableIterator)

        fun detachReference(): RawAddress = host.detachReference(WinRTBindableInterfaceIds.IBindableIterator)
    }

    internal fun detachReference(managed: Iterator<Any?>): RawAddress =
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
        private val iterator: WinRTBindableIteratorReference,
    ) {
        var hasCurrent: Boolean = iterator.hasCurrent()
            private set
        private var currentValue: Any? = if (hasCurrent) {
            WinRTBindableObjectMarshaller.fromOwnedReference(iterator.currentOrNull())
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
                WinRTBindableObjectMarshaller.fromOwnedReference(iterator.currentOrNull())
            } else {
                null
            }
            return value
        }
    }
}

object WinRTBindableVectorViewProjection {
    class FromAbiHelper internal constructor(
        private val vectorView: WinRTBindableVectorViewReference,
    ) : AbstractList<Any?>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vectorView

        override val primaryTypeHandle: WinRTTypeHandle
            get() = bindableVectorViewTypeHandle

        override val size: Int
            get() = vectorView.size().toIntChecked("IBindableVectorView.Size")

        override fun get(index: Int): Any? {
            require(index >= 0) { "index must be non-negative." }
            return WinRTBindableObjectMarshaller.fromOwnedReference(vectorView.getAtOrNull(index.toUInt()))
        }

        override fun close() {
            vectorView.close()
        }
    }

    internal class ToAbiHelper(
        private val managed: List<Any?>,
    ) {
        private val host = createBindableHost(
            managedValue = managed,
            defaultInterfaceId = WinRTBindableInterfaceIds.IBindableVectorView,
            interfaceDefinitions = listOf(
                bindableIterableDefinition { managed.iterator() },
                WinRTInspectableInterfaceDefinition(
                    interfaceId = WinRTBindableInterfaceIds.IBindableVectorView,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index.toInt() !in managed.indices) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(value))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val value = WinRTBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as RawAddress)
                            val indexOut = rawArgs[1] as RawAddress
                            val foundOut = rawArgs[2] as RawAddress
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRTBindableProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, WinRTBindableInterfaceIds.IBindableVectorView)

        fun detachReference(): RawAddress = host.detachReference(WinRTBindableInterfaceIds.IBindableVectorView)
    }

    fun createMarshaler(value: List<Any?>?): WinRTBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableVectorViewTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: List<Any?>?): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, bindableVectorViewTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: RawAddress): FromAbiHelper? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(WinRTBindableVectorViewReference(pointer, WinRTBindableInterfaceIds.IBindableVectorView))
        }

    fun fromAbi(reference: IUnknownReference): FromAbiHelper =
        reference.queryInterface(WinRTBindableInterfaceIds.IBindableVectorView).getOrThrow().use {
            FromAbiHelper(WinRTBindableVectorViewReference(it.getRefPointer().asRawAddress(), WinRTBindableInterfaceIds.IBindableVectorView))
        }
}

object WinRTBindableVectorProjection {
    class FromAbiHelper internal constructor(
        private val vector: WinRTBindableVectorReference,
    ) : AbstractMutableList<Any?>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vector

        override val primaryTypeHandle: WinRTTypeHandle
            get() = bindableVectorTypeHandle

        override val size: Int
            get() = vector.size().toIntChecked("IBindableVector.Size")

        override fun get(index: Int): Any? {
            require(index >= 0) { "index must be non-negative." }
            return WinRTBindableObjectMarshaller.fromOwnedReference(vector.getAtOrNull(index.toUInt()))
        }

        override fun set(index: Int, element: Any?): Any? {
            val previous = get(index)
            WinRTBindableObjectMarshaller.createMarshaler(element).use { marshaler ->
                vector.setAt(index.toUInt(), marshaler?.abi ?: PlatformAbi.nullPointer)
            }
            return previous
        }

        override fun add(index: Int, element: Any?) {
            require(index >= 0) { "index must be non-negative." }
            WinRTBindableObjectMarshaller.createMarshaler(element).use { marshaler ->
                if (index == size) {
                    vector.append(marshaler?.abi ?: PlatformAbi.nullPointer)
                } else {
                    vector.insertAt(index.toUInt(), marshaler?.abi ?: PlatformAbi.nullPointer)
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
        private val host = createBindableHost(
            managedValue = managed,
            defaultInterfaceId = WinRTBindableInterfaceIds.IBindableVector,
            interfaceDefinitions = listOf(
                bindableIterableDefinition { managed.iterator() },
                WinRTInspectableInterfaceDefinition(
                    interfaceId = WinRTBindableInterfaceIds.IBindableVector,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index.toInt() !in managed.indices) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeReturnedPointer(WinRTBindableObjectMarshaller.fromManaged(value))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(WinRTBindableVectorViewProjection.ToAbiHelper(managed).detachReference())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val value = WinRTBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as RawAddress)
                            val indexOut = rawArgs[1] as RawAddress
                            val foundOut = rawArgs[2] as RawAddress
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() !in managed.indices) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed[index.toInt()] = WinRTBindableObjectMarshaller.fromBorrowedAbi(rawArgs[1] as RawAddress)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() > managed.size) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.add(index.toInt(), WinRTBindableObjectMarshaller.fromBorrowedAbi(rawArgs[1] as RawAddress))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() !in managed.indices) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.removeAt(index.toInt())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            managed.add(WinRTBindableObjectMarshaller.fromBorrowedAbi(rawArgs[0] as RawAddress))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(),
                        ) {
                            if (managed.isEmpty()) {
                                KnownHResults.E_BOUNDS.value
                            } else {
                                managed.removeAt(managed.lastIndex)
                                KnownHResults.S_OK.value
                            }
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(),
                        ) {
                            managed.clear()
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRTBindableProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, WinRTBindableInterfaceIds.IBindableVector)

        fun detachReference(): RawAddress = host.detachReference(WinRTBindableInterfaceIds.IBindableVector)
    }

    fun createMarshaler(value: MutableList<Any?>?): WinRTBindableProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, bindableVectorTypeHandle)?.let { return it }
        return ToAbiHelper(value).createMarshaler()
    }

    fun fromManaged(value: MutableList<Any?>?): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, bindableVectorTypeHandle) ?: ToAbiHelper(value).detachReference()
        }

    fun fromAbi(pointer: RawAddress): FromAbiHelper? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(WinRTBindableVectorReference(pointer, WinRTBindableInterfaceIds.IBindableVector))
        }

    fun fromAbi(reference: IUnknownReference): FromAbiHelper =
        reference.queryInterface(WinRTBindableInterfaceIds.IBindableVector).getOrThrow().use {
            FromAbiHelper(WinRTBindableVectorReference(it.getRefPointer().asRawAddress(), WinRTBindableInterfaceIds.IBindableVector))
        }
}

private val bindableInspectableTypeHandle =
    WinRTTypeHandle("kotlin.Any?", IID.IInspectable)

private val bindableIterableTypeHandle =
    WinRTTypeHandle("kotlin.collections.Iterable<kotlin.Any?>", WinRTBindableInterfaceIds.IBindableIterable)

private val bindableIteratorTypeHandle =
    WinRTTypeHandle("kotlin.collections.Iterator<kotlin.Any?>", WinRTBindableInterfaceIds.IBindableIterator)

private val bindableVectorViewTypeHandle =
    WinRTTypeHandle("kotlin.collections.List<kotlin.Any?>", WinRTBindableInterfaceIds.IBindableVectorView)

private val bindableVectorTypeHandle =
    WinRTTypeHandle("kotlin.collections.MutableList<kotlin.Any?>", WinRTBindableInterfaceIds.IBindableVector)

private fun createBindableHost(
    managedValue: Any,
    defaultInterfaceId: Guid,
    interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
): WinRTInspectableComObject {
    val definition = InteropRuntimeHooks.augmentInspectableDefinition(
        value = managedValue,
        definition = WinRTCcwDefinition(
            interfaceDefinitions = interfaceDefinitions,
            defaultInterfaceId = defaultInterfaceId,
        ),
    )
    return WinRTInspectableComObject(
        interfaceDefinitions = definition.interfaceDefinitions,
        hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
        defaultInterfaceId = definition.defaultInterfaceId,
        managedValue = managedValue,
    )
}

private fun bindableIterableDefinition(
    iteratorFactory: () -> Iterator<Any?>,
): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = WinRTBindableInterfaceIds.IBindableIterable,
        methods = listOf(
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                val resultOut = rawArgs[0] as RawAddress
                resultOut.writeReturnedPointer(WinRTBindableIteratorProjection.detachReference(iteratorFactory()))
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

private fun RawAddress.writeReturnedPointer(pointer: RawAddress) {
    PlatformAbi.writePointer(this, pointer)
}

private fun RawAddress.writeBoolean(value: Boolean) {
    PlatformAbi.writeInt8(this, if (value) 1 else 0)
}

private fun RawAddress.writeUInt32(value: UInt) {
    PlatformAbi.writeInt32(this, value.toInt())
}
