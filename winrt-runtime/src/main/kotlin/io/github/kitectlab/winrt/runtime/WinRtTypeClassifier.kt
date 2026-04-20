package io.github.kitectlab.winrt.runtime

internal data class WinRtKnownType(
    val representativeClass: Class<*>,
    val canonicalRuntimeName: String,
    val signature: WinRtTypeSignature,
    val classAliases: Set<Class<*>>,
    val runtimeNameAliases: Set<String> = setOf(canonicalRuntimeName),
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
                representativeClass = java.lang.Byte::class.java,
                canonicalRuntimeName = "Int8",
                signature = WinRtTypeSignature.int8(),
                classAliases = setOf(java.lang.Byte.TYPE, java.lang.Byte::class.java),
                runtimeNameAliases = setOf("Int8"),
            ),
            WinRtKnownType(
                representativeClass = UByte::class.java,
                canonicalRuntimeName = "UInt8",
                signature = WinRtTypeSignature.uint8(),
                classAliases = setOf(UByte::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Short::class.java,
                canonicalRuntimeName = "Int16",
                signature = WinRtTypeSignature.int16(),
                classAliases = setOf(java.lang.Short.TYPE, java.lang.Short::class.java),
                runtimeNameAliases = setOf("Int16"),
            ),
            WinRtKnownType(
                representativeClass = UShort::class.java,
                canonicalRuntimeName = "UInt16",
                signature = WinRtTypeSignature.uint16(),
                classAliases = setOf(UShort::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Integer::class.java,
                canonicalRuntimeName = "Int32",
                signature = WinRtTypeSignature.int32(),
                classAliases = setOf(java.lang.Integer.TYPE, java.lang.Integer::class.java),
                runtimeNameAliases = setOf("Int32"),
            ),
            WinRtKnownType(
                representativeClass = UInt::class.java,
                canonicalRuntimeName = "UInt32",
                signature = WinRtTypeSignature.uint32(),
                classAliases = setOf(UInt::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Long::class.java,
                canonicalRuntimeName = "Int64",
                signature = WinRtTypeSignature.int64(),
                classAliases = setOf(java.lang.Long.TYPE, java.lang.Long::class.java),
                runtimeNameAliases = setOf("Int64"),
            ),
            WinRtKnownType(
                representativeClass = ULong::class.java,
                canonicalRuntimeName = "UInt64",
                signature = WinRtTypeSignature.uint64(),
                classAliases = setOf(ULong::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Boolean::class.java,
                canonicalRuntimeName = "Boolean",
                signature = WinRtTypeSignature.boolean(),
                classAliases = setOf(java.lang.Boolean.TYPE, java.lang.Boolean::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Character::class.java,
                canonicalRuntimeName = "Char16",
                signature = WinRtTypeSignature.char16(),
                classAliases = setOf(java.lang.Character.TYPE, java.lang.Character::class.java),
                runtimeNameAliases = setOf("Char16", "Char"),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Float::class.java,
                canonicalRuntimeName = "Single",
                signature = WinRtTypeSignature.float32(),
                classAliases = setOf(java.lang.Float.TYPE, java.lang.Float::class.java),
            ),
            WinRtKnownType(
                representativeClass = java.lang.Double::class.java,
                canonicalRuntimeName = "Double",
                signature = WinRtTypeSignature.float64(),
                classAliases = setOf(java.lang.Double.TYPE, java.lang.Double::class.java),
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

    fun classify(type: Class<*>): WinRtKnownType? = knownTypesByClass[type]

    fun resolve(runtimeName: String): WinRtKnownType? = knownTypesByRuntimeName[runtimeName]

    fun isIntrinsicWindowsRuntimeType(type: Class<*>): Boolean = classify(type) != null
}
