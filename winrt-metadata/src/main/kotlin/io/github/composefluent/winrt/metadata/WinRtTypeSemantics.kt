package io.github.composefluent.winrt.metadata

enum class WinRtFundamentalType {
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

fun winRtFundamentalTypeForName(typeName: String): WinRtFundamentalType? =
    when (typeName.trim().substringBefore('<').removeSuffix("?")) {
        "Boolean", "Bool", "System.Boolean" -> WinRtFundamentalType.Boolean
        "Char", "Char16", "System.Char" -> WinRtFundamentalType.Char
        "Byte", "SByte", "Int8", "System.SByte" -> WinRtFundamentalType.Int8
        "UByte", "UInt8", "System.Byte" -> WinRtFundamentalType.UInt8
        "Short", "Int16", "System.Int16" -> WinRtFundamentalType.Int16
        "UShort", "UInt16", "System.UInt16" -> WinRtFundamentalType.UInt16
        "Int", "Int32", "System.Int32" -> WinRtFundamentalType.Int32
        "UInt", "UInt32", "System.UInt32" -> WinRtFundamentalType.UInt32
        "Long", "Int64", "System.Int64" -> WinRtFundamentalType.Int64
        "ULong", "UInt64", "System.UInt64" -> WinRtFundamentalType.UInt64
        "Float", "Single", "System.Single" -> WinRtFundamentalType.Float
        "Double", "System.Double" -> WinRtFundamentalType.Double
        "String", "System.String" -> WinRtFundamentalType.String
        else -> null
    }

fun isWinRtFundamentalTypeName(typeName: String): Boolean =
    winRtFundamentalTypeForName(typeName) != null

fun isWinRtFundamentalTypeName(typeName: String, expectedType: WinRtFundamentalType): Boolean =
    winRtFundamentalTypeForName(typeName) == expectedType

fun WinRtFundamentalType.guidSignatureFragment(): String =
    when (this) {
        WinRtFundamentalType.Boolean -> "b1"
        WinRtFundamentalType.Char -> "c2"
        WinRtFundamentalType.Int8 -> "i1"
        WinRtFundamentalType.UInt8 -> "u1"
        WinRtFundamentalType.Int16 -> "i2"
        WinRtFundamentalType.UInt16 -> "u2"
        WinRtFundamentalType.Int32 -> "i4"
        WinRtFundamentalType.UInt32 -> "u4"
        WinRtFundamentalType.Int64 -> "i8"
        WinRtFundamentalType.UInt64 -> "u8"
        WinRtFundamentalType.Float -> "f4"
        WinRtFundamentalType.Double -> "f8"
        WinRtFundamentalType.String -> "string"
    }

val WinRtFundamentalType.isWinRtValueType: Boolean
    get() = this != WinRtFundamentalType.String

val WinRtFundamentalType.isWinRtBlittable: Boolean
    get() = this != WinRtFundamentalType.String &&
        this != WinRtFundamentalType.Char &&
        this != WinRtFundamentalType.Boolean

val WinRtFundamentalType.blittableAbiSizeBytes: Int?
    get() = when (this) {
        WinRtFundamentalType.Int8,
        WinRtFundamentalType.UInt8 -> 1
        WinRtFundamentalType.Int16,
        WinRtFundamentalType.UInt16 -> 2
        WinRtFundamentalType.Int32,
        WinRtFundamentalType.UInt32,
        WinRtFundamentalType.Float -> 4
        WinRtFundamentalType.Int64,
        WinRtFundamentalType.UInt64,
        WinRtFundamentalType.Double -> 8
        WinRtFundamentalType.Boolean,
        WinRtFundamentalType.Char,
        WinRtFundamentalType.String -> null
    }

val WinRtFundamentalType.blittableAbiAlignmentBytes: Int?
    get() = blittableAbiSizeBytes

fun WinRtFundamentalType.toGenericAbiDelegateTypeName(defaultTypeName: String): String =
    when (this) {
        WinRtFundamentalType.String -> "IntPtr"
        WinRtFundamentalType.Boolean -> "byte"
        WinRtFundamentalType.Char -> "ushort"
        else -> defaultTypeName
    }

fun winRtTypeKindForSystemBaseTypeName(typeName: String?): WinRtTypeKind? =
    when (typeName?.trim()) {
        "System.Enum" -> WinRtTypeKind.Enum
        "System.ValueType" -> WinRtTypeKind.Struct
        "System.MulticastDelegate" -> WinRtTypeKind.Delegate
        else -> null
    }

sealed interface WinRtTypeSemantics {
    data class Fundamental(val type: WinRtFundamentalType) : WinRtTypeSemantics
    data object Object : WinRtTypeSemantics
    data object Guid : WinRtTypeSemantics
    data object Type : WinRtTypeSemantics
    data class TypeDefinition(val type: WinRtTypeDefinition) : WinRtTypeSemantics
    data class GenericTypeInstance(
        val genericType: WinRtTypeDefinition,
        val genericArguments: List<WinRtTypeSemantics>,
    ) : WinRtTypeSemantics

    data class GenericTypeIndex(val index: Int) : WinRtTypeSemantics
    data class GenericTypeParameter(val parameter: WinRtGenericParameterDefinition) : WinRtTypeSemantics
}

class WinRtTypeSemanticsResolver(private val model: WinRtMetadataModel) {
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition> =
        model.normalized()
            .namespaces
            .flatMap(WinRtNamespace::types)
            .associateBy(WinRtTypeDefinition::qualifiedName)

    fun resolve(type: WinRtTypeRef, currentNamespace: String? = null): WinRtTypeSemantics =
        resolve(type, currentNamespace, genericParameters = emptyList())

    fun resolve(
        type: WinRtTypeRef,
        currentNamespace: String? = null,
        genericParameters: List<WinRtGenericParameterDefinition>,
    ): WinRtTypeSemantics {
        val normalized = type.normalized()
        return when (normalized.kind) {
            WinRtTypeRefKind.GenericTypeParameter -> {
                val parameter = genericParameters.firstOrNull { it.index == normalized.genericParameterIndex }
                if (parameter != null) {
                    WinRtTypeSemantics.GenericTypeParameter(parameter)
                } else {
                    WinRtTypeSemantics.GenericTypeIndex(normalized.genericParameterIndex ?: 0)
                }
            }

            WinRtTypeRefKind.MethodTypeParameter ->
                WinRtTypeSemantics.GenericTypeIndex(normalized.genericParameterIndex ?: 0)

            WinRtTypeRefKind.Array ->
                throw IllegalArgumentException("Array type semantics must be handled by parameter or ABI shape descriptors: ${normalized.typeName}")

            WinRtTypeRefKind.Unknown ->
                throw IllegalArgumentException("Type semantics cannot be resolved for unknown type '${normalized.typeName}'")

            WinRtTypeRefKind.Named -> resolveNamed(normalized, currentNamespace, genericParameters)
        }
    }

    private fun resolveNamed(
        type: WinRtTypeRef,
        currentNamespace: String?,
        genericParameters: List<WinRtGenericParameterDefinition>,
    ): WinRtTypeSemantics {
        winRtFundamentalTypeForName(type.typeName)?.let { return WinRtTypeSemantics.Fundamental(it) }
        if (isWinRtObjectTypeName(type.typeName)) {
            return WinRtTypeSemantics.Object
        }
        if (isWinRtGuidTypeName(type.typeName)) {
            return WinRtTypeSemantics.Guid
        }
        val definition = resolveDefinition(type, currentNamespace)
        if (definition != null) {
            return if (type.typeArguments.isEmpty()) {
                WinRtTypeSemantics.TypeDefinition(definition)
            } else {
                WinRtTypeSemantics.GenericTypeInstance(
                    genericType = definition,
                    genericArguments = type.typeArguments.map { argument ->
                        resolve(argument, currentNamespace, genericParameters)
                    },
                )
            }
        }
        if (isWinRtTypeTypeName(type.typeName)) {
            return WinRtTypeSemantics.Type
        }
        throw IllegalArgumentException("Could not resolve type semantics for '${type.typeName}'")
    }

    private fun resolveDefinition(type: WinRtTypeRef, currentNamespace: String?): WinRtTypeDefinition? {
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

fun WinRtMetadataModel.typeSemanticsResolver(): WinRtTypeSemanticsResolver =
    WinRtTypeSemanticsResolver(this)
