package io.github.composefluent.winrt.metadata

import io.github.composefluent.winrt.runtime.Guid

enum class WinRTTypeKind {
    Unknown,
    Interface,
    RuntimeClass,
    Enum,
    Struct,
    Delegate,
}

enum class WinRTIntegralType {
    Int8,
    UInt8,
    Int16,
    UInt16,
    Int32,
    UInt32,
    Int64,
    UInt64,
}

enum class WinRTTypeLayoutKind {
    Auto,
    Sequential,
    Explicit,
}

data class WinRTTypeLayout(
    val kind: WinRTTypeLayoutKind = WinRTTypeLayoutKind.Auto,
    val packingSize: Int? = null,
    val classSize: Int? = null,
) {
    fun normalized(): WinRTTypeLayout =
        copy(
            packingSize = packingSize?.takeIf { it > 0 },
            classSize = classSize?.takeIf { it > 0 },
        )

    internal fun merge(other: WinRTTypeLayout): WinRTTypeLayout {
        val left = normalized()
        val right = other.normalized()
        return WinRTTypeLayout(
            kind = if (left.kind != WinRTTypeLayoutKind.Auto) left.kind else right.kind,
            packingSize = left.packingSize ?: right.packingSize,
            classSize = left.classSize ?: right.classSize,
        )
    }
}

data class WinRTEnumMemberDefinition(
    val name: String,
    val valueBits: ULong,
) {
    fun normalized(): WinRTEnumMemberDefinition = copy(name = name.trim())

    internal fun merge(other: WinRTEnumMemberDefinition): WinRTEnumMemberDefinition {
        require(name == other.name) {
            "Can only merge identical enum members: $name vs ${other.name}"
        }
        require(valueBits == other.valueBits) {
            "Can only merge enum members with identical values: $name=$valueBits vs ${other.name}=${other.valueBits}"
        }
        return normalized()
    }
}

data class WinRTFieldDefinition(
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
    val typeSignature: WinRTTypeRef? = null,
) {
    val type: WinRTTypeRef
        get() = typeSignature ?: WinRTTypeRef.fromDisplayName(typeName)

    fun normalized(): WinRTFieldDefinition {
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

data class WinRTGenericParameterDefinition(
    val name: String,
    val index: Int,
    val flags: Int = 0,
    val constraints: List<String> = emptyList(),
) {
    val constraintTypes: List<WinRTTypeRef>
        get() = constraints.map(WinRTTypeRef::fromDisplayName)

    fun normalized(): WinRTGenericParameterDefinition =
        copy(
            name = name.trim().ifBlank { "T${index.coerceAtLeast(0)}" },
            index = index.coerceAtLeast(0),
            constraints = constraintTypes
                .map(WinRTTypeRef::normalized)
                .map(WinRTTypeRef::typeName)
                .distinct()
                .sorted(),
        )

    internal fun merge(other: WinRTGenericParameterDefinition): WinRTGenericParameterDefinition {
        require(index == other.index) {
            "Can only merge generic parameters with the same index: $index vs ${other.index}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRTGenericParameterDefinition(
            name = left.name.ifBlank { right.name },
            index = left.index,
            flags = left.flags or right.flags,
            constraints = (left.constraints + right.constraints).distinct().sorted(),
        )
    }
}

sealed interface WinRTCustomAttributeValue {
    data class StringValue(val value: String?) : WinRTCustomAttributeValue
    data class TypeValue(val typeName: String?) : WinRTCustomAttributeValue
    data class BooleanValue(val value: Boolean) : WinRTCustomAttributeValue
    data class IntegralValue(val value: Long) : WinRTCustomAttributeValue
    data class FloatingPointValue(val value: Double) : WinRTCustomAttributeValue
    data class EnumValue(val enumTypeName: String, val value: Long) : WinRTCustomAttributeValue
    data class ArrayValue(val values: List<WinRTCustomAttributeValue>) : WinRTCustomAttributeValue
    data object NullValue : WinRTCustomAttributeValue

    val stringValue: String?
        get() = when (this) {
            is StringValue -> value
            is TypeValue -> typeName
            else -> null
        }
}

data class WinRTCustomAttributeNamedArgument(
    val name: String,
    val value: WinRTCustomAttributeValue,
    val isField: Boolean = false,
) {
    fun normalized(): WinRTCustomAttributeNamedArgument = copy(name = name.trim())
}

data class WinRTCustomAttributeDefinition(
    val typeName: String,
    val fixedArguments: List<WinRTCustomAttributeValue> = emptyList(),
    val namedArguments: List<WinRTCustomAttributeNamedArgument> = emptyList(),
) {
    val stringArguments: List<String>
        get() = fixedArguments.mapNotNull(WinRTCustomAttributeValue::stringValue)

    fun normalized(): WinRTCustomAttributeDefinition =
        copy(
            typeName = WinRTTypeRef.fromDisplayName(typeName).normalized().typeName,
            namedArguments = namedArguments.map(WinRTCustomAttributeNamedArgument::normalized),
        )
}

data class WinRTInterfaceImplementationDefinition(
    val interfaceName: String,
    val isDefault: Boolean = false,
    val isOverridable: Boolean = false,
    val isProtected: Boolean = false,
) {
    val interfaceType: WinRTTypeRef
        get() = WinRTTypeRef.fromDisplayName(interfaceName)

    fun normalized(): WinRTInterfaceImplementationDefinition {
        val normalizedInterfaceType = interfaceType.normalized()
        return copy(interfaceName = normalizedInterfaceType.typeName)
    }

    internal fun merge(other: WinRTInterfaceImplementationDefinition): WinRTInterfaceImplementationDefinition {
        require(interfaceName == other.interfaceName) {
            "Can only merge identical interface implementations: $interfaceName vs ${other.interfaceName}"
        }
        return WinRTInterfaceImplementationDefinition(
            interfaceName = interfaceName,
            isDefault = isDefault || other.isDefault,
            isOverridable = isOverridable || other.isOverridable,
            isProtected = isProtected || other.isProtected,
        )
    }
}

enum class WinRTMethodImplementationMemberKind {
    MethodDefinition,
    MemberReference,
    Unknown,
}

data class WinRTMethodImplementationMember(
    val kind: WinRTMethodImplementationMemberKind,
    val rowId: Int,
    val name: String? = null,
    val ownerTypeName: String? = null,
) {
    fun normalized(): WinRTMethodImplementationMember =
        copy(
            name = name?.trim()?.takeIf(String::isNotEmpty),
            ownerTypeName = ownerTypeName?.trim()?.takeIf(String::isNotEmpty),
        )
}

data class WinRTMethodImplementationDefinition(
    val classTypeName: String,
    val body: WinRTMethodImplementationMember,
    val declaration: WinRTMethodImplementationMember,
) {
    fun normalized(): WinRTMethodImplementationDefinition =
        copy(
            classTypeName = WinRTTypeRef.fromDisplayName(classTypeName).normalized().typeName,
            body = body.normalized(),
            declaration = declaration.normalized(),
        )
}

data class WinRTActivationShape(
    val isActivatable: Boolean = false,
    val activatableFactoryInterfaceName: String? = null,
    val staticInterfaceNames: List<String> = emptyList(),
    val composableFactoryInterfaceName: String? = null,
    val factories: List<WinRTAttributedFactoryShape> = emptyList(),
) {
    val activatableFactoryInterface: WinRTTypeRef?
        get() = activatableFactoryInterfaceName?.let(WinRTTypeRef::fromDisplayName)

    val staticInterfaces: List<WinRTTypeRef>
        get() = staticInterfaceNames.map(WinRTTypeRef::fromDisplayName)

    val composableFactoryInterface: WinRTTypeRef?
        get() = composableFactoryInterfaceName?.let(WinRTTypeRef::fromDisplayName)

    fun normalized(): WinRTActivationShape {
        val normalizedStaticInterfaces = staticInterfaces
            .map(WinRTTypeRef::normalized)
            .distinctBy(WinRTTypeRef::typeName)
            .sortedBy(WinRTTypeRef::typeName)
        return copy(
            activatableFactoryInterfaceName = activatableFactoryInterface?.normalized()?.typeName,
            staticInterfaceNames = normalizedStaticInterfaces.map(WinRTTypeRef::typeName),
            composableFactoryInterfaceName = composableFactoryInterface?.normalized()?.typeName,
            factories = factories.normalizedAttributedFactories(),
        )
    }

    internal fun merge(other: WinRTActivationShape): WinRTActivationShape {
        val left = normalized()
        val right = other.normalized()
        return WinRTActivationShape(
            isActivatable = left.isActivatable || right.isActivatable,
            activatableFactoryInterfaceName = left.activatableFactoryInterfaceName ?: right.activatableFactoryInterfaceName,
            staticInterfaceNames = (left.staticInterfaceNames + right.staticInterfaceNames).distinct().sorted(),
            composableFactoryInterfaceName = left.composableFactoryInterfaceName ?: right.composableFactoryInterfaceName,
            factories = (left.factories + right.factories).normalizedAttributedFactories(),
        )
    }
}

enum class WinRTAttributedFactoryKind {
    Activatable,
    Static,
    Composable,
}

data class WinRTAttributedFactoryShape(
    val interfaceName: String,
    val kind: WinRTAttributedFactoryKind,
    val isVisible: Boolean = false,
) {
    val interfaceType: WinRTTypeRef
        get() = WinRTTypeRef.fromDisplayName(interfaceName)

    fun normalized(): WinRTAttributedFactoryShape =
        copy(interfaceName = interfaceType.normalized().typeName)
}

private fun List<WinRTAttributedFactoryShape>.normalizedAttributedFactories(): List<WinRTAttributedFactoryShape> {
    val merged = linkedMapOf<Pair<String, WinRTAttributedFactoryKind>, WinRTAttributedFactoryShape>()
    map(WinRTAttributedFactoryShape::normalized).forEach { factory ->
        val key = factory.interfaceName to factory.kind
        merged[key] = merged[key]?.copy(isVisible = merged[key]?.isVisible == true || factory.isVisible) ?: factory
    }
    return merged.values.sortedWith(compareBy(WinRTAttributedFactoryShape::interfaceName, { it.kind.ordinal }))
}

data class WinRTContractVersionMetadata(
    val contractName: String?,
    val version: Long,
    val majorVersion: Int = (version ushr 16).toInt(),
    val platformVersion: String? = null,
) {
    fun normalized(): WinRTContractVersionMetadata =
        copy(contractName = contractName?.trim()?.takeIf(String::isNotEmpty))
}

data class WinRTDeprecationMetadata(
    val message: String?,
    val kind: Long?,
    val version: Long?,
    val contractName: String?,
) {
    fun normalized(): WinRTDeprecationMetadata =
        copy(
            message = message?.trim(),
            contractName = contractName?.trim()?.takeIf(String::isNotEmpty),
        )
}

data class WinRTAvailabilityMetadata(
    val contractVersion: WinRTContractVersionMetadata? = null,
    val version: Long? = null,
    val previousContractVersions: List<WinRTContractVersionMetadata> = emptyList(),
    val deprecations: List<WinRTDeprecationMetadata> = emptyList(),
    val threadingModel: Long? = null,
    val marshalingBehavior: Long? = null,
    val isMuse: Boolean = false,
    val isWebHostHidden: Boolean = false,
) {
    fun normalized(): WinRTAvailabilityMetadata =
        copy(
            contractVersion = contractVersion?.normalized(),
            previousContractVersions = previousContractVersions.map(WinRTContractVersionMetadata::normalized),
            deprecations = deprecations.map(WinRTDeprecationMetadata::normalized),
        )

    internal fun merge(other: WinRTAvailabilityMetadata): WinRTAvailabilityMetadata {
        val left = normalized()
        val right = other.normalized()
        return WinRTAvailabilityMetadata(
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

enum class WinRTParameterDirection {
    In,
    Ref,
    Out,
}

data class WinRTParameterDefinition(
    val name: String,
    val typeName: String,
    val direction: WinRTParameterDirection = WinRTParameterDirection.In,
    val typeIsByRef: Boolean = false,
    val isInParameter: Boolean = false,
    val isOutParameter: Boolean = false,
    val hasDefaultValue: Boolean = false,
    val defaultValueBits: ULong? = null,
    val defaultValueElementType: Int? = null,
    val typeSignature: WinRTTypeRef? = null,
) {
    val type: WinRTTypeRef
        get() = (typeSignature ?: WinRTTypeRef.fromDisplayName(typeName)).withByRef(typeIsByRef)

    fun normalized(): WinRTParameterDefinition {
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

enum class WinRTMethodVisibility {
    Private,
    FamilyAndAssembly,
    Assembly,
    Family,
    FamilyOrAssembly,
    Public,
    Unknown,
}

data class WinRTMethodDefinition(
    val name: String,
    val returnTypeName: String,
    val parameters: List<WinRTParameterDefinition> = emptyList(),
    val genericParameterCount: Int = 0,
    val genericParameters: List<WinRTGenericParameterDefinition> = emptyList(),
    val isStatic: Boolean = false,
    val visibility: WinRTMethodVisibility = WinRTMethodVisibility.Unknown,
    val isSpecialName: Boolean = false,
    val isRuntimeSpecialName: Boolean = false,
    val overloadName: String? = null,
    val isDefaultOverload: Boolean = false,
    val isNoException: Boolean = false,
    val isRemoveOverload: Boolean = false,
    val isObjectEquals: Boolean = false,
    val isClassEquals: Boolean = false,
    val isObjectGetHashCode: Boolean = false,
    val returnParameterAttributes: List<WinRTCustomAttributeDefinition> = emptyList(),
    val returnTypeIsByRef: Boolean = false,
    val methodRowId: Int? = null,
    val returnTypeSignature: WinRTTypeRef? = null,
) {
    val returnType: WinRTTypeRef
        get() = (returnTypeSignature ?: WinRTTypeRef.fromDisplayName(returnTypeName)).withByRef(returnTypeIsByRef)

    fun normalized(): WinRTMethodDefinition {
        val normalizedReturnType = returnType.normalized()
        return copy(
            name = name.trim(),
            returnTypeName = normalizedReturnType.typeName,
            returnTypeSignature = normalizedReturnType,
            overloadName = overloadName?.trim()?.takeIf(String::isNotEmpty),
            genericParameterCount = maxOf(genericParameterCount, genericParameters.size),
            genericParameters = genericParameters
                .map(WinRTGenericParameterDefinition::normalized)
                .groupBy(WinRTGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRTGenericParameterDefinition::merge) }
                .sortedBy(WinRTGenericParameterDefinition::index),
            returnParameterAttributes = returnParameterAttributes.map(WinRTCustomAttributeDefinition::normalized),
            returnTypeIsByRef = normalizedReturnType.isByRef,
            parameters = parameters.map(WinRTParameterDefinition::normalized),
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

    internal fun merge(other: WinRTMethodDefinition): WinRTMethodDefinition {
        require(signatureKey() == other.signatureKey()) {
            "Can only merge identical methods: ${signatureKey()} vs ${other.signatureKey()}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRTMethodDefinition(
            name = left.name,
            returnTypeName = left.returnTypeName,
            returnTypeSignature = left.returnTypeSignature,
            parameters = left.parameters,
            genericParameterCount = maxOf(left.genericParameterCount, right.genericParameterCount),
            genericParameters = (left.genericParameters + right.genericParameters)
                .groupBy(WinRTGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRTGenericParameterDefinition::merge) }
                .sortedBy(WinRTGenericParameterDefinition::index),
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

data class WinRTPropertyDefinition(
    val name: String,
    val typeName: String,
    val isStatic: Boolean = false,
    val getterMethodName: String? = null,
    val setterMethodName: String? = null,
    val getterMethodRowId: Int? = null,
    val setterMethodRowId: Int? = null,
    val isNoException: Boolean = false,
    val hasValidAccessors: Boolean = true,
    val typeSignature: WinRTTypeRef? = null,
) {
    val isReadOnly: Boolean
        get() = setterMethodName == null && setterMethodRowId == null

    val type: WinRTTypeRef
        get() = typeSignature ?: WinRTTypeRef.fromDisplayName(typeName)

    fun normalized(): WinRTPropertyDefinition {
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

    internal fun merge(other: WinRTPropertyDefinition): WinRTPropertyDefinition {
        require(name == other.name && typeName == other.typeName) {
            "Can only merge identical properties: $name:$typeName vs ${other.name}:${other.typeName}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRTPropertyDefinition(
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

data class WinRTEventDefinition(
    val name: String,
    val delegateTypeName: String,
    val isStatic: Boolean = false,
    val addMethodName: String? = null,
    val removeMethodName: String? = null,
    val addMethodRowId: Int? = null,
    val removeMethodRowId: Int? = null,
    val hasValidAccessors: Boolean = true,
    val delegateTypeSignature: WinRTTypeRef? = null,
) {
    val delegateType: WinRTTypeRef
        get() = delegateTypeSignature ?: WinRTTypeRef.fromDisplayName(delegateTypeName)

    fun normalized(): WinRTEventDefinition {
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

    internal fun merge(other: WinRTEventDefinition): WinRTEventDefinition {
        require(name == other.name && delegateTypeName == other.delegateTypeName) {
            "Can only merge identical events: $name:$delegateTypeName vs ${other.name}:${other.delegateTypeName}"
        }
        val left = normalized()
        val right = other.normalized()
        return WinRTEventDefinition(
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

data class WinRTTypeDefinition(
    val namespace: String,
    val name: String,
    val kind: WinRTTypeKind = WinRTTypeKind.Unknown,
    val iid: Guid? = null,
    val baseTypeName: String? = null,
    val enumUnderlyingType: WinRTIntegralType? = null,
    val enumMembers: List<WinRTEnumMemberDefinition> = emptyList(),
    val fields: List<WinRTFieldDefinition> = emptyList(),
    val layout: WinRTTypeLayout = WinRTTypeLayout(),
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
    val implementedInterfaces: List<WinRTInterfaceImplementationDefinition> = emptyList(),
    val methodImplementations: List<WinRTMethodImplementationDefinition> = emptyList(),
    val genericParameterCount: Int = 0,
    val genericParameters: List<WinRTGenericParameterDefinition> = emptyList(),
    val customAttributes: List<WinRTCustomAttributeDefinition> = emptyList(),
    val activation: WinRTActivationShape = WinRTActivationShape(),
    val availability: WinRTAvailabilityMetadata = WinRTAvailabilityMetadata(),
    val methods: List<WinRTMethodDefinition> = emptyList(),
    val properties: List<WinRTPropertyDefinition> = emptyList(),
    val events: List<WinRTEventDefinition> = emptyList(),
) {
    val qualifiedName: String
        get() = if (namespace.isBlank()) name else "$namespace.$name"

    val baseType: WinRTTypeRef?
        get() = baseTypeName?.let(WinRTTypeRef::fromDisplayName)

    val defaultInterface: WinRTTypeRef?
        get() = defaultInterfaceName?.let(WinRTTypeRef::fromDisplayName)

    fun normalized(): WinRTTypeDefinition {
        val normalizedMethods = methods
            .map(WinRTMethodDefinition::normalized)
            .groupBy(WinRTMethodDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRTMethodDefinition::merge) }
            .sortedWith(compareBy<WinRTMethodDefinition>({ it.methodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))
        val normalizedProperties = properties
            .map(WinRTPropertyDefinition::normalized)
            .groupBy(WinRTPropertyDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRTPropertyDefinition::merge) }
            .sortedWith(compareBy<WinRTPropertyDefinition>({ it.getterMethodRowId ?: it.setterMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))
        val normalizedEvents = events
            .map(WinRTEventDefinition::normalized)
            .groupBy(WinRTEventDefinition::signatureKey)
            .values
            .map { duplicates -> duplicates.reduce(WinRTEventDefinition::merge) }
            .sortedWith(compareBy<WinRTEventDefinition>({ it.addMethodRowId ?: it.removeMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() }))
        return copy(
            namespace = namespace.trim(),
            name = name.trim(),
            baseTypeName = baseType?.normalized()?.typeName,
            enumUnderlyingType = enumUnderlyingType,
            enumMembers = enumMembers
                .map(WinRTEnumMemberDefinition::normalized)
                .fold(linkedMapOf<String, WinRTEnumMemberDefinition>()) { unique, member ->
                    unique.putIfAbsent(member.name, member)
                    unique
                }
                .values
                .toList(),
            fields = fields
                .map(WinRTFieldDefinition::normalized)
                .sortedWith(compareBy<WinRTFieldDefinition>({ it.rowId ?: Int.MAX_VALUE }, { it.name })),
            layout = layout.normalized(),
            abiSize = abiSize?.takeIf { it > 0 },
            abiAlignment = abiAlignment?.takeIf { it > 0 },
            defaultInterfaceName = defaultInterface?.normalized()?.typeName,
            implementedInterfaces = implementedInterfaces
                .map(WinRTInterfaceImplementationDefinition::normalized)
                .groupBy(WinRTInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRTInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRTInterfaceImplementationDefinition::interfaceName),
            methodImplementations = methodImplementations
                .map(WinRTMethodImplementationDefinition::normalized)
                .sortedWith(compareBy({ it.declaration.ownerTypeName.orEmpty() }, { it.declaration.name.orEmpty() }, { it.body.name.orEmpty() })),
            genericParameters = genericParameters
                .map(WinRTGenericParameterDefinition::normalized)
                .groupBy(WinRTGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRTGenericParameterDefinition::merge) }
                .sortedBy(WinRTGenericParameterDefinition::index),
            genericParameterCount = maxOf(
                genericParameterCount.coerceAtLeast(0),
                genericParameters.maxOfOrNull { it.index + 1 } ?: 0,
            ),
            customAttributes = customAttributes.map(WinRTCustomAttributeDefinition::normalized),
            activation = activation.normalized(),
            availability = availability.normalized(),
            methods = normalizedMethods,
            properties = normalizedProperties,
            events = normalizedEvents,
        )
    }

    internal fun merge(other: WinRTTypeDefinition): WinRTTypeDefinition {
        require(namespace == other.namespace && name == other.name) {
            "Can only merge identical qualified type names: $qualifiedName vs ${other.qualifiedName}"
        }

        val left = normalized()
        val right = other.normalized()
        val mergedIid = when {
            left.iid == null -> right.iid
            right.iid == null -> left.iid
            left.iid == right.iid -> left.iid
            else -> error("Conflicting WinRT IIDs for ${left.qualifiedName}: ${left.iid} vs ${right.iid}")
        }
        val mergedEnumUnderlyingType = when {
            left.enumUnderlyingType == null -> right.enumUnderlyingType
            right.enumUnderlyingType == null -> left.enumUnderlyingType
            left.enumUnderlyingType == right.enumUnderlyingType -> left.enumUnderlyingType
            else -> error("Can only merge identical enum underlying types: ${left.enumUnderlyingType} vs ${right.enumUnderlyingType}")
        }
        val mergedEnumMembers = linkedMapOf<String, WinRTEnumMemberDefinition>()
        (left.enumMembers + right.enumMembers).forEach { member ->
            mergedEnumMembers.merge(member.name, member, WinRTEnumMemberDefinition::merge)
        }
        return WinRTTypeDefinition(
            namespace = left.namespace,
            name = left.name,
            kind = mergeKind(left.kind, right.kind),
            iid = mergedIid,
            baseTypeName = left.baseTypeName ?: right.baseTypeName,
            enumUnderlyingType = mergedEnumUnderlyingType,
            enumMembers = mergedEnumMembers.values.toList(),
            fields = (left.fields + right.fields)
                .groupBy { it.name }
                .values
                .map { duplicates -> duplicates.first().normalized() }
                .sortedWith(compareBy<WinRTFieldDefinition>({ it.rowId ?: Int.MAX_VALUE }, { it.name })),
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
                .groupBy(WinRTInterfaceImplementationDefinition::interfaceName)
                .values
                .map { duplicates -> duplicates.reduce(WinRTInterfaceImplementationDefinition::merge) }
                .sortedBy(WinRTInterfaceImplementationDefinition::interfaceName),
            methodImplementations = (left.methodImplementations + right.methodImplementations)
                .map(WinRTMethodImplementationDefinition::normalized)
                .distinct()
                .sortedWith(compareBy({ it.declaration.ownerTypeName.orEmpty() }, { it.declaration.name.orEmpty() }, { it.body.name.orEmpty() })),
            genericParameterCount = maxOf(left.genericParameterCount, right.genericParameterCount),
            genericParameters = (left.genericParameters + right.genericParameters)
                .groupBy(WinRTGenericParameterDefinition::index)
                .values
                .map { duplicates -> duplicates.reduce(WinRTGenericParameterDefinition::merge) }
                .sortedBy(WinRTGenericParameterDefinition::index),
            customAttributes = left.customAttributes + right.customAttributes,
            activation = left.activation.merge(right.activation),
            availability = left.availability.merge(right.availability),
            methods = (left.methods + right.methods)
                .groupBy(WinRTMethodDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRTMethodDefinition::merge) }
                .sortedWith(compareBy<WinRTMethodDefinition>({ it.methodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
            properties = (left.properties + right.properties)
                .groupBy(WinRTPropertyDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRTPropertyDefinition::merge) }
                .sortedWith(compareBy<WinRTPropertyDefinition>({ it.getterMethodRowId ?: it.setterMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
            events = (left.events + right.events)
                .groupBy(WinRTEventDefinition::signatureKey)
                .values
                .map { duplicates -> duplicates.reduce(WinRTEventDefinition::merge) }
                .sortedWith(compareBy<WinRTEventDefinition>({ it.addMethodRowId ?: it.removeMethodRowId ?: Int.MAX_VALUE }, { it.signatureKey() })),
        )
    }
}

data class WinRTNamespace(
    val name: String,
    val types: List<WinRTTypeDefinition>,
) {
    fun normalized(): WinRTNamespace =
        copy(
            name = name.trim(),
            types = types
                .map(WinRTTypeDefinition::normalized)
                .groupBy { it.name }
                .values
                .map { duplicates -> duplicates.reduce(WinRTTypeDefinition::merge) }
                .sortedWith(compareBy(WinRTTypeDefinition::name, { it.kind.ordinal }, { it.iid?.toString().orEmpty() })),
        )
}

data class WinRTMetadataModel(
    val namespaces: List<WinRTNamespace>,
    val windowsSdkSelections: List<WinRTWindowsSdkSelection> = emptyList(),
) {
    val universalApiContractMajorVersion: Int?
        get() = windowsSdkSelections
            .mapNotNull { selection ->
                selection.contractMajorVersion(WINDOWS_FOUNDATION_UNIVERSAL_API_CONTRACT)
            }
            .maxOrNull()

    fun normalized(): WinRTMetadataModel =
        copy(
            namespaces = namespaces
                .map(WinRTNamespace::normalized)
                .groupBy { it.name }
                .values
                .map { duplicates ->
                    WinRTNamespace(
                        name = duplicates.first().name,
                        types = duplicates
                            .flatMap(WinRTNamespace::types)
                            .groupBy { it.name }
                            .values
                            .map { typeDuplicates -> typeDuplicates.reduce(WinRTTypeDefinition::merge) }
                            .sortedWith(compareBy(WinRTTypeDefinition::name, { it.kind.ordinal }, { it.iid?.toString().orEmpty() })),
                    )
                }
                .sortedBy(WinRTNamespace::name),
            windowsSdkSelections = windowsSdkSelections
                .map(WinRTWindowsSdkSelection::normalized)
                .distinct()
                .sortedWith { left, right -> compareWindowsSdkVersions(left.version, right.version) },
        )
}

private const val WINDOWS_FOUNDATION_UNIVERSAL_API_CONTRACT = "Windows.Foundation.UniversalApiContract"

private fun mergeKind(left: WinRTTypeKind, right: WinRTTypeKind): WinRTTypeKind = when {
    left == right -> left
    left == WinRTTypeKind.Unknown -> right
    right == WinRTTypeKind.Unknown -> left
    else -> minOf(left, right, compareBy(WinRTTypeKind::ordinal))
}

private fun WinRTTypeRef.renderSignatureKey(): String {
    val normalizedType = normalized()
    return if (normalizedType.isByRef) "${normalizedType.typeName}&" else normalizedType.typeName
}
