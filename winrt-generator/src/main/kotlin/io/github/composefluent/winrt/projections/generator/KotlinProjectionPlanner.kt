package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomAttributeValue
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRTFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTFieldDefinition
import io.github.composefluent.winrt.metadata.WinRTGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRTGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRTGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRTInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRTInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRTIntegralType
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRTMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMappedTypeDescriptor
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.projectedPropertyTypeName
import io.github.composefluent.winrt.metadata.projectedAttributes
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.metadataParameterCategoryFor
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import io.github.composefluent.winrt.metadata.winRTEventHandlerKindForTypeName
import io.github.composefluent.winrt.metadata.WinRTEventHandlerKind
import io.github.composefluent.winrt.runtime.ActivationFactory
import io.github.composefluent.winrt.runtime.ComObjectReference
import io.github.composefluent.winrt.runtime.ComVtableInvoker
import io.github.composefluent.winrt.runtime.Guid
import io.github.composefluent.winrt.runtime.HResult
import io.github.composefluent.winrt.runtime.HString
import io.github.composefluent.winrt.runtime.IUnknownReference
import io.github.composefluent.winrt.runtime.IWinRTObject
import io.github.composefluent.winrt.runtime.Marshaler
import io.github.composefluent.winrt.runtime.PlatformAbi
import io.github.composefluent.winrt.runtime.ParameterizedInterfaceId
import io.github.composefluent.winrt.runtime.RawAddress
import io.github.composefluent.winrt.runtime.NativeNestedStructFieldSpec
import io.github.composefluent.winrt.runtime.NativeScalarFieldSpec
import io.github.composefluent.winrt.runtime.NativeStructLayout
import io.github.composefluent.winrt.runtime.NativeStructScalarKind
import io.github.composefluent.winrt.runtime.WinRTBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRTBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRTCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRTDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTIterableProjection
import io.github.composefluent.winrt.runtime.WinRTListProjection
import io.github.composefluent.winrt.runtime.WinRTAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRTAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRTReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRTReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceProjection
import io.github.composefluent.winrt.runtime.WinRTReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRTPlatformApi
import io.github.composefluent.winrt.runtime.WinRTTypeSignature
import io.github.composefluent.winrt.runtime.WinRTTypeHandle
import io.github.composefluent.winrt.runtime.WinRTUri
import io.github.composefluent.winrt.runtime.WinRTDelegateBridge
import io.github.composefluent.winrt.runtime.WinRTDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRTDelegateReference
import io.github.composefluent.winrt.runtime.WinRTDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRTEvent
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.collections.AbstractList
import kotlin.collections.AbstractMap
import kotlin.LazyThreadSafetyMode
import kotlin.io.path.extension

class KotlinProjectionPlanner(
    private val validator: KotlinProjectionContractValidator = KotlinProjectionContractValidator(),
    private val useWinAppSdkTypeRedirects: Boolean = false,
) {
    fun plan(
        model: WinRTMetadataModel,
        projectionContext: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    ): List<KotlinTypeProjectionPlan> {
        val normalized = validator.validate(model)
        return planValidated(normalized, projectionContext)
    }

    private fun planValidated(
        normalized: WinRTMetadataModel,
        projectionContext: WinRTMetadataProjectionContext,
    ): List<KotlinTypeProjectionPlan> =
        normalized.let {
            val semanticHelpers = normalized.semanticHelpers()
            val typesByQualifiedName = normalized.namespaces
                .flatMap(WinRTNamespace::types)
                .associateBy(WinRTTypeDefinition::qualifiedName)
            val interfaceIidsByName = normalized.namespaces
                .flatMap(WinRTNamespace::types)
                .associate { it.qualifiedName to it.iid }
            val abiSlotBindingCache = mutableMapOf<String, List<KotlinProjectionAbiSlotBinding>>()
            val abiMemberCountCache = mutableMapOf<String, Int>()
            normalized.namespaces.flatMap {
                val namespacePlanner =
                    if (!useWinAppSdkTypeRedirects && it.requiresWinAppSdkTypeRedirects()) {
                        KotlinProjectionPlanner(validator, useWinAppSdkTypeRedirects = true)
                    } else {
                        this
                    }
                namespacePlanner.planNamespace(
                    namespace = it,
                    interfaceIidsByName = interfaceIidsByName,
                    typesByQualifiedName = typesByQualifiedName,
                    projectionContext = projectionContext,
                    semanticHelpers = semanticHelpers,
                    abiSlotBindingCache = abiSlotBindingCache,
                    abiMemberCountCache = abiMemberCountCache,
                )
            }
        }

    private fun WinRTNamespace.requiresWinAppSdkTypeRedirects(): Boolean =
        name == "Microsoft.UI" || name.startsWith("Microsoft.UI.")

    fun planNamespace(
        namespace: WinRTNamespace,
        interfaceIidsByName: Map<String, Guid?> = emptyMap(),
        typesByQualifiedName: Map<String, WinRTTypeDefinition> = emptyMap(),
        projectionContext: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
        semanticHelpers: WinRTMetadataSemanticHelpers? = null,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>> = mutableMapOf(),
        abiMemberCountCache: MutableMap<String, Int> = mutableMapOf(),
    ): List<KotlinTypeProjectionPlan> =
        namespace.normalized().let { normalizedNamespace ->
            val helpers = semanticHelpers ?: WinRTMetadataModel(listOf(normalizedNamespace)).semanticHelpers()
            normalizedNamespace.types.mapNotNull { type ->
                planType(
                    type,
                    interfaceIidsByName,
                    typesByQualifiedName,
                    projectionContext,
                    helpers,
                    abiSlotBindingCache,
                    abiMemberCountCache,
                )
            }
        }

    private fun planType(
        type: WinRTTypeDefinition,
        interfaceIidsByName: Map<String, Guid?>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        projectionContext: WinRTMetadataProjectionContext,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
    ): KotlinTypeProjectionPlan? {
        val typeDeclarationDescriptor = semanticHelpers.typeDeclarationDescriptor(type)
        if (!typeDeclarationDescriptor.writesProjectedDeclaration) {
            return null
        }
        if (shouldSkipMappedProjection(type, semanticHelpers)) {
            return null
        }
        fun interfaceIidFor(interfaceName: String): Guid? =
            interfaceIidsByName[interfaceName]
                ?: interfaceIidsByName[interfaceName.substringBefore('<').removeSuffix("?")]
                ?: interfaceIidsByName["${type.namespace}.${interfaceName.substringBefore('<').removeSuffix("?")}"]

        fun interfaceIidFor(typeRef: WinRTTypeRef?): Guid? {
            val normalized = typeRef?.normalized() ?: return null
            if (normalized.typeArguments.isNotEmpty()) {
                val signature = semanticHelpers.parameterizedGuidSignatureFragment(normalized, type.namespace)
                if (signature.isNotBlank()) {
                    return ParameterizedInterfaceId.createFromSignature(signature)
                }
            }
            val interfaceName = normalized.qualifiedName ?: return null
            return interfaceIidFor(interfaceName)
        }

        val declarationKind = when (type.kind) {
            WinRTTypeKind.Interface -> KotlinProjectionDeclarationKind.Interface
            WinRTTypeKind.RuntimeClass -> KotlinProjectionDeclarationKind.Class
            WinRTTypeKind.Enum -> KotlinProjectionDeclarationKind.Enum
            WinRTTypeKind.Struct -> if (type.isApiContract) {
                KotlinProjectionDeclarationKind.Enum
            } else {
                KotlinProjectionDeclarationKind.Struct
            }
            WinRTTypeKind.Delegate -> KotlinProjectionDeclarationKind.Delegate
            WinRTTypeKind.Unknown -> return null
        }
        val packageName = (ROOT_PACKAGE_SEGMENTS + namespaceSegments(type.namespace)).joinToString(".")
        val relativePath = packageName.replace('.', '/') + "/${type.name}.kt"
        return KotlinTypeProjectionPlan(
            type = type,
            typesByQualifiedName = typesByQualifiedName,
            packageName = packageName,
            relativePath = relativePath,
            declarationKind = declarationKind,
            visibility = planVisibility(type, projectionContext, semanticHelpers),
            modifiers = planModifiers(type),
            specializationKinds = planSpecializations(type),
            interfaceIid = type.iid,
            defaultInterfaceName = type.defaultInterfaceName,
            defaultInterfaceIid = interfaceIidFor(type.defaultInterface),
            staticInterfaceNames = type.activation.staticInterfaceNames,
            staticInterfaceBindings = type.activation.staticInterfaceNames.map { interfaceName ->
                KotlinProjectionInterfaceBinding(
                    qualifiedName = interfaceName,
                    iid = interfaceIidFor(interfaceName),
                )
            },
            implementedInterfaceBindings = type.implementedInterfaces
                .filterNot { it.isDefault }
                .map { implemented ->
                    KotlinProjectionInterfaceBinding(
                        qualifiedName = implemented.interfaceName,
                        iid = interfaceIidFor(implemented.interfaceName),
                    )
                },
            activatableFactoryInterfaceName = type.activation.activatableFactoryInterfaceName,
            activatableFactoryInterfaceIid = type.activation.activatableFactoryInterfaceName?.let(::interfaceIidFor),
            composableFactoryInterfaceName = type.activation.composableFactoryInterfaceName,
            composableFactoryInterfaceIid = type.activation.composableFactoryInterfaceName?.let(::interfaceIidFor),
            abiSlotBindings = cachedAbiSlotBindings(type, typesByQualifiedName, semanticHelpers, abiSlotBindingCache, abiMemberCountCache),
            instanceMemberBindings = planInstanceMemberBindings(type, typesByQualifiedName, semanticHelpers, abiSlotBindingCache, abiMemberCountCache),
            staticMemberBindings = planStaticMemberBindings(type, typesByQualifiedName, semanticHelpers, abiSlotBindingCache, abiMemberCountCache),
            readOnlyCollectionBindings = planReadOnlyCollectionBindings(type, typesByQualifiedName, semanticHelpers),
            mutableCollectionBindings = planMutableCollectionBindings(type, typesByQualifiedName, semanticHelpers),
            delegateInvokeShape =
                if (type.kind == WinRTTypeKind.Delegate) {
                    classifyAbiTypeBinding(type.qualifiedName, type.namespace, typesByQualifiedName).delegateInvokeShape
                } else {
                    null
                },
            eventInvokeDescriptors = type.events.map { event -> semanticHelpers.eventInvokeDescriptor(type, event) },
            typeDeclarationDescriptor = typeDeclarationDescriptor,
            factorySurfaceDescriptor = if (type.kind == WinRTTypeKind.RuntimeClass) semanticHelpers.factorySurfaceDescriptor(type) else null,
            objectReferenceSurfaceDescriptor = if (type.kind in setOf(WinRTTypeKind.RuntimeClass, WinRTTypeKind.Delegate)) {
                semanticHelpers.objectReferenceSurfaceDescriptor(type)
            } else {
                null
            },
            guidSignatureDescriptor = type.iid?.let { semanticHelpers.guidSignatureDescriptor(type) },
            interfaceMemberSignatureSetDescriptor = if (type.kind == WinRTTypeKind.Interface) {
                semanticHelpers.interfaceMemberSignatureSetDescriptor(type)
            } else {
                null
            },
            customMappedMemberOutputDescriptor = if (type.kind == WinRTTypeKind.Interface) {
                semanticHelpers.customMappedMemberOutputDescriptor(type)
            } else {
                null
            },
            classMemberMergeDescriptor = if (type.kind == WinRTTypeKind.RuntimeClass) {
                semanticHelpers.classMemberMergeDescriptor(type)
            } else {
                null
            },
            genericAbiClassInitializationDescriptor = if (type.kind in setOf(WinRTTypeKind.Interface, WinRTTypeKind.Delegate)) {
                semanticHelpers.genericAbiClassInitializationDescriptor(type)
            } else {
                null
            },
            requiredInterfaceAugmentationDescriptor = if (type.kind in setOf(WinRTTypeKind.Interface, WinRTTypeKind.RuntimeClass)) {
                semanticHelpers.requiredInterfaceAugmentationDescriptor(type)
            } else {
                null
            },
            fastAbiClassDescriptor = when (type.kind) {
                WinRTTypeKind.RuntimeClass -> semanticHelpers.getFastAbiClassForClass(type)
                WinRTTypeKind.Interface -> semanticHelpers.getFastAbiClassForInterface(type)
                else -> null
            },
            moduleActivationAndAuthoringDescriptor = if (type.kind == WinRTTypeKind.RuntimeClass) {
                semanticHelpers.moduleActivationAndAuthoringDescriptor(type)
            } else {
                null
            },
            projectedAttributes = semanticHelpers.projectedAttributes(type).normalizeApiContractProjectedAttributes(type),
            companionKinds = planCompanions(type),
        )
    }

    private fun List<WinRTProjectedAttributeDescriptor>.normalizeApiContractProjectedAttributes(
        type: WinRTTypeDefinition,
    ): List<WinRTProjectedAttributeDescriptor> =
        if (!type.isApiContract) {
            this
        } else {
            map { attribute ->
                if (
                    attribute.projectedTypeName == "Windows.Foundation.Metadata.ContractVersion" &&
                    attribute.arguments.size == 1 &&
                    attribute.arguments.single() is WinRTCustomAttributeValue.IntegralValue
                ) {
                    attribute.copy(
                        arguments = listOf(WinRTCustomAttributeValue.StringValue(type.qualifiedName)) + attribute.arguments,
                    )
                } else {
                    attribute
                }
            }
        }

    private fun shouldSkipMappedProjection(
        type: WinRTTypeDefinition,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): Boolean {
        val metadataMappedType = semanticHelpers.getMappedType(type.namespace, type.name)
        val generatorMappedType = mappedTypeByAbiName(type.qualifiedName)
        if (metadataMappedType == null) {
            return generatorMappedType?.isRuntimeOwnedProjection() == true
        }
        if (metadataMappedType.requiresKotlinMappedSupportDeclaration(type)) {
            return false
        }
        return true
    }

    private fun cachedAbiSlotBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
    ): List<KotlinProjectionAbiSlotBinding> {
        if (type.kind != WinRTTypeKind.Interface) {
            return emptyList()
        }
        return abiSlotBindingCache.getOrPut(type.qualifiedName) {
            planAbiSlotBindings(type, typesByQualifiedName, semanticHelpers, abiMemberCountCache)
        }
    }

    private fun planAbiSlotBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiMemberCountCache: MutableMap<String, Int>,
    ): List<KotlinProjectionAbiSlotBinding> {
        if (type.kind != WinRTTypeKind.Interface) {
            return emptyList()
        }
        val methodSlotNamesByRowId = type.methods
            .mapNotNull { method -> method.methodRowId?.let { it to method.abiSlotConstantName(type.methods) } }
            .toMap()
        val methodDescriptorsByConstant = semanticHelpers.vtableWriterDescriptor(type).methods
            .associateBy { descriptor ->
                descriptor.methodRowId?.let(methodSlotNamesByRowId::get) ?: descriptor.methodName.methodSlotConstantName()
            }
        val baseSlotCount = type.implementedInterfaces.sumOf { implemented ->
            interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, mutableSetOf(), abiMemberCountCache)
        }
        val fastAbiSlotStart = semanticHelpers.getFastAbiClassForInterface(type)
            ?.interfaceSlots
            ?.firstOrNull { slot -> slot.interfaceName == type.qualifiedName }
            ?.vtableStartIndex
        val localMembers = type.localAbiMemberOrders()
        val slotIndexByRowId = localMembers
            .map(AbiMemberOrder::rowId)
            .distinct()
            .mapIndexed { index, rowId -> rowId to index }
            .toMap()
        return localMembers.map { member ->
            val constantName = member.constantName
            val descriptor = methodDescriptorsByConstant[constantName]
            KotlinProjectionAbiSlotBinding(
                constantName = constantName,
                slot = fastAbiSlotStart?.plus(slotIndexByRowId.getValue(member.rowId))
                    ?: descriptor?.slotIndex
                    ?: 6 + baseSlotCount + slotIndexByRowId.getValue(member.rowId),
                descriptor = descriptor,
            )
        }
    }

    private fun interfaceAbiMemberCount(
        interfaceName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: MutableSet<String>,
        abiMemberCountCache: MutableMap<String, Int>,
    ): Int {
        abiMemberCountCache[interfaceName]?.let { return it }
        val type = typesByQualifiedName[interfaceName] ?: return 0
        if (type.kind != WinRTTypeKind.Interface || !visiting.add(interfaceName)) {
            return 0
        }
        val count = try {
            type.implementedInterfaces.sumOf { implemented ->
                interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, visiting, abiMemberCountCache)
            } + type.localAbiMemberOrders().map(AbiMemberOrder::rowId).distinct().size
        } finally {
            visiting.remove(interfaceName)
        }
        abiMemberCountCache[interfaceName] = count
        return count
    }

    private fun slotValueOrNull(
        slotInterfaceType: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
        slotConstantName: String,
    ): Int? =
        cachedAbiSlotBindings(slotInterfaceType, typesByQualifiedName, semanticHelpers, abiSlotBindingCache, abiMemberCountCache)
            .firstOrNull { binding -> binding.constantName == slotConstantName }
            ?.slot

    private fun planInstanceMemberBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
    ): List<KotlinProjectionInstanceMemberBinding> {
        if (type.kind != WinRTTypeKind.RuntimeClass && type.kind != WinRTTypeKind.Interface) {
            return emptyList()
        }
        val candidateInterfaces = buildList {
            if (type.kind == WinRTTypeKind.Interface) {
                add(type.qualifiedName)
            } else {
                type.defaultInterfaceName?.let(::add)
            }
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()

        return buildList {
            type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                resolveInstanceMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    semanticHelpers = semanticHelpers,
                    abiSlotBindingCache = abiSlotBindingCache,
                    abiMemberCountCache = abiMemberCountCache,
                    bindingName = method.abiSlotConstantName(type.methods),
                    slotConstantName = method.abiSlotConstantName(type.methods),
                    returnBinding = classifyAbiTypeBinding(method.projectedKotlinReturnTypeName(), type.namespace, typesByQualifiedName),
                    parameterBindings = method.projectedKotlinParameters().map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(parameter.typeName, type.namespace, typesByQualifiedName),
                            category = metadataParameterCategoryFor(parameter),
                        )
                    },
                    signatureDescriptor = signatureDescriptor,
                    marshalerPlanDescriptor = semanticHelpers.abiMarshalerPlanDescriptor(method),
                    suppressHResultCheckResolver = { interfaceType ->
                        interfaceType.methods
                            .firstOrNull { it.projectionSignatureKey() == method.projectionSignatureKey() }
                            ?.let(semanticHelpers::isNoException)
                            ?: semanticHelpers.isNoException(method)
                    },
                    signatureMatcher = { interfaceType ->
                        interfaceType.methods.any { it.projectionSignatureKey() == method.projectionSignatureKey() }
                    },
                    ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                        fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                    },
                    slotConstantNameResolver = { interfaceType ->
                        interfaceType.methods
                            .firstOrNull { it.projectionSignatureKey() == method.projectionSignatureKey() }
                            ?.abiSlotConstantName(interfaceType.methods)
                    },
                )?.let(::add)
            }
            type.properties.filterNot { it.isStatic }.forEach { property ->
                val propertyTypeName = property.projectedPropertyTypeName(type.qualifiedName, typesByQualifiedName)
                if (property.hasNativeProjectionGetterAccessor()) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(propertyTypeName, type.namespace, typesByQualifiedName),
                        parameterBindings = emptyList(),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionGetterAccessor() }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionGetterAccessor()
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(propertyTypeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionSetterAccessor() }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionSetterAccessor()
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
            }
            type.events.filterNot { it.isStatic }.forEach { event ->
                if (event.hasNativeProjectionAddAccessor()) {
                    (
                        resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        slotConstantName = "${event.name.uppercase()}_ADD_SLOT",
                        returnBinding = classifyAbiTypeBinding(
                            "Windows.Foundation.EventRegistrationToken",
                            type.namespace,
                            typesByQualifiedName,
                        ),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "handler",
                                typeBinding = classifyAbiTypeBinding(event.delegateTypeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheck = false,
                        signatureMatcher = { interfaceType ->
                            interfaceType.events.any {
                                it.name == event.name &&
                                    it.hasNativeProjectionAddAccessor()
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(
                                ownerInterface,
                                slotInterface,
                                defaultInterfaceName = if (ownerInterface.contains('<')) null else candidateInterfaces.firstOrNull(),
                                typesByQualifiedName,
                                semanticHelpers,
                            )
                        },
                        ) ?: resolveMappedCollectionEventBinding(
                            candidateInterfaces = candidateInterfaces,
                            typesByQualifiedName = typesByQualifiedName,
                            semanticHelpers = semanticHelpers,
                            abiSlotBindingCache = abiSlotBindingCache,
                            abiMemberCountCache = abiMemberCountCache,
                            event = event,
                            slotConstantName = "${event.name.uppercase()}_ADD_SLOT",
                            returnBinding = classifyAbiTypeBinding(
                                "Windows.Foundation.EventRegistrationToken",
                                type.namespace,
                                typesByQualifiedName,
                            ),
                            parameterBindings = listOf(
                                KotlinProjectionAbiParameterBinding(
                                    name = "handler",
                                    typeBinding = classifyAbiTypeBinding(event.delegateTypeName, type.namespace, typesByQualifiedName),
                                ),
                            ),
                            suppressHResultCheck = false,
                        )
                        )?.let(::add)
                }
                if (event.hasNativeProjectionRemoveAccessor()) {
                    (
                        resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        slotConstantName = "${event.name.uppercase()}_REMOVE_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "token",
                                typeBinding = classifyAbiTypeBinding(
                                    "Windows.Foundation.EventRegistrationToken",
                                    type.namespace,
                                    typesByQualifiedName,
                                ),
                            ),
                        ),
                        suppressHResultCheck = true,
                        signatureMatcher = { interfaceType ->
                            interfaceType.events.any {
                                it.name == event.name &&
                                    it.hasNativeProjectionRemoveAccessor()
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(
                                ownerInterface,
                                slotInterface,
                                defaultInterfaceName = if (ownerInterface.contains('<')) null else candidateInterfaces.firstOrNull(),
                                typesByQualifiedName,
                                semanticHelpers,
                            )
                        },
                        ) ?: resolveMappedCollectionEventBinding(
                            candidateInterfaces = candidateInterfaces,
                            typesByQualifiedName = typesByQualifiedName,
                            semanticHelpers = semanticHelpers,
                            abiSlotBindingCache = abiSlotBindingCache,
                            abiMemberCountCache = abiMemberCountCache,
                            event = event,
                            slotConstantName = "${event.name.uppercase()}_REMOVE_SLOT",
                            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                            parameterBindings = listOf(
                                KotlinProjectionAbiParameterBinding(
                                    name = "token",
                                    typeBinding = classifyAbiTypeBinding(
                                        "Windows.Foundation.EventRegistrationToken",
                                        type.namespace,
                                        typesByQualifiedName,
                                    ),
                                ),
                            ),
                            suppressHResultCheck = true,
                        )
                        )?.let(::add)
                }
            }
        }.distinctBy(KotlinProjectionInstanceMemberBinding::bindingName)
    }

    private fun planStaticMemberBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
    ): List<KotlinProjectionStaticMemberBinding> {
        if (type.kind != WinRTTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = type.activation.staticInterfaceNames.distinct()
        return buildList {
            val staticInterfaces = candidateInterfaces.mapNotNull(typesByQualifiedName::get)
            val staticMethodNameCounts = staticInterfaces
                .flatMap { staticInterface -> staticInterface.methods.filter(WinRTMethodDefinition::isProjectedCallableMethod) }
                .groupingBy(WinRTMethodDefinition::name)
                .eachCount()
            staticInterfaces.flatMap { staticInterface ->
                staticInterface.methods
                    .filter(WinRTMethodDefinition::isProjectedCallableMethod)
                    .map { method -> staticInterface to method.copy(isStatic = true) }
            }.forEach { (staticInterface, method) ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                val bindingSlotConstantName = method.staticBindingSlotConstantName(staticInterface.methods, staticMethodNameCounts)
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    semanticHelpers = semanticHelpers,
                    abiSlotBindingCache = abiSlotBindingCache,
                    abiMemberCountCache = abiMemberCountCache,
                    bindingName = "STATIC_$bindingSlotConstantName",
                    slotConstantName = method.abiSlotConstantName(staticInterface.methods),
                    returnBinding = classifyAbiTypeBinding(signatureDescriptor.projectionReturnTypeName, type.namespace, typesByQualifiedName),
                    parameterBindings = signatureDescriptor.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.escapedName,
                            typeBinding = classifyAbiTypeBinding(parameter.projectionTypeName, type.namespace, typesByQualifiedName),
                            category = parameter.category,
                        )
                    },
                    signatureDescriptor = signatureDescriptor,
                    marshalerPlanDescriptor = semanticHelpers.abiMarshalerPlanDescriptor(method),
                    suppressHResultCheckResolver = { interfaceType ->
                        interfaceType.methods
                            .firstOrNull { it.projectionSignatureIgnoringStaticKey() == method.projectionSignatureIgnoringStaticKey() }
                            ?.let(semanticHelpers::isNoException)
                            ?: semanticHelpers.isNoException(method)
                    },
                    signatureMatcher = { interfaceType ->
                        interfaceType.qualifiedName == staticInterface.qualifiedName
                    },
                    slotConstantNameResolver = { interfaceType ->
                        interfaceType.methods
                            .firstOrNull { it.projectionSignatureIgnoringStaticKey() == method.projectionSignatureIgnoringStaticKey() }
                            ?.abiSlotConstantName(interfaceType.methods)
                    },
                )?.let(::add)
            }
            staticInterfaces.flatMap { staticInterface ->
                staticInterface.properties.map { property -> staticInterface to property.copy(isStatic = true) }
            }.forEach { (staticInterface, property) ->
                val propertyTypeName = property.projectedPropertyTypeName(staticInterface.qualifiedName, typesByQualifiedName)
                if (property.hasNativeProjectionGetterAccessor()) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        bindingName = "STATIC_${property.name.uppercase()}_GETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(propertyTypeName, type.namespace, typesByQualifiedName),
                        parameterBindings = emptyList(),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionGetterAccessor() }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.qualifiedName == staticInterface.qualifiedName
                        },
                    )?.let(::add)
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        bindingName = "STATIC_${property.name.uppercase()}_SETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(propertyTypeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.hasNativeProjectionSetterAccessor() }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.qualifiedName == staticInterface.qualifiedName
                        },
                    )?.let(::add)
                }
            }
            staticInterfaces.flatMap { staticInterface ->
                staticInterface.events.map { event -> staticInterface to event.copy(isStatic = true) }
            }.forEach { (staticInterface, event) ->
                if (event.hasNativeProjectionAddAccessor()) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        bindingName = "STATIC_${event.name.uppercase()}_ADD_SLOT",
                        slotConstantName = "${event.name.uppercase()}_ADD_SLOT",
                        returnBinding = classifyAbiTypeBinding(
                            "Windows.Foundation.EventRegistrationToken",
                            type.namespace,
                            typesByQualifiedName,
                        ),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "handler",
                                typeBinding = classifyAbiTypeBinding(event.delegateTypeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheck = false,
                        signatureMatcher = { interfaceType ->
                            interfaceType.qualifiedName == staticInterface.qualifiedName &&
                                interfaceType.events.any {
                                    it.projectionSignatureIgnoringStaticKey() == event.projectionSignatureIgnoringStaticKey() &&
                                        it.hasNativeProjectionAddAccessor()
                                }
                        },
                        slotConstantNameResolver = { interfaceType ->
                            interfaceType.events
                                .firstOrNull {
                                    it.projectionSignatureIgnoringStaticKey() == event.projectionSignatureIgnoringStaticKey() &&
                                        it.hasNativeProjectionAddAccessor()
                                }
                                ?.let { "${it.name.uppercase()}_ADD_SLOT" }
                        },
                    )?.let(::add)
                }
                if (event.hasNativeProjectionRemoveAccessor()) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        semanticHelpers = semanticHelpers,
                        abiSlotBindingCache = abiSlotBindingCache,
                        abiMemberCountCache = abiMemberCountCache,
                        bindingName = "STATIC_${event.name.uppercase()}_REMOVE_SLOT",
                        slotConstantName = "${event.name.uppercase()}_REMOVE_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "token",
                                typeBinding = classifyAbiTypeBinding(
                                    "Windows.Foundation.EventRegistrationToken",
                                    type.namespace,
                                    typesByQualifiedName,
                                ),
                            ),
                        ),
                        suppressHResultCheck = true,
                        signatureMatcher = { interfaceType ->
                            interfaceType.qualifiedName == staticInterface.qualifiedName &&
                                interfaceType.events.any {
                                    it.projectionSignatureIgnoringStaticKey() == event.projectionSignatureIgnoringStaticKey() &&
                                        it.hasNativeProjectionRemoveAccessor()
                                }
                        },
                        slotConstantNameResolver = { interfaceType ->
                            interfaceType.events
                                .firstOrNull {
                                    it.projectionSignatureIgnoringStaticKey() == event.projectionSignatureIgnoringStaticKey() &&
                                        it.hasNativeProjectionRemoveAccessor()
                                }
                                ?.let { "${it.name.uppercase()}_REMOVE_SLOT" }
                        },
                    )?.let(::add)
                }
            }
        }.distinctBy(KotlinProjectionStaticMemberBinding::bindingName)
    }

    private fun planReadOnlyCollectionBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): List<KotlinProjectionReadOnlyCollectionBinding> {
        if (type.kind != WinRTTypeKind.RuntimeClass && type.kind != WinRTTypeKind.Interface) {
            return emptyList()
        }
        val mutableBindings = planMutableCollectionBindings(type, typesByQualifiedName, semanticHelpers)
        val candidateInterfaces = collectionInterfaceTraversalRoots(type, semanticHelpers)

        val bindings = candidateInterfaces.flatMap { (ownerInterface, currentInterface) ->
            collectReadOnlyCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName?.takeIf { type.kind == WinRTTypeKind.RuntimeClass },
                currentInterfaceName = currentInterface,
                currentNamespace = currentInterface.substringBeforeLast('.', type.namespace),
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
            )
        }.distinctBy { binding ->
            buildString {
                append(binding.kind)
                append('|')
                append(binding.elementBinding?.resolvedTypeName)
                append('|')
                append(binding.keyBinding?.resolvedTypeName)
                append('|')
                append(binding.valueBinding?.resolvedTypeName)
            }
        }

        val vectorElements = bindings
            .filter { it.kind == KotlinProjectionReadOnlyCollectionKind.VectorView }
            .mapNotNull { it.elementBinding?.resolvedTypeName }
            .toSet()
        val mapEntryPairs = bindings
            .filter { it.kind == KotlinProjectionReadOnlyCollectionKind.MapView }
            .map { it.keyBinding?.resolvedTypeName to it.valueBinding?.resolvedTypeName }
            .toSet()

        return bindings.filterNot { binding ->
            when (binding.kind) {
                KotlinProjectionReadOnlyCollectionKind.Iterable -> {
                    val elementBinding = binding.elementBinding ?: return@filterNot false
                    elementBinding.resolvedTypeName in vectorElements
                }
	    else -> false
	}
        }.filterNot { binding -> binding.isRedundantReadOnlyCollectionBinding(mutableBindings) }
    }

    private fun planMutableCollectionBindings(
        type: WinRTTypeDefinition,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): List<KotlinProjectionMutableCollectionBinding> {
        if (type.kind != WinRTTypeKind.RuntimeClass && type.kind != WinRTTypeKind.Interface) {
            return emptyList()
        }
        val candidateInterfaces = collectionInterfaceTraversalRoots(type, semanticHelpers)

        return candidateInterfaces.flatMap { (ownerInterface, currentInterface) ->
            collectMutableCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName?.takeIf { type.kind == WinRTTypeKind.RuntimeClass },
                currentInterfaceName = currentInterface,
                currentNamespace = currentInterface.substringBeforeLast('.', type.namespace),
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
            )
        }.distinctBy { binding ->
            buildString {
                append(binding.kind)
                append('|')
                append(binding.elementBinding?.resolvedTypeName)
                append('|')
                append(binding.keyBinding?.resolvedTypeName)
                append('|')
                append(binding.valueBinding?.resolvedTypeName)
            }
        }
    }

    private fun collectionInterfaceTraversalRoots(
        type: WinRTTypeDefinition,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): List<Pair<String, String>> {
        val ownerInterfaces = buildList {
            if (type.kind == WinRTTypeKind.Interface) {
                add(type.qualifiedName)
            } else {
                type.defaultInterfaceName?.let(::add)
            }
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()
        val requiredInterfaces = semanticHelpers.requiredInterfaceAugmentationDescriptor(type).requiredInterfaceNames
        return ownerInterfaces.flatMap { ownerInterface ->
            buildList {
                add(ownerInterface to ownerInterface)
                requiredInterfaces.forEach { requiredInterface ->
                    add(ownerInterface to requiredInterface)
                }
            }
        }
    }

    private fun collectMutableCollectionBindings(
        ownerInterface: String,
        defaultInterfaceName: String?,
        currentInterfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: MutableSet<String>,
    ): List<KotlinProjectionMutableCollectionBinding> {
        val rawInterfaceName = currentInterfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        if (!visiting.add("$ownerInterface->$currentInterfaceName")) {
            return emptyList()
        }
        return try {
            buildList {
                mutableCollectionBindingFor(
                    ownerInterface = ownerInterface,
                    defaultInterfaceName = defaultInterfaceName,
                    interfaceName = currentInterfaceName,
                    currentNamespace = currentNamespace,
                    typesByQualifiedName = typesByQualifiedName,
                )?.let(::add)
                val interfaceType = typesByQualifiedName[resolvedInterfaceName]
                interfaceType?.implementedInterfaces?.forEach { implemented ->
                    val implementedInterfaceName = substitutedImplementedInterfaceName(currentInterfaceName, implemented)
                    addAll(
                        collectMutableCollectionBindings(
                            ownerInterface = ownerInterface,
                            defaultInterfaceName = defaultInterfaceName,
                            currentInterfaceName = implementedInterfaceName,
                            currentNamespace = interfaceType.namespace,
                            typesByQualifiedName = typesByQualifiedName,
                            visiting = visiting,
                        ),
                    )
                }
            }
        } finally {
            visiting.remove("$ownerInterface->$currentInterfaceName")
        }
    }

    private fun mutableCollectionBindingFor(
        ownerInterface: String,
        defaultInterfaceName: String?,
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): KotlinProjectionMutableCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
        if (isUninstantiatedGenericMappedCollectionDefinition(interfaceName, resolvedInterfaceName, typesByQualifiedName)) {
            return null
        }
        val genericArguments = if ('<' in interfaceName && interfaceName.endsWith('>')) {
            splitGenericArguments(interfaceName.substringAfter('<').substringBeforeLast('>'))
                .map { argument -> classifyAbiTypeBinding(argument, currentNamespace, typesByQualifiedName) }
        } else if (mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVector) {
            listOf(bindableObjectElementBinding())
        } else {
            emptyList()
        }
        val collectionKind = mappedType.mutableCollectionKind ?: return null
        return buildMutableCollectionBinding(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterface,
            ownerCachePropertyName = if (mappedType.isBindableCollectionMapping()) {
                "_inner"
            } else {
                ownerCachePropertyName(interfaceName, defaultInterfaceName)
            },
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = true,
            bindingLocationLabel = "owner",
        )
    }

    private fun collectReadOnlyCollectionBindings(
        ownerInterface: String,
        defaultInterfaceName: String?,
        currentInterfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: MutableSet<String>,
    ): List<KotlinProjectionReadOnlyCollectionBinding> {
        val rawInterfaceName = currentInterfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        if (!visiting.add("$ownerInterface->$currentInterfaceName")) {
            return emptyList()
        }
        return try {
            val bindings = buildList {
                readOnlyCollectionBindingFor(
                    ownerInterface = ownerInterface,
                    defaultInterfaceName = defaultInterfaceName,
                    interfaceName = currentInterfaceName,
                    currentNamespace = currentNamespace,
                    typesByQualifiedName = typesByQualifiedName,
                )?.let(::add)
                val interfaceType = typesByQualifiedName[resolvedInterfaceName]
                interfaceType?.implementedInterfaces?.forEach { implemented ->
                    val implementedInterfaceName = substitutedImplementedInterfaceName(currentInterfaceName, implemented)
                    addAll(
                        collectReadOnlyCollectionBindings(
                            ownerInterface = ownerInterface,
                            defaultInterfaceName = defaultInterfaceName,
                            currentInterfaceName = implementedInterfaceName,
                            currentNamespace = interfaceType.namespace,
                            typesByQualifiedName = typesByQualifiedName,
                            visiting = visiting,
                        ),
                    )
                }
            }
            bindings
        } finally {
            visiting.remove("$ownerInterface->$currentInterfaceName")
        }
    }

    private fun readOnlyCollectionBindingFor(
        ownerInterface: String,
        defaultInterfaceName: String?,
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): KotlinProjectionReadOnlyCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
        if (isUninstantiatedGenericMappedCollectionDefinition(interfaceName, resolvedInterfaceName, typesByQualifiedName)) {
            return null
        }
        val genericArguments = if ('<' in interfaceName && interfaceName.endsWith('>')) {
            splitGenericArguments(interfaceName.substringAfter('<').substringBeforeLast('>'))
                .map { argument -> classifyAbiTypeBinding(argument, currentNamespace, typesByQualifiedName) }
        } else if (mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
            mappedType.abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVectorView
        ) {
            listOf(bindableObjectElementBinding())
        } else {
            emptyList()
        }
        val collectionKind = mappedType.readOnlyCollectionKind ?: return null
        return buildReadOnlyCollectionBinding(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterface,
            ownerCachePropertyName = if (mappedType.isBindableCollectionMapping()) {
                "_inner"
            } else {
                ownerCachePropertyName(interfaceName, defaultInterfaceName)
            },
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = true,
            bindingLocationLabel = "owner",
        )
    }

    private fun buildReadOnlyCollectionBinding(
        collectionKind: KotlinProjectionReadOnlyCollectionKind,
        ownerInterfaceQualifiedName: String,
        ownerCachePropertyName: String,
        slotInterfaceQualifiedName: String,
        delegatePropertyName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
        errorContext: String,
        requireSupportedBinding: Boolean,
        bindingLocationLabel: String = "",
    ): KotlinProjectionReadOnlyCollectionBinding? =
        createReadOnlyCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
            ownerCachePropertyName = ownerCachePropertyName,
            slotInterfaceQualifiedName = slotInterfaceQualifiedName,
            delegatePropertyName = delegatePropertyName,
            typeArguments = typeArguments,
            errorContext = errorContext,
            requireSupportedBinding = requireSupportedBinding,
            bindingLocationLabel = bindingLocationLabel,
        )

    private fun bindableObjectElementBinding(): KotlinProjectionAbiTypeBinding =
        KotlinProjectionAbiTypeBinding(
            kind = KotlinProjectionAbiValueKind.Object,
            typeName = "Any?",
            resolvedTypeName = "System.Object",
        )

    private fun isUninstantiatedGenericMappedCollectionDefinition(
        interfaceName: String,
        resolvedInterfaceName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): Boolean {
        if ('<' in interfaceName) {
            return false
        }
        val type = typesByQualifiedName[resolvedInterfaceName] ?: return false
        return type.genericParameterCount > 0 && mappedTypeByAbiName(resolvedInterfaceName)?.isBindableCollectionMapping() != true
    }

    private fun KotlinProjectionMappedType.isBindableCollectionMapping(): Boolean =
        abiValueKind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
            abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVectorView ||
            abiValueKind == KotlinProjectionAbiValueKind.MappedBindableVector

    private fun buildMutableCollectionBinding(
        collectionKind: KotlinProjectionMutableCollectionKind,
        ownerInterfaceQualifiedName: String,
        ownerCachePropertyName: String,
        slotInterfaceQualifiedName: String,
        delegatePropertyName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
        errorContext: String,
        requireSupportedBinding: Boolean,
        bindingLocationLabel: String = "",
    ): KotlinProjectionMutableCollectionBinding? =
        createMutableCollectionBindingPlan(
            collectionKind = collectionKind,
            ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
            ownerCachePropertyName = ownerCachePropertyName,
            slotInterfaceQualifiedName = slotInterfaceQualifiedName,
            delegatePropertyName = delegatePropertyName,
            typeArguments = typeArguments,
            errorContext = errorContext,
            requireSupportedBinding = requireSupportedBinding,
            bindingLocationLabel = bindingLocationLabel,
        )

    private fun substitutedImplementedInterfaceName(
        currentInterfaceName: String,
        implemented: WinRTInterfaceImplementationDefinition,
    ): String {
        val currentGenericArguments = genericArgumentTypeRefs(currentInterfaceName)
        if (currentGenericArguments.isEmpty()) {
            return implemented.interfaceName
        }
        return implemented.interfaceType
            .substituteTypeParameters(currentGenericArguments)
            .typeName
    }

    private fun genericArgumentTypeRefs(typeName: String): List<WinRTTypeRef> {
        val trimmed = typeName.trim()
        if ('<' !in trimmed || !trimmed.endsWith('>')) {
            return emptyList()
        }
        return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
            .map(WinRTTypeRef::fromDisplayName)
    }

    private fun resolveInstanceMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
        bindingName: String? = null,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureDescriptor: WinRTSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
        suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
        suppressHResultCheckResolver: (WinRTTypeDefinition) -> Boolean = { suppressHResultCheck },
        signatureMatcher: (WinRTTypeDefinition) -> Boolean,
        ownerCachePropertyNameResolver: (String, String) -> String = { ownerInterface, _ ->
            ownerCachePropertyName(ownerInterface, candidateInterfaces.firstOrNull())
        },
        slotConstantNameResolver: (WinRTTypeDefinition) -> String? = { null },
    ): KotlinProjectionInstanceMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            val slotInterfaceType = typesByQualifiedName.getValue(slotInterfaceQualifiedName)
            val resolvedSlotConstantName = slotConstantNameResolver(slotInterfaceType) ?: slotConstantName
            return KotlinProjectionInstanceMemberBinding(
                bindingName = bindingName ?: slotConstantName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerCachePropertyName = ownerCachePropertyNameResolver(candidateInterface, slotInterfaceQualifiedName),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = resolvedSlotConstantName,
                slot = slotValueOrNull(
                    slotInterfaceType,
                    typesByQualifiedName,
                    semanticHelpers,
                    abiSlotBindingCache,
                    abiMemberCountCache,
                    resolvedSlotConstantName,
                ),
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
                projectedAttributes = slotInterfaceType.projectedAttributes()
                    .filter(WinRTProjectedAttributeDescriptor::isPlatformAttribute),
                suppressHResultCheck = suppressHResultCheckResolver(slotInterfaceType),
            )
        }
        return null
    }

    private fun resolveMappedCollectionEventBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
        event: WinRTEventDefinition,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        suppressHResultCheck: Boolean,
    ): KotlinProjectionInstanceMemberBinding? {
        val slotInterfaceCandidates = when (event.name) {
            "VectorChanged" -> listOf(
                "Windows.Foundation.Collections.IObservableVector",
                "Microsoft.UI.Xaml.Interop.IBindableObservableVector",
                "Windows.UI.Xaml.Interop.IBindableObservableVector",
            )
            "MapChanged" -> listOf("Windows.Foundation.Collections.IObservableMap")
            else -> return null
        }
        val ownerInterface = candidateInterfaces.firstOrNull { candidate ->
            candidate.normalizedRawTypeName() in slotInterfaceCandidates
        } ?: return null
        val slotInterfaceQualifiedName = ownerInterface.normalizedRawTypeName()
        val slotInterfaceType = typesByQualifiedName[slotInterfaceQualifiedName]
            ?: typesByQualifiedName[ownerInterface]
        return KotlinProjectionInstanceMemberBinding(
            bindingName = slotConstantName,
            ownerInterfaceQualifiedName = ownerInterface,
            ownerCachePropertyName = ownerCachePropertyName(ownerInterface, defaultInterfaceName = null),
            slotInterfaceQualifiedName = slotInterfaceQualifiedName,
            slotConstantName = slotConstantName,
            slot = slotInterfaceType?.let {
                slotValueOrNull(
                    it,
                    typesByQualifiedName,
                    semanticHelpers,
                    abiSlotBindingCache,
                    abiMemberCountCache,
                    slotConstantName,
                )
            },
            returnBinding = returnBinding,
            parameterBindings = parameterBindings,
            projectedAttributes = slotInterfaceType
                ?.projectedAttributes()
                .orEmpty()
                .filter(WinRTProjectedAttributeDescriptor::isPlatformAttribute),
            suppressHResultCheck = suppressHResultCheck,
        )
    }

    private fun String.normalizedRawTypeName(): String =
        WinRTTypeRef.fromDisplayName(this)
            .normalized()
            .let { type -> type.qualifiedName ?: type.typeName.substringBefore('<').removeSuffix("?") }

    private fun resolveStaticMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
        abiSlotBindingCache: MutableMap<String, List<KotlinProjectionAbiSlotBinding>>,
        abiMemberCountCache: MutableMap<String, Int>,
        bindingName: String,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureDescriptor: WinRTSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
        suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
        suppressHResultCheckResolver: (WinRTTypeDefinition) -> Boolean = { suppressHResultCheck },
        signatureMatcher: (WinRTTypeDefinition) -> Boolean,
        slotConstantNameResolver: (WinRTTypeDefinition) -> String? = { null },
    ): KotlinProjectionStaticMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            val slotInterfaceType = typesByQualifiedName.getValue(slotInterfaceQualifiedName)
            val resolvedSlotConstantName = slotConstantNameResolver(slotInterfaceType) ?: slotConstantName
            return KotlinProjectionStaticMemberBinding(
                bindingName = bindingName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerAccessorName = staticOwnerAccessorName(candidateInterface),
                ownerCachePropertyName = staticOwnerCachePropertyName(candidateInterface),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = resolvedSlotConstantName,
                slot = slotValueOrNull(
                    slotInterfaceType,
                    typesByQualifiedName,
                    semanticHelpers,
                    abiSlotBindingCache,
                    abiMemberCountCache,
                    resolvedSlotConstantName,
                ),
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
                projectedAttributes = slotInterfaceType.projectedAttributes()
                    .filter(WinRTProjectedAttributeDescriptor::isPlatformAttribute),
                suppressHResultCheck = suppressHResultCheckResolver(slotInterfaceType),
            )
        }
        return null
    }

    internal fun classifyAbiTypeBinding(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        includeDelegateInvokeShape: Boolean = true,
    ): KotlinProjectionAbiTypeBinding {
        val normalizedType = WinRTTypeRef
            .fromDisplayName(redirectedWinAppSdkAbiTypeExpression(typeName, useWinAppSdkTypeRedirects, typesByQualifiedName))
            .normalized()
        val trimmedTypeName = normalizedType.typeName
        val rawTypeName = when (normalizedType.kind) {
            WinRTTypeRefKind.Named -> (normalizedType.qualifiedName ?: trimmedTypeName.substringBefore('<')).removeSuffix("?")
            WinRTTypeRefKind.Array -> "Array"
            else -> trimmedTypeName
        }
        val typeArguments = when (normalizedType.kind) {
            WinRTTypeRefKind.Named -> normalizedType.typeArguments.map { argument ->
                    classifyAbiTypeBinding(
                        typeName = argument.typeName,
                        currentNamespace = currentNamespace,
                        typesByQualifiedName = typesByQualifiedName,
                        includeDelegateInvokeShape = false,
                    )
                }
            WinRTTypeRefKind.Array -> listOf(
                classifyAbiTypeBinding(
                    typeName = (normalizedType.elementType ?: WinRTTypeRef.unknown()).typeName,
                    currentNamespace = currentNamespace,
                    typesByQualifiedName = typesByQualifiedName,
                    includeDelegateInvokeShape = false,
                ),
            )
            else -> emptyList()
        }
        val resolvedTypeName = qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
        val resolvedType = typesByQualifiedName[resolvedTypeName]
        val eventHandlerKind = winRTEventHandlerKindForTypeName(resolvedTypeName) ?: winRTEventHandlerKindForTypeName(rawTypeName)
        val mappedType = mappedTypeByAbiName(resolvedTypeName) ?: mappedTypeByAbiName(rawTypeName)
        val isProjectedKeyValuePair = rawTypeName == "Map.Entry" || rawTypeName == "kotlin.collections.Map.Entry"
        val fundamentalType = winRTFundamentalTypeForName(rawTypeName)
        val kind = if (isWinRTVoidTypeName(rawTypeName)) {
            KotlinProjectionAbiValueKind.Unit
        } else if (fundamentalType != null) {
            fundamentalType.toProjectionAbiValueKind()
        } else if (isWinRTGuidTypeName(rawTypeName)) {
            KotlinProjectionAbiValueKind.GuidValue
        } else when (trimmedTypeName) {
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.InspectableReference
            "io.github.composefluent.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            "io.github.composefluent.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            else -> when {
                normalizedType.kind == WinRTTypeRefKind.GenericTypeParameter ||
                    normalizedType.kind == WinRTTypeRefKind.MethodTypeParameter -> KotlinProjectionAbiValueKind.GenericParameter
                normalizedType.kind == WinRTTypeRefKind.Array -> KotlinProjectionAbiValueKind.Array
                isWinRTObjectTypeName(rawTypeName) -> KotlinProjectionAbiValueKind.Object
                isProjectedKeyValuePair -> KotlinProjectionAbiValueKind.MappedKeyValuePair
                rawTypeName == "Windows.Foundation.IPropertyValue" ||
                    resolvedTypeName == "Windows.Foundation.IPropertyValue" -> KotlinProjectionAbiValueKind.PropertyValue
                mappedType?.abiValueKind != null -> mappedType.abiValueKind
                eventHandlerKind != null -> KotlinProjectionAbiValueKind.Delegate
                resolvedType != null -> when (resolvedType.kind) {
                    WinRTTypeKind.Interface -> KotlinProjectionAbiValueKind.ProjectedInterface
                    WinRTTypeKind.RuntimeClass -> KotlinProjectionAbiValueKind.ProjectedRuntimeClass
                    WinRTTypeKind.Enum -> KotlinProjectionAbiValueKind.Enum
                    WinRTTypeKind.Struct -> KotlinProjectionAbiValueKind.Struct
                    WinRTTypeKind.Delegate -> KotlinProjectionAbiValueKind.Delegate
                    WinRTTypeKind.Unknown -> KotlinProjectionAbiValueKind.Unsupported
                }
                rawTypeName.isProjectedWinRTInterfaceReferenceName() -> KotlinProjectionAbiValueKind.ProjectedInterface
                mappedType != null -> KotlinProjectionAbiValueKind.Unsupported
                else -> KotlinProjectionAbiValueKind.Unsupported
            }
        }
        val delegateInvokeShape = if (includeDelegateInvokeShape && kind == KotlinProjectionAbiValueKind.Delegate) {
            if (resolvedType != null) {
                val invokeMethod = requireDelegateInvokeMethod(resolvedType)
                val delegateGenericArguments = typeArguments
                KotlinProjectionDelegateInvokeShape(
                    interfaceId = resolvedType.iid,
                    parameterBindings = invokeMethod.parameters.map { parameter ->
                        KotlinProjectionAbiParameterBinding(
                            name = parameter.name,
                            typeBinding = classifyAbiTypeBinding(
                                typeName = parameter.typeName,
                                currentNamespace = resolvedType.namespace,
                                typesByQualifiedName = typesByQualifiedName,
                                includeDelegateInvokeShape = false,
                            ).withDelegateGenericArgumentProjection(delegateGenericArguments),
                        )
                    },
                    returnBinding = classifyAbiTypeBinding(
                        typeName = invokeMethod.returnTypeName,
                        currentNamespace = resolvedType.namespace,
                        typesByQualifiedName = typesByQualifiedName,
                        includeDelegateInvokeShape = false,
                    ).withDelegateGenericArgumentProjection(delegateGenericArguments),
                )
            } else {
                syntheticEventHandlerDelegateInvokeShape(eventHandlerKind, resolvedTypeName, typeArguments)
            }
        } else {
            null
        }
        val structFieldBindings = if (kind == KotlinProjectionAbiValueKind.Struct && resolvedType?.kind == WinRTTypeKind.Struct) {
            resolvedType.fields.map { field ->
                classifyAbiTypeBinding(
                    typeName = field.typeName,
                    currentNamespace = resolvedType.namespace,
                    typesByQualifiedName = typesByQualifiedName,
                    includeDelegateInvokeShape = false,
                )
            }
        } else {
            emptyList()
        }
        val interfaceId = when (resolvedType?.kind) {
            WinRTTypeKind.RuntimeClass -> resolvedType.defaultInterfaceName
                ?.let { defaultInterfaceName ->
                    typesByQualifiedName[defaultInterfaceName]
                        ?: typesByQualifiedName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                }
                ?.iid
            else -> resolvedType?.iid
        } ?: externalProjectedInterfaceId(rawTypeName, resolvedTypeName)
            ?: mappedReferenceGenericInterfaceId(kind)
        val isNullableDisplayName = typeName.trim().endsWith("?")
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = if (isNullableDisplayName) trimmedTypeName.withNullableSuffix() else trimmedTypeName,
            resolvedTypeName = if (isNullableDisplayName) resolvedTypeName.withNullableSuffix() else resolvedTypeName,
            sourceTypeKind = resolvedType?.kind,
            abiSize = resolvedType?.abiSize,
            abiAlignment = resolvedType?.abiAlignment,
            interfaceId = interfaceId,
            enumUnderlyingType = resolvedType?.enumUnderlyingType,
            delegateInvokeShape = delegateInvokeShape,
            typeArguments = typeArguments,
            structFieldBindings = structFieldBindings,
        )
    }

    private fun String.withNullableSuffix(): String =
        if (trim().endsWith("?")) this else "$this?"

    private fun syntheticEventHandlerDelegateInvokeShape(
        eventHandlerKind: WinRTEventHandlerKind?,
        eventHandlerTypeName: String,
        typeArguments: List<KotlinProjectionAbiTypeBinding>,
    ): KotlinProjectionDelegateInvokeShape? {
        val interfaceId = when (eventHandlerKind) {
            WinRTEventHandlerKind.EventHandler -> Guid("C50898F6-C536-5F47-8583-8B2C2438A13B")
            WinRTEventHandlerKind.TypedEventHandler -> Guid("9DE1C535-6AE1-11E0-84E1-18A905BCC53F")
            WinRTEventHandlerKind.VectorChangedEventHandler -> Guid("0C051752-9FBF-4C70-AA0C-0E4C82D9A761")
            WinRTEventHandlerKind.BindableVectorChangedEventHandler -> Guid("624CD4E1-D007-43B1-9C03-AF4D3E6258C4")
            WinRTEventHandlerKind.MapChangedEventHandler -> Guid("179517F3-94EE-41F8-BDDC-768A895544F3")
            else -> null
        }
        val parameters = when (eventHandlerKind) {
            WinRTEventHandlerKind.EventHandler -> listOf(
                KotlinProjectionAbiParameterBinding(
                    "sender",
                    KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                ),
                KotlinProjectionAbiParameterBinding(
                    "args",
                    typeArguments.getOrNull(0) ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T0"),
                ),
            )
            WinRTEventHandlerKind.TypedEventHandler -> listOf(
                KotlinProjectionAbiParameterBinding(
                    "sender",
                    typeArguments.getOrNull(0) ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T0"),
                ),
                KotlinProjectionAbiParameterBinding(
                    "args",
                    typeArguments.getOrNull(1) ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T1"),
                ),
            )
            WinRTEventHandlerKind.VectorChangedEventHandler -> listOf(
                KotlinProjectionAbiParameterBinding(
                    "sender",
                    KotlinProjectionAbiTypeBinding(
                        KotlinProjectionAbiValueKind.ProjectedInterface,
                        "Windows.Foundation.Collections.IObservableVector",
                        "Windows.Foundation.Collections.IObservableVector",
                        interfaceId = Guid("5917EB53-50B4-4A0D-B309-65862B3F1DBC"),
                        typeArguments = listOf(
                            typeArguments.getOrNull(0)
                                ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T0"),
                        ),
                    ),
                ),
                KotlinProjectionAbiParameterBinding(
                    "event",
                    KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                ),
            )
            WinRTEventHandlerKind.BindableVectorChangedEventHandler -> {
                val ownerTypeName = bindableObservableVectorTypeNameFor(eventHandlerTypeName)
                listOf(
                    KotlinProjectionAbiParameterBinding(
                        "vector",
                        KotlinProjectionAbiTypeBinding(
                            KotlinProjectionAbiValueKind.ProjectedInterface,
                            ownerTypeName,
                            ownerTypeName,
                            interfaceId = Guid("FE1EB536-7E7F-4F90-AC9A-474984AAE512"),
                        ),
                    ),
                    KotlinProjectionAbiParameterBinding(
                        "event",
                        KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Object, "Any", "System.Object"),
                    ),
                )
            }
            WinRTEventHandlerKind.MapChangedEventHandler -> {
                val keyBinding = typeArguments.getOrNull(0)
                    ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T0")
                val valueBinding = typeArguments.getOrNull(1)
                    ?: KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unsupported, "T1")
                listOf(
                    KotlinProjectionAbiParameterBinding(
                        "sender",
                        KotlinProjectionAbiTypeBinding(
                            KotlinProjectionAbiValueKind.ProjectedInterface,
                            "Windows.Foundation.Collections.IObservableMap",
                            "Windows.Foundation.Collections.IObservableMap",
                            interfaceId = Guid("65DF2BF5-BF39-41B5-AEBE-5D7E0513B129"),
                            typeArguments = listOf(keyBinding, valueBinding),
                        ),
                    ),
                    KotlinProjectionAbiParameterBinding(
                        "event",
                        KotlinProjectionAbiTypeBinding(
                            KotlinProjectionAbiValueKind.ProjectedInterface,
                            "Windows.Foundation.Collections.IMapChangedEventArgs",
                            "Windows.Foundation.Collections.IMapChangedEventArgs",
                            interfaceId = Guid("9939F4DF-050A-4C0F-AA60-77075F9C4777"),
                            typeArguments = listOf(keyBinding),
                        ),
                    ),
                )
            }
            else -> return null
        }
        return KotlinProjectionDelegateInvokeShape(
            interfaceId = interfaceId,
            parameterBindings = parameters,
            returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
        )
    }

    private fun bindableObservableVectorTypeNameFor(eventHandlerTypeName: String): String =
        if (eventHandlerTypeName.substringBefore('<').removeSuffix("?").startsWith("Windows.UI.Xaml.Interop.")) {
            "Windows.UI.Xaml.Interop.IBindableObservableVector"
        } else {
            "Microsoft.UI.Xaml.Interop.IBindableObservableVector"
        }

    private fun KotlinProjectionAbiTypeBinding.withDelegateGenericArgumentProjection(
        genericArguments: List<KotlinProjectionAbiTypeBinding>,
    ): KotlinProjectionAbiTypeBinding {
        if (kind != KotlinProjectionAbiValueKind.GenericParameter || genericArguments.isEmpty()) {
            return this
        }
        val index = resolvedTypeName.removePrefix("T").removePrefix("M").toIntOrNull()
            ?: typeName.removePrefix("T").removePrefix("M").toIntOrNull()
            ?: return this
        return genericArguments.getOrNull(index) ?: this
    }

    private fun mappedReferenceGenericInterfaceId(kind: KotlinProjectionAbiValueKind): Guid? =
        when (kind) {
            KotlinProjectionAbiValueKind.Reference -> IREFERENCE_GENERIC_INTERFACE_ID
            KotlinProjectionAbiValueKind.ReferenceArray -> IREFERENCE_ARRAY_GENERIC_INTERFACE_ID
            else -> null
        }

    private fun externalProjectedInterfaceId(
        rawTypeName: String,
        resolvedTypeName: String,
    ): Guid? =
        when (resolvedTypeName.substringBefore('<').removeSuffix("?")) {
            "Windows.Foundation.Collections.IObservableVector",
            rawTypeName.takeIf { it == "Windows.Foundation.Collections.IObservableVector" } ->
                Guid("5917EB53-50B4-4A0D-B309-65862B3F1DBC")
            "Microsoft.UI.Xaml.Interop.IBindableObservableVector",
            rawTypeName.takeIf { it == "Microsoft.UI.Xaml.Interop.IBindableObservableVector" },
            "Windows.UI.Xaml.Interop.IBindableObservableVector",
            rawTypeName.takeIf { it == "Windows.UI.Xaml.Interop.IBindableObservableVector" } ->
                Guid("FE1EB536-7E7F-4F90-AC9A-474984AAE512")
            else -> null
        }

    private fun qualifyTypeName(
        rawTypeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): String? {
        if (rawTypeName.isBlank()) {
            return null
        }
        if (rawTypeName in typesByQualifiedName) {
            return rawTypeName
        }
        if ('.' !in rawTypeName) {
            val qualified = "$currentNamespace.$rawTypeName"
            if (qualified in typesByQualifiedName) {
                return qualified
            }
        }
        return null
    }

    private fun isMappedCollectionInterfaceName(
        interfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
    ): Boolean {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return false
        return mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null
    }

    private fun findDeclaringInterface(
        interfaceName: String,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        visiting: MutableSet<String>,
        signatureMatcher: (WinRTTypeDefinition) -> Boolean,
    ): String? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val type = typesByQualifiedName[interfaceName]
            ?: typesByQualifiedName[rawInterfaceName]
            ?: return null
        if (type.kind != WinRTTypeKind.Interface || !visiting.add(interfaceName)) {
            return null
        }
        return try {
            val instantiatedType = type.withInstantiatedInterfaceSignature(interfaceName)
            if (signatureMatcher(instantiatedType)) {
                type.qualifiedName
            } else {
                instantiatedType.implementedInterfaces.firstNotNullOfOrNull { implemented ->
                    findDeclaringInterface(implemented.interfaceName, typesByQualifiedName, visiting, signatureMatcher)
                }
            }
        } finally {
            visiting.remove(interfaceName)
        }
    }

    private fun WinRTTypeDefinition.withInstantiatedInterfaceSignature(interfaceName: String): WinRTTypeDefinition {
        val genericArguments = genericArgumentTypeRefs(interfaceName)
        if (genericArguments.isEmpty()) {
            return this
        }
        return copy(
            implementedInterfaces = implementedInterfaces.map { implemented ->
                implemented.copy(
                    interfaceName = implemented.interfaceType
                        .substituteTypeParameters(genericArguments)
                        .typeName,
                )
            },
            methods = methods.map { method ->
                val substitutedReturnType = method.returnType.substituteTypeParameters(genericArguments)
                method.copy(
                    returnTypeName = substitutedReturnType.typeName,
                    returnTypeSignature = substitutedReturnType,
                    parameters = method.parameters.map { parameter ->
                        val substitutedParameterType = parameter.type.substituteTypeParameters(genericArguments)
                        parameter.copy(
                            typeName = substitutedParameterType.typeName,
                            typeSignature = substitutedParameterType,
                        )
                    },
                )
            },
            properties = properties.map { property ->
                val substitutedPropertyType = property.type.substituteTypeParameters(genericArguments)
                property.copy(
                    typeName = substitutedPropertyType.typeName,
                    typeSignature = substitutedPropertyType,
                )
            },
            events = events.map { event ->
                val substitutedDelegateType = event.delegateType.substituteTypeParameters(genericArguments)
                event.copy(
                    delegateTypeName = substitutedDelegateType.typeName,
                    delegateTypeSignature = substitutedDelegateType,
                )
            },
        )
    }

    private fun ownerCachePropertyName(interfaceName: String, defaultInterfaceName: String?): String =
        if (interfaceName == defaultInterfaceName) {
            "_defaultInterface"
        } else {
            "_${interfaceName.substringBefore('<').substringAfterLast('.').replaceFirstChar(Char::lowercase)}"
        }

    private fun fastAbiOwnerCachePropertyName(
        ownerInterfaceName: String,
        slotInterfaceName: String,
        defaultInterfaceName: String?,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): String {
        val slotInterface = typesByQualifiedName[slotInterfaceName.substringBefore('<')]
        val fastAbiClass = slotInterface?.let(semanticHelpers::getFastAbiClassForInterface)
        return if (fastAbiClass?.containsOtherInterface(slotInterfaceName.substringBefore('<')) == true && defaultInterfaceName != null) {
            ownerCachePropertyName(defaultInterfaceName, defaultInterfaceName)
        } else {
            ownerCachePropertyName(ownerInterfaceName, defaultInterfaceName)
        }
    }

    private fun staticOwnerAccessorName(interfaceName: String): String =
        interfaceName.substringAfterLast('.').replaceFirstChar(Char::lowercase)

    private fun staticOwnerCachePropertyName(interfaceName: String): String =
        "_${staticOwnerAccessorName(interfaceName)}"

    private fun planCompanions(type: WinRTTypeDefinition): List<KotlinProjectionCompanionKind> = buildList {
        if (shouldEmitMetadataCompanion(type)) {
            add(KotlinProjectionCompanionKind.Metadata)
        }
        if (type.kind == WinRTTypeKind.RuntimeClass && type.activation.isActivatable) {
            add(KotlinProjectionCompanionKind.ActivationFactory)
        }
        if (type.kind == WinRTTypeKind.RuntimeClass && type.activation.staticInterfaceNames.isNotEmpty()) {
            add(KotlinProjectionCompanionKind.StaticInterfaces)
        }
        if (type.kind == WinRTTypeKind.RuntimeClass && type.activation.composableFactoryInterfaceName != null) {
            add(KotlinProjectionCompanionKind.ComposableFactory)
        }
    }

    private fun shouldEmitMetadataCompanion(type: WinRTTypeDefinition): Boolean = when (type.kind) {
        WinRTTypeKind.Interface -> true
        WinRTTypeKind.RuntimeClass -> (!type.isStaticType && !type.isAttributeType) ||
            (type.isStaticType && type.activation.staticInterfaceNames.isNotEmpty())
        else -> false
    }

    private fun planVisibility(
        type: WinRTTypeDefinition,
        projectionContext: WinRTMetadataProjectionContext,
        semanticHelpers: WinRTMetadataSemanticHelpers,
    ): KotlinProjectionVisibility =
        if (
            type.isProjectionInternal ||
            (
                type.kind == WinRTTypeKind.Interface &&
                    type.isExclusiveTo &&
                    !projectionContext.publicExclusiveTo &&
                    !semanticHelpers.isCrossModuleOverridableExclusiveInterface(type)
                )
        ) {
            KotlinProjectionVisibility.Internal
        } else {
            KotlinProjectionVisibility.Public
        }

    private fun planModifiers(type: WinRTTypeDefinition): List<KotlinProjectionModifier> = buildList {
        if (type.isStaticType) {
            add(KotlinProjectionModifier.Static)
        }
        if (type.isAttributeType || (type.kind == WinRTTypeKind.RuntimeClass && type.isSealedType && !type.isStaticType)) {
            add(KotlinProjectionModifier.Sealed)
        }
    }

    private fun planSpecializations(type: WinRTTypeDefinition): List<KotlinProjectionSpecializationKind> = buildList {
        if (type.isAttributeType) {
            add(KotlinProjectionSpecializationKind.AttributeClass)
        }
        if (type.isApiContract) {
            add(KotlinProjectionSpecializationKind.ApiContract)
        }
        if (type.isExclusiveTo) {
            add(KotlinProjectionSpecializationKind.ExclusiveInterface)
        }
        if (type.isProjectionInternal) {
            add(KotlinProjectionSpecializationKind.ProjectionInternal)
        }
        if (type.isStaticType) {
            add(KotlinProjectionSpecializationKind.StaticClass)
        }
        if (isEmpty()) {
            add(KotlinProjectionSpecializationKind.None)
        }
    }

    private fun namespaceSegments(namespace: String): List<String> =
        namespace.split('.')
            .filter { it.isNotBlank() }
            .map { it.lowercase() }
}

internal fun KotlinProjectionAbiTypeBinding.describeAbiKind(): String {
    mappedTypeByAbiKind(kind)?.let { mappedType ->
        return "${mappedType.descriptionName}(${typeArguments.joinToString(",") { it.resolvedTypeName }})"
    }
    return when (kind) {
        KotlinProjectionAbiValueKind.Enum -> "Enum(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.ProjectedInterface -> "Interface(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.ProjectedRuntimeClass -> "RuntimeClass(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Struct -> "Struct(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Delegate -> "Delegate(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Array -> "Array(${typeArguments.joinToString(",") { it.describeAbiKind() }})"
        KotlinProjectionAbiValueKind.Object -> "Object(${resolvedTypeName})"
        KotlinProjectionAbiValueKind.Unsupported -> "Unsupported(${resolvedTypeName})"
        else -> kind.name
    }
}

internal fun KotlinProjectionReadOnlyCollectionBinding.isRedundantReadOnlyCollectionBinding(
    mutableBindings: List<KotlinProjectionMutableCollectionBinding>,
): Boolean = when (kind) {
	    KotlinProjectionReadOnlyCollectionKind.Iterable ->
	        mutableBindings.any { binding ->
	            when (binding.kind) {
	                KotlinProjectionMutableCollectionKind.Vector ->
	                    binding.elementBinding?.resolvedTypeName == elementBinding?.resolvedTypeName
                KotlinProjectionMutableCollectionKind.Map ->
                    elementBinding?.kind == KotlinProjectionAbiValueKind.MappedKeyValuePair &&
                        (
                            elementBinding.typeArguments.firstOrNull()?.resolvedTypeName to
                                elementBinding.typeArguments.getOrNull(1)?.resolvedTypeName
                            ) == (binding.keyBinding?.resolvedTypeName to binding.valueBinding?.resolvedTypeName)
	            }
	        }
	    KotlinProjectionReadOnlyCollectionKind.VectorView ->
	        mutableBindings.any { binding -> binding.kind == KotlinProjectionMutableCollectionKind.Vector }
	    KotlinProjectionReadOnlyCollectionKind.MapView ->
	        mutableBindings.any { binding -> binding.kind == KotlinProjectionMutableCollectionKind.Map }
	}

internal fun isMappedCollectionInterfaceName(interfaceName: String): Boolean {
    val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
    val mappedType = mappedTypeByAbiName(rawInterfaceName) ?: return false
    return mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null
}

internal fun KotlinProjectionMutableCollectionBinding.asReadOnlyEntryBinding(): KotlinProjectionReadOnlyCollectionBinding {
    require(kind == KotlinProjectionMutableCollectionKind.Map) {
        "Entry iterator projection requires a mutable map binding."
    }
    return KotlinProjectionReadOnlyCollectionBinding(
        kind = KotlinProjectionReadOnlyCollectionKind.MapView,
        ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
        ownerCachePropertyName = ownerCachePropertyName,
        slotInterfaceQualifiedName = slotInterfaceQualifiedName,
        delegatePropertyName = delegatePropertyName,
        keyBinding = keyBinding,
        valueBinding = valueBinding,
    )
}

internal fun KotlinProjectionReadOnlyCollectionKind.abiName(): String = when (this) {
    KotlinProjectionReadOnlyCollectionKind.Iterable -> "IIterable"
    KotlinProjectionReadOnlyCollectionKind.VectorView -> "IVectorView"
    KotlinProjectionReadOnlyCollectionKind.MapView -> "IMapView"
}

internal fun KotlinProjectionReadOnlyCollectionKind.ownerDelegatePropertyName(ownerInterface: String): String {
    val ownerSuffix = ownerInterface.collectionOwnerSuffix()
    return when (this) {
        KotlinProjectionReadOnlyCollectionKind.Iterable -> "__${ownerSuffix}IterableCollection"
        KotlinProjectionReadOnlyCollectionKind.VectorView -> "__${ownerSuffix}VectorViewCollection"
        KotlinProjectionReadOnlyCollectionKind.MapView -> "__${ownerSuffix}MapViewCollection"
    }
}

internal fun KotlinProjectionReadOnlyCollectionKind.returnDelegatePropertyName(): String = when (this) {
    KotlinProjectionReadOnlyCollectionKind.Iterable -> "__mappedIterableReturn"
    KotlinProjectionReadOnlyCollectionKind.VectorView -> "__mappedVectorViewReturn"
    KotlinProjectionReadOnlyCollectionKind.MapView -> "__mappedMapViewReturn"
}

internal fun KotlinProjectionMutableCollectionKind.abiName(): String = when (this) {
    KotlinProjectionMutableCollectionKind.Vector -> "IVector"
    KotlinProjectionMutableCollectionKind.Map -> "IMap"
}

internal fun KotlinProjectionMutableCollectionKind.ownerDelegatePropertyName(ownerInterface: String): String {
    val ownerSuffix = ownerInterface.collectionOwnerSuffix()
    return when (this) {
        KotlinProjectionMutableCollectionKind.Vector -> "__${ownerSuffix}VectorCollection"
        KotlinProjectionMutableCollectionKind.Map -> "__${ownerSuffix}MapCollection"
    }
}

internal fun String.collectionOwnerSuffix(): String =
    substringAfterLast('.')
        .filter(Char::isLetterOrDigit)
        .replaceFirstChar(Char::lowercase)

internal fun KotlinProjectionMutableCollectionKind.returnDelegatePropertyName(): String = when (this) {
    KotlinProjectionMutableCollectionKind.Vector -> "__mappedVectorReturn"
    KotlinProjectionMutableCollectionKind.Map -> "__mappedMapReturn"
}

internal fun createReadOnlyCollectionBindingPlan(
    collectionKind: KotlinProjectionReadOnlyCollectionKind,
    ownerInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
    slotInterfaceQualifiedName: String,
    delegatePropertyName: String,
    typeArguments: List<KotlinProjectionAbiTypeBinding>,
    errorContext: String,
    requireSupportedBinding: Boolean,
    bindingLocationLabel: String = "",
): KotlinProjectionReadOnlyCollectionBinding? =
    when (collectionKind) {
        KotlinProjectionReadOnlyCollectionKind.Iterable,
        KotlinProjectionReadOnlyCollectionKind.VectorView -> {
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(typeArguments.size == 1) {
                    "Generator read-only collection parity requires ${collectionKind.abiName()} ${bindingTargetPrefix}binding on $errorContext to carry 1 type argument before projection rendering; found ${typeArguments.size}."
                }
            }
            val elementBinding = typeArguments.singleOrNull() ?: return null
            if (requireSupportedBinding) {
                require(elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}element ${elementBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionReadOnlyCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                elementBinding = elementBinding,
            )
        }

        KotlinProjectionReadOnlyCollectionKind.MapView -> {
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(typeArguments.size == 2) {
                    "Generator read-only collection parity requires ${collectionKind.abiName()} ${bindingTargetPrefix}binding on $errorContext to carry 2 type arguments before projection rendering; found ${typeArguments.size}."
                }
            }
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            if (requireSupportedBinding) {
                require(keyBinding.isSupportedReadOnlyCollectionKeyBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}key ${keyBinding.describeAbiKind()} on $errorContext."
                }
                require(valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator read-only collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}value ${valueBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!keyBinding.isSupportedReadOnlyCollectionKeyBinding() || !valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionReadOnlyCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                keyBinding = keyBinding,
                valueBinding = valueBinding,
            )
        }
    }

internal fun createMutableCollectionBindingPlan(
    collectionKind: KotlinProjectionMutableCollectionKind,
    ownerInterfaceQualifiedName: String,
    ownerCachePropertyName: String,
    slotInterfaceQualifiedName: String,
    delegatePropertyName: String,
    typeArguments: List<KotlinProjectionAbiTypeBinding>,
    errorContext: String,
    requireSupportedBinding: Boolean,
    bindingLocationLabel: String = "",
): KotlinProjectionMutableCollectionBinding? =
    when (collectionKind) {
        KotlinProjectionMutableCollectionKind.Vector -> {
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(typeArguments.size == 1) {
                    "Generator mutable collection parity requires ${collectionKind.abiName()} ${bindingTargetPrefix}binding on $errorContext to carry 1 type argument before projection rendering; found ${typeArguments.size}."
                }
            }
            val elementBinding = typeArguments.singleOrNull() ?: return null
            if (requireSupportedBinding) {
                require(elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}element ${elementBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!elementBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionMutableCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                elementBinding = elementBinding,
            )
        }

        KotlinProjectionMutableCollectionKind.Map -> {
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
            if (requireSupportedBinding) {
                require(typeArguments.size == 2) {
                    "Generator mutable collection parity requires ${collectionKind.abiName()} ${bindingTargetPrefix}binding on $errorContext to carry 2 type arguments before projection rendering; found ${typeArguments.size}."
                }
            }
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            if (requireSupportedBinding) {
                require(keyBinding.isSupportedReadOnlyCollectionKeyBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}key ${keyBinding.describeAbiKind()} on $errorContext."
                }
                require(valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                    "Generator mutable collection parity does not yet support ${collectionKind.abiName()} ${bindingTargetPrefix}value ${valueBinding.describeAbiKind()} on $errorContext."
                }
            } else if (!keyBinding.isSupportedReadOnlyCollectionKeyBinding() || !valueBinding.isSupportedReadOnlyCollectionElementBinding()) {
                return null
            }
            KotlinProjectionMutableCollectionBinding(
                kind = collectionKind,
                ownerInterfaceQualifiedName = ownerInterfaceQualifiedName,
                ownerCachePropertyName = ownerCachePropertyName,
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                delegatePropertyName = delegatePropertyName,
                keyBinding = keyBinding,
                valueBinding = valueBinding,
            )
        }
    }

internal fun WinRTIntegralType.isSupportedProjectedEnumAbi(): Boolean =
    true

internal fun KotlinProjectionAbiTypeBinding.isSupportedReadOnlyCollectionElementBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.GuidValue,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference,
    KotlinProjectionAbiValueKind.Reference,
    KotlinProjectionAbiValueKind.ReferenceArray,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedMapView,
    KotlinProjectionAbiValueKind.GenericParameter -> true
    KotlinProjectionAbiValueKind.MappedKeyValuePair -> typeArguments.size == 2
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

internal fun KotlinProjectionAbiTypeBinding.isSupportedReadOnlyCollectionKeyBinding(): Boolean =
    isSupportedReadOnlyCollectionElementBinding()

internal fun requireDelegateInvokeMethod(type: WinRTTypeDefinition): WinRTMethodDefinition {
    val invokeMethods = type.methods.filter { it.name == "Invoke" }
    require(invokeMethods.size == 1) {
        val methodSummary = type.methods.joinToString(prefix = "[", postfix = "]") { method ->
            buildString {
                append(method.name)
                if (method.isSpecialName) append(":special")
                if (method.isRuntimeSpecialName) append(":runtime-special")
            }
        }
        "Delegate(${type.qualifiedName}) must expose exactly one Invoke method in metadata before projection planning; " +
            "found ${invokeMethods.size} Invoke methods in $methodSummary."
    }
    return invokeMethods.single()
}

internal fun WinRTMethodDefinition.isProjectedCallableMethod(): Boolean =
    !isSpecialName && !isRuntimeSpecialName

internal fun WinRTMethodDefinition.isOrdinaryProjectedMethod(): Boolean =
    !isStatic && !isSpecialName && !isRuntimeSpecialName

internal fun WinRTMethodDefinition.isOrdinaryProjectedStaticMethod(): Boolean =
    isStatic && !isSpecialName && !isRuntimeSpecialName

internal fun KotlinProjectionDelegateInvokeShape.isSupportedOutboundDelegateShape(): Boolean =
    interfaceId != null &&
        parameterBindings.all { it.typeBinding.isSupportedDelegateCallbackBinding() } &&
        returnBinding.isSupportedProjectedDelegateReturnBinding()

internal fun KotlinProjectionDelegateInvokeShape.isSupportedProjectedDelegateShape(): Boolean =
    interfaceId != null &&
        parameterBindings.all { it.typeBinding.isSupportedProjectedDelegateBinding() } &&
        returnBinding.isSupportedProjectedDelegateReturnBinding()

internal fun KotlinProjectionAbiTypeBinding.isSupportedDelegateCallbackBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.GuidValue,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView,
    KotlinProjectionAbiValueKind.Array,
    KotlinProjectionAbiValueKind.GenericParameter,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> kind != KotlinProjectionAbiValueKind.Array || isSupportedDelegateArrayBinding()
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

internal fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateBinding(): Boolean = when (kind) {
    KotlinProjectionAbiValueKind.String,
    KotlinProjectionAbiValueKind.Boolean,
    KotlinProjectionAbiValueKind.Int8,
    KotlinProjectionAbiValueKind.UInt8,
    KotlinProjectionAbiValueKind.Int16,
    KotlinProjectionAbiValueKind.UInt16,
    KotlinProjectionAbiValueKind.Int32,
    KotlinProjectionAbiValueKind.UInt32,
    KotlinProjectionAbiValueKind.Int64,
    KotlinProjectionAbiValueKind.UInt64,
    KotlinProjectionAbiValueKind.Float,
    KotlinProjectionAbiValueKind.Double,
    KotlinProjectionAbiValueKind.Char16,
    KotlinProjectionAbiValueKind.GuidValue,
    KotlinProjectionAbiValueKind.Struct,
    KotlinProjectionAbiValueKind.Object,
    KotlinProjectionAbiValueKind.ProjectedInterface,
    KotlinProjectionAbiValueKind.ProjectedRuntimeClass,
    KotlinProjectionAbiValueKind.MappedAsyncAction,
    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress,
    KotlinProjectionAbiValueKind.MappedAsyncOperation,
    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress,
    KotlinProjectionAbiValueKind.MappedIterable,
    KotlinProjectionAbiValueKind.MappedVector,
    KotlinProjectionAbiValueKind.MappedMap,
    KotlinProjectionAbiValueKind.MappedVectorView,
    KotlinProjectionAbiValueKind.MappedMapView,
    KotlinProjectionAbiValueKind.Array,
    KotlinProjectionAbiValueKind.GenericParameter,
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> kind != KotlinProjectionAbiValueKind.Array || isSupportedDelegateArrayBinding()
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

internal fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateReturnBinding(): Boolean =
    kind == KotlinProjectionAbiValueKind.Unit ||
        (kind != KotlinProjectionAbiValueKind.Array && isSupportedProjectedDelegateBinding())

private fun KotlinProjectionAbiTypeBinding.isSupportedDelegateArrayBinding(): Boolean =
    typeArguments.singleOrNull()?.kind == KotlinProjectionAbiValueKind.UInt8

internal fun KotlinProjectionAbiTypeBinding.isMappedCollectionBinding(): Boolean =
    mappedTypeByAbiKind(kind)?.let { mappedType ->
        (mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null) &&
            kind != KotlinProjectionAbiValueKind.MappedBindableIterable &&
            kind != KotlinProjectionAbiValueKind.MappedBindableVector &&
            kind != KotlinProjectionAbiValueKind.MappedBindableVectorView
    } == true

internal fun KotlinProjectionAbiTypeBinding.isMappedBindableCollectionBinding(): Boolean =
    kind == KotlinProjectionAbiValueKind.MappedBindableIterable ||
        kind == KotlinProjectionAbiValueKind.MappedBindableVector ||
        kind == KotlinProjectionAbiValueKind.MappedBindableVectorView

internal data class AbiMemberOrder(
    val rowId: Int,
    val constantName: String,
)

internal fun WinRTMethodDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

internal fun WinRTMethodDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

internal fun WinRTPropertyDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(typeName)
}

internal fun WinRTPropertyDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(typeName)
}

internal fun WinRTEventDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(delegateTypeName)
}

internal fun WinRTEventDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(delegateTypeName)
}

internal fun WinRTMethodDefinition.methodRowConstantName(methods: List<WinRTMethodDefinition>): String {
    val baseName = "${name.uppercase()}_METHOD_ROW_ID"
    if (methods.count { it.name == name } == 1) {
        return baseName
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${overloadOrdinal(methods)}_METHOD_ROW_ID"
    return "${name.uppercase()}_${rowId}_METHOD_ROW_ID"
}

internal fun String.methodSlotConstantName(): String =
    "${uppercase()}_SLOT"

internal fun WinRTMethodDefinition.abiSlotConstantName(methods: List<WinRTMethodDefinition>): String {
    val baseName = "${name.uppercase()}_SLOT"
    if (methods.count { it.name == name } == 1) {
        return baseName
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${overloadOrdinal(methods)}_SLOT"
    return "${name.uppercase()}_${rowId}_SLOT"
}

internal fun WinRTMethodDefinition.staticBindingSlotConstantName(
    staticInterfaceMethods: List<WinRTMethodDefinition>,
    staticMethodNameCounts: Map<String, Int>,
): String {
    if ((staticMethodNameCounts[name] ?: 0) <= 1) {
        return abiSlotConstantName(staticInterfaceMethods)
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${overloadOrdinal(staticInterfaceMethods)}_SLOT"
    return "${name.uppercase()}_${rowId}_SLOT"
}

private fun WinRTMethodDefinition.overloadOrdinal(methods: List<WinRTMethodDefinition>): Int {
    val sameName = methods.filter { it.name == name }
    val identityIndex = sameName.indexOfFirst { it === this }
    if (identityIndex >= 0) {
        return identityIndex
    }
    val structuralIndex = sameName.indexOf(this)
    if (structuralIndex >= 0) {
        return structuralIndex
    }
    return sameName.indexOfFirst { candidate ->
        candidate.parameters.map { it.type.normalized().typeName } == parameters.map { it.type.normalized().typeName } &&
            candidate.returnType.normalized().typeName == returnType.normalized().typeName
    }.coerceAtLeast(0)
}

internal fun TypeSpec.Builder.addStringListProperty(
    name: String,
    values: List<String>,
) {
    addProperty(
        PropertySpec.builder(name, List::class.asClassName().parameterizedBy(String::class.asClassName()))
            .addModifiers(KModifier.INTERNAL)
            .initializer("%L", stringListCode(values))
            .build(),
    )
}

internal fun stringListCode(values: List<String>): CodeBlock =
    CodeBlock.builder()
        .add("listOf(")
        .apply {
            values.forEachIndexed { index, value ->
                if (index > 0) {
                    add(", ")
                }
                add("%S", value)
            }
        }
        .add(")")
        .build()

internal fun WinRTTypeDefinition.localAbiMembers(): List<String> =
    localAbiMemberOrders().map(AbiMemberOrder::constantName)

private fun WinRTMappedTypeDescriptor.requiresKotlinMappedSupportDeclaration(type: WinRTTypeDefinition): Boolean =
    hasCustomMembersOutput ||
        type.kind == WinRTTypeKind.Delegate ||
        abiQualifiedName == "Windows.Foundation.Collections.IKeyValuePair"

internal fun WinRTTypeDefinition.localAbiMemberOrders(): List<AbiMemberOrder> =
    buildList<AbiMemberOrder> {
        val members = mutableListOf<AbiMemberOrder>()
        properties.forEach { property ->
            property.getterMethodRowId?.let { rowId ->
                members += AbiMemberOrder(rowId, "${property.name.uppercase()}_GETTER_SLOT")
            }
            property.setterMethodRowId?.let { rowId ->
                members += AbiMemberOrder(rowId, "${property.name.uppercase()}_SETTER_SLOT")
            }
        }
        events.forEach { event ->
            event.addMethodRowId?.let { rowId ->
                members += AbiMemberOrder(rowId, "${event.name.uppercase()}_ADD_SLOT")
            }
            event.removeMethodRowId?.let { rowId ->
                members += AbiMemberOrder(rowId, "${event.name.uppercase()}_REMOVE_SLOT")
            }
        }
        methods.filter(WinRTMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
            method.methodRowId?.let { rowId ->
                members += AbiMemberOrder(rowId, method.abiSlotConstantName(methods))
            }
        }
        addAll(members)
    }
        .sortedBy(AbiMemberOrder::rowId)

internal fun splitGenericArguments(arguments: String): List<String> {
    if (arguments.isBlank()) {
        return emptyList()
    }
    val result = mutableListOf<String>()
    var depth = 0
    var start = 0
    arguments.forEachIndexed { index, character ->
        when (character) {
            '<' -> depth += 1
            '>' -> depth -= 1
            ',' -> if (depth == 0) {
                result += arguments.substring(start, index).trim()
                start = index + 1
            }
        }
    }
    result += arguments.substring(start).trim()
    return result.filter(String::isNotEmpty)
}
