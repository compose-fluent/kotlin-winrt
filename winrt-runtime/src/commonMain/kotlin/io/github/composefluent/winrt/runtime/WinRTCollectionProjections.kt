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
open class WinRTReferenceValueAdapter<T>(
    val projectedTypeName: String,
    val typeSignature: WinRTTypeSignature,
    val projector: (IUnknownReference?) -> T,
    val marshaller: (T) -> ComObjectReference,
) {
    open val abiValueIsComReference: Boolean = true

    open fun createInputMarshaler(value: T): WinRTObjectMarshaler =
        marshaller(value).let { reference ->
            WinRTObjectMarshaler(reference.pointer.asRawAddress(), reference::close)
        }

    /**
     * Runs an ABI operation while keeping the input representation alive for the duration of
     * the call.  Reference adapters can override this when the ABI view is already borrowed and
     * no owning marshaler object is needed.
     */
    open fun <R> withInputAbi(
        value: T,
        action: (RawAddress) -> R,
    ): R =
        createInputMarshaler(value).use { marshaled ->
            action(marshaled.abi)
        }

    open fun <R> withInputAbiAndPointerOut(
        value: T,
        action: (inputAbi: RawAddress, resultOut: RawAddress) -> R,
    ): R =
        withInputAbi(value) { inputAbi ->
            acquireNativeScalarScratchFrame(clear = true).use { frame ->
                action(inputAbi, frame.pointer)
            }
        }

    internal open fun <R> withInputAbiAndPointerOutRaw(
        value: T,
        action: RawAddressPairAction<R>,
    ): R =
        withInputAbiAndPointerOut(value) { inputAbi, resultOut ->
            action.invoke(inputAbi, resultOut)
        }

    internal open fun asDirectHStringInputOrNull(value: T): String? = null

    open fun createOutputMarshaler(value: T): WinRTObjectMarshaler =
        marshaller(value).let { reference ->
            WinRTObjectMarshaler(reference.getRefPointer().asRawAddress(), reference::close)
        }

    open fun projectAbi(pointer: RawAddress): T {
        val reference = if (PlatformAbi.isNull(pointer)) {
            null
        } else {
            IUnknownReference(pointer.asRawComPtr(), preventReleaseOnDispose = true)
        }
        return try {
            projector(reference)
        } finally {
            reference?.close()
        }
    }

    open fun projectOwnedAbi(pointer: RawAddress): T =
        try {
            projectAbi(pointer)
        } finally {
            disposeAbi(pointer)
        }

    open fun disposeAbi(pointer: RawAddress) {
        if (!PlatformAbi.isNull(pointer)) {
            IUnknownReference(pointer.asRawComPtr()).close()
        }
    }
}

object WinRTReferenceValueAdapters {
    val string: WinRTReferenceValueAdapter<String> =
        object : WinRTReferenceValueAdapter<String>(
            projectedTypeName = "String",
            typeSignature = WinRTTypeSignature.string(),
            projector = { reference ->
                if (reference == null) {
                    ""
                } else {
                    WinRTReferenceReference(
                        pointer = reference.pointer.asRawAddress(),
                        interfaceId = IID.NullableString,
                        preventReleaseOnDispose = true,
                    ).use { valueReference ->
                        ValueBoxingInterop.readReferenceValue(IID.NullableString, valueReference) as? String ?: ""
                    }
                }
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value, IID.NullableString) },
        ) {
            override val abiValueIsComReference: Boolean = false

            override fun asDirectHStringInputOrNull(value: String): String = value

            override fun createInputMarshaler(value: String): WinRTObjectMarshaler {
                val marshaler = NativeStringMarshaller.createMarshaler(value)
                return WinRTObjectMarshaler(NativeStringMarshaller.getAbi(marshaler)) {
                    NativeStringMarshaller.disposeMarshaler(marshaler)
                }
            }

            override fun <R> withInputAbi(
                value: String,
                action: (RawAddress) -> R,
            ): R =
                withNativeHStringReferenceAbi(value) { handle, _ ->
                    action(handle)
                }

            override fun <R> withInputAbiAndPointerOut(
                value: String,
                action: (inputAbi: RawAddress, resultOut: RawAddress) -> R,
            ): R = withNativeHStringReferenceAbi(value) { inputAbi, resultOut ->
                action(inputAbi, resultOut)
            }

            override fun <R> withInputAbiAndPointerOutRaw(
                value: String,
                action: RawAddressPairAction<R>,
            ): R = withNativeHStringReferenceAbi(value, action)

            override fun createOutputMarshaler(value: String): WinRTObjectMarshaler {
                val hstring = NativeStringMarshaller.fromManaged(value)
                return WinRTObjectMarshaler(NativeStringMarshaller.getAbi(hstring))
            }

            override fun projectAbi(pointer: RawAddress): String =
                NativeStringMarshaller.fromAbi(pointer)

            override fun disposeAbi(pointer: RawAddress) {
                NativeStringMarshaller.disposeAbi(pointer)
            }
        }

    val inspectable: WinRTReferenceValueAdapter<IInspectableReference> =
        WinRTReferenceValueAdapter(
            projectedTypeName = "io.github.composefluent.winrt.runtime.IInspectableReference",
            typeSignature = WinRTTypeSignature.object_(),
            projector = { reference ->
                reference?.asInspectable() ?: IInspectableReference(PlatformAbi.nullComPtr, IID.IInspectable)
            },
            marshaller = { value -> IInspectableReference(value.getRefPointer(), IID.IInspectable) },
        )

    val object_: WinRTReferenceValueAdapter<Any?> =
        object : WinRTReferenceValueAdapter<Any?>(
            projectedTypeName = "Any?",
            typeSignature = WinRTTypeSignature.object_(),
            projector = { reference ->
                WinRTObjectMarshaller.fromAbi(reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer)
            },
            marshaller = { value ->
                check(value != null) { "Null System.Object collection values are not supported by this adapter yet." }
                ComWrappersSupport.createCCWForObject(value, IID.IInspectable)
            },
        ) {
            override fun createInputMarshaler(value: Any?): WinRTObjectMarshaler =
                WinRTObjectMarshaller.createMarshaler(value)

            override fun createOutputMarshaler(value: Any?): WinRTObjectMarshaler =
                WinRTObjectMarshaller.createMarshaler(value)
        }

    fun <T : Any> valueType(
        projectedType: KClass<T>,
        projectedTypeName: String,
        typeSignature: WinRTTypeSignature,
    ): WinRTReferenceValueAdapter<T> =
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
        typeSignature: WinRTTypeSignature,
        nullableInterfaceId: Guid,
    ): WinRTReferenceValueAdapter<T> =
        WinRTReferenceValueAdapter(
            projectedTypeName = projectedTypeName,
            typeSignature = typeSignature,
            projector = { reference ->
                val inspectable = reference?.asInspectable()
                    ?: throw WinRTInvalidCastException(
                        "Expected non-null IReference<$projectedTypeName> value.",
                        HResult(TYPE_E_TYPEMISMATCH),
                    )
                try {
                    WinRTValueBoxing.tryProjectInspectableAsType(inspectable, projectedType) as? T
                        ?: throw WinRTInvalidCastException(
                            "Unable to project IReference<$projectedTypeName> value.",
                            HResult(TYPE_E_TYPEMISMATCH),
                        )
                } finally {
                    inspectable.close()
                }
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value, nullableInterfaceId) },
        )

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> runtimeClass(
        projectedType: KClass<T>,
        projectedTypeName: String,
        defaultInterfaceId: Guid,
        fallbackProjector: (IInspectableReference) -> T,
    ): WinRTReferenceValueAdapter<T> =
        runtimeClass(
            projectedType = projectedType,
            typeHandle = WinRTTypeHandle(projectedTypeName, defaultInterfaceId),
            fallbackProjector = fallbackProjector,
        )

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> runtimeClass(
        projectedType: KClass<T>,
        typeHandle: WinRTTypeHandle,
        fallbackProjector: (IInspectableReference) -> T,
    ): WinRTReferenceValueAdapter<T> {
        // Collection ABI results are owned. Keep the statically known type and factory with
        // the adapter so the hot path does not rebuild either descriptor or projector closure.
        val projectedTypeName = typeHandle.projectedTypeName
        val defaultInterfaceId = typeHandle.interfaceId
        return object : WinRTReferenceValueAdapter<T>(
            projectedTypeName = projectedTypeName,
            typeSignature = WinRTTypeSignature.object_(),
            projector = { reference ->
                val inspectable = reference?.asInspectable()
                    ?: throw WinRTInvalidCastException(
                        "Expected non-null $projectedTypeName value.",
                        HResult(TYPE_E_TYPEMISMATCH),
                    )
                var transferredToFallback = false
                try {
                    val projected = ComWrappersSupport.createRcwForComObject(
                        inspectable.pointer.asRawAddress(),
                        typeHandle,
                    ) ?: run {
                        transferredToFallback = true
                        fallbackProjector(inspectable)
                    }
                    if (!projectedType.isInstance(projected)) {
                        throw WinRTInvalidCastException(
                            "Unable to project $projectedTypeName value.",
                            HResult(TYPE_E_TYPEMISMATCH),
                        )
                    }
                    projected as T
                } finally {
                    if (!transferredToFallback) {
                        inspectable.close()
                    }
                }
            },
            marshaller = { value ->
                (value as IWinRTObject).nativeObject.queryInterface(defaultInterfaceId).getOrThrow()
            },
        ) {
            private val ownedFactory: (IUnknownReference, WinRTTypeHandle) -> T = { reference, _ ->
                val inspectable = reference.asInspectable()
                var transferredToFallback = false
                try {
                    val projected = fallbackProjector(inspectable)
                    if (!projectedType.isInstance(projected)) {
                        throw WinRTInvalidCastException(
                            "Unable to project $projectedTypeName value.",
                            HResult(TYPE_E_TYPEMISMATCH),
                        )
                    }
                    transferredToFallback = true
                    projected
                } finally {
                    // The original owned interface is always consumed here. The inspectable
                    // reference is transferred to the generated wrapper only on success.
                    reference.close()
                    if (!transferredToFallback) {
                        inspectable.close()
                    }
                }
            }

            override fun projectOwnedAbi(pointer: RawAddress): T {
                ComWrappersSupport.tryConsumeCachedRcwForOwnedComObject<T>(
                    pointer = pointer,
                    staticallyDeterminedType = typeHandle,
                )?.let { return it }
                return ComWrappersSupport.createRcwForOwnedComObject(
                    pointer = pointer,
                    staticallyDeterminedType = typeHandle,
                    factory = ownedFactory,
                ) ?: throw WinRTInvalidCastException(
                    "Expected non-null $projectedTypeName value.",
                    HResult(TYPE_E_TYPEMISMATCH),
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> genericParameter(projectedTypeName: String): WinRTReferenceValueAdapter<T> =
        WinRTReferenceValueAdapter(
            projectedTypeName = projectedTypeName,
            typeSignature = WinRTTypeSignature.object_(),
            projector = { reference ->
                val pointer = reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer
                if (PlatformAbi.isNull(pointer)) {
                    null as T
                } else {
                    (WinRTInspectableComObject.findManagedValue(pointer)
                        ?: ComWrappersSupport.createRcwForComObject(pointer)) as T
                }
            },
            marshaller = { value -> ComWrappersSupport.createCCWForObject(value as Any, IID.IInspectable) },
        )
}

object WinRTGenericParameterProjection {
    @Suppress("UNCHECKED_CAST")
    fun <T> fromAbi(pointer: RawAddress): T {
        return WinRTObjectMarshaller.fromAbi(pointer) as T
    }

    fun <T> fromManaged(value: T): RawAddress =
        WinRTObjectMarshaller.fromManaged(value)

    fun <T> createReference(value: T): ComObjectReference? =
        value?.let { ComWrappersSupport.createCCWForObject(it as Any, IID.IInspectable) }
}

typealias WinRTCollectionProjectionMarshaler = WinRTProjectionMarshaler

object WinRTIterableProjection {
    class FromAbiHelper<T> internal constructor(
        private val iterable: WinRTIterableReference,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : Iterable<T>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

        override fun iterator(): Iterator<T> =
            WinRTIteratorProjection.FromAbiHelper(
                iterable = iterable.first(iteratorInterfaceId(elementAdapter)),
                elementAdapter = elementAdapter,
                primaryTypeHandle = iteratorTypeHandle(elementAdapter),
            )

        override fun close() {
            iterable.close()
        }
    }

    internal class ToAbiHelper<T>(
        private val managed: Iterable<T>,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = iterableInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = iterableInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(
                                WinRTIteratorProjection.detachReference(managed.iterator(), elementAdapter),
                            )
                            KnownHResults.S_OK.value
                        },
                    ),
                ),
            ),
        )

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, iterableInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(iterableInterfaceId(elementAdapter))
    }

    fun <T> createMarshaler(
        value: Iterable<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): WinRTCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, iterableTypeHandle(elementAdapter))?.let { return it }
        return ToAbiHelper(value, elementAdapter).createMarshaler()
    }

    fun <T> fromManaged(
        value: Iterable<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, iterableTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? {
        val interfaceId = iterableInterfaceId(elementAdapter)
        return ComWrappersSupport.createRcwForOwnedInterfaceProjection(
            pointer = pointer,
            interfaceId = interfaceId,
            typeHandleFactory = { iterableTypeHandle(elementAdapter) },
        ) { ownedPointer, typeHandle ->
            FromAbiHelper(
                iterable = WinRTIterableReference(ownedPointer, interfaceId),
                elementAdapter = elementAdapter,
                primaryTypeHandle = typeHandle,
            )
        }
    }
}

object WinRTIteratorProjection {
    class FromAbiHelper<T> internal constructor(
        private val iterable: WinRTIteratorReference,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : Iterator<T>, IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = iterable

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
                currentValue = projectOwned(iterable.currentAbiOrNull(), elementAdapter)
            }
        }

        private fun advance() {
            hasCurrent = iterable.moveNext()
            currentValue = if (hasCurrent) {
                projectOwned(iterable.currentAbiOrNull(), elementAdapter)
            } else {
                null
            }
        }
    }

    internal class ToAbiHelper<T>(
        managed: Iterator<T>,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
    ) {
        private val state = IteratorState(managed)
        private val host = createCollectionHost(
            managedValue = state,
            defaultInterfaceId = iteratorInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                WinRTInspectableInterfaceDefinition(
                    interfaceId = iteratorInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            if (!state.hasCurrent) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            resultOut.writeManagedValue(state.current(), elementAdapter)
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

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, iteratorInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(iteratorInterfaceId(elementAdapter))
    }

    fun <T> detachReference(
        managed: Iterator<T>,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): RawAddress = ToAbiHelper(managed, elementAdapter).detachReference()

    fun <T> fromReference(
        reference: IUnknownReference,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): FromAbiHelper<T> =
        FromAbiHelper(
            iterable = WinRTIteratorReference(
                reference.getRefPointer().asRawAddress(),
                iteratorInterfaceId(elementAdapter),
            ),
            elementAdapter = elementAdapter,
            primaryTypeHandle = iteratorTypeHandle(elementAdapter),
        )

    fun <T> createReference(
        managed: Iterator<T>,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): IUnknownReference =
        IUnknownReference(
            detachReference(managed, elementAdapter).asRawComPtr(),
            iteratorInterfaceId(elementAdapter),
        )

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

object WinRTReadOnlyListProjection {
    class FromAbiHelper<T> internal constructor(
        private val vectorView: WinRTVectorViewReference,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : AbstractList<T>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vectorView

        private val adapter by lazy {
            WinRTVectorViewListAdapter(
                vectorView = WinRTVectorViewReference(
                    vectorView.pointer.asRawAddress(),
                    vectorView.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                elementAdapter = elementAdapter,
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
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
    ) {
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = vectorViewInterfaceId(elementAdapter),
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = vectorViewInterfaceId(elementAdapter),
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index >= managed.size.toUInt()) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeManagedValue(value, elementAdapter)
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
                            val value = decodeBorrowedValue(rawArgs[0] as RawAddress, elementAdapter)
                            val indexOut = rawArgs[1] as RawAddress
                            val foundOut = rawArgs[2] as RawAddress
                            val index = managed.indexOf(value)
                            foundOut.writeBoolean(index >= 0)
                            indexOut.writeUInt32(if (index >= 0) index.toUInt() else 0u)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
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

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, vectorViewInterfaceId(elementAdapter))

        fun detachReference(): RawAddress = host.detachReference(vectorViewInterfaceId(elementAdapter))
    }

    fun <T> createMarshaler(
        value: List<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): WinRTCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, vectorViewTypeHandle(elementAdapter))?.let { return it }
        return ToAbiHelper(value, elementAdapter).createMarshaler()
    }

    fun <T> fromManaged(
        value: List<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, vectorViewTypeHandle(elementAdapter))
                ?: ToAbiHelper(value, elementAdapter).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? {
        val interfaceId = vectorViewInterfaceId(elementAdapter)
        return ComWrappersSupport.createRcwForOwnedInterfaceProjection(
            pointer = pointer,
            interfaceId = interfaceId,
            typeHandleFactory = { vectorViewTypeHandle(elementAdapter) },
        ) { ownedPointer, typeHandle ->
            FromAbiHelper(
                vectorView = WinRTVectorViewReference(ownedPointer, interfaceId),
                elementAdapter = elementAdapter,
                primaryTypeHandle = typeHandle,
            )
        }
    }
}

object WinRTListProjection {
    class Descriptor<T> internal constructor(
        internal val elementAdapter: WinRTReferenceValueAdapter<T>,
    ) {
        internal val typeSignature: WinRTTypeSignature =
            WinRTCollectionInterfaceIds.vectorSignature(elementAdapter.typeSignature)
        internal val interfaceId: Guid = ParameterizedInterfaceId.createFromSignature(typeSignature)
        internal val typeHandle = WinRTTypeHandle(
            "kotlin.collections.MutableList<${elementAdapter.projectedTypeName}>",
            interfaceId,
        )
        internal val typeHandleFactory: () -> WinRTTypeHandle = { typeHandle }
        internal val ownedFactory: (RawAddress, WinRTTypeHandle) -> FromAbiHelper<T> =
            { ownedPointer, primaryTypeHandle ->
                FromAbiHelper(
                    vector = WinRTVectorReference(ownedPointer, interfaceId),
                    elementAdapter = elementAdapter,
                    primaryTypeHandle = primaryTypeHandle,
                )
            }
    }

    fun <T> descriptor(elementAdapter: WinRTReferenceValueAdapter<T>): Descriptor<T> =
        Descriptor(elementAdapter)

    class FromAbiHelper<T> internal constructor(
        private val vector: WinRTVectorReference,
        private val elementAdapter: WinRTReferenceValueAdapter<T>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : AbstractMutableList<T>(), IWinRTObject, AutoCloseable {
        override val nativeObject: ComObjectReference
            get() = vector

        private val adapter by lazy {
            WinRTVectorListAdapter(
                vector = WinRTVectorReference(
                    vector.pointer.asRawAddress(),
                    vector.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                elementAdapter = elementAdapter,
                elementMarshaller = elementAdapter::createInputMarshaler,
            )
        }

        override val size: Int
            get() = adapter.size

        override fun get(index: Int): T = adapter[index]

        override fun set(index: Int, element: T): T = adapter.set(index, element)

        override fun add(element: T): Boolean = adapter.add(element)

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
        private val descriptor: Descriptor<T>,
    ) {
        private val elementAdapter = descriptor.elementAdapter
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = descriptor.interfaceId,
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = elementAdapter,
                    iteratorFactory = { managed.iterator() },
                ),
                WinRTInspectableInterfaceDefinition(
                    interfaceId = descriptor.interfaceId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val resultOut = rawArgs[1] as RawAddress
                            if (index >= managed.size.toUInt()) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed[index.toInt()]
                            resultOut.writeManagedValue(value, elementAdapter)
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
                            resultOut.writeReturnedPointer(
                                WinRTReadOnlyListProjection.ToAbiHelper(managed.toList(), elementAdapter).detachReference(),
                            )
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
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
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, elementAdapter)
                            if (index.toInt() !in managed.indices) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed[index.toInt()] = value
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Int32, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val index = (rawArgs[0] as Int).toUInt()
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, elementAdapter)
                            if (index.toInt() > managed.size) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            managed.add(index.toInt(), value)
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
                            managed.add(decodeBorrowedValue(rawArgs[0] as RawAddress, elementAdapter))
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
                        WinRTInspectableMethodDefinition(
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
                        WinRTInspectableMethodDefinition(
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

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, descriptor.interfaceId)

        fun detachReference(): RawAddress = host.detachReference(descriptor.interfaceId)
    }

    fun <T> createMarshaler(
        value: MutableList<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): WinRTCollectionProjectionMarshaler? =
        createMarshaler(value, descriptor(elementAdapter))

    fun <T> createMarshaler(
        value: MutableList<T>?,
        descriptor: Descriptor<T>,
    ): WinRTCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, descriptor.typeHandle)?.let { return it }
        return ToAbiHelper(value, descriptor).createMarshaler()
    }

    fun <T> fromManaged(
        value: MutableList<T>?,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): RawAddress = fromManaged(value, descriptor(elementAdapter))

    fun <T> fromManaged(
        value: MutableList<T>?,
        descriptor: Descriptor<T>,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, descriptor.typeHandle)
                ?: ToAbiHelper(value, descriptor).detachReference()
        }

    fun <T> fromAbi(
        pointer: RawAddress,
        elementAdapter: WinRTReferenceValueAdapter<T>,
    ): FromAbiHelper<T>? = fromAbi(pointer, descriptor(elementAdapter))

    fun <T> fromAbi(
        pointer: RawAddress,
        descriptor: Descriptor<T>,
    ): FromAbiHelper<T>? {
        return ComWrappersSupport.createRcwForOwnedInterfaceProjection(
            pointer = pointer,
            interfaceId = descriptor.interfaceId,
            typeHandleFactory = descriptor.typeHandleFactory,
            factory = descriptor.ownedFactory,
        )
    }
}

object WinRTReadOnlyDictionaryProjection {
    class FromAbiHelper<K, V> internal constructor(
        private val mapView: WinRTMapViewReference,
        private val keyAdapter: WinRTReferenceValueAdapter<K>,
        private val valueAdapter: WinRTReferenceValueAdapter<V>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : AbstractMap<K, V>(), IWinRTObject, AutoCloseable {
        private val lookupAction = RawAddressPairAction<V?> { keyAbi, resultOut ->
            mapView.lookupProjectedOrNull(keyAbi, resultOut, valueAdapter)
        }

        override val nativeObject: ComObjectReference
            get() = mapView

        private val adapter by lazy {
            WinRTMapViewAdapter(
                mapView = WinRTMapViewReference(
                    mapView.pointer.asRawAddress(),
                    mapView.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                iterableInterfaceId = iterableInterfaceId(winRTKeyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(winRTKeyValuePairAdapter(keyAdapter, valueAdapter)),
                keyValuePairInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
                keyMarshaller = keyAdapter::createInputMarshaler,
            )
        }

        override val entries: Set<Map.Entry<K, V>>
            get() = adapter.entries

        override fun containsKey(key: K): Boolean =
            keyAdapter.withInputAbi(key) { keyAbi ->
                mapView.hasKey(keyAbi)
            }

        override fun get(key: K): V? {
            val directHString = keyAdapter.asDirectHStringInputOrNull(key)
            return if (directHString == null) {
                keyAdapter.withInputAbiAndPointerOutRaw(key, lookupAction)
            } else {
                mapView.lookupProjectedOrNull(directHString, valueAdapter)
            }
        }

        override fun close() {
            mapView.close()
        }
    }

    internal class ToAbiHelper<K, V>(
        private val managed: Map<K, V>,
        private val keyAdapter: WinRTReferenceValueAdapter<K>,
        private val valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ) {
        private val mapViewId: Guid = interfaceId ?: mapViewInterfaceId(keyAdapter, valueAdapter)
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = mapViewId,
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = winRTKeyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRTInspectableInterfaceDefinition(
                            interfaceId = mapViewId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val resultOut = rawArgs[1] as RawAddress
                            if (!managed.containsKey(key)) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed.getValue(key)
                            resultOut.writeManagedValue(value, valueAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            (rawArgs[1] as RawAddress).writeBoolean(managed.containsKey(key))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
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

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, mapViewId)

        fun detachReference(): RawAddress = host.detachReference(mapViewId)
    }

    fun <K, V> createMarshaler(
        value: Map<K, V>?,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): WinRTCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, mapViewTypeHandle(keyAdapter, valueAdapter, interfaceId))?.let { return it }
        return ToAbiHelper(value, keyAdapter, valueAdapter, interfaceId).createMarshaler()
    }

    fun <K, V> fromManaged(
        value: Map<K, V>?,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, mapViewTypeHandle(keyAdapter, valueAdapter, interfaceId))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter, interfaceId).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: RawAddress,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): FromAbiHelper<K, V>? {
        val resolvedInterfaceId = interfaceId ?: mapViewInterfaceId(keyAdapter, valueAdapter)
        return ComWrappersSupport.createRcwForOwnedInterfaceProjection(
            pointer = pointer,
            interfaceId = resolvedInterfaceId,
            typeHandleFactory = { mapViewTypeHandle(keyAdapter, valueAdapter, resolvedInterfaceId) },
        ) { ownedPointer, typeHandle ->
            FromAbiHelper(
                mapView = WinRTMapViewReference(
                    ownedPointer,
                    resolvedInterfaceId,
                ),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
                primaryTypeHandle = typeHandle,
            )
        }
    }
}

object WinRTDictionaryProjection {
    class FromAbiHelper<K, V> internal constructor(
        private val map: WinRTMapReference,
        private val keyAdapter: WinRTReferenceValueAdapter<K>,
        private val valueAdapter: WinRTReferenceValueAdapter<V>,
        override val primaryTypeHandle: WinRTTypeHandle,
    ) : AbstractMutableMap<K, V>(), IWinRTObject, AutoCloseable {
        private val lookupAction = RawAddressPairAction<V?> { keyAbi, resultOut ->
            map.lookupProjectedOrNull(keyAbi, resultOut, valueAdapter)
        }

        override val nativeObject: ComObjectReference
            get() = map

        private val adapter by lazy {
            WinRTMapAdapter(
                map = WinRTMapReference(
                    map.pointer.asRawAddress(),
                    map.interfaceId,
                    preventReleaseOnDispose = true,
                ),
                mapViewInterfaceId = mapViewInterfaceId(keyAdapter, valueAdapter),
                iterableInterfaceId = iterableInterfaceId(winRTKeyValuePairAdapter(keyAdapter, valueAdapter)),
                iteratorInterfaceId = iteratorInterfaceId(winRTKeyValuePairAdapter(keyAdapter, valueAdapter)),
                keyValuePairInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
                keyMarshaller = keyAdapter::createInputMarshaler,
                valueMarshaller = valueAdapter::createInputMarshaler,
            )
        }

        override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
            get() = adapter.entries

        override fun put(key: K, value: V): V? {
            val previous = get(key)
            keyAdapter.withInputAbi(key) { keyAbi ->
                valueAdapter.withInputAbi(value) { valueAbi ->
                    map.insert(keyAbi, valueAbi)
                }
            }
            return previous
        }

        override fun get(key: K): V? {
            val directHString = keyAdapter.asDirectHStringInputOrNull(key)
            return if (directHString == null) {
                keyAdapter.withInputAbiAndPointerOutRaw(key, lookupAction)
            } else {
                map.lookupProjectedOrNull(directHString, valueAdapter)
            }
        }

        override fun remove(key: K): V? {
            val previous = get(key)
            return keyAdapter.withInputAbi(key) { keyAbi ->
                if (!map.hasKey(keyAbi)) {
                    null
                } else {
                    map.remove(keyAbi)
                    previous
                }
            }
        }

        override fun clear() {
            map.clear()
        }

        override fun close() {
            map.close()
        }
    }

    internal class ToAbiHelper<K, V>(
        private val managed: MutableMap<K, V>,
        private val keyAdapter: WinRTReferenceValueAdapter<K>,
        private val valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ) {
        private val mapId: Guid = interfaceId ?: mapInterfaceId(keyAdapter, valueAdapter)
        private val host = createCollectionHost(
            managedValue = managed,
            defaultInterfaceId = mapId,
            interfaceDefinitions = listOf(
                iterableInterfaceDefinition(
                    elementAdapter = winRTKeyValuePairAdapter(keyAdapter, valueAdapter),
                    iteratorFactory = { managed.entries.map { ProjectionEntrySnapshot(it.key, it.value) }.iterator() },
                ),
                WinRTInspectableInterfaceDefinition(
                            interfaceId = mapId,
                    methods = listOf(
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val resultOut = rawArgs[1] as RawAddress
                            if (!managed.containsKey(key)) {
                                return@WinRTInspectableMethodDefinition KnownHResults.E_BOUNDS.value
                            }
                            val value = managed.getValue(key)
                            resultOut.writeManagedValue(value, valueAdapter)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            (rawArgs[0] as RawAddress).writeUInt32(managed.size.toUInt())
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            (rawArgs[1] as RawAddress).writeBoolean(managed.containsKey(key))
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val resultOut = rawArgs[0] as RawAddress
                            resultOut.writeReturnedPointer(
                                WinRTReadOnlyDictionaryProjection.ToAbiHelper(
                                    managed.toMap(),
                                    keyAdapter,
                                    valueAdapter,
                                ).detachReference(),
                            )
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer, ComAbiValueKind.Pointer, ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            val value = decodeBorrowedValue(rawArgs[1] as RawAddress, valueAdapter)
                            val replaced = managed.containsKey(key)
                            managed[key] = value
                            (rawArgs[2] as RawAddress).writeBoolean(replaced)
                            KnownHResults.S_OK.value
                        },
                        WinRTInspectableMethodDefinition(
                            signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                        ) { rawArgs ->
                            val key = decodeBorrowedValue(rawArgs[0] as RawAddress, keyAdapter)
                            if (!managed.containsKey(key)) {
                                KnownHResults.E_BOUNDS.value
                            } else {
                                managed.remove(key)
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

        fun createMarshaler(): WinRTCollectionProjectionMarshaler =
            WinRTProjectionMarshaler.hosted(host, mapId)

        fun detachReference(): RawAddress = host.detachReference(mapId)
    }

    fun <K, V> createMarshaler(
        value: MutableMap<K, V>?,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): WinRTCollectionProjectionMarshaler? {
        if (value == null) {
            return null
        }
        borrowedProjectionMarshaler(value, mapTypeHandle(keyAdapter, valueAdapter, interfaceId))?.let { return it }
        return ToAbiHelper(value, keyAdapter, valueAdapter, interfaceId).createMarshaler()
    }

    fun <K, V> fromManaged(
        value: MutableMap<K, V>?,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): RawAddress =
        if (value == null) {
            PlatformAbi.nullPointer
        } else {
            borrowedProjectionAbi(value, mapTypeHandle(keyAdapter, valueAdapter, interfaceId))
                ?: ToAbiHelper(value, keyAdapter, valueAdapter, interfaceId).detachReference()
        }

    fun <K, V> fromAbi(
        pointer: RawAddress,
        keyAdapter: WinRTReferenceValueAdapter<K>,
        valueAdapter: WinRTReferenceValueAdapter<V>,
        interfaceId: Guid? = null,
    ): FromAbiHelper<K, V>? {
        val resolvedInterfaceId = interfaceId ?: mapInterfaceId(keyAdapter, valueAdapter)
        return ComWrappersSupport.createRcwForOwnedInterfaceProjection(
            pointer = pointer,
            interfaceId = resolvedInterfaceId,
            typeHandleFactory = { mapTypeHandle(keyAdapter, valueAdapter, resolvedInterfaceId) },
        ) { ownedPointer, typeHandle ->
            FromAbiHelper(
                map = WinRTMapReference(
                    ownedPointer,
                    resolvedInterfaceId,
                ),
                keyAdapter = keyAdapter,
                valueAdapter = valueAdapter,
                primaryTypeHandle = typeHandle,
            )
        }
    }
}

private fun <T> iterableInterfaceId(adapter: WinRTReferenceValueAdapter<T>): Guid =
    WinRTCollectionInterfaceIds.iterable(adapter.typeSignature)

private fun <T> iteratorInterfaceId(adapter: WinRTReferenceValueAdapter<T>): Guid =
    WinRTCollectionInterfaceIds.iterator(adapter.typeSignature)

private fun <T> vectorViewInterfaceId(adapter: WinRTReferenceValueAdapter<T>): Guid =
    WinRTCollectionInterfaceIds.vectorView(adapter.typeSignature)

private fun <K, V> mapViewInterfaceId(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): Guid = WinRTCollectionInterfaceIds.mapView(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <K, V> mapInterfaceId(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): Guid = WinRTCollectionInterfaceIds.map(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <K, V> keyValuePairInterfaceId(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): Guid = WinRTCollectionInterfaceIds.keyValuePair(keyAdapter.typeSignature, valueAdapter.typeSignature)

private fun <T> iterableTypeHandle(adapter: WinRTReferenceValueAdapter<T>): WinRTTypeHandle =
    WinRTTypeHandle("kotlin.collections.Iterable<${adapter.projectedTypeName}>", iterableInterfaceId(adapter))

private fun <T> iteratorTypeHandle(adapter: WinRTReferenceValueAdapter<T>): WinRTTypeHandle =
    WinRTTypeHandle("kotlin.collections.Iterator<${adapter.projectedTypeName}>", iteratorInterfaceId(adapter))

private fun <T> vectorViewTypeHandle(adapter: WinRTReferenceValueAdapter<T>): WinRTTypeHandle =
    WinRTTypeHandle("kotlin.collections.List<${adapter.projectedTypeName}>", vectorViewInterfaceId(adapter))

private fun <K, V> mapViewTypeHandle(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
    interfaceId: Guid? = null,
): WinRTTypeHandle =
    WinRTTypeHandle(
        "kotlin.collections.Map<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        interfaceId ?: mapViewInterfaceId(keyAdapter, valueAdapter),
    )

private fun <K, V> mapTypeHandle(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
    interfaceId: Guid? = null,
): WinRTTypeHandle =
    WinRTTypeHandle(
        "kotlin.collections.MutableMap<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        interfaceId ?: mapInterfaceId(keyAdapter, valueAdapter),
    )

fun <K, V> winRTKeyValuePairAdapter(
    keyAdapter: WinRTReferenceValueAdapter<K>,
    valueAdapter: WinRTReferenceValueAdapter<V>,
): WinRTReferenceValueAdapter<Map.Entry<K, V>> =
    WinRTReferenceValueAdapter(
        projectedTypeName = "kotlin.collections.Map.Entry<${keyAdapter.projectedTypeName}, ${valueAdapter.projectedTypeName}>",
        typeSignature = WinRTCollectionInterfaceIds.keyValuePairSignature(keyAdapter.typeSignature, valueAdapter.typeSignature),
        projector = { reference ->
            val pair = reference?.let {
                WinRTKeyValuePairReference(
                    it.pointer.asRawAddress(),
                    keyValuePairInterfaceId(keyAdapter, valueAdapter),
                    preventReleaseOnDispose = true,
                )
            } ?: return@WinRTReferenceValueAdapter ProjectionEntrySnapshot(
                keyAdapter.projector(null),
                valueAdapter.projector(null),
            )
            pair.use {
                ProjectionEntrySnapshot(
                    projectOwned(it.keyAbiOrNull(), keyAdapter),
                    projectOwned(it.valueAbiOrNull(), valueAdapter),
                )
            }
        },
        marshaller = { entry ->
            createCollectionHost(
                managedValue = entry,
                defaultInterfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                interfaceDefinitions = listOf(
                    WinRTInspectableInterfaceDefinition(
                        interfaceId = keyValuePairInterfaceId(keyAdapter, valueAdapter),
                        methods = listOf(
                            WinRTInspectableMethodDefinition(
                                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
                            ) { rawArgs ->
                                (rawArgs[0] as RawAddress).writeManagedValue(entry.key, keyAdapter)
                                KnownHResults.S_OK.value
                            },
                            WinRTInspectableMethodDefinition(
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
    interfaceDefinitions: List<WinRTInspectableInterfaceDefinition>,
): WinRTInspectableComObject {
    val definition = InteropRuntimeHooks.augmentInspectableDefinition(
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
        shapeCacheKey = definition,
    )
}

private fun iterableInterfaceDefinition(
    elementAdapter: WinRTReferenceValueAdapter<*>,
    iteratorFactory: () -> Iterator<*>,
): WinRTInspectableInterfaceDefinition =
    WinRTInspectableInterfaceDefinition(
        interfaceId = iterableInterfaceId(elementAdapter),
        methods = listOf(
            WinRTInspectableMethodDefinition(
                signature = ComMethodSignature.of(ComAbiValueKind.Pointer),
            ) { rawArgs ->
                val resultOut = rawArgs[0] as RawAddress
                @Suppress("UNCHECKED_CAST")
                resultOut.writeReturnedPointer(
                    WinRTIteratorProjection.detachReference(
                        iteratorFactory(),
                        elementAdapter as WinRTReferenceValueAdapter<Any?>,
                    ),
                )
                KnownHResults.S_OK.value
            },
        ),
    )

private fun <T> projectBorrowed(
    reference: IUnknownReference?,
    adapter: WinRTReferenceValueAdapter<T>,
): T = try {
    adapter.projectAbi(reference?.pointer?.asRawAddress() ?: PlatformAbi.nullPointer)
} finally {
    if (adapter.abiValueIsComReference) {
        reference?.close()
    }
}

private fun <T> projectOwned(
    pointer: RawAddress?,
    adapter: WinRTReferenceValueAdapter<T>,
): T = adapter.projectOwnedAbi(pointer ?: PlatformAbi.nullPointer)

private fun <T> decodeBorrowedValue(
    pointer: RawAddress,
    adapter: WinRTReferenceValueAdapter<T>,
): T = adapter.projectAbi(pointer)

private fun RawAddress.writeReturnedPointer(pointer: RawAddress) {
    PlatformAbi.writePointer(this, pointer)
}

private fun <T> RawAddress.writeManagedValues(
    values: List<T>,
    adapter: WinRTReferenceValueAdapter<T>,
) {
    values.forEachIndexed { index, value ->
        adapter.createOutputMarshaler(value).use { marshaler ->
            PlatformAbi.writePointerAt(this, index, marshaler.abi)
        }
    }
}

private fun <T> RawAddress.writeManagedValue(
    value: T,
    adapter: WinRTReferenceValueAdapter<T>,
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
