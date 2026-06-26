package io.github.composefluent.winrt.metadata

data class WinRTResolvedTypeReference(
    val type: WinRTTypeRef,
    val displayName: String,
    val definitionQualifiedName: String?,
    val definitionType: WinRTTypeDefinition?,
)

internal fun buildTypesByQualifiedName(model: WinRTMetadataModel): Map<String, WinRTTypeDefinition> {
    val normalizedModel = model.normalized()
    return buildMap {
        normalizedModel.namespaces.forEach { namespace ->
            namespace.types.forEach { type ->
                put(type.qualifiedName, type)
            }
        }
    }
}

internal fun resolveTypeReference(
    type: WinRTTypeRef,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): WinRTResolvedTypeReference {
    val normalizedType = type.normalized()
    val definitionQualifiedName = normalizedType.qualifiedName?.let { rawTypeName ->
        qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
    }
    return WinRTResolvedTypeReference(
        type = normalizedType,
        displayName = renderCanonicalDisplayName(normalizedType, currentNamespace, typesByQualifiedName),
        definitionQualifiedName = definitionQualifiedName,
        definitionType = definitionQualifiedName?.let(typesByQualifiedName::get),
    )
}

internal fun qualifyTypeName(
    rawTypeName: String,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
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

private fun renderCanonicalDisplayName(
    type: WinRTTypeRef,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): String {
    val normalizedType = type.normalized()
    return when (normalizedType.kind) {
        WinRTTypeRefKind.Named -> {
            val qualifiedName = normalizedType.qualifiedName?.let { rawTypeName ->
                qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
            } ?: "Any"
            if (normalizedType.typeArguments.isEmpty()) {
                qualifiedName
            } else {
                "$qualifiedName<${normalizedType.typeArguments.joinToString(", ") { argument -> renderCanonicalDisplayName(argument, currentNamespace, typesByQualifiedName) }}>"
            }
        }

        WinRTTypeRefKind.Array -> "Array<${renderCanonicalDisplayName(normalizedType.elementType ?: WinRTTypeRef.unknown(), currentNamespace, typesByQualifiedName)}>"
        WinRTTypeRefKind.GenericTypeParameter,
        WinRTTypeRefKind.MethodTypeParameter,
        WinRTTypeRefKind.Unknown,
        -> normalizedType.typeName
    }
}
