package io.github.composefluent.winrt.metadata

fun WinRtTypeDefinition.forwardedProjectionDependencyTypeNames(
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): Set<String> = buildSet {
    properties.forEach { property ->
        property.forwardedProjectionDependencyTypeNames(
            ownerType = this@forwardedProjectionDependencyTypeNames,
            typesByQualifiedName = typesByQualifiedName,
        ).forEach(::add)
    }
    events.forEach { event ->
        event.forwardedProjectionDependencyTypeNames().forEach(::add)
    }
}

private fun WinRtPropertyDefinition.forwardedProjectionDependencyTypeNames(
    ownerType: WinRtTypeDefinition,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): Set<String> {
    val hasGetter = getterMethodName != null || getterMethodRowId != null
    val hasSetter = setterMethodName != null || setterMethodRowId != null
    if (hasGetter || !hasSetter) {
        return emptySet()
    }
    return findNativeProjectionGetterInterface(
        setterInterfaceType = ownerType,
        property = this,
        typesByQualifiedName = typesByQualifiedName,
    )?.dependencyTypeNames.orEmpty()
}

private fun WinRtEventDefinition.forwardedProjectionDependencyTypeNames(): Set<String> =
    emptySet()
