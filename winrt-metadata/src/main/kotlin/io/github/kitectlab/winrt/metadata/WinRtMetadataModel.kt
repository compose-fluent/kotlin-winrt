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

enum class WinRtIntegralType {
    Int8,
    UInt8,
    Int16,
    UInt16,
    Int32,
    UInt32,
    Int64,
    UInt64,
}

data class WinRtEnumMemberDefinition(
    val name: String,
    val valueBits: ULong,
) {
    fun normalized(): WinRtEnumMemberDefinition = copy(name = name.trim())

    internal fun merge(other: WinRtEnumMemberDefinition): WinRtEnumMemberDefinition {
        require(name == other.name) {
            "Can only merge identical enum members: $name vs ${other.name}"
        }
        require(valueBits == other.valueBits) {
            "Can only merge enum members with identical values: $name=$valueBits vs ${other.name}=${other.valueBits}"
        }
        return normalized()
    }
}

data class WinRtInterfaceImplementationDefinition(
    val interfaceName: String,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
) {
    val interfaceType: WinRtTypeRef
        get() = WinRtTypeRef.fromDisplayName(interfaceName)

    fun normalized(): WinRtInterfaceImplementationDefinition {
        val normalizedInterfaceType = interfaceType.normalized()
        return copy(interfaceName = normalizedInterfaceType.typeName)
    }

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
    val activatableFactoryInterface: WinRtTypeRef?
        get() = activatableFactoryInterfaceName?.let(WinRtTypeRef::fromDisplayName)

    val staticInterfaces: List<WinRtTypeRef>
        get() = staticInterfaceNames.map(WinRtTypeRef::fromDisplayName)

    val composableFactoryInterface: WinRtTypeRef?
        get() = composableFactoryInterfaceName?.let(WinRtTypeRef::fromDisplayName)

    fun normalized(): WinRtActivationShape {
        val normalizedStaticInterfaces = staticInterfaces
            .map(WinRtTypeRef::normalized)
            .distinctBy(WinRtTypeRef::typeName)
            .sortedBy(WinRtTypeRef::typeName)
        return copy(
            activatableFactoryInterfaceName = activatableFactoryInterface?.normalized()?.typeName,
            staticInterfaceNames = normalizedStaticInterfaces.map(WinRtTypeRef::typeName),
            composableFactoryInterfaceName = composableFactoryInterface?.normalized()?.typeName,
        )
    }

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
    val typeIsByRef: Boolean = false,
    val isInParameter: Boolean = false,
    val isOutParameter: Boolean = false,
) {
    val type: WinRtTypeRef
        get() = WinRtTypeRef.fromDisplayName(typeName).withByRef(typeIsByRef)

    fun normalized(): WinRtParameterDefinition {
        val normalizedType = type.normalized()
        return copy(
            name = name.trim(),
            typeName = normalizedType.typeName,
            typeIsByRef = normalizedType.isByRef,
        )
    }

    internal fun signatureKey(): String =
        "$name:${type.renderSignatureKey()}:$direction:$isInParameter:$isOutParameter"
}

data class WinRtMethodDefinition(
    val name: String,
    val returnTypeName: String,
    val parameters: List<WinRtParameterDefinition> = emptyList(),
    val isStatic: Boolean = false,
    val returnTypeIsByRef: Boolean = false,
    val methodRowId: Int? = null,
) {
    val returnType: WinRtTypeRef
        get() = WinRtTypeRef.fromDisplayName(returnTypeName).withByRef(returnTypeIsByRef)

    fun normalized(): WinRtMethodDefinition {
        val normalizedReturnType = returnType.normalized()
        return copy(
            name = name.trim(),
            returnTypeName = normalizedReturnType.typeName,
            returnTypeIsByRef = normalizedReturnType.isByRef,
            parameters = parameters.map(WinRtParameterDefinition::normalized),
            methodRowId = methodRowId?.takeIf { it > 0 },
        )
    }

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(returnType.renderSignatureKey())
        append('|')
        append(parameters.joinToString(",") { it.signatureKey() })
    }

    internal fun sortKey(): Pair<Int, String> = (methodRowId ?: Int.MAX_VALUE) to signatureKey()

    internal fun merge(other: WinRtMethodDefinition): WinRtMethodDefinition {
        require(signatureKey() == other.signatureKey()) {
            "Can only merge identical methods: ${signatureKey()} vs ${other.signatureKey()}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRtMethodDefinition(
            name = left.name,
            returnTypeName = left.returnTypeName,
            parameters = left.parameters,
            isStatic = left.isStatic,
            returnTypeIsByRef = left.returnTypeIsByRef || right.returnTypeIsByRef,
            methodRowId = listOfNotNull(left.methodRowId, right.methodRowId).minOrNull(),
        )
    }
}

data class WinRtPropertyDefinition(
    val name: String,
    val typeName: String,
    val isStatic: Boolean = false,
    val getterMethodName: String? = null,
    val setterMethodName: String? = null,
    val getterMethodRowId: Int? = null,
    val setterMethodRowId: Int? = null,
) {
    val isReadOnly: Boolean
        get() = setterMethodName == null

    val type: WinRtTypeRef
        get() = WinRtTypeRef.fromDisplayName(typeName)

    fun normalized(): WinRtPropertyDefinition {
        val normalizedType = type.normalized()
        return copy(
            name = name.trim(),
            typeName = normalizedType.typeName,
            getterMethodName = getterMethodName?.trim(),
            setterMethodName = setterMethodName?.trim(),
            getterMethodRowId = getterMethodRowId?.takeIf { it > 0 },
            setterMethodRowId = setterMethodRowId?.takeIf { it > 0 },
        )
    }

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(type.normalized().typeName)
    }

    internal fun sortKey(): Pair<Int, String> =
        (getterMethodRowId ?: setterMethodRowId ?: Int.MAX_VALUE) to signatureKey()

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
            getterMethodRowId = listOfNotNull(left.getterMethodRowId, right.getterMethodRowId).minOrNull(),
            setterMethodRowId = listOfNotNull(left.setterMethodRowId, right.setterMethodRowId).minOrNull(),
        )
    }
}

data class WinRtEventDefinition(
    val name: String,
    val delegateTypeName: String,
    val isStatic: Boolean = false,
    val addMethodName: String? = null,
    val removeMethodName: String? = null,
    val addMethodRowId: Int? = null,
    val removeMethodRowId: Int? = null,
) {
    val delegateType: WinRtTypeRef
        get() = WinRtTypeRef.fromDisplayName(delegateTypeName)

    fun normalized(): WinRtEventDefinition {
        val normalizedDelegateType = delegateType.normalized()
        return copy(
            name = name.trim(),
            delegateTypeName = normalizedDelegateType.typeName,
            addMethodName = addMethodName?.trim(),
            removeMethodName = removeMethodName?.trim(),
            addMethodRowId = addMethodRowId?.takeIf { it > 0 },
            removeMethodRowId = removeMethodRowId?.takeIf { it > 0 },
        )
    }

    internal fun signatureKey(): String = buildString {
        append(if (isStatic) 'S' else 'I')
        append('|')
        append(name)
        append('|')
        append(delegateType.normalized().typeName)
    }

    internal fun sortKey(): Pair<Int, String> =
        (addMethodRowId ?: removeMethodRowId ?: Int.MAX_VALUE) to signatureKey()

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
            addMethodRowId = listOfNotNull(left.addMethodRowId, right.addMethodRowId).minOrNull(),
            removeMethodRowId = listOfNotNull(left.removeMethodRowId, right.removeMethodRowId).minOrNull(),
        )
    }
}

data class WinRtTypeDefinition(
    val namespace: String,
    val name: String,
    val kind: WinRtTypeKind = WinRtTypeKind.Unknown,
    val iid: Guid? = null,
    val baseTypeName: String? = null,
    val enumUnderlyingType: WinRtIntegralType? = null,
    val enumMembers: List<WinRtEnumMemberDefinition> = emptyList(),
    val isProjectionInternal: Boolean = false,
    val isExclusiveTo: Boolean = false,
    val isApiContract: Boolean = false,
    val isAttributeType: Boolean = false,
    val isStaticType: Boolean = false,
    val isSealedType: Boolean = false,
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

    val baseType: WinRtTypeRef?
        get() = baseTypeName?.let(WinRtTypeRef::fromDisplayName)

    val defaultInterface: WinRtTypeRef?
        get() = defaultInterfaceName?.let(WinRtTypeRef::fromDisplayName)

    fun normalized(): WinRtTypeDefinition {
        val normalizedMethods = methods
            .map(WinRtMethodDefinition::normalized)
            .groupBy(WinRtMethodDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRtMethodDefinition::merge) }
            .sortedWith(compareBy<WinRtMethodDefinition>({ it.methodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))
        val normalizedProperties = properties
            .map(WinRtPropertyDefinition::normalized)
            .groupBy(WinRtPropertyDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRtPropertyDefinition::merge) }
            .sortedWith(compareBy<WinRtPropertyDefinition>({ it.getterMethodRowId ?: it.setterMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))
        val normalizedEvents = events
            .map(WinRtEventDefinition::normalized)
            .groupBy(WinRtEventDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRtEventDefinition::merge) }
            .sortedWith(compareBy<WinRtEventDefinition>({ it.addMethodRowId ?: it.removeMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))

        return copy(
            namespace = namespace.trim(),
            name = name.trim(),
            baseTypeName = baseType?.normalized()?.typeName,
            enumUnderlyingType = enumUnderlyingType,
            enumMembers = enumMembers
                .map(WinRtEnumMemberDefinition::normalized)
                .fold(linkedMapOf<String, WinRtEnumMemberDefinition>()) { unique, member ->
                    unique.putIfAbsent(member.name, member)
                    unique
                }
                .values
                .toList(),
            defaultInterfaceName = defaultInterface?.normalized()?.typeName,
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
        val mergedEnumUnderlyingType = when {
            left.enumUnderlyingType == null -> right.enumUnderlyingType
            right.enumUnderlyingType == null -> left.enumUnderlyingType
            left.enumUnderlyingType == right.enumUnderlyingType -> left.enumUnderlyingType
            else -> error("Can only merge identical enum underlying types: ${left.enumUnderlyingType} vs ${right.enumUnderlyingType}")
        }
        val mergedEnumMembers = linkedMapOf<String, WinRtEnumMemberDefinition>()
        (left.enumMembers + right.enumMembers).forEach { member ->
            mergedEnumMembers.merge(member.name, member, WinRtEnumMemberDefinition::merge)
        }
        return WinRtTypeDefinition(
            namespace = left.namespace,
            name = left.name,
            kind = mergeKind(left.kind, right.kind),
            iid = left.iid ?: right.iid,
            baseTypeName = left.baseTypeName ?: right.baseTypeName,
            enumUnderlyingType = mergedEnumUnderlyingType,
            enumMembers = mergedEnumMembers.values.toList(),
            isProjectionInternal = left.isProjectionInternal || right.isProjectionInternal,
            isExclusiveTo = left.isExclusiveTo || right.isExclusiveTo,
            isApiContract = left.isApiContract || right.isApiContract,
            isAttributeType = left.isAttributeType || right.isAttributeType,
            isStaticType = left.isStaticType || right.isStaticType,
            isSealedType = left.isSealedType || right.isSealedType,
            defaultInterfaceName = left.defaultInterfaceName ?: right.defaultInterfaceName,
            implementedInterfaces = (left.implementedInterfaces + right.implementedInterfaces)
                .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName),
            genericParameterCount = maxOf(left.genericParameterCount, right.genericParameterCount),
            activation = left.activation.merge(right.activation),
            methods = (left.methods + right.methods)
                .groupBy(WinRtMethodDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRtMethodDefinition::merge) }
                .sortedWith(compareBy<WinRtMethodDefinition>({ it.methodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
            properties = (left.properties + right.properties)
                .groupBy(WinRtPropertyDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRtPropertyDefinition::merge) }
                .sortedWith(compareBy<WinRtPropertyDefinition>({ it.getterMethodRowId ?: it.setterMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
            events = (left.events + right.events)
                .groupBy(WinRtEventDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRtEventDefinition::merge) }
                .sortedWith(compareBy<WinRtEventDefinition>({ it.addMethodRowId ?: it.removeMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
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

private fun WinRtTypeRef.renderSignatureKey(): String {
    val normalizedType = normalized()
    return if (normalizedType.isByRef) "${normalizedType.typeName}&" else normalizedType.typeName
}
