package io.github.composefluent.winrt.metadata

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

data class WinRtFastAbiInterfaceSlot(
    val interfaceName: String,
    val interfaceType: WinRtTypeRef,
    val isDefault: Boolean,
    val vtableStartIndex: Int,
    val methodCount: Int,
    val hierarchyOffsetAfterDefault: Int = 0,
) {
    val nextVtableStartIndex: Int
        get() = vtableStartIndex + methodCount + hierarchyOffsetAfterDefault
}

data class WinRtFastAbiClassDescriptor(
    val classType: WinRtTypeDefinition,
    val defaultInterface: WinRtRuntimeClassInterfaceDescriptor?,
    val otherInterfaces: List<WinRtRuntimeClassInterfaceDescriptor>,
    val interfaceSlots: List<WinRtFastAbiInterfaceSlot>,
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
    val operationName: String = abiDelegateName.removePrefix("_").substringBeforeLast("_", missingDelimiterValue = abiDelegateName.removePrefix("_")),
    val abiReturnTypeName: String = "Int",
    val abiParameterTypeNames: List<String> = emptyList(),
    val declaration: String = "",
    val typeArrayShape: List<String> = emptyList(),
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

data class WinRtObjectMethodMatchDescriptor(
    val methodName: String,
    val matches: Boolean,
    val returnTypeMatches: Boolean,
)

data class WinRtClassObjectMethodDescriptor(
    val classTypeName: String,
    val objectEquals: WinRtObjectMethodMatchDescriptor?,
    val classEquals: WinRtObjectMethodMatchDescriptor?,
    val objectHashCode: WinRtObjectMethodMatchDescriptor?,
    val hasObjectEqualsMethod: Boolean,
    val hasClassEqualsMethod: Boolean,
    val hasObjectHashCodeMethod: Boolean,
)

data class WinRtGenericInstantiationWriterDescriptor(
    val instantiationClassName: String,
    val sourceTypeName: String,
    val isDelegateInstantiation: Boolean,
    val rcwFunctionNames: List<String>,
    val vtableFunctionNames: List<String>,
    val propertyAccessorFunctionNames: List<String>,
    val genericReturnOnlyRcwFunctionNames: List<String> = emptyList(),
    val projectedGenericFallbackFunctionNames: List<String> = emptyList(),
    val initializationDependencies: List<String>,
)

enum class WinRtProjectedNameKind {
    Projected,
    Abi,
    Ccw,
    NonProjected,
    StaticAbiClass,
}

data class WinRtTypeNameWriterContext(
    val currentNamespace: String,
    val inAbiNamespace: Boolean = false,
    val inAbiImplNamespace: Boolean = false,
    val component: Boolean = false,
)

data class WinRtTypeNameDescriptor(
    val typeName: String,
    val nameKind: WinRtProjectedNameKind,
    val renderedName: String,
    val forceNamespace: Boolean,
    val namespacePrefix: String,
    val writesGlobalPrefix: Boolean,
    val rewritesExclusiveProjectedToCcw: Boolean,
    val genericArityStrippedName: String,
)

data class WinRtEventHelperSubclassDescriptor(
    val eventTypeName: String,
    val projectedEventTypeName: String,
    val abiEventTypeName: String,
    val ownerTypeName: String,
    val sourceClassName: String,
    val genericArgumentTypeNames: List<String>,
    val usesSharedEventHandlerSource: Boolean,
    val interfaceId: io.github.composefluent.winrt.runtime.Guid?,
)

data class WinRtPlatformGuardDescriptor(
    val ownerName: String,
    val platform: String?,
    val checkPlatform: Boolean,
    val platformAttribute: String?,
    val suppressesDuplicatePlatform: Boolean,
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

data class WinRtSignatureWriterDescriptor(
    val methodName: String,
    val escapedMethodName: String,
    val projectionReturnTypeName: String,
    val abiReturnTypeName: String,
    val parameters: List<WinRtSignatureParameterWriterDescriptor>,
    val hasProjectedGenericParameters: Boolean,
    val hasAbiGenericParameters: Boolean,
)

data class WinRtSignatureParameterWriterDescriptor(
    val originalName: String,
    val escapedName: String,
    val category: WinRtMetadataParameterCategory,
    val projectionTypeName: String,
    val abiTypeName: String,
    val modifier: String?,
    val expandsArrayLength: Boolean,
)

data class WinRtEventInvokeDescriptor(
    val eventName: String,
    val delegateTypeName: String,
    val invokeMethodName: String?,
    val returnTypeName: String,
    val parameters: List<WinRtSignatureParameterWriterDescriptor>,
    val outDefaultAssignments: List<String>,
    val sourceTableAddIndex: Int?,
    val sourceTableRemoveIndex: Int?,
    val isStatic: Boolean,
)

data class WinRtAbiMarshalerPlanDescriptor(
    val methodName: String,
    val marshalers: List<WinRtAbiMarshalerSlotDescriptor>,
    val requiresPinnedScope: Boolean,
    val requiresDispose: Boolean,
    val hasNoExceptionAttribute: Boolean,
    val isGenericInstantiationClass: Boolean,
)

data class WinRtAbiMarshalerSlotDescriptor(
    val name: String,
    val typeName: String,
    val category: WinRtMetadataParameterCategory,
    val isReturn: Boolean = false,
    val isPinnable: Boolean,
    val isGeneric: Boolean,
    val requiresFromAbi: Boolean,
    val requiresToAbi: Boolean,
    val requiresDispose: Boolean,
)

data class WinRtFactorySurfaceDescriptor(
    val classTypeName: String,
    val defaultInterfaceName: String?,
    val activationFactoryCacheName: String,
    val staticFactoryCacheNames: List<String>,
    val constructorFactories: List<String>,
    val composableFactories: List<String>,
    val staticMemberTargets: List<String>,
    val gcPressureAmount: Int,
)

data class WinRtCustomMappedMemberOutputDescriptor(
    val interfaceTypeName: String,
    val mappedTypeName: String,
    val memberPlans: List<String>,
    val callMode: String,
    val emitsExplicitMembers: Boolean,
    val emitsPrivateMembers: Boolean,
)

data class WinRtInterfaceMemberSignatureSetDescriptor(
    val interfaceTypeName: String,
    val methodSignatures: List<WinRtProjectedMethodSignatureDescriptor>,
    val propertyNames: List<String>,
    val eventNames: List<String>,
    val newPropertyNames: List<String>,
)

data class WinRtGuidSignatureDescriptor(
    val typeName: String,
    val guidText: String?,
    val guidBytes: List<Int>,
    val signatureFragment: String,
    val parameterizedSignatureFragments: List<String>,
)

data class WinRtVtableWriterDescriptor(
    val typeName: String,
    val methods: List<WinRtMethodVtableDescriptor>,
    val delegateCacheNames: List<String>,
    val usesFunctionPointers: Boolean,
    val genericAbiTypeArrays: List<List<String>>,
)

data class WinRtTypeDeclarationDescriptor(
    val typeName: String,
    val declarationKind: WinRtTypeKind,
    val writesProjectedDeclaration: Boolean,
    val writesAbiDeclaration: Boolean,
    val writesWrapperDeclaration: Boolean,
    val writesImplementationClass: Boolean,
    val writesHelperClass: Boolean,
    val netStandardBranch: Boolean,
)

data class WinRtObjectReferenceSurfaceDescriptor(
    val typeName: String,
    val inheritanceTypeNames: List<String>,
    val objectReferenceNames: List<String>,
    val objectReferencePlans: List<WinRtObjectReferencePlanDescriptor>,
    val baseConstructorDispatchTargets: List<String>,
    val exposedTypeMetadataNames: List<String>,
    val hasRcwFactory: Boolean,
    val hasUnwrappableNativeObject: Boolean,
)

data class WinRtObjectReferencePlanDescriptor(
    val interfaceName: String,
    val cacheName: String,
    val isDefaultInterface: Boolean,
    val skippedReason: String? = null,
    val usesInner: Boolean = false,
    val usesDefaultInterfaceObjRef: Boolean = false,
    val defaultInterfaceHierarchyIndex: Int? = null,
    val defaultInterfaceObjRefVtableSlot: Int? = null,
    val requiresGenericInstantiation: Boolean = false,
)

data class WinRtManagedAbiInvokeDescriptor(
    val memberName: String,
    val invokeKind: String,
    val managedLocals: List<String>,
    val conversionOperations: List<String>,
    val hasNoExceptionAttribute: Boolean,
    val requiresHelperMethod: Boolean,
)

data class WinRtGenericAbiClassInitializationDescriptor(
    val typeName: String,
    val requiresRcwFallbackInitialization: Boolean,
    val requiresCcwFallbackInitialization: Boolean,
    val genericMethodDelegateVariables: List<String>,
    val invokeSlotNames: List<String>,
    val genericTypeArrayDependencies: List<String>,
)

data class WinRtRequiredInterfaceAugmentationDescriptor(
    val typeName: String,
    val requiredInterfaceNames: List<String>,
    val explicitForwardMemberNames: List<String>,
    val mappedAugmentationMembers: List<String>,
    val mappedHelperPlans: List<WinRtRequiredMappedHelperPlanDescriptor>,
    val genericAbiParameterArrays: List<List<String>>,
    val implementsCcwInterface: Boolean,
)

data class WinRtRequiredMappedHelperPlanDescriptor(
    val interfaceName: String,
    val mappedTypeName: String,
    val memberFamily: String,
    val helperWrapperName: String?,
    val adapterFieldName: String?,
    val callMode: String,
    val emitsMappedTypeHelpers: Boolean,
    val emitsPrivateMembers: Boolean,
    val removesNonGenericEnumerable: Boolean,
    val removesGenericEnumerableName: String?,
)

data class WinRtModuleActivationAndAuthoringDescriptor(
    val typeName: String,
    val authoringMetadataTypeName: String?,
    val factoryClassName: String?,
    val factoryMemberNames: List<String>,
    val moduleActivationFactoryEntries: List<String>,
    val baseTypeEntry: String?,
    val metadataTypeEntry: String?,
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
            if (current.kind == WinRtTypeRefKind.GenericTypeParameter || current.kind == WinRtTypeRefKind.MethodTypeParameter) {
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
            interfaceSlots = fastAbiInterfaceSlots(closure),
            propertySlots = fastAbiPropertySlots(closure),
        )
    }

    fun getFastAbiClassForInterface(type: WinRtTypeDefinition): WinRtFastAbiClassDescriptor? {
        if (type.isExclusiveTo) {
            val exclusiveToClass = getExclusiveToType(type)
            if (exclusiveToClass?.isFastAbi == true) {
                return getFastAbiClassForClass(exclusiveToClass)
            }
        }
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

    fun isObjectEqualsMethod(method: WinRtMethodDefinition): WinRtObjectMethodMatchDescriptor {
        val hasExpectedNameAndParams = method.name == "Equals" &&
            method.parameters.size == 1 &&
            typeClassifier.classify(method.parameters.single().type, "").projectionCategory == WinRtProjectionCategory.Object
        return WinRtObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && method.returnType.normalized().typeName == "Boolean",
            returnTypeMatches = method.returnType.normalized().typeName == "Boolean",
        )
    }

    fun isClassEqualsMethod(
        method: WinRtMethodDefinition,
        classType: WinRtTypeDefinition,
    ): WinRtObjectMethodMatchDescriptor {
        val parameterType = method.parameters.singleOrNull()?.type?.normalized()?.typeName
        val hasExpectedNameAndParams = method.name == "Equals" && parameterType == classType.qualifiedName
        return WinRtObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && method.returnType.normalized().typeName == "Boolean",
            returnTypeMatches = method.returnType.normalized().typeName == "Boolean",
        )
    }

    fun isObjectHashCodeMethod(method: WinRtMethodDefinition): WinRtObjectMethodMatchDescriptor {
        val hasExpectedNameAndParams = method.name == "GetHashCode" && method.parameters.isEmpty()
        return WinRtObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && method.returnType.normalized().typeName == "Int",
            returnTypeMatches = method.returnType.normalized().typeName == "Int",
        )
    }

    fun hasObjectEqualsMethod(type: WinRtTypeDefinition): Boolean =
        type.methods.any { method -> isObjectEqualsMethod(method).matches }

    fun hasClassEqualsMethod(type: WinRtTypeDefinition): Boolean =
        type.methods.any { method -> isClassEqualsMethod(method, type).matches }

    fun hasObjectHashCodeMethod(type: WinRtTypeDefinition): Boolean =
        type.methods.any { method -> isObjectHashCodeMethod(method).matches }

    fun classObjectMethodDescriptor(type: WinRtTypeDefinition): WinRtClassObjectMethodDescriptor =
        WinRtClassObjectMethodDescriptor(
            classTypeName = type.qualifiedName,
            objectEquals = type.methods.firstOrNull { method -> method.name == "Equals" && method.parameters.size == 1 }
                ?.let(::isObjectEqualsMethod),
            classEquals = type.methods.firstOrNull { method ->
                method.name == "Equals" && method.parameters.singleOrNull()?.type?.normalized()?.typeName == type.qualifiedName
            }?.let { method -> isClassEqualsMethod(method, type) },
            objectHashCode = type.methods.firstOrNull { method -> method.name == "GetHashCode" }?.let(::isObjectHashCodeMethod),
            hasObjectEqualsMethod = hasObjectEqualsMethod(type),
            hasClassEqualsMethod = hasClassEqualsMethod(type),
            hasObjectHashCodeMethod = hasObjectHashCodeMethod(type),
        )

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
    ): WinRtGenericInstantiationWorklistDescriptor {
        val discovered = linkedMapOf<String, WinRtGenericTypeInstantiationDescriptor>()
        fun enqueue(instantiations: List<WinRtGenericTypeInstantiationDescriptor>) {
            instantiations.forEach { instantiation ->
                discovered.putIfAbsent(
                    instantiation.instantiationClassName,
                    instantiation.copy(
                        implementsCcwInterface = instantiation.definitionType
                            ?.let { definition -> doesAbiInterfaceImplementCcwInterface(definition, context) }
                            ?: false,
                    ),
                )
            }
        }

        enqueue(genericAbiInventory(context).genericTypeInstantiations)
        var index = 0
        while (index < discovered.size) {
            val instantiation = discovered.values.elementAt(index++)
            val dependencies = genericInstantiationWriterDescriptor(instantiation).initializationDependencies
            enqueue(dependencies.mapNotNull(::genericTypeInstantiationDescriptorForDependency))
        }

        return WinRtGenericInstantiationWorklistDescriptor(
            pending = discovered.values.sortedBy(WinRtGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }

    fun genericInstantiationWriterDescriptors(
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): List<WinRtGenericInstantiationWriterDescriptor> =
        genericInstantiationWorklist(context).pending.map(::genericInstantiationWriterDescriptor)

    fun genericInstantiationWriterDescriptor(
        instantiation: WinRtGenericTypeInstantiationDescriptor,
    ): WinRtGenericInstantiationWriterDescriptor {
        val definition = instantiation.definitionType
        val isDelegate = definition?.kind == WinRtTypeKind.Delegate
        val rcwFunctions = mutableListOf<String>()
        val vtableFunctions = mutableListOf<String>()
        val propertyFunctions = mutableListOf<String>()
        val genericReturnOnlyRcwFunctions = mutableListOf<String>()
        val projectedGenericFallbackFunctions = mutableListOf<String>()
        if (definition != null && isDelegate) {
            getDelegateInvoke(definition)?.name?.let { invoke ->
                rcwFunctions += invoke
                vtableFunctions += invoke
                projectedGenericFallbackFunctions += invoke
            }
        } else {
            definition?.methods.orEmpty().forEach { method ->
                if (!projectedSignatureHasGenericParameters(method.returnType, method.parameters)) return@forEach
                if (!(isSpecial(method) && (method.name.startsWith("add_") || method.name.startsWith("remove_")))) {
                    rcwFunctions += method.name
                    projectedGenericFallbackFunctions += method.name
                    if (signatureHasOnlyGenericReturn(method.returnType, method.parameters)) {
                        genericReturnOnlyRcwFunctions += method.name
                    }
                }
            }
            val methods = definition?.methods.orEmpty()
            definition?.properties.orEmpty().forEach { property ->
                val getter = property.getterMethodName?.let { name -> methods.firstOrNull { it.name == name } }
                val setter = property.setterMethodName?.let { name -> methods.firstOrNull { it.name == name } }
                val propertyHasGeneric = property.type.containsGenericTypeParameter()
                if (getter != null) {
                    if (projectedSignatureHasGenericParameters(getter.returnType, getter.parameters)) {
                        rcwFunctions += getter.name
                        propertyFunctions += getter.name
                        projectedGenericFallbackFunctions += getter.name
                        if (signatureHasOnlyGenericReturn(getter.returnType, getter.parameters)) {
                            genericReturnOnlyRcwFunctions += getter.name
                        }
                    }
                } else if (property.getterMethodName != null && propertyHasGeneric) {
                    rcwFunctions += property.getterMethodName
                    propertyFunctions += property.getterMethodName
                    projectedGenericFallbackFunctions += property.getterMethodName
                    genericReturnOnlyRcwFunctions += property.getterMethodName
                }
                if (setter != null) {
                    if (projectedSignatureHasGenericParameters(setter.returnType, setter.parameters)) {
                        rcwFunctions += setter.name
                        propertyFunctions += setter.name
                        projectedGenericFallbackFunctions += setter.name
                    }
                } else if (property.setterMethodName != null && propertyHasGeneric) {
                    rcwFunctions += property.setterMethodName
                    propertyFunctions += property.setterMethodName
                    projectedGenericFallbackFunctions += property.setterMethodName
                }
            }
        }
        val dependencies = buildList {
            definition?.implementedInterfaces.orEmpty().forEach { implemented ->
                add(implemented.interfaceType.substituteTypeParameters(instantiation.genericArguments).normalized().typeName)
            }
            definition?.events.orEmpty().forEach { event ->
                add(event.delegateType.substituteTypeParameters(instantiation.genericArguments).normalized().typeName)
            }
        }
        return WinRtGenericInstantiationWriterDescriptor(
            instantiationClassName = instantiation.instantiationClassName,
            sourceTypeName = instantiation.type.normalized().typeName,
            isDelegateInstantiation = isDelegate,
            rcwFunctionNames = rcwFunctions.distinct(),
            vtableFunctionNames = vtableFunctions.distinct(),
            propertyAccessorFunctionNames = propertyFunctions.distinct(),
            genericReturnOnlyRcwFunctionNames = genericReturnOnlyRcwFunctions.distinct(),
            projectedGenericFallbackFunctionNames = projectedGenericFallbackFunctions.distinct(),
            initializationDependencies = dependencies.distinct().sorted(),
        )
    }

    private fun genericTypeInstantiationDescriptorForDependency(typeName: String): WinRtGenericTypeInstantiationDescriptor? {
        val type = WinRtTypeRef.fromDisplayName(typeName).normalized()
        if (type.typeArguments.isEmpty()) return null
        val currentNamespace = type.qualifiedName?.substringBeforeLast('.', "") ?: ""
        val resolved = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val sourceType = resolved.type
        return WinRtGenericTypeInstantiationDescriptor(
            type = sourceType,
            definitionType = resolved.definitionType,
            instantiationClassName = escapeTypeNameForIdentifier(sourceType.typeName),
            genericArguments = sourceType.typeArguments,
            implementsCcwInterface = false,
        )
    }

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
        val hasBaseClass = type.baseTypeName?.let { !isWinRtObjectTypeName(it) } ?: false
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

    fun typeNameDescriptor(
        type: WinRtTypeDefinition,
        nameKind: WinRtProjectedNameKind = WinRtProjectedNameKind.Projected,
        context: WinRtTypeNameWriterContext = WinRtTypeNameWriterContext(type.namespace),
        forceNamespace: Boolean = false,
    ): WinRtTypeNameDescriptor {
        val authoredType = context.component
        val rewritesExclusive = authoredType && isExclusiveTo(type) && nameKind == WinRtProjectedNameKind.Projected
        val effectiveKind = if (rewritesExclusive) WinRtProjectedNameKind.Ccw else nameKind
        val strippedName = stripGenericArity(type.name)
        val namespacePrefix = if (context.component) "ABI.Impl." else ""
        val writesGlobalPrefix = forceNamespace || type.namespace != context.currentNamespace ||
            (effectiveKind == WinRtProjectedNameKind.Projected && (context.inAbiNamespace || context.inAbiImplNamespace)) ||
            (effectiveKind == WinRtProjectedNameKind.Abi && !context.inAbiNamespace) ||
            (effectiveKind == WinRtProjectedNameKind.Ccw && authoredType && !context.inAbiImplNamespace) ||
            (effectiveKind == WinRtProjectedNameKind.Ccw && !authoredType && (context.inAbiNamespace || context.inAbiImplNamespace))
        val renderedNamespace = if (writesGlobalPrefix) {
            when (effectiveKind) {
                WinRtProjectedNameKind.Abi,
                WinRtProjectedNameKind.StaticAbiClass,
                -> "ABI.${type.namespace}"
                WinRtProjectedNameKind.Ccw -> if (authoredType) "ABI.Impl.${type.namespace}" else type.namespace
                else -> type.namespace
            }
        } else {
            ""
        }
        val baseName = when (effectiveKind) {
            WinRtProjectedNameKind.Abi -> "${strippedName}Abi"
            WinRtProjectedNameKind.Ccw -> "${strippedName}Ccw"
            WinRtProjectedNameKind.StaticAbiClass -> "${strippedName}Methods"
            else -> strippedName
        }
        val typeParams = type.genericParameters
            .sortedBy(WinRtGenericParameterDefinition::index)
            .takeIf(List<*>::isNotEmpty)
            ?.joinToString(", ", prefix = "<", postfix = ">") { it.name }
            ?: if (type.genericParameterCount > 0) {
                (0 until type.genericParameterCount).joinToString(", ", prefix = "<", postfix = ">") { "T$it" }
            } else {
                ""
            }
        return WinRtTypeNameDescriptor(
            typeName = type.qualifiedName,
            nameKind = nameKind,
            renderedName = listOf(renderedNamespace, "$baseName$typeParams").filter(String::isNotEmpty).joinToString("."),
            forceNamespace = forceNamespace,
            namespacePrefix = namespacePrefix,
            writesGlobalPrefix = writesGlobalPrefix,
            rewritesExclusiveProjectedToCcw = rewritesExclusive,
            genericArityStrippedName = strippedName,
        )
    }

    fun eventHelperSubclassDescriptors(type: WinRtTypeDefinition): List<WinRtEventHelperSubclassDescriptor> =
        type.events.map { event ->
            val eventType = event.delegateType.normalized()
            val eventHandlerDescriptor =
                typeClassifier.classify(eventType, type.namespace).specialType as? WinRtEventHandlerTypeDescriptor
            val usesSharedEventHandlerSource =
                eventHandlerDescriptor?.kind == WinRtEventHandlerKind.EventHandler &&
                    eventHandlerDescriptor.typeArguments.size == 1
            WinRtEventHelperSubclassDescriptor(
                eventTypeName = eventType.typeName,
                projectedEventTypeName = eventType.typeName,
                abiEventTypeName = renderAbiTypeName(eventType),
                ownerTypeName = type.qualifiedName,
                sourceClassName = if (usesSharedEventHandlerSource) {
                    "EventHandlerEventSource"
                } else {
                    escapeTypeNameForIdentifier("_EventSource_${eventType.typeName}")
                },
                genericArgumentTypeNames = eventType.typeArguments.map { it.normalized().typeName },
                usesSharedEventHandlerSource = usesSharedEventHandlerSource,
                interfaceId = eventDelegateInterfaceId(eventType, type.namespace),
            )
        }.distinctBy { it.eventTypeName to it.ownerTypeName }

    fun platformGuardDescriptor(
        ownerName: String,
        availability: WinRtAvailabilityMetadata,
        inheritedPlatform: String? = null,
    ): WinRtPlatformGuardDescriptor {
        val platform = availability.contractVersion?.platformVersion
        return WinRtPlatformGuardDescriptor(
            ownerName = ownerName,
            platform = platform,
            checkPlatform = platform != null,
            platformAttribute = platform?.let { "[SupportedOSPlatform(\"windows$it\")]" },
            suppressesDuplicatePlatform = platform != null && platform == inheritedPlatform,
        )
    }

    fun methodVtableDescriptors(type: WinRtTypeDefinition): List<WinRtMethodVtableDescriptor> {
        val normalized = type.normalized()
        return normalized.methods.map { method -> methodVtableDescriptor(normalized, method) }
    }

    fun methodVtableDescriptor(type: WinRtTypeDefinition, method: WinRtMethodDefinition): WinRtMethodVtableDescriptor {
        val normalizedType = type.normalized()
        val methodOrder = methodOrderByName(normalizedType)
        val abiOrder = abiMemberOrderByRowId(normalizedType)
        val vmethodIndex = method.methodRowId?.let(abiOrder::get)
            ?: methodOrder[method.name]
            ?: normalizedType.methods.indexOfFirst { it.signatureKey() == method.signatureKey() }.coerceAtLeast(0)
        return WinRtMethodVtableDescriptor(
            ownerTypeName = normalizedType.qualifiedName,
            methodName = method.name,
            methodRowId = method.methodRowId,
            vmethodIndex = vmethodIndex,
            vmethodName = "${method.name}_$vmethodIndex",
            slotIndex = INSPECTABLE_METHOD_COUNT + vmethodIndex,
        )
    }

    fun signatureWriterDescriptor(method: WinRtMethodDefinition): WinRtSignatureWriterDescriptor {
        val signature = methodSignature(method)
        val parameters = signature.parameters.map { parameter ->
            signatureParameterWriterDescriptor(parameter.parameter, parameter.category)
        }
        val usage = genericSignatureUsage(method.returnType)
        val parameterHasGeneric = method.parameters.any { genericSignatureUsage(it.type).containsProjectedGenericParameter }
        return WinRtSignatureWriterDescriptor(
            methodName = method.name,
            escapedMethodName = escapeIdentifier(method.name),
            projectionReturnTypeName = method.returnType.normalized().typeName,
            abiReturnTypeName = renderAbiTypeName(method.returnType),
            parameters = parameters,
            hasProjectedGenericParameters = usage.containsProjectedGenericParameter || parameterHasGeneric,
            hasAbiGenericParameters = usage.containsAbiGenericParameter || parameterHasGeneric,
        )
    }

    fun eventInvokeDescriptor(type: WinRtTypeDefinition, event: WinRtEventDefinition): WinRtEventInvokeDescriptor {
        val delegateType = resolveType(event.delegateType, type.namespace)
        val invoke = delegateType?.let(::getDelegateInvoke)
        val invokeSignature = invoke?.let(::signatureWriterDescriptor)
        return WinRtEventInvokeDescriptor(
            eventName = event.name,
            delegateTypeName = event.delegateType.normalized().typeName,
            invokeMethodName = invoke?.name,
            returnTypeName = invoke?.returnType?.normalized()?.typeName ?: "Void",
            parameters = invokeSignature?.parameters.orEmpty(),
            outDefaultAssignments = invoke?.parameters.orEmpty()
                .filter { parameter -> parameterCategory(parameter) == WinRtMetadataParameterCategory.Out }
                .map { parameter -> "${escapeIdentifier(parameter.name)} = default" },
            sourceTableAddIndex = event.addMethodRowId,
            sourceTableRemoveIndex = event.removeMethodRowId,
            isStatic = event.isStatic,
        )
    }

    fun abiMarshalerPlanDescriptor(
        method: WinRtMethodDefinition,
        isGenericInstantiationClass: Boolean = false,
    ): WinRtAbiMarshalerPlanDescriptor {
        val slots = mutableListOf<WinRtAbiMarshalerSlotDescriptor>()
        if (method.returnType.normalized().typeName != "Void") {
            slots += abiMarshalerSlot("__return_value__", method.returnType, WinRtMetadataParameterCategory.Out, isReturn = true)
        }
        method.parameters.forEach { parameter ->
            slots += abiMarshalerSlot(parameter.name, parameter.type, parameterCategory(parameter), isReturn = false)
        }
        return WinRtAbiMarshalerPlanDescriptor(
            methodName = method.name,
            marshalers = slots,
            requiresPinnedScope = slots.any(WinRtAbiMarshalerSlotDescriptor::isPinnable),
            requiresDispose = slots.any(WinRtAbiMarshalerSlotDescriptor::requiresDispose),
            hasNoExceptionAttribute = isNoException(method),
            isGenericInstantiationClass = isGenericInstantiationClass,
        )
    }

    fun factorySurfaceDescriptor(type: WinRtTypeDefinition): WinRtFactorySurfaceDescriptor {
        val attributed = getAttributedTypes(type)
        return WinRtFactorySurfaceDescriptor(
            classTypeName = type.qualifiedName,
            defaultInterfaceName = getDefaultInterface(type)?.normalized()?.typeName,
            activationFactoryCacheName = "${type.name.substringBefore('`')}ActivationFactory",
            staticFactoryCacheNames = attributed.filter(WinRtAttributedFactoryDescriptor::statics).map { cacheNameFor(it.interfaceName) },
            constructorFactories = attributed.filter(WinRtAttributedFactoryDescriptor::activatable).map(WinRtAttributedFactoryDescriptor::interfaceName),
            composableFactories = attributed.filter(WinRtAttributedFactoryDescriptor::composable).map(WinRtAttributedFactoryDescriptor::interfaceName),
            staticMemberTargets = attributed.filter(WinRtAttributedFactoryDescriptor::statics).map(WinRtAttributedFactoryDescriptor::interfaceName),
            gcPressureAmount = getGcPressureAmount(type),
        )
    }

    fun customMappedMemberOutputDescriptor(
        interfaceType: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
        isPrivate: Boolean = false,
        callStaticAbiMethods: Boolean = context.target != WinRtMetadataTarget.NetStandard20,
    ): WinRtCustomMappedMemberOutputDescriptor? {
        val mapping = getMappedType(interfaceType.namespace, interfaceType.name) ?: return null
        val members = buildList {
            interfaceType.methods.filterNot(::isSpecial).forEach { add(it.name) }
            interfaceType.properties.forEach { add(it.name) }
            interfaceType.events.forEach { add(it.name) }
            if (mapping.hasCustomMembersOutput) add((mapping.mappedName ?: mapping.abiName).substringAfterLast('.'))
        }.distinct().sorted()
        return WinRtCustomMappedMemberOutputDescriptor(
            interfaceTypeName = interfaceType.qualifiedName,
            mappedTypeName = mapping.mappedQualifiedName ?: mapping.mappedName.orEmpty(),
            memberPlans = members,
            callMode = if (callStaticAbiMethods) "static-abi" else "idic",
            emitsExplicitMembers = mapping.hasCustomMembersOutput,
            emitsPrivateMembers = isPrivate,
        )
    }

    fun interfaceMemberSignatureSetDescriptor(type: WinRtTypeDefinition): WinRtInterfaceMemberSignatureSetDescriptor =
        WinRtInterfaceMemberSignatureSetDescriptor(
            interfaceTypeName = type.qualifiedName,
            methodSignatures = type.methods.map(::projectedMethodSignature),
            propertyNames = type.properties.map(WinRtPropertyDefinition::name).sorted(),
            eventNames = type.events.map(WinRtEventDefinition::name).sorted(),
            newPropertyNames = type.properties
                .filter { property -> property.getterMethodName == null && property.setterMethodName != null }
                .map(WinRtPropertyDefinition::name)
                .sorted(),
        )

    fun guidSignatureDescriptor(type: WinRtTypeDefinition): WinRtGuidSignatureDescriptor {
        val guidText = type.iid?.toString()
        return WinRtGuidSignatureDescriptor(
            typeName = type.qualifiedName,
            guidText = guidText,
            guidBytes = guidText?.let(::guidTextToBytes).orEmpty(),
            signatureFragment = guidSignatureFragment(type),
            parameterizedSignatureFragments = type.genericParameters.map { parameter -> parameter.name },
        )
    }

    fun vtableWriterDescriptor(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): WinRtVtableWriterDescriptor =
        WinRtVtableWriterDescriptor(
            typeName = type.qualifiedName,
            methods = methodVtableDescriptors(type),
            delegateCacheNames = type.methods.map { method -> "_${methodVtableDescriptor(type, method).vmethodName}" },
            usesFunctionPointers = context.target != WinRtMetadataTarget.NetStandard20 && type.genericParameterCount == 0,
            genericAbiTypeArrays = type.methods.mapNotNull { method ->
                val genericTypes = getGenericAbiTypes(method)
                genericTypes.takeIf(List<*>::isNotEmpty)
            },
        )

    fun typeDeclarationDescriptor(
        type: WinRtTypeDefinition,
        context: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    ): WinRtTypeDeclarationDescriptor =
        WinRtTypeDeclarationDescriptor(
            typeName = type.qualifiedName,
            declarationKind = type.kind,
            writesProjectedDeclaration = type.kind != WinRtTypeKind.Unknown,
            writesAbiDeclaration = type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.RuntimeClass, WinRtTypeKind.Delegate, WinRtTypeKind.Struct),
            writesWrapperDeclaration = type.kind == WinRtTypeKind.RuntimeClass,
            writesImplementationClass = type.kind == WinRtTypeKind.Interface && hasDerivedGenericInterface(type),
            writesHelperClass = type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.RuntimeClass, WinRtTypeKind.Delegate),
            netStandardBranch = context.target == WinRtMetadataTarget.NetStandard20,
        )

    fun objectReferenceSurfaceDescriptor(type: WinRtTypeDefinition): WinRtObjectReferenceSurfaceDescriptor {
        val closure = if (type.kind == WinRtTypeKind.RuntimeClass) closureResolver.resolveRuntimeClass(type) else null
        val instanceInterfaces = closure?.instanceInterfaces.orEmpty()
        val interfaces = instanceInterfaces.map(WinRtRuntimeClassInterfaceDescriptor::interfaceName)
        val replaceDefaultByInner = type.isSealedType
        val classHierarchyIndex = closure?.classHierarchyIndex ?: 0
        val fastAbiDefaultSlot = getFastAbiClassForClass(type)
            ?.interfaceSlots
            ?.firstOrNull { slot -> slot.isDefault }
        val objectReferencePlans = instanceInterfaces.map { descriptor ->
            val interfaceType = descriptor.definitionType
            val manuallyGenerated = interfaceType?.let { isManuallyGeneratedInterface(it).manuallyGenerated } ?: false
            val mappedSkipReason = interfaceType?.let(::mappedObjectReferenceSkipReason)
            val fastAbiNonDefaultExclusive = type.isFastAbi && descriptor.isExclusiveTo && !descriptor.isDefault
            val usesInner = replaceDefaultByInner && descriptor.isDefault && (interfaceType?.genericParameterCount ?: 0) == 0
            val usesDefaultInterfaceObjRef =
                !type.isSealedType && type.isFastAbi && descriptor.isExclusiveTo && descriptor.isDefault
            WinRtObjectReferencePlanDescriptor(
                interfaceName = descriptor.interfaceName,
                cacheName = cacheNameFor(descriptor.interfaceName),
                isDefaultInterface = descriptor.isDefault,
                skippedReason = when {
                    manuallyGenerated -> "manual-bindable"
                    mappedSkipReason != null -> mappedSkipReason
                    fastAbiNonDefaultExclusive -> "fast-abi-non-default-exclusive"
                    else -> null
                },
                usesInner = usesInner,
                usesDefaultInterfaceObjRef = usesDefaultInterfaceObjRef,
                defaultInterfaceHierarchyIndex = if (usesDefaultInterfaceObjRef) classHierarchyIndex else null,
                defaultInterfaceObjRefVtableSlot =
                    if (usesDefaultInterfaceObjRef && fastAbiDefaultSlot != null) {
                        fastAbiDefaultSlot.vtableStartIndex + fastAbiDefaultSlot.methodCount + classHierarchyIndex
                    } else {
                        null
                    },
                requiresGenericInstantiation = (interfaceType?.genericParameterCount ?: 0) != 0,
            )
        }
        return WinRtObjectReferenceSurfaceDescriptor(
            typeName = type.qualifiedName,
            inheritanceTypeNames = listOfNotNull(type.baseTypeName) + interfaces,
            objectReferenceNames = objectReferencePlans
                .filter { plan -> plan.skippedReason == null }
                .map(WinRtObjectReferencePlanDescriptor::cacheName),
            objectReferencePlans = objectReferencePlans,
            baseConstructorDispatchTargets = listOfNotNull(type.baseTypeName),
            exposedTypeMetadataNames = listOf(type.qualifiedName) +
                objectReferencePlans
                    .filter { plan -> plan.skippedReason == null }
                    .map(WinRtObjectReferencePlanDescriptor::interfaceName),
            hasRcwFactory = type.kind == WinRtTypeKind.RuntimeClass,
            hasUnwrappableNativeObject = type.kind in setOf(WinRtTypeKind.RuntimeClass, WinRtTypeKind.Delegate),
        )
    }

    private fun mappedObjectReferenceSkipReason(type: WinRtTypeDefinition): String? {
        val mapping = getMappedType(type.namespace, type.name) ?: return null
        if (mapping.mappedQualifiedName == null) {
            return "mapped-helper-only"
        }
        if (mapping.mappedNamespace == "System.Collections.Generic") {
            return null
        }
        return when (mapping.mappedNamespace) {
            "System",
            "System.ComponentModel",
            "System.Windows.Input",
            "System.Collections",
            "System.Collections.Specialized" -> "runtime-owned-mapped"
            else -> null
        }
    }

    fun managedAbiInvokeDescriptor(memberName: String, method: WinRtMethodDefinition, invokeKind: String): WinRtManagedAbiInvokeDescriptor {
        val plan = abiMarshalerPlanDescriptor(method)
        return WinRtManagedAbiInvokeDescriptor(
            memberName = memberName,
            invokeKind = invokeKind,
            managedLocals = plan.marshalers.map { it.name },
            conversionOperations = plan.marshalers.flatMap { slot ->
                listOfNotNull(
                    "toAbi".takeIf { slot.requiresToAbi },
                    "fromAbi".takeIf { slot.requiresFromAbi },
                    "dispose".takeIf { slot.requiresDispose },
                ).map { op -> "${slot.name}:$op" }
            },
            hasNoExceptionAttribute = plan.hasNoExceptionAttribute,
            requiresHelperMethod = plan.marshalers.any { it.isGeneric || it.category != WinRtMetadataParameterCategory.In },
        )
    }

    fun genericAbiClassInitializationDescriptor(type: WinRtTypeDefinition): WinRtGenericAbiClassInitializationDescriptor =
        WinRtGenericAbiClassInitializationDescriptor(
            typeName = type.qualifiedName,
            requiresRcwFallbackInitialization = type.genericParameterCount > 0 || hasDerivedGenericInterface(type),
            requiresCcwFallbackInitialization = doesAbiInterfaceImplementCcwInterface(type),
            genericMethodDelegateVariables = type.methods.filter { method ->
                signatureWriterDescriptor(method).hasProjectedGenericParameters
            }.map { method -> "_${method.name}Delegate" }.sorted(),
            invokeSlotNames = methodVtableDescriptors(type).map(WinRtMethodVtableDescriptor::vmethodName),
            genericTypeArrayDependencies = type.methods.flatMap(::getGenericAbiTypes).distinct().sorted(),
        )

    fun requiredInterfaceAugmentationDescriptor(type: WinRtTypeDefinition): WinRtRequiredInterfaceAugmentationDescriptor {
        val required = collectRequiredInterfaceClosure(type)
        val mappedMembers = required.mapNotNull { name ->
            val ref = WinRtTypeRef.fromDisplayName(name)
            val qualifiedName = ref.normalized().qualifiedName.orEmpty()
            getMappedType(qualifiedName.substringBeforeLast('.', ""), qualifiedName.substringAfterLast('.'))?.mappedName
        }.sorted()
        return WinRtRequiredInterfaceAugmentationDescriptor(
            typeName = type.qualifiedName,
            requiredInterfaceNames = required,
            explicitForwardMemberNames = explicitImplementations(type).mapNotNull(WinRtExplicitImplementationDescriptor::declarationName).sorted(),
            mappedAugmentationMembers = mappedMembers,
            mappedHelperPlans = required.mapNotNull(::requiredMappedHelperPlan),
            genericAbiParameterArrays = type.methods.mapNotNull { method ->
                getGenericAbiTypes(method).takeIf(List<*>::isNotEmpty)
            },
            implementsCcwInterface = doesAbiInterfaceImplementCcwInterface(type),
        )
    }

    private fun collectRequiredInterfaceClosure(type: WinRtTypeDefinition): List<String> {
        val required = linkedSetOf<String>()
        fun visit(interfaceRef: WinRtTypeRef, currentNamespace: String) {
            val resolved = resolveTypeReference(interfaceRef, currentNamespace, typesByQualifiedName)
            if (!required.add(resolved.displayName)) {
                return
            }
            val definition = resolved.definitionQualifiedName?.let(typesByQualifiedName::get) ?: return
            val genericArguments = resolved.type.typeArguments
            definition.implementedInterfaces.forEach { implemented ->
                visit(
                    implemented.interfaceType.substituteTypeParameters(genericArguments).normalized(),
                    definition.namespace,
                )
            }
        }
        type.implementedInterfaces.forEach { implemented ->
            visit(implemented.interfaceType, type.namespace)
        }
        return required.sorted()
    }

    private fun requiredMappedHelperPlan(interfaceName: String): WinRtRequiredMappedHelperPlanDescriptor? {
        val ref = WinRtTypeRef.fromDisplayName(interfaceName).normalized()
        val qualifiedName = ref.qualifiedName.orEmpty()
        val mapping = getMappedType(qualifiedName.substringBeforeLast('.', ""), qualifiedName.substringAfterLast('.')) ?: return null
        val typeArguments = ref.typeArguments.map { it.normalized().typeName }
        fun genericEnumerable(elementName: String): String =
            "System.Collections.Generic.IEnumerable<$elementName>"
        fun keyValuePairEnumerable(): String? {
            val key = typeArguments.getOrNull(0) ?: return null
            val value = typeArguments.getOrNull(1) ?: return null
            return genericEnumerable("System.Collections.Generic.KeyValuePair<$key, $value>")
        }
        val plan = when {
            mapping.abiName == "IIterable" -> RequiredMappedHelperPlan(
                memberFamily = "IEnumerable",
                helperWrapperName = "ABI.System.Collections.Generic.IEnumerable",
                adapterFieldName = "_iterableToEnumerable",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = null,
            )
            mapping.abiName == "IIterator" -> RequiredMappedHelperPlan(
                memberFamily = "IEnumerator",
                helperWrapperName = "ABI.System.Collections.Generic.IEnumerator",
                adapterFieldName = "_iteratorToEnumerator",
                removesNonGenericEnumerable = false,
                removesGenericEnumerableName = null,
            )
            mapping.abiName == "IMapView" -> RequiredMappedHelperPlan(
                memberFamily = "IReadOnlyDictionary",
                helperWrapperName = "ABI.System.Collections.Generic.IReadOnlyDictionary",
                adapterFieldName = "_mapViewToReadOnlyDictionary",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = keyValuePairEnumerable(),
            )
            mapping.abiName == "IMap" -> RequiredMappedHelperPlan(
                memberFamily = "IDictionary",
                helperWrapperName = "ABI.System.Collections.Generic.IDictionary",
                adapterFieldName = "_mapToDictionary",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = keyValuePairEnumerable(),
            )
            mapping.abiName == "IVectorView" -> RequiredMappedHelperPlan(
                memberFamily = "IReadOnlyList",
                helperWrapperName = "ABI.System.Collections.Generic.IReadOnlyList",
                adapterFieldName = "_vectorViewToReadOnlyList",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = typeArguments.singleOrNull()?.let { genericEnumerable(it) },
            )
            mapping.abiName == "IVector" -> RequiredMappedHelperPlan(
                memberFamily = "IList",
                helperWrapperName = "ABI.System.Collections.Generic.IList",
                adapterFieldName = "_vectorToList",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = typeArguments.singleOrNull()?.let { genericEnumerable(it) },
            )
            mapping.abiName == "IBindableIterable" -> RequiredMappedHelperPlan(
                memberFamily = "IEnumerable",
                helperWrapperName = "ABI.System.Collections.IEnumerable",
                adapterFieldName = "_bindableIterableToEnumerable",
                removesNonGenericEnumerable = false,
                removesGenericEnumerableName = null,
            )
            mapping.abiName == "IBindableVector" -> RequiredMappedHelperPlan(
                memberFamily = "IList",
                helperWrapperName = "ABI.System.Collections.IList",
                adapterFieldName = "_bindableVectorToList",
                removesNonGenericEnumerable = true,
                removesGenericEnumerableName = null,
            )
            mapping.mappedName == "IDisposable" -> RequiredMappedHelperPlan(
                memberFamily = "IDisposable",
                helperWrapperName = null,
                adapterFieldName = null,
                removesNonGenericEnumerable = false,
                removesGenericEnumerableName = null,
            )
            mapping.mappedName == "INotifyDataErrorInfo" -> RequiredMappedHelperPlan(
                memberFamily = "INotifyDataErrorInfo",
                helperWrapperName = null,
                adapterFieldName = null,
                removesNonGenericEnumerable = false,
                removesGenericEnumerableName = null,
            )
            mapping.mappedName == "INotifyPropertyChanged" -> RequiredMappedHelperPlan(
                memberFamily = "INotifyPropertyChanged",
                helperWrapperName = null,
                adapterFieldName = null,
                removesNonGenericEnumerable = false,
                removesGenericEnumerableName = null,
            )
            else -> return null
        }
        return WinRtRequiredMappedHelperPlanDescriptor(
            interfaceName = interfaceName,
            mappedTypeName = mapping.mappedQualifiedName ?: mapping.mappedName.orEmpty(),
            memberFamily = plan.memberFamily,
            helperWrapperName = plan.helperWrapperName,
            adapterFieldName = plan.adapterFieldName,
            callMode = "idic",
            emitsMappedTypeHelpers = false,
            emitsPrivateMembers = true,
            removesNonGenericEnumerable = plan.removesNonGenericEnumerable,
            removesGenericEnumerableName = plan.removesGenericEnumerableName,
        )
    }

    private data class RequiredMappedHelperPlan(
        val memberFamily: String,
        val helperWrapperName: String?,
        val adapterFieldName: String?,
        val removesNonGenericEnumerable: Boolean,
        val removesGenericEnumerableName: String?,
    )

    fun moduleActivationAndAuthoringDescriptor(type: WinRtTypeDefinition): WinRtModuleActivationAndAuthoringDescriptor {
        val factory = factorySurfaceDescriptor(type)
        return WinRtModuleActivationAndAuthoringDescriptor(
            typeName = type.qualifiedName,
            authoringMetadataTypeName = type.qualifiedName.takeIf { type.kind == WinRtTypeKind.RuntimeClass },
            factoryClassName = "${type.name.substringBefore('`')}ActivationFactory".takeIf { factory.constructorFactories.isNotEmpty() || factory.staticMemberTargets.isNotEmpty() },
            factoryMemberNames = factory.constructorFactories + factory.staticMemberTargets + factory.composableFactories,
            moduleActivationFactoryEntries = listOf(type.qualifiedName).filter { factory.constructorFactories.isNotEmpty() || factory.staticMemberTargets.isNotEmpty() },
            baseTypeEntry = type.baseTypeName?.let { "${type.qualifiedName} -> $it" },
            metadataTypeEntry = type.qualifiedName.takeIf { type.kind != WinRtTypeKind.Unknown },
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
            WinRtMetadataParityAuditEntry("helpers.h", "object/class equals/hashcode helpers", "WinRtMetadataSemanticHelpers.classObjectMethodDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "generic ABI delegate operation entries", "WinRtGenericAbiDelegateDescriptor.operationName/declaration/typeArrayShape", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "write_generic_type_instantiation descriptor", "WinRtMetadataSemanticHelpers.genericInstantiationWriterDescriptor", true),
            WinRtMetadataParityAuditEntry("type_writers.h/code_writers.h", "type-name writer context", "WinRtMetadataSemanticHelpers.typeNameDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "event helper subclass descriptors", "WinRtMetadataSemanticHelpers.eventHelperSubclassDescriptors", true),
            WinRtMetadataParityAuditEntry("type_writers.h/code_writers.h", "platform guard/member platform descriptors", "WinRtMetadataSemanticHelpers.platformGuardDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "projected/ABI signature writer descriptors", "WinRtMetadataSemanticHelpers.signatureWriterDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "event invoke/event-source descriptors", "WinRtMetadataSemanticHelpers.eventInvokeDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "ABI marshaler plan descriptors", "WinRtMetadataSemanticHelpers.abiMarshalerPlanDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "static/factory/composable surface descriptors", "WinRtMetadataSemanticHelpers.factorySurfaceDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "custom mapped member output descriptors", "WinRtMetadataSemanticHelpers.customMappedMemberOutputDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "property-interface/member signature descriptors", "WinRtMetadataSemanticHelpers.interfaceMemberSignatureSetDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "GUID/IID signature writer descriptors", "WinRtMetadataSemanticHelpers.guidSignatureDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "vtable delegate/function-pointer descriptors", "WinRtMetadataSemanticHelpers.vtableWriterDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "type declaration writer taxonomy", "WinRtMetadataSemanticHelpers.typeDeclarationDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "object-reference/inheritance surface descriptors", "WinRtMetadataSemanticHelpers.objectReferenceSurfaceDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "managed ABI invoke descriptors", "WinRtMetadataSemanticHelpers.managedAbiInvokeDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "generic ABI class initialization descriptors", "WinRtMetadataSemanticHelpers.genericAbiClassInitializationDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "required-interface ABI augmentation descriptors", "WinRtMetadataSemanticHelpers.requiredInterfaceAugmentationDescriptor", true),
            WinRtMetadataParityAuditEntry("code_writers.h", "module activation/authoring helper descriptors", "WinRtMetadataSemanticHelpers.moduleActivationAndAuthoringDescriptor", true),
            WinRtMetadataParityAuditEntry("cswinrt full audit", "metadata/generator/runtime/plugin/authoring classification", "PLAN.md Queue 10.9 + Metadata audit classification", true),
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

    private fun fastAbiInterfaceSlots(closure: WinRtRuntimeClassClosureDescriptor): List<WinRtFastAbiInterfaceSlot> {
        var vtableStartIndex = 6
        return closure.fastAbiInterfaces.mapIndexed { index, interfaceDescriptor ->
            val methodCount = interfaceDescriptor.definitionType?.methods?.size ?: 0
            val slot = WinRtFastAbiInterfaceSlot(
                interfaceName = interfaceDescriptor.interfaceName,
                interfaceType = interfaceDescriptor.interfaceType,
                isDefault = interfaceDescriptor.isDefault,
                vtableStartIndex = vtableStartIndex,
                methodCount = methodCount,
                hierarchyOffsetAfterDefault = if (index == 0) closure.classHierarchyIndex else 0,
            )
            vtableStartIndex = slot.nextVtableStartIndex
            slot
        }
    }

    private fun fastAbiPropertySlots(closure: WinRtRuntimeClassClosureDescriptor): List<WinRtFastAbiPropertySlot> {
        val slots = mutableListOf<WinRtFastAbiPropertySlot>()
        fastAbiInterfaceSlots(closure).forEach { interfaceSlot ->
            val interfaceDescriptor = closure.fastAbiInterfaces.first { it.interfaceName == interfaceSlot.interfaceName }
            val interfaceType = interfaceDescriptor.definitionType
            val methodOrder = interfaceType?.let(::methodOrderByName).orEmpty()
            interfaceType?.properties.orEmpty().forEach { property ->
                val getterOffset = property.getterMethodName?.let { methodOrder[it] }
                val setterOffset = property.setterMethodName?.let { methodOrder[it] }
                slots += WinRtFastAbiPropertySlot(
                    propertyName = property.name,
                    getterMethodName = property.getterMethodName,
                    setterMethodName = property.setterMethodName,
                    vtableStartIndex = interfaceSlot.vtableStartIndex,
                    getterVtableIndex = getterOffset?.let { interfaceSlot.vtableStartIndex + it },
                    setterVtableIndex = setterOffset?.let { interfaceSlot.vtableStartIndex + it },
                )
            }
        }
        return slots
    }

    private fun methodOrderByName(type: WinRtTypeDefinition): Map<String, Int> =
        type.normalized().let { normalized ->
            val members = buildList<Pair<String, Int?>> {
                normalized.properties.forEach { property ->
                    property.getterMethodName?.let { add(it to normalized.effectiveAccessorRowId(it, property.getterMethodRowId)) }
                    property.setterMethodName?.let { add(it to normalized.effectiveAccessorRowId(it, property.setterMethodRowId)) }
                }
                normalized.events.forEach { event ->
                    event.addMethodName?.let { add(it to normalized.effectiveAccessorRowId(it, event.addMethodRowId)) }
                    event.removeMethodName?.let { add(it to normalized.effectiveAccessorRowId(it, event.removeMethodRowId)) }
                }
                normalized.methods.forEach { method ->
                    add(method.name to method.methodRowId)
                }
            }
            val rowIndex = members.mapNotNull { it.second }.distinct().sorted().mapIndexed { index, rowId -> rowId to index }.toMap()
            var fallbackIndex = rowIndex.size
            buildMap {
                members.forEach { (name, rowId) ->
                    if (!containsKey(name)) {
                        put(name, rowId?.let(rowIndex::get) ?: fallbackIndex++)
                    }
                }
            }
        }

    private fun abiMemberOrderByRowId(type: WinRtTypeDefinition): Map<Int, Int> {
        val members = buildList {
            type.properties.forEach { property ->
                property.getterMethodName?.let { type.effectiveAccessorRowId(it, property.getterMethodRowId) }?.let(::add)
                property.setterMethodName?.let { type.effectiveAccessorRowId(it, property.setterMethodRowId) }?.let(::add)
            }
            type.events.forEach { event ->
                event.addMethodName?.let { type.effectiveAccessorRowId(it, event.addMethodRowId) }?.let(::add)
                event.removeMethodName?.let { type.effectiveAccessorRowId(it, event.removeMethodRowId) }?.let(::add)
            }
            type.methods.forEach { method ->
                method.methodRowId?.let(::add)
            }
        }.distinct().sorted()
        return members.mapIndexed { index, rowId -> rowId to index }.toMap()
    }

    private fun WinRtTypeDefinition.effectiveAccessorRowId(accessorName: String, declaredRowId: Int?): Int? =
        methods.firstOrNull { it.name == accessorName }?.methodRowId ?: declaredRowId

    private fun projectedSignatureHasGenericParameters(
        returnType: WinRtTypeRef,
        parameters: List<WinRtParameterDefinition>,
    ): Boolean =
        returnType.containsGenericTypeParameter() || parameters.any { parameter -> parameter.type.containsGenericTypeParameter() }

    private fun signatureHasOnlyGenericReturn(
        returnType: WinRtTypeRef,
        parameters: List<WinRtParameterDefinition>,
    ): Boolean =
        returnType.containsGenericTypeParameter() && parameters.none { parameter -> parameter.type.containsGenericTypeParameter() }

    private fun WinRtTypeRef.containsGenericTypeParameter(): Boolean {
        val normalized = normalized()
        return normalized.kind == WinRtTypeRefKind.GenericTypeParameter ||
            normalized.kind == WinRtTypeRefKind.MethodTypeParameter ||
            normalized.typeArguments.any { it.containsGenericTypeParameter() } ||
            normalized.elementType?.containsGenericTypeParameter() == true
    }

    private fun stripGenericArity(name: String): String = name.substringBefore('`')

    private fun renderAbiTypeName(type: WinRtTypeRef): String =
        type.normalized().typeName.replace("String", "IntPtr")

    private fun signatureParameterWriterDescriptor(
        parameter: WinRtParameterDefinition,
        category: WinRtMetadataParameterCategory,
    ): WinRtSignatureParameterWriterDescriptor =
        WinRtSignatureParameterWriterDescriptor(
            originalName = parameter.name,
            escapedName = escapeIdentifier(parameter.name),
            category = category,
            projectionTypeName = parameter.type.normalized().typeName,
            abiTypeName = renderAbiTypeName(parameter.type),
            modifier = when (category) {
                WinRtMetadataParameterCategory.Ref -> "ref"
                WinRtMetadataParameterCategory.Out,
                WinRtMetadataParameterCategory.FillArray,
                WinRtMetadataParameterCategory.ReceiveArray,
                -> "out"
                else -> null
            },
            expandsArrayLength = category in setOf(
                WinRtMetadataParameterCategory.PassArray,
                WinRtMetadataParameterCategory.FillArray,
                WinRtMetadataParameterCategory.ReceiveArray,
            ),
        )

    private fun abiMarshalerSlot(
        name: String,
        type: WinRtTypeRef,
        category: WinRtMetadataParameterCategory,
        isReturn: Boolean,
    ): WinRtAbiMarshalerSlotDescriptor {
        val normalized = type.normalized()
        val classification = typeClassifier.classify(normalized, "")
        val generic = normalized.containsGenericTypeParameter()
        val array = normalized.kind == WinRtTypeRefKind.Array
        val value = semanticValueDescriptor(normalized, "", forArray = array)
        val pinnable = value.isBlittable && (category == WinRtMetadataParameterCategory.In || category == WinRtMetadataParameterCategory.PassArray)
        val requiresMarshal = generic || array || classification.projectionCategory !in setOf(
            WinRtProjectionCategory.Fundamental,
            WinRtProjectionCategory.Enum,
            WinRtProjectionCategory.Guid,
        )
        return WinRtAbiMarshalerSlotDescriptor(
            name = escapeIdentifier(name),
            typeName = normalized.typeName,
            category = category,
            isReturn = isReturn,
            isPinnable = pinnable,
            isGeneric = generic,
            requiresFromAbi = isReturn || category in setOf(WinRtMetadataParameterCategory.Out, WinRtMetadataParameterCategory.Ref, WinRtMetadataParameterCategory.ReceiveArray),
            requiresToAbi = !isReturn && category in setOf(WinRtMetadataParameterCategory.In, WinRtMetadataParameterCategory.Ref, WinRtMetadataParameterCategory.PassArray),
            requiresDispose = requiresMarshal && !pinnable,
        )
    }

    private fun cacheNameFor(typeName: String): String =
        escapeTypeNameForIdentifier(typeName).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "Cache"

    private fun getGenericAbiTypes(method: WinRtMethodDefinition): List<String> =
        (listOf(method.returnType) + method.parameters.map(WinRtParameterDefinition::type))
            .filter { it.containsGenericTypeParameter() || it.typeArguments.isNotEmpty() }
            .map { renderAbiTypeName(it) }
            .distinct()

    private fun guidTextToBytes(guid: String): List<Int> =
        guid.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }

    private fun guidSignatureFragment(type: WinRtTypeDefinition): String =
        when (type.kind) {
            WinRtTypeKind.Interface -> type.iid?.let(::guidSignatureFragmentForGuid).orEmpty()
            WinRtTypeKind.Delegate -> type.iid?.let { guid -> "delegate(${guidSignatureFragmentForGuid(guid)})" }.orEmpty()
            WinRtTypeKind.RuntimeClass -> {
                val defaultInterfaceSignature = getDefaultInterface(type)
                    ?.let { defaultInterface -> guidSignatureFragmentForTypeRef(defaultInterface, type.namespace) }
                    .orEmpty()
                if (defaultInterfaceSignature.isNotEmpty()) {
                    "rc(${type.qualifiedName};$defaultInterfaceSignature)"
                } else {
                    type.iid?.let(::guidSignatureFragmentForGuid).orEmpty()
                }
            }
            WinRtTypeKind.Struct -> {
                val fieldSignatures = type.fields
                    .filterNot { field -> field.isStatic || field.isLiteral }
                    .joinToString(";") { field -> guidSignatureFragmentForTypeRef(field.type, type.namespace) }
                "struct(${type.qualifiedName};$fieldSignatures)"
            }
            WinRtTypeKind.Enum -> "enum(${type.qualifiedName};${if (isFlagsEnum(type)) "u4" else "i4"})"
            WinRtTypeKind.Unknown -> ""
        }

    private fun guidSignatureFragmentForTypeRef(type: WinRtTypeRef, currentNamespace: String): String {
        val normalizedType = type.normalized()
        fundamentalGuidSignatureFragment(normalizedType.typeName)?.let { signature -> return signature }
        if (normalizedType.kind != WinRtTypeRefKind.Named) {
            return ""
        }
        val resolvedType = resolveTypeReference(normalizedType, currentNamespace, typesByQualifiedName)
        val definition = resolvedType.definitionType
        if (normalizedType.typeArguments.isNotEmpty() && definition?.iid != null) {
            return buildString {
                append("pinterface(")
                append(guidSignatureFragmentForGuid(definition.iid))
                normalizedType.typeArguments.forEach { argument ->
                    append(';')
                    append(guidSignatureFragmentForTypeRef(argument, currentNamespace))
                }
                append(')')
            }
        }
        return definition?.let(::guidSignatureFragment)
            ?: fundamentalGuidSignatureFragment(resolvedType.displayName)
            ?: ""
    }

    private fun eventDelegateInterfaceId(
        type: WinRtTypeRef,
        currentNamespace: String,
    ): io.github.composefluent.winrt.runtime.Guid? {
        val normalizedType = type.normalized()
        val resolvedType = resolveTypeReference(normalizedType, currentNamespace, typesByQualifiedName)
        val definition = resolvedType.definitionType
        if (normalizedType.typeArguments.isEmpty()) {
            return definition?.iid
        }
        val signature = guidSignatureFragmentForTypeRef(normalizedType, currentNamespace)
        if (signature.isBlank()) {
            return definition?.iid
        }
        return io.github.composefluent.winrt.runtime.ParameterizedInterfaceId.createFromSignature(signature)
    }

    private fun guidSignatureFragmentForGuid(guid: io.github.composefluent.winrt.runtime.Guid): String =
        "{${guid.toString().lowercase()}}"

    private fun fundamentalGuidSignatureFragment(typeName: String): String? =
        if (isWinRtObjectTypeName(typeName)) {
            "cinterface(IInspectable)"
        } else when (typeName.substringBefore('<').removeSuffix("?")) {
            "Boolean", "Bool" -> "b1"
            "Char", "Char16" -> "c2"
            "Byte", "Int8", "SByte" -> "i1"
            "UByte", "UInt8" -> "u1"
            "Short", "Int16" -> "i2"
            "UShort", "UInt16" -> "u2"
            "Int", "Int32" -> "i4"
            "UInt", "UInt32" -> "u4"
            "Long", "Int64" -> "i8"
            "ULong", "UInt64" -> "u8"
            "Float", "Single" -> "f4"
            "Double" -> "f8"
            "String" -> "string"
            "Guid", "System.Guid" -> "g16"
            else -> null
        }

    private fun escapeIdentifier(value: String): String =
        if (value in KOTLIN_KEYWORDS || value in CSWINRT_KEYWORDS) "`$value`" else value

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
                WinRtTypeKind.RuntimeClass -> addGenericTypeReferencesInRuntimeClass(type)
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

        private fun addGenericTypeReferencesInRuntimeClass(type: WinRtTypeDefinition) {
            type.methods.filterNot(::isSpecial).forEach { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
            type.properties.forEach { property -> addIfGenericTypeReference(property.type, property.type.kind == WinRtTypeRefKind.Array, type.namespace) }
            type.events.forEach { event -> addIfGenericTypeReference(event.delegateType, isArray = false, currentNamespace = type.namespace) }
            type.implementedInterfaces.forEach { implemented ->
                addIfGenericTypeReference(implemented.interfaceType, isArray = false, currentNamespace = type.namespace)
            }
            type.activation.factories.forEach { factory ->
                typesByQualifiedName[factory.interfaceName]?.let(::addGenericTypeReferencesInInterfaceType)
            }
            type.activation.staticInterfaceNames.forEach { interfaceName ->
                typesByQualifiedName[interfaceName]?.let(::addGenericTypeReferencesInInterfaceType)
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
            abiDelegateOperationsFor(name, normalized.typeArguments).forEach { operation ->
                addAbiDelegateOperation(normalized, operation, namespace)
            }
        }

        private fun addAbiDelegateOperation(
            sourceType: WinRtTypeRef,
            operation: AbiDelegateOperation,
            currentNamespace: String,
        ) {
            val abiTypeNames = operation.argumentIndexes.mapNotNull { index ->
                sourceType.typeArguments.getOrNull(index)?.let { argument ->
                    renderAbiDelegateTypeName(argument, currentNamespace)
                }
            }
            val escapedAbiTypes = abiTypeNames.map(::escapeTypeNameForIdentifier)
            val suffix = escapedAbiTypes.joinToString("_")
            val delegateName = if (suffix.isEmpty()) "_${operation.name}" else "_${operation.name}_$suffix"
            val key = listOf(sourceType.typeName, operation.name, abiTypeNames.joinToString("|")).joinToString("#")
            abiDelegates.putIfAbsent(
                key,
                WinRtGenericAbiDelegateDescriptor(
                    abiDelegateName = delegateName,
                    sourceGenericType = sourceType,
                    abiDelegateTypesKey = abiTypeNames.joinToString("_"),
                    genericArguments = sourceType.typeArguments,
                    operationName = operation.name,
                    abiParameterTypeNames = operation.parameterShape(abiTypeNames),
                    declaration = operation.declaration(delegateName, abiTypeNames),
                    typeArrayShape = operation.typeArrayShape(abiTypeNames),
                ),
            )
        }

        private fun abiDelegateOperationsFor(typeName: String, arguments: List<WinRtTypeRef>): List<AbiDelegateOperation> =
            when (typeName) {
                "IIterator" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn))
                "IKeyValuePair" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Key", listOf(0), AbiDelegateShape.IntPtrOutPointer)) +
                        ifRequired(arguments, 1, AbiDelegateOperation("get_Value", listOf(1), AbiDelegateShape.IntPtrOutPointer))
                "IMapView" -> {
                    val lookup = if (arguments.getOrNull(0)?.isAbiDelegateRequired() == true || arguments.getOrNull(1)?.isAbiDelegateRequired() == true) {
                        listOf(AbiDelegateOperation("lookup", listOf(0, 1), AbiDelegateShape.MapLookup))
                    } else {
                        emptyList()
                    }
                    lookup + ifRequired(arguments, 0, AbiDelegateOperation("has_key", listOf(0), AbiDelegateShape.HasKey))
                }
                "IMap" -> {
                    val mapOperations = if (arguments.getOrNull(0)?.isAbiDelegateRequired() == true || arguments.getOrNull(1)?.isAbiDelegateRequired() == true) {
                        listOf(
                            AbiDelegateOperation("lookup", listOf(0, 1), AbiDelegateShape.MapLookup),
                            AbiDelegateOperation("insert", listOf(0, 1), AbiDelegateShape.MapInsert),
                        )
                    } else {
                        emptyList()
                    }
                    mapOperations +
                        ifRequired(arguments, 0, AbiDelegateOperation("has_key", listOf(0), AbiDelegateShape.HasKey)) +
                        ifRequired(arguments, 0, AbiDelegateOperation("remove", listOf(0), AbiDelegateShape.Remove))
                }
                "IVectorView" ->
                    ifRequired(
                        arguments,
                        0,
                        AbiDelegateOperation("get_at", listOf(0), AbiDelegateShape.IndexOutReturn),
                        AbiDelegateOperation("index_of", listOf(0), AbiDelegateShape.IndexOf),
                        AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn),
                    )
                "IVector" ->
                    ifRequired(
                        arguments,
                        0,
                        AbiDelegateOperation("get_at", listOf(0), AbiDelegateShape.IndexOutReturn),
                        AbiDelegateOperation("index_of", listOf(0), AbiDelegateShape.IndexOf),
                        AbiDelegateOperation("set_at", listOf(0), AbiDelegateShape.IndexValue),
                        AbiDelegateOperation("append", listOf(0), AbiDelegateShape.Value),
                        AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn),
                    )
                "EventHandler" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("invoke", listOf(0), AbiDelegateShape.EventHandler))
                "IReference" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Value", listOf(0), AbiDelegateShape.OutReturn))
                "IMapChangedEventArgs", "IAsyncOperation" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get", listOf(0), AbiDelegateShape.OutReturn))
                "IAsyncOperationWithProgress" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get", listOf(0), AbiDelegateShape.OutReturn)) +
                        ifRequired(arguments, 1, AbiDelegateOperation("invoke", listOf(1), AbiDelegateShape.AsyncProgress))
                "TypedEventHandler" -> {
                    if (arguments.getOrNull(0)?.isAbiDelegateRequired() == true || arguments.getOrNull(1)?.isAbiDelegateRequired() == true) {
                        listOf(AbiDelegateOperation("invoke", listOf(0, 1), AbiDelegateShape.TypedEventHandler))
                    } else {
                        emptyList()
                    }
                }
                "AsyncOperationProgressHandler" ->
                    ifRequired(arguments, 1, AbiDelegateOperation("invoke", listOf(1), AbiDelegateShape.AsyncProgress))
                "AsyncActionProgressHandler" ->
                    ifRequired(arguments, 0, AbiDelegateOperation("invoke", listOf(0), AbiDelegateShape.AsyncProgress))
                else -> emptyList()
            }

        private fun ifRequired(
            arguments: List<WinRtTypeRef>,
            index: Int,
            vararg operations: AbiDelegateOperation,
        ): List<AbiDelegateOperation> =
            if (arguments.getOrNull(index)?.isAbiDelegateRequired() == true) operations.toList() else emptyList()

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

        private fun renderAbiDelegateTypeName(type: WinRtTypeRef, currentNamespace: String): String {
            val resolved = resolveTypeReference(type.normalized(), currentNamespace, typesByQualifiedName).type.normalized()
            val descriptor = typeClassifier.classify(resolved, currentNamespace)
            return when (descriptor.projectionCategory) {
                WinRtProjectionCategory.Fundamental,
                WinRtProjectionCategory.String,
                -> when (descriptor.typeName) {
                    "String" -> "IntPtr"
                    "Boolean" -> "byte"
                    "Char16" -> "ushort"
                    else -> descriptor.typeName
                }
                WinRtProjectionCategory.Guid,
                WinRtProjectionCategory.Enum,
                WinRtProjectionCategory.Struct,
                -> descriptor.type.typeName
                WinRtProjectionCategory.Object,
                WinRtProjectionCategory.Interface,
                WinRtProjectionCategory.RuntimeClass,
                WinRtProjectionCategory.Delegate,
                WinRtProjectionCategory.Attribute,
                WinRtProjectionCategory.Array,
                -> "IntPtr"
                WinRtProjectionCategory.Type,
                WinRtProjectionCategory.ApiContract,
                WinRtProjectionCategory.GenericTypeParameter,
                WinRtProjectionCategory.MethodTypeParameter,
                WinRtProjectionCategory.Unit,
                WinRtProjectionCategory.Unknown,
                -> descriptor.type.typeName
            }
        }
    }

    private enum class AbiDelegateShape {
        OutReturn,
        IntPtrOutPointer,
        MapLookup,
        MapInsert,
        HasKey,
        Remove,
        IndexOutReturn,
        IndexOf,
        IndexValue,
        Value,
        EventHandler,
        TypedEventHandler,
        AsyncProgress,
    }

    private data class AbiDelegateOperation(
        val name: String,
        val argumentIndexes: List<Int>,
        val shape: AbiDelegateShape,
    ) {
        fun parameterShape(abiTypes: List<String>): List<String> =
            when (shape) {
                AbiDelegateShape.OutReturn -> listOf("void*", "out ${abiTypes[0]}", "int")
                AbiDelegateShape.IntPtrOutPointer -> listOf("IntPtr", "${abiTypes[0]}*", "int")
                AbiDelegateShape.MapLookup -> listOf("void*", abiTypes[0], "out ${abiTypes[1]}", "int")
                AbiDelegateShape.MapInsert -> listOf("void*", abiTypes[0], abiTypes[1], "out byte", "int")
                AbiDelegateShape.HasKey -> listOf("void*", abiTypes[0], "out byte", "int")
                AbiDelegateShape.Remove -> listOf("void*", abiTypes[0], "int")
                AbiDelegateShape.IndexOutReturn -> listOf("void*", "uint", "out ${abiTypes[0]}", "int")
                AbiDelegateShape.IndexOf -> listOf("void*", abiTypes[0], "out uint", "out byte", "int")
                AbiDelegateShape.IndexValue -> listOf("void*", "uint", abiTypes[0], "int")
                AbiDelegateShape.Value -> listOf("void*", abiTypes[0], "int")
                AbiDelegateShape.EventHandler -> listOf("void*", "IntPtr", abiTypes[0], "int")
                AbiDelegateShape.TypedEventHandler -> listOf("void*", abiTypes[0], abiTypes[1], "int")
                AbiDelegateShape.AsyncProgress -> listOf("void*", "IntPtr", abiTypes[0], "int")
            }

        fun declaration(delegateName: String, abiTypes: List<String>): String =
            "internal unsafe delegate int $delegateName(${parameterShape(abiTypes).dropLast(1).joinToString(", ")});"

        fun typeArrayShape(abiTypes: List<String>): List<String> =
            parameterShape(abiTypes).map { parameter ->
                when {
                    parameter.startsWith("out ") -> "${parameter.removePrefix("out ")}.MakeByRefType()"
                    parameter.endsWith("*") -> parameter
                    else -> parameter
                }
            }
    }

    companion object {
        private const val SYSTEM_FLAGS_ATTRIBUTE = "System.FlagsAttribute"
        private const val WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO_ATTRIBUTE = "Windows.Foundation.Metadata.ExclusiveToAttribute"
        private const val INSPECTABLE_METHOD_COUNT = 6
        private val PROJECTION_AFFECTING_AUXILIARY_TABLES = setOf("FieldMarshal", "ImplMap", "FieldRVA", "DeclSecurity", "ModuleRef")
        private val KOTLIN_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while",
        )
        private val CSWINRT_KEYWORDS = setOf("event", "delegate", "base", "params", "ref", "out", "in")
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
