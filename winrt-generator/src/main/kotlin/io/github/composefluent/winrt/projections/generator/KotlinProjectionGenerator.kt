package io.github.composefluent.winrt.projections.generator

import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerPlanDescriptor
import io.github.composefluent.winrt.metadata.WinRtAbiMarshalerSlotDescriptor
import io.github.composefluent.winrt.metadata.WinRtCustomMappedMemberOutputDescriptor
import io.github.composefluent.winrt.metadata.WinRtEventDefinition
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
import io.github.composefluent.winrt.metadata.WinRtPropertyDefinition
import io.github.composefluent.winrt.metadata.WinRtRequiredInterfaceAugmentationDescriptor
import io.github.composefluent.winrt.metadata.WinRtSignatureWriterDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDeclarationDescriptor
import io.github.composefluent.winrt.metadata.WinRtTypeDefinition
import io.github.composefluent.winrt.metadata.WinRtTypeRef
import io.github.composefluent.winrt.metadata.WinRtTypeKind
import io.github.composefluent.winrt.metadata.WinRtMetadataValidationOptions
import io.github.composefluent.winrt.metadata.projectionInventory
import io.github.composefluent.winrt.metadata.WinRtMetadataSemanticHelpers
import io.github.composefluent.winrt.metadata.requireValidForProjection
import io.github.composefluent.winrt.metadata.semanticHelpers
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
        validateGeneratorContracts(plans)
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
        validateGeneratorContracts(plans)
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

    private fun validateGeneratorContracts(plans: List<KotlinTypeProjectionPlan>) {
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
                        val interfaceType = plan.typesByQualifiedName[binding.qualifiedName]
                            ?: plan.typesByQualifiedName[binding.qualifiedName.substringBefore('<').removeSuffix("?")]
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
            plan.type.events.forEach { event ->
                validateEventDelegateContract(plan, event)
            }
            plan.instanceMemberBindings.forEach { binding ->
                validateProjectedAbiBindingContract(plan, binding.bindingName, binding.returnBinding, binding.parameterBindings)
            }
            plan.staticMemberBindings.forEach { binding ->
                validateProjectedAbiBindingContract(plan, binding.bindingName, binding.returnBinding, binding.parameterBindings)
            }
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

    private fun validateProjectedAbiBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        returnBinding: KotlinProjectionAbiTypeBinding,
        parameterBindings: List<KotlinProjectionAbiParameterBinding>,
    ) {
        validateProjectedAbiTypeBindingContract(plan, bindingName, "return", returnBinding)
        parameterBindings.forEach { parameter ->
            validateProjectedAbiTypeBindingContract(plan, bindingName, "parameter ${parameter.name}", parameter.typeBinding)
        }
    }

    private fun validateProjectedAbiTypeBindingContract(
        plan: KotlinTypeProjectionPlan,
        bindingName: String,
        bindingRole: String,
        typeBinding: KotlinProjectionAbiTypeBinding,
    ) {
        if (typeBinding.kind == KotlinProjectionAbiValueKind.Delegate) {
            val invokeShape = typeBinding.delegateInvokeShape
            require(invokeShape?.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole delegate ${typeBinding.resolvedTypeName} to carry metadata IID before projection rendering."
            }
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedRuntimeClass) {
            require(typeBinding.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole runtime class ${typeBinding.resolvedTypeName} to carry default-interface metadata IID before projection rendering."
            }
        }
        if (typeBinding.kind == KotlinProjectionAbiValueKind.ProjectedInterface) {
            require(typeBinding.interfaceId != null) {
                "Generator requires ${plan.projectionContractSubject()} ABI binding $bindingName $bindingRole interface ${typeBinding.resolvedTypeName} to carry metadata IID before projection rendering."
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
        typeBinding.typeArguments.forEach { argument ->
            validateProjectedAbiTypeBindingContract(plan, bindingName, bindingRole, argument)
        }
    }

    private fun eventDelegateType(
        delegateTypeName: String,
        plan: KotlinTypeProjectionPlan,
    ): WinRtTypeDefinition? {
        val rawName = delegateTypeName.substringBefore('<').removeSuffix("?")
        return plan.typesByQualifiedName[delegateTypeName]
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
