package io.github.kitectlab.winrt.metadata

internal data class WinRtResolvedTypeReference(
    val type: WinRtTypeRef,
    val displayName: String,
    val definitionQualifiedName: String?,
    val definitionType: WinRtTypeDefinition?,
)

internal fun buildTypesByQualifiedName(model: WinRtMetadataModel): Map<String, WinRtTypeDefinition> {
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
    type: WinRtTypeRef,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): WinRtResolvedTypeReference {
    val normalizedType = type.normalized()
    val definitionQualifiedName = normalizedType.qualifiedName?.let { rawTypeName ->
        qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
    }
    return WinRtResolvedTypeReference(
        type = normalizedType,
        displayName = renderCanonicalDisplayName(normalizedType, currentNamespace, typesByQualifiedName),
        definitionQualifiedName = definitionQualifiedName,
        definitionType = definitionQualifiedName?.let(typesByQualifiedName::get),
    )
}

internal fun qualifyTypeName(
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

private fun renderCanonicalDisplayName(
    type: WinRtTypeRef,
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): String {
    val normalizedType = type.normalized()
    return when (normalizedType.kind) {
        WinRtTypeRefKind.Named -> {
            val qualifiedName = normalizedType.qualifiedName?.let { rawTypeName ->
                qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
            } ?: "Any"
            if (normalizedType.typeArguments.isEmpty()) {
                qualifiedName
            } else {
                "$qualifiedName<${normalizedType.typeArguments.joinToString(", ") { argument -> renderCanonicalDisplayName(argument, currentNamespace, typesByQualifiedName) }}>"
            }
        }

        WinRtTypeRefKind.Array -> "Array<${renderCanonicalDisplayName(normalizedType.elementType ?: WinRtTypeRef.unknown(), currentNamespace, typesByQualifiedName)}>"
        WinRtTypeRefKind.GenericTypeParameter,
        WinRtTypeRefKind.MethodTypeParameter,
        WinRtTypeRefKind.Unknown,
        -> normalizedType.typeName
    }
}
