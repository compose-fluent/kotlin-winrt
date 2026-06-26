package io.github.composefluent.winrt.metadata

data class WinRTPropertyGetterInterfaceResolution(
    val interfaceType: WinRTTypeDefinition,
    val fromBaseInterface: Boolean,
    val dependencyTypeNames: Set<String>,
)

fun findNativeProjectionGetterInterface(
    setterInterfaceType: WinRTTypeDefinition,
    property: WinRTPropertyDefinition,
    typesByQualifiedName: Map<String, WinRTTypeDefinition>,
): WinRTPropertyGetterInterfaceResolution? {
    val dependencyTypeNames = linkedSetOf<String>()

    fun resolveInterface(typeName: String, currentNamespace: String): WinRTTypeDefinition? {
        val rawName = typeName.substringBefore('<').removeSuffix("?")
        return typesByQualifiedName[rawName]
            ?: typesByQualifiedName["$currentNamespace.$rawName"]
    }

    fun WinRTPropertyDefinition.hasProjectedPropertyAccessor(): Boolean =
        hasValidAccessors &&
            (
                getterMethodName != null ||
                    getterMethodRowId != null ||
                    setterMethodName != null ||
                    setterMethodRowId != null
                )

    fun WinRTTypeDefinition.hasProjectedProperty(property: WinRTPropertyDefinition): Boolean =
        properties.any { candidate -> candidate.name == property.name && candidate.hasProjectedPropertyAccessor() }

    fun found(
        interfaceType: WinRTTypeDefinition,
        fromBaseInterface: Boolean,
    ): WinRTPropertyGetterInterfaceResolution =
        WinRTPropertyGetterInterfaceResolution(
            interfaceType = interfaceType,
            fromBaseInterface = fromBaseInterface,
            dependencyTypeNames = dependencyTypeNames.toSet(),
        )

    fun searchInterfaces(
        type: WinRTTypeDefinition,
        fromBaseInterface: Boolean,
        visited: MutableSet<String> = mutableSetOf(),
    ): WinRTPropertyGetterInterfaceResolution? {
        if (!visited.add(type.qualifiedName)) return null
        type.implementedInterfaces.forEach { implemented ->
            val interfaceType = resolveInterface(implemented.interfaceName, type.namespace) ?: return@forEach
            dependencyTypeNames += interfaceType.qualifiedName
            if (interfaceType.qualifiedName != setterInterfaceType.qualifiedName && interfaceType.hasProjectedProperty(property)) {
                return found(interfaceType, fromBaseInterface)
            }
            searchInterfaces(interfaceType, fromBaseInterface, visited)?.let { return it }
        }
        return null
    }

    searchInterfaces(setterInterfaceType, fromBaseInterface = true)?.let { return it }

    val exclusiveToTypeName = setterInterfaceType.customAttributes
        .firstOrNull { it.typeName == "Windows.Foundation.Metadata.ExclusiveToAttribute" }
        ?.stringArguments
        ?.firstOrNull()
    val exclusiveToType = exclusiveToTypeName
        ?.let { resolveInterface(it, setterInterfaceType.namespace) }
        ?: typesByQualifiedName.values.firstOrNull { candidate ->
            candidate.implementedInterfaces.any { implemented ->
                resolveInterface(implemented.interfaceName, candidate.namespace)?.qualifiedName == setterInterfaceType.qualifiedName
            }
        }
        ?: return null
    dependencyTypeNames += exclusiveToType.qualifiedName

    searchInterfaces(exclusiveToType, fromBaseInterface = false)?.let { return it }

    val staticAttributedInterfaces = buildList {
        exclusiveToType.activation.staticInterfaces.forEach { add(it.typeName) }
        exclusiveToType.activation.factories
            .filter { factory -> factory.kind == WinRTAttributedFactoryKind.Static }
            .mapNotNullTo(this) { factory -> factory.interfaceName }
    }
    staticAttributedInterfaces.forEach { interfaceName ->
        val interfaceType = resolveInterface(interfaceName, exclusiveToType.namespace) ?: return@forEach
        dependencyTypeNames += interfaceType.qualifiedName
        if (interfaceType.hasProjectedProperty(property)) {
            return found(interfaceType, fromBaseInterface = false)
        }
        searchInterfaces(interfaceType, fromBaseInterface = false)?.let { return it }
    }

    return null
}
