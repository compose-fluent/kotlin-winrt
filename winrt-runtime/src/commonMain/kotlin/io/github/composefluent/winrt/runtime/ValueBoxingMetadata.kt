@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Instant

private const val IREFERENCE_GENERIC_INTERFACE = "61C17706-2D65-11E0-9AE8-D48564015472"
private val enumSignaturePattern = Regex("^enum\\((.+);(i4|u4)\\)$")

internal data class WinRTValueTypeMetadata(
    val projectedClass: KClass<*>,
    val nullableInterfaceId: Guid?,
    val referenceArrayInterfaceId: Guid?,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
    val isNumericScalar: Boolean = false,
)

private data class ManagedArrayMetadata(
    val elements: Array<*>,
    val metadata: WinRTValueTypeMetadata,
)

internal data class WinRTEnumBoxingMetadata(
    val projectedTypeName: String,
    val propertyType: PropertyType,
    val nullableInterfaceId: Guid,
    val toAbiBits: (Any) -> Int,
    val fromAbiBits: (Int) -> Any,
)

internal object ValueBoxingMetadata {
    private val objectMetadata =
        WinRTValueTypeMetadata(
            projectedClass = Any::class,
            nullableInterfaceId = IID.NullableObject,
            referenceArrayInterfaceId = IID.IReferenceArrayOfObject,
            propertyType = null,
            propertyTypeArray = PropertyType.InspectableArray,
        )

    private val exceptionMetadata =
        WinRTValueTypeMetadata(
            projectedClass = Exception::class,
            nullableInterfaceId = IID.NullableException,
            referenceArrayInterfaceId = IID.IReferenceArrayOfException,
            propertyType = null,
            propertyTypeArray = null,
        )

    private val dynamicDescriptorsByClass = ConcurrentCacheMap<KClass<*>, WinRTValueTypeMetadata>()

    private val builtInDescriptors =
        listOf(
            WinRTValueTypeMetadata(Byte::class, IID.NullableSByte, IID.IReferenceArrayOfSByte, null, null),
            WinRTValueTypeMetadata(UByte::class, IID.NullableByte, IID.IReferenceArrayOfByte, PropertyType.UInt8, PropertyType.UInt8Array, isNumericScalar = true),
            WinRTValueTypeMetadata(Short::class, IID.NullableShort, IID.IReferenceArrayOfInt16, PropertyType.Int16, PropertyType.Int16Array, isNumericScalar = true),
            WinRTValueTypeMetadata(UShort::class, IID.NullableUShort, IID.IReferenceArrayOfUInt16, PropertyType.UInt16, PropertyType.UInt16Array, isNumericScalar = true),
            WinRTValueTypeMetadata(Int::class, IID.NullableInt, IID.IReferenceArrayOfInt32, PropertyType.Int32, PropertyType.Int32Array, isNumericScalar = true),
            WinRTValueTypeMetadata(UInt::class, IID.NullableUInt, IID.IReferenceArrayOfUInt32, PropertyType.UInt32, PropertyType.UInt32Array, isNumericScalar = true),
            WinRTValueTypeMetadata(Long::class, IID.NullableLong, IID.IReferenceArrayOfInt64, PropertyType.Int64, PropertyType.Int64Array, isNumericScalar = true),
            WinRTValueTypeMetadata(ULong::class, IID.NullableULong, IID.IReferenceArrayOfUInt64, PropertyType.UInt64, PropertyType.UInt64Array, isNumericScalar = true),
            WinRTValueTypeMetadata(Float::class, IID.NullableFloat, IID.IReferenceArrayOfSingle, PropertyType.Single, PropertyType.SingleArray, isNumericScalar = true),
            WinRTValueTypeMetadata(Double::class, IID.NullableDouble, IID.IReferenceArrayOfDouble, PropertyType.Double, PropertyType.DoubleArray, isNumericScalar = true),
            WinRTValueTypeMetadata(Char::class, IID.NullableChar, IID.IReferenceArrayOfChar, PropertyType.Char16, PropertyType.Char16Array),
            WinRTValueTypeMetadata(Boolean::class, IID.NullableBool, IID.IReferenceArrayOfBoolean, PropertyType.Boolean, PropertyType.BooleanArray),
            WinRTValueTypeMetadata(String::class, IID.NullableString, IID.IReferenceArrayOfString, PropertyType.String, PropertyType.StringArray),
            WinRTValueTypeMetadata(Guid::class, IID.NullableGuid, IID.IReferenceArrayOfGuid, PropertyType.Guid, PropertyType.GuidArray),
            WinRTValueTypeMetadata(Instant::class, IID.NullableDateTimeOffset, IID.IReferenceArrayOfDateTimeOffset, PropertyType.DateTime, PropertyType.DateTimeArray),
            WinRTValueTypeMetadata(Duration::class, IID.NullableTimeSpan, IID.IReferenceArrayOfTimeSpan, PropertyType.TimeSpan, PropertyType.TimeSpanArray),
            WinRTValueTypeMetadata(KClass::class, IID.NullableType, IID.IReferenceArrayOfType, null, null),
            exceptionMetadata,
            objectMetadata,
        )

    private val builtInDescriptorsByClass = builtInDescriptors.associateBy(WinRTValueTypeMetadata::projectedClass)

    fun registerDescriptor(descriptor: WinRTValueTypeMetadata) {
        dynamicDescriptorsByClass[descriptor.projectedClass] = descriptor
    }

    fun boxedRuntimeClassNameForType(type: KClass<*>): String? {
        enumMetadataForClass(type)?.let { descriptor ->
            return WinRTReferenceTypeNames.boxedReference(descriptor.projectedTypeName)
        }
        WinRTTypeClassifier.primitiveArrayElementType(type)?.let { elementType ->
            val descriptor = descriptorForClass(elementType) ?: return null
            val interfaceId = descriptor.referenceArrayInterfaceId ?: return null
            return boxedReferenceArrayRuntimeClassName(interfaceId, descriptor)
        }
        if (isArrayKClass(type)) {
            val descriptor = arrayElementType(type)?.let(::descriptorForClass) ?: return null
            val interfaceId = descriptor.referenceArrayInterfaceId ?: return null
            return boxedReferenceArrayRuntimeClassName(interfaceId, descriptor)
        }
        val descriptor = descriptorForClass(type) ?: return null
        val interfaceId = descriptor.nullableInterfaceId ?: return null
        return boxedReferenceRuntimeClassName(interfaceId, descriptor)
    }

    fun boxedRuntimeClassNameForValue(value: Any): String? =
        normalizeManagedArray(value)?.metadata?.let { descriptor ->
            descriptor.referenceArrayInterfaceId?.let { interfaceId ->
                boxedReferenceArrayRuntimeClassName(interfaceId, descriptor)
            }
        } ?: boxedRuntimeClassNameForType(value::class)

    fun boxedRuntimeClassNameForReferenceArrayInterface(interfaceId: Guid): String? =
        descriptorForReferenceArrayInterface(interfaceId)?.let { descriptor ->
            boxedReferenceArrayRuntimeClassName(interfaceId, descriptor)
        }

    fun isPropertyValueCompatible(value: Any): Boolean =
        propertyTypeOf(value).let { it != PropertyType.OtherType && it != PropertyType.OtherTypeArray }

    fun propertyTypeOf(value: Any): PropertyType {
        val managedArray = normalizeManagedArray(value)
        if (managedArray != null) {
            return managedArray.metadata.propertyTypeArray
                ?: if (managedArray.metadata == objectMetadata) PropertyType.InspectableArray else PropertyType.OtherTypeArray
        }
        if (isSupportedArrayValue(value)) {
            return PropertyType.OtherTypeArray
        }
        enumMetadataForClass(value::class)?.let { return it.propertyType }
        return classifyPropertyValue(value)?.propertyType ?: PropertyType.OtherType
    }

    fun isNumericScalar(value: Any): Boolean =
        normalizeManagedArray(value) == null &&
            (classifyPropertyValue(value)?.isNumericScalar == true || enumMetadataForClass(value::class) != null)

    fun referenceInterfaceIdForValue(value: Any): Guid? =
        descriptorForValue(value)?.nullableInterfaceId ?: enumMetadataForClass(value::class)?.nullableInterfaceId

    fun referenceArrayInterfaceIdForValue(value: Any): Guid? =
        normalizeManagedArray(value)?.metadata?.referenceArrayInterfaceId

    fun normalizedManagedArrayElements(value: Any): Array<*>? =
        normalizeManagedArray(value)?.elements

    fun descriptorForPropertyType(propertyType: PropertyType): WinRTValueTypeMetadata? =
        dynamicDescriptorsByClass.values.firstOrNull { it.propertyType == propertyType }
            ?: builtInDescriptors.firstOrNull { it.propertyType == propertyType }

    fun descriptorForPropertyTypeArray(propertyType: PropertyType): WinRTValueTypeMetadata? =
        dynamicDescriptorsByClass.values.firstOrNull { it.propertyTypeArray == propertyType }
            ?: builtInDescriptors.firstOrNull { it.propertyTypeArray == propertyType }

    fun propertyTypeForReferenceArrayInterface(interfaceId: Guid): PropertyType? =
        descriptorForReferenceArrayInterface(interfaceId)?.propertyTypeArray

    fun inspectableArrayMetadata(): WinRTValueTypeMetadata = objectMetadata

    fun descriptorForClass(type: KClass<*>): WinRTValueTypeMetadata? =
        dynamicDescriptorsByClass[type]
            ?: builtInDescriptorsByClass[type]
            ?: if (isAssignableFrom(Exception::class, type)) exceptionMetadata else null

    fun referenceTypeDescriptors(): List<WinRTValueTypeMetadata> =
        builtInDescriptors + dynamicDescriptorsByClass.values

    fun clearDynamicDescriptorsForTests() {
        dynamicDescriptorsByClass.clear()
    }

    fun enumMetadataForClass(type: KClass<*>): WinRTEnumBoxingMetadata? {
        if (!isEnumType(type)) {
            return null
        }
        val registeredType = type.registeredWinRTType() ?: return null
        val signature = runCatching { GuidGenerator.getSignature(type) }.getOrNull() ?: return null
        val match = enumSignaturePattern.matchEntire(signature) ?: return null
        val projectedTypeName = match.groupValues[1]
        val underlyingSignature = match.groupValues[2]
        val enumAbiValue = registeredType.enumAbiValue as? (Any) -> Int ?: return null
        val constants = enumConstants(type) ?: return null

        fun readBits(enumValue: Any): Int = enumAbiValue(enumValue)

        return when (underlyingSignature) {
            "i4" ->
                WinRTEnumBoxingMetadata(
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.Int32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRTInvalidCastException(
                                "Unknown enum value $abiValue for ${type.typeDisplayName()}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            "u4" ->
                WinRTEnumBoxingMetadata(
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.UInt32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRTInvalidCastException(
                                "Unknown enum value ${abiValue.toUInt()} for ${type.typeDisplayName()}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            else -> null
        }
    }

    private fun descriptorForValue(value: Any): WinRTValueTypeMetadata? =
        if (value is Exception) {
            exceptionMetadata
        } else {
            descriptorForClass(value::class)
        }

    private fun classifyPropertyValue(value: Any): WinRTValueTypeMetadata? =
        descriptorForValue(value)?.takeIf { it.propertyType != null }

    private fun normalizeManagedArray(value: Any): ManagedArrayMetadata? =
        when (value) {
            is Array<*> -> ManagedArrayMetadata(value, classifyReferenceArray(value))
            else -> normalizePrimitiveManagedArray(value)
        }

    private fun classifyReferenceArray(value: Array<*>): WinRTValueTypeMetadata {
        WinRTTypeClassifier.referenceArrayComponentType(value)?.let { componentType ->
            return descriptorForClass(componentType) ?: objectMetadata
        }

        // Native has no declared Array<T> metadata, so inspect every non-null value. This keeps
        // homogeneous arrays typed, while empty, heterogeneous, and unknown arrays become object
        // arrays. A homogeneous declared Array<Any?> is indistinguishable from Array<T> here.
        var inferredDescriptor: WinRTValueTypeMetadata? = null
        value.forEach { element ->
            if (element == null) {
                return@forEach
            }
            val descriptor = descriptorForValue(element) ?: return objectMetadata
            if (inferredDescriptor != null && inferredDescriptor != descriptor) {
                return objectMetadata
            }
            inferredDescriptor = descriptor
        }
        return inferredDescriptor ?: objectMetadata
    }

    private fun descriptorForReferenceArrayInterface(interfaceId: Guid): WinRTValueTypeMetadata? =
        dynamicDescriptorsByClass.values.firstOrNull { it.referenceArrayInterfaceId == interfaceId }
            ?: builtInDescriptors.firstOrNull { it.referenceArrayInterfaceId == interfaceId }

    private fun isSupportedArrayValue(value: Any): Boolean = isArrayKClass(value::class)

    private fun normalizePrimitiveManagedArray(value: Any): ManagedArrayMetadata? {
        val elementType = WinRTTypeClassifier.primitiveArrayElementType(value::class) ?: return null
        val descriptor = descriptorForClass(elementType) ?: return null
        val boxedElements = WinRTTypeClassifier.boxPrimitiveArray(value) ?: return null
        return ManagedArrayMetadata(boxedElements, descriptor)
    }

    private fun boxedReferenceRuntimeClassName(
        interfaceId: Guid,
        descriptor: WinRTValueTypeMetadata,
    ): String {
        check(descriptor.nullableInterfaceId == interfaceId)
        return WinRTReferenceTypeNames.boxedReference(TypeNameSupport.getNameForType(descriptor.projectedClass))
    }

    private fun boxedReferenceArrayRuntimeClassName(
        interfaceId: Guid,
        descriptor: WinRTValueTypeMetadata,
    ): String {
        check(descriptor.referenceArrayInterfaceId == interfaceId)
        return WinRTReferenceTypeNames.boxedReferenceArray(TypeNameSupport.getNameForType(descriptor.projectedClass))
    }

    private fun isArrayKClass(type: KClass<*>): Boolean = arrayElementType(type) != null
}
