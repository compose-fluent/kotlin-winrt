@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.kitectlab.winrt.runtime

internal data class WinRtKnownType(
    val representativeClass: Class<*>,
    val canonicalRuntimeName: String,
    val signature: WinRtTypeSignature,
    val classAliases: Set<Class<*>>,
    val runtimeNameAliases: Set<String> = setOf(canonicalRuntimeName),
    val primitiveArrayClass: Class<*>? = null,
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
                representativeClass = Byte::class.javaObjectType,
                canonicalRuntimeName = "Int8",
                signature = WinRtTypeSignature.int8(),
                classAliases = setOf(Byte::class.javaPrimitiveType!!, Byte::class.javaObjectType),
                runtimeNameAliases = setOf("Int8"),
                primitiveArrayClass = ByteArray::class.java,
                boxPrimitiveArray = { (it as ByteArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = UByte::class.java,
                canonicalRuntimeName = "UInt8",
                signature = WinRtTypeSignature.uint8(),
                classAliases = setOf(UByte::class.java),
                primitiveArrayClass = UByteArray::class.java,
                boxPrimitiveArray = { (it as UByteArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Short::class.javaObjectType,
                canonicalRuntimeName = "Int16",
                signature = WinRtTypeSignature.int16(),
                classAliases = setOf(Short::class.javaPrimitiveType!!, Short::class.javaObjectType),
                runtimeNameAliases = setOf("Int16"),
                primitiveArrayClass = ShortArray::class.java,
                boxPrimitiveArray = { (it as ShortArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = UShort::class.java,
                canonicalRuntimeName = "UInt16",
                signature = WinRtTypeSignature.uint16(),
                classAliases = setOf(UShort::class.java),
                primitiveArrayClass = UShortArray::class.java,
                boxPrimitiveArray = { (it as UShortArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Int::class.javaObjectType,
                canonicalRuntimeName = "Int32",
                signature = WinRtTypeSignature.int32(),
                classAliases = setOf(Int::class.javaPrimitiveType!!, Int::class.javaObjectType),
                runtimeNameAliases = setOf("Int32"),
                primitiveArrayClass = IntArray::class.java,
                boxPrimitiveArray = { (it as IntArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = UInt::class.java,
                canonicalRuntimeName = "UInt32",
                signature = WinRtTypeSignature.uint32(),
                classAliases = setOf(UInt::class.java),
                primitiveArrayClass = UIntArray::class.java,
                boxPrimitiveArray = { (it as UIntArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Long::class.javaObjectType,
                canonicalRuntimeName = "Int64",
                signature = WinRtTypeSignature.int64(),
                classAliases = setOf(Long::class.javaPrimitiveType!!, Long::class.javaObjectType),
                runtimeNameAliases = setOf("Int64"),
                primitiveArrayClass = LongArray::class.java,
                boxPrimitiveArray = { (it as LongArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = ULong::class.java,
                canonicalRuntimeName = "UInt64",
                signature = WinRtTypeSignature.uint64(),
                classAliases = setOf(ULong::class.java),
                primitiveArrayClass = ULongArray::class.java,
                boxPrimitiveArray = { (it as ULongArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Boolean::class.javaObjectType,
                canonicalRuntimeName = "Boolean",
                signature = WinRtTypeSignature.boolean(),
                classAliases = setOf(Boolean::class.javaPrimitiveType!!, Boolean::class.javaObjectType),
                primitiveArrayClass = BooleanArray::class.java,
                boxPrimitiveArray = { (it as BooleanArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Char::class.javaObjectType,
                canonicalRuntimeName = "Char16",
                signature = WinRtTypeSignature.char16(),
                classAliases = setOf(Char::class.javaPrimitiveType!!, Char::class.javaObjectType),
                runtimeNameAliases = setOf("Char16", "Char"),
                primitiveArrayClass = CharArray::class.java,
                boxPrimitiveArray = { (it as CharArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Float::class.javaObjectType,
                canonicalRuntimeName = "Single",
                signature = WinRtTypeSignature.float32(),
                classAliases = setOf(Float::class.javaPrimitiveType!!, Float::class.javaObjectType),
                primitiveArrayClass = FloatArray::class.java,
                boxPrimitiveArray = { (it as FloatArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = Double::class.javaObjectType,
                canonicalRuntimeName = "Double",
                signature = WinRtTypeSignature.float64(),
                classAliases = setOf(Double::class.javaPrimitiveType!!, Double::class.javaObjectType),
                primitiveArrayClass = DoubleArray::class.java,
                boxPrimitiveArray = { (it as DoubleArray).toTypedArray() },
            ),
            WinRtKnownType(
                representativeClass = String::class.java,
                canonicalRuntimeName = "String",
                signature = WinRtTypeSignature.string(),
                classAliases = setOf(String::class.java),
            ),
            WinRtKnownType(
                representativeClass = Guid::class.java,
                canonicalRuntimeName = "Guid",
                signature = WinRtTypeSignature.guidValue(),
                classAliases = setOf(Guid::class.java),
            ),
            WinRtKnownType(
                representativeClass = Any::class.java,
                canonicalRuntimeName = "Object",
                signature = WinRtTypeSignature.object_(),
                classAliases = setOf(Any::class.java),
            ),
        )

    private val knownTypesByClass: Map<Class<*>, WinRtKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.classAliases.forEach { alias ->
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

    private val knownTypesByPrimitiveArrayClass: Map<Class<*>, WinRtKnownType> =
        buildMap {
            knownTypes.forEach { knownType ->
                knownType.primitiveArrayClass?.let { arrayClass ->
                    put(arrayClass, knownType)
                }
            }
        }

    fun classify(type: Class<*>): WinRtKnownType? = knownTypesByClass[type]

    fun resolve(runtimeName: String): WinRtKnownType? = knownTypesByRuntimeName[runtimeName]

    fun primitiveArrayElementType(arrayType: Class<*>): Class<*>? = knownTypesByPrimitiveArrayClass[arrayType]?.representativeClass

    fun primitiveArrayClassForElementType(elementType: Class<*>): Class<*>? = classify(elementType)?.primitiveArrayClass

    fun boxPrimitiveArray(value: Any): Array<*>? = knownTypesByPrimitiveArrayClass[value.javaClass]?.boxPrimitiveArray?.invoke(value)

    fun isIntrinsicWindowsRuntimeType(type: Class<*>): Boolean = classify(type) != null
}
