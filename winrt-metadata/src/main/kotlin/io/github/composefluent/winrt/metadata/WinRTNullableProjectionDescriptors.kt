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
    return type.isNullableWinRTPropertyReference(currentNamespace, typesByQualifiedName)
}

private fun WinRTTypeRef.isNullableWinRTPropertyReference(
    currentNamespace: String,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): Boolean {
    val normalized = normalized()
    if (normalized.kind != WinRTTypeRefKind.Named || normalized.typeArguments.isNotEmpty()) {
        return false
    }
    val rawTypeName = normalized.qualifiedName ?: normalized.typeName
    if (rawTypeName.isNonNullableXamlPropertyRuntimeClassTypeName()) {
        return false
    }
    if (isWinRTObjectTypeName(rawTypeName)) {
        return true
    }
    val resolvedType = resolveTypeReference(normalized, currentNamespace, typesByQualifiedName).definitionType
    return resolvedType?.kind in setOf(
        WinRTTypeKind.Interface,
        WinRTTypeKind.Delegate,
        WinRTTypeKind.RuntimeClass,
    )
}

private fun String.isXamlDependencyPropertyTypeName(): Boolean =
    this == "Microsoft.UI.Xaml.DependencyProperty" ||
        this == "Windows.UI.Xaml.DependencyProperty"

private fun String.isNonNullableXamlPropertyRuntimeClassTypeName(): Boolean =
    isXamlDependencyPropertyTypeName() || isXamlCollectionRuntimeClassTypeName()

private fun String.isXamlCollectionRuntimeClassTypeName(): Boolean {
    if (!startsWith("Microsoft.UI.Xaml.") && !startsWith("Windows.UI.Xaml.")) {
        return false
    }
    val simpleName = substringAfterLast('.')
    return simpleName.endsWith("Collection") || simpleName == "ResourceDictionary"
}
