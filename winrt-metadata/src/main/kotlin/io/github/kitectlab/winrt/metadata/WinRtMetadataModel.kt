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

enum class WinRtTypeLayoutKind {
    Auto,
    Sequential,
    Explicit,
}

data class WinRtTypeLayout(
    val kind: WinRtTypeLayoutKind = WinRtTypeLayoutKind.Auto,
    val packingSize: Int? = null,
    val classSize: Int? = null,
) {
    fun normalized(): WinRtTypeLayout =
        copy(
            packingSize = packingSize?.takeIf { it > 0 },
            classSize = classSize?.takeIf { it > 0 },
        )

    internal fun merge(other: WinRtTypeLayout): WinRtTypeLayout {
        val left = normalized()
        val right = other.normalized()
        return WinRtTypeLayout(
            kind = if (left.kind != WinRtTypeLayoutKind.Auto) left.kind else right.kind,
            packingSize = left.packingSize ?: right.packingSize,
            classSize = left.classSize ?: right.classSize,
        )
    }
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

data class WinRtFieldDefinition(
    val name: String,
    val typeName: String,
    val flags: Int = 0,
    val rowId: Int? = null,
    val offset: Int? = null,
    val isStatic: Boolean = false,
    val isLiteral: Boolean = false,
    val isInitOnly: Boolean = false,
    val hasConstant: Boolean = false,
    val constantValueBits: ULong? = null,
    val constantElementType: Int? = null,
    val abiSize: Int? = null,
    val abiAlignment: Int? = null,
    val isBlittable: Boolean = false,
    val typeSignature: WinRtTypeRef? = null,
) {
    val type: WinRtTypeRef
        get() = typeSignature ?: WinRtTypeRef.fromDisplayName(typeName)

    fun normalized(): WinRtFieldDefinition {
        val normalizedType = type.normalized()
        return copy(
            name = name.trim(),
            typeName = normalizedType.typeName,
            typeSignature = normalizedType,
            rowId = rowId?.takeIf { it > 0 },
            offset = offset?.takeIf { it >= 0 },
            abiSize = abiSize?.takeIf { it > 0 },
            abiAlignment = abiAlignment?.takeIf { it > 0 },
        )
    }
}

data class WinRtGenericParameterDefinition(
    val name: String,
    val index: Int,
    val flags: Int = 0,
    val constraints: List<String> = emptyList(),
) {
    val constraintTypes: List<WinRtTypeRef>
        get() = constraints.map(WinRtTypeRef::fromDisplayName)

    fun normalized(): WinRtGenericParameterDefinition =
        copy(
            name = name.trim().ifBlank { "T${index.coerceAtLeast(0)}" },
            index = index.coerceAtLeast(0),
            constraints = constraintTypes
                .map(WinRtTypeRef::normalized)
                .map(WinRtTypeRef::typeName)
                .distinct()
                .sorted(),
        )

    internal fun merge(other: WinRtGenericParameterDefinition): WinRtGenericParameterDefinition {
        require(index == other.index) {
            "Can only merge generic parameters with the same index: $index vs ${other.index}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRtGenericParameterDefinition(
            name = left.name.ifBlank { right.name },
            index = left.index,
            flags = left.flags or right.flags,
            constraints = (left.constraints + right.constraints).distinct().sorted(),
        )
    }
}

sealed interface WinRtCustomAttributeValue {
    data class StringValue(val value: String?) : WinRtCustomAttributeValue
    data class TypeValue(val typeName: String?) : WinRtCustomAttributeValue
    data class BooleanValue(val value: Boolean) : WinRtCustomAttributeValue
    data class IntegralValue(val value: Long) : WinRtCustomAttributeValue
    data class FloatingPointValue(val value: Double) : WinRtCustomAttributeValue
    data class EnumValue(val enumTypeName: String, val value: Long) : WinRtCustomAttributeValue
    data class ArrayValue(val values: List<WinRtCustomAttributeValue>) : WinRtCustomAttributeValue
    data object NullValue : WinRtCustomAttributeValue

    val stringValue: String?
        get() = when (this) {
            is StringValue -> value
            is TypeValue -> typeName
            else -> null
        }
}

data class WinRtCustomAttributeNamedArgument(
    val name: String,
    val value: WinRtCustomAttributeValue,
    val isField: Boolean = false,
) {
    fun normalized(): WinRtCustomAttributeNamedArgument = copy(name = name.trim())
}

data class WinRtCustomAttributeDefinition(
    val typeName: String,
    val fixedArguments: List<WinRtCustomAttributeValue> = emptyList(),
    val namedArguments: List<WinRtCustomAttributeNamedArgument> = emptyList(),
) {
    val stringArguments: List<String>
        get() = fixedArguments.mapNotNull(WinRtCustomAttributeValue::stringValue)

    fun normalized(): WinRtCustomAttributeDefinition =
        copy(
            typeName = WinRtTypeRef.fromDisplayName(typeName).normalized().typeName,
            namedArguments = namedArguments.map(WinRtCustomAttributeNamedArgument::normalized),
        )
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

enum class WinRtMethodImplementationMemberKind {
    MethodDefinition,
    MemberReference,
    Unknown,
}

data class WinRtMethodImplementationMember(
    val kind: WinRtMethodImplementationMemberKind,
    val rowId: Int,
    val name: String? = null,
    val ownerTypeName: String? = null,
) {
    fun normalized(): WinRtMethodImplementationMember =
        copy(
            name = name?.trim()?.takeIf(String::isNotEmpty),
            ownerTypeName = ownerTypeName?.trim()?.takeIf(String::isNotEmpty),
        )
}

data class WinRtMethodImplementationDefinition(
    val classTypeName: String,
    val body: WinRtMethodImplementationMember,
    val declaration: WinRtMethodImplementationMember,
) {
    fun normalized(): WinRtMethodImplementationDefinition =
        copy(
            classTypeName = WinRtTypeRef.fromDisplayName(classTypeName).normalized().typeName,
            body = body.normalized(),
            declaration = declaration.normalized(),
        )
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

data class WinRtContractVersionMetadata(
    val contractName: String?,
    val version: Long,
    val majorVersion: Int = (version ushr 16).toInt(),
    val platformVersion: String? = null,
) {
    fun normalized(): WinRtContractVersionMetadata =
        copy(contractName = contractName?.trim()?.takeIf(String::isNotEmpty))
}

data class WinRtDeprecationMetadata(
    val message: String?,
    val kind: Long?,
    val version: Long?,
    val contractName: String?,
) {
    fun normalized(): WinRtDeprecationMetadata =
        copy(
            message = message?.trim(),
            contractName = contractName?.trim()?.takeIf(String::isNotEmpty),
        )
}

data class WinRtAvailabilityMetadata(
    val contractVersion: WinRtContractVersionMetadata? = null,
    val version: Long? = null,
    val previousContractVersions: List<WinRtContractVersionMetadata> = emptyList(),
    val deprecations: List<WinRtDeprecationMetadata> = emptyList(),
    val threadingModel: Long? = null,
    val marshalingBehavior: Long? = null,
    val isMuse: Boolean = false,
    val isWebHostHidden: Boolean = false,
) {
    fun normalized(): WinRtAvailabilityMetadata =
        copy(
            contractVersion = contractVersion?.normalized(),
            previousContractVersions = previousContractVersions.map(WinRtContractVersionMetadata::normalized),
            deprecations = deprecations.map(WinRtDeprecationMetadata::normalized),
        )

    internal fun merge(other: WinRtAvailabilityMetadata): WinRtAvailabilityMetadata {
        val left = normalized()
        val right = other.normalized()
        return WinRtAvailabilityMetadata(
            contractVersion = left.contractVersion ?: right.contractVersion,
            version = left.version ?: right.version,
            previousContractVersions = left.previousContractVersions + right.previousContractVersions,
            deprecations = left.deprecations + right.deprecations,
            threadingModel = left.threadingModel ?: right.threadingModel,
            marshalingBehavior = left.marshalingBehavior ?: right.marshalingBehavior,
            isMuse = left.isMuse || right.isMuse,
            isWebHostHidden = left.isWebHostHidden || right.isWebHostHidden,
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
    val hasDefaultValue: Boolean = false,
    val defaultValueBits: ULong? = null,
    val defaultValueElementType: Int? = null,
    val typeSignature: WinRtTypeRef? = null,
) {
    val type: WinRtTypeRef
        get() = (typeSignature ?: WinRtTypeRef.fromDisplayName(typeName)).withByRef(typeIsByRef)

    fun normalized(): WinRtParameterDefinition {
        val normalizedType = type.normalized()
        return copy(
            name = name.trim(),
            typeName = normalizedType.typeName,
            typeSignature = normalizedType,
            typeIsByRef = normalizedType.isByRef,
        )
    }

    internal fun signatureKey(): String =
        "$name:${type.renderSignatureKey()}:$direction:$isInParameter:$isOutParameter:$hasDefaultValue:$defaultValueBits:$defaultValueElementType"
}

enum class WinRtMethodVisibility {
    Private,
    FamilyAndAssembly,
    Assembly,
    Family,
    FamilyOrAssembly,
    Public,
    Unknown,
}

data class WinRtMethodDefinition(
    val name: String,
    val returnTypeName: String,
    val parameters: List<WinRtParameterDefinition> = emptyList(),
    val isStatic: Boolean = false,
    val visibility: WinRtMethodVisibility = WinRtMethodVisibility.Unknown,
    val isSpecialName: Boolean = false,
    val isRuntimeSpecialName: Boolean = false,
    val overloadName: String? = null,
    val isDefaultOverload: Boolean = false,
    val isNoException: Boolean = false,
    val isRemoveOverload: Boolean = false,
    val isObjectEquals: Boolean = false,
    val isClassEquals: Boolean = false,
    val isObjectGetHashCode: Boolean = false,
    val returnParameterAttributes: List<WinRtCustomAttributeDefinition> = emptyList(),
    val returnTypeIsByRef: Boolean = false,
    val methodRowId: Int? = null,
    val returnTypeSignature: WinRtTypeRef? = null,
) {
    val returnType: WinRtTypeRef
        get() = (returnTypeSignature ?: WinRtTypeRef.fromDisplayName(returnTypeName)).withByRef(returnTypeIsByRef)

    fun normalized(): WinRtMethodDefinition {
        val normalizedReturnType = returnType.normalized()
        return copy(
            name = name.trim(),
            returnTypeName = normalizedReturnType.typeName,
            returnTypeSignature = normalizedReturnType,
            overloadName = overloadName?.trim()?.takeIf(String::isNotEmpty),
            returnParameterAttributes = returnParameterAttributes.map(WinRtCustomAttributeDefinition::normalized),
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
            returnTypeSignature = left.returnTypeSignature,
            parameters = left.parameters,
            isStatic = left.isStatic,
            visibility = left.visibility,
            isSpecialName = left.isSpecialName || right.isSpecialName,
            isRuntimeSpecialName = left.isRuntimeSpecialName || right.isRuntimeSpecialName,
            overloadName = left.overloadName ?: right.overloadName,
            isDefaultOverload = left.isDefaultOverload || right.isDefaultOverload,
            isNoException = left.isNoException || right.isNoException,
            isRemoveOverload = left.isRemoveOverload || right.isRemoveOverload,
            isObjectEquals = left.isObjectEquals || right.isObjectEquals,
            isClassEquals = left.isClassEquals || right.isClassEquals,
            isObjectGetHashCode = left.isObjectGetHashCode || right.isObjectGetHashCode,
            returnParameterAttributes = left.returnParameterAttributes + right.returnParameterAttributes,
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
    val isNoException: Boolean = false,
    val hasValidAccessors: Boolean = true,
    val typeSignature: WinRtTypeRef? = null,
) {
    val isReadOnly: Boolean
        get() = setterMethodName == null

    val type: WinRtTypeRef
        get() = typeSignature ?: WinRtTypeRef.fromDisplayName(typeName)

    fun normalized(): WinRtPropertyDefinition {
        val normalizedType = type.normalized()
        return copy(
            name = name.trim(),
            typeName = normalizedType.typeName,
            typeSignature = normalizedType,
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
            typeSignature = left.typeSignature,
            isStatic = left.isStatic || right.isStatic,
            getterMethodName = left.getterMethodName ?: right.getterMethodName,
            setterMethodName = left.setterMethodName ?: right.setterMethodName,
            getterMethodRowId = listOfNotNull(left.getterMethodRowId, right.getterMethodRowId).minOrNull(),
            setterMethodRowId = listOfNotNull(left.setterMethodRowId, right.setterMethodRowId).minOrNull(),
            isNoException = left.isNoException || right.isNoException,
            hasValidAccessors = left.hasValidAccessors && right.hasValidAccessors,
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
    val hasValidAccessors: Boolean = true,
    val delegateTypeSignature: WinRtTypeRef? = null,
) {
    val delegateType: WinRtTypeRef
        get() = delegateTypeSignature ?: WinRtTypeRef.fromDisplayName(delegateTypeName)

    fun normalized(): WinRtEventDefinition {
        val normalizedDelegateType = delegateType.normalized()
        return copy(
            name = name.trim(),
            delegateTypeName = normalizedDelegateType.typeName,
            delegateTypeSignature = normalizedDelegateType,
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
            delegateTypeSignature = left.delegateTypeSignature,
            isStatic = left.isStatic || right.isStatic,
            addMethodName = left.addMethodName ?: right.addMethodName,
            removeMethodName = left.removeMethodName ?: right.removeMethodName,
            addMethodRowId = listOfNotNull(left.addMethodRowId, right.addMethodRowId).minOrNull(),
            removeMethodRowId = listOfNotNull(left.removeMethodRowId, right.removeMethodRowId).minOrNull(),
            hasValidAccessors = left.hasValidAccessors && right.hasValidAccessors,
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
    val fields: List<WinRtFieldDefinition> = emptyList(),
    val layout: WinRtTypeLayout = WinRtTypeLayout(),
    val isBlittable: Boolean = false,
    val abiSize: Int? = null,
    val abiAlignment: Int? = null,
    val isProjectionInternal: Boolean = false,
    val isExclusiveTo: Boolean = false,
    val isApiContract: Boolean = false,
    val isAttributeType: Boolean = false,
    val isStaticType: Boolean = false,
    val isSealedType: Boolean = false,
    val isFastAbi: Boolean = false,
    val gcPressureAmount: Int = 0,
    val defaultInterfaceName: String? = null,
    val implementedInterfaces: List<WinRtInterfaceImplementationDefinition> = emptyList(),
    val methodImplementations: List<WinRtMethodImplementationDefinition> = emptyList(),
    val genericParameterCount: Int = 0,
    val genericParameters: List<WinRtGenericParameterDefinition> = emptyList(),
    val customAttributes: List<WinRtCustomAttributeDefinition> = emptyList(),
    val activation: WinRtActivationShape = WinRtActivationShape(),
    val availability: WinRtAvailabilityMetadata = WinRtAvailabilityMetadata(),
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
            fields = fields
                .map(WinRtFieldDefinition::normalized)
                .sortedWith(compareBy<WinRtFieldDefinition>({ it.rowId ?: Int.MAX_VALUE }, { it.name })),
            layout = layout.normalized(),
            abiSize = abiSize?.takeIf { it > 0 },
            abiAlignment = abiAlignment?.takeIf { it > 0 },
            defaultInterfaceName = defaultInterface?.normalized()?.typeName,
            implementedInterfaces = implementedInterfaces
                .map(WinRtInterfaceImplementationDefinition::normalized)
                .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName),
            methodImplementations = methodImplementations
                .map(WinRtMethodImplementationDefinition::normalized)
                .sortedWith(compareBy({ it.declaration.ownerTypeName.orEmpty() }, { it.declaration.name.orEmpty() }, { it.body.name.orEmpty() })),
            genericParameters = genericParameters
                .map(WinRtGenericParameterDefinition::normalized)
                .groupBy(WinRtGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRtGenericParameterDefinition::merge) }
                .sortedBy(WinRtGenericParameterDefinition::index),
            genericParameterCount = maxOf(
                genericParameterCount.coerceAtLeast(0),
                genericParameters.maxOfOrNull { it.index + 1 } ?: 0,
            ),
            customAttributes = customAttributes.map(WinRtCustomAttributeDefinition::normalized),
            activation = activation.normalized(),
            availability = availability.normalized(),
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
            fields = (left.fields + right.fields)
                .groupBy { it.name }
                .values
                .map { duplicates -> duplicates.first().normalized() }
                .sortedWith(compareBy<WinRtFieldDefinition>({ it.rowId ?: Int.MAX_VALUE }, { it.name })),
            layout = left.layout.merge(right.layout),
            isBlittable = left.isBlittable || right.isBlittable,
            abiSize = left.abiSize ?: right.abiSize,
            abiAlignment = left.abiAlignment ?: right.abiAlignment,
            isProjectionInternal = left.isProjectionInternal || right.isProjectionInternal,
            isExclusiveTo = left.isExclusiveTo || right.isExclusiveTo,
            isApiContract = left.isApiContract || right.isApiContract,
            isAttributeType = left.isAttributeType || right.isAttributeType,
            isStaticType = left.isStaticType || right.isStaticType,
            isSealedType = left.isSealedType || right.isSealedType,
            isFastAbi = left.isFastAbi || right.isFastAbi,
            gcPressureAmount = maxOf(left.gcPressureAmount, right.gcPressureAmount),
            defaultInterfaceName = left.defaultInterfaceName ?: right.defaultInterfaceName,
            implementedInterfaces = (left.implementedInterfaces + right.implementedInterfaces)
                .groupBy(WinRtInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRtInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRtInterfaceImplementationDefinition::interfaceName),
            methodImplementations = (left.methodImplementations + right.methodImplementations)
                .map(WinRtMethodImplementationDefinition::normalized)
                .distinct()
                .sortedWith(compareBy({ it.declaration.ownerTypeName.orEmpty() }, { it.declaration.name.orEmpty() }, { it.body.name.orEmpty() })),
            genericParameterCount = maxOf(left.genericParameterCount, right.genericParameterCount),
            genericParameters = (left.genericParameters + right.genericParameters)
                .groupBy(WinRtGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRtGenericParameterDefinition::merge) }
                .sortedBy(WinRtGenericParameterDefinition::index),
            customAttributes = left.customAttributes + right.customAttributes,
            activation = left.activation.merge(right.activation),
            availability = left.availability.merge(right.availability),
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
