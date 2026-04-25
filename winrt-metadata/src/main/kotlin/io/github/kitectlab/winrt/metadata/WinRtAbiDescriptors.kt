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
    private val typeClassifier: WinRtMetadataTypeClassifier,
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
                val classification = typeClassifier.classify(normalizedType, currentNamespace)
                val resolvedArguments = normalizedType.typeArguments.map { argument ->
                    resolveType(argument, currentNamespace)
                }
                WinRtAbiTypeDescriptor(
                    category = classification.abiCategory,
                    type = classification.type,
                    resolvedTypeName = classification.definitionQualifiedName ?: classification.typeName,
                    resolvedType = classification.definitionType,
                    enumUnderlyingType = classification.definitionType?.enumUnderlyingType,
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
            return WinRtMetadataAbiResolver(model.typeClassifier())
        }
    }
}

fun WinRtMetadataModel.abiResolver(): WinRtMetadataAbiResolver = WinRtMetadataAbiResolver.create(this)

private fun abiCategoryFor(
    parameter: WinRtParameterDefinition,
    typeDescriptor: WinRtAbiTypeDescriptor,
): WinRtAbiParameterCategory {
    val metadataCategory = metadataParameterCategoryFor(parameter)
    return when (metadataCategory) {
        WinRtMetadataParameterCategory.In -> WinRtAbiParameterCategory.In
        WinRtMetadataParameterCategory.Ref -> WinRtAbiParameterCategory.Ref
        WinRtMetadataParameterCategory.Out -> WinRtAbiParameterCategory.Out
        WinRtMetadataParameterCategory.PassArray -> WinRtAbiParameterCategory.PassArray
        WinRtMetadataParameterCategory.FillArray -> WinRtAbiParameterCategory.FillArray
        WinRtMetadataParameterCategory.ReceiveArray -> WinRtAbiParameterCategory.ReceiveArray
    }
}
