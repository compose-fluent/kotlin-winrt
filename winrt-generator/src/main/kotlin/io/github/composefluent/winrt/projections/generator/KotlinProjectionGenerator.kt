package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRTAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRTCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventDefinition
import io.github.composefluent.winrt.metadata.WinRTEventHelperSubclassDescriptor
import io.github.composefluent.winrt.metadata.WinRTEventHandlerKind
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
import io.github.composefluent.winrt.metadata.WinRTMetadataSource
import io.github.composefluent.winrt.metadata.WinRTModuleActivationAndAuthoringDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodVtableDescriptor
import io.github.composefluent.winrt.metadata.WinRTMethodDefinition
import io.github.composefluent.winrt.metadata.WinRTNamespace
import io.github.composefluent.winrt.metadata.WinRTObjectReferenceSurfaceDescriptor
import io.github.composefluent.winrt.metadata.WinRTParameterDefinition
import io.github.composefluent.winrt.metadata.WinRTPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRTProjectedAttributeDescriptor
import io.github.composefluent.winrt.metadata.WinRTRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRTSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRTTypeDefinition
import io.github.composefluent.winrt.metadata.WinRTTypeRef
import io.github.composefluent.winrt.metadata.WinRTTypeRefKind
import io.github.composefluent.winrt.metadata.WinRTTypeKind
import io.github.composefluent.winrt.metadata.WinRTMetadataValidationOptions
import io.github.composefluent.winrt.metadata.filterProjectionSurface
import io.github.composefluent.winrt.metadata.projectionInventory
import io.github.composefluent.winrt.metadata.WinRTMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.isWinRTGuidTypeName
import io.github.composefluent.winrt.metadata.isWinRTObjectTypeName
import io.github.composefluent.winrt.metadata.isWinRTVoidTypeName
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
import io.github.composefluent.winrt.metadata.winRTFundamentalTypeForName
import io.github.composefluent.winrt.metadata.winRTEventHandlerKindForTypeName
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

class KotlinProjectionGenerator(
    private val planner: KotlinProjectionPlanner = KotlinProjectionPlanner(),
    private val renderer: KotlinProjectionRenderer = KotlinProjectionRenderer(),
    private val supportRenderer: KotlinProjectionSupportRenderer = KotlinProjectionSupportRenderer(),
    private val emitSupportFiles: Boolean = false,
    private val projectionContext: WinRTMetadataProjectionContext = WinRTMetadataProjectionContext(sources = emptyList()),
    private val suppressedProjectionTypeNames: Set<String> = emptySet(),
    private val authoredRuntimeClassNames: Set<String> = emptySet(),
    private val generationLayout: KotlinProjectionGenerationLayout = KotlinProjectionGenerationLayout.SingleSourceSet,
    private val groupProjectionFilesByPackageOnWrite: Boolean = false,
    private val supportOwnerIdentity: String? = null,
    private val emitJvmAuthoringHostExports: Boolean = true,
) {
    private val genericTypeInstantiationsClassName = winRTGenericTypeInstantiationsClassName(supportOwnerIdentity)
    private val authoringHostExportsClassName = winRTAuthoringHostExportsClassName(supportOwnerIdentity)
    private val authoringServerActivationFactoriesClassName = winRTAuthoringServerActivationFactoriesClassName(supportOwnerIdentity)
    private val authoringModuleActivationFactoryPlanClassName = winRTAuthoringModuleActivationFactoryPlanClassName(supportOwnerIdentity)
    private val modulePlatformAbiCallClassName = winRTModulePlatformAbiCallClassName(supportOwnerIdentity)
    private val genericAbiSupportFileName = winRTGenericAbiSupportFileName(supportOwnerIdentity)
    private val eventProjectionHelperFilePrefix = winRTEventProjectionHelperFilePrefix(supportOwnerIdentity)
    private val namespaceAdditionsClassName = winRTNamespaceAdditionsClassName(supportOwnerIdentity)

    init {
        require(emitSupportFiles || projectionContext.sources.isEmpty()) {
            "KotlinProjectionGenerator requires emitSupportFiles=true when a projection context is supplied."
        }
    }

    fun generate(model: WinRTMetadataModel): List<KotlinProjectionFile> {
        val normalizedModel = completeProjectionModel(model).withoutExcludedProjectionSurfaceReferences()
        val plans = planner.plan(normalizedModel, projectionContext)
        validateGeneratorContracts(normalizedModel, plans)
        val renderedPlans = plans.filterNot { plan ->
            plan.type.qualifiedName in authoredProjectedTypeNames(normalizedModel) ||
                plan.shouldSkipRuntimeOwnedMappedProjectionOutput()
        }
        val modulePlatformAbiCalls = modulePlatformAbiCallSupport(renderedPlans)
        val projectionRenderer = projectionFileRenderer(modulePlatformAbiCalls = modulePlatformAbiCalls)
        val projectionFiles = renderedPlans.flatMap(projectionRenderer::render)
        if (!emitSupportFiles) {
            return projectionFiles
        }
        return projectionFiles + supportFiles(normalizedModel, plans, modulePlatformAbiCalls)
    }

    fun generateTo(model: WinRTMetadataModel, outputRoot: Path): KotlinProjectionWriteSummary {
        val normalizedModel = completeProjectionModel(model).withoutExcludedProjectionSurfaceReferences()
        val plans = planner.plan(normalizedModel, projectionContext)
        validateGeneratorContracts(normalizedModel, plans)
        val authoredTypeNames = authoredProjectedTypeNames(normalizedModel)
        val renderedPlans = if (groupProjectionFilesByPackageOnWrite) {
            plans.map(KotlinTypeProjectionPlan::withoutRenderedProjectedAttributes)
        } else {
            plans
        }
        val projectionPlans = renderedPlans.filterNot { plan ->
            plan.type.qualifiedName in authoredTypeNames ||
                plan.shouldSkipRuntimeOwnedMappedProjectionOutput()
        }
        val modulePlatformAbiCalls = modulePlatformAbiCallSupport(projectionPlans, renderedPlans)
        val projectionRenderer = projectionFileRenderer(renderedPlans, modulePlatformAbiCalls)
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
        val projectionFiles = projectionPlans
            .flatMap(projectionRenderer::render)
            .let { files ->
                if (groupProjectionFilesByPackageOnWrite && generationLayout == KotlinProjectionGenerationLayout.SingleSourceSet) {
                    files.groupByPackage()
                } else {
                    files
                }
            }
        projectionFiles.forEach(::write)
        if (emitSupportFiles) {
            supportFiles(normalizedModel, plans, modulePlatformAbiCalls).forEach(::write)
        }
        val deleted = deleteStaleGeneratedFiles(outputRoot, expectedPaths)
        return KotlinProjectionWriteSummary(
            renderedFiles = rendered,
            writtenFiles = written,
            unchangedFiles = rendered - written,
            deletedStaleFiles = deleted,
        )
    }

    private fun completeProjectionModel(model: WinRTMetadataModel): WinRTMetadataModel {
        val normalizedModel = model.normalized()
        if (projectionContext.sources.isEmpty() || projectionContext.include.isEmpty()) {
            return normalizedModel
        }
        val excludedNamespaces = projectionContext.exclude - projectionContext.excludedTypes
        val supplementalContext = projectionContext.withWindowsSdkSourceForWindowsProjectionRoots()
        val supplementalModel = supplementalContext.load().filterProjectionSurface(
            types = projectionContext.include,
            excludedNamespaces = excludedNamespaces,
            excludedTypes = projectionContext.excludedTypes,
            additionalTypeReferences = ::redirectedWinAppSdkProjectionSurfaceTypeReferences,
        )
        return WinRTMetadataModel(supplementalModel.namespaces + normalizedModel.namespaces).normalized()
    }

    private fun WinRTMetadataProjectionContext.withWindowsSdkSourceForWindowsProjectionRoots(): WinRTMetadataProjectionContext {
        if (sources.any { source -> source is WinRTMetadataSource.WindowsSdk }) {
            return this
        }
        if (include.none { name -> name == "Windows" || name.startsWith("Windows.") }) {
            return this
        }
        return copy(sources = listOf(WinRTMetadataSource.windowsSdk()) + sources)
    }

    private fun authoredProjectedTypeNames(model: WinRTMetadataModel): Set<String> =
        suppressedProjectionTypeNames

    private fun WinRTMetadataModel.withoutExcludedProjectionSurfaceReferences(): WinRTMetadataModel {
        val excludedProjectionSurfaceNames = projectionContext.excludedTypes
        if (excludedProjectionSurfaceNames.isEmpty()) {
            return this
        }
        return copy(
            namespaces = namespaces.map { namespace ->
                namespace.copy(
                    types = namespace.types.filterNot { type ->
                        type.qualifiedName.isExcludedProjectionSurface(excludedProjectionSurfaceNames)
                    }.map { type ->
                        val implementedInterfaces = type.implementedInterfaces.filterNot { implemented ->
                            !implemented.isDefault && implemented.interfaceName.isExcludedProjectionSurface(excludedProjectionSurfaceNames)
                        }
                        val methods = type.methods.filterNot { method ->
                            method.returnType.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames) ||
                                method.parameters.any { parameter ->
                                    parameter.type.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames)
                                }
                        }
                        val properties = type.properties.filterNot { property ->
                            property.type.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames)
                        }
                        val events = type.events.filterNot { event ->
                            event.delegateType.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames)
                        }
                        if (implementedInterfaces.size == type.implementedInterfaces.size &&
                            methods.size == type.methods.size &&
                            properties.size == type.properties.size &&
                            events.size == type.events.size
                        ) {
                            type
                        } else {
                            type.copy(
                                implementedInterfaces = implementedInterfaces,
                                methods = methods,
                                properties = properties,
                                events = events,
                            )
                        }
                    },
                )
            },
        )
    }

    private fun WinRTTypeRef.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames: Set<String>): Boolean =
        when (kind) {
            WinRTTypeRefKind.Named ->
                qualifiedName.orEmpty().isExcludedProjectionSurface(excludedProjectionSurfaceNames) ||
                    typeArguments.any { argument -> argument.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames) }

            WinRTTypeRefKind.Array ->
                elementType?.referencesExcludedProjectionSurface(excludedProjectionSurfaceNames) == true

            WinRTTypeRefKind.GenericTypeParameter,
            WinRTTypeRefKind.MethodTypeParameter,
            WinRTTypeRefKind.Unknown,
            -> false
        }

    private fun String.isExcludedProjectionSurface(excludedProjectionSurfaceNames: Set<String>): Boolean =
        excludedProjectionSurfaceNames.any { excluded -> this == excluded || startsWith("$excluded.") }

    private fun validateGeneratorContracts(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        if (generationLayout == KotlinProjectionGenerationLayout.ExpectActualJvm) {
            return
        }
        validateEventAccessorPairContracts(plans)
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
            .filter { plan -> plan.type.kind == WinRTTypeKind.RuntimeClass }
            .flatMap { plan -> plan.staticInterfaceNames }
            .toSet()
        plans.forEach { plan ->
            if (KotlinProjectionCompanionKind.ActivationFactory in plan.companionKinds) {
                plan.activatableFactoryInterfaceName?.let { factoryName ->
                    val factoryType = plan.typesByQualifiedName[factoryName]
                    require(factoryType?.kind == WinRTTypeKind.Interface) {
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
                    require(staticType?.kind == WinRTTypeKind.Interface) {
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
                require(defaultInterfaceType?.kind == WinRTTypeKind.Interface) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to be present in the metadata model."
                }
                require(plan.defaultInterfaceIid != null) {
                    "Generator requires runtime class ${plan.type.qualifiedName} default interface $defaultInterfaceName to carry metadata IID before projection rendering."
                }
            }
            if (plan.type.kind == WinRTTypeKind.RuntimeClass) {
                plan.implementedInterfaceBindings
                    .filterNot { isMappedCollectionInterfaceName(it.qualifiedName) }
                    .filterNot { isRuntimeOwnedMappedTypeName(it.qualifiedName) }
                    .forEach { binding ->
                        val interfaceType = projectionType(binding.qualifiedName, plan)
                        if (interfaceType == null && binding.qualifiedName.substringBeforeLast('.', "") != plan.type.namespace) {
                            return@forEach
                        }
                        require(interfaceType?.kind == WinRTTypeKind.Interface) {
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
                    if (interfaceType == null && requiredInterfaceName.substringBeforeLast('.', "") != plan.type.namespace) {
                        return@forEach
                    }
                    require(interfaceType?.kind == WinRTTypeKind.Interface) {
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
                require(factoryType?.kind == WinRTTypeKind.Interface) {
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
                require(defaultInterfaceType?.kind == WinRTTypeKind.Interface) {
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
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ) {
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)
        val plansByType = plans.associateBy { plan -> plan.type.qualifiedName }
        val descriptorsByOwnerAndEvent = planner.eventSourceDescriptors(model, plans)
            .associateBy { descriptor -> descriptor.ownerTypeName to descriptor.eventTypeName }
        plans.forEach { plan ->
            validateRuntimeClassEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
            validateStaticEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
            validateInterfaceNativeProjectionEventSourceHelperContracts(plan, descriptorsByOwnerAndEvent, typesByQualifiedName, plansByType)
        }
    }

    private fun validateRuntimeClassEventSourceHelperContracts(
        plan: KotlinTypeProjectionPlan,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRTEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return
        }
        plan.type.events
            .filterNot(WinRTEventDefinition::isStatic)
            .forEach { event ->
                val binding = plan.instanceMemberBindings.firstOrNull { it.bindingName == "${event.name.uppercase()}_ADD_SLOT" }
                    ?: return@forEach
                val eventTypeName = typesByQualifiedName[binding.slotInterfaceQualifiedName]
                    ?.events
                    ?.firstOrNull { rawEvent -> rawEvent.name == event.name }
                    ?.delegateTypeName
                    ?: plan.eventInvokeDescriptors
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
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRTEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
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

    private fun eventSourceStaticEvents(plan: KotlinTypeProjectionPlan): List<WinRTEventDefinition> {
        val merged = linkedMapOf<String, WinRTEventDefinition>()
        plan.type.events
            .filter(WinRTEventDefinition::isStatic)
            .forEach { event ->
                merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
            }
        plan.staticInterfaceNames
            .mapNotNull(plan.typesByQualifiedName::get)
            .flatMap(WinRTTypeDefinition::events)
            .forEach { event ->
                merged.putIfAbsent("${event.name}|${event.delegateTypeName}", event.copy(isStatic = true))
            }
        return merged.values.toList()
    }

    private fun validateInterfaceNativeProjectionEventSourceHelperContracts(
        plan: KotlinTypeProjectionPlan,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRTEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
        plansByType: Map<String, KotlinTypeProjectionPlan>,
    ) {
        if (plan.type.kind != WinRTTypeKind.Interface || !canRenderInterfaceWrapperSafely(plan)) {
            return
        }
        renderer.collectInterfaceProxyTypes(plan).forEach { interfaceType ->
            interfaceType.events
                .filterNot(WinRTEventDefinition::isStatic)
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
        event: WinRTEventDefinition,
        ownerTypeName: String,
        eventTypeName: String,
        descriptorsByOwnerAndEvent: Map<Pair<String, String>, WinRTEventHelperSubclassDescriptor>,
        typesByQualifiedName: Map<String, WinRTTypeDefinition>,
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
        attribute: WinRTProjectedAttributeDescriptor,
    ) {
        require(renderProjectedAttributeAnnotation(attribute) != null) {
            "Generator requires ${plan.projectionContractSubject()} $ownerLabel projected attribute ${attribute.metadataTypeName} to use renderable attribute metadata before projection rendering."
        }
    }

    private fun validateAuthoredCcwBindingContracts(
        model: WinRTMetadataModel,
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
        val supportPlansByQualifiedName = supportPlansByQualifiedName(model, plans)
        plans
            .filter { plan -> plan.type.kind == WinRTTypeKind.RuntimeClass && plan.type.qualifiedName in authoredTypeNames }
            .forEach { authoredPlan ->
                authoredPlan.type.implementedInterfaces
                    .map { implementation -> implementation.interfaceName.substringBefore('<') }
                    .distinct()
                    .forEach { interfaceName ->
                        val interfacePlan = plansByQualifiedName[interfaceName] ?: supportPlansByQualifiedName[interfaceName]
                        require(interfacePlan != null) {
                            "Generator requires authored runtime class ${authoredPlan.type.qualifiedName} CCW interface $interfaceName to have a projection plan before support rendering."
                        }
                        validateAuthoredCcwInterfaceBindingContracts(authoredPlan, interfacePlan)
                    }
        }
    }

    private fun validateAuthoringActivationFactorySupportContracts(
        model: WinRTMetadataModel,
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
            .filter { plan -> plan.type.kind == WinRTTypeKind.RuntimeClass && plan.type.qualifiedName in authoredTypeNames }
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
                    require(interfaceType?.kind == WinRTTypeKind.Interface) {
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
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .forEach { method ->
                val bindingName = method.abiSlotConstantName(interfaceType.methods)
                validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, bindingName)
            }
        interfaceType.properties
            .filterNot(WinRTPropertyDefinition::isStatic)
            .forEach { property ->
                if (property.hasNativeProjectionGetterAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${property.name.uppercase()}_GETTER_SLOT")
                }
                if (property.hasNativeProjectionSetterAccessor()) {
                    validateAuthoredCcwSlotMetadataContract(authoredPlan, interfacePlan, "${property.name.uppercase()}_SETTER_SLOT")
                }
            }
        interfaceType.events
            .filterNot(WinRTEventDefinition::isStatic)
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
        factoryType: WinRTTypeDefinition,
    ) {
        factoryType.methods
            .filter(WinRTMethodDefinition::isProjectedCallableMethod)
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
        factoryType: WinRTTypeDefinition,
    ) {
        factoryType.methods
            .filter(WinRTMethodDefinition::isProjectedCallableMethod)
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
        factoryType: WinRTTypeDefinition,
        method: WinRTMethodDefinition,
    ): List<WinRTParameterDefinition> {
        require(method.parameters.size >= 2) {
            "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface ${factoryType.qualifiedName} method ${method.name} to end with baseInterface and innerInterface object parameters before projection rendering."
        }
        val trailing = method.parameters.takeLast(2)
        require(trailing.all { parameter -> isWinRTObjectTypeName(parameter.type.typeName) }) {
            "Generator requires runtime class ${plan.type.qualifiedName} composable factory interface ${factoryType.qualifiedName} method ${method.name} to end with baseInterface and innerInterface object parameters before projection rendering."
        }
        return method.parameters.dropLast(2)
    }

    private fun validateFactoryCreateAbiBindingContract(
        plan: KotlinTypeProjectionPlan,
        factoryType: WinRTTypeDefinition,
        method: WinRTMethodDefinition,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameters: List<WinRTParameterDefinition>,
    ) {
        validateProjectedAbiBindingContract(
            plan = plan,
            bindingName = "${factoryType.qualifiedName}.${method.name}",
            returnBinding = returnBinding,
            parameterBindings = parameters.map { parameter ->
                KotlinProjectionAbiParameterBinding(
                    name = parameter.name,
                    typeBinding = KotlinProjectionPlanner(useWinAppSdkTypeRedirects = plan.requiresWinAppSdkTypeRedirects()).classifyAbiTypeBinding(
                        typeName = parameter.typeName,
                        currentNamespace = factoryType.namespace,
                        typesByQualifiedName = plan.typesByQualifiedName,
                    ),
                )
            },
        )
    }

    private fun composableFactoryUserParameters(
        method: WinRTMethodDefinition,
    ): Pair<WinRTMethodDefinition, List<WinRTParameterDefinition>>? {
        if (isWinRTVoidTypeName(method.returnType.typeName) || method.parameters.size < 2) {
            return null
        }
        val trailing = method.parameters.takeLast(2)
        if (trailing.any { parameter -> !isWinRTObjectTypeName(parameter.type.typeName) }) {
            return null
        }
        return method to method.parameters.dropLast(2)
    }

    private fun validateInstanceMethodBindingContracts(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return
        }
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.methods
            .filter(WinRTMethodDefinition::isOrdinaryProjectedMethod)
            .filter { method -> method.methodRowId != null }
            .filterNot { method -> method.hasKotlinOwnedRuntimeMethodProjection(plan, mappedCollectionMemberNames) }
            .forEach { method ->
                val bindingName = method.abiSlotConstantName(plan.type.methods)
                require(plan.instanceMemberBindings.any { it.bindingName == bindingName }) {
                    "Generator requires ${plan.projectionContractSubject()} method ${method.name} binding $bindingName to be present before projection rendering."
                }
            }
    }

    private fun WinRTMethodDefinition.hasKotlinOwnedRuntimeMethodProjection(
        plan: KotlinTypeProjectionPlan,
        mappedCollectionMemberNames: Set<String>,
    ): Boolean =
        runtimeObjectMethodShape(this) != null ||
            isMappedCollectionRuntimeMethod(plan, mappedCollectionMemberNames) ||
            plan.requiredInterfaceAugmentationDescriptor?.mappedAugmentationMembers.orEmpty().let { augmentations ->
                augmentations.contains("IDisposable") && name == "Close" ||
                    augmentations.contains("INotifyDataErrorInfo") && name == "GetErrors"
            }

    private fun validateStaticMethodBindingContracts(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return
        }
        val staticMethods = plan.type.methods.filter(WinRTMethodDefinition::isOrdinaryProjectedStaticMethod)
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
        if (plan.type.kind != WinRTTypeKind.Delegate) {
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
        if (plan.type.isAttributeType) {
            return
        }
        val mappedCollectionMemberNames = mappedCollectionMemberNames(plan)
        plan.type.properties
            .filterNot(WinRTPropertyDefinition::isStatic)
            .filterNot { property -> property.isMappedCollectionRuntimeProperty(plan, mappedCollectionMemberNames) }
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
        event: WinRTEventDefinition,
    ) {
        val delegateTypeName = event.delegateTypeName
        val rawDelegateTypeName = delegateTypeName.substringBefore('<').removeSuffix("?")
        val eventHandlerKind = winRTEventHandlerKindForTypeName(rawDelegateTypeName)
        if (eventHandlerKind != null) {
            validateMappedEventDelegateContract(plan, event, eventHandlerKind)
            return
        }
        val delegateType = eventDelegateType(delegateTypeName, plan)
        require(delegateType?.kind == WinRTTypeKind.Delegate) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} delegate $delegateTypeName to be present in the metadata model."
        }
        require(delegateType.iid != null) {
            "Generator requires ${plan.projectionContractSubject()} event ${event.name} delegate $delegateTypeName to carry metadata IID before projection rendering."
        }
        requireDelegateInvokeMethod(delegateType)
    }

    private fun validateMappedEventDelegateContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRTEventDefinition,
        eventHandlerKind: WinRTEventHandlerKind,
    ) {
        val expectedArgumentCount = when (eventHandlerKind) {
            WinRTEventHandlerKind.EventHandler,
            WinRTEventHandlerKind.VectorChangedEventHandler,
            WinRTEventHandlerKind.AsyncActionProgressHandler -> 1
            WinRTEventHandlerKind.TypedEventHandler,
            WinRTEventHandlerKind.MapChangedEventHandler,
            WinRTEventHandlerKind.AsyncOperationProgressHandler -> 2
            WinRTEventHandlerKind.PropertyChangedEventHandler,
            WinRTEventHandlerKind.BindableVectorChangedEventHandler,
            WinRTEventHandlerKind.NotifyCollectionChangedEventHandler -> 0
        }
        val argumentCount = WinRTTypeRef.fromDisplayName(event.delegateTypeName)
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

    private fun validateEventAccessorPairContracts(plans: List<KotlinTypeProjectionPlan>) {
        plans.forEach { plan ->
            plan.type.events.forEach { event ->
                validateEventAccessorPairContract(plan, event, staticEvent = event.isStatic)
            }
        }
    }

    private fun validateEventAccessorBindingContracts(plans: List<KotlinTypeProjectionPlan>) {
        plans.forEach(::validateEventAccessorBindingContracts)
    }

    private fun validateStaticEventAccessorBindingContract(plan: KotlinTypeProjectionPlan) {
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return
        }
        buildList {
            addAll(plan.type.events.filter(WinRTEventDefinition::isStatic))
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
        event: WinRTEventDefinition,
    ): Boolean =
        generationLayout != KotlinProjectionGenerationLayout.ExpectActualJvm ||
            plan.type.kind != WinRTTypeKind.RuntimeClass ||
            !event.isStatic

    private fun validateInstanceEventAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
        staticEvent: Boolean = false,
    ) {
        require(event.hasNativeProjectionAddAccessor() == event.hasNativeProjectionRemoveAccessor()) {
            val eventLabel = if (staticEvent) "static event" else "event"
            "Generator requires ${plan.projectionContractSubject()} $eventLabel ${event.name} to carry both add and remove accessor metadata before projection rendering."
        }
    }

    private fun validateEventAddAccessorBindingContract(
        plan: KotlinTypeProjectionPlan,
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor?,
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
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
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
        event: WinRTEventDefinition,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
        marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor?,
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
        if (plan.type.kind != WinRTTypeKind.RuntimeClass) {
            return false
        }
        return buildList {
            addAll(plan.type.events.filter(WinRTEventDefinition::isStatic))
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
        marshalerPlanDescriptor: WinRTAbiMarshalerPlanDescriptor? = null,
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
        if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedInterface && customObjectAbi(typeBinding) == null) {
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
            require(structType?.kind == WinRTTypeKind.Struct) {
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
        type: WinRTTypeDefinition,
        plan: KotlinTypeProjectionPlan,
    ): Boolean =
        type.fields
            .filterNot { it.isStatic || it.isLiteral }
            .all { field -> canRenderNativeStructField(field, type.namespace, plan) }

    private fun canRenderNativeStructField(
        field: WinRTFieldDefinition,
        currentNamespace: String,
        plan: KotlinTypeProjectionPlan,
    ): Boolean {
        if (isWinRTGuidTypeName(field.typeName) || winRTFundamentalTypeForName(field.typeName) != null) {
            return true
        }
        val rawTypeName = field.typeName.substringBefore('<').removeSuffix("?")
        if (isWinRTObjectTypeName(rawTypeName)) {
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
        if (resolvedType?.kind == WinRTTypeKind.Interface || resolvedType?.kind == WinRTTypeKind.RuntimeClass) {
            return true
        }
        if (resolvedType?.kind == WinRTTypeKind.Enum) {
            return true
        }
        if (resolvedType?.kind == WinRTTypeKind.Struct) {
            return canRenderNativeStructMetadata(resolvedType, plan)
        }
        val mappedType = mappedTypeByAbiName(rawTypeName)
        return mappedType?.abiValueKind == KotlinProjectionAbiValueKind.Struct
    }

    private fun eventDelegateType(
        delegateTypeName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRTTypeDefinition? =
        projectionType(delegateTypeName, plan)

    private fun projectionType(
        typeName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRTTypeDefinition? {
        val rawName = typeName.substringBefore('<').removeSuffix("?")
        return plan.typesByQualifiedName[typeName]
            ?: plan.typesByQualifiedName[rawName]
            ?: plan.typesByQualifiedName["${plan.type.namespace}.$rawName"]
    }

    private fun requiredInterfaceType(
        requiredInterfaceName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRTTypeDefinition? {
        val rawName = requiredInterfaceName.substringBefore('<').removeSuffix("?")
        return plan.typesByQualifiedName[requiredInterfaceName]
            ?: plan.typesByQualifiedName[rawName]
            ?: plan.typesByQualifiedName["${plan.type.namespace}.$rawName"]
    }

    private fun KotlinTypeProjectionPlan.projectionContractSubject(): String {
        val kind = when (type.kind) {
            WinRTTypeKind.RuntimeClass -> "runtime class"
            WinRTTypeKind.Interface -> "interface"
            WinRTTypeKind.Delegate -> "delegate"
            WinRTTypeKind.Struct -> "struct"
            WinRTTypeKind.Enum -> "enum"
            WinRTTypeKind.Unknown -> "type"
        }
        return "$kind ${type.qualifiedName}"
    }

    private fun KotlinTypeProjectionPlan.requiresDefaultInterfaceContract(): Boolean {
        if (type.kind != WinRTTypeKind.RuntimeClass || type.isStaticType || type.isAttributeType) {
            return false
        }
        val defaultInterfaceName = defaultInterfaceName ?: return false
        return hasInstanceProjectionSurface() ||
            instanceMemberBindings.isNotEmpty() ||
            implementedInterfaceBindings.any { it.qualifiedName == defaultInterfaceName }
    }

    private fun KotlinTypeProjectionPlan.shouldSkipRuntimeOwnedMappedProjectionOutput(): Boolean =
        type.kind == WinRTTypeKind.Delegate &&
            mappedTypeByAbiName(type.qualifiedName)?.runtimeOwnedPublicDeclaration == true

    private fun KotlinTypeProjectionPlan.hasInstanceProjectionSurface(): Boolean =
        type.methods.any { !it.isStatic } ||
            type.properties.any { !it.isStatic } ||
            type.events.any { !it.isStatic }

    private fun projectionFileRenderer(
        plans: List<KotlinTypeProjectionPlan>? = null,
        modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport? = null,
    ): KotlinProjectionFileRenderer =
        when (generationLayout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> KotlinProjectionFileRenderer { plan ->
                listOf(projectionRendererForLayout(plans, plan, modulePlatformAbiCalls).render(plan))
            }
            KotlinProjectionGenerationLayout.ExpectActualJvm -> KotlinProjectionFileRenderer { plan ->
                KotlinExpectActualProjectionRenderer(
                    projectionRendererForLayout(plans, plan, modulePlatformAbiCalls),
                ).render(plan)
            }
        }

    private fun projectionRendererForLayout(
        plans: List<KotlinTypeProjectionPlan>? = null,
        currentPlan: KotlinTypeProjectionPlan? = null,
        modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport? = null,
    ): KotlinProjectionRenderer =
        if (emitSupportFiles) {
            KotlinProjectionRenderer(
                useInterfaceProjectionArtifacts = true,
                useProjectionIntrinsics = true,
                suppressProjectedMemberSlotConstants = groupProjectionFilesByPackageOnWrite,
                projectedSlotLiterals = if (groupProjectionFilesByPackageOnWrite && plans != null) {
                    projectedSlotLiteralMap(plans)
                } else {
                    emptyMap()
                },
                useWinAppSdkTypeRedirects = currentPlan?.requiresWinAppSdkTypeRedirects() == true,
                useKotlinDurationAlias = plans?.requiresKotlinDurationAlias(currentPlan) == true,
                genericTypeInstantiationsClassName = genericTypeInstantiationsClassName,
                modulePlatformAbiCalls = modulePlatformAbiCalls,
            )
        } else {
            renderer
        }

    private fun KotlinTypeProjectionPlan.requiresWinAppSdkTypeRedirects(): Boolean =
        type.qualifiedName.startsWith("Microsoft.UI.")

    private fun List<KotlinTypeProjectionPlan>.requiresKotlinDurationAlias(currentPlan: KotlinTypeProjectionPlan?): Boolean =
        currentPlan != null &&
            any { plan ->
                plan.packageName == currentPlan.packageName &&
                    plan.type.kind == WinRTTypeKind.Struct &&
                    plan.type.name == "Duration"
            }

    private fun projectedSlotLiteralMap(plans: List<KotlinTypeProjectionPlan>): Map<KotlinProjectionSlotLiteralKey, Int> =
        plans
            .asSequence()
            .filter { plan -> plan.type.kind == WinRTTypeKind.Interface }
            .flatMap { plan ->
                plan.abiSlotBindings.asSequence().map { binding ->
                    KotlinProjectionSlotLiteralKey(plan.type.qualifiedName, binding.constantName) to binding.slot
                }
            }
            .toMap()

    private fun supportFiles(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
        modulePlatformAbiCalls: KotlinModulePlatformAbiCallSupport?,
    ): List<KotlinProjectionFile> {
        val supportPlans = plans + supportPlansByQualifiedName(model, plans).values
        val supportRendererFiles = supportRenderer.render(
            model,
            supportPlans.distinctBy { it.type.qualifiedName },
            projectionContext,
            emitProjectionRegistrar = generationLayout == KotlinProjectionGenerationLayout.SingleSourceSet,
            excludedProjectionTypeNames = authoredProjectedTypeNames(model),
            authoredRuntimeClassNames = authoredRuntimeClassNames,
            genericTypeInstantiationsClassName = genericTypeInstantiationsClassName,
            authoringHostExportsClassName = authoringHostExportsClassName,
            authoringServerActivationFactoriesClassName = authoringServerActivationFactoriesClassName,
            authoringModuleActivationFactoryPlanClassName = authoringModuleActivationFactoryPlanClassName,
            emitJvmAuthoringHostExports = emitJvmAuthoringHostExports,
            genericAbiSupportFileName = genericAbiSupportFileName,
            eventProjectionHelperFilePrefix = eventProjectionHelperFilePrefix,
            namespaceAdditionsClassName = namespaceAdditionsClassName,
            supportOwnerIdentity = supportOwnerIdentity,
        )
        val files = when (generationLayout) {
            KotlinProjectionGenerationLayout.SingleSourceSet -> supportRendererFiles
            KotlinProjectionGenerationLayout.ExpectActualJvm -> supportRendererFiles.map { file ->
                KotlinProjectionFile(
                    relativePath = "commonMain/kotlin/${file.relativePath}",
                    packageName = file.packageName,
                    contents = file.contents,
                )
            }
        }
        return files + modulePlatformAbiCalls.orEmptyFiles()
    }

    private fun supportPlansByQualifiedName(
        model: WinRTMetadataModel,
        plans: List<KotlinTypeProjectionPlan>,
    ): Map<String, KotlinTypeProjectionPlan> {
        val plannedTypeNames = plans.mapTo(mutableSetOf()) { it.type.qualifiedName }
        val semanticHelpers = model.semanticHelpers()
        val typesByQualifiedName = model.namespaces
            .flatMap(WinRTNamespace::types)
            .associateBy(WinRTTypeDefinition::qualifiedName)
        return model.namespaces
            .flatMap(WinRTNamespace::types)
            .asSequence()
            .filter { type -> type.qualifiedName !in plannedTypeNames }
            .filter { type ->
                type.kind in setOf(WinRTTypeKind.Interface, WinRTTypeKind.Delegate) &&
                    isRuntimeOwnedPublicDeclarationTypeName(type.qualifiedName)
            }
            .mapNotNull { type -> planner.planSupportType(type, typesByQualifiedName, semanticHelpers) }
            .associateBy { it.type.qualifiedName }
    }

    private fun modulePlatformAbiCallSupport(
        plans: List<KotlinTypeProjectionPlan>,
        renderedPlans: List<KotlinTypeProjectionPlan> = plans,
    ): KotlinModulePlatformAbiCallSupport? {
        if (!emitSupportFiles) {
            return null
        }
        val collector = KotlinModulePlatformAbiCallSupport(
            className = modulePlatformAbiCallClassName,
            enabledCalls = emptySet(),
        )
        val collectorRenderer = projectionFileRenderer(renderedPlans, collector)
        plans.forEach { plan -> collectorRenderer.render(plan) }
        return KotlinModulePlatformAbiCallSupport(
            className = modulePlatformAbiCallClassName,
            enabledCalls = collector.plannedCalls(),
        )
    }

    private fun KotlinModulePlatformAbiCallSupport?.orEmptyFiles(): List<KotlinProjectionFile> =
        this?.renderFiles(generationLayout).orEmpty()

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

private fun List<KotlinProjectionFile>.groupByPackage(): List<KotlinProjectionFile> =
    groupBy(KotlinProjectionFile::packageName)
        .toSortedMap()
        .flatMap { (packageName, files) ->
            files
                .sortedBy(KotlinProjectionFile::relativePath)
                .map(::parseGeneratedKotlinFile)
                .let { parsedFiles ->
                    parsedFiles.chunkByGeneratedBodySizeAndImports(
                        packageDeclaredNames = parsedFiles.flatMap { it.declaredTopLevelNames() }.toSet(),
                    )
                }
                .mapIndexed { index, parsedFiles ->
            val imports = parsedFiles
                .flatMap(ParsedGeneratedKotlinFile::imports)
                .toSortedSet()
            val body = parsedFiles
                .joinToString("\n") { it.body.trim() }
                .trim()
            KotlinProjectionFile(
                relativePath = packageName.replace('.', '/') + "/${packageName.split('.').joinToString("_")}${if (index == 0) "" else "_$index"}.kt",
                packageName = packageName,
                contents = buildString {
                    append(parsedFiles.first().fileAnnotations.trimEnd())
                    append("\n\n")
                    append(parsedFiles.first().packageDeclaration)
                    append("\n\n")
                    if (imports.isNotEmpty()) {
                        imports.forEach { importLine ->
                            append(importLine)
                            append('\n')
                        }
                        append('\n')
                    }
                    append(body)
                    append('\n')
                },
            )
                }
        }

private fun KotlinTypeProjectionPlan.withoutRenderedProjectedAttributes(): KotlinTypeProjectionPlan =
    copy(
        projectedAttributes = emptyList(),
        instanceMemberBindings = instanceMemberBindings.map { binding ->
            binding.copy(projectedAttributes = emptyList())
        },
        staticMemberBindings = staticMemberBindings.map { binding ->
            binding.copy(projectedAttributes = emptyList())
        },
    )

private const val MAX_GROUPED_PROJECTION_BODY_CHARS = 220_000

private fun List<ParsedGeneratedKotlinFile>.chunkByGeneratedBodySizeAndImports(
    packageDeclaredNames: Set<String>,
): List<List<ParsedGeneratedKotlinFile>> =
    buildList {
        var current = mutableListOf<ParsedGeneratedKotlinFile>()
        var currentSize = 0
        var currentImportedNames = emptySet<String>()
        var currentImportedTargetsByName = emptyMap<String, Set<String>>()
        var currentDeclaredNames = emptySet<String>()
        var currentHasPackageShadowingImport = false
        for (file in this@chunkByGeneratedBodySizeAndImports) {
            val fileSize = file.body.length
            val fileImportedNames = file.importedSimpleNames()
            val fileImportedTargetsByName = file.importedTargetsBySimpleName()
            val fileDeclaredNames = file.declaredTopLevelNames()
            val fileHasPackageShadowingImport = fileImportedNames.any(packageDeclaredNames::contains)
            val hasImportDeclarationCollision =
                currentImportedNames.any(fileDeclaredNames::contains) ||
                    fileImportedNames.any(currentDeclaredNames::contains)
            val hasImportTargetCollision =
                fileImportedTargetsByName.any { (name, targets) ->
                    val currentTargets = currentImportedTargetsByName[name].orEmpty()
                    currentTargets.isNotEmpty() && (currentTargets + targets).size > 1
                }
            if (
                current.isNotEmpty() &&
                (
                    currentSize + fileSize > MAX_GROUPED_PROJECTION_BODY_CHARS ||
                        hasImportDeclarationCollision ||
                        hasImportTargetCollision ||
                        currentHasPackageShadowingImport ||
                        fileHasPackageShadowingImport
                )
            ) {
                add(current)
                current = mutableListOf()
                currentSize = 0
                currentImportedNames = emptySet()
                currentImportedTargetsByName = emptyMap()
                currentDeclaredNames = emptySet()
                currentHasPackageShadowingImport = false
            }
            current += file
            currentSize += fileSize
            currentImportedNames = currentImportedNames + fileImportedNames
            currentImportedTargetsByName = currentImportedTargetsByName.mergeImportTargets(fileImportedTargetsByName)
            currentDeclaredNames = currentDeclaredNames + fileDeclaredNames
            currentHasPackageShadowingImport = currentHasPackageShadowingImport || fileHasPackageShadowingImport
        }
        if (current.isNotEmpty()) {
            add(current)
        }
    }

private data class ParsedGeneratedKotlinFile(
    val fileAnnotations: String,
    val packageDeclaration: String,
    val imports: List<String>,
    val body: String,
)

private fun ParsedGeneratedKotlinFile.importedSimpleNames(): Set<String> =
    imports.mapNotNullTo(mutableSetOf()) { importLine ->
        val imported = importLine.removePrefix("import ").trim()
        imported.substringAfter(" as ", missingDelimiterValue = "")
            .ifBlank { imported.substringAfterLast('.') }
            .takeIf(String::isNotBlank)
    }

private fun ParsedGeneratedKotlinFile.importedTargetsBySimpleName(): Map<String, Set<String>> =
    imports
        .mapNotNull { importLine ->
            val imported = importLine.removePrefix("import ").trim()
            val target = imported.substringBefore(" as ").trim()
            val simpleName = imported.substringAfter(" as ", missingDelimiterValue = "")
                .ifBlank { target.substringAfterLast('.') }
                .takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            simpleName to target
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, targets) -> targets.toSet() }

private fun Map<String, Set<String>>.mergeImportTargets(
    other: Map<String, Set<String>>,
): Map<String, Set<String>> =
    if (isEmpty()) {
        other
    } else {
        buildMap {
            putAll(this@mergeImportTargets)
            other.forEach { (name, targets) ->
                put(name, get(name).orEmpty() + targets)
            }
        }
    }

private fun ParsedGeneratedKotlinFile.declaredTopLevelNames(): Set<String> =
    generatedTopLevelDeclarationRegex.findAll(body)
        .map { match -> match.groupValues[1] }
        .toSet()

private val generatedTopLevelDeclarationRegex =
    Regex("""(?m)^(?:public|internal)\s+(?:open\s+|sealed\s+|data\s+|value\s+)?(?:class|interface|enum\s+class|object)\s+([A-Za-z_][A-Za-z0-9_]*)""")

private fun parseGeneratedKotlinFile(file: KotlinProjectionFile): ParsedGeneratedKotlinFile {
    val allLines = file.contents.lines()
    val packageLineIndex = allLines.indexOfFirst { it.trim().startsWith("package ") }
    require(packageLineIndex >= 0) {
        "Generated file ${file.relativePath} does not contain a package declaration."
    }
    val annotations = allLines.take(packageLineIndex).joinToString("\n").trim()
    val lines = allLines.drop(packageLineIndex + 1).dropWhile(String::isBlank)
    val imports = mutableListOf<String>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.startsWith("import ") -> {
                imports += line
                index += 1
            }
            line.isBlank() -> {
                index += 1
                if (imports.isNotEmpty()) {
                    break
                }
            }
            else -> break
        }
    }
    return ParsedGeneratedKotlinFile(
        fileAnnotations = annotations,
        packageDeclaration = allLines[packageLineIndex].trim(),
        imports = imports,
        body = lines.drop(index).joinToString("\n"),
    )
}
