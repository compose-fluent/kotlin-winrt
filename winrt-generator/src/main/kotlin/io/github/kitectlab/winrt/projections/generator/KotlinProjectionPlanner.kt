package io.github.kitectlab.winrt.projections.generator

import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.kitectlab.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.kitectlab.winrt.metadata.WinRtEventDefinition
import io.github.kitectlab.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtFieldDefinition
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGenericAbiInventory
import io.github.kitectlab.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.kitectlab.winrt.metadata.WinRtIntegralType
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionContext
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.kitectlab.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.kitectlab.winrt.metadata.WinRtMetadataParameterCategory
import io.github.kitectlab.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.kitectlab.winrt.metadata.WinRtMethodDefinition
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.kitectlab.winrt.metadata.WinRtPropertyDefinition
import io.github.kitectlab.winrt.metadata.WinRtProjectedAttributeDescriptor
import io.github.kitectlab.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeRef
import io.github.kitectlab.winrt.metadata.WinRtTypeKind
import io.github.kitectlab.winrt.metadata.WinRtMetadataValidationOptions
import io.github.kitectlab.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.kitectlab.winrt.metadata.projectedAttributes
import io.github.kitectlab.winrt.metadata.requireValidForProjection
import io.github.kitectlab.winrt.metadata.semanticHelpers
import io.github.kitectlab.winrt.runtime.ActivationFactory
import io.github.kitectlab.winrt.runtime.ComObjectReference
import io.github.kitectlab.winrt.runtime.ComVtableInvoker
import io.github.kitectlab.winrt.runtime.Guid
import io.github.kitectlab.winrt.runtime.HResult
import io.github.kitectlab.winrt.runtime.HString
import io.github.kitectlab.winrt.runtime.IUnknownReference
import io.github.kitectlab.winrt.runtime.IWinRTObject
import io.github.kitectlab.winrt.runtime.Marshaler
import io.github.kitectlab.winrt.runtime.PlatformAbi
import io.github.kitectlab.winrt.runtime.ParameterizedInterfaceId
import io.github.kitectlab.winrt.runtime.RawAddress
import io.github.kitectlab.winrt.runtime.NativeNestedStructFieldSpec
import io.github.kitectlab.winrt.runtime.NativeScalarFieldSpec
import io.github.kitectlab.winrt.runtime.NativeStructLayout
import io.github.kitectlab.winrt.runtime.NativeStructScalarKind
import io.github.kitectlab.winrt.runtime.WinRtBindableIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorProjection
import io.github.kitectlab.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.kitectlab.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.kitectlab.winrt.runtime.WinRtDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtIterableProjection
import io.github.kitectlab.winrt.runtime.WinRtListProjection
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.kitectlab.winrt.runtime.WinRtReadOnlyListProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceArrayProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceProjection
import io.github.kitectlab.winrt.runtime.WinRtReferenceValueAdapter
import io.github.kitectlab.winrt.runtime.WinRtPlatformApi
import io.github.kitectlab.winrt.runtime.WinRtTypeSignature
import io.github.kitectlab.winrt.runtime.WinRtTypeHandle
import io.github.kitectlab.winrt.runtime.WinRtUri
import io.github.kitectlab.winrt.runtime.WinRtDelegateBridge
import io.github.kitectlab.winrt.runtime.WinRtDelegateDescriptor
import io.github.kitectlab.winrt.runtime.WinRtDelegateReference
import io.github.kitectlab.winrt.runtime.WinRtDelegateValueKind
import io.github.kitectlab.winrt.runtime.WinRtEvent
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
) {
    fun plan(model: WinRtMetadataModel): List<KotlinTypeProjectionPlan> =
        validator.validate(model).let { normalized ->
            val semanticHelpers = normalized.semanticHelpers()
            val typesByQualifiedName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associateBy(WinRtTypeDefinition::qualifiedName)
            val interfaceIidsByName = normalized.namespaces
                .flatMap(WinRtNamespace::types)
                .associate { it.qualifiedName to it.iid }
            normalized.namespaces.flatMap { planNamespace(it, interfaceIidsByName, typesByQualifiedName, semanticHelpers) }
        }

    fun planNamespace(
        namespace: WinRtNamespace,
        interfaceIidsByName: Map<String, Guid?> = emptyMap(),
        typesByQualifiedName: Map<String, WinRtTypeDefinition> = emptyMap(),
        semanticHelpers: WinRtMetadataSemanticHelpers? = null,
    ): List<KotlinTypeProjectionPlan> =
        namespace.normalized().let { normalizedNamespace ->
            val helpers = semanticHelpers ?: WinRtMetadataModel(listOf(normalizedNamespace)).semanticHelpers()
            normalizedNamespace.types.mapNotNull { type ->
                planType(type, interfaceIidsByName, typesByQualifiedName, helpers)
            }
        }

    private fun planType(
        type: WinRtTypeDefinition,
        interfaceIidsByName: Map<String, Guid?>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): KotlinTypeProjectionPlan? {
        val typeDeclarationDescriptor = semanticHelpers.typeDeclarationDescriptor(type)
        if (!typeDeclarationDescriptor.writesProjectedDeclaration) {
            return null
        }
        val metadataMappedType = semanticHelpers.getMappedType(type.namespace, type.name)
        if (metadataMappedType != null && metadataMappedType.mappedQualifiedName == null) {
            return null
        }
        if (mappedTypeByAbiName(type.qualifiedName)?.isRuntimeOwnedProjection() == true) {
            return null
        }
        fun interfaceIidFor(interfaceName: String): Guid? =
            interfaceIidsByName[interfaceName]
                ?: interfaceIidsByName[interfaceName.substringBefore('<').removeSuffix("?")]

        val declarationKind = when (type.kind) {
            WinRtTypeKind.Interface -> KotlinProjectionDeclarationKind.Interface
            WinRtTypeKind.RuntimeClass -> KotlinProjectionDeclarationKind.Class
            WinRtTypeKind.Enum -> KotlinProjectionDeclarationKind.Enum
            WinRtTypeKind.Struct -> if (type.isApiContract) {
                KotlinProjectionDeclarationKind.Enum
            } else {
                KotlinProjectionDeclarationKind.Struct
            }
            WinRtTypeKind.Delegate -> KotlinProjectionDeclarationKind.Delegate
            WinRtTypeKind.Unknown -> return null
        }
        val packageName = (ROOT_PACKAGE_SEGMENTS + namespaceSegments(type.namespace)).joinToString(".")
        val relativePath = packageName.replace('.', '/') + "/${type.name}.kt"
        return KotlinTypeProjectionPlan(
            type = type,
            typesByQualifiedName = typesByQualifiedName,
            packageName = packageName,
            relativePath = relativePath,
            declarationKind = declarationKind,
            visibility = planVisibility(type),
            modifiers = planModifiers(type),
            specializationKinds = planSpecializations(type),
            interfaceIid = type.iid,
            defaultInterfaceName = type.defaultInterfaceName,
            defaultInterfaceIid = type.defaultInterfaceName?.let(interfaceIidsByName::get),
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
            activatableFactoryInterfaceIid = type.activation.activatableFactoryInterfaceName?.let(interfaceIidsByName::get),
            composableFactoryInterfaceName = type.activation.composableFactoryInterfaceName,
            composableFactoryInterfaceIid = type.activation.composableFactoryInterfaceName?.let(interfaceIidsByName::get),
            abiSlotBindings = planAbiSlotBindings(type, typesByQualifiedName, semanticHelpers),
            instanceMemberBindings = planInstanceMemberBindings(type, typesByQualifiedName, semanticHelpers),
            staticMemberBindings = planStaticMemberBindings(type, typesByQualifiedName, semanticHelpers),
            readOnlyCollectionBindings = planReadOnlyCollectionBindings(type, typesByQualifiedName, semanticHelpers),
            mutableCollectionBindings = planMutableCollectionBindings(type, typesByQualifiedName, semanticHelpers),
            delegateInvokeShape =
                if (type.kind == WinRtTypeKind.Delegate) {
                    classifyAbiTypeBinding(type.qualifiedName, type.namespace, typesByQualifiedName).delegateInvokeShape
                } else {
                    null
                },
            eventInvokeDescriptors = type.events.map { event -> semanticHelpers.eventInvokeDescriptor(type, event) },
            typeDeclarationDescriptor = typeDeclarationDescriptor,
            factorySurfaceDescriptor = if (type.kind == WinRtTypeKind.RuntimeClass) semanticHelpers.factorySurfaceDescriptor(type) else null,
            objectReferenceSurfaceDescriptor = if (type.kind in setOf(WinRtTypeKind.RuntimeClass, WinRtTypeKind.Delegate)) {
                semanticHelpers.objectReferenceSurfaceDescriptor(type)
            } else {
                null
            },
            guidSignatureDescriptor = type.iid?.let { semanticHelpers.guidSignatureDescriptor(type) },
            interfaceMemberSignatureSetDescriptor = if (type.kind == WinRtTypeKind.Interface) {
                semanticHelpers.interfaceMemberSignatureSetDescriptor(type)
            } else {
                null
            },
            customMappedMemberOutputDescriptor = if (type.kind == WinRtTypeKind.Interface) {
                semanticHelpers.customMappedMemberOutputDescriptor(type)
            } else {
                null
            },
            classMemberMergeDescriptor = if (type.kind == WinRtTypeKind.RuntimeClass) {
                semanticHelpers.classMemberMergeDescriptor(type)
            } else {
                null
            },
            genericAbiClassInitializationDescriptor = if (type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.Delegate)) {
                semanticHelpers.genericAbiClassInitializationDescriptor(type)
            } else {
                null
            },
            requiredInterfaceAugmentationDescriptor = if (type.kind in setOf(WinRtTypeKind.Interface, WinRtTypeKind.RuntimeClass)) {
                semanticHelpers.requiredInterfaceAugmentationDescriptor(type)
            } else {
                null
            },
            fastAbiClassDescriptor = when (type.kind) {
                WinRtTypeKind.RuntimeClass -> semanticHelpers.getFastAbiClassForClass(type)
                WinRtTypeKind.Interface -> semanticHelpers.getFastAbiClassForInterface(type)
                else -> null
            },
            moduleActivationAndAuthoringDescriptor = if (type.kind == WinRtTypeKind.RuntimeClass) {
                semanticHelpers.moduleActivationAndAuthoringDescriptor(type)
            } else {
                null
            },
            projectedAttributes = semanticHelpers.projectedAttributes(type),
            companionKinds = planCompanions(type),
        )
    }

    private fun planAbiSlotBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionAbiSlotBinding> {
        if (type.kind != WinRtTypeKind.Interface) {
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
            interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, mutableSetOf())
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
    ): Int {
        val type = typesByQualifiedName[interfaceName] ?: return 0
        if (type.kind != WinRtTypeKind.Interface || !visiting.add(interfaceName)) {
            return 0
        }
        return try {
            type.implementedInterfaces.sumOf { implemented ->
                interfaceAbiMemberCount(implemented.interfaceName, typesByQualifiedName, visiting)
            } + type.localAbiMemberOrders().map(AbiMemberOrder::rowId).distinct().size
        } finally {
            visiting.remove(interfaceName)
        }
    }

    private fun planInstanceMemberBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionInstanceMemberBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass && type.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val candidateInterfaces = buildList {
            if (type.kind == WinRtTypeKind.Interface) {
                add(type.qualifiedName)
            } else {
                type.defaultInterfaceName?.let(::add)
            }
            type.implementedInterfaces
                .filterNot { it.isDefault }
                .mapTo(this) { it.interfaceName }
        }.distinct()

        return buildList {
            type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                resolveInstanceMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    bindingName = method.abiSlotConstantName(type.methods),
                    slotConstantName = method.abiSlotConstantName(type.methods),
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
                if (property.getterMethodName != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
                        parameterBindings = emptyList(),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.getterMethodName != null }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.getterMethodName != null
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
                if (property.setterMethodName != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.setterMethodName != null }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.properties.any {
                                it.projectionSignatureKey() == property.projectionSignatureKey() && it.setterMethodName != null
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
            }
            type.events.filterNot { it.isStatic }.forEach { event ->
                if (event.addMethodName != null || event.addMethodRowId != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
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
                                it.projectionSignatureKey() == event.projectionSignatureKey() &&
                                    (it.addMethodName != null || it.addMethodRowId != null)
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
                if (event.removeMethodName != null || event.removeMethodRowId != null) {
                    resolveInstanceMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
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
                                it.projectionSignatureKey() == event.projectionSignatureKey() &&
                                    (it.removeMethodName != null || it.removeMethodRowId != null)
                            }
                        },
                        ownerCachePropertyNameResolver = { ownerInterface, slotInterface ->
                            fastAbiOwnerCachePropertyName(ownerInterface, slotInterface, candidateInterfaces.firstOrNull(), typesByQualifiedName, semanticHelpers)
                        },
                    )?.let(::add)
                }
            }
        }.distinctBy(KotlinProjectionInstanceMemberBinding::bindingName)
    }

    private fun planStaticMemberBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionStaticMemberBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass) {
            return emptyList()
        }
        val candidateInterfaces = type.activation.staticInterfaceNames.distinct()
        return buildList {
            val staticInterfaces = candidateInterfaces.mapNotNull(typesByQualifiedName::get)
            staticInterfaces.flatMap { staticInterface ->
                staticInterface.methods
                    .filter(WinRtMethodDefinition::isProjectedCallableMethod)
                    .map { method -> staticInterface to method.copy(isStatic = true) }
            }.forEach { (staticInterface, method) ->
                val signatureDescriptor = semanticHelpers.signatureWriterDescriptor(method)
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
                    bindingName = "STATIC_${method.abiSlotConstantName(staticInterface.methods)}",
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
                if (property.getterMethodName != null) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        bindingName = "STATIC_${property.name.uppercase()}_GETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_GETTER_SLOT",
                        returnBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
                        parameterBindings = emptyList(),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.getterMethodName != null }
                                ?.let(semanticHelpers::isNoException)
                                ?: semanticHelpers.isNoException(property)
                        },
                        signatureMatcher = { interfaceType ->
                            interfaceType.qualifiedName == staticInterface.qualifiedName
                        },
                    )?.let(::add)
                }
                if (property.setterMethodName != null) {
                    resolveStaticMemberBinding(
                        candidateInterfaces = candidateInterfaces,
                        typesByQualifiedName = typesByQualifiedName,
                        bindingName = "STATIC_${property.name.uppercase()}_SETTER_SLOT",
                        slotConstantName = "${property.name.uppercase()}_SETTER_SLOT",
                        returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                        parameterBindings = listOf(
                            KotlinProjectionAbiParameterBinding(
                                name = "value",
                                typeBinding = classifyAbiTypeBinding(property.typeName, type.namespace, typesByQualifiedName),
                            ),
                        ),
                        suppressHResultCheckResolver = { interfaceType ->
                            interfaceType.properties
                                .firstOrNull { it.projectionSignatureKey() == property.projectionSignatureKey() && it.setterMethodName != null }
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
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
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
                        interfaceType.qualifiedName == staticInterface.qualifiedName
                    },
                )?.let(::add)
                resolveStaticMemberBinding(
                    candidateInterfaces = candidateInterfaces,
                    typesByQualifiedName = typesByQualifiedName,
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
                        interfaceType.qualifiedName == staticInterface.qualifiedName
                    },
                )?.let(::add)
            }
        }.distinctBy(KotlinProjectionStaticMemberBinding::bindingName)
    }

    private fun planReadOnlyCollectionBindings(
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionReadOnlyCollectionBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass && type.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val mutableBindings = planMutableCollectionBindings(type, typesByQualifiedName, semanticHelpers)
        val candidateInterfaces = collectionInterfaceTraversalRoots(type, semanticHelpers)

        val bindings = candidateInterfaces.flatMap { (ownerInterface, currentInterface) ->
            collectReadOnlyCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName?.takeIf { type.kind == WinRtTypeKind.RuntimeClass },
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
        type: WinRtTypeDefinition,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<KotlinProjectionMutableCollectionBinding> {
        if (type.kind != WinRtTypeKind.RuntimeClass && type.kind != WinRtTypeKind.Interface) {
            return emptyList()
        }
        val candidateInterfaces = collectionInterfaceTraversalRoots(type, semanticHelpers)

        return candidateInterfaces.flatMap { (ownerInterface, currentInterface) ->
            collectMutableCollectionBindings(
                ownerInterface = ownerInterface,
                defaultInterfaceName = type.defaultInterfaceName?.takeIf { type.kind == WinRtTypeKind.RuntimeClass },
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
        type: WinRtTypeDefinition,
        semanticHelpers: WinRtMetadataSemanticHelpers,
    ): List<Pair<String, String>> {
        val ownerInterfaces = buildList {
            if (type.kind == WinRtTypeKind.Interface) {
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): KotlinProjectionMutableCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
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
            ownerCachePropertyName = if (mappedType.isBindableCollectionMapping()) "_inner" else ownerCachePropertyName(ownerInterface, defaultInterfaceName),
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = false,
        )
    }

    private fun collectReadOnlyCollectionBindings(
        ownerInterface: String,
        defaultInterfaceName: String?,
        currentInterfaceName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): KotlinProjectionReadOnlyCollectionBinding? {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return null
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
            ownerCachePropertyName = if (mappedType.isBindableCollectionMapping()) "_inner" else ownerCachePropertyName(ownerInterface, defaultInterfaceName),
            slotInterfaceQualifiedName = resolvedInterfaceName,
            delegatePropertyName = collectionKind.ownerDelegatePropertyName(ownerInterface),
            typeArguments = genericArguments,
            errorContext = ownerInterface,
            requireSupportedBinding = false,
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
        implemented: WinRtInterfaceImplementationDefinition,
    ): String {
        val currentGenericArguments = genericArgumentTypeRefs(currentInterfaceName)
        if (currentGenericArguments.isEmpty()) {
            return implemented.interfaceName
        }
        return implemented.interfaceType
            .substituteTypeParameters(currentGenericArguments)
            .typeName
    }

    private fun genericArgumentTypeRefs(typeName: String): List<WinRtTypeRef> {
        val trimmed = typeName.trim()
        if ('<' !in trimmed || !trimmed.endsWith('>')) {
            return emptyList()
        }
        return splitGenericArguments(trimmed.substringAfter('<').substringBeforeLast('>'))
            .map(WinRtTypeRef::fromDisplayName)
    }

    private fun resolveInstanceMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        bindingName: String? = null,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
        suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
        suppressHResultCheckResolver: (WinRtTypeDefinition) -> Boolean = { suppressHResultCheck },
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
        ownerCachePropertyNameResolver: (String, String) -> String = { ownerInterface, _ ->
            ownerCachePropertyName(ownerInterface, candidateInterfaces.firstOrNull())
        },
        slotConstantNameResolver: (WinRtTypeDefinition) -> String? = { null },
    ): KotlinProjectionInstanceMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            val slotInterfaceType = typesByQualifiedName.getValue(slotInterfaceQualifiedName)
            return KotlinProjectionInstanceMemberBinding(
                bindingName = bindingName ?: slotConstantName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerCachePropertyName = ownerCachePropertyNameResolver(candidateInterface, slotInterfaceQualifiedName),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = slotConstantNameResolver(slotInterfaceType) ?: slotConstantName,
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
                projectedAttributes = slotInterfaceType.projectedAttributes()
                    .filter(WinRtProjectedAttributeDescriptor::isPlatformAttribute),
                suppressHResultCheck = suppressHResultCheckResolver(slotInterfaceType),
            )
        }
        return null
    }

    private fun resolveStaticMemberBinding(
        candidateInterfaces: List<String>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        bindingName: String,
        slotConstantName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        signatureDescriptor: WinRtSignatureWriterDescriptor? = null,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
        suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
        suppressHResultCheckResolver: (WinRtTypeDefinition) -> Boolean = { suppressHResultCheck },
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
        slotConstantNameResolver: (WinRtTypeDefinition) -> String? = { null },
    ): KotlinProjectionStaticMemberBinding? {
        candidateInterfaces.forEach { candidateInterface ->
            val slotInterfaceQualifiedName = findDeclaringInterface(
                interfaceName = candidateInterface,
                typesByQualifiedName = typesByQualifiedName,
                visiting = mutableSetOf(),
                signatureMatcher = signatureMatcher,
            ) ?: return@forEach
            val slotInterfaceType = typesByQualifiedName.getValue(slotInterfaceQualifiedName)
            return KotlinProjectionStaticMemberBinding(
                bindingName = bindingName,
                ownerInterfaceQualifiedName = candidateInterface,
                ownerAccessorName = staticOwnerAccessorName(candidateInterface),
                ownerCachePropertyName = staticOwnerCachePropertyName(candidateInterface),
                slotInterfaceQualifiedName = slotInterfaceQualifiedName,
                slotConstantName = slotConstantNameResolver(slotInterfaceType) ?: slotConstantName,
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                signatureDescriptor = signatureDescriptor,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
                projectedAttributes = slotInterfaceType.projectedAttributes()
                    .filter(WinRtProjectedAttributeDescriptor::isPlatformAttribute),
                suppressHResultCheck = suppressHResultCheckResolver(slotInterfaceType),
            )
        }
        return null
    }

    internal fun classifyAbiTypeBinding(
        typeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        includeDelegateInvokeShape: Boolean = true,
    ): KotlinProjectionAbiTypeBinding {
        val trimmedTypeName = typeName.trim()
        val rawTypeName = trimmedTypeName.substringBefore('<').removeSuffix("?")
        val typeArguments = if ('<' in trimmedTypeName && trimmedTypeName.endsWith('>')) {
            splitGenericArguments(trimmedTypeName.substringAfter('<').substringBeforeLast('>'))
                .map { argument ->
                    classifyAbiTypeBinding(
                        typeName = argument,
                        currentNamespace = currentNamespace,
                        typesByQualifiedName = typesByQualifiedName,
                        includeDelegateInvokeShape = false,
                    )
                }
        } else {
            emptyList()
        }
        val resolvedTypeName = qualifyTypeName(rawTypeName, currentNamespace, typesByQualifiedName) ?: rawTypeName
        val resolvedType = typesByQualifiedName[resolvedTypeName]
        val mappedType = mappedTypeByAbiName(rawTypeName)
        val isProjectedKeyValuePair = rawTypeName == "Map.Entry" || rawTypeName == "kotlin.collections.Map.Entry"
        val kind = when (trimmedTypeName) {
            "Unit" -> KotlinProjectionAbiValueKind.Unit
            "String" -> KotlinProjectionAbiValueKind.String
            "Boolean" -> KotlinProjectionAbiValueKind.Boolean
            "Byte",
            "SByte",
            "Int8" -> KotlinProjectionAbiValueKind.Int8
            "UByte",
            "UInt8" -> KotlinProjectionAbiValueKind.UInt8
            "Short",
            "Int16" -> KotlinProjectionAbiValueKind.Int16
            "UShort",
            "UInt16" -> KotlinProjectionAbiValueKind.UInt16
            "Int" -> KotlinProjectionAbiValueKind.Int32
            "UInt" -> KotlinProjectionAbiValueKind.UInt32
            "Long",
            "Int64" -> KotlinProjectionAbiValueKind.Int64
            "ULong",
            "UInt64" -> KotlinProjectionAbiValueKind.UInt64
            "Float",
            "Single" -> KotlinProjectionAbiValueKind.Float
            "Double" -> KotlinProjectionAbiValueKind.Double
            "Char",
            "Char16" -> KotlinProjectionAbiValueKind.Char16
            "Guid",
            "System.Guid" -> KotlinProjectionAbiValueKind.GuidValue
            IUNKNOWN_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.UnknownReference
            IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName -> KotlinProjectionAbiValueKind.InspectableReference
            "io.github.kitectlab.winrt.runtime.IUnknownReference" -> KotlinProjectionAbiValueKind.UnknownReference
            "io.github.kitectlab.winrt.runtime.IInspectableReference" -> KotlinProjectionAbiValueKind.InspectableReference
            else -> when {
                rawTypeName.isGenericTypeParameterName() -> KotlinProjectionAbiValueKind.GenericParameter
                rawTypeName == "Array" -> KotlinProjectionAbiValueKind.Array
                rawTypeName == "Any" || rawTypeName == "System.Object" -> KotlinProjectionAbiValueKind.Object
                isProjectedKeyValuePair -> KotlinProjectionAbiValueKind.MappedKeyValuePair
                mappedType?.abiValueKind != null -> mappedType.abiValueKind
                resolvedType != null -> when (resolvedType.kind) {
                    WinRtTypeKind.Interface -> KotlinProjectionAbiValueKind.ProjectedInterface
                    WinRtTypeKind.RuntimeClass -> KotlinProjectionAbiValueKind.ProjectedRuntimeClass
                    WinRtTypeKind.Enum -> KotlinProjectionAbiValueKind.Enum
                    WinRtTypeKind.Struct -> KotlinProjectionAbiValueKind.Struct
                    WinRtTypeKind.Delegate -> KotlinProjectionAbiValueKind.Delegate
                    WinRtTypeKind.Unknown -> KotlinProjectionAbiValueKind.Unsupported
                }
                rawTypeName.isProjectedWinRtInterfaceReferenceName() -> KotlinProjectionAbiValueKind.ProjectedInterface
                mappedType != null -> KotlinProjectionAbiValueKind.Unsupported
                else -> KotlinProjectionAbiValueKind.Unsupported
            }
        }
        val delegateInvokeShape = if (
            includeDelegateInvokeShape &&
            kind == KotlinProjectionAbiValueKind.Delegate &&
            resolvedType != null
        ) {
            val invokeMethod = requireDelegateInvokeMethod(resolvedType)
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
                        ),
                    )
                },
                returnBinding = classifyAbiTypeBinding(
                    typeName = invokeMethod.returnTypeName,
                    currentNamespace = resolvedType.namespace,
                    typesByQualifiedName = typesByQualifiedName,
                    includeDelegateInvokeShape = false,
                ),
            )
        } else {
            null
        }
        return KotlinProjectionAbiTypeBinding(
            kind = kind,
            typeName = trimmedTypeName,
            resolvedTypeName = resolvedTypeName,
            sourceTypeKind = resolvedType?.kind,
            interfaceId = resolvedType?.iid ?: mappedReferenceGenericInterfaceId(kind),
            enumUnderlyingType = resolvedType?.enumUnderlyingType,
            delegateInvokeShape = delegateInvokeShape,
            typeArguments = typeArguments,
        )
    }

    private fun String.isGenericTypeParameterName(): Boolean =
        (startsWith("T") || startsWith("M")) && drop(1).toIntOrNull() != null

    private fun mappedReferenceGenericInterfaceId(kind: KotlinProjectionAbiValueKind): Guid? =
        when (kind) {
            KotlinProjectionAbiValueKind.Reference -> IREFERENCE_GENERIC_INTERFACE_ID
            KotlinProjectionAbiValueKind.ReferenceArray -> IREFERENCE_ARRAY_GENERIC_INTERFACE_ID
            else -> null
        }

    private fun qualifyTypeName(
        rawTypeName: String,
        currentNamespace: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
    ): Boolean {
        val rawInterfaceName = interfaceName.substringBefore('<').removeSuffix("?")
        val resolvedInterfaceName = qualifyTypeName(rawInterfaceName, currentNamespace, typesByQualifiedName) ?: rawInterfaceName
        val mappedType = mappedTypeByAbiName(resolvedInterfaceName) ?: return false
        return mappedType.readOnlyCollectionKind != null || mappedType.mutableCollectionKind != null
    }

    private fun findDeclaringInterface(
        interfaceName: String,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        visiting: MutableSet<String>,
        signatureMatcher: (WinRtTypeDefinition) -> Boolean,
    ): String? {
        val type = typesByQualifiedName[interfaceName] ?: return null
        if (type.kind != WinRtTypeKind.Interface || !visiting.add(interfaceName)) {
            return null
        }
        return try {
            if (signatureMatcher(type)) {
                interfaceName
            } else {
                type.implementedInterfaces.firstNotNullOfOrNull { implemented ->
                    findDeclaringInterface(implemented.interfaceName, typesByQualifiedName, visiting, signatureMatcher)
                }
            }
        } finally {
            visiting.remove(interfaceName)
        }
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
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        semanticHelpers: WinRtMetadataSemanticHelpers,
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

    private fun planCompanions(type: WinRtTypeDefinition): List<KotlinProjectionCompanionKind> = buildList {
        if (shouldEmitMetadataCompanion(type)) {
            add(KotlinProjectionCompanionKind.Metadata)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.isActivatable) {
            add(KotlinProjectionCompanionKind.ActivationFactory)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.staticInterfaceNames.isNotEmpty()) {
            add(KotlinProjectionCompanionKind.StaticInterfaces)
        }
        if (type.kind == WinRtTypeKind.RuntimeClass && type.activation.composableFactoryInterfaceName != null) {
            add(KotlinProjectionCompanionKind.ComposableFactory)
        }
    }

    private fun shouldEmitMetadataCompanion(type: WinRtTypeDefinition): Boolean = when (type.kind) {
        WinRtTypeKind.Interface -> true
        WinRtTypeKind.RuntimeClass -> (!type.isStaticType && !type.isAttributeType) ||
            (type.isStaticType && type.activation.staticInterfaceNames.isNotEmpty())
        else -> false
    }

    private fun planVisibility(type: WinRtTypeDefinition): KotlinProjectionVisibility =
        if (type.isProjectionInternal || (type.kind == WinRtTypeKind.Interface && type.isExclusiveTo)) {
            KotlinProjectionVisibility.Internal
        } else {
            KotlinProjectionVisibility.Public
        }

    private fun planModifiers(type: WinRtTypeDefinition): List<KotlinProjectionModifier> = buildList {
        if (type.isStaticType) {
            add(KotlinProjectionModifier.Static)
        }
        if (type.isAttributeType || (type.kind == WinRtTypeKind.RuntimeClass && type.isSealedType && !type.isStaticType)) {
            add(KotlinProjectionModifier.Sealed)
        }
    }

    private fun planSpecializations(type: WinRtTypeDefinition): List<KotlinProjectionSpecializationKind> = buildList {
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
            val elementBinding = typeArguments.singleOrNull() ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
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
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
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
            val elementBinding = typeArguments.singleOrNull() ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
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
            val keyBinding = typeArguments.getOrNull(0) ?: return null
            val valueBinding = typeArguments.getOrNull(1) ?: return null
            val bindingTargetPrefix = if (bindingLocationLabel.isBlank()) "" else "$bindingLocationLabel "
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

internal fun WinRtIntegralType.isSupportedProjectedEnumAbi(): Boolean =
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

internal fun requireDelegateInvokeMethod(type: WinRtTypeDefinition): WinRtMethodDefinition {
    val invokeMethods = type.methods.filter { it.name == "Invoke" }
    require(invokeMethods.size == 1 && type.methods.size == 1) {
        "Delegate(${type.qualifiedName}) must expose exactly one Invoke method in metadata before projection planning."
    }
    return invokeMethods.single()
}

internal fun WinRtMethodDefinition.isProjectedCallableMethod(): Boolean =
    !isSpecialName && !isRuntimeSpecialName

internal fun WinRtMethodDefinition.isOrdinaryProjectedMethod(): Boolean =
    !isStatic && !isSpecialName && !isRuntimeSpecialName

internal fun WinRtMethodDefinition.isOrdinaryProjectedStaticMethod(): Boolean =
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
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
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
    KotlinProjectionAbiValueKind.UnknownReference,
    KotlinProjectionAbiValueKind.InspectableReference -> true
    KotlinProjectionAbiValueKind.Enum -> enumUnderlyingType?.isSupportedProjectedEnumAbi() == true
    else -> false
}

internal fun KotlinProjectionAbiTypeBinding.isSupportedProjectedDelegateReturnBinding(): Boolean =
    isSupportedProjectedDelegateBinding() || kind == KotlinProjectionAbiValueKind.Unit

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

internal fun WinRtMethodDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

internal fun WinRtMethodDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(returnTypeName)
    append('|')
    append(parameters.joinToString(",") { "${it.name}:${it.typeName}:${it.direction}" })
}

internal fun WinRtPropertyDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(typeName)
}

internal fun WinRtPropertyDefinition.projectionSignatureIgnoringStaticKey(): String = buildString {
    append(name)
    append('|')
    append(typeName)
}

internal fun WinRtEventDefinition.projectionSignatureKey(): String = buildString {
    append(if (isStatic) 'S' else 'I')
    append('|')
    append(name)
    append('|')
    append(delegateTypeName)
}

internal fun WinRtMethodDefinition.methodRowConstantName(methods: List<WinRtMethodDefinition>): String {
    val baseName = "${name.uppercase()}_METHOD_ROW_ID"
    if (methods.count { it.name == name } == 1) {
        return baseName
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${parameters.size}_METHOD_ROW_ID"
    return "${name.uppercase()}_${rowId}_METHOD_ROW_ID"
}

internal fun String.methodSlotConstantName(): String =
    "${uppercase()}_SLOT"

internal fun WinRtMethodDefinition.abiSlotConstantName(methods: List<WinRtMethodDefinition>): String {
    val baseName = "${name.uppercase()}_SLOT"
    if (methods.count { it.name == name } == 1) {
        return baseName
    }
    val rowId = methodRowId ?: return "${name.uppercase()}_${parameters.size}_SLOT"
    return "${name.uppercase()}_${rowId}_SLOT"
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

internal fun WinRtTypeDefinition.localAbiMembers(): List<String> =
    localAbiMemberOrders().map(AbiMemberOrder::constantName)

internal fun WinRtTypeDefinition.localAbiMemberOrders(): List<AbiMemberOrder> =
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
        methods.filter(WinRtMethodDefinition::isOrdinaryProjectedMethod).forEach { method ->
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
