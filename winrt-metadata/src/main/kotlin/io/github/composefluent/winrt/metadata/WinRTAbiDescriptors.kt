package io.github.composefluent.winrt.metadata

enum class WinRTAbiTypeCategory {
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

enum class WinRTAbiParameterCategory {
    In,
    Ref,
    Out,
    PassArray,
    FillArray,
    ReceiveArray,
}

data class WinRTAbiTypeDescriptor(
    val category: WinRTAbiTypeCategory,
    val type: WinRTTypeRef,
    val resolvedTypeName: String = type.typeName,
    val resolvedType: WinRTTypeDefinition? = null,
    val enumUnderlyingType: WinRTIntegralType? = null,
    val elementType: WinRTAbiTypeDescriptor? = null,
    val typeArguments: List<WinRTAbiTypeDescriptor> = emptyList(),
)

data class WinRTAbiParameterDescriptor(
    val name: String,
    val category: WinRTAbiParameterCategory,
    val direction: WinRTParameterDirection,
    val type: WinRTAbiTypeDescriptor,
    val isInParameter: Boolean,
    val isOutParameter: Boolean,
)

data class WinRTAbiMethodDescriptor(
    val returnType: WinRTAbiTypeDescriptor,
    val parameters: List<WinRTAbiParameterDescriptor>,
)

class WinRTMetadataAbiResolver private constructor(
    private val typeClassifier: WinRTMetadataTypeClassifier,
) {
    fun resolveType(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTAbiTypeDescriptor {
        val normalizedType = type.normalized()
        return when (normalizedType.kind) {
            WinRTTypeRefKind.Array -> {
                val elementDescriptor = resolveType(normalizedType.elementType ?: WinRTTypeRef.unknown(), currentNamespace)
                WinRTAbiTypeDescriptor(
                    category = WinRTAbiTypeCategory.Array,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                    elementType = elementDescriptor,
                )
            }

            WinRTTypeRefKind.GenericTypeParameter ->
                WinRTAbiTypeDescriptor(
                    category = WinRTAbiTypeCategory.GenericTypeParameter,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )

            WinRTTypeRefKind.MethodTypeParameter ->
                WinRTAbiTypeDescriptor(
                    category = WinRTAbiTypeCategory.MethodTypeParameter,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )

            WinRTTypeRefKind.Named -> {
                val classification = typeClassifier.classify(normalizedType, currentNamespace)
                val resolvedArguments = normalizedType.typeArguments.map { argument ->
                    resolveType(argument, currentNamespace)
                }
                WinRTAbiTypeDescriptor(
                    category = classification.abiCategory,
                    type = classification.type,
                    resolvedTypeName = classification.definitionQualifiedName ?: classification.typeName,
                    resolvedType = classification.definitionType,
                    enumUnderlyingType = classification.definitionType?.enumUnderlyingType,
                    typeArguments = resolvedArguments,
                )
            }

            WinRTTypeRefKind.Unknown ->
                WinRTAbiTypeDescriptor(
                    category = WinRTAbiTypeCategory.Unknown,
                    type = normalizedType,
                    resolvedTypeName = normalizedType.typeName,
                )
        }
    }

    fun resolveParameter(
        parameter: WinRTParameterDefinition,
        currentNamespace: String,
    ): WinRTAbiParameterDescriptor {
        val typeDescriptor = resolveType(parameter.type, currentNamespace)
        return WinRTAbiParameterDescriptor(
            name = parameter.name,
            category = abiCategoryFor(parameter, typeDescriptor),
            direction = parameter.direction,
            type = typeDescriptor,
            isInParameter = parameter.isInParameter,
            isOutParameter = parameter.isOutParameter,
        )
    }

    fun resolveMethod(
        method: WinRTMethodDefinition,
        currentNamespace: String,
    ): WinRTAbiMethodDescriptor =
        WinRTAbiMethodDescriptor(
            returnType = resolveType(method.returnType, currentNamespace),
            parameters = method.parameters.map { parameter -> resolveParameter(parameter, currentNamespace) },
        )

    companion object {
        fun create(model: WinRTMetadataModel): WinRTMetadataAbiResolver {
            return WinRTMetadataAbiResolver(model.typeClassifier())
        }
    }
}

fun WinRTMetadataModel.abiResolver(): WinRTMetadataAbiResolver = WinRTMetadataAbiResolver.create(this)

private fun abiCategoryFor(
    parameter: WinRTParameterDefinition,
    typeDescriptor: WinRTAbiTypeDescriptor,
): WinRTAbiParameterCategory {
    val metadataCategory = metadataParameterCategoryFor(parameter)
    return when (metadataCategory) {
        WinRTMetadataParameterCategory.In -> WinRTAbiParameterCategory.In
        WinRTMetadataParameterCategory.Ref -> WinRTAbiParameterCategory.Ref
        WinRTMetadataParameterCategory.Out -> WinRTAbiParameterCategory.Out
        WinRTMetadataParameterCategory.PassArray -> WinRTAbiParameterCategory.PassArray
        WinRTMetadataParameterCategory.FillArray -> WinRTAbiParameterCategory.FillArray
        WinRTMetadataParameterCategory.ReceiveArray -> WinRTAbiParameterCategory.ReceiveArray
    }
}
