package io.github.composefluent.winrt.metadata

fun WinRTPropertyDefinition.projectedPropertyTypeName(
    ownerTypeName: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
): String {
    if (!isNullablePropertyProjection(ownerTypeName, typesByQualifiedName)) {
        return typeName
    }
    return typeName.trim().let { trimmed ->
        if (trimmed.endsWith("?")) trimmed else "$trimmed?"
    }
}

fun WinRTPropertyDefinition.isNullablePropertyProjection(
    ownerTypeName: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
): Boolean {
    val normalizedOwnerTypeName = ownerTypeName
        .substringBefore('<')
        .removeSuffix("?")
    val currentNamespace = normalizedOwnerTypeName.substringBeforeLast('.', "")
    return type.isNullableReferenceProjection(currentNamespace, typesByQualifiedName)
}
