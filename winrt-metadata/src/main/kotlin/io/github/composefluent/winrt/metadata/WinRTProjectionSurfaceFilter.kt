package io.github.composefluent.winrt.metadata

data class WinRTProjectionSurfaceFilter(
    val namespaces: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val excludedNamespaces: Set<String> = emptySet(),
    val excludedTypes: Set<String> = emptySet(),
) {
    fun normalized(): WinRTProjectionSurfaceFilter =
        copy(
            namespaces = namespaces.normalizedProjectionFilterSet(),
            types = types.normalizedProjectionFilterSet(),
            excludedNamespaces = excludedNamespaces.normalizedProjectionFilterSet(),
            excludedTypes = excludedTypes.normalizedProjectionFilterSet(),
        )
}

fun WinRTMetadataModel.filterProjectionSurface(
    namespaces: Set<String> = emptySet(),
    types: Set<String> = emptySet(),
    excludedNamespaces: Set<String> = emptySet(),
    excludedTypes: Set<String> = emptySet(),
): WinRTMetadataModel =
    filterProjectionSurface(
        namespaces = namespaces,
        types = types,
        excludedNamespaces = excludedNamespaces,
        excludedTypes = excludedTypes,
        additionalTypeReferences = { emptyList() },
    )

fun WinRTMetadataModel.filterProjectionSurface(
    namespaces: Set<String> = emptySet(),
    types: Set<String> = emptySet(),
    excludedNamespaces: Set<String> = emptySet(),
    excludedTypes: Set<String> = emptySet(),
    additionalTypeReferences: (WinRTTypeRef) -> Iterable<WinRTTypeRef> = { emptyList() },
): WinRTMetadataModel =
    filterProjectionSurface(
        WinRTProjectionSurfaceFilter(
            namespaces = namespaces,
            types = types,
            excludedNamespaces = excludedNamespaces,
            excludedTypes = excludedTypes,
        ),
        additionalTypeReferences = additionalTypeReferences,
    )

fun WinRTMetadataModel.filterProjectionSurface(filter: WinRTProjectionSurfaceFilter): WinRTMetadataModel =
    filterProjectionSurface(
        filter = filter,
        additionalTypeReferences = { emptyList() },
    )

fun WinRTMetadataModel.filterProjectionSurface(
    filter: WinRTProjectionSurfaceFilter,
    additionalTypeReferences: (WinRTTypeRef) -> Iterable<WinRTTypeRef> = { emptyList() },
): WinRTMetadataModel {
    val normalizedFilter = filter.normalized()
    if (
        normalizedFilter.namespaces.isEmpty() &&
        normalizedFilter.types.isEmpty() &&
        normalizedFilter.excludedNamespaces.isEmpty() &&
        normalizedFilter.excludedTypes.isEmpty()
    ) {
        return this
    }

    val typesByQualifiedName = namespaces
        .flatMap(WinRTNamespace::types)
        .associateBy(WinRTTypeDefinition::qualifiedName)
    val includedNames = linkedSetOf<String>()
    namespaces.forEach { namespace ->
        namespace.types.forEach { type ->
            if (type.isProjectionFilterRootIncluded(normalizedFilter)) {
                includedNames += type.qualifiedName
            }
        }
    }

    val pending = ArrayDeque(includedNames)
    while (pending.isNotEmpty()) {
        val type = typesByQualifiedName[pending.removeFirst()] ?: continue
        type.referencedProjectionTypeNames(typesByQualifiedName, additionalTypeReferences)
            .filter(typesByQualifiedName::containsKey)
            .filterNot(normalizedFilter::isProjectionDependencyExcluded)
            .forEach { referenced ->
                if (includedNames.add(referenced)) {
                    pending += referenced
                }
            }
    }

    return WinRTMetadataModel(
        namespaces.mapNotNull { namespace ->
            val namespaceTypes = namespace.types.filter { type -> type.qualifiedName in includedNames }
            namespaceTypes.takeIf { it.isNotEmpty() }?.let { WinRTNamespace(namespace.name, it) }
        },
    ).normalized()
}

private fun WinRTTypeDefinition.isProjectionFilterRootIncluded(filter: WinRTProjectionSurfaceFilter): Boolean {
    val qualifiedName = qualifiedName
    val hasIncludeFilter = filter.namespaces.isNotEmpty() || filter.types.isNotEmpty()
    val included = !hasIncludeFilter || filter.namespaces.matchesProjectionName(qualifiedName) ||
        filter.types.matchesProjectionName(qualifiedName)
    val excluded = filter.excludedTypes.matchesProjectionName(qualifiedName) ||
        filter.excludedNamespaces.matchesProjectionName(qualifiedName)
    return included && !excluded
}

private fun WinRTProjectionSurfaceFilter.isProjectionDependencyExcluded(qualifiedName: String): Boolean =
    // Keep referenced metadata across namespace excludes unless a concrete type is excluded.
    excludedTypes.matchesProjectionName(qualifiedName)

private fun WinRTTypeDefinition.referencedProjectionTypeNames(
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    additionalTypeReferences: (WinRTTypeRef) -> Iterable<WinRTTypeRef>,
): Set<String> = buildSet {
    fun addTypeRefWithAdditionalReferences(type: WinRTTypeRef) {
        addTypeRef(type)
        additionalTypeReferences(type).forEach(::addTypeRef)
    }

    fun addTypeNameWithAdditionalReferences(typeName: String) {
        addTypeRefWithAdditionalReferences(WinRTTypeRef.fromDisplayName(typeName))
    }

    baseType?.let(::addTypeRefWithAdditionalReferences)
    defaultInterface?.let(::addTypeRefWithAdditionalReferences)
    implementedInterfaces.forEach { addTypeRefWithAdditionalReferences(it.interfaceType) }
    genericParameters.forEach { parameter ->
        parameter.constraintTypes.forEach(::addTypeRefWithAdditionalReferences)
    }
    customAttributes.forEach(::addCustomAttributeRefs)
    fields.forEach { field ->
        addTypeRefWithAdditionalReferences(field.type)
        addTypeNameWithAdditionalReferences(field.typeName)
    }
    methods.forEach { method ->
        addTypeRefWithAdditionalReferences(method.returnType)
        addTypeNameWithAdditionalReferences(method.returnTypeName)
        method.parameters.forEach { parameter ->
            addTypeRefWithAdditionalReferences(parameter.type)
            addTypeNameWithAdditionalReferences(parameter.typeName)
        }
        method.returnParameterAttributes.forEach(::addCustomAttributeRefs)
    }
    properties.forEach { property ->
        addTypeRefWithAdditionalReferences(property.type)
        addTypeNameWithAdditionalReferences(property.typeName)
    }
    events.forEach { event ->
        addTypeRefWithAdditionalReferences(event.delegateType)
        addTypeNameWithAdditionalReferences(event.delegateTypeName)
    }
    forwardedProjectionDependencyTypeNames(typesByQualifiedName).forEach(::add)
    activation.activatableFactoryInterface?.let(::addTypeRefWithAdditionalReferences)
    activation.staticInterfaces.forEach(::addTypeRefWithAdditionalReferences)
    activation.composableFactoryInterface?.let(::addTypeRefWithAdditionalReferences)
    activation.factories.forEach { addTypeRefWithAdditionalReferences(it.interfaceType) }
}

private fun MutableSet<String>.addCustomAttributeRefs(attribute: WinRTCustomAttributeDefinition) {
    addTypeRef(WinRTTypeRef.fromDisplayName(attribute.typeName))
    attribute.fixedArguments.forEach(::addCustomAttributeValueRefs)
    attribute.namedArguments.forEach { argument -> addCustomAttributeValueRefs(argument.value) }
}

private fun MutableSet<String>.addCustomAttributeValueRefs(value: WinRTCustomAttributeValue) {
    when (value) {
        is WinRTCustomAttributeValue.TypeValue -> addTypeRef(WinRTTypeRef.fromDisplayName(value.typeName))
        is WinRTCustomAttributeValue.EnumValue -> addTypeRef(WinRTTypeRef.fromDisplayName(value.enumTypeName))
        is WinRTCustomAttributeValue.ArrayValue -> value.values.forEach(::addCustomAttributeValueRefs)
        is WinRTCustomAttributeValue.BooleanValue,
        is WinRTCustomAttributeValue.FloatingPointValue,
        is WinRTCustomAttributeValue.IntegralValue,
        WinRTCustomAttributeValue.NullValue,
        is WinRTCustomAttributeValue.StringValue -> Unit
    }
}

private fun MutableSet<String>.addTypeRef(type: WinRTTypeRef) {
    when (type.kind) {
        WinRTTypeRefKind.Named -> {
            type.qualifiedName?.let(::add)
            type.typeArguments.forEach(::addTypeRef)
        }
        WinRTTypeRefKind.Array -> type.elementType?.let(::addTypeRef)
        WinRTTypeRefKind.GenericTypeParameter,
        WinRTTypeRefKind.MethodTypeParameter,
        WinRTTypeRefKind.Unknown -> Unit
    }
}

/** Match exact names or dotted ancestors without scanning every declared identity. */
private fun Set<String>.matchesProjectionName(qualifiedName: String): Boolean {
    if (isEmpty()) return false
    if (qualifiedName in this) return true
    var boundary = qualifiedName.lastIndexOf('.')
    while (boundary >= 0) {
        if (qualifiedName.substring(0, boundary) in this) return true
        boundary = qualifiedName.lastIndexOf('.', boundary - 1)
    }
    return false
}

private fun Set<String>.normalizedProjectionFilterSet(): Set<String> =
    map(String::trim).filter(String::isNotEmpty).toSortedSet()
