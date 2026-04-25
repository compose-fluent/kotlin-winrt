package io.github.kitectlab.winrt.metadata

enum class WinRtMetadataParameterCategory {
    In,
    Ref,
    Out,
    PassArray,
    FillArray,
    ReceiveArray,
}

data class WinRtMethodSignatureParameter(
    val parameter: WinRtParameterDefinition,
    val category: WinRtMetadataParameterCategory,
)

data class WinRtMethodSignatureDescriptor(
    val method: WinRtMethodDefinition,
    val returnType: WinRtTypeRef,
    val returnParameterName: String = "__return_value__",
    val parameters: List<WinRtMethodSignatureParameter>,
) {
    fun hasParams(): Boolean = parameters.isNotEmpty()
}

data class WinRtPropertyAccessorDescriptor(
    val getterMethodName: String?,
    val setterMethodName: String?,
    val getterMethodRowId: Int?,
    val setterMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

data class WinRtEventAccessorDescriptor(
    val addMethodName: String?,
    val removeMethodName: String?,
    val addMethodRowId: Int?,
    val removeMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

data class WinRtAttributedFactoryDescriptor(
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val type: WinRtTypeDefinition?,
    val activatable: Boolean = false,
    val statics: Boolean = false,
    val composable: Boolean = false,
)

data class WinRtFastAbiPropertySlot(
    val propertyName: String,
    val getterMethodName: String?,
    val setterMethodName: String?,
    val vtableStartIndex: Int,
)

data class WinRtFastAbiClassDescriptor(
    val classType: WinRtTypeDefinition,
    val defaultInterface: WinRtRuntimeClassInterfaceDescriptor?,
    val otherInterfaces: List<WinRtRuntimeClassInterfaceDescriptor>,
    val propertySlots: List<WinRtFastAbiPropertySlot>,
)

class WinRtMetadataSemanticHelpers(private val model: WinRtMetadataModel) {
    private val normalizedModel = model.normalized()
    private val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
    private val closureResolver = normalizedModel.closureResolver()

    fun getAttribute(type: WinRtTypeDefinition, attributeTypeName: String): WinRtCustomAttributeDefinition? =
        type.customAttributes.firstOrNull { it.typeName == attributeTypeName }

    fun hasAttribute(type: WinRtTypeDefinition, attributeTypeName: String): Boolean =
        getAttribute(type, attributeTypeName) != null

    fun getNumberOfAttributes(type: WinRtTypeDefinition, attributeTypeName: String): Int =
        type.customAttributes.count { it.typeName == attributeTypeName }

    fun getAttributeValue(attribute: WinRtCustomAttributeDefinition, index: Int): WinRtCustomAttributeValue? =
        attribute.fixedArguments.getOrNull(index)

    fun getContractVersion(type: WinRtTypeDefinition): Long? = type.availability.contractVersion?.version

    fun getVersion(type: WinRtTypeDefinition): Long? = type.availability.version

    fun getContractPlatform(type: WinRtTypeDefinition): String? = type.availability.contractVersion?.platformVersion

    fun methodSignature(method: WinRtMethodDefinition): WinRtMethodSignatureDescriptor =
        WinRtMethodSignatureDescriptor(
            method = method,
            returnType = method.returnType,
            parameters = method.parameters.map { parameter ->
                WinRtMethodSignatureParameter(
                    parameter = parameter,
                    category = parameterCategory(parameter),
                )
            },
        )

    fun parameterCategory(parameter: WinRtParameterDefinition): WinRtMetadataParameterCategory {
        return metadataParameterCategoryFor(parameter)
    }

    fun isRemoveOverload(method: WinRtMethodDefinition): Boolean =
        method.isRemoveOverload || (method.isSpecialName && method.name.startsWith("remove_"))

    fun isNoException(method: WinRtMethodDefinition): Boolean =
        isRemoveOverload(method) || method.isNoException

    fun isNoException(property: WinRtPropertyDefinition): Boolean = property.isNoException

    fun isExclusiveTo(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Interface && type.isExclusiveTo

    fun isOverridable(implementation: WinRtInterfaceImplementationDefinition): Boolean =
        implementation.isOverridable

    fun isProjectionInternal(type: WinRtTypeDefinition): Boolean = type.isProjectionInternal

    fun isFlagsEnum(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Enum && type.customAttributes.any { it.typeName == SYSTEM_FLAGS_ATTRIBUTE }

    fun isApiContractType(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Struct && type.isApiContract

    fun isAttributeType(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass && type.isAttributeType

    fun isParameterizedType(type: WinRtTypeDefinition): Boolean =
        type.genericParameterCount > 0 || type.genericParameters.isNotEmpty()

    fun isStatic(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass && type.isStaticType

    fun isConstructor(method: WinRtMethodDefinition): Boolean =
        method.isRuntimeSpecialName && method.name == ".ctor"

    fun hasDefaultConstructor(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.RuntimeClass &&
            type.methods.any { method -> isConstructor(method) && method.parameters.isEmpty() }

    fun isSpecial(method: WinRtMethodDefinition): Boolean =
        method.isSpecialName || method.isRuntimeSpecialName

    fun isStatic(method: WinRtMethodDefinition): Boolean = method.isStatic

    fun getDelegateInvoke(type: WinRtTypeDefinition): WinRtMethodDefinition? =
        type.methods.firstOrNull { method -> method.name == "Invoke" && method.isSpecialName }
            ?: type.methods.firstOrNull { method -> method.name == "Invoke" }

    fun getDefaultInterface(type: WinRtTypeDefinition): WinRtTypeRef? =
        type.defaultInterface
            ?: type.implementedInterfaces.firstOrNull { it.isDefault }?.interfaceType
            ?: if (type.implementedInterfaces.isEmpty()) null else {
                throw IllegalArgumentException("Type '${type.qualifiedName}' does not have a default interface")
            }

    fun getPropertyMethods(property: WinRtPropertyDefinition): WinRtPropertyAccessorDescriptor =
        WinRtPropertyAccessorDescriptor(
            getterMethodName = property.getterMethodName,
            setterMethodName = property.setterMethodName,
            getterMethodRowId = property.getterMethodRowId,
            setterMethodRowId = property.setterMethodRowId,
            isStatic = property.isStatic,
            hasValidAccessors = property.hasValidAccessors,
        )

    fun getEventMethods(event: WinRtEventDefinition): WinRtEventAccessorDescriptor =
        WinRtEventAccessorDescriptor(
            addMethodName = event.addMethodName,
            removeMethodName = event.removeMethodName,
            addMethodRowId = event.addMethodRowId,
            removeMethodRowId = event.removeMethodRowId,
            isStatic = event.isStatic,
            hasValidAccessors = event.hasValidAccessors,
        )

    fun getAttributedTypes(type: WinRtTypeDefinition): List<WinRtAttributedFactoryDescriptor> {
        val factories = linkedMapOf<String, WinRtAttributedFactoryDescriptor>()
        fun put(interfaceType: WinRtTypeRef?, activatable: Boolean = false, statics: Boolean = false, composable: Boolean = false) {
            if (interfaceType == null) return
            val resolved = resolveTypeReference(interfaceType, type.namespace, typesByQualifiedName)
            val current = factories[resolved.displayName]
            factories[resolved.displayName] = WinRtAttributedFactoryDescriptor(
                interfaceName = resolved.displayName,
                interfaceType = resolved.type,
                type = resolved.definitionType,
                activatable = (current?.activatable ?: false) || activatable,
                statics = (current?.statics ?: false) || statics,
                composable = (current?.composable ?: false) || composable,
            )
        }
        put(type.activation.activatableFactoryInterface, activatable = type.activation.isActivatable)
        type.activation.staticInterfaces.forEach { staticInterface -> put(staticInterface, statics = true) }
        put(type.activation.composableFactoryInterface, composable = true)
        return factories.values.toList()
    }

    fun getDefaultAndExclusiveInterfaces(type: WinRtTypeDefinition): Pair<WinRtRuntimeClassInterfaceDescriptor?, List<WinRtRuntimeClassInterfaceDescriptor>> {
        val closure = closureResolver.resolveRuntimeClass(type)
        val defaultInterface = closure.instanceInterfaces.firstOrNull { it.isDefault }
        val exclusiveInterfaces = closure.instanceInterfaces.filter { !it.isDefault && it.isExclusiveTo }
        return defaultInterface to exclusiveInterfaces
    }

    fun getFastAbiClassForClass(type: WinRtTypeDefinition): WinRtFastAbiClassDescriptor? {
        if (!type.isFastAbi) return null
        val closure = closureResolver.resolveRuntimeClass(type)
        val defaultInterface = closure.fastAbiInterfaces.firstOrNull { it.isDefault }
        val otherInterfaces = closure.fastAbiInterfaces.filterNot { it.isDefault }
        return WinRtFastAbiClassDescriptor(
            classType = type,
            defaultInterface = defaultInterface,
            otherInterfaces = otherInterfaces,
            propertySlots = fastAbiPropertySlots(closure),
        )
    }

    fun getFastAbiClassForInterface(type: WinRtTypeDefinition): WinRtFastAbiClassDescriptor? {
        if (!type.isExclusiveTo) return null
        val owner = normalizedModel.namespaces
            .flatMap(WinRtNamespace::types)
            .firstOrNull { candidate ->
                candidate.kind == WinRtTypeKind.RuntimeClass &&
                    candidate.isFastAbi &&
                    candidate.implementedInterfaces.any { implemented ->
                        resolveTypeReference(implemented.interfaceType, candidate.namespace, typesByQualifiedName).definitionQualifiedName == type.qualifiedName
                    }
            }
            ?: return null
        return getFastAbiClassForClass(owner)
    }

    fun getGcPressureAmount(type: WinRtTypeDefinition): Int = type.gcPressureAmount

    fun resolveType(type: WinRtTypeRef, currentNamespace: String): WinRtTypeDefinition? {
        val normalized = type.normalized()
        val qualifiedName = normalized.qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    private fun fastAbiPropertySlots(closure: WinRtRuntimeClassClosureDescriptor): List<WinRtFastAbiPropertySlot> {
        var vtableStartIndex = 6
        val slots = mutableListOf<WinRtFastAbiPropertySlot>()
        closure.fastAbiInterfaces.forEach { interfaceDescriptor ->
            interfaceDescriptor.definitionType?.properties.orEmpty().forEach { property ->
                slots += WinRtFastAbiPropertySlot(
                    propertyName = property.name,
                    getterMethodName = property.getterMethodName,
                    setterMethodName = property.setterMethodName,
                    vtableStartIndex = vtableStartIndex,
                )
            }
            val methodCount = interfaceDescriptor.definitionType?.methods?.size ?: 0
            vtableStartIndex += methodCount
        }
        return slots
    }

    companion object {
        private const val SYSTEM_FLAGS_ATTRIBUTE = "System.FlagsAttribute"
    }
}

fun WinRtMetadataModel.semanticHelpers(): WinRtMetadataSemanticHelpers =
    WinRtMetadataSemanticHelpers(this)

internal fun metadataParameterCategoryFor(parameter: WinRtParameterDefinition): WinRtMetadataParameterCategory {
    val type = parameter.type.normalized()
    if (type.kind == WinRtTypeRefKind.Array) {
        return when {
            parameter.isInParameter -> WinRtMetadataParameterCategory.PassArray
            parameter.typeIsByRef -> WinRtMetadataParameterCategory.ReceiveArray
            parameter.isOutParameter -> WinRtMetadataParameterCategory.FillArray
            parameter.direction == WinRtParameterDirection.Out -> WinRtMetadataParameterCategory.FillArray
            else -> WinRtMetadataParameterCategory.PassArray
        }
    }
    return when {
        parameter.isOutParameter -> WinRtMetadataParameterCategory.Out
        parameter.typeIsByRef -> WinRtMetadataParameterCategory.Ref
        else -> WinRtMetadataParameterCategory.In
    }
}
