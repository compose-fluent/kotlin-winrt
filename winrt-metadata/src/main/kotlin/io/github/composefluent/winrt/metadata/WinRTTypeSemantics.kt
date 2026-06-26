package io.github.composefluent.winrt.metadata

enum class WinRTFundamentalType {
    Boolean,
    Char,
    Int8,
    UInt8,
    Int16,
    UInt16,
    Int32,
    UInt32,
    Int64,
    UInt64,
    Float,
    Double,
    String,
}

fun winRTFundamentalTypeForName(typeName: String): WinRTFundamentalType? =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Boolean", "Bool", "System.Boolean" -> WinRTFundamentalType.Boolean
        "Char", "Char16", "System.Char" -> WinRTFundamentalType.Char
        "Byte", "SByte", "Int8", "System.SByte" -> WinRTFundamentalType.Int8
        "UByte", "UInt8", "System.Byte" -> WinRTFundamentalType.UInt8
        "Short", "Int16", "System.Int16" -> WinRTFundamentalType.Int16
        "UShort", "UInt16", "System.UInt16" -> WinRTFundamentalType.UInt16
        "Int", "Int32", "System.Int32" -> WinRTFundamentalType.Int32
        "UInt", "UInt32", "System.UInt32" -> WinRTFundamentalType.UInt32
        "Long", "Int64", "System.Int64" -> WinRTFundamentalType.Int64
        "ULong", "UInt64", "System.UInt64" -> WinRTFundamentalType.UInt64
        "Float", "Single", "System.Single" -> WinRTFundamentalType.Float
        "Double", "System.Double" -> WinRTFundamentalType.Double
        "String", "System.String" -> WinRTFundamentalType.String
        else -> null
    }

fun isWinRTFundamentalTypeName(typeName: String): Boolean =
    winRTFundamentalTypeForName(typeName) != null

fun isWinRTFundamentalTypeName(typeName: String, expectedType: WinRTFundamentalType): Boolean =
    winRTFundamentalTypeForName(typeName) == expectedType

fun WinRTFundamentalType.guidSignatureFragment(): String =
    when (this) {
        WinRTFundamentalType.Boolean -> "b1"
        WinRTFundamentalType.Char -> "c2"
        WinRTFundamentalType.Int8 -> "i1"
        WinRTFundamentalType.UInt8 -> "u1"
        WinRTFundamentalType.Int16 -> "i2"
        WinRTFundamentalType.UInt16 -> "u2"
        WinRTFundamentalType.Int32 -> "i4"
        WinRTFundamentalType.UInt32 -> "u4"
        WinRTFundamentalType.Int64 -> "i8"
        WinRTFundamentalType.UInt64 -> "u8"
        WinRTFundamentalType.Float -> "f4"
        WinRTFundamentalType.Double -> "f8"
        WinRTFundamentalType.String -> "string"
    }

val WinRTFundamentalType.isWinRTValueType: Boolean
    get() = this != WinRTFundamentalType.String

val WinRTFundamentalType.isWinRTBlittable: Boolean
    get() = this != WinRTFundamentalType.String &&
        this != WinRTFundamentalType.Char &&
        this != WinRTFundamentalType.Boolean

val WinRTFundamentalType.blittableAbiSizeBytes: Int?
    get() = when (this) {
        WinRTFundamentalType.Int8,
        WinRTFundamentalType.UInt8 -> 1
        WinRTFundamentalType.Int16,
        WinRTFundamentalType.UInt16 -> 2
        WinRTFundamentalType.Int32,
        WinRTFundamentalType.UInt32,
        WinRTFundamentalType.Float -> 4
        WinRTFundamentalType.Int64,
        WinRTFundamentalType.UInt64,
        WinRTFundamentalType.Double -> 8
        WinRTFundamentalType.Boolean,
        WinRTFundamentalType.Char,
        WinRTFundamentalType.String -> null
    }

val WinRTFundamentalType.blittableAbiAlignmentBytes: Int?
    get() = blittableAbiSizeBytes

fun WinRTFundamentalType.toNativeAbiTypeName(): String =
    when (this) {
        WinRTFundamentalType.String -> "IntPtr"
        WinRTFundamentalType.Boolean -> "byte"
        WinRTFundamentalType.Char -> "ushort"
        WinRTFundamentalType.Int8 -> "sbyte"
        WinRTFundamentalType.UInt8 -> "byte"
        WinRTFundamentalType.Int16 -> "short"
        WinRTFundamentalType.UInt16 -> "ushort"
        WinRTFundamentalType.Int32 -> "int"
        WinRTFundamentalType.UInt32 -> "uint"
        WinRTFundamentalType.Int64 -> "long"
        WinRTFundamentalType.UInt64 -> "ulong"
        WinRTFundamentalType.Float -> "float"
        WinRTFundamentalType.Double -> "double"
    }

fun WinRTFundamentalType.toKotlinProjectionTypeName(): String =
    when (this) {
        WinRTFundamentalType.Boolean -> "Boolean"
        WinRTFundamentalType.Char -> "Char"
        WinRTFundamentalType.Int8 -> "Byte"
        WinRTFundamentalType.UInt8 -> "UByte"
        WinRTFundamentalType.Int16 -> "Short"
        WinRTFundamentalType.UInt16 -> "UShort"
        WinRTFundamentalType.Int32 -> "Int"
        WinRTFundamentalType.UInt32 -> "UInt"
        WinRTFundamentalType.Int64 -> "Long"
        WinRTFundamentalType.UInt64 -> "ULong"
        WinRTFundamentalType.Float -> "Float"
        WinRTFundamentalType.Double -> "Double"
        WinRTFundamentalType.String -> "String"
    }

fun WinRTFundamentalType.toIntegralType(): WinRTIntegralType? =
    when (this) {
        WinRTFundamentalType.Int8 -> WinRTIntegralType.Int8
        WinRTFundamentalType.UInt8 -> WinRTIntegralType.UInt8
        WinRTFundamentalType.Int16 -> WinRTIntegralType.Int16
        WinRTFundamentalType.UInt16 -> WinRTIntegralType.UInt16
        WinRTFundamentalType.Int32 -> WinRTIntegralType.Int32
        WinRTFundamentalType.UInt32 -> WinRTIntegralType.UInt32
        WinRTFundamentalType.Int64 -> WinRTIntegralType.Int64
        WinRTFundamentalType.UInt64 -> WinRTIntegralType.UInt64
        WinRTFundamentalType.Boolean,
        WinRTFundamentalType.Char,
        WinRTFundamentalType.Float,
        WinRTFundamentalType.Double,
        WinRTFundamentalType.String -> null
    }

fun winRTTypeKindForSystemBaseTypeName(typeName: String?): WinRTTypeKind? =
    when (typeName?.trim()) {
        "System.Enum" -> WinRTTypeKind.Enum
        "System.ValueType" -> WinRTTypeKind.Struct
        "System.MulticastDelegate" -> WinRTTypeKind.Delegate
        else -> null
    }

sealed interface WinRTTypeSemantics {
    data class Fundamental(val type: WinRTFundamentalType) : WinRTTypeSemantics
    data object Object : WinRTTypeSemantics
    data object Guid : WinRTTypeSemantics
    data object Type : WinRTTypeSemantics
    data class TypeDefinition(val type: WinRTTypeDefinition) : WinRTTypeSemantics
    data class GenericTypeInstance(
        val genericType: WinRTTypeDefinition,
        val genericArguments: List<WinRTTypeSemantics>,
    ) : WinRTTypeSemantics

    data class GenericTypeIndex(val index: Int) : WinRTTypeSemantics
    data class GenericTypeParameter(val parameter: WinRTGenericParameterDefinition) : WinRTTypeSemantics
}

class WinRTTypeSemanticsResolver(private val model: WinRTMetadataModel) {
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition> =
        model.normalized()
            .namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)

    fun resolve(type: WinRTTypeRef, currentNamespace: String? = null): WinRTTypeSemantics =
        resolve(type, currentNamespace, genericParameters = emptyList())

    fun resolve(
        type: WinRTTypeRef,
        currentNamespace: String? = null,
        genericParameters: List<WinRTGenericParameterDefinition>,
    ): WinRTTypeSemantics {
        val normalized = type.normalized()
        return when (normalized.kind) {
            WinRTTypeRefKind.GenericTypeParameter -> {
                val parameter = genericParameters.firstOrNull { it.index == normalized.genericParameterIndex }
                if (parameter != null) {
                    WinRTTypeSemantics.GenericTypeParameter(parameter)
                } else {
                    WinRTTypeSemantics.GenericTypeIndex(normalized.genericParameterIndex ?: 0)
                }
            }

            WinRTTypeRefKind.MethodTypeParameter ->
                WinRTTypeSemantics.GenericTypeIndex(normalized.genericParameterIndex ?: 0)

            WinRTTypeRefKind.Array ->
                throw IllegalArgumentException("Array type semantics must be handled by parameter or ABI shape descriptors: ${normalized.typeName}")

            WinRTTypeRefKind.Unknown ->
                throw IllegalArgumentException("Type semantics cannot be resolved for unknown type '${normalized.typeName}'")

            WinRTTypeRefKind.Named -> resolveNamed(normalized, currentNamespace, genericParameters)
        }
    }

    private fun resolveNamed(
        type: WinRTTypeRef,
        currentNamespace: String?,
        genericParameters: List<WinRTGenericParameterDefinition>,
    ): WinRTTypeSemantics {
        winRTFundamentalTypeForName(type.typeName)?.let { return WinRTTypeSemantics.Fundamental(it) }
        if (isWinRTObjectTypeName(type.typeName)) {
            return WinRTTypeSemantics.Object
        }
        if (isWinRTGuidTypeName(type.typeName)) {
            return WinRTTypeSemantics.Guid
        }
        val definition = resolveDefinition(type, currentNamespace)
        if (definition != null) {
            return if (type.typeArguments.isEmpty()) {
                WinRTTypeSemantics.TypeDefinition(definition)
            } else {
                WinRTTypeSemantics.GenericTypeInstance(
                    genericType = definition,
                    genericArguments = type.typeArguments.map { argument ->
                        resolve(argument, currentNamespace, genericParameters)
                    },
                )
            }
        }
        if (isWinRTTypeTypeName(type.typeName)) {
            return WinRTTypeSemantics.Type
        }
        throw IllegalArgumentException("Could not resolve type semantics for '${type.typeName}'")
    }

    private fun resolveDefinition(type: WinRTTypeRef, currentNamespace: String?): WinRTTypeDefinition? {
        val qualifiedName = type.qualifiedName
        if (qualifiedName != null) {
            typesByQualifiedName[qualifiedName]?.let { return it }
        }
        val localName = type.typeName.substringBefore('<')
        if (currentNamespace != null && '.' !in localName) {
            typesByQualifiedName["$currentNamespace.$localName"]?.let { return it }
        }
        return typesByQualifiedName[localName]
    }

}

fun WinRTMetadataModel.typeSemanticsResolver(): WinRTTypeSemanticsResolver =
    WinRTTypeSemanticsResolver(this)
