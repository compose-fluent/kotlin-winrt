package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal const val WINRT_PROPERTY_VALUE_RUNTIME_CLASS_NAME = "Windows.Foundation.PropertyValue"

internal object WinRTValueBoxing {
    private sealed interface RuntimeClassProjectionPlan {
        fun project(inspectablePointer: RawAddress): Any?
    }

    private object PropertyValuePlan : RuntimeClassProjectionPlan {
        override fun project(inspectablePointer: RawAddress): Any? =
            WinRTPropertyValueProjection.tryFromBorrowedAbi(inspectablePointer)
    }

    private data class ReferencePlan(
        val interfaceId: Guid,
        val enumMetadata: WinRTEnumBoxingMetadata? = null,
        val delegateProjection: WinRTDelegateBoxingProjection<Any>? = null,
    ) : RuntimeClassProjectionPlan {
        override fun project(inspectablePointer: RawAddress): Any? =
            withQueriedInterface(inspectablePointer, interfaceId) { referencePointer ->
                when {
                    delegateProjection != null -> readDelegateReferenceValue(referencePointer, delegateProjection)
                    enumMetadata != null -> readEnumReferenceValue(referencePointer, enumMetadata)
                    else -> ValueBoxingInterop.readReferenceValue(interfaceId, referencePointer)
                }
            }
    }

    private data class ReferenceArrayPlan(
        val interfaceId: Guid,
    ) : RuntimeClassProjectionPlan {
        override fun project(inspectablePointer: RawAddress): Any? =
            withQueriedInterface(inspectablePointer, interfaceId) { referencePointer ->
                ValueBoxingInterop.readReferenceArrayValue(interfaceId, referencePointer)
            }
    }

    /**
     * The runtime class name is the closed WinRT metadata key for a boxed value.  Cache the
     * resulting ABI plan once so steady-state unboxing does not repeat type-name resolution,
     * enum/delegate classification, or descriptor scans.
     */
    private val runtimeClassProjectionPlans = ConcurrentCacheMap<String, RuntimeClassProjectionPlan>()

    fun boxedRuntimeClassNameForType(type: KClass<*>): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForType(type)

    fun boxedRuntimeClassNameForValue(
        value: Any,
        declaredElementType: KClass<*>? = null,
    ): String? = ValueBoxingMetadata.boxedRuntimeClassNameForValue(value, declaredElementType)

    fun boxedRuntimeClassNameForReferenceArrayInterface(interfaceId: Guid): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForReferenceArrayInterface(interfaceId)

    fun boxedRuntimeClassNameForReferenceArrayElementType(elementType: KClass<*>): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForReferenceArrayElementType(elementType)

    internal fun isBoxedRuntimeClassName(runtimeClassName: String): Boolean =
        runtimeClassName == WINRT_PROPERTY_VALUE_RUNTIME_CLASS_NAME ||
            WinRTReferenceTypeNames.parseReferenceElement(runtimeClassName) != null ||
            WinRTReferenceTypeNames.parseReferenceArrayElement(runtimeClassName) != null

    /**
     * Attempts the exact closed-value projection selected by [runtimeClassName].  A null plan is
     * intentionally not cached: generated metadata may be registered after an early miss.
     */
    internal fun tryProjectInspectableForRuntimeClassName(
        inspectable: IInspectableReference,
        runtimeClassName: String,
    ): Any? {
        val plan = runtimeClassProjectionPlans[runtimeClassName]
            ?: buildRuntimeClassProjectionPlan(runtimeClassName)?.also { candidate ->
                runtimeClassProjectionPlans.putIfAbsent(runtimeClassName, candidate)
            }
        return plan?.project(inspectable.pointer.asRawAddress())
    }

    internal fun clearRuntimeClassProjectionPlans() {
        runtimeClassProjectionPlans.clear()
    }

    fun isPropertyValueCompatible(
        value: Any,
        declaredElementType: KClass<*>? = null,
    ): Boolean = ValueBoxingMetadata.isPropertyValueCompatible(value, declaredElementType)

    fun propertyTypeOf(
        value: Any,
        declaredElementType: KClass<*>? = null,
    ): PropertyType = ValueBoxingMetadata.propertyTypeOf(value, declaredElementType)

    fun isNumericScalar(value: Any): Boolean =
        ValueBoxingMetadata.isNumericScalar(value)

    fun createReferenceInterfaceDefinition(value: Any): WinRTInspectableInterfaceDefinition? =
        ValueBoxingMetadata.referenceInterfaceIdForValue(value)?.let { interfaceId ->
            ValueBoxingInterop.createReferenceInterfaceDefinition(interfaceId, value)
        }

    fun createReferenceArrayInterfaceDefinition(
        value: Any,
        declaredElementType: KClass<*>? = null,
    ): WinRTInspectableInterfaceDefinition? =
        ValueBoxingMetadata.referenceArrayInterfaceIdForValue(value, declaredElementType)?.let { interfaceId ->
            ValueBoxingInterop.createReferenceArrayInterfaceDefinition(interfaceId, value)
        }

    fun createReferenceArrayInterfaceDefinition(
        value: Any,
        interfaceId: Guid,
    ): WinRTInspectableInterfaceDefinition =
        ValueBoxingInterop.createReferenceArrayInterfaceDefinition(interfaceId, value)

    fun propertyTypeForReferenceArrayInterface(interfaceId: Guid): PropertyType? =
        ValueBoxingMetadata.propertyTypeForReferenceArrayInterface(interfaceId)

    fun propertyTypeForReferenceInterface(interfaceId: Guid): PropertyType? =
        ValueBoxingMetadata.propertyTypeForReferenceInterface(interfaceId)

    fun boxedRuntimeClassNameForReferenceInterface(interfaceId: Guid): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForReferenceInterface(interfaceId)

    fun isDirectReferenceValue(
        value: Any,
        interfaceId: Guid,
    ): Boolean =
        ValueBoxingInterop.adapterForReferenceInterface(interfaceId)
            ?.projectedClass == value::class

    fun readReferenceValue(interfaceId: Guid, pointer: RawAddress): Any? =
        ValueBoxingInterop.readReferenceValue(interfaceId, pointer)

    fun readReferenceArrayValue(interfaceId: Guid, pointer: RawAddress): Array<Any?>? =
        ValueBoxingInterop.readReferenceArrayValue(interfaceId, pointer)

    fun writePropertyValue(expectedType: PropertyType, value: Any, destination: RawAddress) {
        ValueBoxingInterop.writePropertyValue(expectedType, value, destination)
    }

    fun writePropertyValueArray(expectedType: PropertyType, value: Any, countOut: RawAddress, dataOut: RawAddress) {
        ValueBoxingInterop.writePropertyValueArray(expectedType, value, countOut, dataOut)
    }

    fun tryProjectInspectableAsType(inspectable: IInspectableReference, projectedType: KClass<*>): Any? {
        WinRTValueBoxingRegistration.findDelegateProjection(projectedType)?.let { projection ->
            return queryInspectableReference(inspectable, projection.descriptor.referenceInterfaceId)?.use { reference ->
                readDelegateReferenceValue(reference.pointer.asRawAddress(), projection)
            }
        }

        ValueBoxingMetadata.enumMetadataForClass(projectedType)?.let { descriptor ->
            return queryInspectableReference(inspectable, descriptor.nullableInterfaceId)?.use { reference ->
                readEnumReferenceValue(reference.pointer.asRawAddress(), descriptor)
            }
        }

        val arrayElementType = WinRTTypeClassifier.primitiveArrayElementType(projectedType)
            ?: TypeNameSupport.registeredReferenceArrayElementType(projectedType)
        if (arrayElementType != null) {
            val elementType = arrayElementType
            val descriptor = ValueBoxingMetadata.descriptorForClass(elementType) ?: return null
            val interfaceId = descriptor.referenceArrayInterfaceId ?: return null
            return queryInspectableReference(inspectable, interfaceId)?.use { reference ->
                ValueBoxingInterop.readReferenceArrayValue(interfaceId, reference.pointer.asRawAddress())
            }
        }

        val descriptor = ValueBoxingMetadata.descriptorForClass(projectedType) ?: return null
        val interfaceId = descriptor.nullableInterfaceId ?: return null
        return queryInspectableReference(inspectable, interfaceId)?.use { reference ->
            ValueBoxingInterop.readReferenceValue(interfaceId, reference.pointer.asRawAddress())
        }
    }

    fun tryProjectInspectableReference(inspectable: IInspectableReference): Any? =
        ValueBoxingMetadata.referenceTypeDescriptors().firstNotNullOfOrNull { descriptor ->
            val interfaceId = descriptor.nullableInterfaceId ?: return@firstNotNullOfOrNull null
            queryInspectableReference(inspectable, interfaceId)?.use { reference ->
                ValueBoxingInterop.readReferenceValue(interfaceId, reference.pointer.asRawAddress())
            }
        }

    fun tryProjectInspectableReferenceArray(inspectable: IInspectableReference): Any? =
        ValueBoxingMetadata.referenceTypeDescriptors().firstNotNullOfOrNull { descriptor ->
            val interfaceId = descriptor.referenceArrayInterfaceId ?: return@firstNotNullOfOrNull null
            queryInspectableReference(inspectable, interfaceId)?.use { reference ->
                ValueBoxingInterop.readReferenceArrayValue(interfaceId, reference.pointer.asRawAddress())
            }
        }

    private fun buildRuntimeClassProjectionPlan(runtimeClassName: String): RuntimeClassProjectionPlan? {
        if (runtimeClassName == WINRT_PROPERTY_VALUE_RUNTIME_CLASS_NAME) {
            return PropertyValuePlan
        }

        WinRTReferenceTypeNames.parseReferenceElement(runtimeClassName)?.let { elementTypeName ->
            WinRTValueBoxingRegistration.findDelegateProjectionByRuntimeClassName(elementTypeName)?.let { projection ->
                return ReferencePlan(
                    interfaceId = projection.descriptor.referenceInterfaceId,
                    delegateProjection = projection,
                )
            }

            val enumType = TypeNameSupport.findKClassByNameCached(elementTypeName)
            enumType?.let { type ->
                ValueBoxingMetadata.enumMetadataForClass(type)?.let { descriptor ->
                    return ReferencePlan(
                        interfaceId = descriptor.nullableInterfaceId,
                        enumMetadata = descriptor,
                    )
                }
            }

            ValueBoxingMetadata.descriptorForProjectedTypeName(elementTypeName)?.nullableInterfaceId?.let { interfaceId ->
                return ReferencePlan(interfaceId = interfaceId)
            }
            return null
        }

        WinRTReferenceTypeNames.parseReferenceArrayElement(runtimeClassName)?.let { elementTypeName ->
            ValueBoxingMetadata.descriptorForProjectedTypeName(elementTypeName)?.referenceArrayInterfaceId?.let { interfaceId ->
                return ReferenceArrayPlan(interfaceId = interfaceId)
            }
            return null
        }

        return null
    }

    private inline fun <T> withQueriedInterface(
        inspectablePointer: RawAddress,
        interfaceId: Guid,
        action: (RawAddress) -> T,
    ): T? {
        val result = WinRTPlatformApi.queryInterfaceRaw(inspectablePointer, interfaceId)
        val referencePointer = result.pointer
        if (result.hResultValue == KnownHResults.E_NOINTERFACE.value || PlatformAbi.isNull(referencePointer)) {
            return null
        }
        WinRTPlatformApi.checkSucceededRaw(result.hResultValue)
        return try {
            action(referencePointer)
        } finally {
            WinRTPlatformApi.releaseRaw(referencePointer)
        }
    }

    private fun queryInspectableReference(
        inspectable: IInspectableReference,
        interfaceId: Guid,
    ): ComObjectReference? = runCatching { inspectable.queryInterface(interfaceId).getOrThrow() }.getOrNull()

    private fun readEnumReferenceValue(
        referencePointer: RawAddress,
        descriptor: WinRTEnumBoxingMetadata,
    ): Any =
        acquireNativeScalarScratchFrame().use { resultOut ->
            val hr = ComVtableInvoker.invokeArgs(referencePointer.asRawComPtr(), 6, resultOut)
            WinRTPlatformApi.checkSucceededRaw(hr)
            descriptor.fromAbiBits(resultOut.readInt32())
        }

    private fun readDelegateReferenceValue(
        referencePointer: RawAddress,
        projection: WinRTDelegateBoxingProjection<Any>,
    ): Any? =
        acquireNativeScalarScratchFrame().use { resultOut ->
            val hr = ComVtableInvoker.invokeArgs(referencePointer.asRawComPtr(), 6, resultOut)
            WinRTPlatformApi.checkSucceededRaw(hr)
            projection.fromAbi(resultOut.readPointer())
        }
}
