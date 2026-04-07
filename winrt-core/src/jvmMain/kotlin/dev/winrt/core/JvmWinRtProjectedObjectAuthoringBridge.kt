package dev.winrt.core

import dev.winrt.kom.AbiIntPtr
import dev.winrt.kom.ComPtr
import dev.winrt.kom.ComStructValue
import dev.winrt.kom.Guid
import dev.winrt.kom.HResult
import dev.winrt.kom.HString
import dev.winrt.kom.JvmWinRtRuntime
import dev.winrt.kom.KnownHResults
import dev.winrt.kom.PlatformComInterop
import dev.winrt.kom.PlatformHStringBridge
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.util.IdentityHashMap

internal actual object WinRtProjectedObjectAuthoringBridge {
    private val iterableIidText = "faa585ea-6214-4217-afda-7f46de5869b3"
    private val iteratorIidText = "6a79e863-4300-459a-9966-cbb660963ee1"
    private val vectorIidText = "913337e9-11a1-4345-a3a2-4e7f956e222d"
    private val vectorViewIidText = "bbe1fa4c-b0e3-4583-baef-1f1b2e483e56"
    private val mapIidText = "3c2925fe-8519-45c1-aa79-197b6718c1c1"
    private val mapViewIidText = "e480ce40-a338-4ada-adcf-272272e48cb9"
    private val keyValuePairIidText = "02b51929-c1c4-4a7e-8940-0312b5c18500"
    private val iReferenceIidText = "61c17706-2d65-11e0-9ae8-d48564015472"
    private val bindableIterableIidText = "036d2c08-df29-41af-8aa2-d774be62ba6f"
    private val bindableIteratorIidText = "6a1d6c07-076d-49f2-8314-f52c9c9a8331"
    private val bindableVectorIidText = "393de7de-6fd0-4c0d-bb71-47244a113e93"
    private val bindableVectorViewIidText = "346dd6e7-976e-4bc3-815d-ece243bc0f33"
    private val hResultStructSignature = WinRtTypeSignature.struct("Windows.Foundation.HResult", "i4")

    private val iterableIid = guidOf(iterableIidText)
    private val iteratorIid = guidOf(iteratorIidText)
    private val vectorIid = guidOf(vectorIidText)
    private val vectorViewIid = guidOf(vectorViewIidText)
    private val mapIid = guidOf(mapIidText)
    private val mapViewIid = guidOf(mapViewIidText)
    private val keyValuePairIid = guidOf(keyValuePairIidText)
    private val iReferenceIid = guidOf(iReferenceIidText)
    private val bindableIterableIid = guidOf(bindableIterableIidText)
    private val bindableIteratorIid = guidOf(bindableIteratorIidText)
    private val bindableVectorIid = guidOf(bindableVectorIidText)
    private val bindableVectorViewIid = guidOf(bindableVectorViewIidText)

    private val cache = IdentityHashMap<Any, MutableMap<String, ProjectedObjectHandle>>()

    actual fun createPointerOrNull(
        value: Any,
        projectionTypeKey: String,
        signature: String,
    ): ComPtr? {
        val parsedProjectionTypeKey = parseProjectionTypeKey(projectionTypeKey)
        val cacheKey = "$projectionTypeKey::$signature"
        return synchronized(cache) {
            val handles = cache.getOrPut(value) { linkedMapOf() }
            val cached = handles[cacheKey]
            if (cached != null) {
                return@synchronized cached.pointer
            }
            val created = createProjectedHandle(value, parsedProjectionTypeKey, signature) ?: return@synchronized null
            handles[cacheKey] = created
            created.pointer
        }
    }

    private fun createProjectedHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: String,
    ): ProjectedObjectHandle? {
        parseParameterizedInterfaceSignature(signature)?.let { parsedSignature ->
            return createProjectedHandle(value, projectionTypeKey, parsedSignature)
        }
        parseRawInterfaceSignature(signature)?.let { iid ->
            return createRawInterfaceHandle(value, projectionTypeKey, iid)
        }
        return null
    }

    private fun createProjectedHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        return when (signature.iid.canonical) {
            iterableIid.canonical -> createIterableHandle(value, projectionTypeKey, signature)
            iteratorIid.canonical -> createIteratorHandle(value, projectionTypeKey, signature)
            vectorIid.canonical -> createVectorHandle(value, projectionTypeKey, signature)
            vectorViewIid.canonical -> createVectorViewHandle(value, projectionTypeKey, signature)
            mapIid.canonical -> createMapHandle(value, projectionTypeKey, signature)
            mapViewIid.canonical -> createMapViewHandle(value, projectionTypeKey, signature)
            keyValuePairIid.canonical -> createKeyValuePairHandle(value, projectionTypeKey, signature)
            iReferenceIid.canonical -> createReferenceHandle(value, projectionTypeKey, signature)
            bindableIterableIid.canonical -> createBindableIterableHandle(value, projectionTypeKey)
            bindableIteratorIid.canonical -> createBindableIteratorHandle(value, projectionTypeKey)
            else -> null
        }
    }

    private fun createRawInterfaceHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        iid: Guid,
    ): ProjectedObjectHandle? {
        return when (iid.canonical) {
            bindableIterableIid.canonical -> createBindableIterableHandle(value, projectionTypeKey)
            bindableIteratorIid.canonical -> createBindableIteratorHandle(value, projectionTypeKey)
            bindableVectorIid.canonical -> createBindableVectorHandle(value, projectionTypeKey)
            bindableVectorViewIid.canonical -> createBindableVectorViewHandle(value, projectionTypeKey)
            else -> null
        }
    }

    private fun createReferenceHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        val reference = value as? IReference<*> ?: return null
        val elementSignature = signature.arguments.singleOrNull() ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull()
            ?: inferProjectionTypeKey(elementSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val primitiveKind = primitiveAbiKind(elementSignature)
        val interfaceSpec = when {
            elementSignature is AbiValueSignature.StringType -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgHStringMethods = mapOf(
                    6 to {
                        reference.value as? String
                            ?: error("Expected IReference value to be String, got ${reference.value?.let { it::class.qualifiedName }}")
                    },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).referenceInterfaceSpec(signature, reference)
            elementSignature is AbiValueSignature.ObjectType &&
                elementSignature.rawSignature == hResultStructSignature -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgInt32Methods = mapOf(
                    6 to {
                        when (val error = reference.value) {
                            null -> hResultOfException(null)
                            is Exception -> hResultOfException(error)
                            else -> error(
                                "Expected IReference<HResult> value to be Exception?, got ${error::class.qualifiedName}",
                            )
                        }
                    },
                ),
            )
            elementSignature is AbiValueSignature.ObjectType &&
                elementSignature.rawSignature.startsWith("struct(") -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgStructMethods = mapOf(
                    6 to { marshalStructAbiValue(requireNotNull(reference.value) { "IReference struct values cannot be null" }) },
                ),
            )
            elementSignature is AbiValueSignature.ObjectType &&
                elementSignature.rawSignature.startsWith("enum(") -> createEnumReferenceInterfaceSpec(
                signature = signature,
                reference = reference,
                elementSignature = elementSignature.rawSignature,
            )
            else -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgObjectMethods = mapOf(
                    6 to {
                        marshalObjectResultPointer(
                            value = reference.value,
                            projectionTypeKey = elementProjectionTypeKey,
                            signature = elementSignature,
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
            )
        }
        val stub = JvmWinRtObjectStub.create(interfaceSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createEnumReferenceInterfaceSpec(
        signature: AbiValueSignature.ParameterizedInterface,
        reference: IReference<*>,
        elementSignature: String,
    ): JvmWinRtObjectStub.InterfaceSpec {
        val value = requireNotNull(reference.value) { "IReference enum values cannot be null" }
        return when (enumUnderlyingSignature(elementSignature)) {
            "u4" -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgUInt32Methods = mapOf(6 to { marshalEnumUInt32Value(value) }),
            )
            "i8" -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgInt64Methods = mapOf(6 to { marshalEnumInt64Value(value) }),
            )
            "u8" -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgUInt64Methods = mapOf(6 to { marshalEnumUInt64Value(value) }),
            )
            else -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgInt32Methods = mapOf(6 to { marshalEnumInt32Value(value) }),
            )
        }
    }

    private fun createIterableHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        val iterable = value as? Iterable<*> ?: return null
        val elementSignature = signature.arguments.singleOrNull() ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull()
            ?: inferProjectionTypeKey(elementSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val iteratorSignature = AbiValueSignature.ParameterizedInterface(
            iid = iteratorIid,
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iteratorIidText,
                elementSignature.rawSignature,
            ),
        )
        val stub = JvmWinRtObjectStub.create(
            JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgObjectMethods = mapOf(
                    6 to iterableFirstMethod(
                        iterable = iterable,
                        elementProjectionTypeKey = elementProjectionTypeKey,
                        iteratorSignature = iteratorSignature,
                        retainedChildren = retainedChildren,
                    ),
                ),
            ),
        )
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createIteratorHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        val iterator = value as? Iterator<*> ?: return null
        val elementSignature = signature.arguments.singleOrNull() ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull()
            ?: inferProjectionTypeKey(elementSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val state = IteratorState(iterator)
        val primitiveKind = primitiveAbiKind(elementSignature)
        val getManyMethod = iteratorGetManyMethod(
            state = state,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = elementSignature,
            retainedChildren = retainedChildren,
        )
        val baseSpec = when {
            elementSignature is AbiValueSignature.StringType -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgHStringMethods = mapOf(
                    6 to {
                        val current = state.requireCurrentValue("IIterator")
                        current as? String
                            ?: error("Expected iterator element to be String, got ${current?.let { it::class.qualifiedName }}")
                    },
                ),
                noArgBooleanMethods = mapOf(
                    7 to { state.hasCurrent },
                    8 to { state.moveNext() },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).iteratorInterfaceSpec(signature, state)
            else -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgObjectMethods = mapOf(
                    6 to {
                        marshalObjectResultPointer(
                            value = state.requireCurrentValue("IIterator"),
                            projectionTypeKey = elementProjectionTypeKey,
                            signature = elementSignature,
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
                noArgBooleanMethods = mapOf(
                    7 to { state.hasCurrent },
                    8 to { state.moveNext() },
                ),
            )
        }
        val interfaceSpec = baseSpec.copy(
            noArgCallerAllocatedArrayMethods = mapOf(9 to getManyMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createVectorViewHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        val list = value as? List<*> ?: return null
        val elementSignature = signature.arguments.singleOrNull() ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull()
            ?: inferProjectionTypeKey(elementSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val iteratorSignature = AbiValueSignature.ParameterizedInterface(
            iid = iteratorIid,
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iteratorIidText,
                elementSignature.rawSignature,
            ),
        )
        val iterableSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    iterableIidText,
                    elementSignature.rawSignature,
                ),
            ),
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iterableIidText,
                elementSignature.rawSignature,
            ),
        )
        val firstMethod = iterableFirstMethod(
            iterable = list,
            elementProjectionTypeKey = elementProjectionTypeKey,
            iteratorSignature = iteratorSignature,
            retainedChildren = retainedChildren,
        )
        val getManyMethod = vectorGetManyMethod(
            list = list,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = elementSignature,
            retainedChildren = retainedChildren,
        )
        val primitiveKind = primitiveAbiKind(elementSignature)
        val baseSpec = when {
            elementSignature is AbiValueSignature.StringType -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgObjectMethods = mapOf(6 to firstMethod),
                uint32ArgHStringMethods = mapOf(
                    7 to { index ->
                        val element = list.elementAt(index.toInt())
                        element as? String
                            ?: error("Expected list element at $index to be String, got ${element?.let { it::class.qualifiedName }}")
                    },
                ),
                noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
                stringArgIndexOfMethods = mapOf(
                    9 to { element -> indexOfOrNull(list.indexOf(element)) },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).vectorViewInterfaceSpec(
                iid = signature.iid,
                list = list,
                firstMethod = firstMethod,
            )
            else -> createObjectVectorViewInterfaceSpec(
                iid = signature.iid,
                list = list,
                firstMethod = firstMethod,
                elementProjectionTypeKey = elementProjectionTypeKey,
                elementSignature = elementSignature,
                retainedChildren = retainedChildren,
            )
        }
        val derivedSpec = baseSpec.copy(
            uint32ArgCallerAllocatedArrayMethods = mapOf(10 to getManyMethod),
        )
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = iterableSignature.iid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(derivedSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createVectorHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        @Suppress("UNCHECKED_CAST")
        val list = value as? MutableList<Any?> ?: return null
        val elementSignature = signature.arguments.singleOrNull() ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull()
            ?: inferProjectionTypeKey(elementSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val iteratorSignature = AbiValueSignature.ParameterizedInterface(
            iid = iteratorIid,
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iteratorIidText,
                elementSignature.rawSignature,
            ),
        )
        val iterableSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    iterableIidText,
                    elementSignature.rawSignature,
                ),
            ),
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iterableIidText,
                elementSignature.rawSignature,
            ),
        )
        val vectorViewSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    vectorViewIidText,
                    elementSignature.rawSignature,
                ),
            ),
            arguments = listOf(elementSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                vectorViewIidText,
                elementSignature.rawSignature,
            ),
        )
        val firstMethod: () -> ComPtr = iterableFirstMethod(
            iterable = list,
            elementProjectionTypeKey = elementProjectionTypeKey,
            iteratorSignature = iteratorSignature,
            retainedChildren = retainedChildren,
        )
        val getViewMethod: () -> ComPtr = {
            val vectorViewHandle = requireNotNull(
                createVectorViewHandle(
                    list,
                    ProjectionTypeKey("kotlin.collections.List", listOf(elementProjectionTypeKey)),
                    vectorViewSignature,
                ),
            )
            retainedChildren += vectorViewHandle
            vectorViewHandle.pointer.withAddRef()
        }
        val getManyMethod = vectorGetManyMethod(
            list = list,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = elementSignature,
            retainedChildren = retainedChildren,
        )
        val replaceAllMethod = vectorReplaceAllMethod(
            list = list,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = elementSignature,
        )
        val primitiveKind = primitiveAbiKind(elementSignature)
        val baseInterfaceSpec = when {
            elementSignature is AbiValueSignature.StringType -> JvmWinRtObjectStub.InterfaceSpec(
                iid = signature.iid,
                noArgUnitMethods = mapOf(
                    15 to {
                        if (list.isEmpty()) {
                            KnownHResults.E_BOUNDS
                        } else {
                            list.removeAt(list.lastIndex)
                            HResult(0)
                        }
                    },
                    16 to {
                        list.clear()
                        HResult(0)
                    },
                ),
                noArgObjectMethods = mapOf(
                    6 to firstMethod,
                    9 to getViewMethod,
                ),
                noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
                uint32ArgUnitMethods = mapOf(
                    13 to { index ->
                        list.removeAt(index.toInt())
                        HResult(0)
                    },
                ),
                uint32StringArgUnitMethods = mapOf(
                    11 to { index, element ->
                        list[index.toInt()] = element
                        HResult(0)
                    },
                    12 to { index, element ->
                        list.add(index.toInt(), element)
                        HResult(0)
                    },
                ),
                uint32ArgHStringMethods = mapOf(
                    7 to { index ->
                        list.elementAt(index.toInt()) as? String
                            ?: error(
                                "Expected mutable list element at $index to be String, got " +
                                    "${list.elementAt(index.toInt())?.let { it::class.qualifiedName }}",
                            )
                    },
                ),
                stringArgUnitMethods = mapOf(
                    14 to { element ->
                        list.add(element)
                        HResult(0)
                    },
                ),
                stringArgIndexOfMethods = mapOf(
                    10 to { element -> indexOfOrNull(list.indexOf(element)) },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).vectorInterfaceSpec(
                iid = signature.iid,
                list = list,
                firstMethod = firstMethod,
                getViewMethod = getViewMethod,
                elementProjectionTypeKey = elementProjectionTypeKey,
            )
            else -> createObjectVectorInterfaceSpec(
                iid = signature.iid,
                list = list,
                firstMethod = firstMethod,
                getViewMethod = getViewMethod,
                elementProjectionTypeKey = elementProjectionTypeKey,
                elementSignature = elementSignature,
                retainedChildren = retainedChildren,
            )
        }
        val interfaceSpec = baseInterfaceSpec.copy(
            uint32ArgCallerAllocatedArrayMethods = mapOf(17 to getManyMethod),
            callerAllocatedArrayArgUnitMethods = mapOf(18 to replaceAllMethod),
        )
        val baseVectorViewSpec = when {
            elementSignature is AbiValueSignature.StringType -> JvmWinRtObjectStub.InterfaceSpec(
                iid = vectorViewSignature.iid,
                noArgObjectMethods = mapOf(6 to firstMethod),
                uint32ArgHStringMethods = mapOf(
                    7 to { index ->
                        list.elementAt(index.toInt()) as? String
                            ?: error(
                                "Expected mutable list element at $index to be String, got " +
                                    "${list.elementAt(index.toInt())?.let { it::class.qualifiedName }}",
                            )
                    },
                ),
                noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
                stringArgIndexOfMethods = mapOf(
                    9 to { element -> indexOfOrNull(list.indexOf(element)) },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).vectorViewInterfaceSpec(
                iid = vectorViewSignature.iid,
                list = list,
                firstMethod = firstMethod,
            )
            else -> createObjectVectorViewInterfaceSpec(
                iid = vectorViewSignature.iid,
                list = list,
                firstMethod = firstMethod,
                elementProjectionTypeKey = elementProjectionTypeKey,
                elementSignature = elementSignature,
                retainedChildren = retainedChildren,
            )
        }
        val vectorViewSpec = baseVectorViewSpec.copy(
            uint32ArgCallerAllocatedArrayMethods = mapOf(10 to getManyMethod),
        )
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = iterableSignature.iid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec, vectorViewSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private class PrimitiveSlotBinder<T : Any>(
        private val kind: PrimitiveAbiKind,
        private val marshalValue: (Any?) -> T,
        private val bindNoArgValueMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, () -> T>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindUInt32ArgValueMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (UInt) -> T>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindIndexOfMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (T) -> UInt?>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindIndexedMutationMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (UInt, T) -> HResult>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindAppendMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (T) -> HResult>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindStringArgValueMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (String) -> T>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
        private val bindStringArgMutationMethods: (
            JvmWinRtObjectStub.InterfaceSpec,
            Map<Int, (String, T) -> Boolean>,
        ) -> JvmWinRtObjectStub.InterfaceSpec,
    ) {
        fun referenceInterfaceSpec(
            signature: AbiValueSignature.ParameterizedInterface,
            reference: IReference<*>,
        ): JvmWinRtObjectStub.InterfaceSpec {
            return bindNoArgValueMethods(
                JvmWinRtObjectStub.InterfaceSpec(iid = signature.iid),
                mapOf(6 to { marshalValue(reference.value) }),
            )
        }

        fun addNoArgValueMethod(
            baseSpec: JvmWinRtObjectStub.InterfaceSpec,
            slot: Int,
            valueProvider: () -> Any?,
        ): JvmWinRtObjectStub.InterfaceSpec {
            return bindNoArgValueMethods(
                baseSpec,
                mapOf(slot to { marshalValue(valueProvider()) }),
            )
        }

        fun iteratorInterfaceSpec(
            signature: AbiValueSignature.ParameterizedInterface,
            state: IteratorState,
        ): JvmWinRtObjectStub.InterfaceSpec {
            val stateMethods = mapOf(
                7 to { state.hasCurrent },
                8 to { state.moveNext() },
            )
            return bindNoArgValueMethods(
                JvmWinRtObjectStub.InterfaceSpec(
                    iid = signature.iid,
                    noArgBooleanMethods = stateMethods,
                ),
                mapOf(6 to { marshalValue(currentIteratorValue(state)) }),
            )
        }

        fun vectorViewInterfaceSpec(
            iid: Guid,
            list: List<*>,
            firstMethod: () -> ComPtr,
        ): JvmWinRtObjectStub.InterfaceSpec {
            val baseSpec = JvmWinRtObjectStub.InterfaceSpec(
                iid = iid,
                noArgObjectMethods = mapOf(6 to firstMethod),
                noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
            )
            return bindIndexOfMethods(
                bindUInt32ArgValueMethods(
                    baseSpec,
                    mapOf(7 to { index -> marshalValue(list.elementAt(index.toInt())) }),
                ),
                mapOf(
                    9 to { element -> indexOfOrNull(primitiveIndexOf(list, kind, element)) },
                ),
            )
        }

        fun vectorInterfaceSpec(
            iid: Guid,
            list: MutableList<Any?>,
            firstMethod: () -> ComPtr,
            getViewMethod: () -> ComPtr,
            elementProjectionTypeKey: String,
        ): JvmWinRtObjectStub.InterfaceSpec {
            val baseSpec = JvmWinRtObjectStub.InterfaceSpec(
                iid = iid,
                noArgUnitMethods = mapOf(
                    15 to {
                        if (list.isEmpty()) {
                            KnownHResults.E_BOUNDS
                        } else {
                            list.removeAt(list.lastIndex)
                            HResult(0)
                        }
                    },
                    16 to {
                        list.clear()
                        HResult(0)
                    },
                ),
                noArgObjectMethods = mapOf(
                    6 to firstMethod,
                    9 to getViewMethod,
                ),
                noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
                uint32ArgUnitMethods = mapOf(
                    13 to { index: UInt ->
                        list.removeAt(index.toInt())
                        HResult(0)
                    },
                ),
            )
            fun projectElement(element: T): Any = projectPrimitiveValueFromAbi(kind, elementProjectionTypeKey, element)
            return bindAppendMethods(
                bindIndexOfMethods(
                    bindIndexedMutationMethods(
                        bindUInt32ArgValueMethods(
                            baseSpec,
                            mapOf(7 to { index -> marshalValue(list.elementAt(index.toInt())) }),
                        ),
                        mapOf(
                            11 to { index, element ->
                                list[index.toInt()] = projectElement(element)
                                HResult(0)
                            },
                            12 to { index, element ->
                                list.add(index.toInt(), projectElement(element))
                                HResult(0)
                            },
                        ),
                    ),
                    mapOf(
                        10 to { element -> indexOfOrNull(primitiveIndexOf(list, kind, element)) },
                    ),
                ),
                mapOf(
                    14 to { element ->
                        list.add(projectElement(element))
                        HResult(0)
                    },
                ),
            )
        }

        fun mapViewInterfaceSpec(
            baseSpec: JvmWinRtObjectStub.InterfaceSpec,
            lookupValue: (String) -> Any?,
        ): JvmWinRtObjectStub.InterfaceSpec {
            return bindStringArgValueMethods(
                baseSpec,
                mapOf(6 to { key -> marshalValue(lookupValue(key)) }),
            )
        }

        fun mapInterfaceSpec(
            baseSpec: JvmWinRtObjectStub.InterfaceSpec,
            map: MutableMap<String, Any?>,
            lookupValue: (String) -> Any?,
            valueProjectionTypeKey: String,
        ): JvmWinRtObjectStub.InterfaceSpec {
            fun projectElement(element: T): Any = projectPrimitiveValueFromAbi(kind, valueProjectionTypeKey, element)
            return bindStringArgMutationMethods(
                bindStringArgValueMethods(
                    baseSpec,
                    mapOf(6 to { key -> marshalValue(lookupValue(key)) }),
                ),
                mapOf(
                    10 to { key, element ->
                        val replaced = map.containsKey(key)
                        map[key] = projectElement(element)
                        replaced
                    },
                ),
            )
        }
    }

    private val booleanPrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.BOOLEAN,
        marshalValue = ::marshalPrimitiveBooleanValue,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgBooleanMethods = spec.noArgBooleanMethods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgBooleanMethods = spec.uint32ArgBooleanMethods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(booleanArgIndexOfMethods = spec.booleanArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32BooleanArgUnitMethods = spec.uint32BooleanArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(booleanArgUnitMethods = spec.booleanArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgBooleanMethods = spec.stringArgBooleanMethods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringBooleanArgBooleanMethods = spec.stringBooleanArgBooleanMethods + methods) },
    )

    private val int32PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.INT32,
        marshalValue = ::marshalPrimitiveInt32Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgInt32Methods = spec.noArgInt32Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgInt32Methods = spec.uint32ArgInt32Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(int32ArgIndexOfMethods = spec.int32ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32Int32ArgUnitMethods = spec.uint32Int32ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(int32ArgUnitMethods = spec.int32ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgInt32Methods = spec.stringArgInt32Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringInt32ArgBooleanMethods = spec.stringInt32ArgBooleanMethods + methods) },
    )

    private val uint32PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.UINT32,
        marshalValue = ::marshalPrimitiveUInt32Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgUInt32Methods = spec.noArgUInt32Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgUInt32Methods = spec.uint32ArgUInt32Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(uint32ArgIndexOfMethods = spec.uint32ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32UInt32ArgUnitMethods = spec.uint32UInt32ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(uint32ArgUnitMethods = spec.uint32ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgUInt32Methods = spec.stringArgUInt32Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringUInt32ArgBooleanMethods = spec.stringUInt32ArgBooleanMethods + methods) },
    )

    private val int64PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.INT64,
        marshalValue = ::marshalPrimitiveInt64Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgInt64Methods = spec.noArgInt64Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgInt64Methods = spec.uint32ArgInt64Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(int64ArgIndexOfMethods = spec.int64ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32Int64ArgUnitMethods = spec.uint32Int64ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(int64ArgUnitMethods = spec.int64ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgInt64Methods = spec.stringArgInt64Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringInt64ArgBooleanMethods = spec.stringInt64ArgBooleanMethods + methods) },
    )

    private val uint64PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.UINT64,
        marshalValue = ::marshalPrimitiveUInt64Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgUInt64Methods = spec.noArgUInt64Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgUInt64Methods = spec.uint32ArgUInt64Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(uint64ArgIndexOfMethods = spec.uint64ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32UInt64ArgUnitMethods = spec.uint32UInt64ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(uint64ArgUnitMethods = spec.uint64ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgUInt64Methods = spec.stringArgUInt64Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringUInt64ArgBooleanMethods = spec.stringUInt64ArgBooleanMethods + methods) },
    )

    private val float32PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.FLOAT32,
        marshalValue = ::marshalPrimitiveFloat32Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgFloat32Methods = spec.noArgFloat32Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgFloat32Methods = spec.uint32ArgFloat32Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(float32ArgIndexOfMethods = spec.float32ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32Float32ArgUnitMethods = spec.uint32Float32ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(float32ArgUnitMethods = spec.float32ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgFloat32Methods = spec.stringArgFloat32Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringFloat32ArgBooleanMethods = spec.stringFloat32ArgBooleanMethods + methods) },
    )

    private val float64PrimitiveSlotBinder = PrimitiveSlotBinder(
        kind = PrimitiveAbiKind.FLOAT64,
        marshalValue = ::marshalPrimitiveFloat64Value,
        bindNoArgValueMethods = { spec, methods -> spec.copy(noArgFloat64Methods = spec.noArgFloat64Methods + methods) },
        bindUInt32ArgValueMethods = { spec, methods -> spec.copy(uint32ArgFloat64Methods = spec.uint32ArgFloat64Methods + methods) },
        bindIndexOfMethods = { spec, methods -> spec.copy(float64ArgIndexOfMethods = spec.float64ArgIndexOfMethods + methods) },
        bindIndexedMutationMethods = { spec, methods -> spec.copy(uint32Float64ArgUnitMethods = spec.uint32Float64ArgUnitMethods + methods) },
        bindAppendMethods = { spec, methods -> spec.copy(float64ArgUnitMethods = spec.float64ArgUnitMethods + methods) },
        bindStringArgValueMethods = { spec, methods -> spec.copy(stringArgFloat64Methods = spec.stringArgFloat64Methods + methods) },
        bindStringArgMutationMethods = { spec, methods -> spec.copy(stringFloat64ArgBooleanMethods = spec.stringFloat64ArgBooleanMethods + methods) },
    )

    private fun primitiveSlotBinder(primitiveKind: PrimitiveAbiKind): PrimitiveSlotBinder<*> {
        return when (primitiveKind) {
            PrimitiveAbiKind.BOOLEAN -> booleanPrimitiveSlotBinder
            PrimitiveAbiKind.INT32 -> int32PrimitiveSlotBinder
            PrimitiveAbiKind.UINT32 -> uint32PrimitiveSlotBinder
            PrimitiveAbiKind.INT64 -> int64PrimitiveSlotBinder
            PrimitiveAbiKind.UINT64 -> uint64PrimitiveSlotBinder
            PrimitiveAbiKind.FLOAT32 -> float32PrimitiveSlotBinder
            PrimitiveAbiKind.FLOAT64 -> float64PrimitiveSlotBinder
        }
    }

    private fun createMapViewHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        @Suppress("UNCHECKED_CAST")
        val map = value as? Map<String, Any?> ?: return null
        val keySignature = signature.arguments.getOrNull(0) ?: return null
        val valueSignature = signature.arguments.getOrNull(1) ?: return null
        if (keySignature !is AbiValueSignature.StringType) {
            return null
        }
        val keyProjectionTypeKey = projectionTypeKey.arguments.getOrNull(0) ?: inferProjectionTypeKey(keySignature)
        val valueProjectionTypeKey = projectionTypeKey.arguments.getOrNull(1) ?: inferProjectionTypeKey(valueSignature)
        val keyValuePairSignature = AbiValueSignature.ParameterizedInterface(
            iid = keyValuePairIid,
            arguments = listOf(keySignature, valueSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                keyValuePairIidText,
                keySignature.rawSignature,
                valueSignature.rawSignature,
            ),
        )
        val keyValuePairProjectionTypeKey = ProjectionTypeKey(
            rawType = "kotlin.collections.Map.Entry",
            arguments = listOf(keyProjectionTypeKey, valueProjectionTypeKey),
        )
        val iterableSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    iterableIidText,
                    keyValuePairSignature.rawSignature,
                ),
            ),
            arguments = listOf(keyValuePairSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iterableIidText,
                keyValuePairSignature.rawSignature,
            ),
        )
        val retainedChildren = mutableListOf<AutoCloseable>()
        val entries = map.entries
        val iteratorSignature = AbiValueSignature.ParameterizedInterface(
            iid = iteratorIid,
            arguments = listOf(keyValuePairSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iteratorIidText,
                keyValuePairSignature.rawSignature,
            ),
        )
        val firstMethod: () -> ComPtr = iterableFirstMethod(
            iterable = entries,
            elementProjectionTypeKey = keyValuePairProjectionTypeKey.render(),
            iteratorSignature = iteratorSignature,
            retainedChildren = retainedChildren,
        )
        val primitiveKind = primitiveAbiKind(valueSignature)
        val lookupValue: (String) -> Any? = { key ->
            if (!map.containsKey(key)) {
                throw IndexOutOfBoundsException("Map does not contain key '$key'")
            }
            map[key]
        }
        val splitMethod = mapViewSplitMethod(
            map = map,
            projectionTypeKey = projectionTypeKey,
            signature = signature,
            retainedChildren = retainedChildren,
        )
        val containsKeyMethod: (String) -> Boolean = { key -> map.containsKey(key) }
        val baseInterfaceSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = signature.iid,
            noArgUInt32Methods = mapOf(7 to { map.size.toUInt() }),
            stringArgBooleanMethods = mapOf(8 to containsKeyMethod),
        )
        val interfaceSpec = when {
            valueSignature is AbiValueSignature.StringType -> baseInterfaceSpec.copy(
                stringArgHStringMethods = mapOf(
                    6 to {
                        val result = lookupValue(it)
                        result as? String
                            ?: error("Expected map value for '$it' to be String, got ${result?.let { value -> value::class.qualifiedName }}")
                    },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).mapViewInterfaceSpec(baseInterfaceSpec, lookupValue)
            else -> baseInterfaceSpec.copy(
                stringArgObjectMethods = mapOf(
                    6 to { key ->
                        marshalObjectResultPointer(
                            value = lookupValue(key),
                            projectionTypeKey = valueProjectionTypeKey,
                            signature = valueSignature,
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
            )
        }.copy(noArgTwoObjectMethods = mapOf(9 to splitMethod))
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = iterableSignature.iid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createMapHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        @Suppress("UNCHECKED_CAST")
        val map = value as? MutableMap<String, Any?> ?: return null
        val keySignature = signature.arguments.getOrNull(0) ?: return null
        val valueSignature = signature.arguments.getOrNull(1) ?: return null
        if (keySignature !is AbiValueSignature.StringType) {
            return null
        }
        val keyProjectionTypeKey = projectionTypeKey.arguments.getOrNull(0) ?: inferProjectionTypeKey(keySignature)
        val valueProjectionTypeKey = projectionTypeKey.arguments.getOrNull(1) ?: inferProjectionTypeKey(valueSignature)
        val keyValuePairSignature = AbiValueSignature.ParameterizedInterface(
            iid = keyValuePairIid,
            arguments = listOf(keySignature, valueSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                keyValuePairIidText,
                keySignature.rawSignature,
                valueSignature.rawSignature,
            ),
        )
        val keyValuePairProjectionTypeKey = ProjectionTypeKey(
            rawType = "kotlin.collections.Map.Entry",
            arguments = listOf(keyProjectionTypeKey, valueProjectionTypeKey),
        )
        val iterableSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    iterableIidText,
                    keyValuePairSignature.rawSignature,
                ),
            ),
            arguments = listOf(keyValuePairSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iterableIidText,
                keyValuePairSignature.rawSignature,
            ),
        )
        val mapViewSignature = AbiValueSignature.ParameterizedInterface(
            iid = ParameterizedInterfaceId.createFromSignature(
                WinRtTypeSignature.parameterizedInterface(
                    mapViewIidText,
                    keySignature.rawSignature,
                    valueSignature.rawSignature,
                ),
            ),
            arguments = listOf(keySignature, valueSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                mapViewIidText,
                keySignature.rawSignature,
                valueSignature.rawSignature,
            ),
        )
        val retainedChildren = mutableListOf<AutoCloseable>()
        val entries = map.entries
        val iteratorSignature = AbiValueSignature.ParameterizedInterface(
            iid = iteratorIid,
            arguments = listOf(keyValuePairSignature),
            rawSignature = WinRtTypeSignature.parameterizedInterface(
                iteratorIidText,
                keyValuePairSignature.rawSignature,
            ),
        )
        val firstMethod: () -> ComPtr = iterableFirstMethod(
            iterable = entries,
            elementProjectionTypeKey = keyValuePairProjectionTypeKey.render(),
            iteratorSignature = iteratorSignature,
            retainedChildren = retainedChildren,
        )
        val primitiveKind = primitiveAbiKind(valueSignature)
        val lookupValue: (String) -> Any? = { key ->
            if (!map.containsKey(key)) {
                throw IndexOutOfBoundsException("Map does not contain key '$key'")
            }
            map[key]
        }
        val getViewMethod: () -> ComPtr = {
            val viewHandle = requireNotNull(
                createMapViewHandle(
                    map,
                    ProjectionTypeKey("kotlin.collections.Map", listOf(keyProjectionTypeKey, valueProjectionTypeKey)),
                    mapViewSignature,
                ),
            )
            retainedChildren += viewHandle
            viewHandle.pointer.withAddRef()
        }
        val removeMethod: (String) -> HResult = { key -> removeStringKeyedMapEntry(map, key) }
        val splitMethod = mapViewSplitMethod(
            map = map,
            projectionTypeKey = ProjectionTypeKey("kotlin.collections.Map", listOf(keyProjectionTypeKey, valueProjectionTypeKey)),
            signature = mapViewSignature,
            retainedChildren = retainedChildren,
        )
        val containsKeyMethod: (String) -> Boolean = { key -> map.containsKey(key) }
        val baseInterfaceSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = signature.iid,
            noArgUnitMethods = mapOf(
                12 to {
                    map.clear()
                    HResult(0)
                },
            ),
            noArgObjectMethods = mapOf(9 to getViewMethod),
            stringArgUnitMethods = mapOf(11 to removeMethod),
            stringArgBooleanMethods = mapOf(8 to containsKeyMethod),
            noArgUInt32Methods = mapOf(7 to { map.size.toUInt() }),
        )
        val interfaceSpec = when {
            valueSignature is AbiValueSignature.StringType -> baseInterfaceSpec.copy(
                stringArgHStringMethods = mapOf(
                    6 to {
                        val result = lookupValue(it)
                        result as? String
                            ?: error("Expected map value for '$it' to be String, got ${result?.let { value -> value::class.qualifiedName }}")
                    },
                ),
                stringStringArgBooleanMethods = mapOf(
                    10 to { key, element ->
                        val replaced = map.containsKey(key)
                        map[key] = element
                        replaced
                    },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).mapInterfaceSpec(
                baseSpec = baseInterfaceSpec,
                map = map,
                lookupValue = lookupValue,
                valueProjectionTypeKey = valueProjectionTypeKey,
            )
            else -> baseInterfaceSpec.copy(
                stringArgObjectMethods = mapOf(
                    6 to { key ->
                        marshalObjectResultPointer(
                            value = lookupValue(key),
                            projectionTypeKey = valueProjectionTypeKey,
                            signature = valueSignature,
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
                stringObjectArgBooleanMethods = mapOf(
                    10 to { key, pointer ->
                        val replaced = map.containsKey(key)
                        map[key] = projectObjectValueFromPointer(pointer, valueProjectionTypeKey, valueSignature)
                        replaced
                    },
                ),
            )
        }
        val baseMapViewSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = mapViewSignature.iid,
            noArgUInt32Methods = mapOf(7 to { map.size.toUInt() }),
            stringArgBooleanMethods = mapOf(8 to containsKeyMethod),
        )
        val mapViewSpec = when {
            valueSignature is AbiValueSignature.StringType -> baseMapViewSpec.copy(
                stringArgHStringMethods = mapOf(
                    6 to {
                        val result = lookupValue(it)
                        result as? String
                            ?: error("Expected map value for '$it' to be String, got ${result?.let { value -> value::class.qualifiedName }}")
                    },
                ),
            )
            primitiveKind != null -> primitiveSlotBinder(primitiveKind).mapViewInterfaceSpec(baseMapViewSpec, lookupValue)
            else -> baseMapViewSpec.copy(
                stringArgObjectMethods = mapOf(
                    6 to { key ->
                        marshalObjectResultPointer(
                            value = lookupValue(key),
                            projectionTypeKey = valueProjectionTypeKey,
                            signature = valueSignature,
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
            )
        }.copy(noArgTwoObjectMethods = mapOf(9 to splitMethod))
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = iterableSignature.iid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec, mapViewSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createKeyValuePairHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
    ): ProjectedObjectHandle? {
        val entry = value as? Map.Entry<*, *> ?: return null
        val keySignature = signature.arguments.getOrNull(0) ?: return null
        val valueSignature = signature.arguments.getOrNull(1) ?: return null
        val keyProjectionTypeKey = projectionTypeKey.arguments.getOrNull(0) ?: inferProjectionTypeKey(keySignature)
        val valueProjectionTypeKey = projectionTypeKey.arguments.getOrNull(1) ?: inferProjectionTypeKey(valueSignature)
        val valuePrimitiveKind = primitiveAbiKind(valueSignature)
        val retainedChildren = mutableListOf<AutoCloseable>()
        val baseInterfaceSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = signature.iid,
            noArgObjectMethods = buildMap {
                if (keySignature !is AbiValueSignature.StringType) {
                    put(
                        6,
                        {
                            marshalObjectResultPointer(
                                value = entry.key,
                                projectionTypeKey = keyProjectionTypeKey,
                                signature = keySignature,
                                retainedChildren = retainedChildren,
                            )
                        },
                    )
                }
                if (valueSignature !is AbiValueSignature.StringType && valuePrimitiveKind == null) {
                    put(
                        7,
                        {
                            marshalObjectResultPointer(
                                value = entry.value,
                                projectionTypeKey = valueProjectionTypeKey,
                                signature = valueSignature,
                                retainedChildren = retainedChildren,
                            )
                        },
                    )
                }
            },
            noArgHStringMethods = buildMap {
                if (keySignature is AbiValueSignature.StringType) {
                    put(
                        6,
                        {
                            entry.key as? String
                                ?: error("Expected key to be String, got ${entry.key?.let { it::class.qualifiedName }}")
                        },
                    )
                }
                if (valueSignature is AbiValueSignature.StringType) {
                    put(
                        7,
                        {
                            entry.value as? String
                                ?: error("Expected value to be String, got ${entry.value?.let { it::class.qualifiedName }}")
                        },
                    )
                }
            },
        )
        val interfaceSpec = when {
            valuePrimitiveKind != null -> primitiveSlotBinder(valuePrimitiveKind).addNoArgValueMethod(
                baseSpec = baseInterfaceSpec,
                slot = 7,
                valueProvider = { entry.value },
            )
            else -> baseInterfaceSpec
        }
        val stub = JvmWinRtObjectStub.create(interfaceSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createObjectVectorViewInterfaceSpec(
        iid: Guid,
        list: List<*>,
        firstMethod: () -> ComPtr,
        elementProjectionTypeKey: String,
        elementSignature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ): JvmWinRtObjectStub.InterfaceSpec {
        return JvmWinRtObjectStub.InterfaceSpec(
            iid = iid,
            noArgObjectMethods = mapOf(6 to firstMethod),
            uint32ArgObjectMethods = mapOf(
                7 to { index ->
                    marshalObjectResultPointer(
                        value = list.elementAt(index.toInt()),
                        projectionTypeKey = elementProjectionTypeKey,
                        signature = elementSignature,
                        retainedChildren = retainedChildren,
                    )
                },
            ),
            noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
            objectArgIndexOfMethods = mapOf(
                9 to { pointer -> indexOfOrNull(inspectableIndexOf(list, pointer)) },
            ),
        )
    }

    private fun createObjectVectorInterfaceSpec(
        iid: Guid,
        list: MutableList<Any?>,
        firstMethod: () -> ComPtr,
        getViewMethod: () -> ComPtr,
        elementProjectionTypeKey: String,
        elementSignature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ): JvmWinRtObjectStub.InterfaceSpec {
        fun projectElement(pointer: ComPtr): Any? =
            projectObjectValueFromPointer(pointer, elementProjectionTypeKey, elementSignature)
        return JvmWinRtObjectStub.InterfaceSpec(
            iid = iid,
            noArgUnitMethods = mapOf(
                15 to {
                    if (list.isEmpty()) {
                        KnownHResults.E_BOUNDS
                    } else {
                        list.removeAt(list.lastIndex)
                        HResult(0)
                    }
                },
                16 to {
                    list.clear()
                    HResult(0)
                },
            ),
            noArgObjectMethods = mapOf(
                6 to firstMethod,
                9 to getViewMethod,
            ),
            noArgUInt32Methods = mapOf(8 to { list.size.toUInt() }),
            uint32ArgUnitMethods = mapOf(
                13 to { index ->
                    list.removeAt(index.toInt())
                    HResult(0)
                },
            ),
            uint32ArgObjectMethods = mapOf(
                7 to { index ->
                    marshalObjectResultPointer(
                        value = list.elementAt(index.toInt()),
                        projectionTypeKey = elementProjectionTypeKey,
                        signature = elementSignature,
                        retainedChildren = retainedChildren,
                    )
                },
            ),
            uint32ObjectArgUnitMethods = mapOf(
                11 to { index, pointer ->
                    list[index.toInt()] = projectElement(pointer)
                    HResult(0)
                },
                12 to { index, pointer ->
                    list.add(index.toInt(), projectElement(pointer))
                    HResult(0)
                },
            ),
            objectArgIndexOfMethods = mapOf(
                10 to { pointer -> indexOfOrNull(inspectableIndexOf(list, pointer)) },
            ),
            objectArgUnitMethods = mapOf(
                14 to { pointer ->
                    list.add(projectElement(pointer))
                    HResult(0)
                },
            ),
        )
    }

    private fun createBindableIterableHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
    ): ProjectedObjectHandle? {
        val iterable = value as? Iterable<*> ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull() ?: "Object"
        val retainedChildren = mutableListOf<AutoCloseable>()
        val stub = JvmWinRtObjectStub.create(
            JvmWinRtObjectStub.InterfaceSpec(
                iid = bindableIterableIid,
                noArgObjectMethods = mapOf(
                    6 to {
                        val iteratorHandle = requireNotNull(
                            createBindableIteratorHandle(
                                iterable.iterator(),
                                ProjectionTypeKey("kotlin.collections.Iterator", listOf(elementProjectionTypeKey)),
                            ),
                        )
                        retainedChildren += iteratorHandle
                        iteratorHandle.pointer.withAddRef()
                    },
                ),
            ),
        )
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createBindableIteratorHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
    ): ProjectedObjectHandle? {
        val iterator = value as? Iterator<*> ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull() ?: "Object"
        val retainedChildren = mutableListOf<AutoCloseable>()
        val state = IteratorState(iterator)
        val stub = JvmWinRtObjectStub.create(
            JvmWinRtObjectStub.InterfaceSpec(
                iid = bindableIteratorIid,
                noArgObjectMethods = mapOf(
                    6 to {
                        marshalObjectResultPointer(
                            value = state.requireCurrentValue("IBindableIterator"),
                            projectionTypeKey = elementProjectionTypeKey,
                            signature = AbiValueSignature.ObjectType(WinRtTypeSignature.object_()),
                            retainedChildren = retainedChildren,
                        )
                    },
                ),
                noArgBooleanMethods = mapOf(
                    7 to { state.hasCurrent },
                    8 to { state.moveNext() },
                ),
                noArgCallerAllocatedArrayMethods = mapOf(
                    9 to { _, _ ->
                        throw UnsupportedOperationException("IBindableIterator.GetMany is not implemented")
                    },
                ),
            ),
        )
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createBindableVectorViewHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
    ): ProjectedObjectHandle? {
        val list = value as? List<*> ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull() ?: "Object"
        val retainedChildren = mutableListOf<AutoCloseable>()
        val firstMethod: () -> ComPtr = {
            val iteratorHandle = requireNotNull(
                createBindableIteratorHandle(
                    list.iterator(),
                    ProjectionTypeKey("kotlin.collections.Iterator", listOf(elementProjectionTypeKey)),
                ),
            )
            retainedChildren += iteratorHandle
            iteratorHandle.pointer.withAddRef()
        }
        val interfaceSpec = createObjectVectorViewInterfaceSpec(
            iid = bindableVectorViewIid,
            list = list,
            firstMethod = firstMethod,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = AbiValueSignature.ObjectType(WinRtTypeSignature.object_()),
            retainedChildren = retainedChildren,
        )
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = bindableIterableIid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun createBindableVectorHandle(
        value: Any,
        projectionTypeKey: ProjectionTypeKey,
    ): ProjectedObjectHandle? {
        @Suppress("UNCHECKED_CAST")
        val list = value as? MutableList<Any?> ?: return null
        val elementProjectionTypeKey = projectionTypeKey.arguments.singleOrNull() ?: "Object"
        val retainedChildren = mutableListOf<AutoCloseable>()
        val firstMethod: () -> ComPtr = {
            val iteratorHandle = requireNotNull(
                createBindableIteratorHandle(
                    list.iterator(),
                    ProjectionTypeKey("kotlin.collections.Iterator", listOf(elementProjectionTypeKey)),
                ),
            )
            retainedChildren += iteratorHandle
            iteratorHandle.pointer.withAddRef()
        }
        val getViewMethod: () -> ComPtr = {
            val vectorViewHandle = requireNotNull(
                createBindableVectorViewHandle(
                    list,
                    ProjectionTypeKey("kotlin.collections.List", listOf(elementProjectionTypeKey)),
                ),
            )
            retainedChildren += vectorViewHandle
            vectorViewHandle.pointer.withAddRef()
        }
        val interfaceSpec = createObjectVectorInterfaceSpec(
            iid = bindableVectorIid,
            list = list,
            firstMethod = firstMethod,
            getViewMethod = getViewMethod,
            elementProjectionTypeKey = elementProjectionTypeKey,
            elementSignature = AbiValueSignature.ObjectType(WinRtTypeSignature.object_()),
            retainedChildren = retainedChildren,
        )
        val baseIterableSpec = JvmWinRtObjectStub.InterfaceSpec(
            iid = bindableIterableIid,
            noArgObjectMethods = mapOf(6 to firstMethod),
        )
        val stub = JvmWinRtObjectStub.create(interfaceSpec, baseIterableSpec)
        return ProjectedObjectHandle(stub, retainedChildren)
    }

    private fun iteratorGetManyMethod(
        state: IteratorState,
        elementProjectionTypeKey: String,
        elementSignature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ): (Int, MemorySegment) -> UInt {
        return { itemsSize, items ->
            require(itemsSize >= 0) { "Caller buffer size must be non-negative: $itemsSize" }
            val buffer = callerAllocatedArrayBuffer(items, itemsSize, elementSignature)
            var index = 0
            while (index < itemsSize && state.hasCurrent) {
                writeCallerAllocatedArrayElement(
                    buffer = buffer,
                    index = index,
                    value = state.requireCurrentValue("IIterator"),
                    projectionTypeKey = elementProjectionTypeKey,
                    signature = elementSignature,
                    retainedChildren = retainedChildren,
                )
                state.moveNext()
                index += 1
            }
            fillRemainingStringElements(buffer, index, itemsSize, elementSignature)
            index.toUInt()
        }
    }

    private fun vectorGetManyMethod(
        list: List<*>,
        elementProjectionTypeKey: String,
        elementSignature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ): (UInt, Int, MemorySegment) -> UInt {
        return { startIndex, itemsSize, items ->
            require(itemsSize >= 0) { "Caller buffer size must be non-negative: $itemsSize" }
            if (startIndex > Int.MAX_VALUE.toUInt()) {
                throw IndexOutOfBoundsException("Start index $startIndex exceeds Int range")
            }
            val start = startIndex.toInt()
            if (start > list.size) {
                throw IndexOutOfBoundsException("Start index $startIndex is outside vector size ${list.size}")
            }
            val buffer = callerAllocatedArrayBuffer(items, itemsSize, elementSignature)
            val itemCount = minOf(itemsSize, list.size - start)
            repeat(itemCount) { offset ->
                writeCallerAllocatedArrayElement(
                    buffer = buffer,
                    index = offset,
                    value = list[start + offset],
                    projectionTypeKey = elementProjectionTypeKey,
                    signature = elementSignature,
                    retainedChildren = retainedChildren,
                )
            }
            fillRemainingStringElements(buffer, itemCount, itemsSize, elementSignature)
            itemCount.toUInt()
        }
    }

    private fun vectorReplaceAllMethod(
        list: MutableList<Any?>,
        elementProjectionTypeKey: String,
        elementSignature: AbiValueSignature,
    ): (Int, MemorySegment) -> HResult {
        return { itemsSize, items ->
            require(itemsSize >= 0) { "Caller buffer size must be non-negative: $itemsSize" }
            val buffer = callerAllocatedArrayBuffer(items, itemsSize, elementSignature)
            list.clear()
            repeat(itemsSize) { index ->
                list.add(
                    readCallerAllocatedArrayElement(
                        buffer = buffer,
                        index = index,
                        projectionTypeKey = elementProjectionTypeKey,
                        signature = elementSignature,
                    ),
                )
            }
            HResult(0)
        }
    }

    private fun callerAllocatedArrayBuffer(
        items: MemorySegment,
        itemsSize: Int,
        elementSignature: AbiValueSignature,
    ): MemorySegment {
        if (itemsSize == 0) {
            return MemorySegment.NULL
        }
        if (items.address() == 0L) {
            throw NullPointerException("Caller buffer was null for $itemsSize elements")
        }
        return items.reinterpret(callerAllocatedArrayElementSize(elementSignature) * itemsSize.toLong())
    }

    private fun callerAllocatedArrayElementSize(signature: AbiValueSignature): Long {
        return when (primitiveAbiKind(signature)) {
            PrimitiveAbiKind.BOOLEAN -> ValueLayout.JAVA_BYTE.byteSize()
            PrimitiveAbiKind.INT32,
            PrimitiveAbiKind.UINT32,
            -> ValueLayout.JAVA_INT.byteSize()
            PrimitiveAbiKind.INT64,
            PrimitiveAbiKind.UINT64,
            -> ValueLayout.JAVA_LONG.byteSize()
            PrimitiveAbiKind.FLOAT32 -> ValueLayout.JAVA_FLOAT.byteSize()
            PrimitiveAbiKind.FLOAT64 -> ValueLayout.JAVA_DOUBLE.byteSize()
            null -> ValueLayout.ADDRESS.byteSize()
        }.toLong()
    }

    private fun fillRemainingStringElements(
        buffer: MemorySegment,
        startIndex: Int,
        itemsSize: Int,
        signature: AbiValueSignature,
    ) {
        if (signature !is AbiValueSignature.StringType) {
            return
        }
        for (index in startIndex until itemsSize) {
            val hString = JvmWinRtRuntime.createHString("")
            buffer.setAtIndex(ValueLayout.ADDRESS, index.toLong(), MemorySegment.ofAddress(hString.raw))
        }
    }

    private fun writeCallerAllocatedArrayElement(
        buffer: MemorySegment,
        index: Int,
        value: Any?,
        projectionTypeKey: String,
        signature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ) {
        when (signature) {
            is AbiValueSignature.StringType -> {
                val hString = JvmWinRtRuntime.createHString(
                    value as? String
                        ?: error("Expected String array element, got ${value?.let { it::class.qualifiedName }}"),
                )
                buffer.setAtIndex(ValueLayout.ADDRESS, index.toLong(), MemorySegment.ofAddress(hString.raw))
            }
            is AbiValueSignature.ParameterizedInterface -> {
                val pointer = marshalObjectResultPointer(value, projectionTypeKey, signature, retainedChildren)
                buffer.setAtIndex(
                    ValueLayout.ADDRESS,
                    index.toLong(),
                    if (pointer.isNull) MemorySegment.NULL else MemorySegment.ofAddress(pointer.value.rawValue),
                )
            }
            is AbiValueSignature.ObjectType -> primitiveAbiKind(signature)?.let { primitiveKind ->
                when (primitiveKind) {
                    PrimitiveAbiKind.BOOLEAN -> buffer.setAtIndex(
                        ValueLayout.JAVA_BYTE,
                        index.toLong(),
                        if (marshalPrimitiveBooleanValue(value)) 1 else 0,
                    )
                    PrimitiveAbiKind.INT32 -> buffer.setAtIndex(
                        ValueLayout.JAVA_INT,
                        index.toLong(),
                        marshalPrimitiveInt32Value(value),
                    )
                    PrimitiveAbiKind.UINT32 -> buffer.setAtIndex(
                        ValueLayout.JAVA_INT,
                        index.toLong(),
                        marshalPrimitiveUInt32Value(value).toInt(),
                    )
                    PrimitiveAbiKind.INT64 -> buffer.setAtIndex(
                        ValueLayout.JAVA_LONG,
                        index.toLong(),
                        marshalPrimitiveInt64Value(value),
                    )
                    PrimitiveAbiKind.UINT64 -> buffer.setAtIndex(
                        ValueLayout.JAVA_LONG,
                        index.toLong(),
                        marshalPrimitiveUInt64Value(value).toLong(),
                    )
                    PrimitiveAbiKind.FLOAT32 -> buffer.setAtIndex(
                        ValueLayout.JAVA_FLOAT,
                        index.toLong(),
                        marshalPrimitiveFloat32Value(value),
                    )
                    PrimitiveAbiKind.FLOAT64 -> buffer.setAtIndex(
                        ValueLayout.JAVA_DOUBLE,
                        index.toLong(),
                        marshalPrimitiveFloat64Value(value),
                    )
                }
            } ?: run {
                val pointer = marshalObjectResultPointer(value, projectionTypeKey, signature, retainedChildren)
                buffer.setAtIndex(
                    ValueLayout.ADDRESS,
                    index.toLong(),
                    if (pointer.isNull) MemorySegment.NULL else MemorySegment.ofAddress(pointer.value.rawValue),
                )
            }
        }
    }

    private fun readCallerAllocatedArrayElement(
        buffer: MemorySegment,
        index: Int,
        projectionTypeKey: String,
        signature: AbiValueSignature,
    ): Any? {
        return when (signature) {
            is AbiValueSignature.StringType -> {
                val address = buffer.getAtIndex(ValueLayout.ADDRESS, index.toLong()).address()
                if (address == 0L) {
                    ""
                } else {
                    PlatformHStringBridge.toKotlinString(HString(address))
                }
            }
            is AbiValueSignature.ParameterizedInterface -> projectObjectValueFromPointer(
                ComPtr(AbiIntPtr(buffer.getAtIndex(ValueLayout.ADDRESS, index.toLong()).address())),
                projectionTypeKey,
                signature,
            )
            is AbiValueSignature.ObjectType -> primitiveAbiKind(signature)?.let { primitiveKind ->
                val value = when (primitiveKind) {
                    PrimitiveAbiKind.BOOLEAN -> buffer.getAtIndex(ValueLayout.JAVA_BYTE, index.toLong()).toInt() != 0
                    PrimitiveAbiKind.INT32 -> buffer.getAtIndex(ValueLayout.JAVA_INT, index.toLong())
                    PrimitiveAbiKind.UINT32 -> buffer.getAtIndex(ValueLayout.JAVA_INT, index.toLong()).toUInt()
                    PrimitiveAbiKind.INT64 -> buffer.getAtIndex(ValueLayout.JAVA_LONG, index.toLong())
                    PrimitiveAbiKind.UINT64 -> buffer.getAtIndex(ValueLayout.JAVA_LONG, index.toLong()).toULong()
                    PrimitiveAbiKind.FLOAT32 -> buffer.getAtIndex(ValueLayout.JAVA_FLOAT, index.toLong())
                    PrimitiveAbiKind.FLOAT64 -> buffer.getAtIndex(ValueLayout.JAVA_DOUBLE, index.toLong())
                }
                projectPrimitiveValueFromAbi(primitiveKind, projectionTypeKey, value)
            } ?: projectObjectValueFromPointer(
                ComPtr(AbiIntPtr(buffer.getAtIndex(ValueLayout.ADDRESS, index.toLong()).address())),
                projectionTypeKey,
                signature,
            )
        }
    }

    private fun marshalObjectResultPointer(
        value: Any?,
        projectionTypeKey: String,
        signature: AbiValueSignature,
        retainedChildren: MutableList<AutoCloseable>,
    ): ComPtr {
        val pointer = when (signature) {
            is AbiValueSignature.ParameterizedInterface -> {
                if (value == null) {
                    ComPtr.NULL
                } else if (value is Inspectable) {
                    value.getObjectReferenceForProjectedType(
                        projectionTypeKey,
                        ParameterizedInterfaceId.createFromSignature(signature.rawSignature),
                    )
                } else {
                    val child = requireNotNull(
                        createProjectedHandle(
                            value,
                            parseProjectionTypeKey(projectionTypeKey),
                            signature,
                        ),
                    ) {
                        "Unsupported plain Kotlin value ${value::class.qualifiedName} for projected signature ${signature.rawSignature}"
                    }
                    retainedChildren += child
                    child.pointer
                }
            }
            is AbiValueSignature.ObjectType -> primitiveAbiKind(signature)?.let {
                error("Primitive values for $projectionTypeKey must use the primitive ABI path")
            } ?: when (value) {
                null -> ComPtr.NULL
                is Inspectable -> value.getInspectableArgumentPointer()
                else -> error(
                    "Projected object values for $projectionTypeKey require an Inspectable value; " +
                        "got ${value::class.qualifiedName}",
                )
            }
            is AbiValueSignature.StringType -> error("String values must use the HSTRING result path")
        }
        return pointer.withAddRef()
    }

    private fun inferProjectionTypeKey(signature: AbiValueSignature): String {
        return when (signature) {
            is AbiValueSignature.StringType -> "String"
            is AbiValueSignature.ObjectType -> primitiveAbiKind(signature)?.wrapperQualifiedType ?: if (signature.rawSignature.startsWith("struct(")) {
                signature.rawSignature.removePrefix("struct(").substringBefore(';')
            } else if (signature.rawSignature.startsWith("rc(")) {
                signature.rawSignature.removePrefix("rc(").substringBefore(';')
            } else if (signature.rawSignature.startsWith("enum(")) {
                signature.rawSignature.removePrefix("enum(").substringBefore(';')
            } else {
                "Object"
            }
            is AbiValueSignature.ParameterizedInterface -> when (signature.iid.canonical) {
                iterableIid.canonical,
                bindableIterableIid.canonical,
                -> "kotlin.collections.Iterable<${inferProjectionTypeKey(signature.arguments.single())}>"
                iteratorIid.canonical,
                bindableIteratorIid.canonical,
                -> "kotlin.collections.Iterator<${inferProjectionTypeKey(signature.arguments.single())}>"
                vectorIid.canonical -> "kotlin.collections.MutableList<${inferProjectionTypeKey(signature.arguments.single())}>"
                vectorViewIid.canonical -> "kotlin.collections.List<${inferProjectionTypeKey(signature.arguments.single())}>"
                mapIid.canonical -> "kotlin.collections.MutableMap<${signature.arguments.joinToString(", ") { inferProjectionTypeKey(it) }}>"
                mapViewIid.canonical -> "kotlin.collections.Map<${signature.arguments.joinToString(", ") { inferProjectionTypeKey(it) }}>"
                keyValuePairIid.canonical ->
                    "kotlin.collections.Map.Entry<${signature.arguments.joinToString(", ") { inferProjectionTypeKey(it) }}>"
                iReferenceIid.canonical -> "dev.winrt.core.IReference<${inferProjectionTypeKey(signature.arguments.single())}>"
                else -> "Object"
            }
        }
    }

    private fun primitiveAbiKind(signature: AbiValueSignature): PrimitiveAbiKind? {
        return when (signature) {
            is AbiValueSignature.ObjectType -> PrimitiveAbiKind.fromRawSignature(signature.rawSignature)
            else -> null
        }
    }

    private fun currentIteratorValue(state: IteratorState): Any? {
        return state.requireCurrentValue("IIterator")
    }

    private fun splitStringKeyedMapView(map: Map<String, Any?>): Pair<Map<String, Any?>?, Map<String, Any?>?> {
        if (map.size < 2) {
            return null to null
        }
        val sortedEntries = map.entries.sortedBy(Map.Entry<String, Any?>::key)
        val splitIndex = (sortedEntries.size + 1) / 2
        val first = sortedEntries
            .subList(0, splitIndex)
            .associateTo(linkedMapOf<String, Any?>()) { it.key to it.value }
        val second = sortedEntries
            .subList(splitIndex, sortedEntries.size)
            .associateTo(linkedMapOf<String, Any?>()) { it.key to it.value }
        return first to second
    }

    private fun mapViewSplitMethod(
        map: Map<String, Any?>,
        projectionTypeKey: ProjectionTypeKey,
        signature: AbiValueSignature.ParameterizedInterface,
        retainedChildren: MutableList<AutoCloseable>,
    ): () -> Pair<ComPtr, ComPtr> {
        return {
            val (firstPartition, secondPartition) = splitStringKeyedMapView(map)
            fun createPartitionPointer(partition: Map<String, Any?>?): ComPtr {
                if (partition == null) {
                    return ComPtr.NULL
                }
                val handle = requireNotNull(createMapViewHandle(partition, projectionTypeKey, signature))
                retainedChildren += handle
                return handle.pointer.withAddRef()
            }
            createPartitionPointer(firstPartition) to createPartitionPointer(secondPartition)
        }
    }

    private fun removeStringKeyedMapEntry(map: MutableMap<String, Any?>, key: String): HResult {
        if (!map.containsKey(key)) {
            return KnownHResults.E_BOUNDS
        }
        map.remove(key)
        return HResult(0)
    }

    private fun indexOfOrNull(index: Int): UInt? = if (index >= 0) index.toUInt() else null

    private fun primitiveIndexOf(list: List<*>, primitiveKind: PrimitiveAbiKind, value: Any): Int {
        return list.indexOfFirst { element ->
            when (primitiveKind) {
                PrimitiveAbiKind.BOOLEAN -> marshalPrimitiveBooleanValue(element) == value as Boolean
                PrimitiveAbiKind.INT32 -> marshalPrimitiveInt32Value(element) == value as Int
                PrimitiveAbiKind.UINT32 -> marshalPrimitiveUInt32Value(element) == value as UInt
                PrimitiveAbiKind.INT64 -> marshalPrimitiveInt64Value(element) == value as Long
                PrimitiveAbiKind.UINT64 -> marshalPrimitiveUInt64Value(element) == value as ULong
                PrimitiveAbiKind.FLOAT32 -> marshalPrimitiveFloat32Value(element) == value as Float
                PrimitiveAbiKind.FLOAT64 -> marshalPrimitiveFloat64Value(element) == value as Double
            }
        }
    }

    private fun projectPrimitiveValueFromAbi(
        primitiveKind: PrimitiveAbiKind,
        projectionTypeKey: String,
        value: Any,
    ): Any {
        val rawType = parseProjectionTypeKey(projectionTypeKey).rawType
        return when (primitiveKind) {
            PrimitiveAbiKind.BOOLEAN -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as Boolean
            } else {
                WinRtBoolean(value as Boolean)
            }
            PrimitiveAbiKind.INT32 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as Int
            } else {
                Int32(value as Int)
            }
            PrimitiveAbiKind.UINT32 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as UInt
            } else {
                UInt32(value as UInt)
            }
            PrimitiveAbiKind.INT64 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as Long
            } else {
                Int64(value as Long)
            }
            PrimitiveAbiKind.UINT64 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as ULong
            } else {
                UInt64(value as ULong)
            }
            PrimitiveAbiKind.FLOAT32 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as Float
            } else {
                Float32(value as Float)
            }
            PrimitiveAbiKind.FLOAT64 -> if (matchesKotlinPrimitiveType(rawType, primitiveKind)) {
                value as Double
            } else {
                Float64(value as Double)
            }
        }
    }

    private fun marshalPrimitiveBooleanValue(value: Any?): Boolean {
        return when (value) {
            is WinRtBoolean -> value.value
            is Boolean -> value
            else -> error("Expected WinRT Boolean value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveInt32Value(value: Any?): Int {
        return when (value) {
            is Int32 -> value.value
            is Int -> value
            else -> error("Expected WinRT Int32 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveUInt32Value(value: Any?): UInt {
        return when (value) {
            is UInt32 -> value.value
            is UInt -> value
            else -> error("Expected WinRT UInt32 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveInt64Value(value: Any?): Long {
        return when (value) {
            is Int64 -> value.value
            is Long -> value
            else -> error("Expected WinRT Int64 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveUInt64Value(value: Any?): ULong {
        return when (value) {
            is UInt64 -> value.value
            is ULong -> value
            else -> error("Expected WinRT UInt64 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveFloat32Value(value: Any?): Float {
        return when (value) {
            is Float32 -> value.value
            is Float -> value
            else -> error("Expected WinRT Float32 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalPrimitiveFloat64Value(value: Any?): Double {
        return when (value) {
            is Float64 -> value.value
            is Double -> value
            else -> error("Expected WinRT Float64 value, got ${value?.let { it::class.qualifiedName }}")
        }
    }

    private fun marshalStructAbiValue(value: Any): ComStructValue {
        val toAbi = value::class.java.methods.firstOrNull { method ->
            method.name == "toAbi" && method.parameterCount == 0
        } ?: error("Projected struct values for ${value::class.qualifiedName} require a public toAbi() method")
        return toAbi.invoke(value) as? ComStructValue
            ?: error("Projected struct ${value::class.qualifiedName}.toAbi() must return ComStructValue")
    }

    private fun enumUnderlyingSignature(signature: String): String {
        return signature.removePrefix("enum(")
            .removeSuffix(")")
            .substringAfter(';', "i4")
    }

    private fun marshalEnumInt32Value(value: Any): Int = when (val rawValue = readProjectedEnumValue(value)) {
        is Int -> rawValue
        is UInt -> rawValue.toInt()
        is Short -> rawValue.toInt()
        is UShort -> rawValue.toInt()
        is Byte -> rawValue.toInt()
        is UByte -> rawValue.toInt()
        is Char -> rawValue.code
        is Long -> rawValue.toInt()
        is ULong -> rawValue.toLong().toInt()
        else -> (rawValue as? Number)?.toInt()
            ?: error("Unsupported projected enum backing value ${rawValue::class.qualifiedName}")
    }

    private fun marshalEnumUInt32Value(value: Any): UInt = when (val rawValue = readProjectedEnumValue(value)) {
        is UInt -> rawValue
        is Int -> rawValue.toUInt()
        is Short -> rawValue.toInt().toUInt()
        is UShort -> rawValue.toUInt()
        is Byte -> rawValue.toInt().toUInt()
        is UByte -> rawValue.toUInt()
        is Char -> rawValue.code.toUInt()
        is Long -> rawValue.toUInt()
        is ULong -> rawValue.toUInt()
        else -> (rawValue as? Number)?.toLong()?.toUInt()
            ?: error("Unsupported projected enum backing value ${rawValue::class.qualifiedName}")
    }

    private fun marshalEnumInt64Value(value: Any): Long = when (val rawValue = readProjectedEnumValue(value)) {
        is Long -> rawValue
        is ULong -> rawValue.toLong()
        is Int -> rawValue.toLong()
        is UInt -> rawValue.toLong()
        is Short -> rawValue.toLong()
        is UShort -> rawValue.toLong()
        is Byte -> rawValue.toLong()
        is UByte -> rawValue.toLong()
        is Char -> rawValue.code.toLong()
        else -> (rawValue as? Number)?.toLong()
            ?: error("Unsupported projected enum backing value ${rawValue::class.qualifiedName}")
    }

    private fun marshalEnumUInt64Value(value: Any): ULong = when (val rawValue = readProjectedEnumValue(value)) {
        is ULong -> rawValue
        is Long -> rawValue.toULong()
        is UInt -> rawValue.toULong()
        is Int -> rawValue.toUInt().toULong()
        is UShort -> rawValue.toULong()
        is Short -> rawValue.toUInt().toULong()
        is UByte -> rawValue.toULong()
        is Byte -> rawValue.toUInt().toULong()
        is Char -> rawValue.code.toULong()
        else -> (rawValue as? Number)?.toLong()?.toULong()
            ?: error("Unsupported projected enum backing value ${rawValue::class.qualifiedName}")
    }

    private fun readProjectedEnumValue(value: Any): Any {
        val getter = value::class.java.methods.firstOrNull { method ->
            method.parameterCount == 0 && (method.name == "getValue" || method.name.startsWith("getValue-"))
        }
        if (getter != null) {
            return getter.invoke(value)
                ?: error("Projected enum value getter for ${value::class.qualifiedName} returned null")
        }

        val field = value::class.java.fields.firstOrNull { it.name == "value" }
            ?: error("Projected enum values for ${value::class.qualifiedName} require a public val value")
        return field.get(value)
            ?: error("Projected enum value field for ${value::class.qualifiedName} returned null")
    }

    private fun parseParameterizedInterfaceSignature(signature: String): AbiValueSignature.ParameterizedInterface? {
        if (!signature.startsWith("pinterface(") || !signature.endsWith(")")) {
            return null
        }
        val content = signature.removePrefix("pinterface(").removeSuffix(")")
        val parts = splitTopLevel(content, ';')
        if (parts.isEmpty()) {
            return null
        }
        val iid = guidOf(parts.first().removePrefix("{").removeSuffix("}"))
        return AbiValueSignature.ParameterizedInterface(
            iid = iid,
            arguments = parts.drop(1).map(::parseAbiValueSignature),
            rawSignature = signature,
        )
    }

    private fun parseRawInterfaceSignature(signature: String): Guid? {
        if (!signature.startsWith("{") || !signature.endsWith("}")) {
            return null
        }
        return guidOf(signature.removePrefix("{").removeSuffix("}"))
    }

    private fun parseAbiValueSignature(signature: String): AbiValueSignature {
        return when {
            signature == "string" -> AbiValueSignature.StringType(signature)
            signature.startsWith("pinterface(") -> parseParameterizedInterfaceSignature(signature)
                ?: AbiValueSignature.ObjectType(signature)
            else -> AbiValueSignature.ObjectType(signature)
        }
    }

    private fun parseProjectionTypeKey(projectionTypeKey: String): ProjectionTypeKey {
        val rawType = projectionTypeKey.substringBefore('<').trim()
        val argumentSource = projectionTypeKey.substringAfter('<', "").substringBeforeLast('>', "")
        if (argumentSource.isBlank()) {
            return ProjectionTypeKey(rawType, emptyList())
        }
        return ProjectionTypeKey(rawType, splitTopLevel(argumentSource, ',').map(String::trim))
    }

    private fun iterableFirstMethod(
        iterable: Iterable<*>,
        elementProjectionTypeKey: String,
        iteratorSignature: AbiValueSignature.ParameterizedInterface,
        retainedChildren: MutableList<AutoCloseable>,
    ): () -> ComPtr {
        return {
            val iteratorHandle = requireNotNull(
                createIteratorHandle(
                    iterable.iterator(),
                    ProjectionTypeKey("kotlin.collections.Iterator", listOf(elementProjectionTypeKey)),
                    iteratorSignature,
                ),
            )
            retainedChildren += iteratorHandle
            iteratorHandle.pointer.withAddRef()
        }
    }

    private fun inspectableIndexOf(list: List<*>, pointer: ComPtr): Int {
        return list.indexOfFirst { element ->
            when (element) {
                null -> pointer.isNull
                is Inspectable -> element.pointer == pointer
                else -> false
            }
        }
    }

    private fun projectObjectValueFromPointer(
        pointer: ComPtr,
        projectionTypeKey: String,
        signature: AbiValueSignature,
    ): Any? {
        if (pointer.isNull) {
            return null
        }
        val inspectable = Inspectable(pointer)
        val rawProjectionType = parseProjectionTypeKey(projectionTypeKey).rawType
        if (rawProjectionType == "Object" || rawProjectionType == Inspectable::class.qualifiedName) {
            return inspectable
        }

        val projectedClass = resolveProjectedClass(rawProjectionType)
            ?: error("Unsupported projected type $projectionTypeKey for ABI pointer re-projection")
        projectedCompanion(projectedClass)?.let { companion ->
            projectedCompanionFactory(companion)?.let { factory ->
                return factory.invoke(companion, inspectable)
            }
        }
        instantiateProjectedClass(
            projectedClass = projectedClass,
            pointer = pointer,
            stringArguments = if (signature is AbiValueSignature.ParameterizedInterface) {
                signature.arguments.map { it.rawSignature }
            } else {
                emptyList()
            },
        )?.let { return it }
        error("Unsupported projected type $projectionTypeKey for ABI pointer re-projection")
    }

    private fun resolveProjectedClass(rawProjectionType: String): Class<*>? {
        return sequenceOf(
            rawProjectionType,
            toJvmQualifiedTypeName(rawProjectionType),
        ).distinct().mapNotNull { candidate ->
            runCatching { Class.forName(candidate) }.getOrNull()
        }.firstOrNull()
    }

    private fun projectedCompanion(projectedClass: Class<*>): Any? {
        return runCatching { projectedClass.getField("Companion").get(null) }.getOrNull()
    }

    private fun projectedCompanionFactory(companion: Any): java.lang.reflect.Method? {
        return companion.javaClass.methods.firstOrNull { method ->
            method.name == "from" &&
                method.parameterTypes.contentEquals(arrayOf(Inspectable::class.java))
        }
    }

    private fun instantiateProjectedClass(
        projectedClass: Class<*>,
        pointer: ComPtr,
        stringArguments: List<String>,
    ): Any? {
        projectedClass.constructors.firstOrNull { constructor ->
            constructor.parameterTypes.contentEquals(arrayOf(ComPtr::class.java))
        }?.let { constructor ->
            return constructor.newInstance(pointer)
        }
        projectedClass.constructors.firstOrNull { constructor ->
            val parameterTypes = constructor.parameterTypes
            parameterTypes.size == stringArguments.size + 1 &&
                parameterTypes.firstOrNull() == Long::class.javaPrimitiveType &&
                parameterTypes.drop(1).all { it == String::class.java }
        }?.let { constructor ->
            return constructor.newInstance(pointer.value.rawValue, *stringArguments.toTypedArray())
        }
        projectedClass.constructors.firstOrNull { constructor ->
            val parameterTypes = constructor.parameterTypes
            parameterTypes.size == stringArguments.size + 2 &&
                parameterTypes.firstOrNull() == Long::class.javaPrimitiveType &&
                parameterTypes.lastOrNull()?.name == "kotlin.jvm.internal.DefaultConstructorMarker" &&
                parameterTypes.drop(1).dropLast(1).all { it == String::class.java }
        }?.let { constructor ->
            return constructor.newInstance(pointer.value.rawValue, *stringArguments.toTypedArray(), null)
        }
        return null
    }

    private fun toJvmQualifiedTypeName(rawProjectionType: String): String {
        if (!rawProjectionType.contains('.')) {
            return rawProjectionType
        }
        val parts = rawProjectionType.split('.')
        return buildString {
            append(parts.dropLast(1).joinToString(".") { it.lowercase() })
            append('.')
            append(parts.last())
        }
    }

    private class IteratorState(
        private val iterator: Iterator<*>,
    ) {
        private var initialStatePending = true
        private var currentValue: Any? = null
        private var hasCurrentValue: Boolean = false

        val hasCurrent: Boolean
            get() {
                ensureInitialized()
                return hasCurrentValue
            }

        fun moveNext(): Boolean {
            if (initialStatePending) {
                initialStatePending = false
                return advance()
            }
            return advance()
        }

        fun requireCurrentValue(iteratorInterfaceName: String): Any? {
            ensureInitialized()
            if (!hasCurrentValue) {
                throw WinRtException(KnownHResults.E_BOUNDS)
            }
            return currentValue
        }

        private fun ensureInitialized() {
            if (!initialStatePending) {
                return
            }
            initialStatePending = false
            advance()
        }

        private fun advance(): Boolean {
            if (iterator.hasNext()) {
                currentValue = iterator.next()
                hasCurrentValue = true
                return true
            }
            currentValue = null
            hasCurrentValue = false
            return false
        }
    }

    private enum class PrimitiveAbiKind(
        val rawSignature: String,
        val wrapperQualifiedType: String,
        val wrapperSimpleType: String,
        val kotlinQualifiedType: String,
        val kotlinSimpleType: String,
    ) {
        BOOLEAN("b1", "dev.winrt.core.WinRtBoolean", "WinRtBoolean", "kotlin.Boolean", "Boolean"),
        INT32("i4", "dev.winrt.core.Int32", "Int32", "kotlin.Int", "Int"),
        UINT32("u4", "dev.winrt.core.UInt32", "UInt32", "kotlin.UInt", "UInt"),
        INT64("i8", "dev.winrt.core.Int64", "Int64", "kotlin.Long", "Long"),
        UINT64("u8", "dev.winrt.core.UInt64", "UInt64", "kotlin.ULong", "ULong"),
        FLOAT32("f4", "dev.winrt.core.Float32", "Float32", "kotlin.Float", "Float"),
        FLOAT64("f8", "dev.winrt.core.Float64", "Float64", "kotlin.Double", "Double");

        companion object {
            fun fromRawSignature(rawSignature: String): PrimitiveAbiKind? =
                entries.firstOrNull { it.rawSignature == rawSignature }
        }
    }

    private fun matchesKotlinPrimitiveType(rawType: String, primitiveKind: PrimitiveAbiKind): Boolean {
        return rawType == primitiveKind.kotlinSimpleType ||
            rawType == primitiveKind.kotlinQualifiedType
    }

    private data class ProjectionTypeKey(
        val rawType: String,
        val arguments: List<String>,
    ) {
        fun render(): String {
            return if (arguments.isEmpty()) {
                rawType
            } else {
                "$rawType<${arguments.joinToString(", ")}>"
            }
        }
    }

    private sealed class AbiValueSignature(
        open val rawSignature: String,
    ) {
        data class StringType(
            override val rawSignature: String,
        ) : AbiValueSignature(rawSignature)

        data class ObjectType(
            override val rawSignature: String,
        ) : AbiValueSignature(rawSignature)

        data class ParameterizedInterface(
            val iid: Guid,
            val arguments: List<AbiValueSignature>,
            override val rawSignature: String,
        ) : AbiValueSignature(rawSignature)
    }
    private class ProjectedObjectHandle(
        private val stub: JvmWinRtObjectStub,
        retainedChildren: List<AutoCloseable>,
    ) : AutoCloseable {
        private val retainedChildren: MutableList<AutoCloseable> = retainedChildren.toMutableList()

        val pointer: ComPtr
            get() = stub.primaryPointer

        override fun close() {
            retainedChildren.asReversed().forEach(AutoCloseable::close)
            retainedChildren.clear()
            stub.close()
        }
    }

    private fun ComPtr.withAddRef(): ComPtr {
        if (!isNull) {
            PlatformComInterop.addRef(this)
        }
        return this
    }
}

private val Guid.canonical: String
    get() = toString()
