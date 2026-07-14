@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal data class WinRTIntrinsicType(
    val representativeType: KClass<*>,
    val canonicalRuntimeName: String,
    val signature: WinRTTypeSignature,
    val typeAliases: Set<KClass<*>>,
    val runtimeNameAliases: Set<String> = setOf(canonicalRuntimeName),
    val primitiveArrayType: KClass<*>? = null,
    val boxPrimitiveArray: ((Any) -> Array<*>)? = null,
)

/**
 * Shared intrinsic WinRT type classification corresponding to the primitive/object/string/guid
 * convergence points used by `.cswinrt/src/WinRT.Runtime/TypeNameSupport.cs`,
 * `Projections.cs`, and `GuidGenerator.cs`.
 */
internal object WinRTTypeClassifier {
    private val intrinsicTypes =
        listOf(
            WinRTIntrinsicType(
                representativeType = Byte::class,
                canonicalRuntimeName = "Int8",
                signature = WinRTTypeSignature.int8(),
                typeAliases = setOf(Byte::class),
                runtimeNameAliases = setOf("Int8"),
                primitiveArrayType = ByteArray::class,
                boxPrimitiveArray = { (it as ByteArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = UByte::class,
                canonicalRuntimeName = "UInt8",
                signature = WinRTTypeSignature.uint8(),
                typeAliases = setOf(UByte::class),
                primitiveArrayType = UByteArray::class,
                boxPrimitiveArray = { (it as UByteArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Short::class,
                canonicalRuntimeName = "Int16",
                signature = WinRTTypeSignature.int16(),
                typeAliases = setOf(Short::class),
                runtimeNameAliases = setOf("Int16"),
                primitiveArrayType = ShortArray::class,
                boxPrimitiveArray = { (it as ShortArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = UShort::class,
                canonicalRuntimeName = "UInt16",
                signature = WinRTTypeSignature.uint16(),
                typeAliases = setOf(UShort::class),
                primitiveArrayType = UShortArray::class,
                boxPrimitiveArray = { (it as UShortArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Int::class,
                canonicalRuntimeName = "Int32",
                signature = WinRTTypeSignature.int32(),
                typeAliases = setOf(Int::class),
                runtimeNameAliases = setOf("Int32"),
                primitiveArrayType = IntArray::class,
                boxPrimitiveArray = { (it as IntArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = UInt::class,
                canonicalRuntimeName = "UInt32",
                signature = WinRTTypeSignature.uint32(),
                typeAliases = setOf(UInt::class),
                primitiveArrayType = UIntArray::class,
                boxPrimitiveArray = { (it as UIntArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Long::class,
                canonicalRuntimeName = "Int64",
                signature = WinRTTypeSignature.int64(),
                typeAliases = setOf(Long::class),
                runtimeNameAliases = setOf("Int64"),
                primitiveArrayType = LongArray::class,
                boxPrimitiveArray = { (it as LongArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = ULong::class,
                canonicalRuntimeName = "UInt64",
                signature = WinRTTypeSignature.uint64(),
                typeAliases = setOf(ULong::class),
                primitiveArrayType = ULongArray::class,
                boxPrimitiveArray = { (it as ULongArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Boolean::class,
                canonicalRuntimeName = "Boolean",
                signature = WinRTTypeSignature.boolean(),
                typeAliases = setOf(Boolean::class),
                primitiveArrayType = BooleanArray::class,
                boxPrimitiveArray = { (it as BooleanArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Char::class,
                canonicalRuntimeName = "Char16",
                signature = WinRTTypeSignature.char16(),
                typeAliases = setOf(Char::class),
                runtimeNameAliases = setOf("Char16", "Char"),
                primitiveArrayType = CharArray::class,
                boxPrimitiveArray = { (it as CharArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Float::class,
                canonicalRuntimeName = "Single",
                signature = WinRTTypeSignature.float32(),
                typeAliases = setOf(Float::class),
                primitiveArrayType = FloatArray::class,
                boxPrimitiveArray = { (it as FloatArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = Double::class,
                canonicalRuntimeName = "Double",
                signature = WinRTTypeSignature.float64(),
                typeAliases = setOf(Double::class),
                primitiveArrayType = DoubleArray::class,
                boxPrimitiveArray = { (it as DoubleArray).toTypedArray() },
            ),
            WinRTIntrinsicType(
                representativeType = String::class,
                canonicalRuntimeName = "String",
                signature = WinRTTypeSignature.string(),
                typeAliases = setOf(String::class),
            ),
            WinRTIntrinsicType(
                representativeType = Guid::class,
                canonicalRuntimeName = "Guid",
                signature = WinRTTypeSignature.guidValue(),
                typeAliases = setOf(Guid::class),
            ),
            WinRTIntrinsicType(
                representativeType = Any::class,
                canonicalRuntimeName = "Object",
                signature = WinRTTypeSignature.object_(),
                typeAliases = setOf(Any::class),
                runtimeNameAliases = setOf("Object", "System.Object", "Any"),
            ),
        )

    private val intrinsicTypesByType: Map<KClass<*>, WinRTIntrinsicType> =
        buildMap {
            intrinsicTypes.forEach { knownType ->
                knownType.typeAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val intrinsicTypesByRuntimeName: Map<String, WinRTIntrinsicType> =
        buildMap {
            intrinsicTypes.forEach { knownType ->
                knownType.runtimeNameAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val intrinsicTypesByPrimitiveArrayType: Map<KClass<*>, WinRTIntrinsicType> =
        buildMap {
            intrinsicTypes.forEach { knownType ->
                knownType.primitiveArrayType?.let { arrayType ->
                    put(arrayType, knownType)
                }
            }
        }

    fun classify(type: KClass<*>): WinRTIntrinsicType? = intrinsicTypesByType[type]

    fun resolve(runtimeName: String): WinRTIntrinsicType? = intrinsicTypesByRuntimeName[runtimeName]

    fun primitiveArrayElementType(arrayType: KClass<*>): KClass<*>? =
        intrinsicTypesByPrimitiveArrayType[arrayType]?.representativeType

    fun arrayElementType(arrayType: KClass<*>): KClass<*>? =
        primitiveArrayElementType(arrayType)

    fun primitiveArrayTypeForElementType(elementType: KClass<*>): KClass<*>? =
        classify(elementType)?.primitiveArrayType

    fun isObjectRuntimeName(runtimeName: String): Boolean =
        resolve(runtimeName)?.representativeType == Any::class

    fun boxPrimitiveArray(value: Any): Array<*>? =
        intrinsicTypesByPrimitiveArrayType[value::class]?.boxPrimitiveArray?.invoke(value)

    fun isIntrinsicScalarOrObjectType(type: KClass<*>): Boolean = classify(type) != null
}
