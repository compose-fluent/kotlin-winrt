package io.github.kitectlab.winrt.metadata

enum class WinRtAbiTypeCategory {
    Unit,
    Fundamental,
    String,
    Object,
    Guid,
    Type,
    Enum,
    Struct,
    Interface,
    Delegate,
    RuntimeClass,
    GenericTypeParameter,
    MethodTypeParameter,
    Array,
    Unknown,
}

enum class WinRtAbiParameterCategory {
    In,
    Ref,
    Out,
    PassArray,
    FillArray,
    ReceiveArray,
}

data class WinRtAbiTypeDescriptor(
    val category: WinRtAbiTypeCategory,
    val type: WinRtTypeRef,
    val resolvedTypeName: String = type.typeName,
    val resolvedType: WinRtTypeDefinition? = null,
    val enumUnderlyingType: WinRtIntegralType? = null,
    val elementType: WinRtAbiTypeDescriptor? = null,
    val typeArguments: List<WinRtAbiTypeDescriptor> = emptyList(),
)

data class WinRtAbiParameterDescriptor(
    val name: String,
    val category: WinRtAbiParameterCategory,
    val direction: WinRtParameterDirection,
    val type: WinRtAbiTypeDescriptor,
    val isInParameter: Boolean,
    val isOutParameter: Boolean,
)

data class WinRtAbiMethodDescriptor(
    val returnType: WinRtAbiTypeDescriptor,
    val parameters: List<WinRtAbiParameterDescriptor>,
)

class WinRtMetadataAbiResolver private constructor(
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
) {
    fun resolveType(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtAbiTypeDescriptor {
        val normalizedType = type.normalized()
        return when (normalizedType.kind) {
            WinRtTypeRefKind.Array -> {
                val elementDescriptor = resolveType(normalizedType.elementType ?: WinRtTypeRef.unknown(), currentNamespace)
                WinRtAbiTypeDescriptor(
                    category = WinRtAbiTypeCategory.Array,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                    elementType = elementDescriptor,
                )
            }

            WinRtTypeRefKind.GenericTypeParameter ->
                WinRtAbiTypeDescriptor(
                    category = WinRtAbiTypeCategory.GenericTypeParameter,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )

            WinRtTypeRefKind.MethodTypeParameter ->
                WinRtAbiTypeDescriptor(
                    category = WinRtAbiTypeCategory.MethodTypeParameter,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )

            WinRtTypeRefKind.Named -> {
                val rawTypeName = normalizedType.qualifiedName ?: "Any"
                val resolvedTypeName = qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
                val resolvedType = typesByQualifiedName[resolvedTypeName]
                val resolvedArguments = normalizedType.typeArguments.map { argument ->
                    resolveType(argument, currentNamespace)
                }
                WinRtAbiTypeDescriptor(
                    category = abiCategoryFor(rawTypeName, resolvedType),
                    type = normalizedType,
                    resolvedTypeName = resolvedTypeName,
                    resolvedType = resolvedType,
                    enumUnderlyingType = resolvedType?.enumUnderlyingType,
                    typeArguments = resolvedArguments,
                )
            }

            WinRtTypeRefKind.Unknown ->
                WinRtAbiTypeDescriptor(
                    category = WinRtAbiTypeCategory.Unknown,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )
        }
    }

    fun resolveParameter(
        parameter: WinRtParameterDefinition,
        currentNamespace: String,
    ): WinRtAbiParameterDescriptor {
        val typeDescriptor = resolveType(parameter.type, currentNamespace)
        return WinRtAbiParameterDescriptor(
            name = parameter.name,
            category = abiCategoryFor(parameter, typeDescriptor),
            direction = parameter.direction,
            type = typeDescriptor,
            isInParameter = parameter.isInParameter,
            isOutParameter = parameter.isOutParameter,
        )
    }

    fun resolveMethod(
        method: WinRtMethodDefinition,
        currentNamespace: String,
    ): WinRtAbiMethodDescriptor =
        WinRtAbiMethodDescriptor(
            returnType = resolveType(method.returnType, currentNamespace),
            parameters = method.parameters.map { parameter -> resolveParameter(parameter, currentNamespace) },
        )

    companion object {
        fun create(model: WinRtMetadataModel): WinRtMetadataAbiResolver {
            val normalizedModel = model.normalized()
            val byQualifiedName = buildMap {
                normalizedModel.namespaces.forEach { namespace ->
                    namespace.types.forEach { type ->
                        put(type.qualifiedName, type)
                    }
                }
            }
            return WinRtMetadataAbiResolver(byQualifiedName)
        }
    }
}

fun WinRtMetadataModel.abiResolver(): WinRtMetadataAbiResolver = WinRtMetadataAbiResolver.create(this)

private fun abiCategoryFor(
    rawTypeName: String,
    resolvedType: WinRtTypeDefinition?,
): WinRtAbiTypeCategory = when {
    rawTypeName == "Unit" -> WinRtAbiTypeCategory.Unit
    rawTypeName in FUNDAMENTAL_TYPE_NAMES -> WinRtAbiTypeCategory.Fundamental
    rawTypeName == "String" -> WinRtAbiTypeCategory.String
    rawTypeName == "Any" || rawTypeName == "System.Object" -> WinRtAbiTypeCategory.Object
    rawTypeName == "Guid" || rawTypeName == "System.Guid" -> WinRtAbiTypeCategory.Guid
    rawTypeName == "Type" || rawTypeName == "System.Type" -> WinRtAbiTypeCategory.Type
    resolvedType != null -> when (resolvedType.kind) {
        WinRtTypeKind.Enum -> WinRtAbiTypeCategory.Enum
        WinRtTypeKind.Struct -> WinRtAbiTypeCategory.Struct
        WinRtTypeKind.Interface -> WinRtAbiTypeCategory.Interface
        WinRtTypeKind.Delegate -> WinRtAbiTypeCategory.Delegate
        WinRtTypeKind.RuntimeClass -> WinRtAbiTypeCategory.RuntimeClass
        WinRtTypeKind.Unknown -> WinRtAbiTypeCategory.Unknown
    }

    else -> WinRtAbiTypeCategory.Unknown
}

private fun abiCategoryFor(
    parameter: WinRtParameterDefinition,
    typeDescriptor: WinRtAbiTypeDescriptor,
): WinRtAbiParameterCategory {
    if (typeDescriptor.category == WinRtAbiTypeCategory.Array) {
        return when {
            parameter.isInParameter -> WinRtAbiParameterCategory.PassArray
            parameter.typeIsByRef && parameter.isOutParameter -> WinRtAbiParameterCategory.ReceiveArray
            parameter.isOutParameter -> WinRtAbiParameterCategory.FillArray
            parameter.direction == WinRtParameterDirection.Out && parameter.typeIsByRef -> WinRtAbiParameterCategory.ReceiveArray
            parameter.direction == WinRtParameterDirection.Out -> WinRtAbiParameterCategory.FillArray
            else -> WinRtAbiParameterCategory.PassArray
        }
    }

    return when (parameter.direction) {
        WinRtParameterDirection.In -> WinRtAbiParameterCategory.In
        WinRtParameterDirection.Ref -> WinRtAbiParameterCategory.Ref
        WinRtParameterDirection.Out -> WinRtAbiParameterCategory.Out
    }
}

private fun qualifyTypeName(
    rawTypeName: String,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): String? {
    if (rawTypeName.isBlank()) {
        return null
    }
    if (rawTypeName in typesByQualifiedName) {
        return rawTypeName
    }
    if ('.' !in rawTypeName) {
        val qualified = "$currentNamespace.$rawTypeName"
        if (qualified in typesByQualifiedName) {
            return qualified
        }
    }
    return null
}

private val FUNDAMENTAL_TYPE_NAMES = setOf(
    "Boolean",
    "Char",
    "Byte",
    "UByte",
    "Short",
    "UShort",
    "Int",
    "UInt",
    "Long",
    "ULong",
    "Float",
    "Double",
)
