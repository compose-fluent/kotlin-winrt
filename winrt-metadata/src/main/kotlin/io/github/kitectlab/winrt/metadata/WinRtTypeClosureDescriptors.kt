package io.github.kitectlab.winrt.metadata

enum class WinRtRuntimeClassInterfaceKind {
    Default,
    Implemented,
    Static,
    ActivatableFactory,
    ComposableFactory,
}

data class WinRtResolvedInterfaceDescriptor(
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRtTypeDefinition? = null,
    val depth: Int = 0,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
)

data class WinRtInterfaceClosureDescriptor(
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRtTypeDefinition? = null,
    val baseInterfaces: List<WinRtResolvedInterfaceDescriptor> = emptyList(),
)

data class WinRtRuntimeClassInterfaceDescriptor(
    val kind: WinRtRuntimeClassInterfaceKind,
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRtTypeDefinition? = null,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
    val closure: WinRtInterfaceClosureDescriptor? = null,
)

data class WinRtRuntimeClassActivationDescriptor(
    val isActivatable: Boolean = false,
    val activatableFactoryInterface: WinRtRuntimeClassInterfaceDescriptor? = null,
    val staticInterfaces: List<WinRtRuntimeClassInterfaceDescriptor> = emptyList(),
    val composableFactoryInterface: WinRtRuntimeClassInterfaceDescriptor? = null,
)

data class WinRtRuntimeClassClosureDescriptor(
    val qualifiedTypeName: String,
    val defaultInterfaceName: String? = null,
    val instanceInterfaces: List<WinRtRuntimeClassInterfaceDescriptor> = emptyList(),
    val instanceInterfaceClosure: List<WinRtResolvedInterfaceDescriptor> = emptyList(),
    val activation: WinRtRuntimeClassActivationDescriptor = WinRtRuntimeClassActivationDescriptor(),
)

class WinRtMetadataClosureResolver private constructor(
    private val typesByQualifiedName: Map<String, WinRtTypeDefinition>,
) {
    fun resolveInterface(type: WinRtTypeDefinition): WinRtInterfaceClosureDescriptor =
        resolveInterface(WinRtTypeRef.named(type.qualifiedName), type.namespace)

    fun resolveInterface(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): WinRtInterfaceClosureDescriptor {
        val resolvedInterface = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val baseInterfaces = collectInterfaceClosure(
            interfaceType = resolvedInterface.type,
            currentNamespace = currentNamespace,
            initialDepth = 1,
        )
        return WinRtInterfaceClosureDescriptor(
            interfaceName = resolvedInterface.displayName,
            interfaceType = resolvedInterface.type,
            definitionQualifiedName = resolvedInterface.definitionQualifiedName,
            definitionType = resolvedInterface.definitionType,
            baseInterfaces = baseInterfaces,
        )
    }

    fun resolveRuntimeClass(type: WinRtTypeDefinition): WinRtRuntimeClassClosureDescriptor {
        require(type.kind == WinRtTypeKind.RuntimeClass) {
            "Runtime-class closure can only be resolved for runtime classes: ${type.qualifiedName}"
        }
        val defaultInterface = type.defaultInterfaceName?.let { interfaceName ->
            resolveTypeReference(WinRtTypeRef.fromDisplayName(interfaceName), type.namespace, typesByQualifiedName)
        }
        val directInstanceInterfaces = orderedDirectInstanceInterfaces(type, defaultInterface?.displayName)
        val instanceInterfaces = directInstanceInterfaces.map { implemented ->
            createRuntimeClassInterfaceDescriptor(
                kind = if (implemented.isDefault) WinRtRuntimeClassInterfaceKind.Default else WinRtRuntimeClassInterfaceKind.Implemented,
                type = implemented.interfaceType,
                currentNamespace = type.namespace,
                isDefault = implemented.isDefault,
                isOverridable = implemented.isOverridable,
                isProtected = implemented.isProtected,
            )
        }
        return WinRtRuntimeClassClosureDescriptor(
            qualifiedTypeName = type.qualifiedName,
            defaultInterfaceName = defaultInterface?.displayName,
            instanceInterfaces = instanceInterfaces,
            instanceInterfaceClosure = collapseRuntimeClassInterfaceClosure(instanceInterfaces),
            activation = WinRtRuntimeClassActivationDescriptor(
                isActivatable = type.activation.isActivatable,
                activatableFactoryInterface =
                    type.activation.activatableFactoryInterface?.let { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRtRuntimeClassInterfaceKind.ActivatableFactory,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
                staticInterfaces =
                    type.activation.staticInterfaces.map { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRtRuntimeClassInterfaceKind.Static,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
                composableFactoryInterface =
                    type.activation.composableFactoryInterface?.let { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRtRuntimeClassInterfaceKind.ComposableFactory,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
            ),
        )
    }

    private fun orderedDirectInstanceInterfaces(
        type: WinRtTypeDefinition,
        resolvedDefaultInterfaceName: String?,
    ): List<WinRtInterfaceImplementationDefinition> {
        val directInterfacesByName = linkedMapOf<String, WinRtInterfaceImplementationDefinition>()
        if (resolvedDefaultInterfaceName != null) {
            val defaultImplementation = type.implementedInterfaces.firstOrNull { implemented ->
                resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName).displayName == resolvedDefaultInterfaceName
            } ?: WinRtInterfaceImplementationDefinition(interfaceName = resolvedDefaultInterfaceName, isDefault = true)
            directInterfacesByName[resolvedDefaultInterfaceName] = defaultImplementation.copy(
                interfaceName = resolvedDefaultInterfaceName,
                isDefault = true,
            )
        }
        type.implementedInterfaces.forEach { implemented ->
            val resolvedInterface = resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName)
            directInterfacesByName.putIfAbsent(
                resolvedInterface.displayName,
                implemented.copy(interfaceName = resolvedInterface.displayName),
            )
        }
        return directInterfacesByName.values.toList()
    }

    private fun collapseRuntimeClassInterfaceClosure(
        instanceInterfaces: List<WinRtRuntimeClassInterfaceDescriptor>,
    ): List<WinRtResolvedInterfaceDescriptor> {
        val seen = linkedSetOf<String>()
        return buildList {
            instanceInterfaces.forEach { interfaceDescriptor ->
                val selfDescriptor = interfaceDescriptor.toResolvedInterfaceDescriptor()
                if (seen.add(selfDescriptor.interfaceName)) {
                    add(selfDescriptor)
                }
                interfaceDescriptor.closure?.baseInterfaces?.forEach { baseInterface ->
                    if (seen.add(baseInterface.interfaceName)) {
                        add(baseInterface)
                    }
                }
            }
        }
    }

    private fun createRuntimeClassInterfaceDescriptor(
        kind: WinRtRuntimeClassInterfaceKind,
        type: WinRtTypeRef,
        currentNamespace: String,
        isDefault: Boolean = false,
        isOverridable: Boolean = false,
        isProtected: Boolean = false,
    ): WinRtRuntimeClassInterfaceDescriptor {
        val resolvedInterface = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val closure = resolveInterface(resolvedInterface.type, currentNamespace)
        return WinRtRuntimeClassInterfaceDescriptor(
            kind = kind,
            interfaceName = resolvedInterface.displayName,
            interfaceType = resolvedInterface.type,
            definitionQualifiedName = resolvedInterface.definitionQualifiedName,
            definitionType = resolvedInterface.definitionType,
            isDefault = isDefault,
            isOverridable = isOverridable,
            isProtected = isProtected,
            isExclusiveTo = resolvedInterface.definitionType?.isExclusiveTo ?: false,
            closure = closure,
        )
    }

    private fun collectInterfaceClosure(
        interfaceType: WinRtTypeRef,
        currentNamespace: String,
        initialDepth: Int,
    ): List<WinRtResolvedInterfaceDescriptor> {
        val seen = linkedSetOf<String>()
        return collectInterfaceClosure(
            interfaceType = interfaceType,
            currentNamespace = currentNamespace,
            depth = initialDepth,
            visiting = linkedSetOf(),
            seen = seen,
        )
    }

    private fun collectInterfaceClosure(
        interfaceType: WinRtTypeRef,
        currentNamespace: String,
        depth: Int,
        visiting: LinkedHashSet<String>,
        seen: LinkedHashSet<String>,
    ): List<WinRtResolvedInterfaceDescriptor> {
        val resolvedInterface = resolveTypeReference(interfaceType, currentNamespace, typesByQualifiedName)
        val definitionType = resolvedInterface.definitionType
        if (definitionType?.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val visitKey = resolvedInterface.displayName
        if (!visiting.add(visitKey)) {
            return emptyList()
        }
        try {
            return buildList {
                definitionType.implementedInterfaces.forEach { implemented ->
                    val substitutedInterface = implemented.interfaceType.substituteTypeParameters(
                        genericTypeArguments = resolvedInterface.type.typeArguments,
                    )
                    val resolvedBase = resolveTypeReference(substitutedInterface, definitionType.namespace, typesByQualifiedName)
                    if (seen.add(resolvedBase.displayName)) {
                        add(
                            WinRtResolvedInterfaceDescriptor(
                                interfaceName = resolvedBase.displayName,
                                interfaceType = resolvedBase.type,
                                definitionQualifiedName = resolvedBase.definitionQualifiedName,
                                definitionType = resolvedBase.definitionType,
                                depth = depth,
                                isExclusiveTo = resolvedBase.definitionType?.isExclusiveTo ?: false,
                            ),
                        )
                    }
                    addAll(
                        collectInterfaceClosure(
                            interfaceType = substitutedInterface,
                            currentNamespace = definitionType.namespace,
                            depth = depth + 1,
                            visiting = visiting,
                            seen = seen,
                        ),
                    )
                }
            }
        } finally {
            visiting.remove(visitKey)
        }
    }

    companion object {
        fun create(model: WinRtMetadataModel): WinRtMetadataClosureResolver =
            WinRtMetadataClosureResolver(buildTypesByQualifiedName(model))
    }
}

fun WinRtMetadataModel.closureResolver(): WinRtMetadataClosureResolver = WinRtMetadataClosureResolver.create(this)

private fun WinRtRuntimeClassInterfaceDescriptor.toResolvedInterfaceDescriptor(): WinRtResolvedInterfaceDescriptor =
    WinRtResolvedInterfaceDescriptor(
        interfaceName = interfaceName,
        interfaceType = interfaceType,
        definitionQualifiedName = definitionQualifiedName,
        definitionType = definitionType,
        depth = 0,
        isExclusiveTo = isExclusiveTo,
    )
