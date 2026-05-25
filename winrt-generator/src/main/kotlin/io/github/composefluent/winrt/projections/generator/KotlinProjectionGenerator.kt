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
