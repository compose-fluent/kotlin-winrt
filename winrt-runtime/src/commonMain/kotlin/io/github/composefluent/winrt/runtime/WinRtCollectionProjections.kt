package io.github.composefluent.winrt.runtime

import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.collections.AbstractMutableList
import kotlin.collections.AbstractMutableMap
import kotlin.reflect.KClass

/**
 * Runtime projection helper layer corresponding to `.cswinrt/src/WinRT.Runtime/Projections/IEnumerable*`,
 * `IList*`, `IReadOnlyList*`, `IDictionary*`, and `IReadOnlyDictionary*`.
 *
 * This slice owns RCW/CCW helper structure and `CreateMarshaler` / `FromManaged` / `FromAbi` paths.
 * Element-specific scalar/string/value-type marshalling is still a separate runtime concern in the later
 * `Marshalers.cs` parity work, so the helper layer is parameterized by reference-style projector/marshaller
 * lambdas instead of inventing a second marshaling model here.
 */
open class WinRtReferenceValueAdapter<T>(
    val projectedTypeName: String,
    val typeSignature: WinRtTypeSignature,
    val projector: (IUnknownReference?) -> T,
    val marshaller: (T) -> ComObjectReference,
) {
    open fun createInputMarshaler(value: T): WinRtObjectMarshaler =
        marshaller(value).let { reference ->
            WinRtObjectMarshaler(reference.pointer.asRawAddress(), reference::close)
        }

    open fun createOutputMarshaler(value: T): WinRtObjectMarshaler =
        marshaller(value).let { reference ->
            WinRtObjectMarshaler(reference.getRefPointer().asRawAddress(), reference::close)
        }
}

object WinRtReferenceValueAdapters {
    val string: WinRtReferenceValueAdapter<String> =
        WinRtReferenceValueAdapter(
            projectedTypeName = "String",
            typeSignature = WinRtTypeSignature.string(),
            projector = { reference ->
                if (reference == null) {
                    ""
                } else {
                    WinRtReferenceReference(
                        pointer = reference.pointer.asRawAddress(),
                        interfaceId = IID.NullableString,
                        preventReleaseOnDispose = true,
                    ).use { valueReference ->
                        ValueBoxingInterop.readReferenceValue(IID.NullableString, valueReference) as? String ?: ""
                    }
                }
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value, IID.NullableString) },
        )

    val inspectable: WinRtReferenceValueAdapter<IInspectableReference> =
        WinRtReferenceValueAdapter(
            projectedTypeName = "io.github.composefluent.winrt.runtime.IInspectableReference",
            typeSignature = WinRtTypeSignature.object_(),
            projector = { reference ->
                reference?.asInspectable() ?: IInspectableReference(PlatformAbi.nullComPtr, IID.IInspectable)
            },
            marshaller = { value -> IInspectableReference(value.getRefPointer(), IID.IInspectable) },
        )

    val object_: WinRtReferenceValueAdapter<Any?> =
        object : WinRtReferenceValueAdapter<Any?>(
            projectedTypeName = "Any?",
            typeSignature = WinRtTypeSignature.object_(),
            projector = { reference ->
                WinRtObjectMarshaller.fromAbi(reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer)
            },
            marshaller = { value ->
                check(value != null) { "Null System.Object collection values are not supported by this adapter yet." }
                ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
            },
        ) {
            override fun createInputMarshaler(value: Any?): WinRtObjectMarshaler =
                WinRtObjectMarshaller.createMarshaler(value)

            override fun createOutputMarshaler(value: Any?): WinRtObjectMarshaler =
                WinRtObjectMarshaller.createMarshaler(value)
        }

    fun <T : Any> valueType(
        projectedType: KClass<T>,
        projectedTypeName: String,
        typeSignature: WinRtTypeSignature,
    ): WinRtReferenceValueAdapter<T> =
        valueType(
            projectedType = projectedType,
            projectedTypeName = projectedTypeName,
            typeSignature = typeSignature,
            nullableInterfaceId = ParameterizedInterfaceId.createFromParameterizedInterface(
                "61C17706-2D65-11E0-9AE8-D48564015472",
                typeSignature,
            ),
        )

    fun <T : Any> valueType(
        projectedType: KClass<T>,
        projectedTypeName: String,
        typeSignature: WinRtTypeSignature,
        nullableInterfaceId: Guid,
    ): WinRtReferenceValueAdapter<T> =
        WinRtReferenceValueAdapter(
            projectedTypeName = projectedTypeName,
            typeSignature = typeSignature,
            projector = { reference ->
                val inspectable = reference?.asInspectable()
                    ?: throw WinRtInvalidCastException(
                        "Expected non-null IReference<$projectedTypeName> value.",
                        HResult(TYPE_E_TYPEMISMATCH),
                    )
                WinRtValueBoxing.tryProjectInspectableAsType(inspectable, projectedType) as? T
                    ?: throw WinRtInvalidCastException(
                        "Unable to project IReference<$projectedTypeName> value.",
                        HResult(TYPE_E_TYPEMISMATCH),
                    )
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value, nullableInterfaceId) },
        )

    @Suppress("UNCHECKED_CAST")
    fun <T> genericParameter(projectedTypeName: String): WinRtReferenceValueAdapter<T> =
        WinRtReferenceValueAdapter(
            projectedTypeName = projectedTypeName,
            typeSignature = WinRtTypeSignature.object_(),
            projector = { reference ->
                val pointer = reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer
                if (PlatformAbi.isNull(pointer)) {
                    null as T
                } else {
                    (WinRtInspectableComObject.findManagedValue(pointer)
                        ?: ComWrappersSupport.createRcwForComObject(pointer)) as T
                }
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value as Any, IID.IInspectable) },
        )
}

object WinRtGenericParameterProjection {
    @Suppress("UNCHECKED_CAST")
    fun <T> fromAbi(pointer: RawAddress): T {
        return WinRtObjectMarshaller.fromAbi(pointer) as T
    }

    fun <T> fromManaged(value: T): RawAddress =
        WinRtObjectMarshaller.fromManaged(value)

    fun <T> createReference(value: T): ComObjectReference? =
        value?.let { ComWrappersSupport.createCCWForObject(it as Any, IID.IInspectable) }
}

typealias WinRtCollectionProjectionMarshaler = WinRtProjectionMarshaler

object WinRtIterableProjection {
    class FromAbiHelper<T> internal constructor(
        private val iterable: WinRtIterableReference,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) : Iterable<T>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

        override val primaryTypeHandle: WinRtTypeHandle
            get() = iterableTypeHandle(elementAdapter)

        override fun iterator(): Iterator<T> =
            WinRtIteratorProjection.FromAbiHelper(
                iterable = iterable.first(iteratorInterfaceId(elementAdapter)),
                elementAdapter = elementAdapter,
            )

        override fun close() {
            iterable.close()
        }
    }

    internal class ToAbiHelper<T>(
        private val managed: Iterable<T>,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = iterableInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = iterableInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(
                                WinRtIteratorProjection.detachReference(managed.iterator(), elementAdapter),
                            )
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, iterableInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(iterableInterfaceId(elementAdapter))
    }

    fun <T> createMarshaler(
        value: Iterable<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): WinRtCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, iterableTypeHandle(elementAdapter))?.let { return it }
        return ToAbiHelper(value, elementAdapter).createMarshaler()
    }

    fun <T> fromManaged(
        value: Iterable<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, iterableTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(
                iterable = WinRtIterableReference(pointer, iterableInterfaceId(elementAdapter)),
                elementAdapter = elementAdapter,
            )
        }
}

object WinRtIteratorProjection {
    class FromAbiHelper<T> internal constructor(
        private val iterable: WinRtIteratorReference,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) : Iterator<T>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

        override val primaryTypeHandle: WinRtTypeHandle
            get() = iteratorTypeHandle(elementAdapter)

        private var initialized = false
        private var hasCurrent = false
        private var currentValue: T? = null

        override fun hasNext(): Boolean {
            ensureInitialized()
            return hasCurrent
        }

        override fun next(): T {
            ensureInitialized()
            if (!hasCurrent) {
                throw NoSuchElementException("IIterator has no remaining elements.")
            }
            val value = currentValue ?: error("Iterator current value must be initialized.")
            advance()
            return value
        }

        override fun close() {
            iterable.close()
        }

        private fun ensureInitialized() {
            if (initialized) {
                return
            }
            initialized = true
            hasCurrent = iterable.hasCurrent()
            if (hasCurrent) {
                currentValue = projectBorrowed(iterable.currentOrNull(), elementAdapter)
            }
        }

        private fun advance() {
            hasCurrent = iterable.moveNext()
            currentValue = if (hasCurrent) {
                projectBorrowed(iterable.currentOrNull(), elementAdapter)
            } else {
                null
            }
        }
    }

    internal class ToAbiHelper<T>(
        managed: Iterator<T>,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) {
        private val state = IteratorState(managed)
        private val host = createCollectionHost(
            managedValue = state,
            defaultInterfaceId = iteratorInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = iteratorInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            if (!state.hasCurrent) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            resultOut.writeManagedValue(state.current(), elementAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            state.moveNext()
                            (rawArgs[0] as RawAddress).writeBoolean(state.hasCurrent)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val capacity = rawArgs[0] as Int
                            val itemsOut = rawArgs[1] as RawAddress
                            val countOut = rawArgs[2] as RawAddress
                            val written = state.getMany(capacity)
                            itemsOut.writeManagedValues(written, elementAdapter)
                            countOut.writeUInt32(written.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, iteratorInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(iteratorInterfaceId(elementAdapter))
    }

    internal fun <T> detachReference(
        managed: Iterator<T>,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): RawAddress = ToAbiHelper(managed, elementAdapter).detachReference()

    private class IteratorState<T>(
        managed: Iterator<T>,
    ) {
        private val iterator = managed
        var hasCurrent: Boolean = iterator.hasNext()
            private set
        private var currentValue: T? = if (hasCurrent) iterator.next() else null

        fun currentOrNull(): T? = currentValue

        fun current(): T {
            check(hasCurrent) { "Iterator current value is not available." }
            @Suppress("UNCHECKED_CAST")
            return currentValue as T
        }

        fun moveNext(): Boolean {
            hasCurrent = iterator.hasNext()
            currentValue = if (hasCurrent) iterator.next() else null
            return hasCurrent
        }

        fun getMany(capacity: Int): List<T> {
            require(capacity >= 0) { "capacity must be non-negative." }
            if (capacity == 0 || !hasCurrent) {
                return emptyList()
            }
            val written = mutableListOf<T>()
            written.add(current())
            while (written.size < capacity && moveNext()) {
                written.add(current())
            }
            if (written.size >= capacity) {
                moveNext()
            }
            return written
        }
    }
}

object WinRtReadOnlyListProjection {
    class FromAbiHelper<T> internal constructor(
        private val vectorView: WinRtVectorViewReference,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) : AbstractList<T>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vectorView

        override val primaryTypeHandle: WinRtTypeHandle
            get() = vectorViewTypeHandle(elementAdapter)

        private val adapter by lazy {
            WinRtVectorViewListAdapter(
                vectorView = WinRtVectorViewReference(
                    vectorView.pointer.asRawAddress(),
                    vectorView.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                elementProjector = elementAdapter.projector,
            )
        }

        override val size: Int
            get() = adapter.size

        override fun get(index: Int): T = adapter[index]

        override fun close() {
            vectorView.close()
        }
    }

    internal class ToAbiHelper<T>(
        private val managed: List<T>,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = vectorViewInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = vectorViewInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index >= managed.size.toUInt()) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeManagedValue(value, elementAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val value = decodeBorrowedValue(rawArgs[0] as RawAddress, elementAdapter)
                            val indexOut = rawArgs[1] as RawAddress
                            val foundOut = rawArgs[2] as RawAddress
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(
                                ComAbiValueKind.Int32,
                                ComAbiValueKind.Int32,
                                ComAbiValueKind.Pointer,
                                ComAbiValueKind.Pointer,
                            ),
                        ) { rawArgs ->
                            val startIndex = (rawArgs[0] as Int).toUInt()
                            val capacity = rawArgs[1] as Int
                            val itemsOut = rawArgs[2] as RawAddress
                            val countOut = rawArgs[3] as RawAddress
                            val written = managed.drop(startIndex.toInt()).take(capacity)
                            itemsOut.writeManagedValues(written, elementAdapter)
                            countOut.writeUInt32(written.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, vectorViewInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(vectorViewInterfaceId(elementAdapter))
    }

    fun <T> createMarshaler(
        value: List<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): WinRtCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, vectorViewTypeHandle(elementAdapter))?.let { return it }
        return ToAbiHelper(value, elementAdapter).createMarshaler()
    }

    fun <T> fromManaged(
        value: List<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, vectorViewTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(
                vectorView = WinRtVectorViewReference(pointer, vectorViewInterfaceId(elementAdapter)),
                elementAdapter = elementAdapter,
            )
        }
}

object WinRtListProjection {
    class FromAbiHelper<T> internal constructor(
        private val vector: WinRtVectorReference,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) : AbstractMutableList<T>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vector

        override val primaryTypeHandle: WinRtTypeHandle
            get() = vectorTypeHandle(elementAdapter)

        private val adapter by lazy {
            WinRtVectorListAdapter(
                vector = WinRtVectorReference(
                    vector.pointer.asRawAddress(),
                    vector.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                elementProjector = elementAdapter.projector,
                elementMarshaller = elementAdapter::createInputMarshaler,
            )
        }

        override val size: Int
            get() = adapter.size

        override fun get(index: Int): T = adapter[index]

        override fun set(index: Int, element: T): T = adapter.set(index, element)

        override fun add(index: Int, element: T) {
            adapter.add(index, element)
        }

        override fun removeAt(index: Int): T = adapter.removeAt(index)

        override fun clear() {
            adapter.clear()
        }

        override fun close() {
            vector.close()
        }
    }

    internal class ToAbiHelper<T>(
        private val managed: MutableList<T>,
        private val elementAdapter: WinRtReferenceValueAdapter<T>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = vectorInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = vectorInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index >= managed.size.toUInt()) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeManagedValue(value, elementAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(
                                WinRtReadOnlyListProjection.ToAbiHelper(managed.toList(), elementAdapter).detachReference(),
                            )
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val value = decodeBorrowedValue(rawArgs[0] as RawAddress, elementAdapter)
                            val indexOut = rawArgs[1] as RawAddress
                            val foundOut = rawArgs[2] as RawAddress
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, elementAdapter)
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed[index.toInt()] = value
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, elementAdapter)
                            if (index.toInt() > managed.size) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.add(index.toInt(), value)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.removeAt(index.toInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            managed.add(decodeBorrowedValue(rawArgs[0] as RawAddress, elementAdapter))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(),
                        ) {
                            if (managed.isEmpty()) {
                                KnownHResults.E_BOUNDS.value
                            } else {
                                managed.removeAt(managed.lastIndex)
                                KnownHResults.S_OK.value
                            }
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(),
                        ) {
                            managed.clear()
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(
                                ComAbiValueKind.Int32,
                                ComAbiValueKind.Int32,
                                ComAbiValueKind.Pointer,
                                ComAbiValueKind.Pointer,
                            ),
                        ) { rawArgs ->
                            val startIndex = (rawArgs[0] as Int).toUInt()
                            val capacity = rawArgs[1] as Int
                            val itemsOut = rawArgs[2] as RawAddress
                            val countOut = rawArgs[3] as RawAddress
                            val written = managed.drop(startIndex.toInt()).take(capacity)
                            itemsOut.writeManagedValues(written, elementAdapter)
                            countOut.writeUInt32(written.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val size = rawArgs[0] as Int
                            val itemsIn = rawArgs[1] as RawAddress
                            managed.clear()
                            repeat(size) { index ->
                                managed += decodeBorrowedValue(
                                    PlatformAbi.readPointerAt(itemsIn, index),
                                    elementAdapter,
                                )
                            }
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, vectorInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(vectorInterfaceId(elementAdapter))
    }

    fun <T> createMarshaler(
        value: MutableList<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): WinRtCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, vectorTypeHandle(elementAdapter))?.let { return it }
        return ToAbiHelper(value, elementAdapter).createMarshaler()
    }

    fun <T> fromManaged(
        value: MutableList<T>?,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, vectorTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(
                vector = WinRtVectorReference(pointer, vectorInterfaceId(elementAdapter)),
                elementAdapter = elementAdapter,
            )
        }
}

object WinRtReadOnlyDictionaryProjection {
    class FromAbiHelper<K, V> internal constructor(
        private val mapView: WinRtMapViewReference,
        private val keyAdapter: WinRtReferenceValueAdapter<K>,
        private val valueAdapter: WinRtReferenceValueAdapter<V>,
    ) : AbstractMap<K, V>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = mapView

        override val primaryTypeHandle: WinRtTypeHandle
            get() = mapViewTypeHandle(keyAdapter, valueAdapter)

        private val adapter by lazy {
            WinRtMapViewAdapter(
                mapView = WinRtMapViewReference(
                    mapView.pointer.asRawAddress(),
                    mapView.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                iterableInterfaceId = iterableInterfaceId(winRtKeyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(winRtKeyValuePairAdapter(keyAdapter, valueAdapter)),
                keyValuePairInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                keyProjector = keyAdapter.projector,
                valueProjector = valueAdapter.projector,
                keyMarshaller = keyAdapter.marshaller,
            )
        }

        override val entries: Set<Map.Entry<K, V>>
            get() = adapter.entries

        override fun containsKey(key: K): Boolean = adapter.containsKey(key)

        override fun get(key: K): V? = adapter[key]

        override fun close() {
            mapView.close()
        }
    }

    internal class ToAbiHelper<K, V>(
        private val managed: Map<K, V>,
        private val keyAdapter: WinRtReferenceValueAdapter<K>,
        private val valueAdapter: WinRtReferenceValueAdapter<V>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = winRtKeyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val resultOut = rawArgs[1] as RawAddress
                            val value = managed[key]
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, valueAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            (rawArgs[1] as RawAddress).writeBoolean(managed.containsKey(key))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val firstOut = rawArgs[0] as RawAddress
                            val secondOut = rawArgs[1] as RawAddress
                            val entries = managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }
                            val midpoint = entries.size / 2
                            val first = entries.take(midpoint).associate { it.key to it.value }
                            val second = entries.drop(midpoint).associate { it.key to it.value }
                            firstOut.writeReturnedPointer(
                                ToAbiHelper(first, keyAdapter, valueAdapter).detachReference(),
                            )
                            secondOut.writeReturnedPointer(
                                ToAbiHelper(second, keyAdapter, valueAdapter).detachReference(),
                            )
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, mapViewInterfaceId(keyAdapter, valueAdapter))

        fun detachReference(): RawAddress = host.detachReference(mapViewInterfaceId(keyAdapter, valueAdapter))
    }

    fun <K, V> createMarshaler(
        value: Map<K, V>?,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): WinRtCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, mapViewTypeHandle(keyAdapter, valueAdapter))?.let { return it }
        return ToAbiHelper(value, keyAdapter, valueAdapter).createMarshaler()
    }

    fun <K, V> fromManaged(
        value: Map<K, V>?,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, mapViewTypeHandle(keyAdapter, valueAdapter))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: RawAddress,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): FromAbiHelper<K, V>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(
                mapView = WinRtMapViewReference(pointer, mapViewInterfaceId(keyAdapter, valueAdapter)),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
            )
        }
}

object WinRtDictionaryProjection {
    class FromAbiHelper<K, V> internal constructor(
        private val map: WinRtMapReference,
        private val keyAdapter: WinRtReferenceValueAdapter<K>,
        private val valueAdapter: WinRtReferenceValueAdapter<V>,
    ) : AbstractMutableMap<K, V>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = map

        override val primaryTypeHandle: WinRtTypeHandle
            get() = mapTypeHandle(keyAdapter, valueAdapter)

        private val adapter by lazy {
            WinRtMapAdapter(
                map = WinRtMapReference(
                    map.pointer.asRawAddress(),
                    map.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                mapViewInterfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
                iterableInterfaceId = iterableInterfaceId(winRtKeyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(winRtKeyValuePairAdapter(keyAdapter, valueAdapter)),
                keyValuePairInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                keyProjector = keyAdapter.projector,
                valueProjector = valueAdapter.projector,
                keyMarshaller = keyAdapter.marshaller,
                valueMarshaller = valueAdapter.marshaller,
            )
        }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = adapter.entries

        override fun put(key: K, value: V): V? = adapter.put(key, value)

        override fun get(key: K): V? = adapter[key]

        override fun remove(key: K): V? = adapter.remove(key)

        override fun clear() {
            adapter.clear()
        }

        override fun close() {
            map.close()
        }
    }

    internal class ToAbiHelper<K, V>(
        private val managed: MutableMap<K, V>,
        private val keyAdapter: WinRtReferenceValueAdapter<K>,
        private val valueAdapter: WinRtReferenceValueAdapter<V>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = mapInterfaceId(keyAdapter, valueAdapter),
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = winRtKeyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = mapInterfaceId(keyAdapter, valueAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val resultOut = rawArgs[1] as RawAddress
                            val value = managed[key]
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, valueAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            (rawArgs[1] as RawAddress).writeBoolean(managed.containsKey(key))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(
                                WinRtReadOnlyDictionaryProjection.ToAbiHelper(
                                    managed.toMap(),
                                    keyAdapter,
                                    valueAdapter,
                                ).detachReference(),
                            )
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, valueAdapter)
                            val replaced = managed.put(key, value) != null
                            (rawArgs[2] as RawAddress).writeBoolean(replaced)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            if (managed.remove(key) == null) {
                                KnownHResults.E_BOUNDS.value
                            } else {
                                KnownHResults.S_OK.value
                            }
                        },
                        WinRtInspectableMethodDefinition(
                            signature = ComMethodSignature.of(),
                        ) {
                            managed.clear()
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, mapInterfaceId(keyAdapter, valueAdapter))

        fun detachReference(): RawAddress = host.detachReference(mapInterfaceId(keyAdapter, valueAdapter))
    }

    fun <K, V> createMarshaler(
        value: MutableMap<K, V>?,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): WinRtCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, mapTypeHandle(keyAdapter, valueAdapter))?.let { return it }
        return ToAbiHelper(value, keyAdapter, valueAdapter).createMarshaler()
    }

    fun <K, V> fromManaged(
        value: MutableMap<K, V>?,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, mapTypeHandle(keyAdapter, valueAdapter))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: RawAddress,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): FromAbiHelper<K, V>? =
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            FromAbiHelper(
                map = WinRtMapReference(pointer, mapInterfaceId(keyAdapter, valueAdapter)),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
            )
        }
}

private fun <T> iterableInterfaceId(adapter: WinRtReferenceValueAdapter<T>): Guid =
    WinRtCollectionInterfaceIds.iterable(adapter.typeSignature)

private fun <T> iteratorInterfaceId(adapter: WinRtReferenceValueAdapter<T>): Guid =
    WinRtCollectionInterfaceIds.iterator(adapter.typeSignature)

private fun <T> vectorViewInterfaceId(adapter: WinRtReferenceValueAdapter<T>): Guid =
    WinRtCollectionInterfaceIds.vectorView(adapter.typeSignature)

private fun <T> vectorInterfaceId(adapter: WinRtReferenceValueAdapter<T>): Guid =
    WinRtCollectionInterfaceIds.vector(adapter.typeSignature)

private fun <K, V> mapViewInterfaceId(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): Guid = WinRtCollectionInterfaceIds.mapView(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <K, V> mapInterfaceId(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): Guid = WinRtCollectionInterfaceIds.map(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <K, V> keyValuePairInterfaceId(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): Guid = WinRtCollectionInterfaceIds.keyValuePair(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <T> iterableTypeHandle(adapter: WinRtReferenceValueAdapter<T>): WinRtTypeHandle =
    WinRtTypeHandle("kotlin.collections.Iterable<${adapter.projectedTypeName}>", iterableInterfaceId(adapter))

private fun <T> iteratorTypeHandle(adapter: WinRtReferenceValueAdapter<T>): WinRtTypeHandle =
    WinRtTypeHandle("kotlin.collections.Iterator<${adapter.projectedTypeName}>", iteratorInterfaceId(adapter))

private fun <T> vectorViewTypeHandle(adapter: WinRtReferenceValueAdapter<T>): WinRtTypeHandle =
    WinRtTypeHandle("kotlin.collections.List<${adapter.projectedTypeName}>", vectorViewInterfaceId(adapter))

private fun <T> vectorTypeHandle(adapter: WinRtReferenceValueAdapter<T>): WinRtTypeHandle =
    WinRtTypeHandle("kotlin.collections.MutableList<${adapter.projectedTypeName}>", vectorInterfaceId(adapter))

private fun <K, V> mapViewTypeHandle(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): WinRtTypeHandle =
    WinRtTypeHandle(
        "kotlin.collections.Map<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        mapViewInterfaceId(keyAdapter, valueAdapter),
    )

private fun <K, V> mapTypeHandle(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): WinRtTypeHandle =
    WinRtTypeHandle(
        "kotlin.collections.MutableMap<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        mapInterfaceId(keyAdapter, valueAdapter),
    )

fun <K, V> winRtKeyValuePairAdapter(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): WinRtReferenceValueAdapter<Map.Entry<K, V>> =
    WinRtReferenceValueAdapter(
        projectedTypeName = "kotlin.collections.Map.Entry<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        typeSignature = WinRtCollectionInterfaceIds.keyValuePairSignature(keyAdapter.typeSignature, valueAdapter.typeSignature),
        projector = { reference ->
            val pair = reference?.let {
                WinRtKeyValuePairReference(
                    it.pointer.asRawAddress(),
                    keyValuePairInterfaceId(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                )
            } ?: return@WinRtReferenceValueAdapter ProjectionEntrySnapshot(
                keyAdapter.projector(null),
                valueAdapter.projector(null),
            )
            pair.use {
                ProjectionEntrySnapshot(
                    projectBorrowed(it.key(), keyAdapter),
                    projectBorrowed(it.value(), valueAdapter),
                )
            }
        },
        marshaller = { entry ->
            createCollectionHost(
                managedValue = entry,
                defaultInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                        methods = listOf(
                            WinRtInspectableMethodDefinition(
                                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            ) { rawArgs ->
                                (rawArgs[0] as RawAddress).writeManagedValue(entry.key, keyAdapter)
                                KnownHResults.S_OK.value
                            },
                            WinRtInspectableMethodDefinition(
                                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            ) { rawArgs ->
                                (rawArgs[0] as RawAddress).writeManagedValue(entry.value, valueAdapter)
                                KnownHResults.S_OK.value
                            },
                        ),
                    ),
                ),
            ).let { host ->
                val reference = host.createReference(keyValuePairInterfaceId(keyAdapter, valueAdapter))
                host.releaseManagedReference()
                reference
            }
        },
    )

private fun createCollectionHost(
    managedValue: Any,
    defaultInterfaceId: Guid,
    interfaceDefinitions: List<WinRtInspectableInterfaceDefinition>,
): WinRtInspectableComObject {
    val definition = InteropRuntimeHooks.augmentInspectableDefinition(
        value = managedValue,
        definition = WinRtCcwDefinition(
            interfaceDefinitions = interfaceDefinitions,
            defaultInterfaceId = defaultInterfaceId,
        ),
    )
    return WinRtInspectableComObject(
        interfaceDefinitions = definition.interfaceDefinitions,
        hiddenInterfaceDefinitions = definition.hiddenInterfaceDefinitions,
        defaultInterfaceId = definition.defaultInterfaceId,
        managedValue = managedValue,
    )
}

private fun iterableInterfaceDefinition(
    elementAdapter: WinRtReferenceValueAdapter<*>,
    iteratorFactory: () -> Iterator<*>,
): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = iterableInterfaceId(elementAdapter),
        methods = listOf(
            WinRtInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                val resultOut = rawArgs[0] as RawAddress
                @Suppress("UNCHECKED_CAST")
                resultOut.writeReturnedPointer(
                    WinRtIteratorProjection.detachReference(
                        iteratorFactory(),
                        elementAdapter as WinRtReferenceValueAdapter<Any?>,
                    ),
                )
                KnownHResults.S_OK.value
            },
        ),
    )

private fun <T> projectBorrowed(
    reference: IUnknownReference?,
    adapter: WinRtReferenceValueAdapter<T>,
): T = try {
    adapter.projector(reference)
} finally {
    reference?.close()
}

private fun <T> decodeBorrowedValue(
    pointer: RawAddress,
    adapter: WinRtReferenceValueAdapter<T>,
): T =
    projectBorrowed(
        if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            IUnknownReference(pointer.asRawComPtr(), preventReleaseOnDispose = true)
        },
        adapter,
    )

private fun RawAddress.writeReturnedPointer(pointer: RawAddress) {
    PlatformAbi.writePointer(this, pointer)
}

private fun <T> RawAddress.writeManagedValues(
    values: List<T>,
    adapter: WinRtReferenceValueAdapter<T>,
) {
    values.forEachIndexed { index, value ->
        adapter.createOutputMarshaler(value).use { marshaler ->
            PlatformAbi.writePointerAt(this, index, marshaler.abi)
        }
    }
}

private fun <T> RawAddress.writeManagedValue(
    value: T,
    adapter: WinRtReferenceValueAdapter<T>,
) {
    adapter.createOutputMarshaler(value).use { marshaler ->
        PlatformAbi.writePointer(this, marshaler.abi)
    }
}

private fun RawAddress.writeBoolean(value: Boolean) {
    PlatformAbi.writeInt8(this, if (value) 1 else 0)
}

private fun RawAddress.writeUInt32(value: UInt) {
    PlatformAbi.writeInt32(this, value.toInt())
}


private data class ProjectionEntrySnapshot<K, V>(
    override val key: K,
    override val value: V,
) : Map.Entry<K, V>
