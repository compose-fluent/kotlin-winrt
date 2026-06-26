package io.github.composefluent.winrt.metadata

fun WinRTTypeDefinition.forwardedProjectionDependencyTypeNames(
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
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

private fun WinRTPropertyDefinition.forwardedProjectionDependencyTypeNames(
    ownerType: WinRTTypeDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
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

private fun WinRTEventDefinition.forwardedProjectionDependencyTypeNames(): Set<String> =
    emptySet()
