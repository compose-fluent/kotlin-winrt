package io.github.kitectlab.winrt.runtime

import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.collections.AbstractMutableList
import kotlin.collections.AbstractMutableMap

/**
 * Runtime projection helper layer corresponding to `.cswinrt/src/WinRT.Runtime/Projections/IEnumerable*`,
 * `IList*`, `IReadOnlyList*`, `IDictionary*`, and `IReadOnlyDictionary*`.
 *
 * This slice owns RCW/CCW helper structure and `CreateMarshaler` / `FromManaged` / `FromAbi` paths.
 * Element-specific scalar/string/value-type marshalling is still a separate runtime concern in the later
 * `Marshalers.cs` parity work, so the helper layer is parameterized by reference-style projector/marshaller
 * lambdas instead of inventing a second marshaling model here.
 */
class WinRtReferenceValueAdapter<T>(
    val projectedTypeName: String,
    val typeSignature: WinRtTypeSignature,
    val projector: (IUnknownReference?) -> T,
    val marshaller: (T) -> ComObjectReference,
)

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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = iterableInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as MemorySegment
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

        fun detachReference(): MemorySegment = host.detachReference(iterableInterfaceId(elementAdapter))
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
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, iterableTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: MemorySegment,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (pointer == MemorySegment.NULL) {
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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                WinRtInspectableInterfaceDefinition(
                    interfaceId = iteratorInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as MemorySegment
                            val current = state.currentOrNull()
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(current, elementAdapter)
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
                        ) { rawArgs ->
                            val capacity = rawArgs[0] as Int
                            val itemsOut = rawArgs[1] as MemorySegment
                            val countOut = rawArgs[2] as MemorySegment
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

        fun detachReference(): MemorySegment = host.detachReference(iteratorInterfaceId(elementAdapter))
    }

    internal fun <T> detachReference(
        managed: Iterator<T>,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): MemorySegment = ToAbiHelper(managed, elementAdapter).detachReference()

    private class IteratorState<T>(
        managed: Iterator<T>,
    ) {
        private val iterator = managed
        var hasCurrent: Boolean = iterator.hasNext()
            private set
        private var currentValue: T? = if (hasCurrent) iterator.next() else null

        fun currentOrNull(): T? = currentValue

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
            currentValue?.let(written::add)
            while (written.size < capacity && moveNext()) {
                currentValue?.let(written::add)
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
                    vectorView.pointer,
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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = vectorViewInterfaceId(elementAdapter),
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
                            val value = managed.getOrNull(index.toInt())
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, elementAdapter)
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
                            val value = decodeBorrowedValue(rawArgs[0] as MemorySegment, elementAdapter)
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
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val startIndex = (rawArgs[0] as Int).toUInt()
                            val capacity = rawArgs[1] as Int
                            val itemsOut = rawArgs[2] as MemorySegment
                            val countOut = rawArgs[3] as MemorySegment
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

        fun detachReference(): MemorySegment = host.detachReference(vectorViewInterfaceId(elementAdapter))
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
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, vectorViewTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: MemorySegment,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (pointer == MemorySegment.NULL) {
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
                    vector.pointer,
                    vector.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                elementProjector = elementAdapter.projector,
                elementMarshaller = elementAdapter.marshaller,
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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = vectorInterfaceId(elementAdapter),
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
                            val value = managed.getOrNull(index.toInt())
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, elementAdapter)
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
                            resultOut.writeReturnedPointer(
                                WinRtReadOnlyListProjection.ToAbiHelper(managed.toList(), elementAdapter).detachReference(),
                            )
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
                            val value = decodeBorrowedValue(rawArgs[0] as MemorySegment, elementAdapter)
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
                            val value = decodeBorrowedValue(rawArgs[1] as MemorySegment, elementAdapter)
                            if (index.toInt() !in managed.indices) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed[index.toInt()] = value
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
                            val value = decodeBorrowedValue(rawArgs[1] as MemorySegment, elementAdapter)
                            if (index.toInt() > managed.size) {
                                return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.add(index.toInt(), value)
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
                            managed.add(decodeBorrowedValue(rawArgs[0] as MemorySegment, elementAdapter))
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
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.JAVA_INT,
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val startIndex = (rawArgs[0] as Int).toUInt()
                            val capacity = rawArgs[1] as Int
                            val itemsOut = rawArgs[2] as MemorySegment
                            val countOut = rawArgs[3] as MemorySegment
                            val written = managed.drop(startIndex.toInt()).take(capacity)
                            itemsOut.writeManagedValues(written, elementAdapter)
                            countOut.writeUInt32(written.size.toUInt())
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
                            val size = rawArgs[0] as Int
                            val itemsIn = rawArgs[1] as MemorySegment
                            managed.clear()
                            repeat(size) { index ->
                                managed += decodeBorrowedValue(
                                    itemsIn.getAtIndex(ValueLayout.ADDRESS, index.toLong()),
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

        fun detachReference(): MemorySegment = host.detachReference(vectorInterfaceId(elementAdapter))
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
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, vectorTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: MemorySegment,
        elementAdapter: WinRtReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? =
        if (pointer == MemorySegment.NULL) {
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
                    mapView.pointer,
                    mapView.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                iterableInterfaceId = iterableInterfaceId(keyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(keyValuePairAdapter(keyAdapter, valueAdapter)),
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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = keyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            val resultOut = rawArgs[1] as MemorySegment
                            val value = managed[key]
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, valueAdapter)
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
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            (rawArgs[1] as MemorySegment).writeBoolean(managed.containsKey(key))
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val firstOut = rawArgs[0] as MemorySegment
                            val secondOut = rawArgs[1] as MemorySegment
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

        fun detachReference(): MemorySegment = host.detachReference(mapViewInterfaceId(keyAdapter, valueAdapter))
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
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, mapViewTypeHandle(keyAdapter, valueAdapter))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: MemorySegment,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): FromAbiHelper<K, V>? =
        if (pointer == MemorySegment.NULL) {
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
                    map.pointer,
                    map.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                mapViewInterfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
                iterableInterfaceId = iterableInterfaceId(keyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(keyValuePairAdapter(keyAdapter, valueAdapter)),
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
        private val host = WinRtInspectableComObject(
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = keyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRtInspectableInterfaceDefinition(
                    interfaceId = mapInterfaceId(keyAdapter, valueAdapter),
                    methods = listOf(
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            val resultOut = rawArgs[1] as MemorySegment
                            val value = managed[key]
                                ?: return@WinRtInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            resultOut.writeManagedValue(value, valueAdapter)
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
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            (rawArgs[1] as MemorySegment).writeBoolean(managed.containsKey(key))
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
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            val value = decodeBorrowedValue(rawArgs[1] as MemorySegment, valueAdapter)
                            val replaced = managed.put(key, value) != null
                            (rawArgs[2] as MemorySegment).writeBoolean(replaced)
                            KnownHResults.S_OK.value
                        },
                        WinRtInspectableMethodDefinition(
                            descriptor = FunctionDescriptor.of(
                                ValueLayout.JAVA_INT,
                                ValueLayout.ADDRESS,
                                ValueLayout.ADDRESS,
                            ),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as MemorySegment, keyAdapter)
                            if (managed.remove(key) == null) {
                                KnownHResults.E_BOUNDS.value
                            } else {
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

        fun createMarshaler(): WinRtCollectionProjectionMarshaler =
            WinRtProjectionMarshaler.hosted(host, mapInterfaceId(keyAdapter, valueAdapter))

        fun detachReference(): MemorySegment = host.detachReference(mapInterfaceId(keyAdapter, valueAdapter))
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
    ): MemorySegment =
        if (value == null) {
            MemorySegment.NULL
        } else {
            borrowedProjectionAbi(value, mapTypeHandle(keyAdapter, valueAdapter))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: MemorySegment,
        keyAdapter: WinRtReferenceValueAdapter<K>,
        valueAdapter: WinRtReferenceValueAdapter<V>,
    ): FromAbiHelper<K, V>? =
        if (pointer == MemorySegment.NULL) {
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

private fun <K, V> keyValuePairAdapter(
    keyAdapter: WinRtReferenceValueAdapter<K>,
    valueAdapter: WinRtReferenceValueAdapter<V>,
): WinRtReferenceValueAdapter<Map.Entry<K, V>> =
    WinRtReferenceValueAdapter(
        projectedTypeName = "kotlin.collections.Map.Entry<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        typeSignature = WinRtCollectionInterfaceIds.keyValuePairSignature(keyAdapter.typeSignature, valueAdapter.typeSignature),
        projector = { reference ->
            val pair = reference?.let {
                WinRtKeyValuePairReference(
                    it.pointer,
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
            WinRtInspectableComObject(
                interfaceDefinitions = listOf(
                    WinRtInspectableInterfaceDefinition(
                        interfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                        methods = listOf(
                            WinRtInspectableMethodDefinition(
                                descriptor = FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                ),
                            ) { rawArgs ->
                                (rawArgs[0] as MemorySegment).writeManagedValue(entry.key, keyAdapter)
                                KnownHResults.S_OK.value
                            },
                            WinRtInspectableMethodDefinition(
                                descriptor = FunctionDescriptor.of(
                                    ValueLayout.JAVA_INT,
                                    ValueLayout.ADDRESS,
                                    ValueLayout.ADDRESS,
                                ),
                            ) { rawArgs ->
                                (rawArgs[0] as MemorySegment).writeManagedValue(entry.value, valueAdapter)
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

private fun iterableInterfaceDefinition(
    elementAdapter: WinRtReferenceValueAdapter<*>,
    iteratorFactory: () -> Iterator<*>,
): WinRtInspectableInterfaceDefinition =
    WinRtInspectableInterfaceDefinition(
        interfaceId = iterableInterfaceId(elementAdapter),
        methods = listOf(
            WinRtInspectableMethodDefinition(
                descriptor = FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                ),
            ) { rawArgs ->
                val resultOut = rawArgs[0] as MemorySegment
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
    pointer: MemorySegment,
    adapter: WinRtReferenceValueAdapter<T>,
): T =
    projectBorrowed(
        if (pointer == MemorySegment.NULL) {
            null
        } else {
            IUnknownReference(pointer, preventReleaseOnDispose = true)
        },
        adapter,
    )

private fun MemorySegment.writeReturnedPointer(pointer: MemorySegment) {
    reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, pointer)
}

private fun <T> MemorySegment.writeManagedValues(
    values: List<T>,
    adapter: WinRtReferenceValueAdapter<T>,
) {
    values.forEachIndexed { index, value ->
        setAtIndex(ValueLayout.ADDRESS, index.toLong(), adapter.marshaller(value).useAndGetRef())
    }
}

private fun <T> MemorySegment.writeManagedValue(
    value: T,
    adapter: WinRtReferenceValueAdapter<T>,
) {
    reinterpret(ValueLayout.ADDRESS.byteSize()).set(ValueLayout.ADDRESS, 0, adapter.marshaller(value).useAndGetRef())
}

private fun MemorySegment.writeBoolean(value: Boolean) {
    reinterpret(ValueLayout.JAVA_BYTE.byteSize()).set(ValueLayout.JAVA_BYTE, 0, if (value) 1 else 0)
}

private fun MemorySegment.writeUInt32(value: UInt) {
    reinterpret(ValueLayout.JAVA_INT.byteSize()).set(ValueLayout.JAVA_INT, 0, value.toInt())
}


private data class ProjectionEntrySnapshot<K, V>(
    override val key: K,
    override val value: V,
) : Map.Entry<K, V>
