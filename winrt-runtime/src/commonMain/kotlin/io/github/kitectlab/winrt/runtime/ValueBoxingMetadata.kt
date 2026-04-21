@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Instant

private const val IREFERENCE_GENERIC_INTERFACE = "61C17706-2D65-11E0-9AE8-D48564015472"
private val enumSignaturePattern = Regex("^enum\\((.+);(i4|u4)\\)$")

internal data class WinRtValueTypeMetadata(
    val projectedClass: KClass<*>,
    val nullableInterfaceId: Guid?,
    val referenceArrayInterfaceId: Guid?,
    val propertyType: PropertyType?,
    val propertyTypeArray: PropertyType?,
    val isNumericScalar: Boolean = false,
)

private data class ManagedArrayMetadata(
    val elements: Array<*>,
    val metadata: WinRtValueTypeMetadata,
)

internal data class WinRtEnumBoxingMetadata(
    val projectedTypeName: String,
    val propertyType: PropertyType,
    val nullableInterfaceId: Guid,
    val toAbiBits: (Any) -> Int,
    val fromAbiBits: (Int) -> Any,
)

internal object ValueBoxingMetadata {
    private val objectMetadata =
        WinRtValueTypeMetadata(
            projectedClass = Any::class,
            nullableInterfaceId = IID.NullableObject,
            referenceArrayInterfaceId = IID.IReferenceArrayOfObject,
            propertyType = null,
            propertyTypeArray = PropertyType.InspectableArray,
        )

    private val exceptionMetadata =
        WinRtValueTypeMetadata(
            projectedClass = Exception::class,
            nullableInterfaceId = IID.NullableException,
            referenceArrayInterfaceId = IID.IReferenceArrayOfException,
            propertyType = null,
            propertyTypeArray = null,
        )

    private val descriptors =
        listOf(
            WinRtValueTypeMetadata(Byte::class, IID.NullableSByte, IID.IReferenceArrayOfSByte, null, null),
            WinRtValueTypeMetadata(UByte::class, IID.NullableByte, IID.IReferenceArrayOfByte, PropertyType.UInt8, PropertyType.UInt8Array, isNumericScalar = true),
            WinRtValueTypeMetadata(Short::class, IID.NullableShort, IID.IReferenceArrayOfInt16, PropertyType.Int16, PropertyType.Int16Array, isNumericScalar = true),
            WinRtValueTypeMetadata(UShort::class, IID.NullableUShort, IID.IReferenceArrayOfUInt16, PropertyType.UInt16, PropertyType.UInt16Array, isNumericScalar = true),
            WinRtValueTypeMetadata(Int::class, IID.NullableInt, IID.IReferenceArrayOfInt32, PropertyType.Int32, PropertyType.Int32Array, isNumericScalar = true),
            WinRtValueTypeMetadata(UInt::class, IID.NullableUInt, IID.IReferenceArrayOfUInt32, PropertyType.UInt32, PropertyType.UInt32Array, isNumericScalar = true),
            WinRtValueTypeMetadata(Long::class, IID.NullableLong, IID.IReferenceArrayOfInt64, PropertyType.Int64, PropertyType.Int64Array, isNumericScalar = true),
            WinRtValueTypeMetadata(ULong::class, IID.NullableULong, IID.IReferenceArrayOfUInt64, PropertyType.UInt64, PropertyType.UInt64Array, isNumericScalar = true),
            WinRtValueTypeMetadata(Float::class, IID.NullableFloat, IID.IReferenceArrayOfSingle, PropertyType.Single, PropertyType.SingleArray, isNumericScalar = true),
            WinRtValueTypeMetadata(Double::class, IID.NullableDouble, IID.IReferenceArrayOfDouble, PropertyType.Double, PropertyType.DoubleArray, isNumericScalar = true),
            WinRtValueTypeMetadata(Char::class, IID.NullableChar, IID.IReferenceArrayOfChar, PropertyType.Char16, PropertyType.Char16Array),
            WinRtValueTypeMetadata(Boolean::class, IID.NullableBool, IID.IReferenceArrayOfBoolean, PropertyType.Boolean, PropertyType.BooleanArray),
            WinRtValueTypeMetadata(String::class, IID.NullableString, IID.IReferenceArrayOfString, PropertyType.String, PropertyType.StringArray),
            WinRtValueTypeMetadata(Guid::class, IID.NullableGuid, IID.IReferenceArrayOfGuid, PropertyType.Guid, PropertyType.GuidArray),
            WinRtValueTypeMetadata(Instant::class, IID.NullableDateTimeOffset, IID.IReferenceArrayOfDateTimeOffset, PropertyType.DateTime, PropertyType.DateTimeArray),
            WinRtValueTypeMetadata(Duration::class, IID.NullableTimeSpan, IID.IReferenceArrayOfTimeSpan, PropertyType.TimeSpan, PropertyType.TimeSpanArray),
            WinRtValueTypeMetadata(Point::class, IID.IReferenceOfPoint, IID.IReferenceArrayOfPoint, PropertyType.Point, PropertyType.PointArray),
            WinRtValueTypeMetadata(Size::class, IID.IReferenceOfSize, IID.IReferenceArrayOfSize, PropertyType.Size, PropertyType.SizeArray),
            WinRtValueTypeMetadata(Rect::class, IID.IReferenceOfRect, IID.IReferenceArrayOfRect, PropertyType.Rect, PropertyType.RectArray),
            WinRtValueTypeMetadata(Matrix3x2::class, IID.IReferenceMatrix3x2, IID.IReferenceArrayOfMatrix3x2, null, null),
            WinRtValueTypeMetadata(Matrix4x4::class, IID.IReferenceMatrix4x4, IID.IReferenceArrayOfMatrix4x4, null, null),
            WinRtValueTypeMetadata(Plane::class, IID.IReferencePlane, IID.IReferenceArrayOfPlane, null, null),
            WinRtValueTypeMetadata(Quaternion::class, IID.IReferenceQuaternion, IID.IReferenceArrayOfQuaternion, null, null),
            WinRtValueTypeMetadata(Vector2::class, IID.IReferenceVector2, IID.IReferenceArrayOfVector2, null, null),
            WinRtValueTypeMetadata(Vector3::class, IID.IReferenceVector3, IID.IReferenceArrayOfVector3, null, null),
            WinRtValueTypeMetadata(Vector4::class, IID.IReferenceVector4, IID.IReferenceArrayOfVector4, null, null),
            WinRtValueTypeMetadata(KClass::class, IID.NullableType, IID.IReferenceArrayOfType, null, null),
            exceptionMetadata,
            objectMetadata,
        )

    private val descriptorsByClass = descriptors.associateBy(WinRtValueTypeMetadata::projectedClass)

    fun boxedRuntimeClassNameForType(type: KClass<*>): String? {
        enumMetadataForClass(type)?.let { descriptor ->
            return WinRtReferenceTypeNames.boxedReference(descriptor.projectedTypeName)
        }
        WinRtTypeClassifier.primitiveArrayElementType(type)?.let { elementType ->
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

    fun isPropertyValueCompatible(value: Any): Boolean =
        propertyTypeOf(value).let { it != PropertyType.OtherType && it != PropertyType.OtherTypeArray }

    fun propertyTypeOf(value: Any): PropertyType {
        val managedArray = normalizeManagedArray(value)
        if (managedArray != null) {
            val descriptor = classifyPropertyValueDescriptor(managedArray.elements.firstOrNull(), managedArray.metadata)
            return descriptor.propertyTypeArray
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

    fun descriptorForPropertyType(propertyType: PropertyType): WinRtValueTypeMetadata? =
        descriptors.firstOrNull { it.propertyType == propertyType }

    fun descriptorForPropertyTypeArray(propertyType: PropertyType): WinRtValueTypeMetadata? =
        descriptors.firstOrNull { it.propertyTypeArray == propertyType }

    fun inspectableArrayMetadata(): WinRtValueTypeMetadata = objectMetadata

    fun enumMetadataForClass(type: KClass<*>): WinRtEnumBoxingMetadata? {
        if (!platformIsEnumType(type)) {
            return null
        }
        val registeredType = type.registeredWinRtType() ?: return null
        val signature = runCatching { GuidGenerator.getSignature(type) }.getOrNull() ?: return null
        val match = enumSignaturePattern.matchEntire(signature) ?: return null
        val projectedTypeName = match.groupValues[1]
        val underlyingSignature = match.groupValues[2]
        val enumAbiValue = registeredType.enumAbiValue as? (Any) -> Int ?: return null
        val constants = platformEnumConstants(type) ?: return null

        fun readBits(enumValue: Any): Int = enumAbiValue(enumValue)

        return when (underlyingSignature) {
            "i4" ->
                WinRtEnumBoxingMetadata(
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.Int32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRtInvalidCastException(
                                "Unknown enum value $abiValue for ${type.typeDisplayName()}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            "u4" ->
                WinRtEnumBoxingMetadata(
                    projectedTypeName = projectedTypeName,
                    propertyType = PropertyType.UInt32,
                    nullableInterfaceId = ParameterizedInterfaceId.createFromSignature("pinterface({${IREFERENCE_GENERIC_INTERFACE.lowercase()}};$signature)"),
                    toAbiBits = ::readBits,
                    fromAbiBits = { abiValue ->
                        constants.firstOrNull { readBits(it) == abiValue }
                            ?: throw WinRtInvalidCastException(
                                "Unknown enum value ${abiValue.toUInt()} for ${type.typeDisplayName()}.",
                                HResult(TYPE_E_TYPEMISMATCH),
                            )
                    },
                )

            else -> null
        }
    }

    private fun descriptorForValue(value: Any): WinRtValueTypeMetadata? = descriptorForClass(value::class)

    private fun descriptorForClass(type: KClass<*>): WinRtValueTypeMetadata? =
        descriptorsByClass[type]
            ?: if (platformIsAssignableFrom(Exception::class, type)) exceptionMetadata else null

    private fun classifyPropertyValue(value: Any): WinRtValueTypeMetadata? =
        descriptorForValue(value)?.takeIf { it.propertyType != null }

    private fun classifyPropertyValueDescriptor(
        sampleElement: Any?,
        defaultDescriptor: WinRtValueTypeMetadata,
    ): WinRtValueTypeMetadata =
        when {
            sampleElement == null -> defaultDescriptor
            defaultDescriptor == objectMetadata -> objectMetadata
            else -> descriptorForValue(sampleElement) ?: defaultDescriptor
        }

    private fun normalizeManagedArray(value: Any): ManagedArrayMetadata? =
        when (value) {
            is Array<*> -> {
                val componentType = platformArrayElementType(value::class) ?: Any::class
                val descriptor = descriptorForClass(componentType) ?: if (componentType == Any::class) objectMetadata else null
                descriptor?.let { ManagedArrayMetadata(value, it) }
            }
            else -> normalizePrimitiveManagedArray(value)
        }

    private fun isSupportedArrayValue(value: Any): Boolean = isArrayKClass(value::class)

    private fun normalizePrimitiveManagedArray(value: Any): ManagedArrayMetadata? {
        val elementType = WinRtTypeClassifier.primitiveArrayElementType(value::class) ?: return null
        val descriptor = descriptorsByClass[elementType] ?: return null
        val boxedElements = WinRtTypeClassifier.boxPrimitiveArray(value) ?: return null
        return ManagedArrayMetadata(boxedElements, descriptor)
    }

    private fun boxedReferenceRuntimeClassName(
        interfaceId: Guid,
        descriptor: WinRtValueTypeMetadata,
    ): String {
        check(descriptor.nullableInterfaceId == interfaceId)
        return WinRtReferenceTypeNames.boxedReference(TypeNameSupport.getNameForType(descriptor.projectedClass))
    }

    private fun boxedReferenceArrayRuntimeClassName(
        interfaceId: Guid,
        descriptor: WinRtValueTypeMetadata,
    ): String {
        check(descriptor.referenceArrayInterfaceId == interfaceId)
        return WinRtReferenceTypeNames.boxedReferenceArray(TypeNameSupport.getNameForType(descriptor.projectedClass))
    }

    private fun isArrayKClass(type: KClass<*>): Boolean = platformArrayElementType(type) != null

    private fun arrayElementType(type: KClass<*>): KClass<*>? = platformArrayElementType(type)
}
