package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal object WinRtValueBoxing {
    fun boxedRuntimeClassNameForType(type: KClass<*>): String? =
        ValueBoxingMetadata.boxedRuntimeClassNameForType(type)

    fun isPropertyValueCompatible(value: Any): Boolean =
        ValueBoxingMetadata.isPropertyValueCompatible(value)

    fun propertyTypeOf(value: Any): PropertyType =
        ValueBoxingMetadata.propertyTypeOf(value)

    fun isNumericScalar(value: Any): Boolean =
        ValueBoxingMetadata.isNumericScalar(value)

    fun createReferenceInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        ValueBoxingMetadata.referenceInterfaceIdForValue(value)?.let { interfaceId ->
            ValueBoxingInterop.createReferenceInterfaceDefinition(interfaceId, value)
        }

    fun createReferenceArrayInterfaceDefinition(value: Any): WinRtInspectableInterfaceDefinition? =
        ValueBoxingMetadata.referenceArrayInterfaceIdForValue(value)?.let { interfaceId ->
            ValueBoxingInterop.createReferenceArrayInterfaceDefinition(interfaceId, value)
        }

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
        ValueBoxingMetadata.enumMetadataForClass(projectedType)?.let { descriptor ->
            return queryInspectableReference(inspectable, descriptor.nullableInterfaceId)?.use { reference ->
                readEnumReferenceValue(
                    WinRtReferenceReference(
                        reference.pointer.asRawAddress(),
                        descriptor.nullableInterfaceId,
                        preventReleaseOnDispose = true,
                    ),
                    descriptor,
                )
            }
        }

        if (isArrayKClass(projectedType) || WinRtTypeClassifier.primitiveArrayElementType(projectedType) != null) {
            val elementType = WinRtTypeClassifier.primitiveArrayElementType(projectedType) ?: arrayElementType(projectedType) ?: return null
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

    private fun queryInspectableReference(
        inspectable: IInspectableReference,
        interfaceId: Guid,
    ): ComObjectReference? = runCatching { inspectable.queryInterface(interfaceId).getOrThrow() }.getOrNull()

    private fun readEnumReferenceValue(
        reference: WinRtReferenceReference,
        descriptor: WinRtEnumBoxingMetadata,
    ): Any =
        PlatformAbi.confinedScope().use { scope ->
            val resultOut = PlatformAbi.allocateInt32Slot(scope)
            reference.comPtr.throwIfDisposed()
            val hr = ComVtableInvoker.invokeArgs(reference.comPtr.raw, 6, resultOut)
            WinRtPlatformApi.checkSucceededRaw(hr)
            descriptor.fromAbiBits(PlatformAbi.readInt32(resultOut))
        }
}

private fun isArrayKClass(type: KClass<*>): Boolean = arrayElementType(type) != null
