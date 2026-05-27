package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
import io.github.composefluent.winrt.metadata.WinRtEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventHandlerKind
import io.github.composefluent.winrt.metadata.WinRtEventInvokeDescriptor
import io.github.composefluent.winrt.metadata.WinRtFactorySurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtFieldDefinition
import io.github.composefluent.winrt.metadata.WinRtGenericAbiClassInitializationDescriptor
import io.github.composefluent.winrt.metadata.WinRtGenericAbiInventory
import io.github.composefluent.winrt.metadata.WinRtGenericInstantiationWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtGuidSignatureDescriptor
import io.github.composefluent.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.composefluent.winrt.metadata.WinRtInterfaceMemberSignatureSetDescriptor
import io.github.composefluent.winrt.metadata.WinRtIntegralType
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionContext
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventory
import io.github.composefluent.winrt.metadata.WinRtMetadataProjectionInventoryBuilder
import io.github.composefluent.winrt.metadata.WinRtMetadataParameterCategory
import io.github.composefluent.winrt.metadata.WinRtModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRtMethodDefinition
import io.github.composefluent.winrt.metadata.WinRtNamespace
import io.github.composefluent.winrt.metadata.WinRtObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRtParameterDefinition
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtMetadataValidationOptions
import io.github.composefluent.winrt.metadata.projectionInventory
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.isWinRtGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRtObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRtVoidTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.winRtFundamentalTypeForName
import io.github.composefluent.winrt.metadata.winRtEventHandlerKindForTypeName
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
import io.github.composefluent.winrt.runtime.WinRtBindableIterableProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorProjection
import io.github.composefluent.winrt.runtime.WinRtBindableVectorViewProjection
import io.github.composefluent.winrt.runtime.WinRtCollectionInterfaceIds
import io.github.composefluent.winrt.runtime.WinRtDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtIterableProjection
import io.github.composefluent.winrt.runtime.WinRtListProjection
import io.github.composefluent.winrt.runtime.WinRtAsyncActionReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncActionWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressReference
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationWithProgressVftblSlots
import io.github.composefluent.winrt.runtime.WinRtAsyncOperationVftblSlots
import io.github.composefluent.winrt.runtime.WinRtReadOnlyDictionaryProjection
import io.github.composefluent.winrt.runtime.WinRtReadOnlyListProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceArrayProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceProjection
import io.github.composefluent.winrt.runtime.WinRtReferenceValueAdapter
import io.github.composefluent.winrt.runtime.WinRtPlatformApi
import io.github.composefluent.winrt.runtime.WinRtTypeSignature
import io.github.composefluent.winrt.runtime.WinRtTypeHandle
import io.github.composefluent.winrt.runtime.WinRtUri
import io.github.composefluent.winrt.runtime.WinRtDelegateBridge
import io.github.composefluent.winrt.runtime.WinRtDelegateDescriptor
import io.github.composefluent.winrt.runtime.WinRtDelegateReference
import io.github.composefluent.winrt.runtime.WinRtDelegateValueKind
import io.github.composefluent.winrt.runtime.WinRtEvent
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

class KotlinProjectionGenerator(
    private val planner: KotlinProjectionPlanner = KotlinProjectionPlanner(),
    private val renderer: KotlinProjectionRenderer = KotlinProjectionRenderer(),
    private val supportRenderer: KotlinProjectionSupportRenderer = KotlinProjectionSupportRenderer(),
    private val emitSupportFiles: Boolean = false,
    private val projectionContext: WinRtMetadataProjectionContext = WinRtMetadataProjectionContext(sources = emptyList()),
    private val suppressedProjectionTypeNames: Set<String> = emptySet(),
    private val generationLayout: KotlinProjectionGenerationLayout = KotlinProjectionGenerationLayout.SingleSourceSet,
) {
    init {
        require(emitSupportFiles || projectionContext.sources.isEmpty()) {
            "KotlinProjectionGenerator requires emitSupportFiles=true when a projection context is supplied."
        }
    }

    fun generate(model: WinRtMetadataModel): List<KotlinProjectionFile> {
        val normalizedModel = model.normalized()
        val plans = planner.plan(normalizedModel)
        validateGeneratorContracts(normalizedModel, plans)
        val projectionRenderer = projectionFileRenderer()
        val projectionFiles = plans
            .filterNot { it.type.qualifiedName in authoredProjectedTypeNames(normalizedModel) }
            .flatMap(projectionRenderer::render)
        if (!emitSupportFiles) {
            return projectionFiles
        }
        return projectionFiles + supportFiles(normalizedModel, plans)
    }

    fun generateTo(model: WinRtMetadataModel, outputRoot: Path): KotlinProjectionWriteSummary {
        val normalizedModel = model.normalized()
        val plans = planner.plan(normalizedModel)
        validateGeneratorContracts(normalizedModel, plans)
        val authoredTypeNames = authoredProjectedTypeNames(normalizedModel)
        val projectionRenderer = projectionFileRenderer()
        var rendered = 0
        var written = 0
        val expectedPaths = mutableSetOf<String>()
        fun write(file: KotlinProjectionFile) {
            rendered += 1
            expectedPaths += outputRoot.resolve(file.relativePath).toAbsolutePath().normalize().toString()
            if (file.writeToIfChanged(outputRoot)) {
                written += 1
            }
        }
        plans.filterNot { it.type.qualifiedName in authoredTypeNames }.forEach { plan ->
            projectionRenderer.render(plan).forEach(::write)
        }
        if (emitSupportFiles) {
            supportFiles(normalizedModel, plans).forEach(::write)
        }
        val deleted = deleteStaleGeneratedFiles(outputRoot, expectedPaths)
        return KotlinProjectionWriteSummary(
            renderedFiles = rendered,
            writtenFiles = written,
            unchangedFiles = rendered - written,
            deletedStaleFiles = deleted,
        )
    }

    private fun authoredProjectedTypeNames(model: WinRtMetadataModel): Set<String> =
        if (!projectionContext.component) {
            suppressedProjectionTypeNames
        } else {
            model.projectionInventory(projectionContext)
                .authoredMetadataTypeMappings
                .mapTo(suppressedProjectionTypeNames.toMutableSet()) { it.projectedTypeName }
        }

    private fun validateGeneratorContracts(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        validateAuthoredCcwBindingContracts(model, plans)
        validateAuthoringActivationFactorySupportContracts(model, plans)
        plans.forEach { plan ->
            plan.type.events.filter { event -> shouldValidateEventAbiContracts(plan, event) }.forEach { event ->
                validateEventDelegateContract(plan, event)
            }
        }
        validateEventAccessorBindingContracts(plans)
        validateEventSourceHelperContracts(model, plans)
        val runtimeClassStaticInterfaceNames = plans
            .filter { plan -> plan.type.kind == WinRtTypeKind.RuntimeClass }
            .flatMap { plan -> plan.staticInterfaceNames }
            .toSet()
        plans.forEach { plan ->
            if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
                plan.activatableFactoryInterfaceName?.let { factoryName ->
                    val factoryType = plan.typesByQualifiedName[factoryName]
                    require(factoryType?.kind == WinRtTypeKind.Interface) {
                        "Generator requires runtime class ${plan.type.qualifiedName} activatable factory interface $factoryName to be present in the metadata model."
                    }
                    require(plan.activatableFactoryInterfaceIid != null) {
                        "Generator requires runtime class ${plan.type.qualifiedName} activatable factory interface $factoryName to carry metadata IID before projection rendering."
                    }
                    validateFactoryCreateBindingContracts(plan, factoryType)
                }
            }
            if (KotlinProjectionCompanionKind.StaticInterfaces in plan.companionKinds) {
                plan.staticInterfaceBindings.forEach { binding ->
                    val staticType = plan.typesByQualifiedName[binding.qualifiedName]
                    require(staticType?.kind == WinRtTypeKind.Interface) {
                        "Generator requires runtime class ${plan.type.qualifiedName} static interface ${binding.qualifiedName} to be present in the metadata model."
                    }
                    require(binding.iid != null) {
                        "Generator requires runtime class ${plan.type.qualifiedName} static interface ${binding.qualifiedName} to carry metadata IID before projection rendering."
                    }
                }
            }
            if (plan.requiresDefaultInterfaceContract()) {
                val defaultInterfaceName = requireNotNull(plan.defaultInterfaceName) {
                    "Generator requires runtime class ${plan.type.qualifiedName} to carry default interface metadata before projection rendering."
                }
                val defaultInterfaceType = plan.typesByQualifiedName[defaultInterfaceName]
                    ?: plan.typesByQualifiedName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                require(defaultInterfaceType?.kind == WinRtTypeKind.Interface) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to be present in the metadata model."
                }
                require(plan.defaultInterfaceIid != null) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to carry metadata IID before projection rendering."
                }
            }
            if (plan.type.kind == WinRtTypeKind.RuntimeClass) {
                plan.implementedInterfaceBindings
                    .filterNot { isMappedCollectionInterfaceName(it.qualifiedName) }
                    .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
                    .forEach { binding ->
                        val interfaceType = projectionType(binding.qualifiedName, plan)
                        require(interfaceType?.kind == WinRtTypeKind.Interface) {
                            "Generator requires runtime class ${plan.type.qualifiedName} implemented interface ${binding.qualifiedName} to be present in the metadata model."
                        }
                        require(binding.iid != null) {
                            "Generator requires runtime class ${plan.type.qualifiedName} implemented interface ${binding.qualifiedName} to carry metadata IID before projection rendering."
                        }
                    }
            }
            plan.requiredInterfaceAugmentationDescriptor
                ?.requiredInterfaceNames
                .orEmpty()
                .asSequence()
                .filterNot(::isMappedCollectionInterfaceName)
                .filterNot(::isRuntimeOwnedMappedTypeName)
                .forEach { requiredInterfaceName ->
                    val interfaceType = requiredInterfaceType(requiredInterfaceName, plan)
                    require(interfaceType?.kind == WinRtTypeKind.Interface) {
                        "Generator requires ${plan.projectionContractSubject()} required interface $requiredInterfaceName to be present in the metadata model."
                    }
                    require(interfaceType.iid != null) {
                        "Generator requires ${plan.projectionContractSubject()} required interface $requiredInterfaceName to carry metadata IID before projection rendering."
                    }
                }
            validateDelegateInvokeBindingContracts(plan)
            validateInstanceMethodBindingContracts(plan)
            validateInstancePropertyBindingContracts(plan)
            plan.instanceMemberBindings.forEach { binding ->
                if (binding.isInstanceEventAccessorBinding(plan)) {
                    return@forEach
                }
                validateProjectedAbiBindingContract(
                    plan,
                    binding.bindingName,
                    binding.returnBinding,
                    binding.parameterBindings,
                    binding.marshalerPlanDescriptor,
                    binding.suppressHResultCheck,
                    validateAbiCallPlan = plan.type.qualifiedName !in runtimeClassStaticInterfaceNames,
                )
            }
            plan.staticMemberBindings.forEach { binding ->
                if (binding.isStaticEventAccessorBinding(plan)) {
                    return@forEach
                }
                validateProjectedAbiBindingContract(
                    plan,
                    binding.bindingName,
                    binding.returnBinding,
                    binding.parameterBindings,
                    binding.marshalerPlanDescriptor,
                    binding.suppressHResultCheck,
                    validateAbiCallPlan = false,
                )
            }
            validateStaticMethodBindingContracts(plan)
            validateStaticPropertyBindingContracts(plan)
            validateProjectedAttributeContracts(plan)
            if (KotlinProjectionCompanionKind.ComposableFactory in plan.companionKinds) {
                val factoryName = plan.composableFactoryInterfaceName
                    ?: throw IllegalArgumentException(
                        "Generator requires runtime class ${plan.type.qualifiedName} to carry composable factory interface metadata before projection rendering.",
                    )
                val factoryType = plan.typesByQualifiedName[factoryName]
                require(factoryType?.kind == WinRtTypeKind.Interface) {
                    "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface $factoryName to be present in the metadata model."
                }
                require(plan.composableFactoryInterfaceIid != null) {
                    "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface $factoryName to carry metadata IID before projection rendering."
                }
                val defaultInterfaceName = plan.defaultInterfaceName
                    ?: throw IllegalArgumentException(
                        "Generator requires runtime class ${plan.type.qualifiedName} composable projection to carry default interface metadata before projection rendering.",
                    )
                val defaultInterfaceType = plan.typesByQualifiedName[defaultInterfaceName]
                    ?: plan.typesByQualifiedName[defaultInterfaceName.substringBefore('<').removeSuffix("?")]
                require(defaultInterfaceType?.kind == WinRtTypeKind.Interface) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to be present in the metadata model for composable projection."
                }
                require(plan.defaultInterfaceIid != null) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to carry metadata IID for composable projection."
                }
                validateComposableFactoryCreateBindingContracts(plan, factoryType)
            }
        }
    }

    private fun validateEventSourceHelperContracts(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRtNamespace::types)
            .associateBy(WinRtTypeDefinition::qualifiedName)
        val plansByType = plans.associateBy { plan -> plan.type.qualifiedName }
        val descriptorsByOwnerAndEvent = model.semanticHelpers()
            .let { helpers ->
                model.namespaces
                    .flatMap(WinRtNamespace::types)
                    .flatMap(helpers::eventHelperSubclassDescriptors)
            }
            .associateBy { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName }
        plans.forEach { plan ->
            validateRuntimeClassEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
            validateStaticEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
            validateInterfaceNativeProjectionEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
        }
    }

    private fun validateRuntimeClassEventSourceHelperContracts(
        plan: KotlinTypeProjectionPlan,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRtEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return
        }
        plan.type.events
            .filterNot(WinRtEventDefinition::isStatic)
            .forEach { event ->
                val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                    ?: return@forEach
                val eventTypeName = plan.eventInvokeDescriptors
                    .firstOrNull { it.eventName == event.name && !it.isStatic }
                    ?.delegateTypeName
                    ?: event.delegateTypeName
                validateEventSourceHelperContract(
                    plan,
                    event,
                    binding.slotInterfaceQualifiedName,
                    eventTypeName,
                    descriptorsByOwnerAndEvent,
                    typesByQualifiedName,
                    plansByType,
                )
            }
    }

    private fun validateStaticEventSourceHelperContracts(
        plan: KotlinTypeProjectionPlan,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRtEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return
        }
        eventSourceStaticEvents(plan)
            .forEach { event ->
                val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == "STATIC_${event.name.uppercase()}_ADD_SLOT" }
                    ?: return@forEach
                val eventTypeName = plan.eventInvokeDescriptors
                    .firstOrNull { it.eventName == event.name && it.isStatic }
                    ?.delegateTypeName
                    ?: event.delegateTypeName
                validateEventSourceHelperContract(
                    plan,
                    event,
                    binding.ownerInterfaceQualifiedName,
                    eventTypeName,
                    descriptorsByOwnerAndEvent,
                    typesByQualifiedName,
                    plansByType,
                )
            }
    }

    private fun eventSourceStaticEvents(plan: KotlinTypeProjectionPlan): List<WinRtEventDefinition> {
        val merged = linkedMapOf<String, WinRtEventDefinition>()
        plan.type.events
            .filter(WinRtEventDefinition::isStatic)
            .forEach { event ->
                merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
            }
        plan.staticInterfaceNames
            .mapNotNull(plan.typesByQualifiedName::get)
            .flatMap(WinRtTypeDefinition::events)
            .forEach { event ->
                merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
            }
        return merged.values.toList()
    }

    private fun validateInterfaceNativeProjectionEventSourceHelperContracts(
        plan: KotlinTypeProjectionPlan,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRtEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRtTypeKind.Interface || !canRenderInterfaceWrapperSafely(plan)) {
            return
        }
        renderer.collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.events
                .filterNot(WinRtEventDefinition::isStatic)
                .forEach { event ->
                    val eventTypeName = plan.typesByQualifiedName[interfaceType.qualifiedName]
                        ?.events
                        ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                        ?.delegateTypeName
                        ?: event.delegateTypeName
                    validateEventSourceHelperContract(
                        plan,
                        event,
                        interfaceType.qualifiedName,
                        eventTypeName,
                        descriptorsByOwnerAndEvent,
                        typesByQualifiedName,
                        plansByType,
                    )
                }
        }
    }

    private fun canRenderInterfaceWrapperSafely(plan: KotlinTypeProjectionPlan): Boolean =
        runCatching { renderer.canRenderInterfaceWrapper(plan) }.getOrDefault(false)

    private fun validateEventSourceHelperContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        ownerTypeName: String,
        eventTypeName: String,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRtEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRtTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        val descriptor = descriptorsByOwnerAndEvent[ownerTypeName to eventTypeName]
        require(descriptor != null) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} event-source helper for $ownerTypeName::$eventTypeName to be present before projection rendering."
        }
        require(supportRenderer.canRenderEventSourceHelper(descriptor, typesByQualifiedName, plansByType)) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} event-source helper for $ownerTypeName::$eventTypeName to use supported event-source ABI metadata before projection rendering."
        }
    }

    private fun validateProjectedAttributeContracts(plan: KotlinTypeProjectionPlan) {
        plan.projectedAttributes.forEach { attribute ->
            validateProjectedAttributeContract(plan, "type", attribute)
        }
        plan.instanceMemberBindings.forEach { binding ->
            binding.projectedAttributes.forEach { attribute ->
                validateProjectedAttributeContract(plan, "ABI binding ${binding.bindingName}", attribute)
            }
        }
        plan.staticMemberBindings.forEach { binding ->
            binding.projectedAttributes.forEach { attribute ->
                validateProjectedAttributeContract(plan, "ABI binding ${binding.bindingName}", attribute)
            }
        }
    }

    private fun validateProjectedAttributeContract(
        plan: KotlinTypeProjectionPlan,
        ownerLabel: String,
        attribute: WinRtProjectedAttributeDescriptor,
    ) {
        require(renderProjectedAttributeAnnotation(attribute) != null) {
            "Generator requires ${plan.projectionContractSubject()} $ownerLabel projected attribute ${attribute.metadataTypeName} to use renderable attribute metadata before projection rendering."
        }
    }

    private fun validateAuthoredCcwBindingContracts(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        if (!projectionContext.component) {
            return
        }
        val authoredTypeNames = model.projectionInventory(projectionContext)
            .authoredMetadataTypeMappings
            .mapTo(mutableSetOf()) { it.projectedTypeName }
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val plansByQualifiedName = plans.associateBy { it.type.qualifiedName }
        plans
            .filter { plan -> plan.type.kind == WinRtTypeKind.RuntimeClass && plan.type.qualifiedName in authoredTypeNames }
            .forEach { authoredPlan ->
                authoredPlan.type.implementedInterfaces
                    .map { implementation -> implementation.interfaceName.substringBefore('<') }
                    .distinct()
                    .forEach { interfaceName ->
                        val interfacePlan = plansByQualifiedName[interfaceName]
                        require(interfacePlan != null) {
                            "Generator requires authored runtime class ${authoredPlan.type.qualifiedName} CCW interface $interfaceName to have a projection plan before support rendering."
                        }
                        validateAuthoredCcwInterfaceBindingContracts(authoredPlan, interfacePlan)
                    }
        }
    }

    private fun validateAuthoringActivationFactorySupportContracts(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        if (!projectionContext.component) {
            return
        }
        val authoredTypeNames = model.projectionInventory(projectionContext)
            .authoredMetadataTypeMappings
            .mapTo(mutableSetOf()) { it.projectedTypeName }
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val semanticHelpers = model.semanticHelpers()
        plans
            .filter { plan -> plan.type.kind == WinRtTypeKind.RuntimeClass && plan.type.qualifiedName in authoredTypeNames }
            .filterNot { plan -> semanticHelpers.isStatic(plan.type) }
            .forEach { plan ->
                val factory = plan.factorySurfaceDescriptor ?: semanticHelpers.factorySurfaceDescriptor(plan.type)
                val factoryInterfaceNames = (
                    factory.constructorFactories +
                        factory.staticMemberTargets +
                        factory.composableFactories
                    ).distinct()
                factoryInterfaceNames.forEach { interfaceName ->
                    val interfaceType = plan.typesByQualifiedName[interfaceName]
                        ?: plan.typesByQualifiedName[interfaceName.substringBefore('<').removeSuffix("?")]
                    require(interfaceType?.kind == WinRtTypeKind.Interface) {
                        "Generator requires authored runtime class ${plan.type.qualifiedName} activation factory interface $interfaceName to be present in the metadata model before authoring support rendering."
                    }
                    require(interfaceType.iid != null) {
                        "Generator requires authored runtime class ${plan.type.qualifiedName} activation factory interface $interfaceName to carry metadata IID before authoring support rendering."
                    }
                }
            }
    }

    private fun validateAuthoredCcwInterfaceBindingContracts(
        authoredPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
    ) {
        val interfaceType = interfacePlan.type
        interfaceType.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method ->
                val bindingName = method.abiSlotConstantName(interfaceType.methods)
                validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, bindingName)
            }
        interfaceType.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .forEach { property ->
                if (property.hasNativeProjectionGetterAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${property.name.uppercase()}_GETTER_SLOT")
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${property.name.uppercase()}_SETTER_SLOT")
                }
            }
        interfaceType.events
            .filterNot(WinRtEventDefinition::isStatic)
            .forEach { event ->
                if (event.hasNativeProjectionAddAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${event.name.uppercase()}_ADD_SLOT")
                }
                if (event.hasNativeProjectionRemoveAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${event.name.uppercase()}_REMOVE_SLOT")
                }
            }
        interfacePlan.instanceMemberBindings.forEach { binding ->
            val event = interfaceType.events.firstOrNull { event ->
                binding.bindingName == "${event.name.uppercase()}_ADD_SLOT" ||
                    binding.bindingName == "${event.name.uppercase()}_REMOVE_SLOT"
            }
            if (event != null) {
                return@forEach
            }
            if (authoredCcwBindingHasIntentionalFallback(interfaceType, binding)) {
                return@forEach
            }
            authoredCcwBindingUnsupportedReason(renderer, interfaceType, binding)?.let { reason ->
                throw IllegalArgumentException(
                    "Generator requires authored runtime class ${authoredPlan.type.qualifiedName} CCW binding ${interfaceType.qualifiedName}.${binding.bindingName} to use supported authored ABI metadata before support rendering; unsupported $reason.",
                )
            }
        }
    }

    private fun validateAuthoredCcwSlotMetadataContract(
        authoredPlan: KotlinTypeProjectionPlan,
        interfacePlan: KotlinTypeProjectionPlan,
        bindingName: String,
    ) {
        val interfaceType = interfacePlan.type
        require(interfacePlan.instanceMemberBindings.any { it.bindingName == bindingName }) {
            "Generator requires authored runtime class ${authoredPlan.type.qualifiedName} CCW binding ${interfaceType.qualifiedName}.$bindingName to be present before support rendering."
        }
        require(interfacePlan.abiSlotBindings.any { it.constantName == bindingName }) {
            "Generator requires authored runtime class ${authoredPlan.type.qualifiedName} CCW binding ${interfaceType.qualifiedName}.$bindingName to carry ABI slot metadata before support rendering."
        }
    }

    private fun validateFactoryCreateBindingContracts(
        plan: KotlinTypeProjectionPlan,
        factoryType: WinRtTypeDefinition,
    ) {
        factoryType.methods
            .filter(WinRtMethodDefinition::isProjectedCallableMethod)
            .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
            .forEach { method ->
                validateFactoryCreateAbiBindingContract(
                    plan = plan,
                    factoryType = factoryType,
                    method = method,
                    returnBinding = KotlinProjectionAbiTypeBinding(
                        kind = KotlinProjectionAbiValueKind.InspectableReference,
                        typeName = IINSPECTABLE_REFERENCE_CLASS_NAME.canonicalName,
                    ),
                    parameters = method.parameters,
                )
            }
    }

    private fun validateComposableFactoryCreateBindingContracts(
        plan: KotlinTypeProjectionPlan,
        factoryType: WinRtTypeDefinition,
    ) {
        factoryType.methods
            .filter(WinRtMethodDefinition::isProjectedCallableMethod)
            .filter { method -> method.returnType.typeName == plan.type.qualifiedName }
            .forEach { method ->
                val userParameters = requireComposableFactoryUserParameters(plan, factoryType, method)
                validateFactoryCreateAbiBindingContract(
                    plan = plan,
                    factoryType = factoryType,
                    method = method,
                    returnBinding = KotlinProjectionAbiTypeBinding(KotlinProjectionAbiValueKind.Unit, "Unit"),
                    parameters = userParameters,
                )
            }
    }

    private fun requireComposableFactoryUserParameters(
        plan: KotlinTypeProjectionPlan,
        factoryType: WinRtTypeDefinition,
        method: WinRtMethodDefinition,
    ): List<WinRtParameterDefinition> {
        require(method.parameters.size >= 2) {
            "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface ${factoryType.qualifiedName} method ${method.name} to end with baseInterface and innerInterface object parameters before projection rendering."
        }
        val trailing = method.parameters.takeLast(2)
        require(trailing.all { parameter -> isWinRtObjectTypeName(parameter.type.typeName) }) {
            "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface ${factoryType.qualifiedName} method ${method.name} to end with baseInterface and innerInterface object parameters before projection rendering."
        }
        return method.parameters.dropLast(2)
    }

    private fun validateFactoryCreateAbiBindingContract(
        plan: KotlinTypeProjectionPlan,
        factoryType: WinRtTypeDefinition,
        method: WinRtMethodDefinition,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameters: List<WinRtParameterDefinition>,
    ) {
        validateProjectedAbiBindingContract(
            plan = plan,
            bindingName = "${factoryType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameters.map { parameter ->
                KotlinProjectionAbiParameterBinding(
                    name = parameter.name,
                    typeBinding = KotlinProjectionPlanner().classifyAbiTypeBinding(
                        typeName = parameter.typeName,
                        currentNamespace = factoryType.namespace,
                        typesByQualifiedName = plan.typesByQualifiedName,
                    ),
                )
            },
        )
    }

    private fun composableFactoryUserParameters(
        method: WinRtMethodDefinition,
    ): Pair<WinRtMethodDefinition, List<WinRtParameterDefinition>>? {
        if (isWinRtVoidTypeName(method.returnType.typeName) || method.parameters.size < 2) {
            return null
        }
        val trailing = method.parameters.takeLast(2)
        if (trailing.any { parameter -> !isWinRtObjectTypeName(parameter.type.typeName) }) {
            return null
        }
        return method to method.parameters.dropLast(2)
    }

    private fun validateInstanceMethodBindingContracts(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return
        }
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.methods
            .filter(WinRtMethodDefinition::isOrdinaryProjectedMethod)
            .filter { method -> method.methodRowId != null }
            .filterNot { method -> method.hasKotlinOwnedRuntimeMethodProjection(plan, mappedCollectionMemberNames) }
            .forEach { method ->
                val bindingName = method.abiSlotConstantName(plan.type.methods)
                require(plan.instanceMemberBindings.any { it.bindingName == bindingName }) {
                    "Generator requires ${plan.projectionContractSubject()} method ${method.name} binding $bindingName to be present before projection rendering."
                }
            }
    }

    private fun WinRtMethodDefinition.hasKotlinOwnedRuntimeMethodProjection(
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ): Boolean =
        isMappedCollectionRuntimeMethod(plan, mappedCollectionMemberNames) ||
            plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().let { augmentations ->
                augmentations.contains("IDisposable") && name == "Close" ||
                    augmentations.contains("INotifyDataErrorInfo") && name == "GetErrors"
            }

    private fun validateStaticMethodBindingContracts(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return
        }
        val staticMethods = plan.type.methods.filter(WinRtMethodDefinition::isOrdinaryProjectedStaticMethod)
        mergedStaticMethods(plan, staticMethods).forEach { method ->
            if (isActivationFactoryCreateMethod(plan, method)) {
                return@forEach
            }
            val bindingName = staticMethodBindingName(plan, method)
            val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == bindingName }
            if (binding == null) {
                require(method.methodRowId == null) {
                    "Generator requires runtime class ${plan.type.qualifiedName} static method ${method.name} binding $bindingName to be present before projection rendering."
                }
                return@forEach
            }
            validateProjectedAbiBindingContract(
                plan,
                binding.bindingName,
                binding.returnBinding,
                binding.parameterBindings,
                binding.marshalerPlanDescriptor,
                binding.suppressHResultCheck,
            )
        }
    }

    private fun validateDelegateInvokeBindingContracts(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRtTypeKind.Delegate) {
            return
        }
        val invokeShape = plan.delegateInvokeShape ?: return
        require(invokeShape.interfaceId != null) {
            "Generator requires delegate ${plan.type.qualifiedName} Invoke to carry metadata IID before projection rendering."
        }
        validateProjectedAbiBindingContract(
            plan = plan,
            bindingName = "Invoke",
            returnBinding = invokeShape.returnBinding,
            parameterBindings = invokeShape.parameterBindings,
            delegateInvokeContext = true,
        )
    }

    private fun validateInstancePropertyBindingContracts(plan: KotlinTypeProjectionPlan) {
        plan.type.properties
            .filterNot(WinRtPropertyDefinition::isStatic)
            .forEach { property ->
                if (property.hasNativeProjectionGetterAccessor()) {
                    val getterBindingName = "${property.name.uppercase()}_GETTER_SLOT"
                    require(plan.instanceMemberBindings.any { it.bindingName == getterBindingName }) {
                        "Generator requires ${plan.projectionContractSubject()} property ${property.name} getter binding $getterBindingName to be present before projection rendering."
                    }
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    val setterBindingName = "${property.name.uppercase()}_SETTER_SLOT"
                    require(plan.instanceMemberBindings.any { it.bindingName == setterBindingName }) {
                        "Generator requires ${plan.projectionContractSubject()} property ${property.name} setter binding $setterBindingName to be present before projection rendering."
                    }
                }
            }
    }

    private fun validateStaticPropertyBindingContracts(plan: KotlinTypeProjectionPlan) {
        val staticProperties = plan.type.properties.filter { it.isStatic }
        mergedStaticProperties(plan, staticProperties)
            .forEach { property ->
                if (property.hasNativeProjectionGetterAccessor()) {
                    val getterBindingName = "STATIC_${property.name.uppercase()}_GETTER_SLOT"
                    val getterBinding = plan.staticMemberBindings.firstOrNull { it.bindingName == getterBindingName }
                    if (getterBinding == null) {
                        require(property.getterMethodRowId == null) {
                            "Generator requires runtime class ${plan.type.qualifiedName} static property ${property.name} getter binding $getterBindingName to be present before projection rendering."
                        }
                        return@forEach
                    }
                    validateProjectedAbiBindingContract(
                        plan,
                        getterBinding.bindingName,
                        getterBinding.returnBinding,
                        getterBinding.parameterBindings,
                        getterBinding.marshalerPlanDescriptor,
                        getterBinding.suppressHResultCheck,
                    )
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    val setterBindingName = "STATIC_${property.name.uppercase()}_SETTER_SLOT"
                    val setterBinding = plan.staticMemberBindings.firstOrNull { it.bindingName == setterBindingName }
                    if (setterBinding == null) {
                        require(property.setterMethodRowId == null) {
                            "Generator requires runtime class ${plan.type.qualifiedName} static property ${property.name} setter binding $setterBindingName to be present before projection rendering."
                        }
                        return@forEach
                    }
                    validateProjectedAbiBindingContract(
                        plan,
                        setterBinding.bindingName,
                        setterBinding.returnBinding,
                        setterBinding.parameterBindings,
                        setterBinding.marshalerPlanDescriptor,
                        setterBinding.suppressHResultCheck,
                    )
                }
            }
    }

    private fun validateEventDelegateContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ) {
        val delegateTypeName = event.delegateTypeName
        val rawDelegateTypeName = delegateTypeName.substringBefore('<').removeSuffix("?")
        if (mappedTypeByAbiName(rawDelegateTypeName) != null) {
            validateMappedEventDelegateContract(plan, event, rawDelegateTypeName)
            return
        }
        val delegateType = eventDelegateType(delegateTypeName, plan)
        require(delegateType?.kind == WinRtTypeKind.Delegate) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} delegate $delegateTypeName to be present in the metadata model."
        }
        require(delegateType.iid != null) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} delegate $delegateTypeName to carry metadata IID before projection rendering."
        }
        requireDelegateInvokeMethod(delegateType)
    }

    private fun validateMappedEventDelegateContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        rawDelegateTypeName: String,
    ) {
        val expectedArgumentCount = when (winRtEventHandlerKindForTypeName(rawDelegateTypeName)) {
            WinRtEventHandlerKind.EventHandler,
            WinRtEventHandlerKind.VectorChangedEventHandler,
            WinRtEventHandlerKind.AsyncActionProgressHandler -> 1
            WinRtEventHandlerKind.TypedEventHandler,
            WinRtEventHandlerKind.MapChangedEventHandler,
            WinRtEventHandlerKind.AsyncOperationProgressHandler -> 2
            WinRtEventHandlerKind.PropertyChangedEventHandler,
            WinRtEventHandlerKind.NotifyCollectionChangedEventHandler -> 0
            null -> return
        }
        val argumentCount = WinRtTypeRef.fromDisplayName(event.delegateTypeName)
            .normalized()
            .typeArguments
            .size
        require(argumentCount == expectedArgumentCount) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} mapped delegate ${event.delegateTypeName} to carry $expectedArgumentCount generic argument(s) before projection rendering; found $argumentCount."
        }
    }

    private fun validateEventAccessorBindingContracts(plan: KotlinTypeProjectionPlan) {
        plan.type.events
            .filterNot { it.isStatic }
            .filter { event -> shouldValidateEventAbiContracts(plan, event) }
            .forEach { event ->
                validateInstanceEventAccessorBindingContract(plan, event)
            }
        validateStaticEventAccessorBindingContract(plan)
    }

    private fun validateEventAccessorBindingContracts(plans: List<KotlinTypeProjectionPlan>) {
        plans.forEach(::validateEventAccessorBindingContracts)
    }

    private fun validateStaticEventAccessorBindingContract(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return
        }
        buildList {
            addAll(plan.type.events.filter(WinRtEventDefinition::isStatic))
            plan.staticInterfaceNames
                .mapNotNull(plan.typesByQualifiedName::get)
                .forEach { staticInterface -> addAll(staticInterface.events) }
        }
            .asSequence()
            .map { it.copy(isStatic = true) }
            .distinctBy { "${it.name}|${it.delegateTypeName}" }
            .filter { event -> shouldValidateEventAbiContracts(plan, event) }
            .forEach { event ->
                validateStaticEventAccessorBindingContract(plan, event)
            }
    }

    private fun shouldValidateEventAbiContracts(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ): Boolean =
        generationLayout != KotlinProjectionGenerationLayout.ExpectActualJvm ||
            plan.type.kind != WinRtTypeKind.RuntimeClass ||
            !event.isStatic

    private fun validateInstanceEventAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ) {
        validateEventAccessorPairContract(plan, event)
        if (event.hasNativeProjectionAddAccessor()) {
            val bindingName = "${event.name.uppercase()}_ADD_SLOT"
            val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName }
            require(binding != null) {
                "Generator requires ${plan.projectionContractSubject()} event ${event.name} add binding $bindingName to be present before projection rendering."
            }
            validateEventAddAccessorBindingContract(plan, event, bindingName, binding)
        }
        if (event.hasNativeProjectionRemoveAccessor()) {
            val bindingName = "${event.name.uppercase()}_REMOVE_SLOT"
            val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == bindingName }
            require(binding != null) {
                "Generator requires ${plan.projectionContractSubject()} event ${event.name} remove binding $bindingName to be present before projection rendering."
            }
            validateEventRemoveAccessorBindingContract(plan, event, bindingName, binding)
        }
    }

    private fun validateStaticEventAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
    ) {
        validateEventAccessorPairContract(plan, event, staticEvent = true)
        if (event.hasNativeProjectionAddAccessor()) {
            val bindingName = "STATIC_${event.name.uppercase()}_ADD_SLOT"
            val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == bindingName }
            require(binding != null) {
                "Generator requires runtime class ${plan.type.qualifiedName} static event ${event.name} add binding $bindingName to be present before projection rendering."
            }
            validateEventAddAccessorBindingContract(plan, event, bindingName, binding)
        }
        if (event.hasNativeProjectionRemoveAccessor()) {
            val bindingName = "STATIC_${event.name.uppercase()}_REMOVE_SLOT"
            val binding = plan.staticMemberBindings.firstOrNull { it.bindingName == bindingName }
            require(binding != null) {
                "Generator requires runtime class ${plan.type.qualifiedName} static event ${event.name} remove binding $bindingName to be present before projection rendering."
            }
            validateEventRemoveAccessorBindingContract(plan, event, bindingName, binding)
        }
    }

    private fun validateEventAccessorPairContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        staticEvent: Boolean = false,
    ) {
        require(event.hasNativeProjectionAddAccessor() == event.hasNativeProjectionRemoveAccessor()) {
            val eventLabel = if (staticEvent) "static event" else "event"
            "Generator requires ${plan.projectionContractSubject()} $eventLabel ${event.name} to carry both add and remove accessor metadata before projection rendering."
        }
    }

    private fun validateEventAddAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        binding: KotlinProjectionInstanceMemberBinding,
    ) {
        validateEventAddAccessorBindingContract(
            plan,
            event,
            bindingName,
            binding.returnBinding,
            binding.parameterBindings,
            binding.marshalerPlanDescriptor,
            binding.suppressHResultCheck,
        )
    }

    private fun validateEventAddAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        binding: KotlinProjectionStaticMemberBinding,
    ) {
        validateEventAddAccessorBindingContract(
            plan,
            event,
            bindingName,
            binding.returnBinding,
            binding.parameterBindings,
            binding.marshalerPlanDescriptor,
            binding.suppressHResultCheck,
        )
    }

    private fun validateEventAddAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor?,
        suppressHResultCheck: Boolean,
        validateAbiCallPlan: Boolean = true,
    ) {
        require(returnBinding.isEventRegistrationTokenBinding()) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} add binding $bindingName to return Windows.Foundation.EventRegistrationToken before projection rendering; found ${returnBinding.describeAbiKind()}."
        }
        require(parameterBindings.size == 1 && parameterBindings.single().typeBinding.kind == KotlinProjectionAbiValueKind.Delegate) {
            val found = parameterBindings.singleOrNull()?.typeBinding?.describeAbiKind() ?: "${parameterBindings.size} parameter(s)"
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} add binding $bindingName to take exactly one delegate handler parameter before projection rendering; found $found."
        }
        if (validateAbiCallPlan) {
            validateProjectedAbiBindingContract(
                plan,
                bindingName,
                returnBinding,
                parameterBindings,
                marshalerPlanDescriptor,
                suppressHResultCheck,
            )
        }
    }

    private fun validateEventRemoveAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        binding: KotlinProjectionInstanceMemberBinding,
    ) {
        validateEventRemoveAccessorBindingContract(
            plan,
            event,
            bindingName,
            binding.returnBinding,
            binding.parameterBindings,
            binding.marshalerPlanDescriptor,
            binding.suppressHResultCheck,
        )
    }

    private fun validateEventRemoveAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        binding: KotlinProjectionStaticMemberBinding,
    ) {
        validateEventRemoveAccessorBindingContract(
            plan,
            event,
            bindingName,
            binding.returnBinding,
            binding.parameterBindings,
            binding.marshalerPlanDescriptor,
            binding.suppressHResultCheck,
        )
    }

    private fun validateEventRemoveAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRtEventDefinition,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor?,
        suppressHResultCheck: Boolean,
        validateAbiCallPlan: Boolean = true,
    ) {
        require(returnBinding.kind == KotlinProjectionAbiValueKind.Unit) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} remove binding $bindingName to return Unit before projection rendering; found ${returnBinding.describeAbiKind()}."
        }
        require(parameterBindings.size == 1 && parameterBindings.single().typeBinding.isEventRegistrationTokenBinding()) {
            val found = parameterBindings.singleOrNull()?.typeBinding?.describeAbiKind() ?: "${parameterBindings.size} parameter(s)"
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} remove binding $bindingName to take exactly one Windows.Foundation.EventRegistrationToken parameter before projection rendering; found $found."
        }
        if (validateAbiCallPlan) {
            validateProjectedAbiBindingContract(
                plan,
                bindingName,
                returnBinding,
                parameterBindings,
                marshalerPlanDescriptor,
                suppressHResultCheck,
            )
        }
    }

    private fun KotlinProjectionAbiTypeBinding.isEventRegistrationTokenBinding(): Boolean =
        resolvedTypeName.removeSuffix("?") == "Windows.Foundation.EventRegistrationToken" &&
            kind == KotlinProjectionAbiValueKind.Struct

    private fun KotlinProjectionInstanceMemberBinding.isInstanceEventAccessorBinding(
        plan: KotlinTypeProjectionPlan,
    ): Boolean =
        plan.type.events.any { event ->
            !event.isStatic &&
                (bindingName == "${event.name.uppercase()}_ADD_SLOT" ||
                    bindingName == "${event.name.uppercase()}_REMOVE_SLOT")
        }

    private fun KotlinProjectionStaticMemberBinding.isStaticEventAccessorBinding(
        plan: KotlinTypeProjectionPlan,
    ): Boolean {
        if (plan.type.kind != WinRtTypeKind.RuntimeClass) {
            return false
        }
        return buildList {
            addAll(plan.type.events.filter(WinRtEventDefinition::isStatic))
            plan.staticInterfaceNames
                .mapNotNull(plan.typesByQualifiedName::get)
                .forEach { staticInterface -> addAll(staticInterface.events) }
        }.any { event ->
            bindingName == "STATIC_${event.name.uppercase()}_ADD_SLOT" ||
                bindingName == "STATIC_${event.name.uppercase()}_REMOVE_SLOT"
        }
    }

    private fun validateProjectedAbiBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRtAbiMarshalerPlanDescriptor? = null,
        suppressHResultCheck: Boolean = marshalerPlanDescriptor?.hasNoExceptionAttribute == true,
        delegateInvokeContext: Boolean = false,
        validateAbiCallPlan: Boolean = true,
    ) {
        validateProjectedAbiTypeBindingContract(plan, bindingName, "return", returnBinding, delegateInvokeContext)
        parameterBindings.forEach { parameter ->
            validateProjectedAbiTypeBindingContract(
                plan,
                bindingName,
                "parameter ${parameter.name}",
                parameter.typeBinding,
                delegateInvokeContext,
            )
        }
        if (!delegateInvokeContext && validateAbiCallPlan) {
            renderer.requireAbiCallPlan(
                bindingName = bindingName,
                returnBinding = returnBinding,
                parameterBindings = parameterBindings,
                marshalerPlanDescriptor = marshalerPlanDescriptor,
                suppressHResultCheck = suppressHResultCheck,
            )
        }
    }

    private fun validateProjectedAbiTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
        delegateInvokeContext: Boolean = false,
    ) {
        if (typeBinding.kind == KotlinProjectionAbiValueKind.Delegate) {
            val invokeShape = typeBinding.delegateInvokeShape
            require(typeBinding.interfaceId != null || invokeShape?.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole delegate ${typeBinding.resolvedTypeName} to carry metadata IID before projection rendering."
            }
            if (typeBinding.typeArguments.isNotEmpty() &&
                typeBinding.typeArguments.none { argument -> argument.kind == KotlinProjectionAbiValueKind.Unsupported } &&
                invokeShape != null
            ) {
                require(renderer.delegateInterfaceIdCode(typeBinding, invokeShape) != null) {
                    "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole delegate ${typeBinding.resolvedTypeName} generic arguments to have renderable WinRT type signatures before projection rendering."
                }
            }
            invokeShape?.let { shape ->
                validateProjectedAbiTypeBindingContract(
                    plan,
                    bindingName,
                    "$bindingRole delegate ${typeBinding.resolvedTypeName} Invoke return",
                    shape.returnBinding,
                    delegateInvokeContext = true,
                )
                shape.parameterBindings.forEach { parameter ->
                    validateProjectedAbiTypeBindingContract(
                        plan,
                        bindingName,
                        "$bindingRole delegate ${typeBinding.resolvedTypeName} Invoke parameter ${parameter.name}",
                        parameter.typeBinding,
                        delegateInvokeContext = true,
                    )
                }
            }
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedRuntimeClass && customObjectAbi(typeBinding) == null) {
            require(typeBinding.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole runtime class ${typeBinding.resolvedTypeName} to carry default-interface metadata IID before projection rendering."
            }
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedInterface) {
            require(typeBinding.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole interface ${typeBinding.resolvedTypeName} to carry metadata IID before projection rendering."
            }
        }
        validateProjectedGenericTypeBindingContract(plan, bindingName, bindingRole, typeBinding)
        validateMappedReferenceTypeBindingContract(plan, bindingName, bindingRole, typeBinding)
        validateMappedAsyncTypeBindingContract(plan, bindingName, bindingRole, typeBinding)
        validateMappedCollectionTypeBindingContract(plan, bindingName, bindingRole, typeBinding)
        validateMappedKeyValuePairTypeBindingContract(plan, bindingName, bindingRole, typeBinding)
        if (typeBinding.kind == KotlinProjectionAbiValueKind.Array) {
            require(typeBinding.typeArguments.size == 1) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole array ${typeBinding.resolvedTypeName} to carry exactly one element ABI binding before projection rendering."
            }
            if (delegateInvokeContext) {
                val elementKind = typeBinding.typeArguments.single().kind
                require(elementKind == KotlinProjectionAbiValueKind.UInt8) {
                    "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole delegate array ${typeBinding.resolvedTypeName} to use supported delegate array ABI metadata before projection rendering; found ${typeBinding.describeAbiKind()}."
                }
            }
        }
        require(typeBinding.kind != KotlinProjectionAbiValueKind.Unsupported) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole to use supported ABI metadata before projection rendering; found ${typeBinding.describeAbiKind()}."
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.Enum) {
            require(typeBinding.enumUnderlyingType != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole enum ${typeBinding.resolvedTypeName} to carry underlying type metadata before projection rendering."
            }
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.Struct && customStructAbi(typeBinding) == null) {
            if (isRuntimeOwnedMappedTypeName(typeBinding.resolvedTypeName)) {
                return
            }
            val structType = plan.typesByQualifiedName[typeBinding.resolvedTypeName.removeSuffix("?")]
            require(structType?.kind == WinRtTypeKind.Struct) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole struct ${typeBinding.resolvedTypeName} to be present in the metadata model before projection rendering."
            }
            require(canRenderNativeStructMetadata(structType, plan)) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole struct ${typeBinding.resolvedTypeName} to carry renderable native struct layout metadata before projection rendering."
            }
        }
        typeBinding.typeArguments.forEach { argument ->
            validateProjectedAbiTypeBindingContract(plan, bindingName, bindingRole, argument, delegateInvokeContext)
        }
        validateMappedCollectionAdapterBindingContract(plan, bindingName, bindingRole, typeBinding)
    }

    private fun validateProjectedGenericTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        if (typeBinding.kind !in setOf(KotlinProjectionAbiValueKind.ProjectedInterface, KotlinProjectionAbiValueKind.Delegate)) {
            return
        }
        val resolvedTypeName = typeBinding.resolvedTypeName.substringBefore('<').removeSuffix("?")
        val resolvedType = plan.typesByQualifiedName[resolvedTypeName] ?: return
        val expectedArgumentCount = resolvedType.genericParameterCount
        require(typeBinding.typeArguments.size == expectedArgumentCount) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole ${resolvedType.kind.name.lowercase()} ${typeBinding.resolvedTypeName} to carry $expectedArgumentCount generic argument(s) before projection rendering; found ${typeBinding.typeArguments.size}."
        }
    }

    private fun validateMappedReferenceTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        if (typeBinding.kind !in setOf(KotlinProjectionAbiValueKind.Reference, KotlinProjectionAbiValueKind.ReferenceArray)) {
            return
        }
        require(typeBinding.interfaceId != null) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole reference ${typeBinding.resolvedTypeName} to carry parameterized-interface metadata IID before projection rendering."
        }
        require(typeBinding.typeArguments.size == 1) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole reference ${typeBinding.resolvedTypeName} to carry 1 type argument before projection rendering; found ${typeBinding.typeArguments.size}."
        }
        val argument = typeBinding.typeArguments.single()
        if (argument.kind != KotlinProjectionAbiValueKind.Unsupported) {
            require(renderer.referenceTypeSignatureCode(typeBinding) != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole reference ${typeBinding.resolvedTypeName} element ${argument.resolvedTypeName} to have a renderable WinRT type signature before projection rendering."
            }
        }
    }

    private fun validateMappedKeyValuePairTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        if (typeBinding.kind != KotlinProjectionAbiValueKind.MappedKeyValuePair) {
            return
        }
        require(typeBinding.typeArguments.size == 2) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole key-value pair ${typeBinding.resolvedTypeName} to carry 2 type argument(s) before projection rendering; found ${typeBinding.typeArguments.size}."
        }
        typeBinding.typeArguments
            .filterNot { argument -> argument.kind == KotlinProjectionAbiValueKind.Unsupported }
            .forEachIndexed { index, argument ->
                require(renderer.abiTypeSignature(argument) != null) {
                    val argumentRole = if (index == 0) "key" else "value"
                    "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole key-value pair ${typeBinding.resolvedTypeName} $argumentRole ${argument.resolvedTypeName} to have a renderable WinRT type signature before projection rendering."
                }
            }
    }

    private fun validateMappedCollectionTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        val expectedArgumentCount = when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedVectorView -> 1
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedMapView -> 2
            KotlinProjectionAbiValueKind.MappedBindableIterable,
            KotlinProjectionAbiValueKind.MappedBindableVector,
            KotlinProjectionAbiValueKind.MappedBindableVectorView -> 0
            else -> return
        }
        require(typeBinding.typeArguments.size == expectedArgumentCount) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole collection ${typeBinding.resolvedTypeName} to carry $expectedArgumentCount type argument(s) before projection rendering; found ${typeBinding.typeArguments.size}."
        }
    }

    private fun validateMappedCollectionAdapterBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        val adapterArguments = when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedIterable,
            KotlinProjectionAbiValueKind.MappedVector,
            KotlinProjectionAbiValueKind.MappedVectorView ->
                listOf("element" to typeBinding.typeArguments.singleOrNull())
            KotlinProjectionAbiValueKind.MappedMap,
            KotlinProjectionAbiValueKind.MappedMapView ->
                listOf(
                    "key" to typeBinding.typeArguments.getOrNull(0),
                    "value" to typeBinding.typeArguments.getOrNull(1),
                )
            else -> return
        }
        adapterArguments.forEach { (role, argument) ->
            require(argument != null && renderer.collectionReferenceAdapterCode(argument) != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole collection ${typeBinding.resolvedTypeName} $role ${argument?.resolvedTypeName ?: "<missing>"} to have a supported collection reference adapter before projection rendering."
            }
        }
    }

    private fun validateMappedAsyncTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        val expectedArgumentCount = when (typeBinding.kind) {
            KotlinProjectionAbiValueKind.MappedAsyncAction -> 0
            KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> 1
            KotlinProjectionAbiValueKind.MappedAsyncOperation -> 1
            KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> 2
            else -> return
        }
        require(typeBinding.typeArguments.size == expectedArgumentCount) {
            "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole async ${typeBinding.resolvedTypeName} to carry $expectedArgumentCount type argument(s) before projection rendering; found ${typeBinding.typeArguments.size}."
        }
        typeBinding.typeArguments.forEachIndexed { index, argument ->
            require(renderer.asyncOperationResultTypeSignature(argument) != null) {
                val asyncArgumentRole = when (typeBinding.kind) {
                    KotlinProjectionAbiValueKind.MappedAsyncActionWithProgress -> "progress"
                    KotlinProjectionAbiValueKind.MappedAsyncOperation -> "result"
                    KotlinProjectionAbiValueKind.MappedAsyncOperationWithProgress -> if (index == 0) "result" else "progress"
                    else -> "argument"
                }
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole async ${typeBinding.resolvedTypeName} $asyncArgumentRole ${argument.resolvedTypeName} to have a renderable WinRT type signature before projection rendering."
            }
        }
    }

    private fun canRenderNativeStructMetadata(
        type: WinRtTypeDefinition,
        plan: KotlinTypeProjectionPlan,
    ): Boolean =
        type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .all { field -> canRenderNativeStructField(field, type.namespace, plan) }

    private fun canRenderNativeStructField(
        field: WinRtFieldDefinition,
        currentNamespace: String,
        plan: KotlinTypeProjectionPlan,
    ): Boolean {
        if (isWinRtGuidTypeName(field.typeName) || winRtFundamentalTypeForName(field.typeName) != null) {
            return true
        }
        val rawTypeName = field.typeName.substringBefore('<').removeSuffix("?")
        if (isWinRtObjectTypeName(rawTypeName)) {
            return true
        }
        if (
            rawTypeName == IINSPECTABLE_REFERENCE_CLASS_NAME.simpleName ||
            rawTypeName == IUNKNOWN_REFERENCE_CLASS_NAME.simpleName ||
            rawTypeName == "io.github.composefluent.winrt.runtime.IInspectableReference" ||
            rawTypeName == "io.github.composefluent.winrt.runtime.IUnknownReference"
        ) {
            return true
        }
        val qualifiedName = when {
            plan.typesByQualifiedName.containsKey(rawTypeName) -> rawTypeName
            currentNamespace.isNotBlank() && plan.typesByQualifiedName.containsKey("$currentNamespace.$rawTypeName") -> "$currentNamespace.$rawTypeName"
            else -> plan.typesByQualifiedName.keys.firstOrNull { it.endsWith(".$rawTypeName") }
        }
        val resolvedType = qualifiedName?.let(plan.typesByQualifiedName::get)
        if (resolvedType?.kind == WinRtTypeKind.Interface || resolvedType?.kind == WinRtTypeKind.RuntimeClass) {
            return true
        }
        if (resolvedType?.kind == WinRtTypeKind.Enum) {
            return true
        }
        if (resolvedType?.kind == WinRtTypeKind.Struct) {
            return canRenderNativeStructMetadata(resolvedType, plan)
        }
        val mappedType = mappedTypeByAbiName(rawTypeName)
        return mappedType?.abiValueKind == KotlinProjectionAbiValueKind.Struct
    }

    private fun eventDelegateType(
        delegateTypeName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRtTypeDefinition? =
        projectionType(delegateTypeName, plan)

    private fun projectionType(
        typeName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRtTypeDefinition? {
        val rawName = typeName.substringBefore('<').removeSuffix("?")
        return plan.typesByQualifiedName[typeName]
            ?: plan.typesByQualifiedName[rawName]
            ?: plan.typesByQualifiedName["${plan.type.namespace}.$rawName"]
    }

    private fun requiredInterfaceType(
        requiredInterfaceName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRtTypeDefinition? {
        val rawName = requiredInterfaceName.substringBefore('<').removeSuffix("?")
        return plan.typesByQualifiedName[requiredInterfaceName]
            ?: plan.typesByQualifiedName[rawName]
            ?: plan.typesByQualifiedName["${plan.type.namespace}.$rawName"]
    }

    private fun KotlinTypeProjectionPlan.projectionContractSubject(): String {
        val kind = when (type.kind) {
            WinRtTypeKind.RuntimeClass -> "runtime class"
            WinRtTypeKind.Interface -> "interface"
            WinRtTypeKind.Delegate -> "delegate"
            WinRtTypeKind.Struct -> "struct"
            WinRtTypeKind.Enum -> "enum"
            WinRtTypeKind.Unknown -> "type"
        }
        return "$kind ${type.qualifiedName}"
    }

    private fun KotlinTypeProjectionPlan.requiresDefaultInterfaceContract(): Boolean {
        if (type.kind != WinRtTypeKind.RuntimeClass || type.isStaticType || type.isAttributeType) {
            return false
        }
        val defaultInterfaceName = defaultInterfaceName ?: return false
        return hasInstanceProjectionSurface() ||
            instanceMemberBindings.isNotEmpty() ||
            implementedInterfaceBindings.any { it.qualifiedName == defaultInterfaceName }
    }

    private fun KotlinTypeProjectionPlan.hasInstanceProjectionSurface(): Boolean =
        type.methods.any { !it.isStatic } ||
            type.properties.any { !it.isStatic } ||
            type.events.any { !it.isStatic }

    private fun projectionFileRenderer(): KotlinProjectionFileRenderer =
        when (generationLayout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> KotlinProjectionFileRenderer { plan ->
                listOf(projectionRendererForLayout().render(plan))
            }
            KotlinProjectionGenerationLayout.ExpectActualJvm -> KotlinExpectActualProjectionRenderer(renderer)
        }

    private fun projectionRendererForLayout(): KotlinProjectionRenderer =
        if (emitSupportFiles) {
            KotlinProjectionRenderer(
                useInterfaceProjectionArtifacts = true,
                useProjectionIntrinsics = true,
            )
        } else {
            renderer
        }

    private fun supportFiles(
        model: WinRtMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ): List<KotlinProjectionFile> {
        val files = supportRenderer.render(
            model,
            plans,
            projectionContext,
            emitProjectionRegistrar = generationLayout == KotlinProjectionGenerationLayout.SingleSourceSet,
            excludedProjectionTypeNames = authoredProjectedTypeNames(model),
        )
        return when (generationLayout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> files
            KotlinProjectionGenerationLayout.ExpectActualJvm -> files.map { file ->
                KotlinProjectionFile(
                    relativePath = "commonMain/kotlin/${file.relativePath}",
                    packageName = file.packageName,
                    contents = file.contents,
                )
            }
        }
    }

    private fun deleteStaleGeneratedFiles(outputRoot: Path, expectedPaths: Set<String>): Int {
        if (!Files.isDirectory(outputRoot)) {
            return 0
        }
        var deleted = 0
        Files.walk(outputRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.isStaleGeneratedProjectionCandidate(outputRoot) }
                .filter { it.toAbsolutePath().normalize().toString() !in expectedPaths }
                .forEach { staleFile ->
                    Files.deleteIfExists(staleFile)
                    deleted += 1
                }
        }
        return deleted
    }

    private fun Path.isStaleGeneratedProjectionCandidate(outputRoot: Path): Boolean {
        if (extension == "kt") {
            return true
        }
        val relativePath = outputRoot.relativize(this).toString().replace('\\', '/')
        return relativePath.startsWith("kotlin-winrt-support/") ||
            relativePath.startsWith("commonMain/kotlin/kotlin-winrt-support/")
    }
}

internal fun interface KotlinProjectionFileRenderer {
    fun render(plan: KotlinTypeProjectionPlan): List<KotlinProjectionFile>
}
