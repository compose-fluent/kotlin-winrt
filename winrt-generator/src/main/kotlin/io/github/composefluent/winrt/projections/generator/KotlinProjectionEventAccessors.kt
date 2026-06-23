package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtAttributedFactoryKind
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition

internal fun WinRtEventDefinition.hasNativeProjectionAccessorPair(): Boolean =
    hasValidAccessors &&
        (addMethodName != null || addMethodRowId != null) &&
        (removeMethodName != null || removeMethodRowId != null)

internal fun WinRtEventDefinition.hasNativeProjectionAddAccessor(): Boolean =
    hasValidAccessors && (addMethodName != null || addMethodRowId != null)

internal fun WinRtEventDefinition.hasNativeProjectionRemoveAccessor(): Boolean =
    hasValidAccessors && (removeMethodName != null || removeMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionGetterAccessor(): Boolean =
    hasValidAccessors && (getterMethodName != null || getterMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionSetterAccessor(): Boolean =
    hasValidAccessors && (setterMethodName != null || setterMethodRowId != null)

internal fun WinRtPropertyDefinition.hasNativeProjectionPropertyAccessor(): Boolean =
    hasNativeProjectionGetterAccessor() || hasNativeProjectionSetterAccessor()

internal data class WinRtPropertyGetterInterfaceResolution(
    val interfaceType: WinRtTypeDefinition,
    val fromBaseInterface: Boolean,
)

internal fun findNativeProjectionGetterInterface(
    setterInterfaceType: WinRtTypeDefinition,
    property: WinRtPropertyDefinition,
    typesByQualifiedName: Map<String, WinRtTypeDefinition>,
): WinRtPropertyGetterInterfaceResolution? {
    fun resolveInterface(typeName: String, currentNamespace: String): WinRtTypeDefinition? {
        val rawName = typeName.substringBefore('<').removeSuffix("?")
        return typesByQualifiedName[rawName]
            ?: typesByQualifiedName["$currentNamespace.$rawName"]
    }

    fun WinRtTypeDefinition.hasProjectedPropertyGetter(): Boolean =
        properties.any { candidate ->
            candidate.name == property.name && candidate.hasNativeProjectionPropertyAccessor()
        }

    fun searchInterfaces(
        type: WinRtTypeDefinition,
        fromBaseInterface: Boolean,
        visited: MutableSet<String> = mutableSetOf(),
    ): WinRtPropertyGetterInterfaceResolution? {
        if (!visited.add(type.qualifiedName)) return null
        type.implementedInterfaces.forEach { implemented ->
            val interfaceType = resolveInterface(implemented.interfaceName, type.namespace) ?: return@forEach
            if (interfaceType.qualifiedName != setterInterfaceType.qualifiedName && interfaceType.hasProjectedPropertyGetter()) {
                return WinRtPropertyGetterInterfaceResolution(interfaceType, fromBaseInterface)
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

    searchInterfaces(exclusiveToType, fromBaseInterface = false)?.let { return it }

    val staticAttributedInterfaces = buildList {
        exclusiveToType.activation.staticInterfaces.forEach { add(it.typeName) }
        exclusiveToType.activation.factories
            .filter { factory -> factory.kind == WinRtAttributedFactoryKind.Static }
            .mapNotNullTo(this) { factory -> factory.interfaceName }
    }
    staticAttributedInterfaces.forEach { interfaceName ->
        val interfaceType = resolveInterface(interfaceName, exclusiveToType.namespace) ?: return@forEach
        if (interfaceType.hasProjectedPropertyGetter()) {
            return WinRtPropertyGetterInterfaceResolution(interfaceType, fromBaseInterface = false)
        }
        searchInterfaces(interfaceType, fromBaseInterface = false)?.let { return it }
    }

    return null
}
