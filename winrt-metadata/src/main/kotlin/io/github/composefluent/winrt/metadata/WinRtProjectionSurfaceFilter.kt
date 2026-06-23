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
        namespaces = namespaces,
        types = types,
        excludedNamespaces = excludedNamespaces,
        excludedTypes = excludedTypes,
        additionalTypeReferences = { emptyList() },
    )

fun WinRtMetadataModel.filterProjectionSurface(
    namespaces: Set<String> = emptySet(),
    types: Set<String> = emptySet(),
    excludedNamespaces: Set<String> = emptySet(),
    excludedTypes: Set<String> = emptySet(),
    additionalTypeReferences: (WinRtTypeRef) -> Iterable<WinRtTypeRef> = { emptyList() },
): WinRtMetadataModel =
    filterProjectionSurface(
        WinRtProjectionSurfaceFilter(
            namespaces = namespaces,
            types = types,
            excludedNamespaces = excludedNamespaces,
            excludedTypes = excludedTypes,
        ),
        additionalTypeReferences = additionalTypeReferences,
    )

fun WinRtMetadataModel.filterProjectionSurface(filter: WinRtProjectionSurfaceFilter): WinRtMetadataModel =
    filterProjectionSurface(
        filter = filter,
        additionalTypeReferences = { emptyList() },
    )

fun WinRtMetadataModel.filterProjectionSurface(
    filter: WinRtProjectionSurfaceFilter,
    additionalTypeReferences: (WinRtTypeRef) -> Iterable<WinRtTypeRef> = { emptyList() },
): WinRtMetadataModel {
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
        type.referencedProjectionTypeNames(typesByQualifiedName, additionalTypeReferences)
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
    val excluded = filter.excludedTypes.any { qualifiedName.isProjectionFilterMatch(it) } ||
        filter.excludedNamespaces.any { qualifiedName.isProjectionFilterMatch(it) }
    return included && !excluded
}

private fun WinRtProjectionSurfaceFilter.isProjectionDependencyExcluded(qualifiedName: String): Boolean =
    // Dependency closure is a Kotlin generator compile-surface boundary, not a
    // root projection selection rule. Keep referenced metadata available across
    // namespace excludes unless a concrete type is explicitly excluded.
    excludedTypes.any { qualifiedName.isProjectionFilterMatch(it) }

private fun WinRtTypeDefinition.referencedProjectionTypeNames(
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    additionalTypeReferences: (WinRtTypeRef) -> Iterable<WinRtTypeRef>,
): Set<String> = buildSet {
    fun addTypeRefWithAdditionalReferences(type: WinRtTypeRef) {
        addTypeRef(type)
        additionalTypeReferences(type).forEach(::addTypeRef)
    }

    fun addTypeNameWithAdditionalReferences(typeName: String) {
        addTypeRefWithAdditionalReferences(WinRtTypeRef.fromDisplayName(typeName))
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
