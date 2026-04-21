@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

import kotlin.reflect.KClass

internal data class WinRtKnownType(
    val representativeType: KClass<*>,
    val canonicalRuntimeName: String,
    val signature: WinRtTypeSignature,
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
internal object WinRtTypeClassifier {
    private val knownTypes =
        listOf(
            WinRtKnownType(
                representativeType = Byte::class,
                canonicalRuntimeName = "Int8",
                signature = WinRtTypeSignature.int8(),
                typeAliases = setOf(Byte::class),
                runtimeNameAliases = setOf("Int8"),
                primitiveArrayType = ByteArray::class,
                boxPrimitiveArray = { (it as ByteArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = UByte::class,
                canonicalRuntimeName = "UInt8",
                signature = WinRtTypeSignature.uint8(),
                typeAliases = setOf(UByte::class),
                primitiveArrayType = UByteArray::class,
                boxPrimitiveArray = { (it as UByteArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Short::class,
                canonicalRuntimeName = "Int16",
                signature = WinRtTypeSignature.int16(),
                typeAliases = setOf(Short::class),
                runtimeNameAliases = setOf("Int16"),
                primitiveArrayType = ShortArray::class,
                boxPrimitiveArray = { (it as ShortArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = UShort::class,
                canonicalRuntimeName = "UInt16",
                signature = WinRtTypeSignature.uint16(),
                typeAliases = setOf(UShort::class),
                primitiveArrayType = UShortArray::class,
                boxPrimitiveArray = { (it as UShortArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Int::class,
                canonicalRuntimeName = "Int32",
                signature = WinRtTypeSignature.int32(),
                typeAliases = setOf(Int::class),
                runtimeNameAliases = setOf("Int32"),
                primitiveArrayType = IntArray::class,
                boxPrimitiveArray = { (it as IntArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = UInt::class,
                canonicalRuntimeName = "UInt32",
                signature = WinRtTypeSignature.uint32(),
                typeAliases = setOf(UInt::class),
                primitiveArrayType = UIntArray::class,
                boxPrimitiveArray = { (it as UIntArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Long::class,
                canonicalRuntimeName = "Int64",
                signature = WinRtTypeSignature.int64(),
                typeAliases = setOf(Long::class),
                runtimeNameAliases = setOf("Int64"),
                primitiveArrayType = LongArray::class,
                boxPrimitiveArray = { (it as LongArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = ULong::class,
                canonicalRuntimeName = "UInt64",
                signature = WinRtTypeSignature.uint64(),
                typeAliases = setOf(ULong::class),
                primitiveArrayType = ULongArray::class,
                boxPrimitiveArray = { (it as ULongArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Boolean::class,
                canonicalRuntimeName = "Boolean",
                signature = WinRtTypeSignature.boolean(),
                typeAliases = setOf(Boolean::class),
                primitiveArrayType = BooleanArray::class,
                boxPrimitiveArray = { (it as BooleanArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Char::class,
                canonicalRuntimeName = "Char16",
                signature = WinRtTypeSignature.char16(),
                typeAliases = setOf(Char::class),
                runtimeNameAliases = setOf("Char16", "Char"),
                primitiveArrayType = CharArray::class,
                boxPrimitiveArray = { (it as CharArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Float::class,
                canonicalRuntimeName = "Single",
                signature = WinRtTypeSignature.float32(),
                typeAliases = setOf(Float::class),
                primitiveArrayType = FloatArray::class,
                boxPrimitiveArray = { (it as FloatArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = Double::class,
                canonicalRuntimeName = "Double",
                signature = WinRtTypeSignature.float64(),
                typeAliases = setOf(Double::class),
                primitiveArrayType = DoubleArray::class,
                boxPrimitiveArray = { (it as DoubleArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeType = String::class,
                canonicalRuntimeName = "String",
                signature = WinRtTypeSignature.string(),
                typeAliases = setOf(String::class),
            ),
            WinRtKnownType(
                representativeType = Guid::class,
                canonicalRuntimeName = "Guid",
                signature = WinRtTypeSignature.guidValue(),
                typeAliases = setOf(Guid::class),
            ),
            WinRtKnownType(
                representativeType = Any::class,
                canonicalRuntimeName = "Object",
                signature = WinRtTypeSignature.object_(),
                typeAliases = setOf(Any::class),
            ),
        )

    private val knownTypesByType: Map<KClass<*>, WinRtKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.typeAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val knownTypesByRuntimeName: Map<String, WinRtKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.runtimeNameAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val knownTypesByPrimitiveArrayType: Map<KClass<*>, WinRtKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.primitiveArrayType?.let { arrayType ->
                    put(arrayType, knownType)
                }
            }
        }

    fun classify(type: KClass<*>): WinRtKnownType? = knownTypesByType[type]

    fun resolve(runtimeName: String): WinRtKnownType? = knownTypesByRuntimeName[runtimeName]

    fun primitiveArrayElementType(arrayType: KClass<*>): KClass<*>? =
        knownTypesByPrimitiveArrayType[arrayType]?.representativeType

    fun primitiveArrayTypeForElementType(elementType: KClass<*>): KClass<*>? =
        classify(elementType)?.primitiveArrayType

    fun boxPrimitiveArray(value: Any): Array<*>? =
        knownTypesByPrimitiveArrayType[value::class]?.boxPrimitiveArray?.invoke(value)

    fun isIntrinsicWindowsRuntimeType(type: KClass<*>): Boolean = classify(type) != null
}
