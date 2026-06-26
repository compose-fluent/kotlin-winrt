package io.github.composefluent.winrt.metadata

enum class WinRTRuntimeClassInterfaceKind {
    Default,
    Implemented,
    Static,
    ActivatableFactory,
    ComposableFactory,
}

data class WinRTResolvedInterfaceDescriptor(
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRTTypeDefinition? = null,
    val depth: Int = 0,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
    val availability: WinRTAvailabilityMetadata = definitionType?.availability ?: WinRTAvailabilityMetadata(),
)

data class WinRTInterfaceClosureDescriptor(
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRTTypeDefinition? = null,
    val baseInterfaces: List<WinRTResolvedInterfaceDescriptor> = emptyList(),
)

data class WinRTRuntimeClassInterfaceDescriptor(
    val kind: WinRTRuntimeClassInterfaceKind,
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val definitionQualifiedName: String? = interfaceType.qualifiedName,
    val definitionType: WinRTTypeDefinition? = null,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
    val isExclusiveTo: Boolean = definitionType?.isExclusiveTo ?: false,
    val availability: WinRTAvailabilityMetadata = definitionType?.availability ?: WinRTAvailabilityMetadata(),
    val closure: WinRTInterfaceClosureDescriptor? = null,
)

data class WinRTRuntimeClassActivationDescriptor(
    val isActivatable: Boolean = false,
    val activatableFactoryInterface: WinRTRuntimeClassInterfaceDescriptor? = null,
    val staticInterfaces: List<WinRTRuntimeClassInterfaceDescriptor> = emptyList(),
    val composableFactoryInterface: WinRTRuntimeClassInterfaceDescriptor? = null,
)

data class WinRTRuntimeClassClosureDescriptor(
    val qualifiedTypeName: String,
    val defaultInterfaceName: String? = null,
    val classHierarchyIndex: Int = 0,
    val isFastAbi: Boolean = false,
    val gcPressureAmount: Int = 0,
    val instanceInterfaces: List<WinRTRuntimeClassInterfaceDescriptor> = emptyList(),
    val fastAbiInterfaces: List<WinRTRuntimeClassInterfaceDescriptor> = emptyList(),
    val instanceInterfaceClosure: List<WinRTResolvedInterfaceDescriptor> = emptyList(),
    val activation: WinRTRuntimeClassActivationDescriptor = WinRTRuntimeClassActivationDescriptor(),
)

class WinRTMetadataClosureResolver private constructor(
    private val typesByQualifiedName: Map<String, WinRTTypeDefinition>,
) {
    fun resolveInterface(type: WinRTTypeDefinition): WinRTInterfaceClosureDescriptor =
        resolveInterface(WinRTTypeRef.named(type.qualifiedName), type.namespace)

    fun resolveInterface(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): WinRTInterfaceClosureDescriptor {
        val resolvedInterface = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val baseInterfaces = collectInterfaceClosure(
            interfaceType = resolvedInterface.type,
            currentNamespace = currentNamespace,
            initialDepth = 1,
        )
        return WinRTInterfaceClosureDescriptor(
            interfaceName = resolvedInterface.displayName,
            interfaceType = resolvedInterface.type,
            definitionQualifiedName = resolvedInterface.definitionQualifiedName,
            definitionType = resolvedInterface.definitionType,
            baseInterfaces = baseInterfaces,
        )
    }

    fun resolveRuntimeClass(type: WinRTTypeDefinition): WinRTRuntimeClassClosureDescriptor {
        require(type.kind == WinRTTypeKind.RuntimeClass) {
            "Runtime-class closure can only be resolved for runtime classes: ${type.qualifiedName}"
        }
        val defaultInterface = type.defaultInterfaceName?.let { interfaceName ->
            resolveTypeReference(WinRTTypeRef.fromDisplayName(interfaceName), type.namespace, typesByQualifiedName)
        }
        val directInstanceInterfaces = orderedDirectInstanceInterfaces(type, defaultInterface?.displayName)
        val instanceInterfaces = directInstanceInterfaces.map { implemented ->
            createRuntimeClassInterfaceDescriptor(
                kind = if (implemented.isDefault) WinRTRuntimeClassInterfaceKind.Default else WinRTRuntimeClassInterfaceKind.Implemented,
                type = implemented.interfaceType,
                currentNamespace = type.namespace,
                isDefault = implemented.isDefault,
                isOverridable = implemented.isOverridable,
                isProtected = implemented.isProtected,
            )
        }
        return WinRTRuntimeClassClosureDescriptor(
            qualifiedTypeName = type.qualifiedName,
            defaultInterfaceName = defaultInterface?.displayName,
            classHierarchyIndex = classHierarchyIndex(type),
            isFastAbi = type.isFastAbi,
            gcPressureAmount = type.gcPressureAmount,
            instanceInterfaces = instanceInterfaces,
            fastAbiInterfaces = if (type.isFastAbi) fastAbiInterfaces(instanceInterfaces) else emptyList(),
            instanceInterfaceClosure = collapseRuntimeClassInterfaceClosure(instanceInterfaces),
            activation = WinRTRuntimeClassActivationDescriptor(
                isActivatable = type.activation.isActivatable,
                activatableFactoryInterface =
                    type.activation.activatableFactoryInterface?.let { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRTRuntimeClassInterfaceKind.ActivatableFactory,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
                staticInterfaces =
                    type.activation.staticInterfaces.map { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRTRuntimeClassInterfaceKind.Static,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
                composableFactoryInterface =
                    type.activation.composableFactoryInterface?.let { interfaceType ->
                        createRuntimeClassInterfaceDescriptor(
                            kind = WinRTRuntimeClassInterfaceKind.ComposableFactory,
                            type = interfaceType,
                            currentNamespace = type.namespace,
                        )
                    },
            ),
        )
    }

    private fun orderedDirectInstanceInterfaces(
        type: WinRTTypeDefinition,
        resolvedDefaultInterfaceName: String?,
    ): List<WinRTInterfaceImplementationDefinition> {
        val directInterfacesByName = linkedMapOf<String, WinRTInterfaceImplementationDefinition>()
        if (resolvedDefaultInterfaceName != null) {
            val defaultImplementation = type.implementedInterfaces.firstOrNull { implemented ->
                resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName).displayName == resolvedDefaultInterfaceName
            } ?: WinRTInterfaceImplementationDefinition(interfaceName = resolvedDefaultInterfaceName, isDefault = true)
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
        instanceInterfaces: List<WinRTRuntimeClassInterfaceDescriptor>,
    ): List<WinRTResolvedInterfaceDescriptor> {
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
        kind: WinRTRuntimeClassInterfaceKind,
        type: WinRTTypeRef,
        currentNamespace: String,
        isDefault: Boolean = false,
        isOverridable: Boolean = false,
        isProtected: Boolean = false,
    ): WinRTRuntimeClassInterfaceDescriptor {
        val resolvedInterface = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val closure = resolveInterface(resolvedInterface.type, currentNamespace)
        return WinRTRuntimeClassInterfaceDescriptor(
            kind = kind,
            interfaceName = resolvedInterface.displayName,
            interfaceType = resolvedInterface.type,
            definitionQualifiedName = resolvedInterface.definitionQualifiedName,
            definitionType = resolvedInterface.definitionType,
            isDefault = isDefault,
            isOverridable = isOverridable,
            isProtected = isProtected,
            isExclusiveTo = resolvedInterface.definitionType?.isExclusiveTo ?: false,
            availability = resolvedInterface.definitionType?.availability ?: WinRTAvailabilityMetadata(),
            closure = closure,
        )
    }

    private fun collectInterfaceClosure(
        interfaceType: WinRTTypeRef,
        currentNamespace: String,
        initialDepth: Int,
    ): List<WinRTResolvedInterfaceDescriptor> {
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
        interfaceType: WinRTTypeRef,
        currentNamespace: String,
        depth: Int,
        visiting: LinkedHashSet<String>,
        seen: LinkedHashSet<String>,
    ): List<WinRTResolvedInterfaceDescriptor> {
        val resolvedInterface = resolveTypeReference(interfaceType, currentNamespace, typesByQualifiedName)
        val definitionType = resolvedInterface.definitionType
        if (definitionType?.kind != WinRTTypeKind.Interface) {
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
                            WinRTResolvedInterfaceDescriptor(
                                interfaceName = resolvedBase.displayName,
                                interfaceType = resolvedBase.type,
                                definitionQualifiedName = resolvedBase.definitionQualifiedName,
                                definitionType = resolvedBase.definitionType,
                                depth = depth,
                                isExclusiveTo = resolvedBase.definitionType?.isExclusiveTo ?: false,
                                availability = resolvedBase.definitionType?.availability ?: WinRTAvailabilityMetadata(),
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
        fun create(model: WinRTMetadataModel): WinRTMetadataClosureResolver =
            WinRTMetadataClosureResolver(buildTypesByQualifiedName(model))
    }

    private fun fastAbiInterfaces(
        instanceInterfaces: List<WinRTRuntimeClassInterfaceDescriptor>,
    ): List<WinRTRuntimeClassInterfaceDescriptor> {
        val defaultInterface = instanceInterfaces.firstOrNull(WinRTRuntimeClassInterfaceDescriptor::isDefault)
        val otherInterfaces = instanceInterfaces
            .filter { !it.isDefault && it.isExclusiveTo }
            .sortedWith(fastAbiInterfaceComparator())
        return listOfNotNull(defaultInterface) + otherInterfaces
    }

    private fun fastAbiInterfaceComparator(): Comparator<WinRTRuntimeClassInterfaceDescriptor> =
        compareBy<WinRTRuntimeClassInterfaceDescriptor> { -it.availability.previousContractVersions.size }
            .thenBy { it.availability.contractVersion?.version ?: Long.MAX_VALUE }
            .thenBy { it.availability.version ?: Long.MAX_VALUE }
            .thenBy { it.definitionType?.namespace ?: it.interfaceType.qualifiedName?.substringBeforeLast('.', "") }
            .thenBy { it.definitionType?.name ?: it.interfaceType.qualifiedName?.substringAfterLast('.') }

    private fun classHierarchyIndex(type: WinRTTypeDefinition): Int {
        var depth = 0
        var current = type
        val seen = linkedSetOf<String>()
        while (seen.add(current.qualifiedName)) {
            val baseName = current.baseTypeName ?: return depth
            val baseType = resolveTypeReference(WinRTTypeRef.fromDisplayName(baseName), current.namespace, typesByQualifiedName)
                .definitionType
                ?: return depth
            depth += 1
            current = baseType
        }
        return depth
    }
}

fun WinRTMetadataModel.closureResolver(): WinRTMetadataClosureResolver = WinRTMetadataClosureResolver.create(this)

private fun WinRTRuntimeClassInterfaceDescriptor.toResolvedInterfaceDescriptor(): WinRTResolvedInterfaceDescriptor =
    WinRTResolvedInterfaceDescriptor(
        interfaceName = interfaceName,
        interfaceType = interfaceType,
        definitionQualifiedName = definitionQualifiedName,
        definitionType = definitionType,
        depth = 0,
        isExclusiveTo = isExclusiveTo,
        availability = availability,
    )
