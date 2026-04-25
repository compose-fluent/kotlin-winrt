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

data class WinRtValueTypeFieldDescriptor(
    val field: WinRtFieldDefinition,
    val type: WinRtTypeClassificationDescriptor,
    val offset: Int?,
    val abiSize: Int?,
    val abiAlignment: Int?,
    val isBlittable: Boolean,
)

data class WinRtValueTypeDescriptor(
    val type: WinRtTypeDefinition,
    val isValueType: Boolean,
    val isBlittable: Boolean,
    val abiSize: Int?,
    val abiAlignment: Int?,
    val layout: WinRtTypeLayout,
    val fields: List<WinRtValueTypeFieldDescriptor>,
    val enumUnderlyingType: WinRtIntegralType?,
    val enumMembers: List<WinRtEnumMemberDefinition>,
    val isFlagsEnum: Boolean,
    val mappedType: WinRtMappedTypeDescriptor?,
) {
    val requiresAbiCompanionShape: Boolean
        get() = isValueType && !isBlittable
}

data class WinRtGenericAbiDelegateDescriptor(
    val abiDelegateName: String,
    val sourceGenericType: WinRtTypeRef,
    val abiDelegateTypesKey: String,
    val genericArguments: List<WinRtTypeRef>,
)

data class WinRtGenericTypeInstantiationDescriptor(
    val type: WinRtTypeRef,
    val definitionType: WinRtTypeDefinition?,
    val instantiationClassName: String,
    val genericArguments: List<WinRtTypeRef>,
    val implementsCcwInterface: Boolean,
)

data class WinRtGenericAbiInventory(
    val genericAbiDelegates: List<WinRtGenericAbiDelegateDescriptor>,
    val genericTypeInstantiations: List<WinRtGenericTypeInstantiationDescriptor>,
)

data class WinRtGenericScopeParameterDescriptor(
    val parameter: WinRtGenericParameterDefinition,
    val projectedName: String,
    val abiName: String,
)

data class WinRtGenericScopeDescriptor(
    val ownerType: WinRtTypeDefinition,
    val parameters: List<WinRtGenericScopeParameterDescriptor>,
)

data class WinRtGenericSignatureUsageDescriptor(
    val containsProjectedGenericParameter: Boolean,
    val containsAbiGenericParameter: Boolean,
)

data class WinRtExplicitImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String?,
    val declarationName: String?,
    val bodyName: String?,
    val isPrivateBody: Boolean,
)

class WinRtMetadataSemanticHelpers(private val model: WinRtMetadataModel) {
    private val normalizedModel = model.normalized()
    private val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
    private val closureResolver = normalizedModel.closureResolver()
    private val typeClassifier = normalizedModel.typeClassifier()

    fun getMappedTypesInNamespace(namespace: String): List<WinRtMappedTypeDescriptor> =
        typeClassifier.mappedTypesInNamespace(namespace)

    fun getMappedType(namespace: String, name: String): WinRtMappedTypeDescriptor? =
        typeClassifier.mappedType(namespace, name)

    fun getMappedType(type: WinRtTypeRef, currentNamespace: String): WinRtMappedTypeDescriptor? =
        typeClassifier.classify(type, currentNamespace).mappedType

    fun getAttribute(type: WinRtTypeDefinition, attributeTypeName: String): WinRtCustomAttributeDefinition? =
        type.customAttributes.firstOrNull { it.typeName == attributeTypeName }

    fun hasAttribute(type: WinRtTypeDefinition, attributeTypeName: String): Boolean =
        getAttribute(type, attributeTypeName) != null

    fun getNumberOfAttributes(type: WinRtTypeDefinition, attributeTypeName: String): Int =
        type.customAttributes.count { it.typeName == attributeTypeName }

    fun getAttributeValue(attribute: WinRtCustomAttributeDefinition, index: Int): WinRtCustomAttributeValue? =
        attribute.fixedArguments.getOrNull(index)

    fun projectedAttributes(type: WinRtTypeDefinition, enablePlatformAttributes: Boolean = true): List<WinRtProjectedAttributeDescriptor> =
        type.projectedAttributes(enablePlatformAttributes)

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

    fun genericScope(type: WinRtTypeDefinition): WinRtGenericScopeDescriptor {
        val normalized = type.normalized()
        val parameters = if (normalized.genericParameters.isNotEmpty()) {
            normalized.genericParameters
        } else {
            (0 until normalized.genericParameterCount).map { index ->
                WinRtGenericParameterDefinition(name = "T$index", index = index)
            }
        }
        return WinRtGenericScopeDescriptor(
            ownerType = normalized,
            parameters = parameters.map { parameter ->
                val projectedName = parameter.name.ifBlank { "T${parameter.index}" }
                WinRtGenericScopeParameterDescriptor(
                    parameter = parameter,
                    projectedName = projectedName,
                    abiName = "${projectedName}Abi",
                )
            },
        )
    }

    fun genericSignatureUsage(type: WinRtTypeRef): WinRtGenericSignatureUsageDescriptor {
        var contains = false
        fun visit(current: WinRtTypeRef) {
            if (current.kind == WinRtTypeRefKind.GenericTypeParameter) {
                contains = true
            }
            current.elementType?.let(::visit)
            current.typeArguments.forEach(::visit)
        }
        visit(type.normalized())
        return WinRtGenericSignatureUsageDescriptor(
            containsProjectedGenericParameter = contains,
            containsAbiGenericParameter = contains,
        )
    }

    fun explicitImplementations(type: WinRtTypeDefinition): List<WinRtExplicitImplementationDescriptor> =
        type.normalized().methodImplementations.map { implementation ->
            val body = implementation.body.name
                ?.let { bodyName -> type.methods.firstOrNull { it.name == bodyName } }
            WinRtExplicitImplementationDescriptor(
                classTypeName = implementation.classTypeName,
                interfaceTypeName = implementation.declaration.ownerTypeName,
                declarationName = implementation.declaration.name,
                bodyName = implementation.body.name,
                isPrivateBody = body?.visibility == WinRtMethodVisibility.Private,
            )
        }

    fun isRemoveOverload(method: WinRtMethodDefinition): Boolean =
        method.isRemoveOverload || (method.isSpecialName && method.name.startsWith("remove_"))

    fun isNoException(method: WinRtMethodDefinition): Boolean =
        isRemoveOverload(method) || method.isNoException

    fun isNoException(property: WinRtPropertyDefinition): Boolean = property.isNoException

    fun isExclusiveTo(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Interface && type.isExclusiveTo

    fun doesAbiInterfaceImplementCcwInterface(type: WinRtTypeDefinition): Boolean =
        io.github.kitectlab.winrt.metadata.doesAbiInterfaceImplementCcwInterface(type)

    fun isOverridable(implementation: WinRtInterfaceImplementationDefinition): Boolean =
        implementation.isOverridable

    fun isProjectionInternal(type: WinRtTypeDefinition): Boolean = type.isProjectionInternal

    fun isFlagsEnum(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Enum && type.customAttributes.any { it.typeName == SYSTEM_FLAGS_ATTRIBUTE }

    fun isValueType(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Struct || type.kind == WinRtTypeKind.Enum

    fun isTypeBlittable(type: WinRtTypeDefinition): Boolean =
        valueTypeDescriptor(type).isBlittable

    fun valueTypeDescriptor(type: WinRtTypeDefinition): WinRtValueTypeDescriptor {
        val normalizedType = type.normalized()
        val classification = typeClassifier.classify(normalizedType)
        val fields = normalizedType.fields.map { field ->
            val fieldType = typeClassifier.classify(field.type, normalizedType.namespace)
            WinRtValueTypeFieldDescriptor(
                field = field,
                type = fieldType,
                offset = field.offset,
                abiSize = field.abiSize,
                abiAlignment = field.abiAlignment,
                isBlittable = field.isBlittable || fieldType.isBlittable || fieldType.projectionCategory == WinRtProjectionCategory.Fundamental,
            )
        }
        return WinRtValueTypeDescriptor(
            type = normalizedType,
            isValueType = isValueType(normalizedType),
            isBlittable = when (normalizedType.kind) {
                WinRtTypeKind.Enum -> true
                WinRtTypeKind.Struct -> normalizedType.isBlittable
                else -> false
            },
            abiSize = normalizedType.abiSize,
            abiAlignment = normalizedType.abiAlignment,
            layout = normalizedType.layout,
            fields = fields,
            enumUnderlyingType = normalizedType.enumUnderlyingType,
            enumMembers = normalizedType.enumMembers,
            isFlagsEnum = isFlagsEnum(normalizedType),
            mappedType = classification.mappedType,
        )
    }

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

    fun genericAbiInventory(): WinRtGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        normalizedModel.namespaces.flatMap(WinRtNamespace::types).forEach { type ->
            collector.addGenericTypeReferencesInType(type)
        }
        return collector.toInventory()
    }

    fun collectGenericAbiInventory(type: WinRtTypeDefinition): WinRtGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        collector.addGenericTypeReferencesInType(type.normalized())
        return collector.toInventory()
    }

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

    private inner class GenericAbiInventoryCollector {
        private val abiDelegates = linkedMapOf<String, WinRtGenericAbiDelegateDescriptor>()
        private val typeInstantiations = linkedMapOf<String, WinRtGenericTypeInstantiationDescriptor>()

        fun addGenericTypeReferencesInType(type: WinRtTypeDefinition) {
            when (type.kind) {
                WinRtTypeKind.Delegate -> getDelegateInvoke(type)?.let { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
                WinRtTypeKind.Interface -> addGenericTypeReferencesInInterfaceType(type)
                else -> Unit
            }
        }

        fun toInventory(): WinRtGenericAbiInventory =
            WinRtGenericAbiInventory(
                genericAbiDelegates = abiDelegates.values.sortedWith(compareBy({ it.abiDelegateTypesKey }, { it.abiDelegateName })),
                genericTypeInstantiations = typeInstantiations.values.sortedBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName),
            )

        private fun addGenericTypeReferencesInInterfaceType(type: WinRtTypeDefinition) {
            type.methods.filterNot(::isSpecial).forEach { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
            type.properties.forEach { property -> addIfGenericTypeReference(property.type, property.type.kind == WinRtTypeRefKind.Array, type.namespace) }
            type.events.forEach { event -> addIfGenericTypeReference(event.delegateType, isArray = false, currentNamespace = type.namespace) }
            type.implementedInterfaces.forEach { implemented ->
                addIfGenericTypeReference(implemented.interfaceType, isArray = false, currentNamespace = type.namespace)
            }
        }

        private fun addGenericTypeReferencesInMethod(method: WinRtMethodDefinition, currentNamespace: String) {
            method.parameters.forEach { parameter ->
                addIfGenericTypeReference(parameter.type, parameter.type.kind == WinRtTypeRefKind.Array, currentNamespace = currentNamespace)
            }
            addIfGenericTypeReference(method.returnType, method.returnType.kind == WinRtTypeRefKind.Array, currentNamespace = currentNamespace)
        }

        private fun addIfGenericTypeReference(type: WinRtTypeRef, isArray: Boolean, currentNamespace: String) {
            val normalized = type.normalized()
            when (normalized.kind) {
                WinRtTypeRefKind.Named -> {
                    normalized.typeArguments.forEach { argument ->
                        addIfGenericTypeReference(argument, isArray = false, currentNamespace = currentNamespace)
                    }
                    val resolved = resolveTypeReference(normalized, currentNamespace, typesByQualifiedName)
                    val definitionType = resolved.definitionType
                    if (normalized.typeArguments.isNotEmpty()) {
                        val sourceType = resolved.type
                        val className = escapeTypeNameForIdentifier(sourceType.typeName)
                        typeInstantiations.putIfAbsent(
                            className,
                            WinRtGenericTypeInstantiationDescriptor(
                                type = sourceType,
                                definitionType = definitionType,
                                instantiationClassName = className,
                                genericArguments = sourceType.typeArguments,
                                implementsCcwInterface = definitionType?.let(::doesAbiInterfaceImplementCcwInterface) ?: false,
                            ),
                        )
                        addAbiDelegatesForType(sourceType)
                    }
                    if (isArray) {
                        addAbiDelegatesForArray(normalized, currentNamespace)
                    }
                    if (definitionType?.kind == WinRtTypeKind.Struct) {
                        definitionType.fields.forEach { field ->
                            addIfGenericTypeReference(field.type, field.type.kind == WinRtTypeRefKind.Array, definitionType.namespace)
                        }
                    }
                }

                WinRtTypeRefKind.Array -> {
                    val element = normalized.elementType ?: WinRtTypeRef.unknown()
                    addIfGenericTypeReference(element, isArray = false, currentNamespace = currentNamespace)
                    addAbiDelegatesForArray(element, currentNamespace)
                }

                else -> {
                    if (isArray) {
                        addAbiDelegatesForArray(normalized, currentNamespace)
                    }
                }
            }
        }

        private fun addAbiDelegatesForArray(elementType: WinRtTypeRef, currentNamespace: String) {
            addAbiDelegatesForType(
                WinRtTypeRef.named(
                    qualifiedName = "Windows.Foundation.Collections.IVector",
                    typeArguments = listOf(resolveTypeReference(elementType, currentNamespace, typesByQualifiedName).type),
                ),
            )
        }

        private fun addAbiDelegatesForType(type: WinRtTypeRef) {
            val normalized = type.normalized()
            val typeName = normalized.qualifiedName ?: return
            if (normalized.typeArguments.isEmpty()) return
            val namespace = typeName.substringBeforeLast('.', "")
            val name = typeName.substringAfterLast('.')
            if (namespace != "Windows.Foundation" && namespace != "Windows.Foundation.Collections") return
            val requiredIndexes = requiredAbiDelegateArgumentIndexes(name, normalized.typeArguments)
            requiredIndexes.forEach { index ->
                val argument = normalized.typeArguments.getOrNull(index) ?: return@forEach
                val argumentDescriptor = typeClassifier.classify(argument, namespace)
                val abiTypeKey = argumentDescriptor.type.typeName
                val delegateName = "_${delegateOperationFor(name, index)}_${escapeTypeNameForIdentifier(abiTypeKey)}"
                abiDelegates.putIfAbsent(
                    abiTypeKey,
                    WinRtGenericAbiDelegateDescriptor(
                        abiDelegateName = delegateName,
                        sourceGenericType = normalized,
                        abiDelegateTypesKey = abiTypeKey,
                        genericArguments = normalized.typeArguments,
                    ),
                )
            }
        }

        private fun requiredAbiDelegateArgumentIndexes(typeName: String, arguments: List<WinRtTypeRef>): List<Int> =
            when (typeName) {
                "IIterator", "IVector", "IVectorView", "EventHandler", "IReference", "IMapChangedEventArgs", "IAsyncOperation", "AsyncActionProgressHandler" ->
                    listOf(0).filter { index -> arguments.getOrNull(index)?.isAbiDelegateRequired() == true }
                "IKeyValuePair", "IMap", "IMapView", "TypedEventHandler" ->
                    listOf(0, 1).filter { index -> arguments.getOrNull(index)?.isAbiDelegateRequired() == true }
                "IAsyncOperationWithProgress" ->
                    listOf(0, 1).filter { index -> arguments.getOrNull(index)?.isAbiDelegateRequired() == true }
                "AsyncOperationProgressHandler" ->
                    listOf(1).filter { index -> arguments.getOrNull(index)?.isAbiDelegateRequired() == true }
                else -> emptyList()
            }

        private fun delegateOperationFor(typeName: String, argumentIndex: Int): String =
            when (typeName) {
                "IKeyValuePair" -> if (argumentIndex == 0) "get_Key" else "get_Value"
                "IMap", "IMapView" -> if (argumentIndex == 0) "has_key" else "lookup"
                "EventHandler", "TypedEventHandler", "AsyncOperationProgressHandler", "AsyncActionProgressHandler" -> "invoke"
                "IReference" -> "get_Value"
                "IMapChangedEventArgs", "IAsyncOperation", "IAsyncOperationWithProgress" -> "get"
                else -> "get_at"
            }

        private fun WinRtTypeRef.isAbiDelegateRequired(): Boolean {
            val normalized = normalized()
            return when (normalized.kind) {
                WinRtTypeRefKind.Named -> {
                    val descriptor = typeClassifier.classify(normalized, "")
                    when (descriptor.projectionCategory) {
                        WinRtProjectionCategory.Fundamental -> descriptor.typeName != "String"
                        WinRtProjectionCategory.Guid,
                        WinRtProjectionCategory.Enum,
                        WinRtProjectionCategory.Struct,
                        -> true
                        else -> false
                    }
                }

                WinRtTypeRefKind.GenericTypeParameter,
                WinRtTypeRefKind.MethodTypeParameter,
                WinRtTypeRefKind.Array,
                WinRtTypeRefKind.Unknown,
                -> false
            }
        }
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

private fun doesAbiInterfaceImplementCcwInterface(type: WinRtTypeDefinition): Boolean =
    type.kind == WinRtTypeKind.Interface && type.isExclusiveTo

private fun escapeTypeNameForIdentifier(typeName: String): String =
    typeName.replace(Regex("""[\s:<>,.]"""), "_")
