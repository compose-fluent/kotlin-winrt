package io.github.kitectlab.winrt.metadata

import io.github.kitectlab.winrt.runtime.Guid

enum class WinRtTypeKind {
    Unknown,
    Interface,
    RuntimeClass,
    Enum,
    Struct,
    Delegate,
}

data class WinRtInterfaceImplementationDefinition(
    val interfaceName: String,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
) {
    fun normalized(): WinRtInterfaceImplementationDefinition = copy(interfaceName = interfaceName.trim())

    internal fun merge(other: WinRtInterfaceImplementationDefinition): WinRtInterfaceImplementationDefinition {
        require(interfaceName == other.interfaceName) {
            "Can only merge identical interface implementations: $interfaceName vs ${other.interfaceName}"
        }
        return WinRtInterfaceImplementationDefinition(
            interfaceName = interfaceName,
            isDefault = isDefault || other.isDefault,
            isOverridable = isOverridable || other.isOverridable,
            isProtected = isProtected || other.isProtected,
        )
    }
}

data class WinRtActivationShape(
    val isActivatable: Boolean = false,
    val activatableFactoryInterfaceName: String? = null,
    val staticInterfaceNames: List<String> = emptyList(),
    val composableFactoryInterfaceName: String? = null,
) {
    fun normalized(): WinRtActivationShape = copy(
        activatableFactoryInterfaceName = activatableFactoryInterfaceName?.trim(),
        staticInterfaceNames = staticInterfaceNames
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted(),
        composableFactoryInterfaceName = composableFactoryInterfaceName?.trim(),
    )

    internal fun merge(other: WinRtActivationShape): WinRtActivationShape {
        val left = normalized()
        val right = other.normalized()
        return WinRtActivationShape(
            isActivatable = left.isActivatable || right.isActivatable,
            activatableFactoryInterfaceName = left.activatableFactoryInterfaceName ?: right.activatableFactoryInterfaceName,
            staticInterfaceNames = (left.staticInterfaceNames + right.staticInterfaceNames).distinct().sorted(),
            composableFactoryInterfaceName = left.composableFactoryInterfaceName ?: right.composableFactoryInterfaceName,
        )
    }
}

enum class WinRtParameterDirection {
    In,
    Ref,
    Out,
}

data class WinRtParameterDefinition(
    val name: String,
    val typeName: String,
    val direction: WinRtParameterDirection = WinRtParameterDirection.In,
) {
    fun normalized(): WinRtParameterDefinition = copy(
        name = name.trim(),
        typeName = typeName.trim(),
    )

    internal fun signatureKey(): String = "$name:$typeName:$direction"
}

data class WinRtMethodDefinition(
    val name: String,
    val returnTypeName: String,
    val parameters: List<WinRtParameterDefinition> = emptyList(),
    val isStatic: Boolean = false,
) {
    fun normalized(): WinRtMethodDefinition = copy(
        name = name.trim(),
        returnTypeName = returnTypeName.trim(),
        parameters = parameters.map(WinRtParameterDefinition::normalized),
    )

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(returnTypeName)
        append('|')
        append(parameters.joinToString(",") { it.signatureKey() })
    }
}

data class WinRtPropertyDefinition(
    val name: String,
    val typeName: String,
    val isStatic: Boolean = false,
    val getterMethodName: String? = null,
    val setterMethodName: String? = null,
) {
    val isReadOnly: Boolean
        get() = setterMethodName == null

    fun normalized(): WinRtPropertyDefinition = copy(
        name = name.trim(),
        typeName = typeName.trim(),
        getterMethodName = getterMethodName?.trim(),
        setterMethodName = setterMethodName?.trim(),
    )

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(typeName)
    }

    internal fun merge(other: WinRtPropertyDefinition): WinRtPropertyDefinition {
        require(name == other.name && typeName == other.typeName) {
            "Can only merge identical properties: $name:$typeName vs ${other.name}:${other.typeName}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRtPropertyDefinition(
            name = left.name,
            typeName = left.typeName,
            isStatic = left.isStatic || right.isStatic,
            getterMethodName = left.getterMethodName ?: right.getterMethodName,
            setterMethodName = left.setterMethodName ?: right.setterMethodName,
        )
    }
}

data class WinRtEventDefinition(
    val name: String,
    val delegateTypeName: String,
    val isStatic: Boolean = false,
    val addMethodName: String? = null,
    val removeMethodName: String? = null,
) {
    fun normalized(): WinRtEventDefinition = copy(
        name = name.trim(),
        delegateTypeName = delegateTypeName.trim(),
        addMethodName = addMethodName?.trim(),
        removeMethodName = removeMethodName?.trim(),
    )

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(delegateTypeName)
    }

    internal fun merge(other: WinRtEventDefinition): WinRtEventDefinition {
        require(name == other.name && delegateTypeName == other.delegateTypeName) {
            "Can only merge identical events: $name:$delegateTypeName vs ${other.name}:${other.delegateTypeName}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRtEventDefinition(
            name = left.name,
            delegateTypeName = left.delegateTypeName,
            isStatic = left.isStatic || right.isStatic,
            addMethodName = left.addMethodName ?: right.addMethodName,
            removeMethodName = left.removeMethodName ?: right.removeMethodName,
        )
    }
}

data class WinRtTypeDefinition(
    val namespace: String,
    val name: String,
    val kind: WinRtTypeKind = WinRtTypeKind.Unknown,
    val iid: Guid? = null,
    val baseTypeName: String? = null,
    val defaultInterfaceName: String? = null,
    val implementedInterfaces: List<WinRtInterfaceImplementationDefinition> = emptyList(),
    val genericParameterCount: Int = 0,
    val activation: WinRtActivationShape = WinRtActivationShape(),
    val methods: List<WinRtMethodDefinition> = emptyList(),
    val properties: List<WinRtPropertyDefinition> = emptyList(),
    val events: List<WinRtEventDefinition> = emptyList(),
) {
    val qualifiedName: String
        get() = if (namespace.isBlank()) name else "$namespace.$name"

    fun normalized(): WinRtTypeDefinition {
        val normalizedMethods = methods
            .map(WinRtMethodDefinition::normalized)
            .sortedWith(compareBy(WinRtMethodDefinition::signatureKey))
            .distinctBy(WinRtMethodDefinition::signatureKey)
        val normalizedProperties = properties
            .map(WinRtPropertyDefinition::normalized)
            .sortedWith(compareBy(WinRtPropertyDefinition::signatureKey))
            .distinctBy(WinRtPropertyDefinition::signatureKey)
        val normalizedEvents = events
            .map(WinRtEventDefinition::normalized)
            .sortedWith(compareBy(WinRtEventDefinition::signatureKey))
            .distinctBy(WinRtEventDefinition::signatureKey)

        return copy(
            namespace = namespace.trim(),
            name = name.trim(),
            baseTypeName = baseTypeName?.trim(),
            defaultInterfaceName = defaultInterfaceName?.trim(),
            implementedInterfaces = implementedInterfaces
                .map(WinRtInterfaceImplementationDefinition::normalized)
                .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName),
            genericParameterCount = genericParameterCount.coerceAtLeast(0),
            activation = activation.normalized(),
            methods = normalizedMethods,
            properties = normalizedProperties,
            events = normalizedEvents,
        )
    }

    internal fun merge(other: WinRtTypeDefinition): WinRtTypeDefinition {
        require(namespace == other.namespace && name == other.name) {
            "Can only merge identical qualified type names: $qualifiedName vs ${other.qualifiedName}"
        }

        val left = normalized()
        val right = other.normalized()
        return WinRtTypeDefinition(
            namespace = left.namespace,
            name = left.name,
            kind = mergeKind(left.kind, right.kind),
            iid = left.iid ?: right.iid,
            baseTypeName = left.baseTypeName ?: right.baseTypeName,
            defaultInterfaceName = left.defaultInterfaceName ?: right.defaultInterfaceName,
            implementedInterfaces = (left.implementedInterfaces + right.implementedInterfaces)
                .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName),
            genericParameterCount = maxOf(left.genericParameterCount, right.genericParameterCount),
            activation = left.activation.merge(right.activation),
            methods = (left.methods + right.methods)
                .sortedWith(compareBy(WinRtMethodDefinition::signatureKey))
                .distinctBy(WinRtMethodDefinition::signatureKey),
            properties = (left.properties + right.properties)
                .groupBy(WinRtPropertyDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRtPropertyDefinition::merge) }
                .sortedWith(compareBy(WinRtPropertyDefinition::signatureKey)),
            events = (left.events + right.events)
                .groupBy(WinRtEventDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRtEventDefinition::merge) }
                .sortedWith(compareBy(WinRtEventDefinition::signatureKey)),
        )
    }
}

data class WinRtNamespace(
    val name: String,
    val types: List<WinRtTypeDefinition>,
) {
    fun normalized(): WinRtNamespace =
        copy(
            name = name.trim(),
            types = types
                .map(WinRtTypeDefinition::normalized)
                .groupBy { it.name }
                .values
                .map { duplicates -> duplicates.reduce(WinRtTypeDefinition::merge) }
                .sortedWith(compareBy(WinRtTypeDefinition::name, { it.kind.ordinal }, { it.iid?.toString().orEmpty() })),
        )
}

data class WinRtMetadataModel(
    val namespaces: List<WinRtNamespace>,
) {
    fun normalized(): WinRtMetadataModel =
        copy(
            namespaces = namespaces
                .map(WinRtNamespace::normalized)
                .groupBy { it.name }
                .values
                .map { duplicates ->
                    WinRtNamespace(
                        name = duplicates.first().name,
                        types = duplicates
                            .flatMap(WinRtNamespace::types)
                            .groupBy { it.name }
                            .values
                            .map { typeDuplicates -> typeDuplicates.reduce(WinRtTypeDefinition::merge) }
                            .sortedWith(compareBy(WinRtTypeDefinition::name, { it.kind.ordinal }, { it.iid?.toString().orEmpty() })),
                    )
                }
                .sortedBy(WinRtNamespace::name),
        )
}

private fun mergeKind(left: WinRtTypeKind, right: WinRtTypeKind): WinRtTypeKind = when {
    left == right -> left
    left == WinRtTypeKind.Unknown -> right
    right == WinRtTypeKind.Unknown -> left
    else -> minOf(left, right, compareBy(WinRtTypeKind::ordinal))
}
