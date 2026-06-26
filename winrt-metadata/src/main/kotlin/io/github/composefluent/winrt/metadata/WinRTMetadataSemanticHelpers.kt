package io.github.composefluent.winrt.metadata

enum class WinRTMetadataParameterCategory {
    In,
    Ref,
    Out,
    PassArray,
    FillArray,
    ReceiveArray,
}

data class WinRTMethodSignatureParameter(
    val parameter: WinRTParameterDefinition,
    val category: WinRTMetadataParameterCategory,
)

data class WinRTMethodSignatureDescriptor(
    val method: WinRTMethodDefinition,
    val returnType: WinRTTypeRef,
    val returnParameterName: String = "__return_value__",
    val parameters: List<WinRTMethodSignatureParameter>,
) {
    fun hasParams(): Boolean = parameters.isNotEmpty()
}

data class WinRTPropertyAccessorDescriptor(
    val getterMethodName: String?,
    val setterMethodName: String?,
    val getterMethodRowId: Int?,
    val setterMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

data class WinRTEventAccessorDescriptor(
    val addMethodName: String?,
    val removeMethodName: String?,
    val addMethodRowId: Int?,
    val removeMethodRowId: Int?,
    val isStatic: Boolean,
    val hasValidAccessors: Boolean,
)

data class WinRTAttributedFactoryDescriptor(
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val type: WinRTTypeDefinition?,
    val kind: WinRTAttributedFactoryKind? = null,
    val activatable: Boolean = false,
    val statics: Boolean = false,
    val composable: Boolean = false,
    val visible: Boolean = false,
)

data class WinRTFastAbiPropertySlot(
    val propertyName: String,
    val getterMethodName: String?,
    val setterMethodName: String?,
    val vtableStartIndex: Int,
    val getterVtableIndex: Int?,
    val setterVtableIndex: Int?,
)

data class WinRTFastAbiInterfaceSlot(
    val interfaceName: String,
    val interfaceType: WinRTTypeRef,
    val isDefault: Boolean,
    val vtableStartIndex: Int,
    val methodCount: Int,
    val hierarchyOffsetAfterDefault: Int = 0,
) {
    val nextVtableStartIndex: Int
        get() = vtableStartIndex + methodCount + hierarchyOffsetAfterDefault
}

data class WinRTFastAbiClassDescriptor(
    val classType: WinRTTypeDefinition,
    val defaultInterface: WinRTRuntimeClassInterfaceDescriptor?,
    val otherInterfaces: List<WinRTRuntimeClassInterfaceDescriptor>,
    val interfaceSlots: List<WinRTFastAbiInterfaceSlot>,
    val propertySlots: List<WinRTFastAbiPropertySlot>,
) {
    private val otherInterfaceNames = otherInterfaces.map(WinRTRuntimeClassInterfaceDescriptor::interfaceName).toSet()
    private val getterNames = propertySlots.mapNotNull { slot -> slot.getterMethodName?.let { slot.propertyName } }.toSet()
    private val setterNames = propertySlots.mapNotNull { slot -> slot.setterMethodName?.let { slot.propertyName } }.toSet()

    fun containsOtherInterface(interfaceName: String): Boolean = interfaceName in otherInterfaceNames
    fun containsGetter(propertyName: String): Boolean = propertyName in getterNames
    fun containsSetter(propertyName: String): Boolean = propertyName in setterNames
}

data class WinRTValueTypeFieldDescriptor(
    val field: WinRTFieldDefinition,
    val type: WinRTTypeClassificationDescriptor,
    val offset: Int?,
    val abiSize: Int?,
    val abiAlignment: Int?,
    val isBlittable: Boolean,
)

data class WinRTValueTypeDescriptor(
    val type: WinRTTypeDefinition,
    val isValueType: Boolean,
    val isBlittable: Boolean,
    val abiSize: Int?,
    val abiAlignment: Int?,
    val layout: WinRTTypeLayout,
    val fields: List<WinRTValueTypeFieldDescriptor>,
    val enumUnderlyingType: WinRTIntegralType?,
    val enumMembers: List<WinRTEnumMemberDefinition>,
    val isFlagsEnum: Boolean,
    val mappedType: WinRTMappedTypeDescriptor?,
) {
    val requiresAbiCompanionShape: Boolean
        get() = isValueType && !isBlittable
}

data class WinRTSemanticValueDescriptor(
    val type: WinRTTypeRef,
    val category: WinRTProjectionCategory,
    val isValueType: Boolean,
    val isBlittable: Boolean,
    val isBlittableForArray: Boolean,
    val mappedType: WinRTMappedTypeDescriptor?,
)

data class WinRTGenericAbiDelegateDescriptor(
    val abiDelegateName: String,
    val sourceGenericType: WinRTTypeRef,
    val abiDelegateTypesKey: String,
    val genericArguments: List<WinRTTypeRef>,
    val operationName: String = abiDelegateName.removePrefix("_").substringBeforeLast("_", missingDelimiterValue = abiDelegateName.removePrefix("_")),
    val abiReturnTypeName: String = "Int",
    val abiParameterTypeNames: List<String> = emptyList(),
    val declaration: String = "",
    val typeArrayShape: List<String> = emptyList(),
)

data class WinRTGenericTypeInstantiationDescriptor(
    val type: WinRTTypeRef,
    val definitionType: WinRTTypeDefinition?,
    val instantiationClassName: String,
    val genericArguments: List<WinRTTypeRef>,
    val implementsCcwInterface: Boolean,
)

data class WinRTGenericAbiInventory(
    val genericAbiDelegates: List<WinRTGenericAbiDelegateDescriptor>,
    val genericTypeInstantiations: List<WinRTGenericTypeInstantiationDescriptor>,
    val derivedGenericInterfaces: List<String> = emptyList(),
)

data class WinRTObjectMethodMatchDescriptor(
    val methodName: String,
    val matches: Boolean,
    val returnTypeMatches: Boolean,
)

data class WinRTClassObjectMethodDescriptor(
    val classTypeName: String,
    val objectEquals: WinRTObjectMethodMatchDescriptor?,
    val classEquals: WinRTObjectMethodMatchDescriptor?,
    val objectHashCode: WinRTObjectMethodMatchDescriptor?,
    val hasObjectEqualsMethod: Boolean,
    val hasClassEqualsMethod: Boolean,
    val hasObjectHashCodeMethod: Boolean,
)

data class WinRTGenericInstantiationWriterDescriptor(
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

enum class WinRTProjectedNameKind {
    Projected,
    Abi,
    Ccw,
    NonProjected,
    StaticAbiClass,
}

data class WinRTTypeNameWriterContext(
    val currentNamespace: String,
    val inAbiNamespace: Boolean = false,
    val inAbiImplNamespace: Boolean = false,
    val component: Boolean = false,
)

data class WinRTTypeNameDescriptor(
    val typeName: String,
    val nameKind: WinRTProjectedNameKind,
    val renderedName: String,
    val forceNamespace: Boolean,
    val namespacePrefix: String,
    val writesGlobalPrefix: Boolean,
    val rewritesExclusiveProjectedToCcw: Boolean,
    val genericArityStrippedName: String,
)

data class WinRTEventHelperSubclassDescriptor(
    val eventTypeName: String,
    val projectedEventTypeName: String,
    val abiEventTypeName: String,
    val ownerTypeName: String,
    val sourceClassName: String,
    val genericArgumentTypeNames: List<String>,
    val usesSharedEventHandlerSource: Boolean,
    val interfaceId: io.github.composefluent.winrt.runtime.Guid?,
)

data class WinRTPlatformGuardDescriptor(
    val ownerName: String,
    val platform: String?,
    val checkPlatform: Boolean,
    val platformAttribute: String?,
    val suppressesDuplicatePlatform: Boolean,
)

data class WinRTGenericInstantiationWorklistDescriptor(
    val pending: List<WinRTGenericTypeInstantiationDescriptor>,
    val written: List<WinRTGenericTypeInstantiationDescriptor> = emptyList(),
) {
    fun markWritten(instantiationClassName: String): WinRTGenericInstantiationWorklistDescriptor {
        val moved = pending.firstOrNull { it.instantiationClassName == instantiationClassName } ?: return this
        return copy(
            pending = pending.filterNot { it.instantiationClassName == instantiationClassName },
            written = (written + moved)
                .distinctBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName)
                .sortedBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }

    fun enqueue(discovered: List<WinRTGenericTypeInstantiationDescriptor>): WinRTGenericInstantiationWorklistDescriptor {
        val known = (pending + written).map(WinRTGenericTypeInstantiationDescriptor::instantiationClassName).toSet()
        return copy(
            pending = (pending + discovered.filterNot { it.instantiationClassName in known })
                .distinctBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName)
                .sortedBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }
}

data class WinRTComponentActivatableClassDescriptor(
    val className: String,
    val classType: WinRTTypeDefinition,
    val attributedFactories: List<WinRTAttributedFactoryDescriptor>,
)

data class WinRTGenericScopeParameterDescriptor(
    val parameter: WinRTGenericParameterDefinition,
    val projectedName: String,
    val abiName: String,
)

data class WinRTGenericScopeDescriptor(
    val ownerType: WinRTTypeDefinition,
    val parameters: List<WinRTGenericScopeParameterDescriptor>,
)

data class WinRTGenericSignatureUsageDescriptor(
    val containsProjectedGenericParameter: Boolean,
    val containsAbiGenericParameter: Boolean,
)

data class WinRTExplicitImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String?,
    val declarationName: String?,
    val bodyName: String?,
    val isPrivateBody: Boolean,
)

data class WinRTProjectedMethodSignatureDescriptor(
    val methodName: String,
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
) {
    fun signatureEquals(other: WinRTProjectedMethodSignatureDescriptor): Boolean =
        returnTypeName == other.returnTypeName && parameterTypeNames == other.parameterTypeNames
}

data class WinRTPrivateImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String,
    val interfaceMemberName: String,
    val privateMethodName: String,
    val isImplementedAsPrivateMethod: Boolean,
)

data class WinRTMappedInterfaceImplementationDescriptor(
    val classTypeName: String,
    val interfaceTypeName: String,
    val probeMemberName: String?,
    val isImplementedAsPrivateMappedInterface: Boolean,
)

data class WinRTCustomQueryInterfaceDescriptor(
    val classTypeName: String,
    val hasBaseClass: Boolean,
    val overridableInterfaceNames: List<String>,
    val visibility: String,
    val overridableModifier: String,
    val delegatesToBase: Boolean,
)

data class WinRTProjectionContextSemanticsDescriptor(
    val internalAccessibility: String,
    val enumAccessibility: String,
    val embedded: Boolean,
    val publicExclusiveTo: Boolean,
    val idicExclusiveTo: Boolean,
    val partialFactory: Boolean,
    val partialFactoryFallbackExpression: String,
)

data class WinRTTypeProjectionContextDescriptor(
    val typeName: String,
    val accessibility: String,
    val enumAccessibility: String,
    val exclusiveToAccessibility: String,
    val writesVtablePointer: Boolean,
    val supportsDynamicInterfaceCastable: Boolean,
    val partialFactoryFallbackExpression: String,
)

data class WinRTManualInterfaceDescriptor(
    val typeName: String,
    val manuallyGenerated: Boolean,
    val reason: String? = null,
)

data class WinRTClassMemberMergeDescriptor(
    val classTypeName: String,
    val interfaceDescriptors: List<WinRTClassInterfaceMemberDescriptor>,
    val mergedProperties: List<WinRTMergedPropertyDescriptor>,
)

data class WinRTClassInterfaceMemberDescriptor(
    val interfaceTypeName: String,
    val target: String,
    val isDefaultInterface: Boolean,
    val isOverridableInterface: Boolean,
    val isProtectedInterface: Boolean,
    val isManuallyGeneratedInterface: Boolean,
    val callStaticMethod: Boolean,
    val mappedTypeHasCustomMembers: Boolean,
)

data class WinRTMergedPropertyDescriptor(
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

data class WinRTMethodVtableDescriptor(
    val ownerTypeName: String,
    val methodName: String,
    val methodRowId: Int?,
    val vmethodIndex: Int,
    val vmethodName: String,
    val slotIndex: Int,
)

data class WinRTSignatureWriterDescriptor(
    val methodName: String,
    val escapedMethodName: String,
    val projectionReturnTypeName: String,
    val abiReturnTypeName: String,
    val parameters: List<WinRTSignatureParameterWriterDescriptor>,
    val hasProjectedGenericParameters: Boolean,
    val hasAbiGenericParameters: Boolean,
)

data class WinRTSignatureParameterWriterDescriptor(
    val originalName: String,
    val escapedName: String,
    val category: WinRTMetadataParameterCategory,
    val projectionTypeName: String,
    val abiTypeName: String,
    val modifier: String?,
    val expandsArrayLength: Boolean,
)

data class WinRTEventInvokeDescriptor(
    val eventName: String,
    val delegateTypeName: String,
    val invokeMethodName: String?,
    val returnTypeName: String,
    val parameters: List<WinRTSignatureParameterWriterDescriptor>,
    val outDefaultAssignments: List<String>,
    val sourceTableAddIndex: Int?,
    val sourceTableRemoveIndex: Int?,
    val isStatic: Boolean,
)

data class WinRTAbiMarshalerPlanDescriptor(
    val methodName: String,
    val marshalers: List<WinRTAbiMarshalerSlotDescriptor>,
    val requiresPinnedScope: Boolean,
    val requiresDispose: Boolean,
    val hasNoExceptionAttribute: Boolean,
    val isGenericInstantiationClass: Boolean,
)

data class WinRTAbiMarshalerSlotDescriptor(
    val name: String,
    val typeName: String,
    val category: WinRTMetadataParameterCategory,
    val isReturn: Boolean = false,
    val isPinnable: Boolean,
    val isGeneric: Boolean,
    val requiresFromAbi: Boolean,
    val requiresToAbi: Boolean,
    val requiresDispose: Boolean,
)

data class WinRTFactorySurfaceDescriptor(
    val classTypeName: String,
    val defaultInterfaceName: String?,
    val activationFactoryCacheName: String,
    val staticFactoryCacheNames: List<String>,
    val constructorFactories: List<String>,
    val composableFactories: List<String>,
    val staticMemberTargets: List<String>,
    val gcPressureAmount: Int,
)

data class WinRTCustomMappedMemberOutputDescriptor(
    val interfaceTypeName: String,
    val mappedTypeName: String,
    val memberPlans: List<String>,
    val callMode: String,
    val emitsExplicitMembers: Boolean,
    val emitsPrivateMembers: Boolean,
)

data class WinRTInterfaceMemberSignatureSetDescriptor(
    val interfaceTypeName: String,
    val methodSignatures: List<WinRTProjectedMethodSignatureDescriptor>,
    val propertyNames: List<String>,
    val eventNames: List<String>,
    val newPropertyNames: List<String>,
)

data class WinRTGuidSignatureDescriptor(
    val typeName: String,
    val guidText: String?,
    val guidBytes: List<Int>,
    val signatureFragment: String,
    val parameterizedSignatureFragments: List<String>,
)

data class WinRTVtableWriterDescriptor(
    val typeName: String,
    val methods: List<WinRTMethodVtableDescriptor>,
    val delegateCacheNames: List<String>,
    val usesFunctionPointers: Boolean,
    val genericAbiTypeArrays: List<List<String>>,
)

data class WinRTTypeDeclarationDescriptor(
    val typeName: String,
    val declarationKind: WinRTTypeKind,
    val writesProjectedDeclaration: Boolean,
    val writesAbiDeclaration: Boolean,
    val writesWrapperDeclaration: Boolean,
    val writesImplementationClass: Boolean,
    val writesHelperClass: Boolean,
    val netStandardBranch: Boolean,
)

data class WinRTObjectReferenceSurfaceDescriptor(
    val typeName: String,
    val inheritanceTypeNames: List<String>,
    val objectReferenceNames: List<String>,
    val objectReferencePlans: List<WinRTObjectReferencePlanDescriptor>,
    val baseConstructorDispatchTargets: List<String>,
    val exposedTypeMetadataNames: List<String>,
    val hasRcwFactory: Boolean,
    val hasUnwrappableNativeObject: Boolean,
)

data class WinRTObjectReferencePlanDescriptor(
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

data class WinRTManagedAbiInvokeDescriptor(
    val memberName: String,
    val invokeKind: String,
    val managedLocals: List<String>,
    val conversionOperations: List<String>,
    val hasNoExceptionAttribute: Boolean,
    val requiresHelperMethod: Boolean,
)

data class WinRTGenericAbiClassInitializationDescriptor(
    val typeName: String,
    val requiresRcwFallbackInitialization: Boolean,
    val requiresCcwFallbackInitialization: Boolean,
    val genericMethodDelegateVariables: List<String>,
    val invokeSlotNames: List<String>,
    val genericTypeArrayDependencies: List<String>,
)

data class WinRTRequiredInterfaceAugmentationDescriptor(
    val typeName: String,
    val requiredInterfaceNames: List<String>,
    val explicitForwardMemberNames: List<String>,
    val mappedAugmentationMembers: List<String>,
    val mappedHelperPlans: List<WinRTRequiredMappedHelperPlanDescriptor>,
    val genericAbiParameterArrays: List<List<String>>,
    val implementsCcwInterface: Boolean,
)

data class WinRTRequiredMappedHelperPlanDescriptor(
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

data class WinRTModuleActivationAndAuthoringDescriptor(
    val typeName: String,
    val authoringMetadataTypeName: String?,
    val factoryClassName: String?,
    val factoryMemberNames: List<String>,
    val moduleActivationFactoryEntries: List<String>,
    val baseTypeEntry: String?,
    val metadataTypeEntry: String?,
)

data class WinRTAuxiliaryTableSemanticBoundaryDescriptor(
    val tableName: String,
    val rowCount: Int,
    val modeled: Boolean,
    val projectionAffecting: Boolean,
    val note: String,
)

data class WinRTMetadataParityAuditEntry(
    val referenceArea: String,
    val referenceEntryPoint: String,
    val kotlinOwner: String,
    val closed: Boolean,
)

class WinRTMetadataSemanticHelpers(private val model: WinRTMetadataModel) {
    private val normalizedModel = model.normalized()
    private val typesByQualifiedName = buildTypesByQualifiedName(normalizedModel)
    private val closureResolver = normalizedModel.closureResolver()
    private val typeClassifier = normalizedModel.typeClassifier()
    private val typeSemanticsResolver = normalizedModel.typeSemanticsResolver()

    fun getMappedTypesInNamespace(namespace: String): List<WinRTMappedTypeDescriptor> =
        typeClassifier.mappedTypesInNamespace(namespace)

    fun getMappedType(namespace: String, name: String): WinRTMappedTypeDescriptor? =
        typeClassifier.mappedType(namespace, name)

    fun getMappedType(type: WinRTTypeRef, currentNamespace: String): WinRTMappedTypeDescriptor? =
        typeClassifier.classify(type, currentNamespace).mappedType

    fun getAttribute(type: WinRTTypeDefinition, attributeTypeName: String): WinRTCustomAttributeDefinition? =
        type.customAttributes.firstOrNull { it.typeName == attributeTypeName }

    fun hasAttribute(type: WinRTTypeDefinition, attributeTypeName: String): Boolean =
        getAttribute(type, attributeTypeName) != null

    fun getNumberOfAttributes(type: WinRTTypeDefinition, attributeTypeName: String): Int =
        type.customAttributes.count { it.typeName == attributeTypeName }

    fun getAttributeValue(attribute: WinRTCustomAttributeDefinition, index: Int): WinRTCustomAttributeValue? =
        attribute.fixedArguments.getOrNull(index)

    fun projectedAttributes(type: WinRTTypeDefinition, enablePlatformAttributes: Boolean = true): List<WinRTProjectedAttributeDescriptor> =
        type.projectedAttributes(enablePlatformAttributes)

    fun getContractVersion(type: WinRTTypeDefinition): Long? = type.availability.contractVersion?.version

    fun getVersion(type: WinRTTypeDefinition): Long? = type.availability.version

    fun getContractPlatform(type: WinRTTypeDefinition): String? = type.availability.contractVersion?.platformVersion

    fun methodSignature(method: WinRTMethodDefinition): WinRTMethodSignatureDescriptor =
        WinRTMethodSignatureDescriptor(
            method = method,
            returnType = method.returnType,
            parameters = method.parameters.map { parameter ->
                WinRTMethodSignatureParameter(
                    parameter = parameter,
                    category = parameterCategory(parameter),
                )
            },
        )

    fun parameterCategory(parameter: WinRTParameterDefinition): WinRTMetadataParameterCategory {
        return metadataParameterCategoryFor(parameter)
    }

    fun genericScope(type: WinRTTypeDefinition): WinRTGenericScopeDescriptor {
        val normalized = type.normalized()
        val parameters = if (normalized.genericParameters.isNotEmpty()) {
            normalized.genericParameters
        } else {
            (0 until normalized.genericParameterCount).map { index ->
                WinRTGenericParameterDefinition(name = "T$index", index = index)
            }
        }
        return WinRTGenericScopeDescriptor(
            ownerType = normalized,
            parameters = parameters.map { parameter ->
                val projectedName = parameter.name.ifBlank { "T${parameter.index}" }
                WinRTGenericScopeParameterDescriptor(
                    parameter = parameter,
                    projectedName = projectedName,
                    abiName = "${projectedName}Abi",
                )
            },
        )
    }

    fun genericSignatureUsage(type: WinRTTypeRef): WinRTGenericSignatureUsageDescriptor {
        var contains = false
        fun visit(current: WinRTTypeRef) {
            if (current.kind == WinRTTypeRefKind.GenericTypeParameter || current.kind == WinRTTypeRefKind.MethodTypeParameter) {
                contains = true
            }
            current.elementType?.let(::visit)
            current.typeArguments.forEach(::visit)
        }
        visit(type.normalized())
        return WinRTGenericSignatureUsageDescriptor(
            containsProjectedGenericParameter = contains,
            containsAbiGenericParameter = contains,
        )
    }

    fun explicitImplementations(type: WinRTTypeDefinition): List<WinRTExplicitImplementationDescriptor> =
        type.normalized().methodImplementations.map { implementation ->
            val body = implementation.body.name
                ?.let { bodyName -> type.methods.firstOrNull { it.name == bodyName } }
            WinRTExplicitImplementationDescriptor(
                classTypeName = implementation.classTypeName,
                interfaceTypeName = implementation.declaration.ownerTypeName,
                declarationName = implementation.declaration.name,
                bodyName = implementation.body.name,
                isPrivateBody = body?.visibility == WinRTMethodVisibility.Private,
            )
        }

    fun isRemoveOverload(method: WinRTMethodDefinition): Boolean =
        method.isRemoveOverload || (method.isSpecialName && method.name.startsWith("remove_"))

    fun isNoException(method: WinRTMethodDefinition): Boolean =
        isRemoveOverload(method) || method.isNoException

    fun isNoException(property: WinRTPropertyDefinition): Boolean = property.isNoException

    fun isExclusiveTo(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.Interface && type.isExclusiveTo

    fun getExclusiveToType(type: WinRTTypeDefinition): WinRTTypeDefinition? {
        if (!isExclusiveTo(type)) return null
        val exclusiveTypeName = type.customAttributes
            .firstOrNull { it.typeName == WINDOWS_FOUNDATION_METADATA_EXCLUSIVE_TO_ATTRIBUTE }
            ?.fixedArguments
            ?.mapNotNull(WinRTCustomAttributeValue::stringValue)
            ?.firstOrNull()
        if (exclusiveTypeName != null) {
            resolveType(WinRTTypeRef.fromDisplayName(exclusiveTypeName), type.namespace)?.let { return it }
        }
        return normalizedModel.namespaces
            .flatMap(WinRTNamespace::types)
            .firstOrNull { candidate ->
                candidate.kind == WinRTTypeKind.RuntimeClass &&
                    candidate.implementedInterfaces.any { implemented ->
                        resolveTypeReference(implemented.interfaceType, candidate.namespace, typesByQualifiedName).definitionQualifiedName == type.qualifiedName
                    }
            }
    }

    fun isCrossModuleOverridableExclusiveInterface(type: WinRTTypeDefinition): Boolean {
        if (!isExclusiveTo(type) || type.isProjectionInternal) return false
        val exclusiveType = getExclusiveToType(type) ?: return false
        if (exclusiveType.kind != WinRTTypeKind.RuntimeClass || exclusiveType.isSealedType) return false
        return exclusiveType.implementedInterfaces.any { implemented ->
            implemented.isOverridable &&
                resolveTypeReference(
                    implemented.interfaceType,
                    exclusiveType.namespace,
                    typesByQualifiedName,
                ).definitionQualifiedName == type.qualifiedName
        }
    }

    fun doesAbiInterfaceImplementCcwInterface(type: WinRTTypeDefinition): Boolean =
        doesAbiInterfaceImplementCcwInterface(type, WinRTMetadataProjectionContext(sources = emptyList()))

    fun doesAbiInterfaceImplementCcwInterface(
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext,
    ): Boolean =
        context.component && context.filter.includes(type) && isExclusiveTo(type)

    fun isOverridable(implementation: WinRTInterfaceImplementationDefinition): Boolean =
        implementation.isOverridable

    fun isProjectionInternal(type: WinRTTypeDefinition): Boolean = type.isProjectionInternal

    fun isFlagsEnum(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.Enum && type.customAttributes.any { it.typeName == SYSTEM_FLAGS_ATTRIBUTE }

    fun isValueType(type: WinRTTypeDefinition): Boolean =
        semanticValueDescriptor(WinRTTypeRef.named(type.qualifiedName), type.namespace).isValueType

    fun isTypeBlittable(type: WinRTTypeDefinition): Boolean =
        semanticValueDescriptor(WinRTTypeRef.named(type.qualifiedName), type.namespace).isBlittable

    fun isTypeBlittable(type: WinRTTypeRef, currentNamespace: String, forArray: Boolean = false): Boolean =
        semanticValueDescriptor(type, currentNamespace, forArray).isBlittable

    fun isValueType(type: WinRTTypeRef, currentNamespace: String): Boolean =
        semanticValueDescriptor(type, currentNamespace).isValueType

    fun semanticValueDescriptor(
        type: WinRTTypeRef,
        currentNamespace: String,
        forArray: Boolean = false,
    ): WinRTSemanticValueDescriptor {
        val classification = typeClassifier.classify(type, currentNamespace)
        val resolvedType = classification.definitionType
        val isGenericInstance = classification.type.typeArguments.isNotEmpty()
        val fundamentalType = winRTFundamentalTypeForName(classification.typeName)
        val isValueType = when {
            isGenericInstance -> false
            classification.projectionCategory == WinRTProjectionCategory.Object -> false
            classification.projectionCategory == WinRTProjectionCategory.String -> false
            classification.projectionCategory == WinRTProjectionCategory.Enum -> true
            classification.projectionCategory == WinRTProjectionCategory.Struct ||
                classification.projectionCategory == WinRTProjectionCategory.ApiContract -> {
                val mapped = classification.mappedType
                if (mapped != null) {
                    true
                } else {
                    resolvedType?.fields.orEmpty().all { field -> isValueType(field.type, resolvedType?.namespace ?: currentNamespace) }
                }
            }
            classification.projectionCategory == WinRTProjectionCategory.Fundamental -> fundamentalType?.isWinRTValueType == true
            classification.projectionCategory == WinRTProjectionCategory.Guid ||
                classification.projectionCategory == WinRTProjectionCategory.Type -> true
            else -> false
        }
        val isBlittable = when {
            isGenericInstance -> false
            classification.projectionCategory == WinRTProjectionCategory.Object -> false
            classification.projectionCategory == WinRTProjectionCategory.String -> false
            classification.projectionCategory == WinRTProjectionCategory.Enum -> !forArray
            classification.projectionCategory == WinRTProjectionCategory.Struct ||
                classification.projectionCategory == WinRTProjectionCategory.ApiContract -> {
                val mapped = classification.mappedType
                if (mapped != null) {
                    !mapped.requiresMarshaling
                } else {
                    resolvedType?.fields.orEmpty().all { field -> isTypeBlittable(field.type, resolvedType?.namespace ?: currentNamespace) }
                }
            }
            classification.projectionCategory == WinRTProjectionCategory.Fundamental -> fundamentalType?.isWinRTBlittable == true
            classification.projectionCategory == WinRTProjectionCategory.Guid ||
                classification.projectionCategory == WinRTProjectionCategory.Type -> true
            else -> false
        }
        val isBlittableForArray = if (forArray) {
            isBlittable
        } else {
            semanticValueDescriptor(type, currentNamespace, forArray = true).isBlittable
        }
        return WinRTSemanticValueDescriptor(
            type = classification.type,
            category = classification.projectionCategory,
            isValueType = isValueType,
            isBlittable = isBlittable,
            isBlittableForArray = isBlittableForArray,
            mappedType = classification.mappedType,
        )
    }

    fun valueTypeDescriptor(type: WinRTTypeDefinition): WinRTValueTypeDescriptor {
        val normalizedType = type.normalized()
        val classification = typeClassifier.classify(normalizedType)
        val fields = normalizedType.fields.map { field ->
            val fieldType = typeClassifier.classify(field.type, normalizedType.namespace)
            WinRTValueTypeFieldDescriptor(
                field = field,
                type = fieldType,
                offset = field.offset,
                abiSize = field.abiSize,
                abiAlignment = field.abiAlignment,
                isBlittable = field.isBlittable || fieldType.isBlittable || fieldType.projectionCategory == WinRTProjectionCategory.Fundamental,
            )
        }
        return WinRTValueTypeDescriptor(
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

    fun isApiContractType(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.Struct && type.isApiContract

    fun isAttributeType(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.RuntimeClass && type.isAttributeType

    fun isParameterizedType(type: WinRTTypeDefinition): Boolean =
        type.genericParameterCount > 0 || type.genericParameters.isNotEmpty()

    fun isPType(type: WinRTTypeDefinition): Boolean = isParameterizedType(type)

    fun isStatic(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.RuntimeClass && type.isStaticType

    fun isConstructor(method: WinRTMethodDefinition): Boolean =
        method.isRuntimeSpecialName && method.name == ".ctor"

    fun hasDefaultConstructor(type: WinRTTypeDefinition): Boolean =
        type.kind == WinRTTypeKind.RuntimeClass &&
            type.methods.any { method -> isConstructor(method) && method.parameters.isEmpty() }

    fun isSpecial(method: WinRTMethodDefinition): Boolean =
        method.isSpecialName || method.isRuntimeSpecialName

    fun isStatic(method: WinRTMethodDefinition): Boolean = method.isStatic

    fun getDelegateInvoke(type: WinRTTypeDefinition): WinRTMethodDefinition? =
        type.methods.firstOrNull { method -> method.name == "Invoke" && method.isSpecialName }
            ?: type.methods.firstOrNull { method -> method.name == "Invoke" }

    fun getDefaultInterface(type: WinRTTypeDefinition): WinRTTypeRef? =
        type.defaultInterface
            ?: type.implementedInterfaces.firstOrNull { it.isDefault }?.interfaceType
            ?: if (type.implementedInterfaces.isEmpty()) null else {
                throw IllegalArgumentException("Type '${type.qualifiedName}' does not have a default interface")
            }

    fun getDefaultInterfaceSemantics(type: WinRTTypeDefinition): WinRTTypeSemantics {
        val defaultInterface = getDefaultInterface(type)
            ?: throw IllegalArgumentException("Class does not have a default interface: ${type.qualifiedName}")
        return typeSemanticsResolver.resolve(defaultInterface, type.namespace, type.genericParameters)
    }

    fun getPropertyMethods(property: WinRTPropertyDefinition): WinRTPropertyAccessorDescriptor =
        WinRTPropertyAccessorDescriptor(
            getterMethodName = property.getterMethodName,
            setterMethodName = property.setterMethodName,
            getterMethodRowId = property.getterMethodRowId,
            setterMethodRowId = property.setterMethodRowId,
            isStatic = property.isStatic,
            hasValidAccessors = property.hasValidAccessors,
        )

    fun getEventMethods(event: WinRTEventDefinition): WinRTEventAccessorDescriptor =
        WinRTEventAccessorDescriptor(
            addMethodName = event.addMethodName,
            removeMethodName = event.removeMethodName,
            addMethodRowId = event.addMethodRowId,
            removeMethodRowId = event.removeMethodRowId,
            isStatic = event.isStatic,
            hasValidAccessors = event.hasValidAccessors,
        )

    fun getAttributedTypes(type: WinRTTypeDefinition): List<WinRTAttributedFactoryDescriptor> {
        val factories = linkedMapOf<String, WinRTAttributedFactoryDescriptor>()
        fun put(
            interfaceType: WinRTTypeRef?,
            kind: WinRTAttributedFactoryKind? = null,
            activatable: Boolean = false,
            statics: Boolean = false,
            composable: Boolean = false,
            visible: Boolean = false,
        ) {
            if (interfaceType == null) return
            val resolved = resolveTypeReference(interfaceType, type.namespace, typesByQualifiedName)
            val current = factories[resolved.displayName]
            factories[resolved.displayName] = WinRTAttributedFactoryDescriptor(
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
                    activatable = factory.kind == WinRTAttributedFactoryKind.Activatable,
                    statics = factory.kind == WinRTAttributedFactoryKind.Static,
                    composable = factory.kind == WinRTAttributedFactoryKind.Composable,
                    visible = factory.isVisible,
                )
            }
        } else {
            put(type.activation.activatableFactoryInterface, kind = WinRTAttributedFactoryKind.Activatable, activatable = type.activation.isActivatable)
            type.activation.staticInterfaces.forEach { staticInterface ->
                put(staticInterface, kind = WinRTAttributedFactoryKind.Static, statics = true)
            }
            put(type.activation.composableFactoryInterface, kind = WinRTAttributedFactoryKind.Composable, composable = true)
        }
        return factories.values.toList()
    }

    fun componentActivatableClasses(): List<WinRTComponentActivatableClassDescriptor> =
        normalizedModel.namespaces
            .flatMap(WinRTNamespace::types)
            .filter { type -> type.kind == WinRTTypeKind.RuntimeClass }
            .mapNotNull { type ->
                val factories = getAttributedTypes(type).filter { factory -> factory.activatable || factory.statics }
                if (factories.isEmpty()) {
                    null
                } else {
                    WinRTComponentActivatableClassDescriptor(
                        className = type.qualifiedName,
                        classType = type,
                        attributedFactories = factories,
                    )
                }
            }

    fun getDefaultAndExclusiveInterfaces(type: WinRTTypeDefinition): Pair<WinRTRuntimeClassInterfaceDescriptor?, List<WinRTRuntimeClassInterfaceDescriptor>> {
        val closure = closureResolver.resolveRuntimeClass(type)
        val defaultInterface = closure.instanceInterfaces.firstOrNull { it.isDefault }
        val exclusiveInterfaces = closure.instanceInterfaces.filter { !it.isDefault && it.isExclusiveTo }
        return defaultInterface to exclusiveInterfaces
    }

    fun getFastAbiClassForClass(type: WinRTTypeDefinition): WinRTFastAbiClassDescriptor? {
        if (!type.isFastAbi) return null
        val closure = closureResolver.resolveRuntimeClass(type)
        val defaultInterface = closure.fastAbiInterfaces.firstOrNull { it.isDefault }
        val otherInterfaces = closure.fastAbiInterfaces.filterNot { it.isDefault }
        return WinRTFastAbiClassDescriptor(
            classType = type,
            defaultInterface = defaultInterface,
            otherInterfaces = otherInterfaces,
            interfaceSlots = fastAbiInterfaceSlots(closure),
            propertySlots = fastAbiPropertySlots(closure),
        )
    }

    fun getFastAbiClassForInterface(type: WinRTTypeDefinition): WinRTFastAbiClassDescriptor? {
        if (type.isExclusiveTo) {
            val exclusiveToClass = getExclusiveToType(type)
            if (exclusiveToClass?.isFastAbi == true) {
                return getFastAbiClassForClass(exclusiveToClass)
            }
        }
        val owner = normalizedModel.namespaces
            .flatMap(WinRTNamespace::types)
            .firstOrNull { candidate ->
                candidate.kind == WinRTTypeKind.RuntimeClass &&
                    candidate.isFastAbi &&
                    candidate.implementedInterfaces.any { implemented ->
                        resolveTypeReference(implemented.interfaceType, candidate.namespace, typesByQualifiedName).definitionQualifiedName == type.qualifiedName
                    }
            }
            ?: return null
        return getFastAbiClassForClass(owner)
    }

    fun getGcPressureAmount(type: WinRTTypeDefinition): Int = type.gcPressureAmount

    fun isObjectEqualsMethod(method: WinRTMethodDefinition): WinRTObjectMethodMatchDescriptor {
        val hasExpectedNameAndParams = method.name == "Equals" &&
            method.parameters.size == 1 &&
            typeClassifier.classify(method.parameters.single().type, "").projectionCategory == WinRTProjectionCategory.Object
        val returnTypeMatches = method.returnType.matchesFundamentalType(WinRTFundamentalType.Boolean)
        return WinRTObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && returnTypeMatches,
            returnTypeMatches = returnTypeMatches,
        )
    }

    fun isClassEqualsMethod(
        method: WinRTMethodDefinition,
        classType: WinRTTypeDefinition,
    ): WinRTObjectMethodMatchDescriptor {
        val parameterType = method.parameters.singleOrNull()?.type?.normalized()?.typeName
        val hasExpectedNameAndParams = method.name == "Equals" && parameterType == classType.qualifiedName
        val returnTypeMatches = method.returnType.matchesFundamentalType(WinRTFundamentalType.Boolean)
        return WinRTObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && returnTypeMatches,
            returnTypeMatches = returnTypeMatches,
        )
    }

    fun isObjectHashCodeMethod(method: WinRTMethodDefinition): WinRTObjectMethodMatchDescriptor {
        val hasExpectedNameAndParams = method.name == "GetHashCode" && method.parameters.isEmpty()
        val returnTypeMatches = method.returnType.matchesFundamentalType(WinRTFundamentalType.Int32)
        return WinRTObjectMethodMatchDescriptor(
            methodName = method.name,
            matches = hasExpectedNameAndParams && returnTypeMatches,
            returnTypeMatches = returnTypeMatches,
        )
    }

    fun hasObjectEqualsMethod(type: WinRTTypeDefinition): Boolean =
        type.methods.any { method -> isObjectEqualsMethod(method).matches }

    fun hasClassEqualsMethod(type: WinRTTypeDefinition): Boolean =
        type.methods.any { method -> isClassEqualsMethod(method, type).matches }

    fun hasObjectHashCodeMethod(type: WinRTTypeDefinition): Boolean =
        type.methods.any { method -> isObjectHashCodeMethod(method).matches }

    fun classObjectMethodDescriptor(type: WinRTTypeDefinition): WinRTClassObjectMethodDescriptor =
        WinRTClassObjectMethodDescriptor(
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

    fun genericAbiInventory(): WinRTGenericAbiInventory =
        genericAbiInventory(WinRTMetadataProjectionContext(sources = emptyList()))

    fun genericAbiInventory(context: WinRTMetadataProjectionContext): WinRTGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        normalizedModel.namespaces.flatMap(WinRTNamespace::types).forEach { type ->
            collector.addGenericTypeReferencesInType(type)
        }
        return collector.toInventory(context)
    }

    fun collectGenericAbiInventory(type: WinRTTypeDefinition): WinRTGenericAbiInventory =
        collectGenericAbiInventory(type, WinRTMetadataProjectionContext(sources = emptyList()))

    fun collectGenericAbiInventory(
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext,
    ): WinRTGenericAbiInventory {
        val collector = GenericAbiInventoryCollector()
        collector.addGenericTypeReferencesInType(type.normalized())
        return collector.toInventory(context)
    }

    fun hasDerivedGenericInterface(type: WinRTTypeDefinition): Boolean {
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
        type: WinRTTypeRef,
        genericTypeArguments: List<WinRTTypeRef>,
        methodTypeArguments: List<WinRTTypeRef> = emptyList(),
    ): WinRTTypeRef =
        type.substituteTypeParameters(genericTypeArguments, methodTypeArguments).normalized()

    fun genericInstantiationWorklist(
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    ): WinRTGenericInstantiationWorklistDescriptor {
        val discovered = linkedMapOf<String, WinRTGenericTypeInstantiationDescriptor>()
        fun enqueue(instantiations: List<WinRTGenericTypeInstantiationDescriptor>) {
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

        return WinRTGenericInstantiationWorklistDescriptor(
            pending = discovered.values.sortedBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName),
        )
    }

    fun genericInstantiationWriterDescriptors(
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    ): List<WinRTGenericInstantiationWriterDescriptor> =
        genericInstantiationWorklist(context).pending.map(::genericInstantiationWriterDescriptor)

    fun genericInstantiationWriterDescriptor(
        instantiation: WinRTGenericTypeInstantiationDescriptor,
    ): WinRTGenericInstantiationWriterDescriptor {
        val definition = instantiation.definitionType
        val isDelegate = definition?.kind == WinRTTypeKind.Delegate
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
                val getter = property.getterMethod(methods)
                val setter = property.setterMethod(methods)
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
            instantiation.genericArguments.forEach { argument ->
                addGenericArgumentInstantiationDependencies(argument)
            }
            definition?.implementedInterfaces.orEmpty().forEach { implemented ->
                add(implemented.interfaceType.substituteTypeParameters(instantiation.genericArguments).normalized().typeName)
            }
            definition?.events.orEmpty().forEach { event ->
                add(event.delegateType.substituteTypeParameters(instantiation.genericArguments).normalized().typeName)
            }
        }
        return WinRTGenericInstantiationWriterDescriptor(
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

    private fun MutableList<String>.addGenericArgumentInstantiationDependencies(type: WinRTTypeRef) {
        val normalized = type.normalized()
        when (normalized.kind) {
            WinRTTypeRefKind.Named -> {
                normalized.typeArguments.forEach { argument ->
                    addGenericArgumentInstantiationDependencies(argument)
                }
                if (normalized.typeArguments.isNotEmpty()) {
                    add(normalized.typeName)
                }
            }
            WinRTTypeRefKind.Array ->
                addGenericArgumentInstantiationDependencies(normalized.elementType ?: WinRTTypeRef.unknown())
            WinRTTypeRefKind.GenericTypeParameter,
            WinRTTypeRefKind.MethodTypeParameter,
            WinRTTypeRefKind.Unknown,
            -> Unit
        }
    }

    private fun WinRTPropertyDefinition.getterMethod(methods: List<WinRTMethodDefinition>): WinRTMethodDefinition? =
        getterMethodRowId?.let { rowId -> methods.firstOrNull { it.methodRowId == rowId } }
            ?: getterMethodName?.let { name -> methods.firstOrNull { it.name == name } }

    private fun WinRTPropertyDefinition.setterMethod(methods: List<WinRTMethodDefinition>): WinRTMethodDefinition? =
        setterMethodRowId?.let { rowId -> methods.firstOrNull { it.methodRowId == rowId } }
            ?: setterMethodName?.let { name -> methods.firstOrNull { it.name == name } }

    private fun genericTypeInstantiationDescriptorForDependency(typeName: String): WinRTGenericTypeInstantiationDescriptor? {
        val type = WinRTTypeRef.fromDisplayName(typeName).normalized()
        if (type.typeArguments.isEmpty()) return null
        val currentNamespace = type.qualifiedName?.substringBeforeLast('.', "") ?: ""
        val resolved = resolveTypeReference(type, currentNamespace, typesByQualifiedName)
        val sourceType = resolved.type
        return WinRTGenericTypeInstantiationDescriptor(
            type = sourceType,
            definitionType = resolved.definitionType,
            instantiationClassName = escapeTypeNameForIdentifier(sourceType.typeName),
            genericArguments = sourceType.typeArguments,
            implementsCcwInterface = false,
        )
    }

    fun projectedMethodSignature(method: WinRTMethodDefinition): WinRTProjectedMethodSignatureDescriptor =
        WinRTProjectedMethodSignatureDescriptor(
            methodName = method.name,
            returnTypeName = method.returnType.normalized().typeName,
            parameterTypeNames = method.parameters.map { parameter -> parameter.type.normalized().typeName },
        )

    fun methodSignatureEqual(first: WinRTMethodDefinition, second: WinRTMethodDefinition): Boolean =
        projectedMethodSignature(first).signatureEquals(projectedMethodSignature(second))

    fun isImplementedAsPrivateMethod(
        classType: WinRTTypeDefinition,
        interfaceType: WinRTTypeDefinition,
        interfaceMethod: WinRTMethodDefinition,
    ): WinRTPrivateImplementationDescriptor {
        val privateMethodName = "${interfaceType.qualifiedName}.${interfaceMethod.name}"
        val privateMethod = classType.methods.firstOrNull { method ->
            method.visibility == WinRTMethodVisibility.Private &&
                method.name == privateMethodName &&
                methodSignatureEqual(method, interfaceMethod)
        }
        return WinRTPrivateImplementationDescriptor(
            classTypeName = classType.qualifiedName,
            interfaceTypeName = interfaceType.qualifiedName,
            interfaceMemberName = interfaceMethod.name,
            privateMethodName = privateMethodName,
            isImplementedAsPrivateMethod = privateMethod != null,
        )
    }

    fun isImplementedAsPrivateMappedInterface(
        classType: WinRTTypeDefinition,
        interfaceType: WinRTTypeDefinition,
    ): WinRTMappedInterfaceImplementationDescriptor {
        val probeMethod = interfaceType.methods.firstOrNull()
            ?: interfaceType.properties.firstOrNull()?.getterMethodName?.let { getter -> interfaceType.methods.firstOrNull { it.name == getter } }
            ?: interfaceType.events.firstOrNull()?.addMethodName?.let { add -> interfaceType.methods.firstOrNull { it.name == add } }
        val private = probeMethod?.let { isImplementedAsPrivateMethod(classType, interfaceType, it).isImplementedAsPrivateMethod } ?: false
        return WinRTMappedInterfaceImplementationDescriptor(
            classTypeName = classType.qualifiedName,
            interfaceTypeName = interfaceType.qualifiedName,
            probeMemberName = probeMethod?.name,
            isImplementedAsPrivateMappedInterface = private,
        )
    }

    fun customQueryInterfaceDescriptor(type: WinRTTypeDefinition): WinRTCustomQueryInterfaceDescriptor {
        val hasBaseClass = type.baseTypeName?.let { !isWinRTObjectTypeName(it) } ?: false
        val overridableInterfaces = type.implementedInterfaces
            .filter(WinRTInterfaceImplementationDefinition::isOverridable)
            .map { implemented -> resolveTypeReference(implemented.interfaceType, type.namespace, typesByQualifiedName).displayName }
            .sorted()
        return WinRTCustomQueryInterfaceDescriptor(
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

    fun projectionContextSemantics(context: WinRTMetadataProjectionContext): WinRTProjectionContextSemanticsDescriptor =
        WinRTProjectionContextSemanticsDescriptor(
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
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext,
    ): WinRTTypeProjectionContextDescriptor {
        val contextSemantics = projectionContextSemantics(context)
        val exclusiveTo = isExclusiveTo(type)
        val projectionInternal = isProjectionInternal(type)
        val crossModuleOverridableExclusive = isCrossModuleOverridableExclusiveInterface(type)
        val typeAccessibility = when {
            type.kind == WinRTTypeKind.Enum -> contextSemantics.enumAccessibility
            projectionInternal || context.internal || context.embedded -> "internal"
            exclusiveTo && !context.publicExclusiveTo && !crossModuleOverridableExclusive -> "internal"
            else -> "public"
        }
        return WinRTTypeProjectionContextDescriptor(
            typeName = type.qualifiedName,
            accessibility = typeAccessibility,
            enumAccessibility = contextSemantics.enumAccessibility,
            exclusiveToAccessibility = if (exclusiveTo && !context.publicExclusiveTo && !crossModuleOverridableExclusive) {
                "internal"
            } else {
                typeAccessibility
            },
            writesVtablePointer = !exclusiveTo || context.publicExclusiveTo || crossModuleOverridableExclusive,
            supportsDynamicInterfaceCastable = !exclusiveTo || context.idicExclusiveTo,
            partialFactoryFallbackExpression = contextSemantics.partialFactoryFallbackExpression,
        )
    }

    fun isManuallyGeneratedInterface(type: WinRTTypeDefinition): WinRTManualInterfaceDescriptor {
        val manuallyGenerated = type.namespace == "Microsoft.UI.Xaml.Interop" &&
            (type.name == "IBindableVector" || type.name == "IBindableIterable")
        return WinRTManualInterfaceDescriptor(
            typeName = type.qualifiedName,
            manuallyGenerated = manuallyGenerated,
            reason = if (manuallyGenerated) "The reference projection manually generates Microsoft.UI.Xaml.Interop bindable collection interfaces." else null,
        )
    }

    fun classMemberMergeDescriptor(
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
        wrapperType: Boolean = false,
        interfaceImplType: Boolean = false,
    ): WinRTClassMemberMergeDescriptor {
        val interfaceDescriptors = mutableListOf<WinRTClassInterfaceMemberDescriptor>()
        val properties = linkedMapOf<String, MutableMergedProperty>()
        val writtenInterfaces = linkedSetOf<String>()

        fun writeClassInterface(
            interfaceType: WinRTTypeDefinition,
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
            val callStaticMethod = context.target != WinRTMetadataTarget.NetStandard20 && !wrapperType && !manual
            val mapped = getMappedType(interfaceType.namespace, interfaceType.name)
            interfaceDescriptors += WinRTClassInterfaceMemberDescriptor(
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

        if (interfaceImplType && type.kind == WinRTTypeKind.Interface) {
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
        return WinRTClassMemberMergeDescriptor(
            classTypeName = type.qualifiedName,
            interfaceDescriptors = interfaceDescriptors.sortedBy(WinRTClassInterfaceMemberDescriptor::interfaceTypeName),
            mergedProperties = properties.values
                .map(MutableMergedProperty::toDescriptor)
                .sortedBy(WinRTMergedPropertyDescriptor::propertyName),
        )
    }

    fun typeNameDescriptor(
        type: WinRTTypeDefinition,
        nameKind: WinRTProjectedNameKind = WinRTProjectedNameKind.Projected,
        context: WinRTTypeNameWriterContext = WinRTTypeNameWriterContext(type.namespace),
        forceNamespace: Boolean = false,
    ): WinRTTypeNameDescriptor {
        val authoredType = context.component
        val rewritesExclusive = authoredType && isExclusiveTo(type) && nameKind == WinRTProjectedNameKind.Projected
        val effectiveKind = if (rewritesExclusive) WinRTProjectedNameKind.Ccw else nameKind
        val strippedName = stripGenericArity(type.name)
        val namespacePrefix = if (context.component) "ABI.Impl." else ""
        val writesGlobalPrefix = forceNamespace || type.namespace != context.currentNamespace ||
            (effectiveKind == WinRTProjectedNameKind.Projected && (context.inAbiNamespace || context.inAbiImplNamespace)) ||
            (effectiveKind == WinRTProjectedNameKind.Abi && !context.inAbiNamespace) ||
            (effectiveKind == WinRTProjectedNameKind.Ccw && authoredType && !context.inAbiImplNamespace) ||
            (effectiveKind == WinRTProjectedNameKind.Ccw && !authoredType && (context.inAbiNamespace || context.inAbiImplNamespace))
        val renderedNamespace = if (writesGlobalPrefix) {
            when (effectiveKind) {
                WinRTProjectedNameKind.Abi,
                WinRTProjectedNameKind.StaticAbiClass,
                -> "ABI.${type.namespace}"
                WinRTProjectedNameKind.Ccw -> if (authoredType) "ABI.Impl.${type.namespace}" else type.namespace
                else -> type.namespace
            }
        } else {
            ""
        }
        val baseName = when (effectiveKind) {
            WinRTProjectedNameKind.Abi -> "${strippedName}Abi"
            WinRTProjectedNameKind.Ccw -> "${strippedName}Ccw"
            WinRTProjectedNameKind.StaticAbiClass -> "${strippedName}Methods"
            else -> strippedName
        }
        val typeParams = type.genericParameters
            .sortedBy(WinRTGenericParameterDefinition::index)
            .takeIf(List<*>::isNotEmpty)
            ?.joinToString(", ", prefix = "<", postfix = ">") { it.name }
            ?: if (type.genericParameterCount > 0) {
                (0 until type.genericParameterCount).joinToString(", ", prefix = "<", postfix = ">") { "T$it" }
            } else {
                ""
            }
        return WinRTTypeNameDescriptor(
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

    fun eventHelperSubclassDescriptors(type: WinRTTypeDefinition): List<WinRTEventHelperSubclassDescriptor> =
        type.events.map { event ->
            val eventType = event.delegateType.normalized()
            val eventHandlerDescriptor =
                typeClassifier.classify(eventType, type.namespace).specialType as? WinRTEventHandlerTypeDescriptor
            val usesSharedEventHandlerSource =
                eventHandlerDescriptor?.kind == WinRTEventHandlerKind.EventHandler &&
                    eventHandlerDescriptor.typeArguments.size == 1
            WinRTEventHelperSubclassDescriptor(
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
        availability: WinRTAvailabilityMetadata,
        inheritedPlatform: String? = null,
    ): WinRTPlatformGuardDescriptor {
        val platform = availability.contractVersion?.platformVersion
        return WinRTPlatformGuardDescriptor(
            ownerName = ownerName,
            platform = platform,
            checkPlatform = platform != null,
            platformAttribute = platform?.let { "[SupportedOSPlatform(\"windows$it\")]" },
            suppressesDuplicatePlatform = platform != null && platform == inheritedPlatform,
        )
    }

    fun methodVtableDescriptors(type: WinRTTypeDefinition): List<WinRTMethodVtableDescriptor> {
        val normalized = type.normalized()
        return normalized.methods.map { method -> methodVtableDescriptor(normalized, method) }
    }

    fun methodVtableDescriptor(type: WinRTTypeDefinition, method: WinRTMethodDefinition): WinRTMethodVtableDescriptor {
        val normalizedType = type.normalized()
        val methodOrder = methodOrderByName(normalizedType)
        val abiOrder = abiMemberOrderByRowId(normalizedType)
        val vmethodIndex = method.methodRowId?.let(abiOrder::get)
            ?: methodOrder[method.name]
            ?: normalizedType.methods.indexOfFirst { it.signatureKey() == method.signatureKey() }.coerceAtLeast(0)
        return WinRTMethodVtableDescriptor(
            ownerTypeName = normalizedType.qualifiedName,
            methodName = method.name,
            methodRowId = method.methodRowId,
            vmethodIndex = vmethodIndex,
            vmethodName = "${method.name}_$vmethodIndex",
            slotIndex = INSPECTABLE_METHOD_COUNT + vmethodIndex,
        )
    }

    fun signatureWriterDescriptor(method: WinRTMethodDefinition): WinRTSignatureWriterDescriptor {
        val signature = methodSignature(method)
        val parameters = signature.parameters.map { parameter ->
            signatureParameterWriterDescriptor(parameter.parameter, parameter.category)
        }
        val usage = genericSignatureUsage(method.returnType)
        val parameterHasGeneric = method.parameters.any { genericSignatureUsage(it.type).containsProjectedGenericParameter }
        return WinRTSignatureWriterDescriptor(
            methodName = method.name,
            escapedMethodName = escapeIdentifier(method.name),
            projectionReturnTypeName = method.returnType.normalized().typeName,
            abiReturnTypeName = renderAbiTypeName(method.returnType),
            parameters = parameters,
            hasProjectedGenericParameters = usage.containsProjectedGenericParameter || parameterHasGeneric,
            hasAbiGenericParameters = usage.containsAbiGenericParameter || parameterHasGeneric,
        )
    }

    fun eventInvokeDescriptor(type: WinRTTypeDefinition, event: WinRTEventDefinition): WinRTEventInvokeDescriptor {
        val delegateType = resolveType(event.delegateType, type.namespace)
        val invoke = delegateType?.let(::getDelegateInvoke)
        val invokeSignature = invoke?.let(::signatureWriterDescriptor)
        return WinRTEventInvokeDescriptor(
            eventName = event.name,
            delegateTypeName = event.delegateType.normalized().typeName,
            invokeMethodName = invoke?.name,
            returnTypeName = invoke?.returnType?.normalized()?.typeName
                ?.let { returnTypeName -> if (isWinRTVoidTypeName(returnTypeName)) "Unit" else returnTypeName }
                ?: "Unit",
            parameters = invokeSignature?.parameters.orEmpty(),
            outDefaultAssignments = invoke?.parameters.orEmpty()
                .filter { parameter -> parameterCategory(parameter) == WinRTMetadataParameterCategory.Out }
                .map { parameter -> "${escapeIdentifier(parameter.name)} = default" },
            sourceTableAddIndex = event.addMethodRowId,
            sourceTableRemoveIndex = event.removeMethodRowId,
            isStatic = event.isStatic,
        )
    }

    fun abiMarshalerPlanDescriptor(
        method: WinRTMethodDefinition,
        isGenericInstantiationClass: Boolean = false,
    ): WinRTAbiMarshalerPlanDescriptor {
        val slots = mutableListOf<WinRTAbiMarshalerSlotDescriptor>()
        if (!isWinRTVoidTypeName(method.returnType.normalized().typeName)) {
            slots += abiMarshalerSlot("__return_value__", method.returnType, WinRTMetadataParameterCategory.Out, isReturn = true)
        }
        method.parameters.forEach { parameter ->
            slots += abiMarshalerSlot(parameter.name, parameter.type, parameterCategory(parameter), isReturn = false)
        }
        return WinRTAbiMarshalerPlanDescriptor(
            methodName = method.name,
            marshalers = slots,
            requiresPinnedScope = slots.any(WinRTAbiMarshalerSlotDescriptor::isPinnable),
            requiresDispose = slots.any(WinRTAbiMarshalerSlotDescriptor::requiresDispose),
            hasNoExceptionAttribute = isNoException(method),
            isGenericInstantiationClass = isGenericInstantiationClass,
        )
    }

    fun factorySurfaceDescriptor(type: WinRTTypeDefinition): WinRTFactorySurfaceDescriptor {
        val attributed = getAttributedTypes(type)
        val staticMemberTargets = (
            attributed.filter(WinRTAttributedFactoryDescriptor::statics).map(WinRTAttributedFactoryDescriptor::interfaceName) +
                type.activation.staticInterfaceNames
            ).distinct().sorted()
        return WinRTFactorySurfaceDescriptor(
            classTypeName = type.qualifiedName,
            defaultInterfaceName = getDefaultInterface(type)?.normalized()?.typeName,
            activationFactoryCacheName = "${type.name.substringBefore('`')}ActivationFactory",
            staticFactoryCacheNames = staticMemberTargets.map { cacheNameFor(it) },
            constructorFactories = attributed.filter(WinRTAttributedFactoryDescriptor::activatable).map(WinRTAttributedFactoryDescriptor::interfaceName),
            composableFactories = (
                attributed.filter(WinRTAttributedFactoryDescriptor::composable).map(WinRTAttributedFactoryDescriptor::interfaceName) +
                    listOfNotNull(type.activation.composableFactoryInterfaceName)
                ).distinct().sorted(),
            staticMemberTargets = staticMemberTargets,
            gcPressureAmount = getGcPressureAmount(type),
        )
    }

    fun customMappedMemberOutputDescriptor(
        interfaceType: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
        isPrivate: Boolean = false,
        callStaticAbiMethods: Boolean = context.target != WinRTMetadataTarget.NetStandard20,
    ): WinRTCustomMappedMemberOutputDescriptor? {
        val mapping = getMappedType(interfaceType.namespace, interfaceType.name) ?: return null
        val members = buildList {
            interfaceType.methods.filterNot(::isSpecial).forEach { add(it.name) }
            interfaceType.properties.forEach { add(it.name) }
            interfaceType.events.forEach { add(it.name) }
            if (mapping.hasCustomMembersOutput) add((mapping.mappedName ?: mapping.abiName).substringAfterLast('.'))
        }.distinct().sorted()
        return WinRTCustomMappedMemberOutputDescriptor(
            interfaceTypeName = interfaceType.qualifiedName,
            mappedTypeName = mapping.mappedQualifiedName ?: mapping.mappedName.orEmpty(),
            memberPlans = members,
            callMode = if (callStaticAbiMethods) "static-abi" else "idic",
            emitsExplicitMembers = mapping.hasCustomMembersOutput,
            emitsPrivateMembers = isPrivate,
        )
    }

    fun interfaceMemberSignatureSetDescriptor(type: WinRTTypeDefinition): WinRTInterfaceMemberSignatureSetDescriptor =
        WinRTInterfaceMemberSignatureSetDescriptor(
            interfaceTypeName = type.qualifiedName,
            methodSignatures = type.methods.map(::projectedMethodSignature),
            propertyNames = type.properties.map(WinRTPropertyDefinition::name).sorted(),
            eventNames = type.events.map(WinRTEventDefinition::name).sorted(),
            newPropertyNames = type.properties
                .filter { property -> !property.hasGetterAccessor() && property.hasSetterAccessor() }
                .map(WinRTPropertyDefinition::name)
                .sorted(),
        )

    private fun WinRTPropertyDefinition.hasGetterAccessor(): Boolean =
        getterMethodName != null || getterMethodRowId != null

    private fun WinRTPropertyDefinition.hasSetterAccessor(): Boolean =
        setterMethodName != null || setterMethodRowId != null

    fun guidSignatureDescriptor(type: WinRTTypeDefinition): WinRTGuidSignatureDescriptor {
        val guidText = type.iid?.toString()
        return WinRTGuidSignatureDescriptor(
            typeName = type.qualifiedName,
            guidText = guidText,
            guidBytes = guidText?.let(::guidTextToBytes).orEmpty(),
            signatureFragment = guidSignatureFragment(type),
            parameterizedSignatureFragments = type.genericParameters.map { parameter -> parameter.name },
        )
    }

    fun parameterizedGuidSignatureFragment(
        type: WinRTTypeRef,
        currentNamespace: String,
    ): String =
        guidSignatureFragmentForTypeRef(type.normalized(), currentNamespace)

    fun vtableWriterDescriptor(
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    ): WinRTVtableWriterDescriptor =
        WinRTVtableWriterDescriptor(
            typeName = type.qualifiedName,
            methods = methodVtableDescriptors(type),
            delegateCacheNames = type.methods.map { method -> "_${methodVtableDescriptor(type, method).vmethodName}" },
            usesFunctionPointers = context.target != WinRTMetadataTarget.NetStandard20 && type.genericParameterCount == 0,
            genericAbiTypeArrays = type.methods.mapNotNull { method ->
                val genericTypes = getGenericAbiTypes(method)
                genericTypes.takeIf(List<*>::isNotEmpty)
            },
        )

    fun typeDeclarationDescriptor(
        type: WinRTTypeDefinition,
        context: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    ): WinRTTypeDeclarationDescriptor =
        WinRTTypeDeclarationDescriptor(
            typeName = type.qualifiedName,
            declarationKind = type.kind,
            writesProjectedDeclaration = type.kind != WinRTTypeKind.Unknown,
            writesAbiDeclaration = type.kind in setOf(WinRTTypeKind.Interface, WinRTTypeKind.RuntimeClass, WinRTTypeKind.Delegate, WinRTTypeKind.Struct),
            writesWrapperDeclaration = type.kind == WinRTTypeKind.RuntimeClass,
            writesImplementationClass = type.kind == WinRTTypeKind.Interface && hasDerivedGenericInterface(type),
            writesHelperClass = type.kind in setOf(WinRTTypeKind.Interface, WinRTTypeKind.RuntimeClass, WinRTTypeKind.Delegate),
            netStandardBranch = context.target == WinRTMetadataTarget.NetStandard20,
        )

    fun objectReferenceSurfaceDescriptor(type: WinRTTypeDefinition): WinRTObjectReferenceSurfaceDescriptor {
        val closure = if (type.kind == WinRTTypeKind.RuntimeClass) closureResolver.resolveRuntimeClass(type) else null
        val instanceInterfaces = closure?.instanceInterfaces.orEmpty()
        val interfaces = instanceInterfaces.map(WinRTRuntimeClassInterfaceDescriptor::interfaceName)
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
            WinRTObjectReferencePlanDescriptor(
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
        return WinRTObjectReferenceSurfaceDescriptor(
            typeName = type.qualifiedName,
            inheritanceTypeNames = listOfNotNull(type.baseTypeName) + interfaces,
            objectReferenceNames = objectReferencePlans
                .filter { plan -> plan.skippedReason == null }
                .map(WinRTObjectReferencePlanDescriptor::cacheName),
            objectReferencePlans = objectReferencePlans,
            baseConstructorDispatchTargets = listOfNotNull(type.baseTypeName),
            exposedTypeMetadataNames = listOf(type.qualifiedName) +
                objectReferencePlans
                    .filter { plan -> plan.skippedReason == null }
                    .map(WinRTObjectReferencePlanDescriptor::interfaceName),
            hasRcwFactory = type.kind == WinRTTypeKind.RuntimeClass,
            hasUnwrappableNativeObject = type.kind in setOf(WinRTTypeKind.RuntimeClass, WinRTTypeKind.Delegate),
        )
    }

    private fun mappedObjectReferenceSkipReason(type: WinRTTypeDefinition): String? {
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

    fun managedAbiInvokeDescriptor(memberName: String, method: WinRTMethodDefinition, invokeKind: String): WinRTManagedAbiInvokeDescriptor {
        val plan = abiMarshalerPlanDescriptor(method)
        return WinRTManagedAbiInvokeDescriptor(
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
            requiresHelperMethod = plan.marshalers.any { it.isGeneric || it.category != WinRTMetadataParameterCategory.In },
        )
    }

    fun genericAbiClassInitializationDescriptor(type: WinRTTypeDefinition): WinRTGenericAbiClassInitializationDescriptor =
        WinRTGenericAbiClassInitializationDescriptor(
            typeName = type.qualifiedName,
            requiresRcwFallbackInitialization = type.genericParameterCount > 0 || hasDerivedGenericInterface(type),
            requiresCcwFallbackInitialization = doesAbiInterfaceImplementCcwInterface(type),
            genericMethodDelegateVariables = type.methods.filter { method ->
                signatureWriterDescriptor(method).hasProjectedGenericParameters
            }.map { method -> "_${method.name}Delegate" }.sorted(),
            invokeSlotNames = methodVtableDescriptors(type).map(WinRTMethodVtableDescriptor::vmethodName),
            genericTypeArrayDependencies = type.methods.flatMap(::getGenericAbiTypes).distinct().sorted(),
        )

    fun requiredInterfaceAugmentationDescriptor(type: WinRTTypeDefinition): WinRTRequiredInterfaceAugmentationDescriptor {
        val required = collectRequiredInterfaceClosure(type)
        val mappedMembers = required.mapNotNull { name ->
            val ref = WinRTTypeRef.fromDisplayName(name)
            val qualifiedName = ref.normalized().qualifiedName.orEmpty()
            getMappedType(qualifiedName.substringBeforeLast('.', ""), qualifiedName.substringAfterLast('.'))?.mappedName
        }.sorted()
        return WinRTRequiredInterfaceAugmentationDescriptor(
            typeName = type.qualifiedName,
            requiredInterfaceNames = required,
            explicitForwardMemberNames = explicitImplementations(type).mapNotNull(WinRTExplicitImplementationDescriptor::declarationName).sorted(),
            mappedAugmentationMembers = mappedMembers,
            mappedHelperPlans = required.mapNotNull(::requiredMappedHelperPlan),
            genericAbiParameterArrays = type.methods.mapNotNull { method ->
                getGenericAbiTypes(method).takeIf(List<*>::isNotEmpty)
            },
            implementsCcwInterface = doesAbiInterfaceImplementCcwInterface(type),
        )
    }

    private fun collectRequiredInterfaceClosure(type: WinRTTypeDefinition): List<String> {
        val required = linkedSetOf<String>()
        fun visit(interfaceRef: WinRTTypeRef, currentNamespace: String) {
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

    private fun requiredMappedHelperPlan(interfaceName: String): WinRTRequiredMappedHelperPlanDescriptor? {
        val ref = WinRTTypeRef.fromDisplayName(interfaceName).normalized()
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
        return WinRTRequiredMappedHelperPlanDescriptor(
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

    fun moduleActivationAndAuthoringDescriptor(type: WinRTTypeDefinition): WinRTModuleActivationAndAuthoringDescriptor {
        val factory = factorySurfaceDescriptor(type)
        return WinRTModuleActivationAndAuthoringDescriptor(
            typeName = type.qualifiedName,
            authoringMetadataTypeName = type.qualifiedName.takeIf { type.kind == WinRTTypeKind.RuntimeClass },
            factoryClassName = "${type.name.substringBefore('`')}ActivationFactory".takeIf { factory.constructorFactories.isNotEmpty() || factory.staticMemberTargets.isNotEmpty() },
            factoryMemberNames = factory.constructorFactories + factory.staticMemberTargets + factory.composableFactories,
            moduleActivationFactoryEntries = listOf(type.qualifiedName).filter { factory.constructorFactories.isNotEmpty() || factory.staticMemberTargets.isNotEmpty() },
            baseTypeEntry = type.baseTypeName?.let { "${type.qualifiedName} -> $it" },
            metadataTypeEntry = type.qualifiedName.takeIf { type.kind != WinRTTypeKind.Unknown },
        )
    }

    fun resolveType(type: WinRTTypeRef, currentNamespace: String): WinRTTypeDefinition? {
        val normalized = type.normalized()
        val qualifiedName = normalized.qualifiedName ?: return null
        return typesByQualifiedName[qualifiedName]
            ?: if ('.' !in qualifiedName) typesByQualifiedName["$currentNamespace.$qualifiedName"] else null
    }

    fun auxiliaryTableSemanticBoundaries(inventory: WinRTMetadataAuxiliaryTableInventory): List<WinRTAuxiliaryTableSemanticBoundaryDescriptor> =
        inventory.tables
            .groupBy(WinRTMetadataAuxiliaryTableDescriptor::tableName)
            .map { (tableName, tables) ->
                val rowCount = tables.sumOf(WinRTMetadataAuxiliaryTableDescriptor::rowCount)
                val modeled = tables.any(WinRTMetadataAuxiliaryTableDescriptor::modeled)
                val projectionAffecting = tableName in PROJECTION_AFFECTING_AUXILIARY_TABLES
                WinRTAuxiliaryTableSemanticBoundaryDescriptor(
                    tableName = tableName,
                    rowCount = rowCount,
                    modeled = modeled,
                    projectionAffecting = projectionAffecting,
                    note = when {
                        modeled -> "Decoded into normalized metadata descriptors."
                        projectionAffecting -> "Projection-affecting in ECMA metadata; decode before generator consumes this table."
                        else -> "Cache-tolerated infrastructure table; no active reference generator semantic for the current Kotlin target."
                    },
                )
            }
            .sortedBy(WinRTAuxiliaryTableSemanticBoundaryDescriptor::tableName)

    fun referenceMetadataParityAudit(): List<WinRTMetadataParityAuditEntry> =
        listOf(
            WinRTMetadataParityAuditEntry("helpers.h", "get_exclusive_to_type", "WinRTMetadataSemanticHelpers.getExclusiveToType", true),
            WinRTMetadataParityAuditEntry("helpers.h", "is_ptype", "WinRTMetadataSemanticHelpers.isPType", true),
            WinRTMetadataParityAuditEntry("helpers.h", "get_default_iface_as_type_sem", "WinRTMetadataSemanticHelpers.getDefaultInterfaceSemantics", true),
            WinRTMetadataParityAuditEntry("helpers.h", "does_abi_interface_implement_ccw_interface", "WinRTMetadataSemanticHelpers.doesAbiInterfaceImplementCcwInterface(context)", true),
            WinRTMetadataParityAuditEntry("main.cpp", "componentActivatableClasses pre-scan", "WinRTMetadataSemanticHelpers.componentActivatableClasses", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "is_implemented_as_private_method", "WinRTMetadataSemanticHelpers.isImplementedAsPrivateMethod", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "is_implemented_as_private_mapped_interface", "WinRTMetadataSemanticHelpers.isImplementedAsPrivateMappedInterface", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "write_custom_query_interface_impl", "WinRTMetadataSemanticHelpers.customQueryInterfaceDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "generic_type_instances fixed point", "WinRTMetadataSemanticHelpers.genericInstantiationWorklist", true),
            WinRTMetadataParityAuditEntry("WinMD tables", "auxiliary table semantic boundary", "WinRTMetadataSemanticHelpers.auxiliaryTableSemanticBoundaries", true),
            WinRTMetadataParityAuditEntry("main.cpp", "helper output inventory", "WinRTMetadataProjectionInventory.helperOutputs", true),
            WinRTMetadataParityAuditEntry("main.cpp", "WinRTAbiDelegateInitializer conditions", "WinRTProjectionHelperOutputInventory.abiDelegateInitializerRequired", true),
            WinRTMetadataParityAuditEntry("main.cpp", "WinRTGenericTypeInstantiations/base strings conditions", "WinRTProjectionHelperOutputInventory", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "is_manually_generated_iface", "WinRTMetadataSemanticHelpers.isManuallyGeneratedInterface", true),
            WinRTMetadataParityAuditEntry("settings.h/code_writers.h", "projection context flags", "WinRTMetadataSemanticHelpers.projectionContextSemantics", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "write_class_members property merge", "WinRTMetadataSemanticHelpers.classMemberMergeDescriptor", true),
            WinRTMetadataParityAuditEntry("helpers.h", "object/class equals/hashcode helpers", "WinRTMetadataSemanticHelpers.classObjectMethodDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "generic ABI delegate operation entries", "WinRTGenericAbiDelegateDescriptor.operationName/declaration/typeArrayShape", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "write_generic_type_instantiation descriptor", "WinRTMetadataSemanticHelpers.genericInstantiationWriterDescriptor", true),
            WinRTMetadataParityAuditEntry("type_writers.h/code_writers.h", "type-name writer context", "WinRTMetadataSemanticHelpers.typeNameDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "event helper subclass descriptors", "WinRTMetadataSemanticHelpers.eventHelperSubclassDescriptors", true),
            WinRTMetadataParityAuditEntry("type_writers.h/code_writers.h", "platform guard/member platform descriptors", "WinRTMetadataSemanticHelpers.platformGuardDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "projected/ABI signature writer descriptors", "WinRTMetadataSemanticHelpers.signatureWriterDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "event invoke/event-source descriptors", "WinRTMetadataSemanticHelpers.eventInvokeDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "ABI marshaler plan descriptors", "WinRTMetadataSemanticHelpers.abiMarshalerPlanDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "static/factory/composable surface descriptors", "WinRTMetadataSemanticHelpers.factorySurfaceDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "custom mapped member output descriptors", "WinRTMetadataSemanticHelpers.customMappedMemberOutputDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "property-interface/member signature descriptors", "WinRTMetadataSemanticHelpers.interfaceMemberSignatureSetDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "GUID/IID signature writer descriptors", "WinRTMetadataSemanticHelpers.guidSignatureDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "vtable delegate/function-pointer descriptors", "WinRTMetadataSemanticHelpers.vtableWriterDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "type declaration writer taxonomy", "WinRTMetadataSemanticHelpers.typeDeclarationDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "object-reference/inheritance surface descriptors", "WinRTMetadataSemanticHelpers.objectReferenceSurfaceDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "managed ABI invoke descriptors", "WinRTMetadataSemanticHelpers.managedAbiInvokeDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "generic ABI class initialization descriptors", "WinRTMetadataSemanticHelpers.genericAbiClassInitializationDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "required-interface ABI augmentation descriptors", "WinRTMetadataSemanticHelpers.requiredInterfaceAugmentationDescriptor", true),
            WinRTMetadataParityAuditEntry("code_writers.h", "module activation/authoring helper descriptors", "WinRTMetadataSemanticHelpers.moduleActivationAndAuthoringDescriptor", true),
            WinRTMetadataParityAuditEntry("reference full audit", "metadata/generator/runtime/plugin/authoring classification", "PLAN.md Queue 10.9 + Metadata audit classification", true),
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
        fun toDescriptor(): WinRTMergedPropertyDescriptor =
            WinRTMergedPropertyDescriptor(
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

    private fun fastAbiInterfaceSlots(closure: WinRTRuntimeClassClosureDescriptor): List<WinRTFastAbiInterfaceSlot> {
        var vtableStartIndex = 6
        return closure.fastAbiInterfaces.mapIndexed { index, interfaceDescriptor ->
            val methodCount = interfaceDescriptor.definitionType?.methods?.size ?: 0
            val slot = WinRTFastAbiInterfaceSlot(
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

    private fun fastAbiPropertySlots(closure: WinRTRuntimeClassClosureDescriptor): List<WinRTFastAbiPropertySlot> {
        val slots = mutableListOf<WinRTFastAbiPropertySlot>()
        fastAbiInterfaceSlots(closure).forEach { interfaceSlot ->
            val interfaceDescriptor = closure.fastAbiInterfaces.first { it.interfaceName == interfaceSlot.interfaceName }
            val interfaceType = interfaceDescriptor.definitionType
            val methodOrder = interfaceType?.let(::methodOrderByName).orEmpty()
            interfaceType?.properties.orEmpty().forEach { property ->
                val getterOffset = property.getterMethodName?.let { methodOrder[it] }
                val setterOffset = property.setterMethodName?.let { methodOrder[it] }
                slots += WinRTFastAbiPropertySlot(
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

    private fun methodOrderByName(type: WinRTTypeDefinition): Map<String, Int> =
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

    private fun abiMemberOrderByRowId(type: WinRTTypeDefinition): Map<Int, Int> {
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

    private fun WinRTTypeDefinition.effectiveAccessorRowId(accessorName: String, declaredRowId: Int?): Int? =
        methods.firstOrNull { it.name == accessorName }?.methodRowId ?: declaredRowId

    private fun projectedSignatureHasGenericParameters(
        returnType: WinRTTypeRef,
        parameters: List<WinRTParameterDefinition>,
    ): Boolean =
        returnType.containsGenericTypeParameter() || parameters.any { parameter -> parameter.type.containsGenericTypeParameter() }

    private fun signatureHasOnlyGenericReturn(
        returnType: WinRTTypeRef,
        parameters: List<WinRTParameterDefinition>,
    ): Boolean =
        returnType.containsGenericTypeParameter() && parameters.none { parameter -> parameter.type.containsGenericTypeParameter() }

    private fun WinRTTypeRef.containsGenericTypeParameter(): Boolean {
        val normalized = normalized()
        return normalized.kind == WinRTTypeRefKind.GenericTypeParameter ||
            normalized.kind == WinRTTypeRefKind.MethodTypeParameter ||
            normalized.typeArguments.any { it.containsGenericTypeParameter() } ||
            normalized.elementType?.containsGenericTypeParameter() == true
    }

    private fun stripGenericArity(name: String): String = name.substringBefore('`')

    private fun renderAbiTypeName(type: WinRTTypeRef): String {
        val normalizedTypeName = type.normalized().typeName
        return winRTFundamentalTypeForName(normalizedTypeName)?.toNativeAbiTypeName() ?: normalizedTypeName
    }

    private fun signatureParameterWriterDescriptor(
        parameter: WinRTParameterDefinition,
        category: WinRTMetadataParameterCategory,
    ): WinRTSignatureParameterWriterDescriptor =
        WinRTSignatureParameterWriterDescriptor(
            originalName = parameter.name,
            escapedName = escapeIdentifier(parameter.name),
            category = category,
            projectionTypeName = parameter.type.normalized().typeName,
            abiTypeName = renderAbiTypeName(parameter.type),
            modifier = when (category) {
                WinRTMetadataParameterCategory.Ref -> "ref"
                WinRTMetadataParameterCategory.Out,
                WinRTMetadataParameterCategory.FillArray,
                WinRTMetadataParameterCategory.ReceiveArray,
                -> "out"
                else -> null
            },
            expandsArrayLength = category in setOf(
                WinRTMetadataParameterCategory.PassArray,
                WinRTMetadataParameterCategory.FillArray,
                WinRTMetadataParameterCategory.ReceiveArray,
            ),
        )

    private fun abiMarshalerSlot(
        name: String,
        type: WinRTTypeRef,
        category: WinRTMetadataParameterCategory,
        isReturn: Boolean,
    ): WinRTAbiMarshalerSlotDescriptor {
        val normalized = type.normalized()
        val classification = typeClassifier.classify(normalized, "")
        val generic = normalized.containsGenericTypeParameter()
        val array = normalized.kind == WinRTTypeRefKind.Array
        val value = semanticValueDescriptor(normalized, "", forArray = array)
        val pinnable = value.isBlittable && (category == WinRTMetadataParameterCategory.In || category == WinRTMetadataParameterCategory.PassArray)
        val requiresMarshal = generic || array || classification.projectionCategory !in setOf(
            WinRTProjectionCategory.Fundamental,
            WinRTProjectionCategory.Enum,
            WinRTProjectionCategory.Guid,
        )
        return WinRTAbiMarshalerSlotDescriptor(
            name = escapeIdentifier(name),
            typeName = normalized.typeName,
            category = category,
            isReturn = isReturn,
            isPinnable = pinnable,
            isGeneric = generic,
            requiresFromAbi = isReturn || category in setOf(WinRTMetadataParameterCategory.Out, WinRTMetadataParameterCategory.Ref, WinRTMetadataParameterCategory.ReceiveArray),
            requiresToAbi = !isReturn && category in setOf(WinRTMetadataParameterCategory.In, WinRTMetadataParameterCategory.Ref, WinRTMetadataParameterCategory.PassArray),
            requiresDispose = requiresMarshal && !pinnable,
        )
    }

    private fun cacheNameFor(typeName: String): String =
        escapeTypeNameForIdentifier(typeName).replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + "Cache"

    private fun getGenericAbiTypes(method: WinRTMethodDefinition): List<String> =
        (listOf(method.returnType) + method.parameters.map(WinRTParameterDefinition::type))
            .filter { it.containsGenericTypeParameter() || it.typeArguments.isNotEmpty() }
            .map { renderAbiTypeName(it) }
            .distinct()

    private fun guidTextToBytes(guid: String): List<Int> =
        guid.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            .chunked(2)
            .mapNotNull { it.toIntOrNull(16) }

    private fun guidSignatureFragment(type: WinRTTypeDefinition): String =
        when (type.kind) {
            WinRTTypeKind.Interface -> type.iid?.let(::guidSignatureFragmentForGuid).orEmpty()
            WinRTTypeKind.Delegate -> type.iid?.let { guid -> "delegate(${guidSignatureFragmentForGuid(guid)})" }.orEmpty()
            WinRTTypeKind.RuntimeClass -> {
                val defaultInterfaceSignature = getDefaultInterface(type)
                    ?.let { defaultInterface -> guidSignatureFragmentForTypeRef(defaultInterface, type.namespace) }
                    .orEmpty()
                if (defaultInterfaceSignature.isNotEmpty()) {
                    "rc(${type.qualifiedName};$defaultInterfaceSignature)"
                } else {
                    type.iid?.let(::guidSignatureFragmentForGuid).orEmpty()
                }
            }
            WinRTTypeKind.Struct -> {
                val fieldSignatures = type.fields
                    .filterNot { field -> field.isStatic || field.isLiteral }
                    .joinToString(";") { field -> guidSignatureFragmentForTypeRef(field.type, type.namespace) }
                "struct(${type.qualifiedName};$fieldSignatures)"
            }
            WinRTTypeKind.Enum -> "enum(${type.qualifiedName};${if (isFlagsEnum(type)) "u4" else "i4"})"
            WinRTTypeKind.Unknown -> ""
        }

    private fun guidSignatureFragmentForTypeRef(type: WinRTTypeRef, currentNamespace: String): String {
        val normalizedType = type.normalized()
        fundamentalGuidSignatureFragment(normalizedType.typeName)?.let { signature -> return signature }
        if (normalizedType.kind != WinRTTypeRefKind.Named) {
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
        type: WinRTTypeRef,
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

    private fun fundamentalGuidSignatureFragment(typeName: String): String? {
        if (isWinRTObjectTypeName(typeName)) {
            return "cinterface(IInspectable)"
        }
        winRTFundamentalTypeForName(typeName)?.let { type -> return type.guidSignatureFragment() }
        return if (isWinRTGuidTypeName(typeName)) "g16" else null
    }

    private fun escapeIdentifier(value: String): String =
        if (value in KOTLIN_KEYWORDS || value in CSHARP_PROJECTION_KEYWORDS) "`$value`" else value

    private inner class GenericAbiInventoryCollector {
        private val abiDelegates = linkedMapOf<String, WinRTGenericAbiDelegateDescriptor>()
        private val typeInstantiations = linkedMapOf<String, WinRTGenericTypeInstantiationDescriptor>()
        private val derivedGenericInterfaces = linkedSetOf<String>()

        fun addGenericTypeReferencesInType(type: WinRTTypeDefinition) {
            when (type.kind) {
                WinRTTypeKind.Delegate -> getDelegateInvoke(type)?.let { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
                WinRTTypeKind.Interface -> {
                    if (hasDerivedGenericInterface(type)) {
                        derivedGenericInterfaces += type.qualifiedName
                    }
                    addGenericTypeReferencesInInterfaceType(type)
                }
                WinRTTypeKind.RuntimeClass -> addGenericTypeReferencesInRuntimeClass(type)
                else -> Unit
            }
        }

        fun toInventory(context: WinRTMetadataProjectionContext): WinRTGenericAbiInventory =
            WinRTGenericAbiInventory(
                genericAbiDelegates = abiDelegates.values.sortedWith(compareBy({ it.abiDelegateTypesKey }, { it.abiDelegateName })),
                genericTypeInstantiations = typeInstantiations.values
                    .map { instantiation ->
                        instantiation.copy(
                            implementsCcwInterface = instantiation.definitionType
                                ?.let { definition -> doesAbiInterfaceImplementCcwInterface(definition, context) }
                                ?: false,
                        )
                    }
                    .sortedBy(WinRTGenericTypeInstantiationDescriptor::instantiationClassName),
                derivedGenericInterfaces = derivedGenericInterfaces.sorted(),
            )

        private fun addGenericTypeReferencesInInterfaceType(type: WinRTTypeDefinition) {
            type.methods.filterNot(::isSpecial).forEach { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
            type.properties.forEach { property -> addIfGenericTypeReference(property.type, property.type.kind == WinRTTypeRefKind.Array, type.namespace) }
            type.events.forEach { event -> addIfGenericTypeReference(event.delegateType, isArray = false, currentNamespace = type.namespace) }
            type.implementedInterfaces.forEach { implemented ->
                addIfGenericTypeReference(implemented.interfaceType, isArray = false, currentNamespace = type.namespace)
            }
        }

        private fun addGenericTypeReferencesInRuntimeClass(type: WinRTTypeDefinition) {
            type.methods.filterNot(::isSpecial).forEach { method -> addGenericTypeReferencesInMethod(method, type.namespace) }
            type.properties.forEach { property -> addIfGenericTypeReference(property.type, property.type.kind == WinRTTypeRefKind.Array, type.namespace) }
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

        private fun addGenericTypeReferencesInMethod(method: WinRTMethodDefinition, currentNamespace: String) {
            method.parameters.forEach { parameter ->
                addIfGenericTypeReference(parameter.type, parameter.type.kind == WinRTTypeRefKind.Array, currentNamespace = currentNamespace)
            }
            addIfGenericTypeReference(method.returnType, method.returnType.kind == WinRTTypeRefKind.Array, currentNamespace = currentNamespace)
        }

        private fun addIfGenericTypeReference(type: WinRTTypeRef, isArray: Boolean, currentNamespace: String) {
            val normalized = type.normalized()
            when (normalized.kind) {
                WinRTTypeRefKind.Named -> {
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
                            WinRTGenericTypeInstantiationDescriptor(
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
                    if (definitionType?.kind == WinRTTypeKind.Struct) {
                        definitionType.fields.forEach { field ->
                            addIfGenericTypeReference(field.type, field.type.kind == WinRTTypeRefKind.Array, definitionType.namespace)
                        }
                    }
                }

                WinRTTypeRefKind.Array -> {
                    val element = normalized.elementType ?: WinRTTypeRef.unknown()
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

        private fun addAbiDelegatesForArray(elementType: WinRTTypeRef, currentNamespace: String) {
            addAbiDelegatesForType(
                WinRTTypeRef.named(
                    qualifiedName = "Windows.Foundation.Collections.IVector",
                    typeArguments = listOf(resolveTypeReference(elementType, currentNamespace, typesByQualifiedName).type),
                ),
            )
        }

        private fun addAbiDelegatesForType(type: WinRTTypeRef) {
            val normalized = type.normalized()
            if (normalized.typeArguments.isEmpty()) return
            val descriptor = typeClassifier.classify(normalized, "")
            abiDelegateOperationsFor(descriptor.specialType, normalized.typeArguments).forEach { operation ->
                val currentNamespace = descriptor.definitionType?.namespace
                    ?: normalized.qualifiedName?.substringBeforeLast('.', missingDelimiterValue = "")
                    ?: ""
                addAbiDelegateOperation(normalized, operation, currentNamespace)
            }
        }

        private fun addAbiDelegateOperation(
            sourceType: WinRTTypeRef,
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
                WinRTGenericAbiDelegateDescriptor(
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

        private fun abiDelegateOperationsFor(
            specialType: WinRTSpecialTypeDescriptor?,
            arguments: List<WinRTTypeRef>,
        ): List<AbiDelegateOperation> =
            when (specialType) {
                is WinRTCollectionTypeDescriptor -> collectionAbiDelegateOperationsFor(specialType.kind, arguments)
                is WinRTReferenceTypeDescriptor -> referenceAbiDelegateOperationsFor(specialType.kind, arguments)
                is WinRTAsyncTypeDescriptor -> asyncAbiDelegateOperationsFor(specialType.kind, arguments)
                is WinRTEventHandlerTypeDescriptor -> eventHandlerAbiDelegateOperationsFor(specialType.kind, arguments)
                is WinRTBindableCollectionTypeDescriptor,
                null,
                -> emptyList()
            }

        private fun collectionAbiDelegateOperationsFor(
            kind: WinRTCollectionInterfaceKind,
            arguments: List<WinRTTypeRef>,
        ): List<AbiDelegateOperation> =
            when (kind) {
                WinRTCollectionInterfaceKind.Iterator ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn))
                WinRTCollectionInterfaceKind.KeyValuePair ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Key", listOf(0), AbiDelegateShape.IntPtrOutPointer)) +
                        ifRequired(arguments, 1, AbiDelegateOperation("get_Value", listOf(1), AbiDelegateShape.IntPtrOutPointer))
                WinRTCollectionInterfaceKind.MapView -> {
                    val lookup = if (arguments.getOrNull(0)?.isAbiDelegateRequired() == true || arguments.getOrNull(1)?.isAbiDelegateRequired() == true) {
                        listOf(AbiDelegateOperation("lookup", listOf(0, 1), AbiDelegateShape.MapLookup))
                    } else {
                        emptyList()
                    }
                    lookup + ifRequired(arguments, 0, AbiDelegateOperation("has_key", listOf(0), AbiDelegateShape.HasKey))
                }
                WinRTCollectionInterfaceKind.Map -> {
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
                WinRTCollectionInterfaceKind.VectorView ->
                    ifRequired(
                        arguments,
                        0,
                        AbiDelegateOperation("get_at", listOf(0), AbiDelegateShape.IndexOutReturn),
                        AbiDelegateOperation("index_of", listOf(0), AbiDelegateShape.IndexOf),
                        AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn),
                    )
                WinRTCollectionInterfaceKind.Vector ->
                    ifRequired(
                        arguments,
                        0,
                        AbiDelegateOperation("get_at", listOf(0), AbiDelegateShape.IndexOutReturn),
                        AbiDelegateOperation("index_of", listOf(0), AbiDelegateShape.IndexOf),
                        AbiDelegateOperation("set_at", listOf(0), AbiDelegateShape.IndexValue),
                        AbiDelegateOperation("append", listOf(0), AbiDelegateShape.Value),
                        AbiDelegateOperation("get_Current", listOf(0), AbiDelegateShape.OutReturn),
                    )
                WinRTCollectionInterfaceKind.Iterable -> emptyList()
            }

        private fun referenceAbiDelegateOperationsFor(
            kind: WinRTReferenceInterfaceKind,
            arguments: List<WinRTTypeRef>,
        ): List<AbiDelegateOperation> =
            when (kind) {
                WinRTReferenceInterfaceKind.Reference ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get_Value", listOf(0), AbiDelegateShape.OutReturn))
                WinRTReferenceInterfaceKind.ReferenceArray -> emptyList()
            }

        private fun asyncAbiDelegateOperationsFor(
            kind: WinRTAsyncInterfaceKind,
            arguments: List<WinRTTypeRef>,
        ): List<AbiDelegateOperation> =
            when (kind) {
                WinRTAsyncInterfaceKind.Operation ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get", listOf(0), AbiDelegateShape.OutReturn))
                WinRTAsyncInterfaceKind.OperationWithProgress ->
                    ifRequired(arguments, 0, AbiDelegateOperation("get", listOf(0), AbiDelegateShape.OutReturn)) +
                        ifRequired(arguments, 1, AbiDelegateOperation("invoke", listOf(1), AbiDelegateShape.AsyncProgress))
                WinRTAsyncInterfaceKind.Info,
                WinRTAsyncInterfaceKind.Action,
                WinRTAsyncInterfaceKind.ActionWithProgress,
                -> emptyList()
            }

        private fun eventHandlerAbiDelegateOperationsFor(
            kind: WinRTEventHandlerKind,
            arguments: List<WinRTTypeRef>,
        ): List<AbiDelegateOperation> =
            when (kind) {
                WinRTEventHandlerKind.EventHandler ->
                    ifRequired(arguments, 0, AbiDelegateOperation("invoke", listOf(0), AbiDelegateShape.EventHandler))
                WinRTEventHandlerKind.TypedEventHandler -> {
                    if (arguments.getOrNull(0)?.isAbiDelegateRequired() == true || arguments.getOrNull(1)?.isAbiDelegateRequired() == true) {
                        listOf(AbiDelegateOperation("invoke", listOf(0, 1), AbiDelegateShape.TypedEventHandler))
                    } else {
                        emptyList()
                    }
                }
                WinRTEventHandlerKind.AsyncOperationProgressHandler ->
                    ifRequired(arguments, 1, AbiDelegateOperation("invoke", listOf(1), AbiDelegateShape.AsyncProgress))
                WinRTEventHandlerKind.AsyncActionProgressHandler ->
                    ifRequired(arguments, 0, AbiDelegateOperation("invoke", listOf(0), AbiDelegateShape.AsyncProgress))
                WinRTEventHandlerKind.PropertyChangedEventHandler,
                WinRTEventHandlerKind.NotifyCollectionChangedEventHandler,
                WinRTEventHandlerKind.VectorChangedEventHandler,
                WinRTEventHandlerKind.BindableVectorChangedEventHandler,
                WinRTEventHandlerKind.MapChangedEventHandler,
                -> emptyList()
            }

        private fun ifRequired(
            arguments: List<WinRTTypeRef>,
            index: Int,
            vararg operations: AbiDelegateOperation,
        ): List<AbiDelegateOperation> =
            if (arguments.getOrNull(index)?.isAbiDelegateRequired() == true) operations.toList() else emptyList()

        private fun WinRTTypeRef.isAbiDelegateRequired(): Boolean {
            val normalized = normalized()
            return when (normalized.kind) {
                WinRTTypeRefKind.Named -> {
                    val descriptor = typeClassifier.classify(normalized, "")
                    when (descriptor.projectionCategory) {
                        WinRTProjectionCategory.Fundamental ->
                            winRTFundamentalTypeForName(descriptor.typeName)?.isWinRTValueType == true
                        WinRTProjectionCategory.Guid,
                        WinRTProjectionCategory.Enum,
                        WinRTProjectionCategory.Struct,
                        -> true
                        else -> false
                    }
                }

                WinRTTypeRefKind.GenericTypeParameter,
                WinRTTypeRefKind.MethodTypeParameter,
                WinRTTypeRefKind.Array,
                WinRTTypeRefKind.Unknown,
                -> false
            }
        }

        private fun renderAbiDelegateTypeName(type: WinRTTypeRef, currentNamespace: String): String {
            val resolved = resolveTypeReference(type.normalized(), currentNamespace, typesByQualifiedName).type.normalized()
            val descriptor = typeClassifier.classify(resolved, currentNamespace)
            return when (descriptor.projectionCategory) {
                WinRTProjectionCategory.Fundamental,
                WinRTProjectionCategory.String,
                -> winRTFundamentalTypeForName(descriptor.typeName)
                    ?.toNativeAbiTypeName()
                    ?: descriptor.typeName
                WinRTProjectionCategory.Guid,
                WinRTProjectionCategory.Enum,
                WinRTProjectionCategory.Struct,
                -> descriptor.type.typeName
                WinRTProjectionCategory.Object,
                WinRTProjectionCategory.Interface,
                WinRTProjectionCategory.RuntimeClass,
                WinRTProjectionCategory.Delegate,
                WinRTProjectionCategory.Attribute,
                WinRTProjectionCategory.Array,
                -> "IntPtr"
                WinRTProjectionCategory.Type,
                WinRTProjectionCategory.ApiContract,
                WinRTProjectionCategory.GenericTypeParameter,
                WinRTProjectionCategory.MethodTypeParameter,
                WinRTProjectionCategory.Unit,
                WinRTProjectionCategory.Unknown,
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
        private val CSHARP_PROJECTION_KEYWORDS = setOf("event", "delegate", "base", "params", "ref", "out", "in")
    }
}

fun WinRTMetadataModel.semanticHelpers(): WinRTMetadataSemanticHelpers =
    WinRTMetadataSemanticHelpers(this)

fun metadataParameterCategoryFor(parameter: WinRTParameterDefinition): WinRTMetadataParameterCategory {
    val type = parameter.type.normalized()
    if (type.kind == WinRTTypeRefKind.Array) {
        return when {
            parameter.isInParameter -> WinRTMetadataParameterCategory.PassArray
            parameter.typeIsByRef -> WinRTMetadataParameterCategory.ReceiveArray
            parameter.isOutParameter -> WinRTMetadataParameterCategory.FillArray
            parameter.direction == WinRTParameterDirection.Out -> WinRTMetadataParameterCategory.FillArray
            else -> WinRTMetadataParameterCategory.PassArray
        }
    }
    return when {
        parameter.isOutParameter -> WinRTMetadataParameterCategory.Out
        parameter.typeIsByRef -> WinRTMetadataParameterCategory.Ref
        else -> WinRTMetadataParameterCategory.In
    }
}

private fun escapeTypeNameForIdentifier(typeName: String): String =
    typeName.replace(Regex("""[\s:<>,.]"""), "_")

private fun WinRTTypeRef.matchesFundamentalType(expectedType: WinRTFundamentalType): Boolean =
    isWinRTFundamentalTypeName(normalized().typeName, expectedType)
