@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.composefluent.winrt.runtime

import kotlin.reflect.KClass

internal data class WinRTKnownType(
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
    private val knownTypes =
        listOf(
            WinRTKnownType(
                representativeType = Byte::class,
                canonicalRuntimeName = "Int8",
                signature = WinRTTypeSignature.int8(),
                typeAliases = setOf(Byte::class),
                runtimeNameAliases = setOf("Int8"),
                primitiveArrayType = ByteArray::class,
                boxPrimitiveArray = { (it as ByteArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = UByte::class,
                canonicalRuntimeName = "UInt8",
                signature = WinRTTypeSignature.uint8(),
                typeAliases = setOf(UByte::class),
                primitiveArrayType = UByteArray::class,
                boxPrimitiveArray = { (it as UByteArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Short::class,
                canonicalRuntimeName = "Int16",
                signature = WinRTTypeSignature.int16(),
                typeAliases = setOf(Short::class),
                runtimeNameAliases = setOf("Int16"),
                primitiveArrayType = ShortArray::class,
                boxPrimitiveArray = { (it as ShortArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = UShort::class,
                canonicalRuntimeName = "UInt16",
                signature = WinRTTypeSignature.uint16(),
                typeAliases = setOf(UShort::class),
                primitiveArrayType = UShortArray::class,
                boxPrimitiveArray = { (it as UShortArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Int::class,
                canonicalRuntimeName = "Int32",
                signature = WinRTTypeSignature.int32(),
                typeAliases = setOf(Int::class),
                runtimeNameAliases = setOf("Int32"),
                primitiveArrayType = IntArray::class,
                boxPrimitiveArray = { (it as IntArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = UInt::class,
                canonicalRuntimeName = "UInt32",
                signature = WinRTTypeSignature.uint32(),
                typeAliases = setOf(UInt::class),
                primitiveArrayType = UIntArray::class,
                boxPrimitiveArray = { (it as UIntArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Long::class,
                canonicalRuntimeName = "Int64",
                signature = WinRTTypeSignature.int64(),
                typeAliases = setOf(Long::class),
                runtimeNameAliases = setOf("Int64"),
                primitiveArrayType = LongArray::class,
                boxPrimitiveArray = { (it as LongArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = ULong::class,
                canonicalRuntimeName = "UInt64",
                signature = WinRTTypeSignature.uint64(),
                typeAliases = setOf(ULong::class),
                primitiveArrayType = ULongArray::class,
                boxPrimitiveArray = { (it as ULongArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Boolean::class,
                canonicalRuntimeName = "Boolean",
                signature = WinRTTypeSignature.boolean(),
                typeAliases = setOf(Boolean::class),
                primitiveArrayType = BooleanArray::class,
                boxPrimitiveArray = { (it as BooleanArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Char::class,
                canonicalRuntimeName = "Char16",
                signature = WinRTTypeSignature.char16(),
                typeAliases = setOf(Char::class),
                runtimeNameAliases = setOf("Char16", "Char"),
                primitiveArrayType = CharArray::class,
                boxPrimitiveArray = { (it as CharArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Float::class,
                canonicalRuntimeName = "Single",
                signature = WinRTTypeSignature.float32(),
                typeAliases = setOf(Float::class),
                primitiveArrayType = FloatArray::class,
                boxPrimitiveArray = { (it as FloatArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = Double::class,
                canonicalRuntimeName = "Double",
                signature = WinRTTypeSignature.float64(),
                typeAliases = setOf(Double::class),
                primitiveArrayType = DoubleArray::class,
                boxPrimitiveArray = { (it as DoubleArray).toTypedArray() },
            ),
            WinRTKnownType(
                representativeType = String::class,
                canonicalRuntimeName = "String",
                signature = WinRTTypeSignature.string(),
                typeAliases = setOf(String::class),
            ),
            WinRTKnownType(
                representativeType = Guid::class,
                canonicalRuntimeName = "Guid",
                signature = WinRTTypeSignature.guidValue(),
                typeAliases = setOf(Guid::class),
            ),
            WinRTKnownType(
                representativeType = Any::class,
                canonicalRuntimeName = "Object",
                signature = WinRTTypeSignature.object_(),
                typeAliases = setOf(Any::class),
                runtimeNameAliases = setOf("Object", "System.Object", "Any"),
            ),
        )

    private val knownTypesByType: Map<KClass<*>, WinRTKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.typeAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val knownTypesByRuntimeName: Map<String, WinRTKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.runtimeNameAliases.forEach { alias ->
                    put(alias, knownType)
                }
            }
        }

    private val knownTypesByPrimitiveArrayType: Map<KClass<*>, WinRTKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.primitiveArrayType?.let { arrayType ->
                    put(arrayType, knownType)
                }
            }
        }

    fun classify(type: KClass<*>): WinRTKnownType? = knownTypesByType[type]

    fun resolve(runtimeName: String): WinRTKnownType? = knownTypesByRuntimeName[runtimeName]

    fun primitiveArrayElementType(arrayType: KClass<*>): KClass<*>? =
        knownTypesByPrimitiveArrayType[arrayType]?.representativeType

    fun primitiveArrayTypeForElementType(elementType: KClass<*>): KClass<*>? =
        classify(elementType)?.primitiveArrayType

    fun isObjectRuntimeName(runtimeName: String): Boolean =
        resolve(runtimeName)?.representativeType == Any::class

    fun boxPrimitiveArray(value: Any): Array<*>? =
        knownTypesByPrimitiveArrayType[value::class]?.boxPrimitiveArray?.invoke(value)

    fun isIntrinsicWindowsRuntimeType(type: KClass<*>): Boolean = classify(type) != null
}
