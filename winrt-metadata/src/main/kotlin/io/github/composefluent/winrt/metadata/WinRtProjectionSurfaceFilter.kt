package io.github.composefluent.winrt.metadata

data class WinRtProjectionSurfaceFilter(
    val namespaces: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val excludedNamespaces: Set<String> = emptySet(),
    val excludedTypes: Set<String> = emptySet(),
) {
    fun normalized(): WinRtProjectionSurfaceFilter =
        copy(
            namespaces = namespaces.normalizedProjectionFilterSet(),
            types = types.normalizedProjectionFilterSet(),
            excludedNamespaces = excludedNamespaces.normalizedProjectionFilterSet(),
            excludedTypes = excludedTypes.normalizedProjectionFilterSet(),
        )
}

fun WinRtMetadataModel.filterProjectionSurface(
    namespaces: Set<String> = emptySet(),
    types: Set<String> = emptySet(),
    excludedNamespaces: Set<String> = emptySet(),
    excludedTypes: Set<String> = emptySet(),
): WinRtMetadataModel =
    filterProjectionSurface(
        WinRtProjectionSurfaceFilter(
            namespaces = namespaces,
            types = types,
            excludedNamespaces = excludedNamespaces,
            excludedTypes = excludedTypes,
        ),
    )

fun WinRtMetadataModel.filterProjectionSurface(filter: WinRtProjectionSurfaceFilter): WinRtMetadataModel {
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
        .flatMap(WinRtNamespace::types)
        .associateBy(WinRtTypeDefinition::qualifiedName)
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
        type.referencedProjectionTypeNames()
            .filter(typesByQualifiedName::containsKey)
            .filterNot(normalizedFilter::isProjectionDependencyExcluded)
            .forEach { referenced ->
                if (includedNames.add(referenced)) {
                    pending += referenced
                }
            }
    }

    return WinRtMetadataModel(
        namespaces.mapNotNull { namespace ->
            val namespaceTypes = namespace.types.filter { type -> type.qualifiedName in includedNames }
            namespaceTypes.takeIf { it.isNotEmpty() }?.let { WinRtNamespace(namespace.name, it) }
        },
    ).normalized()
}

private fun WinRtTypeDefinition.isProjectionFilterRootIncluded(filter: WinRtProjectionSurfaceFilter): Boolean {
    val hasIncludeFilter = filter.namespaces.isNotEmpty() || filter.types.isNotEmpty()
    val explicitlyIncludedType = filter.types.any { qualifiedName.isProjectionFilterMatch(it) }
    val included = !hasIncludeFilter ||
        filter.namespaces.any { qualifiedName.isProjectionFilterMatch(it) } ||
        explicitlyIncludedType
    val explicitlyExcludedType = filter.excludedTypes.any { qualifiedName.isProjectionFilterMatch(it) }
    val namespaceExcludeOverridden = filter.excludedNamespaces.any { excludedNamespace ->
        filter.namespaces.any { includedNamespace ->
            includedNamespace.startsWith("$excludedNamespace.") &&
                qualifiedName.isProjectionFilterMatch(includedNamespace)
        }
    }
    val excluded = explicitlyExcludedType ||
        (
            !explicitlyIncludedType &&
                !namespaceExcludeOverridden &&
                filter.excludedNamespaces.any { qualifiedName.isProjectionFilterMatch(it) }
            )
    return included && !excluded
}

private fun WinRtProjectionSurfaceFilter.isProjectionDependencyExcluded(qualifiedName: String): Boolean =
    excludedTypes.any { qualifiedName.isProjectionFilterMatch(it) }

private fun WinRtTypeDefinition.referencedProjectionTypeNames(): Set<String> = buildSet {
    baseType?.let(::addTypeRef)
    defaultInterface?.let(::addTypeRef)
    implementedInterfaces.forEach { addTypeRef(it.interfaceType) }
    genericParameters.forEach { parameter ->
        parameter.constraintTypes.forEach(::addTypeRef)
    }
    customAttributes.forEach(::addCustomAttributeRefs)
    fields.forEach { addTypeRef(it.type) }
    methods.forEach { method ->
        addTypeRef(method.returnType)
        method.parameters.forEach { addTypeRef(it.type) }
        method.returnParameterAttributes.forEach(::addCustomAttributeRefs)
    }
    properties.forEach { addTypeRef(it.type) }
    events.forEach { addTypeRef(it.delegateType) }
    activation.activatableFactoryInterface?.let(::addTypeRef)
    activation.staticInterfaces.forEach(::addTypeRef)
    activation.composableFactoryInterface?.let(::addTypeRef)
    activation.factories.forEach { addTypeRef(it.interfaceType) }
}

private fun MutableSet<String>.addCustomAttributeRefs(attribute: WinRtCustomAttributeDefinition) {
    addTypeRef(WinRtTypeRef.fromDisplayName(attribute.typeName))
    attribute.fixedArguments.forEach(::addCustomAttributeValueRefs)
    attribute.namedArguments.forEach { argument -> addCustomAttributeValueRefs(argument.value) }
}

private fun MutableSet<String>.addCustomAttributeValueRefs(value: WinRtCustomAttributeValue) {
    when (value) {
        is WinRtCustomAttributeValue.TypeValue -> addTypeRef(WinRtTypeRef.fromDisplayName(value.typeName))
        is WinRtCustomAttributeValue.EnumValue -> addTypeRef(WinRtTypeRef.fromDisplayName(value.enumTypeName))
        is WinRtCustomAttributeValue.ArrayValue -> value.values.forEach(::addCustomAttributeValueRefs)
        is WinRtCustomAttributeValue.BooleanValue,
        is WinRtCustomAttributeValue.FloatingPointValue,
        is WinRtCustomAttributeValue.IntegralValue,
        WinRtCustomAttributeValue.NullValue,
        is WinRtCustomAttributeValue.StringValue -> Unit
    }
}

private fun MutableSet<String>.addTypeRef(type: WinRtTypeRef) {
    when (type.kind) {
        WinRtTypeRefKind.Named -> {
            type.qualifiedName?.let(::add)
            type.typeArguments.forEach(::addTypeRef)
        }
        WinRtTypeRefKind.Array -> type.elementType?.let(::addTypeRef)
        WinRtTypeRefKind.GenericTypeParameter,
        WinRtTypeRefKind.MethodTypeParameter,
        WinRtTypeRefKind.Unknown -> Unit
    }
}

private fun String.isProjectionFilterMatch(prefixOrType: String): Boolean =
    this == prefixOrType || startsWith("$prefixOrType.")

private fun Set<String>.normalizedProjectionFilterSet(): Set<String> =
    map(String::trim).filter(String::isNotEmpty).toSortedSet()
