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
        fundamentalType(type.typeName)?.let { return WinRtTypeSemantics.Fundamental(it) }
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

    private fun fundamentalType(name: String): WinRtFundamentalType? = when (name) {
        "Boolean", "System.Boolean" -> WinRtFundamentalType.Boolean
        "Char", "System.Char" -> WinRtFundamentalType.Char
        "Byte", "System.SByte" -> WinRtFundamentalType.Int8
        "UByte", "System.Byte" -> WinRtFundamentalType.UInt8
        "Short", "System.Int16" -> WinRtFundamentalType.Int16
        "UShort", "System.UInt16" -> WinRtFundamentalType.UInt16
        "Int", "System.Int32" -> WinRtFundamentalType.Int32
        "UInt", "System.UInt32" -> WinRtFundamentalType.UInt32
        "Long", "System.Int64" -> WinRtFundamentalType.Int64
        "ULong", "System.UInt64" -> WinRtFundamentalType.UInt64
        "Float", "System.Single" -> WinRtFundamentalType.Float
        "Double", "System.Double" -> WinRtFundamentalType.Double
        "String", "System.String" -> WinRtFundamentalType.String
        else -> null
    }
}

fun WinRtMetadataModel.typeSemanticsResolver(): WinRtTypeSemanticsResolver =
    WinRtTypeSemanticsResolver(this)
