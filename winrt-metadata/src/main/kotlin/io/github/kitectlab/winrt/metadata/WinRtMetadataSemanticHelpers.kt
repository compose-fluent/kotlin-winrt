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
    val kind: WinRtAttributedFactoryKind? = null,
    val activatable: Boolean = false,
    val statics: Boolean = false,
    val composable: Boolean = false,
    val visible: Boolean = false,
)

data class WinRtFastAbiPropertySlot(
    val propertyName: String,
    val getterMethodName: String?,
    val setterMethodName: String?,
    val vtableStartIndex: Int,
    val getterVtableIndex: Int?,
    val setterVtableIndex: Int?,
)

data class WinRtFastAbiClassDescriptor(
    val classType: WinRtTypeDefinition,
    val defaultInterface: WinRtRuntimeClassInterfaceDescriptor?,
    val otherInterfaces: List<WinRtRuntimeClassInterfaceDescriptor>,
    val propertySlots: List<WinRtFastAbiPropertySlot>,
) {
    private val otherInterfaceNames = otherInterfaces.map(WinRtRuntimeClassInterfaceDescriptor::interfaceName).toSet()
    private val getterNames = propertySlots.mapNotNull { slot -> slot.getterMethodName?.let { slot.propertyName } }.toSet()
    private val setterNames = propertySlots.mapNotNull { slot -> slot.setterMethodName?.let { slot.propertyName } }.toSet()

    fun containsOtherInterface(interfaceName: String): Boolean = interfaceName in otherInterfaceNames
    fun containsGetter(propertyName: String): Boolean = propertyName in getterNames
    fun containsSetter(propertyName: String): Boolean = propertyName in setterNames
}

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

data class WinRtSemanticValueDescriptor(
    val type: WinRtTypeRef,
    val category: WinRtProjectionCategory,
    val isValueType: Boolean,
    val isBlittable: Boolean,
    val isBlittableForArray: Boolean,
    val mappedType: WinRtMappedTypeDescriptor?,
)

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
    val derivedGenericInterfaces: List<String> = emptyList(),
)

data class WinRtGenericInstantiationWorklistDescriptor(
    val pending: List<WinRtGenericTypeInstantiationDescriptor>,
    val written: List<WinRtGenericTypeInstantiationDescriptor> = emptyList(),
) {
    fun markWritten(instantiationClassName: String): WinRtGenericInstantiationWorklistDescriptor {
        val moved = pending.firstOrNull { it.instantiationClassName == instantiationClassName } ?: return this
        return copy(
            pending = pending.filterNot { it.instantiationClassName == instantiationClassName },
            written = (written + moved)
                .distinctBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName)
                .sortedBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }

    fun enqueue(discovered: List<WinRtGenericTypeInstantiationDescriptor>): WinRtGenericInstantiationWorklistDescriptor {
        val known = (pending + written).map(WinRtGenericTypeInstantiationDescriptor::instantiationClassName).toSet()
        return copy(
            pending = (pending + discovered.filterNot { it.instantiationClassName in known })
                .distinctBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName)
                .sortedBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }
}

data class WinRtComponentActivatableClassDescriptor(
    val className: String,
    val classType: WinRtTypeDefinition,
    val attributedFactories: List<WinRtAttributedFactoryDescriptor>,
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

data class WinRtProjectedMethodSignatureDescriptor(
    val methodName: String,
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
) {
    fun signatureEquals(other: WinRtProjectedMethodSignatureDescriptor): Boolean =
        returnTypeName == other.returnTypeName && parameterTypeNames == other.parameterTypeNames
}

data class WinRtPrivateImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String,
    val interfaceMemberName: String,
    val privateMethodName: String,
    val isImplementedAsPrivateMethod: Boolean,
)

data class WinRtMappedInterfaceImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String,
    val probeMemberName: String?,
    val isImplementedAsPrivateMappedInterface: Boolean,
)

data class WinRtCustomQueryInterfaceDescriptor(
    val classTypeName: String,
    val hasBaseClass: Boolean,
    val overridableInterfaceNames: List<String>,
    val visibility: String,
    val overridableModifier: String,
    val delegatesToBase: Boolean,
)

data class WinRtProjectionContextSemanticsDescriptor(
    val internalAccessibility: String,
    val enumAccessibility: String,
    val embedded: Boolean,
    val publicExclusiveTo: Boolean,
    val idicExclusiveTo: Boolean,
    val partialFactory: Boolean,
    val partialFactoryFallbackExpression: String,
)

data class WinRtTypeProjectionContextDescriptor(
    val typeName: String,
    val accessibility: String,
    val enumAccessibility: String,
    val exclusiveToAccessibility: String,
    val writesVtablePointer: Boolean,
    val supportsDynamicInterfaceCastable: Boolean,
    val partialFactoryFallbackExpression: String,
)

data class WinRtManualInterfaceDescriptor(
    val typeName: String,
    val manuallyGenerated: Boolean,
    val reason: String? = null,
)

data class WinRtClassMemberMergeDescriptor(
    val classTypeName: String,
    val interfaceDescriptors: List<WinRtClassInterfaceMemberDescriptor>,
    val mergedProperties: List<WinRtMergedPropertyDescriptor>,
)

data class WinRtClassInterfaceMemberDescriptor(
    val interfaceTypeName: String,
    val target: String,
    val isDefaultInterface: Boolean,
    val isOverridableInterface: Boolean,
    val isProtectedInterface: Boolean,
    val isManuallyGeneratedInterface: Boolean,
    val callStaticMethod: Boolean,
    val mappedTypeHasCustomMembers: Boolean,
)

data class WinRtMergedPropertyDescriptor(
    val propertyName: String,
    val propertyTypeName: String,
    val getterTarget: String?,
    val getterPlatform: String?,
    val setterTarget: String?,
    val setterPlatform: String?,
    val isOverridable: Boolean,
    val isPublic: Boolean,
    val isPrivate: Boolean,
    val getterStaticCallTarget: String?,
    val setterStaticCallTarget: String?,
)

data class WinRtMethodVtableDescriptor(
    val ownerTypeName: String,
    val methodName: String,
    val methodRowId: Int?,
    val vmethodIndex: Int,
    val vmethodName: String,
    val slotIndex: Int,
)

data class WinRtAuxiliaryTableSemanticBoundaryDescriptor(
    val tableName: String,
    val rowCount: Int,
    val modeled: Boolean,
    val projectionAffecting: Boolean,
    val note: String,
)

data class WinRtMetadataParityAuditEntry(
    val cswinrtArea: String,
    val cswinrtEntryPoint: String,
    val kotlinOwner: String,
    val closed: Boolean,
)

class WinRtMetadataSemanticHelpers(private val model: WinRtMetadataModel) {
    private val normalizedModel = model.normalized()
    private val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
    private val closureResolver = normalizedModel.closureResolver()
    private val typeClassifier = normalizedModel.typeClassifier()
    private val typeSemanticsResolver = normalizedModel.typeSemanticsResolver()

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

    fun getExclusiveToType(type: WinRtTypeDefinition): WinRtTypeDefinition? {
        if (!isExclusiveTo(type)) return null
        val exclusiveTypeName = type.customAttributes
            .firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO_ATTRIBUTE }
            ?.fixedArguments
            ?.mapNotNull(WinRtCustomAttributeValue::stringValue)
            ?.firstOrNull()
        if (exclusiveTypeName != null) {
            resolveType(WinRtTypeRef.fromDisplayName(exclusiveTypeName), type.namespace)?.let { return it }
        }
        return normalizedModel.namespaces
            .flatMap(WinRtNamespace::types)
            .firstOrNull { candidate ->
                candidate.kind == WinRtTypeKind.RuntimeClass &&
                    candidate.implementedInterfaces.any { implemented ->
                        resolveTypeReference(implemented.interfaceType, candidate.namespace, typesByQualifiedName).definitionQualifiedName == type.qualifiedName
                    }
            }
    }

    fun doesAbiInterfaceImplementCcwInterface(type: WinRtTypeDefinition): Boolean =
        doesAbiInterfaceImplementCcwInterface(type, WinRtMetadataProjectionContext(sources = emptyList()))

    fun doesAbiInterfaceImplementCcwInterface(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext,
    ): Boolean =
        context.component && context.filter.includes(type) && isExclusiveTo(type)

    fun isOverridable(implementation: WinRtInterfaceImplementationDefinition): Boolean =
        implementation.isOverridable

    fun isProjectionInternal(type: WinRtTypeDefinition): Boolean = type.isProjectionInternal

    fun isFlagsEnum(type: WinRtTypeDefinition): Boolean =
        type.kind == WinRtTypeKind.Enum && type.customAttributes.any { it.typeName == SYSTEM_FLAGS_ATTRIBUTE }

    fun isValueType(type: WinRtTypeDefinition): Boolean =
        semanticValueDescriptor(WinRtTypeRef.named(type.qualifiedName), type.namespace).isValueType

    fun isTypeBlittable(type: WinRtTypeDefinition): Boolean =
        semanticValueDescriptor(WinRtTypeRef.named(type.qualifiedName), type.namespace).isBlittable

    fun isTypeBlittable(type: WinRtTypeRef, currentNamespace: String, forArray: Boolean = false): Boolean =
        semanticValueDescriptor(type, currentNamespace, forArray).isBlittable

    fun isValueType(type: WinRtTypeRef, currentNamespace: String): Boolean =
        semanticValueDescriptor(type, currentNamespace).isValueType

    fun semanticValueDescriptor(
        type: WinRtTypeRef,
        currentNamespace: String,
        forArray: Boolean = false,
    ): WinRtSemanticValueDescriptor {
        val classification = typeClassifier.classify(type, currentNamespace)
        val resolvedType = classification.definitionType
        val isGenericInstance = classification.type.typeArguments.isNotEmpty()
        val isValueType = when {
            isGenericInstance -> false
            classification.projectionCategory == WinRtProjectionCategory.Object -> false
            classification.projectionCategory == WinRtProjectionCategory.String -> false
            classification.projectionCategory == WinRtProjectionCategory.Enum -> true
            classification.projectionCategory == WinRtProjectionCategory.Struct ||
                classification.projectionCategory == WinRtProjectionCategory.ApiContract -> {
                val mapped = classification.mappedType
                if (mapped != null) {
                    true
                } else {
                    resolvedType?.fields.orEmpty().all { field -> isValueType(field.type, resolvedType?.namespace ?: currentNamespace) }
                }
            }
            classification.projectionCategory == WinRtProjectionCategory.Fundamental -> classification.typeName != "String"
            classification.projectionCategory == WinRtProjectionCategory.Guid ||
                classification.projectionCategory == WinRtProjectionCategory.Type -> true
            else -> false
        }
        val isBlittable = when {
            isGenericInstance -> false
            classification.projectionCategory == WinRtProjectionCategory.Object -> false
            classification.projectionCategory == WinRtProjectionCategory.String -> false
            classification.projectionCategory == WinRtProjectionCategory.Enum -> !forArray
            classification.projectionCategory == WinRtProjectionCategory.Struct ||
                classification.projectionCategory == WinRtProjectionCategory.ApiContract -> {
                val mapped = classification.mappedType
                if (mapped != null) {
                    !mapped.requiresMarshaling
                } else {
                    resolvedType?.fields.orEmpty().all { field -> isTypeBlittable(field.type, resolvedType?.namespace ?: currentNamespace) }
                }
            }
            classification.projectionCategory == WinRtProjectionCategory.Fundamental ->
                classification.typeName != "String" && classification.typeName != "Char" && classification.typeName != "Boolean"
            classification.projectionCategory == WinRtProjectionCategory.Guid ||
                classification.projectionCategory == WinRtProjectionCategory.Type -> true
            else -> false
        }
        val isBlittableForArray = if (forArray) {
            isBlittable
        } else {
            semanticValueDescriptor(type, currentNamespace, forArray = true).isBlittable
        }
        return WinRtSemanticValueDescriptor(
            type = classification.type,
            category = classification.projectionCategory,
            isValueType = isValueType,
            isBlittable = isBlittable,
            isBlittableForArray = isBlittableForArray,
            mappedType = classification.mappedType,
        )
    }

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
            isBlittable = isTypeBlittable(normalizedType),
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

    fun isPType(type: WinRtTypeDefinition): Boolean = isParameterizedType(type)

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

    fun getDefaultInterfaceSemantics(type: WinRtTypeDefinition): WinRtTypeSemantics {
        val defaultInterface = getDefaultInterface(type)
            ?: throw IllegalArgumentException("Class does not have a default interface: ${type.qualifiedName}")
        return typeSemanticsResolver.resolve(defaultInterface, type.namespace, type.genericParameters)
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
        fun put(
            interfaceType: WinRtTypeRef?,
            kind: WinRtAttributedFactoryKind? = null,
            activatable: Boolean = false,
            statics: Boolean = false,
            composable: Boolean = false,
            visible: Boolean = false,
        ) {
            if (interfaceType == null) return
            val resolved = resolveTypeReference(interfaceType, type.namespace, typesByQualifiedName)
            val current = factories[resolved.displayName]
            factories[resolved.displayName] = WinRtAttributedFactoryDescriptor(
                interfaceName = resolved.displayName,
                interfaceType = resolved.type,
                type = resolved.definitionType,
                kind = current?.kind ?: kind,
                activatable = (current?.activatable ?: false) || activatable,
                statics = (current?.statics ?: false) || statics,
                composable = (current?.composable ?: false) || composable,
                visible = (current?.visible ?: false) || visible,
            )
        }
        if (type.activation.factories.isNotEmpty()) {
            type.activation.factories.forEach { factory ->
                put(
                    interfaceType = factory.interfaceType,
                    kind = factory.kind,
                    activatable = factory.kind == WinRtAttributedFactoryKind.Activatable,
                    statics = factory.kind == WinRtAttributedFactoryKind.Static,
                    composable = factory.kind == WinRtAttributedFactoryKind.Composable,
                    visible = factory.isVisible,
                )
            }
        } else {
            put(type.activation.activatableFactoryInterface, kind = WinRtAttributedFactoryKind.Activatable, activatable = type.activation.isActivatable)
            type.activation.staticInterfaces.forEach { staticInterface ->
                put(staticInterface, kind = WinRtAttributedFactoryKind.Static, statics = true)
            }
            put(type.activation.composableFactoryInterface, kind = WinRtAttributedFactoryKind.Composable, composable = true)
        }
        return factories.values.toList()
    }

    fun componentActivatableClasses(): List<WinRtComponentActivatableClassDescriptor> =
        normalizedModel.namespaces
            .flatMap(WinRtNamespace::types)
            .filter { type -> type.kind == WinRtTypeKind.RuntimeClass }
            .mapNotNull { type ->
                val factories = getAttributedTypes(type).filter { factory -> factory.activatable || factory.statics }
                if (factories.isEmpty()) {
                    null
                } else {
                    WinRtComponentActivatableClassDescriptor(
                        className = type.qualifiedName,
                        classType = type,
                        attributedFactories = factories,
                    )
                }
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

    fun genericAbiInventory(): WinRtGenericAbiInventory =
        genericAbiInventory(WinRtMetadataProjectionContext(sources = emptyList()))

    fun genericAbiInventory(context: WinRtMetadataProjectionContext): WinRtGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        normalizedModel.namespaces.flatMap(WinRtNamespace::types).forEach { type ->
            collector.addGenericTypeReferencesInType(type)
        }
        return collector.toInventory(context)
    }

    fun collectGenericAbiInventory(type: WinRtTypeDefinition): WinRtGenericAbiInventory =
        collectGenericAbiInventory(type, WinRtMetadataProjectionContext(sources = emptyList()))

    fun collectGenericAbiInventory(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext,
    ): WinRtGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        collector.addGenericTypeReferencesInType(type.normalized())
        return collector.toInventory(context)
    }

    fun hasDerivedGenericInterface(type: WinRtTypeDefinition): Boolean {
        if (type.isExclusiveTo) return false
        return type.implementedInterfaces.any { implemented ->
            val interfaceType = implemented.interfaceType
            if (interfaceType.typeArguments.isNotEmpty()) {
                true
            } else {
                resolveType(interfaceType, type.namespace)?.let(::hasDerivedGenericInterface) == true
            }
        }
    }

    fun convertGenericTypeInstanceToConcreteType(
        type: WinRtTypeRef,
        genericTypeArguments: List<WinRtTypeRef>,
        methodTypeArguments: List<WinRtTypeRef> = emptyList(),
    ): WinRtTypeRef =
        type.substituteTypeParameters(genericTypeArguments, methodTypeArguments).normalized()

    fun genericInstantiationWorklist(
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): WinRtGenericInstantiationWorklistDescriptor =
        WinRtGenericInstantiationWorklistDescriptor(pending = genericAbiInventory(context).genericTypeInstantiations)

    fun projectedMethodSignature(method: WinRtMethodDefinition): WinRtProjectedMethodSignatureDescriptor =
        WinRtProjectedMethodSignatureDescriptor(
            methodName = method.name,
            returnTypeName = method.returnType.normalized().typeName,
            parameterTypeNames = method.parameters.map { parameter -> parameter.type.normalized().typeName },
        )

    fun methodSignatureEqual(first: WinRtMethodDefinition, second: WinRtMethodDefinition): Boolean =
        projectedMethodSignature(first).signatureEquals(projectedMethodSignature(second))

    fun isImplementedAsPrivateMethod(
        classType: WinRtTypeDefinition,
        interfaceType: WinRtTypeDefinition,
        interfaceMethod: WinRtMethodDefinition,
    ): WinRtPrivateImplementationDescriptor {
        val privateMethodName = "${interfaceType.qualifiedName}.${interfaceMethod.name}"
        val privateMethod = classType.methods.firstOrNull { method ->
            method.visibility == WinRtMethodVisibility.Private &&
                method.name == privateMethodName &&
                methodSignatureEqual(method, interfaceMethod)
        }
        return WinRtPrivateImplementationDescriptor(
            classTypeName = classType.qualifiedName,
            interfaceTypeName = interfaceType.qualifiedName,
            interfaceMemberName = interfaceMethod.name,
            privateMethodName = privateMethodName,
            isImplementedAsPrivateMethod = privateMethod != null,
        )
    }

    fun isImplementedAsPrivateMappedInterface(
        classType: WinRtTypeDefinition,
        interfaceType: WinRtTypeDefinition,
    ): WinRtMappedInterfaceImplementationDescriptor {
        val probeMethod = interfaceType.methods.firstOrNull()
            ?: interfaceType.properties.firstOrNull()?.getterMethodName?.let { getter -> interfaceType.methods.firstOrNull { it.name == getter } }
            ?: interfaceType.events.firstOrNull()?.addMethodName?.let { add -> interfaceType.methods.firstOrNull { it.name == add } }
        val private = probeMethod?.let { isImplementedAsPrivateMethod(classType, interfaceType, it).isImplementedAsPrivateMethod } ?: false
        return WinRtMappedInterfaceImplementationDescriptor(
            classTypeName = classType.qualifiedName,
            interfaceTypeName = interfaceType.qualifiedName,
            probeMemberName = probeMethod?.name,
            isImplementedAsPrivateMappedInterface = private,
        )
    }

    fun customQueryInterfaceDescriptor(type: WinRtTypeDefinition): WinRtCustomQueryInterfaceDescriptor {
        val hasBaseClass = type.baseTypeName?.let { it != "System.Object" && it != "Any" } ?: false
        val overridableInterfaces = type.implementedInterfaces
            .filter(WinRtInterfaceImplementationDefinition::isOverridable)
            .map { implemented -> resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName).displayName }
            .sorted()
        return WinRtCustomQueryInterfaceDescriptor(
            classTypeName = type.qualifiedName,
            hasBaseClass = hasBaseClass,
            overridableInterfaceNames = overridableInterfaces,
            visibility = if (!hasBaseClass && type.isSealedType) "private" else "protected",
            overridableModifier = when {
                hasBaseClass -> "override"
                type.isSealedType -> ""
                else -> "virtual"
            },
            delegatesToBase = hasBaseClass,
        )
    }

    fun projectionContextSemantics(context: WinRtMetadataProjectionContext): WinRtProjectionContextSemanticsDescriptor =
        WinRtProjectionContextSemanticsDescriptor(
            internalAccessibility = if (context.internal || context.embedded) "internal" else "public",
            enumAccessibility = if (context.internal || context.embedded) {
                if (context.publicEnums) "public" else "internal"
            } else {
                "public"
            },
            embedded = context.embedded,
            publicExclusiveTo = context.publicExclusiveTo,
            idicExclusiveTo = context.idicExclusiveTo,
            partialFactory = context.partialFactory,
            partialFactoryFallbackExpression = if (context.partialFactory) {
                "GetActivationFactoryPartial(runtimeClassId)"
            } else {
                "IntPtr.Zero"
            },
        )

    fun typeProjectionContextDescriptor(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext,
    ): WinRtTypeProjectionContextDescriptor {
        val contextSemantics = projectionContextSemantics(context)
        val exclusiveTo = isExclusiveTo(type)
        val projectionInternal = isProjectionInternal(type)
        val typeAccessibility = when {
            type.kind == WinRtTypeKind.Enum -> contextSemantics.enumAccessibility
            exclusiveTo && !context.publicExclusiveTo -> "internal"
            projectionInternal || context.internal || context.embedded -> "internal"
            else -> "public"
        }
        return WinRtTypeProjectionContextDescriptor(
            typeName = type.qualifiedName,
            accessibility = typeAccessibility,
            enumAccessibility = contextSemantics.enumAccessibility,
            exclusiveToAccessibility = if (exclusiveTo && !context.publicExclusiveTo) "internal" else typeAccessibility,
            writesVtablePointer = !exclusiveTo || context.publicExclusiveTo,
            supportsDynamicInterfaceCastable = !exclusiveTo || context.idicExclusiveTo,
            partialFactoryFallbackExpression = contextSemantics.partialFactoryFallbackExpression,
        )
    }

    fun isManuallyGeneratedInterface(type: WinRtTypeDefinition): WinRtManualInterfaceDescriptor {
        val manuallyGenerated = type.namespace == "Microsoft.UI.Xaml.Interop" &&
            (type.name == "IBindableVector" || type.name == "IBindableIterable")
        return WinRtManualInterfaceDescriptor(
            typeName = type.qualifiedName,
            manuallyGenerated = manuallyGenerated,
            reason = if (manuallyGenerated) "CsWinRT manually generates Microsoft.UI.Xaml.Interop bindable collection interfaces." else null,
        )
    }

    fun classMemberMergeDescriptor(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
        wrapperType: Boolean = false,
        interfaceImplType: Boolean = false,
    ): WinRtClassMemberMergeDescriptor {
        val interfaceDescriptors = mutableListOf<WinRtClassInterfaceMemberDescriptor>()
        val properties = linkedMapOf<String, MutableMergedProperty>()
        val writtenInterfaces = linkedSetOf<String>()

        fun writeClassInterface(
            interfaceType: WinRtTypeDefinition,
            isDefaultInterface: Boolean,
            isOverridableInterface: Boolean,
            isProtectedInterface: Boolean,
        ) {
            if (!writtenInterfaces.add(interfaceType.qualifiedName)) return
            val manual = isManuallyGeneratedInterface(interfaceType).manuallyGenerated
            val target = if (wrapperType) {
                "((%s) _comp)".format(interfaceType.qualifiedName)
            } else if (isDefaultInterface) {
                "_default"
            } else {
                "AsInternal(new InterfaceTag<${interfaceType.qualifiedName}>())"
            }
            val callStaticMethod = context.target != WinRtMetadataTarget.NetStandard20 && !wrapperType && !manual
            val mapped = getMappedType(interfaceType.namespace, interfaceType.name)
            interfaceDescriptors += WinRtClassInterfaceMemberDescriptor(
                interfaceTypeName = interfaceType.qualifiedName,
                target = target,
                isDefaultInterface = isDefaultInterface,
                isOverridableInterface = isOverridableInterface,
                isProtectedInterface = isProtectedInterface,
                isManuallyGeneratedInterface = manual,
                callStaticMethod = callStaticMethod,
                mappedTypeHasCustomMembers = mapped?.hasCustomMembersOutput == true,
            )
            interfaceType.properties.forEach { property ->
                val getter = property.getterMethodName?.let { getterName -> interfaceType.methods.firstOrNull { it.name == getterName } }
                val setter = property.setterMethodName?.let { setterName -> interfaceType.methods.firstOrNull { it.name == setterName } }
                val privateProperty = getter?.let { isImplementedAsPrivateMethod(type, interfaceType, it).isImplementedAsPrivateMethod } == true
                val propertyName = if (privateProperty) "${interfaceType.qualifiedName}.${property.name}" else property.name
                val platform = interfaceType.availability.contractVersion?.platformVersion
                val current = properties.getOrPut(propertyName) {
                    MutableMergedProperty(
                        propertyName = propertyName,
                        propertyTypeName = property.type.normalized().typeName,
                        isOverridable = isOverridableInterface,
                        isPublic = !isProtectedInterface && !isOverridableInterface,
                        isPrivate = privateProperty,
                    )
                }
                if (getter != null && current.getterTarget == null) {
                    current.getterTarget = target
                    current.getterPlatform = platform
                    current.getterStaticCallTarget = if (callStaticMethod) interfaceType.qualifiedName else null
                }
                if (setter != null && current.setterTarget == null) {
                    current.setterTarget = target
                    current.setterPlatform = platform
                    current.setterStaticCallTarget = if (callStaticMethod) interfaceType.qualifiedName else null
                }
                current.isOverridable = current.isOverridable || isOverridableInterface
                current.isPublic = current.isPublic || (!isProtectedInterface && !isOverridableInterface)
                current.isPrivate = current.isPrivate || privateProperty
            }
            if (interfaceImplType) {
                interfaceType.implementedInterfaces.forEach { implemented ->
                    resolveType(implemented.interfaceType, interfaceType.namespace)?.let { baseInterface ->
                        writeClassInterface(
                            interfaceType = baseInterface,
                            isDefaultInterface = false,
                            isOverridableInterface = implemented.isOverridable,
                            isProtectedInterface = implemented.isProtected,
                        )
                    }
                }
            }
        }

        if (interfaceImplType && type.kind == WinRtTypeKind.Interface) {
            writeClassInterface(type, isDefaultInterface = false, isOverridableInterface = false, isProtectedInterface = false)
        }
        type.implementedInterfaces.forEach { implemented ->
            resolveType(implemented.interfaceType, type.namespace)?.let { interfaceType ->
                writeClassInterface(
                    interfaceType = interfaceType,
                    isDefaultInterface = implemented.isDefault,
                    isOverridableInterface = implemented.isOverridable,
                    isProtectedInterface = implemented.isProtected,
                )
            }
        }
        return WinRtClassMemberMergeDescriptor(
            classTypeName = type.qualifiedName,
            interfaceDescriptors = interfaceDescriptors.sortedBy(WinRtClassInterfaceMemberDescriptor::interfaceTypeName),
            mergedProperties = properties.values
                .map(MutableMergedProperty::toDescriptor)
                .sortedBy(WinRtMergedPropertyDescriptor::propertyName),
        )
    }

    fun methodVtableDescriptors(type: WinRtTypeDefinition): List<WinRtMethodVtableDescriptor> {
        val normalized = type.normalized()
        return normalized.methods.map { method -> methodVtableDescriptor(normalized, method) }
    }

    fun methodVtableDescriptor(type: WinRtTypeDefinition, method: WinRtMethodDefinition): WinRtMethodVtableDescriptor {
        val normalizedType = type.normalized()
        val methodOrder = methodOrderByName(normalizedType)
        val vmethodIndex = method.methodRowId?.let { rowId ->
            normalizedType.methods.mapNotNull(WinRtMethodDefinition::methodRowId).minOrNull()?.let { base -> rowId - base }
        } ?: methodOrder[method.name] ?: normalizedType.methods.indexOfFirst { it.signatureKey() == method.signatureKey() }.coerceAtLeast(0)
        return WinRtMethodVtableDescriptor(
            ownerTypeName = normalizedType.qualifiedName,
            methodName = method.name,
            methodRowId = method.methodRowId,
            vmethodIndex = vmethodIndex,
            vmethodName = "${method.name}_$vmethodIndex",
            slotIndex = INSPECTABLE_METHOD_COUNT + vmethodIndex,
        )
    }

    fun resolveType(type: WinRtTypeRef, currentNamespace: String): WinRtTypeDefinition? {
        val normalized = type.normalized()
        val qualifiedName = normalized.qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    fun auxiliaryTableSemanticBoundaries(inventory: WinRtMetadataAuxiliaryTableInventory): List<WinRtAuxiliaryTableSemanticBoundaryDescriptor> =
        inventory.tables
            .groupBy(WinRtMetadataAuxiliaryTableDescriptor::tableName)
            .map { (tableName, tables) ->
                val rowCount = tables.sumOf(WinRtMetadataAuxiliaryTableDescriptor::rowCount)
                val modeled = tables.any(WinRtMetadataAuxiliaryTableDescriptor::modeled)
                val projectionAffecting = tableName in PROJECTION_AFFECTING_AUXILIARY_TABLES
                WinRtAuxiliaryTableSemanticBoundaryDescriptor(
                    tableName = tableName,
                    rowCount = rowCount,
                    modeled = modeled,
                    projectionAffecting = projectionAffecting,
                    note = when {
                        modeled -> "Decoded into normalized metadata descriptors."
                        projectionAffecting -> "Projection-affecting in ECMA metadata; decode before generator consumes this table."
                        else -> "Cache-tolerated infrastructure table; no active CsWinRT generator semantic for the current Kotlin target."
                    },
                )
            }
            .sortedBy(WinRtAuxiliaryTableSemanticBoundaryDescriptor::tableName)

    fun cswinrtMetadataParityAudit(): List<WinRtMetadataParityAuditEntry> =
        listOf(
            WinRtMetadataParityAuditEntry("helpers.h", "get_exclusive_to_type", "WinRtMetadataSemanticHelpers.getExclusiveToType", true),
            WinRtMetadataParityAuditEntry("helpers.h", "is_ptype", "WinRtMetadataSemanticHelpers.isPType", true),
            WinRtMetadataParityAuditEntry("helpers.h", "get_default_iface_as_type_sem", "WinRtMetadataSemanticHelpers.getDefaultInterfaceSemantics", true),
            WinRtMetadataParityAuditEntry("helpers.h", "does_abi_interface_implement_ccw_interface", "WinRtMetadataSemanticHelpers.doesAbiInterfaceImplementCcwInterface(context)", true),
            WinRtMetadataParityAuditEntry("main.cpp", "componentActivatableClasses pre-scan", "WinRtMetadataSemanticHelpers.componentActivatableClasses", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "is_implemented_as_private_method", "WinRtMetadataSemanticHelpers.isImplementedAsPrivateMethod", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "is_implemented_as_private_mapped_interface", "WinRtMetadataSemanticHelpers.isImplementedAsPrivateMappedInterface", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "write_custom_query_interface_impl", "WinRtMetadataSemanticHelpers.customQueryInterfaceDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "generic_type_instances fixed point", "WinRtMetadataSemanticHelpers.genericInstantiationWorklist", true),
            WinRtMetadataParityAuditEntry("WinMD tables", "auxiliary table semantic boundary", "WinRtMetadataSemanticHelpers.auxiliaryTableSemanticBoundaries", true),
            WinRtMetadataParityAuditEntry("main.cpp", "helper output inventory", "WinRtMetadataProjectionInventory.helperOutputs", true),
            WinRtMetadataParityAuditEntry("main.cpp", "WinRTAbiDelegateInitializer conditions", "WinRtProjectionHelperOutputInventory.abiDelegateInitializerRequired", true),
            WinRtMetadataParityAuditEntry("main.cpp", "WinRTGenericTypeInstantiations/base strings conditions", "WinRtProjectionHelperOutputInventory", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "is_manually_generated_iface", "WinRtMetadataSemanticHelpers.isManuallyGeneratedInterface", true),
            WinRtMetadataParityAuditEntry("settings.h/code_writers.h", "projection context flags", "WinRtMetadataSemanticHelpers.projectionContextSemantics", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "write_class_members property merge", "WinRtMetadataSemanticHelpers.classMemberMergeDescriptor", true),
        )

    private data class MutableMergedProperty(
        val propertyName: String,
        val propertyTypeName: String,
        var getterTarget: String? = null,
        var getterPlatform: String? = null,
        var setterTarget: String? = null,
        var setterPlatform: String? = null,
        var isOverridable: Boolean,
        var isPublic: Boolean,
        var isPrivate: Boolean,
        var getterStaticCallTarget: String? = null,
        var setterStaticCallTarget: String? = null,
    ) {
        fun toDescriptor(): WinRtMergedPropertyDescriptor =
            WinRtMergedPropertyDescriptor(
                propertyName = propertyName,
                propertyTypeName = propertyTypeName,
                getterTarget = getterTarget,
                getterPlatform = getterPlatform,
                setterTarget = setterTarget,
                setterPlatform = setterPlatform,
                isOverridable = isOverridable,
                isPublic = isPublic,
                isPrivate = isPrivate,
                getterStaticCallTarget = getterStaticCallTarget,
                setterStaticCallTarget = setterStaticCallTarget,
            )
    }

    private fun fastAbiPropertySlots(closure: WinRtRuntimeClassClosureDescriptor): List<WinRtFastAbiPropertySlot> {
        var vtableStartIndex = 6
        val slots = mutableListOf<WinRtFastAbiPropertySlot>()
        closure.fastAbiInterfaces.forEachIndexed { index, interfaceDescriptor ->
            val interfaceType = interfaceDescriptor.definitionType
            val methodOrder = interfaceType?.let(::methodOrderByName).orEmpty()
            interfaceType?.properties.orEmpty().forEach { property ->
                val getterOffset = property.getterMethodName?.let { methodOrder[it] }
                val setterOffset = property.setterMethodName?.let { methodOrder[it] }
                slots += WinRtFastAbiPropertySlot(
                    propertyName = property.name,
                    getterMethodName = property.getterMethodName,
                    setterMethodName = property.setterMethodName,
                    vtableStartIndex = vtableStartIndex,
                    getterVtableIndex = getterOffset?.let { vtableStartIndex + it },
                    setterVtableIndex = setterOffset?.let { vtableStartIndex + it },
                )
            }
            val methodCount = interfaceType?.methods?.size ?: 0
            vtableStartIndex += methodCount + if (index == 0) closure.classHierarchyIndex else 0
        }
        return slots
    }

    private fun methodOrderByName(type: WinRtTypeDefinition): Map<String, Int> =
        type.normalized().methods
            .mapIndexed { index, method -> method.name to method.methodRowId }
            .let { methods ->
                val baseRowId = methods.mapNotNull { it.second }.minOrNull()
                methods.mapIndexed { index, (name, rowId) -> name to (rowId?.let { it - (baseRowId ?: it) } ?: index) }.toMap()
            }

    private inner class GenericAbiInventoryCollector {
        private val abiDelegates = linkedMapOf<String, WinRtGenericAbiDelegateDescriptor>()
        private val typeInstantiations = linkedMapOf<String, WinRtGenericTypeInstantiationDescriptor>()
        private val derivedGenericInterfaces = linkedSetOf<String>()

        fun addGenericTypeReferencesInType(type: WinRtTypeDefinition) {
            when (type.kind) {
                WinRtTypeKind.Delegate -> getDelegateInvoke(type)?.let { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
                WinRtTypeKind.Interface -> {
                    if (hasDerivedGenericInterface(type)) {
                        derivedGenericInterfaces += type.qualifiedName
                    }
                    addGenericTypeReferencesInInterfaceType(type)
                }
                else -> Unit
            }
        }

        fun toInventory(context: WinRtMetadataProjectionContext): WinRtGenericAbiInventory =
            WinRtGenericAbiInventory(
                genericAbiDelegates = abiDelegates.values.sortedWith(compareBy({ it.abiDelegateTypesKey }, { it.abiDelegateName })),
                genericTypeInstantiations = typeInstantiations.values
                    .map { instantiation ->
                        instantiation.copy(
                            implementsCcwInterface = instantiation.definitionType
                                ?.let { definition -> doesAbiInterfaceImplementCcwInterface(definition, context) }
                                ?: false,
                        )
                    }
                    .sortedBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName),
                derivedGenericInterfaces = derivedGenericInterfaces.sorted(),
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
                                implementsCcwInterface = false,
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
        private const val WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO_ATTRIBUTE = "Windows.Foundation.Metadata.ExclusiveToAttribute"
        private const val INSPECTABLE_METHOD_COUNT = 6
        private val PROJECTION_AFFECTING_AUXILIARY_TABLES = setOf("FieldMarshal", "ImplMap", "FieldRVA", "DeclSecurity", "ModuleRef")
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

private fun escapeTypeNameForIdentifier(typeName: String): String =
    typeName.replace(Regex("""[\s:<>,.]"""), "_")
