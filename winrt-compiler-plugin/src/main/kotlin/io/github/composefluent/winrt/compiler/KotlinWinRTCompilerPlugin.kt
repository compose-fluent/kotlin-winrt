package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.authoring.IndexedWinRTType
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoredTypeCandidate
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringCandidateFile
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTAuthoringMetadataModel
import io.github.composefluent.winrt.compiler.authoring.KotlinWinRTProjectionTypeIndexRecord
import io.github.composefluent.winrt.compiler.authoring.PROJECTION_PACKAGE_PREFIX
import io.github.composefluent.winrt.compiler.authoring.WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
import io.github.composefluent.winrt.compiler.authoring.inheritedOverridableInterfaceNames
import io.github.composefluent.winrt.compiler.authoring.projectionPackageToMetadataName
import io.github.composefluent.winrt.compiler.authoring.projectionTypeIndexRecordForSourceType
import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.JvmLoweredDeclarationOrigin
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperator
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrConstructorSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.builders.irAs
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.builders.declarations.buildValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.IrValueParameterBuilder
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.parentClassOrNull
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irIfNull
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irIfThen
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irBranch
import org.jetbrains.kotlin.ir.builders.irElseBranch
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irNotEquals
import org.jetbrains.kotlin.ir.builders.irNull
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irTry
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRTCommandLineProcessor : CommandLineProcessor {
    override val pluginId: String = PLUGIN_ID
    override val pluginOptions: Collection<AbstractCliOption> = listOf(
        CliOption(
            optionName = "metadataIndex",
            valueDescription = "<path>",
            description = "Path to the kotlin-winrt metadata index used by authoring analysis.",
            required = false,
        ),
        CliOption(
            optionName = "typeIndexOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt projection type index resource.",
            required = false,
        ),
        CliOption(
            optionName = "authoredCandidatesOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt authored candidates resource.",
            required = false,
        ),
        CliOption(
            optionName = "authoredMetadataOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt authored metadata descriptor.",
            required = false,
        ),
        CliOption(
            optionName = "authoredWinmdOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt authored WinMD file.",
            required = false,
        ),
        CliOption(
            optionName = "authoredHostManifestOutput",
            valueDescription = "<path>",
            description = "Path to the compiler-generated kotlin-winrt authored host manifest.",
            required = false,
        ),
        CliOption(
            optionName = "authoringAssemblyName",
            valueDescription = "<name>",
            description = "Assembly name used for compiler-generated kotlin-winrt authored metadata assets.",
            required = false,
        ),
        CliOption(
            optionName = "authoringTargetArtifactName",
            valueDescription = "<file>",
            description = "Target artifact file name used for compiler-generated kotlin-winrt authored host manifests.",
            required = false,
        ),
        CliOption(
            optionName = "compilerSupportManifest",
            valueDescription = "<path>",
            description = "Path to the generator-emitted kotlin-winrt compiler support manifest.",
            required = false,
        ),
        CliOption(
            optionName = "compilerSupportClassOutputDirectory",
            valueDescription = "<path>",
            description = "Directory for compiler-generated kotlin-winrt support class artifacts.",
            required = false,
        ),
    )

    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        if (option.optionName == "metadataIndex") {
            configuration.put(METADATA_INDEX_KEY, value)
        } else if (option.optionName == "typeIndexOutput") {
            configuration.put(TYPE_INDEX_OUTPUT_KEY, value)
        } else if (option.optionName == "authoredCandidatesOutput") {
            configuration.put(AUTHORED_CANDIDATES_OUTPUT_KEY, value)
        } else if (option.optionName == "authoredMetadataOutput") {
            configuration.put(AUTHORED_METADATA_OUTPUT_KEY, value)
        } else if (option.optionName == "authoredWinmdOutput") {
            configuration.put(AUTHORED_WINMD_OUTPUT_KEY, value)
        } else if (option.optionName == "authoredHostManifestOutput") {
            configuration.put(AUTHORED_HOST_MANIFEST_OUTPUT_KEY, value)
        } else if (option.optionName == "authoringAssemblyName") {
            configuration.put(AUTHORING_ASSEMBLY_NAME_KEY, value)
        } else if (option.optionName == "authoringTargetArtifactName") {
            configuration.put(AUTHORING_TARGET_ARTIFACT_NAME_KEY, value)
        } else if (option.optionName == "compilerSupportManifest") {
            configuration.put(COMPILER_SUPPORT_MANIFEST_KEY, value)
        } else if (option.optionName == "compilerSupportClassOutputDirectory") {
            configuration.put(COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY, value)
        }
    }

    companion object {
        const val PLUGIN_ID: String = "io.github.composefluent.winrt.compiler"
        val METADATA_INDEX_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt metadata index")
        val TYPE_INDEX_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt type index output")
        val AUTHORED_CANDIDATES_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authored candidates output")
        val AUTHORED_METADATA_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authored metadata output")
        val AUTHORED_WINMD_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authored WinMD output")
        val AUTHORED_HOST_MANIFEST_OUTPUT_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authored host manifest output")
        val AUTHORING_ASSEMBLY_NAME_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authoring assembly name")
        val AUTHORING_TARGET_ARTIFACT_NAME_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt authoring target artifact name")
        val COMPILER_SUPPORT_MANIFEST_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support manifest")
        val COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support class output directory")
    }
}

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRTCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = KotlinWinRTCommandLineProcessor.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            KotlinWinRTIrGenerationExtension(
                metadataIndexPath = configuration.get(KotlinWinRTCommandLineProcessor.METADATA_INDEX_KEY),
                typeIndexOutputPath = configuration.get(KotlinWinRTCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
                authoredCandidatesOutputPath = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_CANDIDATES_OUTPUT_KEY),
                authoredMetadataOutputPath = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_METADATA_OUTPUT_KEY),
                authoredWinmdOutputPath = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_WINMD_OUTPUT_KEY),
                authoredHostManifestOutputPath = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORED_HOST_MANIFEST_OUTPUT_KEY),
                authoringAssemblyName = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORING_ASSEMBLY_NAME_KEY),
                authoringTargetArtifactName = configuration.get(KotlinWinRTCommandLineProcessor.AUTHORING_TARGET_ARTIFACT_NAME_KEY),
                compilerSupportManifestPath = configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
                compilerSupportClassOutputDirectoryPath = configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
            ),
        )
    }
}

class KotlinWinRTIrGenerationExtension(
    private val metadataIndexPath: String?,
    private val typeIndexOutputPath: String?,
    private val authoredCandidatesOutputPath: String?,
    private val authoredMetadataOutputPath: String?,
    private val authoredWinmdOutputPath: String?,
    private val authoredHostManifestOutputPath: String?,
    private val authoringAssemblyName: String?,
    private val authoringTargetArtifactName: String?,
    private val compilerSupportManifestPath: String?,
    private val compilerSupportClassOutputDirectoryPath: String?,
) : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val compilerSupportEntries = readCompilerSupportManifest()
        val projectionRegistrarEntries = readProjectionRegistrarEntries(compilerSupportEntries)
        val projectionSupportOwnerIdentity = authoringTargetArtifactName
            ?.takeIf(String::isNotBlank)
            ?: compilerSupportEntries
                .filter { entry -> entry.kind == "projection-registrar" }
                .maxByOrNull(KotlinWinRTCompilerSupportManifestEntry::entries)
                ?.owner
                .orEmpty()
        val genericTypeInstantiationEntries = readGenericTypeInstantiationEntries(compilerSupportEntries)
        val genericTypeInstantiationSupportClassName = compilerSupportEntries
            .filter { entry -> entry.kind == "generic-type-instantiation" }
            .maxByOrNull(KotlinWinRTCompilerSupportManifestEntry::entries)
            ?.className
        val genericAbiSupportClassName = compilerSupportEntries
            .filter { entry -> entry.kind == "generic-abi-registry" }
            .maxByOrNull(KotlinWinRTCompilerSupportManifestEntry::entries)
            ?.className
        val authoringRegistrarEntries = readAuthoringTypeDetailsRegistrarEntries(compilerSupportEntries)
        writeCompilerSupportClasses(compilerSupportEntries, projectionRegistrarEntries, projectionSupportOwnerIdentity)
        if (moduleFragment.files.isEmpty()) {
            val winRTTypes = metadataIndexPath
                ?.takeIf(String::isNotBlank)
                ?.let { path -> readAuthoringMetadataIndex(Path.of(path)) }
                .orEmpty()
            writeProjectionTypeIndex(emptyList(), winRTTypes)
            writeAuthoredCandidates(emptyList(), winRTTypes)
            writeAuthoredSupportArtifacts(emptyList())
            return
        }
        val genericTypeInstantiationIntrinsicCalls =
            collectGenericTypeInstantiationSupportIntrinsicCalls(moduleFragment)
        val projectionSupportInitialize = addProjectionSupportInitializerFunction(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            entries = projectionRegistrarEntries,
            ownerIdentity = projectionSupportOwnerIdentity,
        )
        val genericTypeInstantiationSupport = addGenericTypeInstantiationSupportFunctions(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            entries = genericTypeInstantiationEntries,
            supportClassName = genericTypeInstantiationSupportClassName,
            ownerIdentity = projectionSupportOwnerIdentity,
            includeInitializeAll = "initializeAll" in genericTypeInstantiationIntrinsicCalls,
            includeInitializeBySourceType = "initializeBySourceType" in genericTypeInstantiationIntrinsicCalls,
        )
        val genericAbiSupport = addGenericAbiSupportFunctions(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            supportClassName = genericAbiSupportClassName,
        )
        lowerProjectionSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            initialize = projectionSupportInitialize,
        )
        lowerGenericTypeInstantiationSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            support = genericTypeInstantiationSupport,
        )
        lowerGenericAbiSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            support = genericAbiSupport,
        )
        lowerAuthoringSupportIntrinsicCalls(moduleFragment, pluginContext, authoringRegistrarEntries)
        lowerProjectionIntrinsics(moduleFragment, pluginContext)
        if (metadataIndexPath.isNullOrBlank()) {
            writeProjectionTypeIndex(emptyList(), emptyMap())
            writeAuthoredCandidates(emptyList(), emptyMap())
            writeAuthoredSupportArtifacts(emptyList())
            return
        }
        val winRTTypes = readAuthoringMetadataIndex(Path.of(metadataIndexPath))
        if (winRTTypes.isEmpty()) {
            writeProjectionTypeIndex(emptyList(), winRTTypes)
            writeAuthoredCandidates(emptyList(), winRTTypes)
            writeAuthoredSupportArtifacts(emptyList())
            return
        }
        val reportError: (String) -> Unit = { message ->
            pluginContext.reportCompilerPluginMessage(CompilerMessageSeverity.ERROR, message)
        }
        val generatedSourceRoot = generatedSourceRootFromMetadataIndex(metadataIndexPath)
        val classContexts = moduleFragment.files
            .asSequence()
            .filterNot { file -> isGeneratedSourceFile(file.fileEntry.name, generatedSourceRoot) }
            .filterNot { file -> file.isKotlinWinRTGeneratedFile() }
            .flatMap { file -> file.declarations.asSequence().flatMap { declaration -> classContextsIn(declaration).asSequence() } }
            .toList()
        val sourceSubtypedNames = sourceSubtypedNames(classContexts)
        val authoredTypeNames = classContexts
            .filter(::isEffectivelyAuthorable)
            .mapNotNull { context ->
                authoredTypeFor(
                    klass = context.klass,
                    winRTTypes = winRTTypes,
                    isPublic = isEffectivelyPublic(context),
                    sourceSubtypedNames = sourceSubtypedNames,
                )?.sourceTypeName
            }
            .toSet()
        lowerAuthoredTypeConstructors(moduleFragment, pluginContext, authoredTypeNames)
        lowerAuthoredTypeConstructorCalls(moduleFragment, pluginContext, authoredTypeNames)
        writeProjectionTypeIndex(classContexts, winRTTypes)
        val authoredCandidates = authoredCandidates(classContexts, winRTTypes, sourceSubtypedNames)
        writeAuthoredCandidates(authoredCandidates)
        writeAuthoredSupportArtifacts(authoredCandidates)
        reportRuntimeClassCastDiagnostics(moduleFragment, pluginContext, winRTTypes, authoredCandidates, generatedSourceRoot)
        classContexts.forEach { context ->
            val klass = context.klass
            if (!isEffectivelyAuthorable(context)) {
                return@forEach
            }
            val authoredType = authoredTypeFor(klass, winRTTypes, isEffectivelyPublic(context), sourceSubtypedNames) ?: return@forEach
            validateAuthoredType(klass, authoredType, pluginContext.afterK2, reportError)
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun reportRuntimeClassCastDiagnostics(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        winRTTypes: Map<String, IndexedWinRTType>,
        authoredCandidates: List<KotlinWinRTAuthoredTypeCandidate>,
        generatedSourceRoot: String?,
    ) {
        val runtimeClassNames = winRTTypes.values
            .asSequence()
            .filter { type -> type.kind == "RuntimeClass" }
            .mapTo(mutableSetOf(), IndexedWinRTType::qualifiedName)
        authoredCandidates.mapTo(runtimeClassNames, KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
        if (runtimeClassNames.isEmpty()) {
            return
        }
        val castOperators = setOf(IrTypeOperator.CAST, IrTypeOperator.SAFE_CAST, IrTypeOperator.INSTANCEOF)
        moduleFragment.files
            .asSequence()
            .filterNot { file -> isGeneratedSourceFile(file.fileEntry.name, generatedSourceRoot) }
            .filterNot { file -> file.isKotlinWinRTGeneratedFile() }
            .forEach { file ->
                file.transformChildrenVoid(
                    object : IrElementTransformerVoidWithContext() {
                        override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
                            val call = super.visitTypeOperator(expression) as IrTypeOperatorCall
                            if (call.operator !in castOperators) {
                                return call
                            }
                            val targetName = call.typeOperand.classFqName?.asString() ?: return call
                            val runtimeClassName = runtimeClassCastTarget(targetName, runtimeClassNames) ?: return call
                            pluginContext.reportCompilerPluginMessage(
                                CompilerMessageSeverity.WARNING,
                                "WinRT runtime class cast to $runtimeClassName is not projection-safe; use WinRT projection cast helpers instead.",
                            )
                            return call
                        }
                    },
                )
            }
    }

    private fun runtimeClassCastTarget(
        targetName: String,
        runtimeClassNames: Set<String>,
    ): String? =
        when {
            targetName in runtimeClassNames -> targetName
            else -> projectionPackageToMetadataName(targetName).takeIf { metadataName -> metadataName in runtimeClassNames }
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrFile.isKotlinWinRTGeneratedFile(): Boolean =
        annotations.any { annotation ->
            annotation.symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() == "kotlin.Suppress" &&
                annotation.arguments.any { argument ->
                    argument.stringConstantValue() == KOTLIN_WINRT_GENERATED_SUPPRESS_MARKER ||
                        KOTLIN_WINRT_GENERATED_SUPPRESS_MARKER in argument.stringArrayConstantValue()
                }
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    private fun lowerProjectionIntrinsics(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val lookupFile = moduleFragment.files.firstOrNull()
        val intrinsicClassId = ClassId.topLevel(WINRT_PROJECTION_INTRINSIC_FQ_NAME)
        val directLowerings = WinRTProjectionIntrinsicIrLowerings.create(pluginContext, lookupFile)
        val intrinsicFunctions = WINRT_PROJECTION_INTRINSIC_FUNCTIONS.associateWith { functionName ->
            pluginContext.findFunctionSymbols(CallableId(intrinsicClassId, Name.identifier(functionName)), lookupFile)
                .singleOrNull()
        }
            .filterValues { symbol -> symbol != null }
        if (intrinsicFunctions.isEmpty()) {
            return
        }
        var reportedMissingDirectLowering = false
        var reportedUnloweredIntrinsic = false
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    directLowerings?.lowerComVtableInvoke(
                        call,
                        pluginContext,
                        builderScope = currentScope?.scope?.scopeOwnerSymbol,
                    )
                        ?.let { return it }
                    val intrinsicName = intrinsicFunctions.entries
                        .firstOrNull { (_, symbol) -> symbol == call.symbol }
                        ?.key
                        ?: call.projectionIntrinsicFunctionName()
                        ?: return call
                    if (intrinsicName in RUNTIME_OWNED_PROJECTION_INTRINSICS) {
                        return call
                    }
                    if (directLowerings == null) {
                        if (!reportedMissingDirectLowering) {
                            reportedMissingDirectLowering = true
                            pluginContext.reportCompilerPluginMessage(
                                CompilerMessageSeverity.ERROR,
                                "kotlin-winrt projection intrinsic lowering requires compiling JVM projections with a JDK that exposes java.lang.foreign. Use JDK 25 for Kotlin/JVM compilation; otherwise generated WinRT projection calls would remain as runtime fallback intrinsics.",
                            )
                        }
                        return call
                    }
                    if (!directLowerings.hasDirectCallBackend) {
                        if (!reportedMissingDirectLowering) {
                            reportedMissingDirectLowering = true
                            pluginContext.reportCompilerPluginMessage(
                                CompilerMessageSeverity.ERROR,
                                "kotlin-winrt projection intrinsic lowering requires a direct-call backend. For JVM projections use JVM target 25 with a JDK that exposes java.lang.foreign; for mingwX64 projections the Kotlin/Native cinterop symbols must be visible to IR lowering.",
                            )
                        }
                        return call
                    }
                    directLowerings.lower(
                        intrinsicName,
                        call,
                        pluginContext,
                        builderScope = currentScope?.scope?.scopeOwnerSymbol,
                    )
                        ?.let { return it }
                    if (!reportedUnloweredIntrinsic) {
                        reportedUnloweredIntrinsic = true
                        pluginContext.reportCompilerPluginMessage(
                            CompilerMessageSeverity.ERROR,
                            "kotlin-winrt projection intrinsic $intrinsicName was recognized but could not be lowered. This would leave a runtime fallback call in generated projection bytecode.",
                        )
                    }
                    return call
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.projectionIntrinsicFunctionName(): String? {
        val name = symbol.owner.name.asString()
        if (!isProjectionIntrinsicFunction(name, (symbol.owner.parent as? IrClass)?.fqNameWhenAvailable?.asString())) {
            return null
        }
        return name
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private class WinRTProjectionIntrinsicIrLowerings private constructor(
        private val hStringCompanion: IrClassSymbol,
        private val hStringCreateReference: IrSimpleFunctionSymbol,
        private val hStringFromHandle: IrSimpleFunctionSymbol,
        private val hStringToKString: IrSimpleFunctionSymbol,
        private val hStringClose: IrSimpleFunctionSymbol,
        private val referencedHStringHandleGetter: IrSimpleFunctionSymbol,
        private val referencedHStringClose: IrSimpleFunctionSymbol,
        private val iWinRTObjectNativeObjectGetter: IrSimpleFunctionSymbol,
        private val comObjectReferencePointerGetter: IrSimpleFunctionSymbol,
        private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
        private val rawAddressValueGetter: IrSimpleFunctionSymbol,
        private val platformAbi: IrClassSymbol,
        private val platformAbiConfinedScope: IrSimpleFunctionSymbol,
        private val platformAbiFromRawComPtr: IrSimpleFunctionSymbol,
        private val platformAbiAllocatePointerSlot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt8Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt32Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateInt64Slot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateDoubleSlot: IrSimpleFunctionSymbol,
        private val platformAbiAllocateBytes: IrSimpleFunctionSymbol,
        private val platformAbiReadPointer: IrSimpleFunctionSymbol,
        private val platformAbiReadPointerAt: IrSimpleFunctionSymbol,
        private val platformAbiReadInt8: IrSimpleFunctionSymbol,
        private val platformAbiReadInt16: IrSimpleFunctionSymbol,
        private val platformAbiReadInt32: IrSimpleFunctionSymbol,
        private val platformAbiReadInt64: IrSimpleFunctionSymbol,
        private val platformAbiReadFloat: IrSimpleFunctionSymbol,
        private val platformAbiReadDouble: IrSimpleFunctionSymbol,
        private val platformAbiIsNullRawAddress: IrSimpleFunctionSymbol,
        private val platformAbiToRawComPtr: IrSimpleFunctionSymbol,
        private val nativeScopeClose: IrSimpleFunctionSymbol,
        private val nativeStructAdapterLayoutGetter: IrSimpleFunctionSymbol,
        private val nativeStructAdapterRead: IrSimpleFunctionSymbol,
        private val nativeStructAdapterWrite: IrSimpleFunctionSymbol,
        private val nativeStructAdapterDisposeAbi: IrSimpleFunctionSymbol,
        private val nativeStructLayoutSizeBytesGetter: IrSimpleFunctionSymbol,
        private val marshalerFromAbiArray: IrSimpleFunctionSymbol,
        private val marshalerDisposeAbiArray: IrSimpleFunctionSymbol,
        private val emptyList: IrSimpleFunctionSymbol,
        private val winRTObjectMarshaller: IrClassSymbol,
        private val winRTObjectMarshallerFromAbi: IrSimpleFunctionSymbol,
        private val iUnknownReferenceConstructor: IrConstructorSymbol,
        private val comObjectReferenceAsInspectable: IrSimpleFunctionSymbol,
        private val comObjectReferenceClose: IrSimpleFunctionSymbol,
        private val function1Invoke: IrSimpleFunctionSymbol,
        private val kotlinError: IrSimpleFunctionSymbol,
        private val hResultConstructor: IrConstructorSymbol,
        private val hResultRequireSuccess: IrSimpleFunctionSymbol,
        private val ubyteConstructor: IrConstructorSymbol?,
        private val ushortConstructor: IrConstructorSymbol?,
        private val uintConstructor: IrConstructorSymbol?,
        private val ulongConstructor: IrConstructorSymbol?,
        private val jvmFfmSymbols: JvmFfmSymbols?,
        private val nativeCInteropSymbols: NativeCInteropSymbols?,
    ) {
        val hasDirectCallBackend: Boolean
            get() = jvmFfmSymbols != null || nativeCInteropSymbols != null

        private fun selectedAbiSymbols(): SelectedProjectionIntrinsicAbiSymbols =
            selectProjectionIntrinsicAbiSymbols(
                hasJvmFfmSymbols = jvmFfmSymbols != null,
                hasNativeCInteropSymbols = nativeCInteropSymbols != null,
            )

        private fun SelectedProjectionIntrinsicAbiSymbols.jvmSymbols(): JvmFfmSymbols? =
            if (useJvmFfm) jvmFfmSymbols else null

        private fun SelectedProjectionIntrinsicAbiSymbols.nativeSymbols(): NativeCInteropSymbols? =
            if (useNativeCInterop) nativeCInteropSymbols else null

        fun lower(
            intrinsicName: String,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? =
            when (intrinsicName) {
                "callUnit" -> lowerDescriptorCallUnit(call, pluginContext, builderScope)
                "callBoolean" -> lowerDescriptorCallBoolean(call, pluginContext, builderScope)
                "callScalar" -> lowerDescriptorCallScalar(call, pluginContext, builderScope)
                "setString" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.String)
                "setBoolean" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Boolean)
                "setInt32" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Int32)
                "setUInt32" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.UInt32)
                "setInt64" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Int64)
                "setUInt64" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.UInt64)
                "setFloat" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Float)
                "setDouble" -> lowerOneArgumentUnit(call, pluginContext, builderScope, UnitCallAbiArgumentKind.Double)
                "getString" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.String)
                "getBoolean" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Boolean)
                "getNoExceptionBoolean" -> lowerNoArgumentGetter(
                    call,
                    pluginContext,
                    builderScope,
                    NoArgumentGetterReturnKind.Boolean,
                    checkHResult = false,
                )
                "getInt32" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Int32)
                "getUInt32" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.UInt32)
                "getInt64" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Int64)
                "getUInt64" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.UInt64)
                "getFloat" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Float)
                "getDouble" -> lowerNoArgumentGetter(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Double)
                "getStruct" -> lowerStructGetter(call, pluginContext, builderScope)
                "callStruct" -> lowerDescriptorCallStruct(call, pluginContext, builderScope)
                "getArray" -> lowerArrayGetter(call, pluginContext, builderScope)
                "getProjectedRuntimeClass" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                    nullable = false,
                )
                "getNullableProjectedRuntimeClass" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                    nullable = true,
                )
                "getProjectedInterface" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                    nullable = false,
                )
                "getNullableProjectedInterface" -> lowerProjectedObjectGetter(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                    nullable = true,
                )
                "staticGetArray" -> lowerStaticArrayGetter(call, pluginContext, builderScope, includeProjectedObjectArgument = false)
                "staticGetArrayWithProjectedObject" ->
                    lowerStaticArrayGetter(call, pluginContext, builderScope, includeProjectedObjectArgument = true)
                "staticCallProjectedRuntimeClassWithString" -> lowerStaticStringProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                )
                "staticCallProjectedInterfaceWithString" -> lowerStaticStringProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                )
                "callProjectedRuntimeClass" -> lowerDescriptorProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.RuntimeClass,
                )
                "callProjectedInterface" -> lowerDescriptorProjectedObjectCall(
                    call,
                    pluginContext,
                    builderScope,
                    ProjectedObjectGetterKind.Interface,
                )
                "callObject" -> lowerDescriptorCallObject(call, pluginContext, builderScope)
                "setStruct" -> lowerStructSetter(call, pluginContext, builderScope)
                else -> null
            }

        private fun lowerDescriptorProjectedObjectCall(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (
                jvmSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true &&
                nativeSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true
            ) {
                return null
            }
            val wrap = call.arguments.getOrNull(4) ?: return null
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 5) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }
                        val abiValues = argumentKinds.map(::abiValueFor)
                        val readResultBlock = builder.irBlock(resultType = call.type) {
                            +directCallUnitBlock(
                                jvmSymbols = jvmSymbols,
                                nativeSymbols = nativeSymbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                values = abiValues + builder.irGet(resultOut),
                                abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
                            )
                            val resultPointer = irTemporary(
                                value = builder.irCall(platformAbiReadPointer).apply {
                                    arguments[0] = builder.irGetObject(platformAbi)
                                    arguments[1] = builder.irGet(resultOut)
                                },
                                nameHint = "resultPointer",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            +builder.irIfThenElse(
                                type = call.type,
                                condition = builder.irCall(platformAbiIsNullRawAddress).apply {
                                    arguments[0] = builder.irGetObject(platformAbi)
                                    arguments[1] = builder.irGet(resultPointer)
                                },
                                thenPart = builder.irCall(kotlinError).apply {
                                    arguments[0] = builder.irString("WINRT_E_NULL_ABI_RETURN")
                                },
                                elsePart = wrapProjectedObjectResult(
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    callType = call.type,
                                    resultPointer = builder.irGet(resultPointer),
                                    wrap = wrap,
                                    kind = kind,
                                ),
                            )
                        }
                        if (structAbis.isEmpty() && stringAbis.isEmpty()) {
                            +readResultBlock
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = readResultBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerDescriptorCallObject(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (
                jvmSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true &&
                nativeSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true
            ) {
                return null
            }
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 4) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }
                        val abiValues = argumentKinds.map(::abiValueFor)
                        val readResultBlock = builder.irBlock(resultType = call.type) {
                            +directCallUnitBlock(
                                jvmSymbols = jvmSymbols,
                                nativeSymbols = nativeSymbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                values = abiValues + builder.irGet(resultOut),
                                abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
                            )
                            val resultPointer = irTemporary(
                                value = builder.irCall(platformAbiReadPointer).apply {
                                    arguments[0] = builder.irGetObject(platformAbi)
                                    arguments[1] = builder.irGet(resultOut)
                                },
                                nameHint = "resultPointer",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            +builder.irAs(
                                builder.irCall(winRTObjectMarshallerFromAbi).apply {
                                    arguments[0] = builder.irGetObject(winRTObjectMarshaller)
                                    arguments[1] = builder.irGet(resultPointer)
                                },
                                call.type,
                            )
                        }
                        if (structAbis.isEmpty() && stringAbis.isEmpty()) {
                            +readResultBlock
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = readResultBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerStaticStringProjectedObjectCall(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.String, UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.String, UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            val wrap = call.arguments.getOrNull(4) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val valueAbi = irTemporary(
                    value = builder.irCall(hStringCreateReference).apply {
                        arguments[0] = builder.irGetObject(hStringCompanion)
                        arguments[1] = value
                    },
                    nameHint = "valueAbi",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val nativeScope = irTemporary(
                            value = builder.irCall(platformAbiConfinedScope).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                            },
                            nameHint = "scope",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                val resultOut = irTemporary(
                                    value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(nativeScope)
                                    },
                                    nameHint = "resultOut",
                                    isMutable = false,
                                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                )
                                +directCallUnitBlock(
                                    jvmSymbols = jvmSymbols,
                                    nativeSymbols = nativeSymbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = listOf(UnitCallAbiArgumentKind.String, UnitCallAbiArgumentKind.Object),
                                    values = listOf(
                                        builder.irCall(referencedHStringHandleGetter).apply {
                                            arguments[0] = builder.irGet(valueAbi)
                                        },
                                        builder.irGet(resultOut),
                                    ),
                                )
                                val resultPointer = irTemporary(
                                    value = builder.irCall(platformAbiReadPointer).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(resultOut)
                                    },
                                    nameHint = "resultPointer",
                                    isMutable = false,
                                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                )
                                +builder.irIfThenElse(
                                    type = call.type,
                                    condition = builder.irCall(platformAbiIsNullRawAddress).apply {
                                        arguments[0] = builder.irGetObject(platformAbi)
                                        arguments[1] = builder.irGet(resultPointer)
                                    },
                                    thenPart = builder.irCall(kotlinError).apply {
                                        arguments[0] = builder.irString("WINRT_E_NULL_ABI_RETURN")
                                    },
                                    elsePart = wrapProjectedObjectResult(
                                        builder = builder,
                                        pluginContext = pluginContext,
                                        callType = call.type,
                                        resultPointer = builder.irGet(resultPointer),
                                        wrap = wrap,
                                        kind = kind,
                                    ),
                                )
                            },
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeScopeClose).apply {
                                    arguments[0] = builder.irGet(nativeScope)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(referencedHStringClose).apply {
                            arguments[0] = builder.irGet(valueAbi)
                        }
                    },
                )
            }
        }

        private fun lowerStaticArrayGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            includeProjectedObjectArgument: Boolean,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val projectedObject = if (includeProjectedObjectArgument) {
                call.arguments.getOrNull(3) ?: return null
            } else {
                null
            }
            val argumentKinds =
                if (projectedObject == null) {
                    listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object)
                } else {
                    listOf(
                        UnitCallAbiArgumentKind.Object,
                        UnitCallAbiArgumentKind.Object,
                        UnitCallAbiArgumentKind.Object,
                    )
                }
            if (
                jvmSymbols?.canLower(argumentKinds) != true &&
                nativeSymbols?.canLower(argumentKinds) != true
            ) {
                return null
            }
            val marshaler = call.arguments.getOrNull(if (includeProjectedObjectArgument) 4 else 3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val lengthOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateInt32Slot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "lengthOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val dataOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "dataOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val values =
                            if (projectedObject == null) {
                                listOf(builder.irGet(lengthOut), builder.irGet(dataOut))
                            } else {
                                listOf(
                                    projectedObjectAbi(builder, projectedObject),
                                    builder.irGet(lengthOut),
                                    builder.irGet(dataOut),
                                )
                            }
                        +directCallUnitBlock(
                            jvmSymbols = jvmSymbols,
                            nativeSymbols = nativeSymbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds,
                            values = values,
                        )
                        +decodeAbiArrayFromOutSlots(
                            builder = builder,
                            pluginContext = pluginContext,
                            callType = call.type,
                            marshaler = marshaler,
                            lengthOut = builder.irGet(lengthOut),
                            dataOut = builder.irGet(dataOut),
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerProjectedObjectGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            kind: ProjectedObjectGetterKind,
            nullable: Boolean,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val wrap = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +directCallUnitBlock(
                            jvmSymbols = jvmSymbols,
                            nativeSymbols = nativeSymbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(resultOut)),
                        )
                        val resultPointer = irTemporary(
                            value = builder.irCall(platformAbiReadPointer).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(resultOut)
                            },
                            nameHint = "resultPointer",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irIfThenElse(
                            type = call.type,
                            condition = builder.irCall(platformAbiIsNullRawAddress).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(resultPointer)
                            },
                            thenPart = if (nullable) {
                                builder.irNull(call.type)
                            } else {
                                builder.irCall(kotlinError).apply {
                                    arguments[0] = builder.irString("WINRT_E_NULL_ABI_RETURN")
                                }
                            },
                            elsePart = builder.irBlock(resultType = call.type) {
                                +wrapProjectedObjectResult(
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    callType = call.type,
                                    resultPointer = builder.irGet(resultPointer),
                                    wrap = wrap,
                                    kind = kind,
                                )
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun wrapProjectedObjectResult(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            resultPointer: IrExpression,
            wrap: IrExpression,
            kind: ProjectedObjectGetterKind,
        ): IrExpression =
            builder.irBlock(resultType = callType) {
                val resultReference = irTemporary(
                    value = builder.irCall(iUnknownReferenceConstructor).apply {
                        arguments[0] = builder.irCall(platformAbiToRawComPtr).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                            arguments[1] = resultPointer
                        }
                    },
                    nameHint = "resultReference",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irAs(
                    when (kind) {
                        ProjectedObjectGetterKind.RuntimeClass -> {
                            val resultInspectable = irTemporary(
                                value = builder.irCall(comObjectReferenceAsInspectable).apply {
                                    arguments[0] = builder.irGet(resultReference)
                                },
                                nameHint = "resultInspectable",
                                isMutable = false,
                                origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                            )
                            builder.irTry(
                                type = callType,
                                tryResult = builder.irCall(function1Invoke).apply {
                                    arguments[0] = wrap
                                    arguments[1] = builder.irGet(resultInspectable)
                                },
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    +builder.irCall(comObjectReferenceClose).apply {
                                        arguments[0] = builder.irGet(resultReference)
                                    }
                                },
                            )
                        }
                        ProjectedObjectGetterKind.Interface ->
                            builder.irCall(function1Invoke).apply {
                                arguments[0] = wrap
                                arguments[1] = builder.irGet(resultReference)
                            }
                    },
                    callType,
                )
            }

        private fun lowerArrayGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val marshaler = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val lengthOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateInt32Slot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "lengthOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val dataOut = irTemporary(
                            value = builder.irCall(platformAbiAllocatePointerSlot).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                            },
                            nameHint = "dataOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +directCallUnitBlock(
                            jvmSymbols = jvmSymbols,
                            nativeSymbols = nativeSymbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object, UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(lengthOut), builder.irGet(dataOut)),
                        )
                        val length = irTemporary(
                            value = builder.irCall(platformAbiReadInt32).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(lengthOut)
                            },
                            nameHint = "length",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val data = irTemporary(
                            value = builder.irCall(platformAbiReadPointer).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(dataOut)
                            },
                            nameHint = "data",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +decodeAbiArray(
                            builder = builder,
                            pluginContext = pluginContext,
                            callType = call.type,
                            marshaler = marshaler,
                            length = builder.irGet(length),
                            data = builder.irGet(data),
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun decodeAbiArrayFromOutSlots(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            marshaler: IrExpression,
            lengthOut: IrExpression,
            dataOut: IrExpression,
        ): IrExpression =
            builder.irBlock(resultType = callType) {
                val length = irTemporary(
                    value = builder.irCall(platformAbiReadInt32).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = lengthOut
                    },
                    nameHint = "length",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val data = irTemporary(
                    value = builder.irCall(platformAbiReadPointer).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = dataOut
                    },
                    nameHint = "data",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +decodeAbiArray(
                    builder = builder,
                    pluginContext = pluginContext,
                    callType = callType,
                    marshaler = marshaler,
                    length = builder.irGet(length),
                    data = builder.irGet(data),
                )
            }

        private fun decodeAbiArray(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            callType: org.jetbrains.kotlin.ir.types.IrType,
            marshaler: IrExpression,
            length: IrExpression,
            data: IrExpression,
        ): IrExpression =
            builder.irTry(
                type = callType,
                tryResult = builder.irBlock(resultType = callType) {
                    val decoded = irTemporary(
                        value = builder.irCall(marshalerFromAbiArray).apply {
                            arguments[0] = marshaler
                            arguments[1] = length
                            arguments[2] = data
                        },
                        nameHint = "decoded",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                    +builder.irIfNull(
                        type = callType,
                        subject = builder.irGet(decoded),
                        thenPart = builder.irAs(
                            builder.irCall(emptyList).apply {
                                typeArguments[0] = pluginContext.irBuiltIns.anyNType
                            },
                            callType,
                        ),
                        elsePart = builder.irAs(builder.irGet(decoded), callType),
                    )
                },
                catches = emptyList(),
                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                    +builder.irCall(marshalerDisposeAbiArray).apply {
                        arguments[0] = marshaler
                        arguments[1] = length
                        arguments[2] = data
                    }
                },
            )

        private fun lowerStructSetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            val adapter = call.arguments.getOrNull(4) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = pluginContext.irBuiltIns.unitType,
                    tryResult = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val valueAbi = irTemporary(
                            value = builder.irCall(platformAbiAllocateBytes).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                                arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                    arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                        arguments[0] = adapter
                                    }
                                }
                            },
                            nameHint = "valueAbi",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irCall(nativeStructAdapterWrite).apply {
                            arguments[0] = adapter
                            arguments[1] = value
                            arguments[2] = builder.irGet(valueAbi)
                        }
                        +builder.irTry(
                            type = pluginContext.irBuiltIns.unitType,
                            tryResult = directCallUnitBlock(
                                jvmSymbols = jvmSymbols,
                                nativeSymbols = nativeSymbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                                values = listOf(builder.irGet(valueAbi)),
                            ),
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                    arguments[0] = adapter
                                    arguments[1] = builder.irGet(valueAbi)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerStructGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val adapter = call.arguments.getOrNull(3) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateBytes).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                                arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                    arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                        arguments[0] = adapter
                                    }
                                }
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                +directCallUnitBlock(
                                    jvmSymbols = jvmSymbols,
                                    nativeSymbols = nativeSymbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                                    values = listOf(builder.irGet(resultOut)),
                                )
                                +builder.irAs(
                                    builder.irCall(nativeStructAdapterRead).apply {
                                        arguments[0] = adapter
                                        arguments[1] = builder.irGet(resultOut)
                                    },
                                    call.type,
                                )
                            },
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                    arguments[0] = adapter
                                    arguments[1] = builder.irGet(resultOut)
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerDescriptorCallStruct(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            val scope = builderScope ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (
                jvmSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true &&
                nativeSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true
            ) {
                return null
            }
            val adapter = call.arguments.getOrNull(4) ?: return null
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = 5) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = builder.irCall(platformAbiAllocateBytes).apply {
                                arguments[0] = builder.irGetObject(platformAbi)
                                arguments[1] = builder.irGet(nativeScope)
                                arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                    arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                        arguments[0] = adapter
                                    }
                                }
                            },
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(argumentKind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (argumentKind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val structAdapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(structAdapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(structAdapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += structAdapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }
                        val abiValues = argumentKinds.map(::abiValueFor)
                        +builder.irTry(
                            type = call.type,
                            tryResult = builder.irBlock(resultType = call.type) {
                                +directCallUnitBlock(
                                    jvmSymbols = jvmSymbols,
                                    nativeSymbols = nativeSymbols,
                                    builder = builder,
                                    pluginContext = pluginContext,
                                    reference = reference,
                                    slot = slot,
                                    argumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object,
                                    values = abiValues + builder.irGet(resultOut),
                                    abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
                                )
                                +builder.irAs(
                                    builder.irCall(nativeStructAdapterRead).apply {
                                        arguments[0] = adapter
                                        arguments[1] = builder.irGet(resultOut)
                                    },
                                    call.type,
                                )
                            },
                            catches = emptyList(),
                            finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                    arguments[0] = adapter
                                    arguments[1] = builder.irGet(resultOut)
                                }
                                structAbis.forEach { (structAdapter, valueAbi) ->
                                    +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                        arguments[0] = builder.irGet(structAdapter)
                                        arguments[1] = builder.irGet(valueAbi)
                                    }
                                }
                                stringAbis.forEach { stringAbi ->
                                    +builder.irCall(referencedHStringClose).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            },
                        )
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerNoArgumentGetter(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            returnKind: NoArgumentGetterReturnKind,
            checkHResult: Boolean = true,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            if (
                jvmSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true &&
                nativeSymbols?.canLower(listOf(UnitCallAbiArgumentKind.Object)) != true
            ) {
                return null
            }
            val scope = builderScope ?: return null
            if ((returnKind == NoArgumentGetterReturnKind.UInt8 && ubyteConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt16 && ushortConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt32 && uintConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt64 && ulongConstructor == null)
            ) {
                return null
            }
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = allocateGetterResultSlot(builder, returnKind, builder.irGet(nativeScope)),
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        +directCallUnitBlock(
                            jvmSymbols = jvmSymbols,
                            nativeSymbols = nativeSymbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = listOf(UnitCallAbiArgumentKind.Object),
                            values = listOf(builder.irGet(resultOut)),
                            checkHResult = checkHResult,
                        )
                        +readGetterResult(builder, pluginContext, returnKind, builder.irGet(resultOut))
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun allocateGetterResultSlot(
            builder: DeclarationIrBuilder,
            returnKind: NoArgumentGetterReturnKind,
            nativeScope: IrExpression,
        ): IrExpression =
            builder.irCall(
                when (returnKind) {
                    NoArgumentGetterReturnKind.String -> platformAbiAllocatePointerSlot
                    NoArgumentGetterReturnKind.Boolean -> platformAbiAllocateInt8Slot
                    NoArgumentGetterReturnKind.Int8,
                    NoArgumentGetterReturnKind.UInt8 -> platformAbiAllocateInt8Slot
                    NoArgumentGetterReturnKind.Int16 -> platformAbiAllocateBytes
                    NoArgumentGetterReturnKind.UInt16 -> platformAbiAllocateBytes
                    NoArgumentGetterReturnKind.Int32,
                    NoArgumentGetterReturnKind.UInt32 -> platformAbiAllocateInt32Slot
                    NoArgumentGetterReturnKind.Int64,
                    NoArgumentGetterReturnKind.UInt64 -> platformAbiAllocateInt64Slot
                    NoArgumentGetterReturnKind.Float -> platformAbiAllocateBytes
                    NoArgumentGetterReturnKind.Double -> platformAbiAllocateDoubleSlot
                    NoArgumentGetterReturnKind.RawAddress -> platformAbiAllocatePointerSlot
                },
            ).apply {
                arguments[0] = builder.irGetObject(platformAbi)
                arguments[1] = nativeScope
                if (
                    returnKind == NoArgumentGetterReturnKind.Int16 ||
                    returnKind == NoArgumentGetterReturnKind.UInt16 ||
                    returnKind == NoArgumentGetterReturnKind.Float
                ) {
                    arguments[2] = builder.irLong(if (returnKind == NoArgumentGetterReturnKind.Float) 4L else 2L)
                }
            }

        private fun readGetterResult(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            returnKind: NoArgumentGetterReturnKind,
            resultOut: IrExpression,
        ): IrExpression =
            when (returnKind) {
                NoArgumentGetterReturnKind.String -> readHStringGetterResult(builder, pluginContext, resultOut)
                NoArgumentGetterReturnKind.Boolean -> builder.irNotEquals(
                    builder.irCall(platformAbiReadInt8).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    },
                    builder.irByte(0),
                )
                NoArgumentGetterReturnKind.Int32 -> builder.irCall(platformAbiReadInt32).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.Int8 -> builder.irCall(platformAbiReadInt8).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt8 -> builder.irCall(requireNotNull(ubyteConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt8).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Int16 -> builder.irCall(platformAbiReadInt16).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt16 -> builder.irCall(requireNotNull(ushortConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt16).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.UInt32 -> builder.irCall(requireNotNull(uintConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt32).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Int64 -> builder.irCall(platformAbiReadInt64).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.UInt64 -> builder.irCall(requireNotNull(ulongConstructor)).apply {
                    arguments[0] = builder.irCall(platformAbiReadInt64).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = resultOut
                    }
                }
                NoArgumentGetterReturnKind.Float -> builder.irCall(platformAbiReadFloat).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.Double -> builder.irCall(platformAbiReadDouble).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
                NoArgumentGetterReturnKind.RawAddress -> builder.irCall(platformAbiReadPointer).apply {
                    arguments[0] = builder.irGetObject(platformAbi)
                    arguments[1] = resultOut
                }
            }

        private fun readHStringGetterResult(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            resultOut: IrExpression,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.stringType) {
                val value = irTemporary(
                    value = builder.irCall(hStringFromHandle).apply {
                        arguments[0] = builder.irGetObject(hStringCompanion)
                        arguments[1] = builder.irCall(platformAbiReadPointer).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                            arguments[1] = resultOut
                        }
                        arguments[2] = builder.irBoolean(true)
                    },
                    nameHint = "result",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = pluginContext.irBuiltIns.stringType,
                    tryResult = builder.irCall(hStringToKString).apply {
                        arguments[0] = builder.irGet(value)
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(hStringClose).apply {
                            arguments[0] = builder.irGet(value)
                        }
                    },
                )
            }

        private fun lowerOneArgumentUnit(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            argumentKind: UnitCallAbiArgumentKind,
        ): IrExpression? {
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val value = call.arguments.getOrNull(3) ?: return null
            return jvmFfmSymbols?.let { symbols ->
                lowerJvmFfmCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = listOf(value),
                    argumentKinds = listOf(argumentKind),
                )
            } ?: nativeCInteropSymbols?.let { symbols ->
                lowerNativeCInteropCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = listOf(value),
                    argumentKinds = listOf(argumentKind),
                )
            }
        }

        private fun booleanAbiValue(
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            value: IrExpression,
        ): IrExpression =
            builder.irIfThenElse(
                type = pluginContext.irBuiltIns.byteType,
                condition = value,
                thenPart = builder.irByte(1),
                elsePart = builder.irByte(0),
            )

        private fun lowerDescriptorCallUnit(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val shape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds)) ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            return jvmFfmSymbols?.let { symbols ->
                lowerJvmFfmCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = values,
                    argumentKinds = argumentKinds,
                    abiShape = shape,
                )
            } ?: nativeCInteropSymbols?.let { symbols ->
                lowerNativeCInteropCallUnitWithArguments(
                    symbols = symbols,
                    call = call,
                    pluginContext = pluginContext,
                    builderScope = builderScope,
                    reference = reference,
                    slot = slot,
                    values = values,
                    argumentKinds = argumentKinds,
                )
            }
        }

        private fun lowerDescriptorCallBoolean(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            return lowerDescriptorCallScalar(call, pluginContext, builderScope, NoArgumentGetterReturnKind.Boolean)
        }

        private fun lowerDescriptorCallScalar(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val returnShape = call.arguments.getOrNull(3)?.stringConstantValue() ?: return null
            val returnKind = when (returnShape) {
                "Int8" -> NoArgumentGetterReturnKind.Int8
                "UInt8" -> NoArgumentGetterReturnKind.UInt8
                "Int16" -> NoArgumentGetterReturnKind.Int16
                "UInt16" -> NoArgumentGetterReturnKind.UInt16
                "Int32" -> NoArgumentGetterReturnKind.Int32
                "UInt32" -> NoArgumentGetterReturnKind.UInt32
                "Int64" -> NoArgumentGetterReturnKind.Int64
                "UInt64" -> NoArgumentGetterReturnKind.UInt64
                "Float" -> NoArgumentGetterReturnKind.Float
                "Double" -> NoArgumentGetterReturnKind.Double
                "RawAddress" -> NoArgumentGetterReturnKind.RawAddress
                else -> return null
            }
            return lowerDescriptorCallScalar(
                call = call,
                pluginContext = pluginContext,
                builderScope = builderScope,
                returnKind = returnKind,
                abiShapeIndex = 4,
                varargIndex = 5,
            )
        }

        private fun lowerDescriptorCallScalar(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            returnKind: NoArgumentGetterReturnKind,
            abiShapeIndex: Int = 3,
            varargIndex: Int = 4,
        ): IrExpression? {
            val selectedSymbols = selectedAbiSymbols()
            val jvmSymbols = selectedSymbols.jvmSymbols()
            val nativeSymbols = selectedSymbols.nativeSymbols()
            val scope = builderScope ?: return null
            if ((returnKind == NoArgumentGetterReturnKind.UInt8 && ubyteConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt16 && ushortConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt32 && uintConstructor == null) ||
                (returnKind == NoArgumentGetterReturnKind.UInt64 && ulongConstructor == null) ||
                returnKind == NoArgumentGetterReturnKind.String
            ) {
                return null
            }
            val shape = call.arguments.getOrNull(abiShapeIndex)?.stringConstantValue() ?: return null
            val argumentKinds = UnitCallAbiShape.parse(shape) ?: return null
            if (
                jvmSymbols?.canLower(argumentKinds) != true &&
                nativeSymbols?.canLower(argumentKinds + UnitCallAbiArgumentKind.Object) != true
            ) {
                return null
            }
            val values = call.varargValues(UnitCallAbiShape.varargValueCount(argumentKinds), varargIndex = varargIndex) ?: return null
            val reference = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = call.type) {
                val nativeScope = irTemporary(
                    value = builder.irCall(platformAbiConfinedScope).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                    },
                    nameHint = "scope",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irTry(
                    type = call.type,
                    tryResult = builder.irBlock(resultType = call.type) {
                        val resultOut = irTemporary(
                            value = allocateGetterResultSlot(builder, returnKind, builder.irGet(nativeScope)),
                            nameHint = "resultOut",
                            isMutable = false,
                            origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                        )
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(kind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (kind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float,
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(nativeScope)
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }

                        val abiValues = argumentKinds.map(::abiValueFor)
                        val callArgumentKinds = argumentKinds + UnitCallAbiArgumentKind.Object
                        val callValues = abiValues + builder.irGet(resultOut)
                        val callBlock = if (jvmSymbols != null) {
                            jvmFfmCallUnitBlock(
                                symbols = jvmSymbols,
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = callArgumentKinds,
                                values = callValues,
                                abiShape = UnitCallAbiShape.appendToken(shape, "Object"),
                            )
                        } else {
                            nativeCInteropCallUnitBlock(
                                symbols = requireNotNull(nativeSymbols),
                                builder = builder,
                                pluginContext = pluginContext,
                                reference = reference,
                                slot = slot,
                                argumentKinds = callArgumentKinds,
                                values = callValues,
                            )
                        }
                        val readResult = readGetterResult(builder, pluginContext, returnKind, builder.irGet(resultOut))
                        if (stringAbis.isEmpty() && structAbis.isEmpty()) {
                            +callBlock
                            +readResult
                        } else {
                            +builder.irTry(
                                type = call.type,
                                tryResult = builder.irBlock(resultType = call.type) {
                                    +callBlock
                                    +readResult
                                },
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    },
                    catches = emptyList(),
                    finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        +builder.irCall(nativeScopeClose).apply {
                            arguments[0] = builder.irGet(nativeScope)
                        }
                    },
                )
            }
        }

        private fun lowerJvmFfmCallUnitWithArguments(
            symbols: JvmFfmSymbols,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            reference: IrExpression,
            slot: IrExpression,
            values: List<IrExpression>,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            abiShape: String? = null,
        ): IrExpression? {
            val scope = builderScope ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hasStructArguments = argumentKinds.any { it.isStruct }
                val nativeScope = if (hasStructArguments) {
                    irTemporary(
                        value = builder.irCall(platformAbiConfinedScope).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                        },
                        nameHint = "scope",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                } else {
                    null
                }
                fun callWithPreparedArguments(): IrExpression =
                    builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(kind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (kind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float -> value
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(requireNotNull(nativeScope))
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }

                        val abiValues = argumentKinds.map(::abiValueFor)
                        val callBlock = jvmFfmCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds,
                            values = abiValues,
                            abiShape = abiShape,
                        )
                        if (stringAbis.isEmpty() && structAbis.isEmpty()) {
                            +callBlock
                        } else {
                            +builder.irTry(
                                type = pluginContext.irBuiltIns.unitType,
                                tryResult = callBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    }
                if (nativeScope == null) {
                    +callWithPreparedArguments()
                } else {
                    +builder.irTry(
                        type = pluginContext.irBuiltIns.unitType,
                        tryResult = callWithPreparedArguments(),
                        catches = emptyList(),
                        finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            +builder.irCall(nativeScopeClose).apply {
                                arguments[0] = builder.irGet(nativeScope)
                            }
                        },
                    )
                }
            }
        }

        private fun lowerNativeCInteropCallUnitWithArguments(
            symbols: NativeCInteropSymbols,
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
            reference: IrExpression,
            slot: IrExpression,
            values: List<IrExpression>,
            argumentKinds: List<UnitCallAbiArgumentKind>,
        ): IrExpression? {
            val scope = builderScope ?: return null
            if (!symbols.canLower(argumentKinds)) {
                return null
            }
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hasStructArguments = argumentKinds.any { it.isStruct }
                val nativeScope = if (hasStructArguments) {
                    irTemporary(
                        value = builder.irCall(platformAbiConfinedScope).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                        },
                        nameHint = "scope",
                        isMutable = false,
                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                    )
                } else {
                    null
                }
                fun callWithPreparedArguments(): IrExpression =
                    builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                        val stringAbis = mutableListOf<org.jetbrains.kotlin.ir.declarations.IrVariable>()
                        val structAbis =
                            mutableListOf<Pair<org.jetbrains.kotlin.ir.declarations.IrVariable, org.jetbrains.kotlin.ir.declarations.IrVariable>>()
                        var valueIndex = 0
                        fun nextValue(): IrExpression = values[valueIndex++]
                        fun abiValueFor(kind: UnitCallAbiArgumentKind): IrExpression {
                            val value = nextValue()
                            return when (kind) {
                                UnitCallAbiArgumentKind.RawAddress,
                                UnitCallAbiArgumentKind.RawComPtr,
                                UnitCallAbiArgumentKind.Byte,
                                UnitCallAbiArgumentKind.Int16,
                                UnitCallAbiArgumentKind.Int32,
                                UnitCallAbiArgumentKind.UInt32,
                                UnitCallAbiArgumentKind.Int64,
                                UnitCallAbiArgumentKind.UInt64,
                                UnitCallAbiArgumentKind.Float -> value
                                UnitCallAbiArgumentKind.Double -> value
                                UnitCallAbiArgumentKind.Boolean -> booleanAbiValue(builder, pluginContext, value)
                                UnitCallAbiArgumentKind.Object -> projectedObjectAbi(builder, value)
                                UnitCallAbiArgumentKind.Struct1,
                                UnitCallAbiArgumentKind.Struct2,
                                UnitCallAbiArgumentKind.Struct4,
                                UnitCallAbiArgumentKind.Struct8,
                                UnitCallAbiArgumentKind.StructPointer -> {
                                    val adapter = irTemporary(
                                        value = nextValue(),
                                        nameHint = "structAdapter",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    val valueAbi = irTemporary(
                                        value = builder.irCall(platformAbiAllocateBytes).apply {
                                            arguments[0] = builder.irGetObject(platformAbi)
                                            arguments[1] = builder.irGet(requireNotNull(nativeScope))
                                            arguments[2] = builder.irCall(nativeStructLayoutSizeBytesGetter).apply {
                                                arguments[0] = builder.irCall(nativeStructAdapterLayoutGetter).apply {
                                                    arguments[0] = builder.irGet(adapter)
                                                }
                                            }
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    +builder.irCall(nativeStructAdapterWrite).apply {
                                        arguments[0] = builder.irGet(adapter)
                                        arguments[1] = value
                                        arguments[2] = builder.irGet(valueAbi)
                                    }
                                    structAbis += adapter to valueAbi
                                    builder.irGet(valueAbi)
                                }
                                UnitCallAbiArgumentKind.String -> {
                                    val stringAbi = irTemporary(
                                        value = builder.irCall(hStringCreateReference).apply {
                                            arguments[0] = builder.irGetObject(hStringCompanion)
                                            arguments[1] = value
                                        },
                                        nameHint = "value${valueIndex}Abi",
                                        isMutable = false,
                                        origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                                    )
                                    stringAbis += stringAbi
                                    builder.irCall(referencedHStringHandleGetter).apply {
                                        arguments[0] = builder.irGet(stringAbi)
                                    }
                                }
                            }
                        }

                        val abiValues = argumentKinds.map(::abiValueFor)
                        val callBlock = nativeCInteropCallUnitBlock(
                            symbols = symbols,
                            builder = builder,
                            pluginContext = pluginContext,
                            reference = reference,
                            slot = slot,
                            argumentKinds = argumentKinds,
                            values = abiValues,
                        )
                        if (stringAbis.isEmpty() && structAbis.isEmpty()) {
                            +callBlock
                        } else {
                            +builder.irTry(
                                type = pluginContext.irBuiltIns.unitType,
                                tryResult = callBlock,
                                catches = emptyList(),
                                finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                                    structAbis.forEach { (adapter, valueAbi) ->
                                        +builder.irCall(nativeStructAdapterDisposeAbi).apply {
                                            arguments[0] = builder.irGet(adapter)
                                            arguments[1] = builder.irGet(valueAbi)
                                        }
                                    }
                                    stringAbis.forEach { stringAbi ->
                                        +builder.irCall(referencedHStringClose).apply {
                                            arguments[0] = builder.irGet(stringAbi)
                                        }
                                    }
                                },
                            )
                        }
                    }
                if (nativeScope == null) {
                    +callWithPreparedArguments()
                } else {
                    +builder.irTry(
                        type = pluginContext.irBuiltIns.unitType,
                        tryResult = callWithPreparedArguments(),
                        catches = emptyList(),
                        finallyExpression = builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                            +builder.irCall(nativeScopeClose).apply {
                                arguments[0] = builder.irGet(nativeScope)
                            }
                        },
                    )
                }
            }
        }

        fun lowerComVtableInvoke(
            call: IrCall,
            pluginContext: IrPluginContext,
            builderScope: org.jetbrains.kotlin.ir.symbols.IrSymbol?,
        ): IrExpression? {
            val callShape = call.comVtableCallShape() ?: return null
            val symbols = jvmFfmSymbols ?: return null
            if (!symbols.canLower(callShape.argumentKinds)) {
                return null
            }
            val scope = builderScope ?: return null
            val instance = call.arguments.getOrNull(1) ?: return null
            val slot = call.arguments.getOrNull(2) ?: return null
            val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
            return builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
                val hResultValue = irTemporary(
                    value = jvmFfmCallRaw(
                        symbols = symbols,
                        builder = builder,
                        pluginContext = pluginContext,
                        instancePointer = instance,
                        slot = slot,
                        argumentKinds = callShape.argumentKinds,
                        values = callShape.values,
                    ),
                    nameHint = "hr",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irGet(hResultValue)
            }
        }

        @OptIn(UnsafeDuringIrConstructionAPI::class)
        private fun IrCall.comVtableCallShape(): ComVtableCallShape? {
            val ownerClass = symbol.owner.parent as? IrClass ?: return null
            if (ownerClass.fqNameWhenAvailable != WINRT_COM_VTABLE_INVOKER_FQ_NAME) {
                return null
            }
            val regularParameters = symbol.owner.parameters.filter { parameter ->
                parameter.kind == IrParameterKind.Regular
            }
            return when (symbol.owner.name.asString()) {
                "invoke" -> ComVtableCallShape(emptyList(), emptyList()).takeIf {
                    regularParameters.size == 2
                }
                "invokeArgs" -> {
                    if (regularParameters.size < 3) {
                        return null
                    }
                    val argumentKinds = regularParameters.drop(2).map { parameter ->
                        parameter.type.comVtableAbiArgumentKind() ?: return null
                    }
                    val values = mutableListOf<IrExpression>()
                    for (index in argumentKinds.indices) {
                        values += arguments.getOrNull(index + 3) ?: return null
                    }
                    ComVtableCallShape(argumentKinds, values)
                }
                "invokeGenericArgs" -> {
                    if (regularParameters.size != 3) {
                        return null
                    }
                    val vararg = arguments.getOrNull(3) as? IrVararg ?: return null
                    val values = vararg.elements.map { element ->
                        element as? IrExpression ?: return null
                    }
                    val argumentKinds = values.map { value ->
                        value.type.comVtableAbiArgumentKind() ?: return null
                    }
                    ComVtableCallShape(argumentKinds, values)
                }
                else -> null
            }
        }

        private data class ComVtableCallShape(
            val argumentKinds: List<UnitCallAbiArgumentKind>,
            val values: List<IrExpression>,
        )

        private fun org.jetbrains.kotlin.ir.types.IrType.comVtableAbiArgumentKind(): UnitCallAbiArgumentKind? =
            when (classFqName) {
                WINRT_RAW_ADDRESS_FQ_NAME -> UnitCallAbiArgumentKind.RawAddress
                WINRT_RAW_COM_PTR_FQ_NAME -> UnitCallAbiArgumentKind.RawComPtr
                KOTLIN_BYTE_FQ_NAME -> UnitCallAbiArgumentKind.Byte
                KOTLIN_SHORT_FQ_NAME -> UnitCallAbiArgumentKind.Int16
                KOTLIN_INT_FQ_NAME -> UnitCallAbiArgumentKind.Int32
                KOTLIN_UINT_FQ_NAME -> UnitCallAbiArgumentKind.UInt32
                KOTLIN_LONG_FQ_NAME -> UnitCallAbiArgumentKind.Int64
                KOTLIN_ULONG_FQ_NAME -> UnitCallAbiArgumentKind.UInt64
                KOTLIN_FLOAT_FQ_NAME -> UnitCallAbiArgumentKind.Float
                KOTLIN_DOUBLE_FQ_NAME -> UnitCallAbiArgumentKind.Double
                else -> null
            }

        private fun jvmFfmCallUnitBlock(
            symbols: JvmFfmSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            reference: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
            abiShape: String? = null,
            checkHResult: Boolean = true,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hResultValue = irTemporary(
                    value = jvmFfmCallRaw(
                        symbols = symbols,
                        builder = builder,
                        pluginContext = pluginContext,
                        instancePointer = referencePointer(builder, reference),
                        slot = slot,
                        argumentKinds = argumentKinds,
                        values = values,
                        abiShape = abiShape,
                    ),
                    nameHint = "hr",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                if (checkHResult) {
                    +builder.irCall(hResultRequireSuccess).apply {
                        arguments[0] = builder.irCall(hResultConstructor).apply {
                            arguments[0] = builder.irGet(hResultValue)
                        }
                        arguments[1] = builder.irString("WinRT call")
                    }
                }
                +builder.irUnit()
            }

        private fun directCallUnitBlock(
            jvmSymbols: JvmFfmSymbols?,
            nativeSymbols: NativeCInteropSymbols?,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            reference: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
            abiShape: String? = null,
            checkHResult: Boolean = true,
        ): IrExpression =
            if (jvmSymbols != null) {
                jvmFfmCallUnitBlock(
                    symbols = jvmSymbols,
                    builder = builder,
                    pluginContext = pluginContext,
                    reference = reference,
                    slot = slot,
                    argumentKinds = argumentKinds,
                    values = values,
                    abiShape = abiShape,
                    checkHResult = checkHResult,
                )
            } else {
                nativeCInteropCallUnitBlock(
                    symbols = requireNotNull(nativeSymbols),
                    builder = builder,
                    pluginContext = pluginContext,
                    reference = reference,
                    slot = slot,
                    argumentKinds = argumentKinds,
                    values = values,
                    checkHResult = checkHResult,
                )
            }

        private fun jvmFfmCallRaw(
            symbols: JvmFfmSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            instancePointer: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
            abiShape: String? = null,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
                val instanceSegment = irTemporary(
                    value = symbols.segmentFromRawComPtr(builder, instancePointer),
                    nameHint = "instanceSegment",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val function = irTemporary(
                    value = symbols.vtableEntry(builder, builder.irGet(instanceSegment), slot),
                    nameHint = "function",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val handle = irTemporary(
                    value = if (abiShape == null) {
                        symbols.downcallHandle(builder, argumentKinds)
                    } else {
                        symbols.downcallHandle(builder, abiShape)
                    },
                    nameHint = "handle",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                +builder.irAs(
                    symbols.invokeHResultDowncall(
                        builder = builder,
                        pluginContext = pluginContext,
                        handle = builder.irGet(handle),
                        arguments = listOf(builder.irGet(function), builder.irGet(instanceSegment)) +
                            values.mapIndexed { index, value ->
                                symbols.jvmCarrier(builder, argumentKinds[index], value)
                            },
                    ),
                    pluginContext.irBuiltIns.intType,
                )
            }

        private fun nativeCInteropCallUnitBlock(
            symbols: NativeCInteropSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            reference: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
            checkHResult: Boolean = true,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.unitType) {
                val hResultValue = irTemporary(
                    value = nativeCInteropCallRaw(
                        symbols = symbols,
                        builder = builder,
                        pluginContext = pluginContext,
                        instancePointer = referencePointer(builder, reference),
                        slot = slot,
                        argumentKinds = argumentKinds,
                        values = values,
                    ),
                    nameHint = "hr",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                if (checkHResult) {
                    +builder.irCall(hResultRequireSuccess).apply {
                        arguments[0] = builder.irCall(hResultConstructor).apply {
                            arguments[0] = builder.irGet(hResultValue)
                        }
                        arguments[1] = builder.irString("WinRT call")
                    }
                }
                +builder.irUnit()
            }

        private fun nativeCInteropCallRaw(
            symbols: NativeCInteropSymbols,
            builder: DeclarationIrBuilder,
            pluginContext: IrPluginContext,
            instancePointer: IrExpression,
            slot: IrExpression,
            argumentKinds: List<UnitCallAbiArgumentKind>,
            values: List<IrExpression>,
        ): IrExpression =
            builder.irBlock(resultType = pluginContext.irBuiltIns.intType) {
                val instance = irTemporary(
                    value = instancePointer,
                    nameHint = "instance",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val vtable = irTemporary(
                    value = builder.irCall(platformAbiReadPointer).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = builder.irCall(platformAbiFromRawComPtr).apply {
                            arguments[0] = builder.irGetObject(platformAbi)
                            arguments[1] = builder.irGet(instance)
                        }
                    },
                    nameHint = "vtable",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val functionAddress = irTemporary(
                    value = builder.irCall(platformAbiReadPointerAt).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = builder.irGet(vtable)
                        arguments[2] = slot
                    },
                    nameHint = "functionAddress",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val receiver = symbols.rawComPtrToOpaquePointer(builder, builder.irGet(instance))
                val nativeValues = values.mapIndexed { index, value ->
                    nativeCInteropCarrier(
                        symbols = symbols,
                        builder = builder,
                        argumentKind = argumentKinds[index],
                        value = value,
                    )
                }
                val parameterTypes = listOf(receiver.type) + nativeValues.map { it.type }
                val function = irTemporary(
                    value = symbols.functionPointer(
                        builder = builder,
                        pluginContext = pluginContext,
                        address = builder.irGet(functionAddress),
                        parameterTypes = parameterTypes,
                    ),
                    nameHint = "function",
                    isMutable = false,
                    origin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
                )
                val invoke = symbols.invokeForArity(parameterTypes.size)
                    ?: error("kotlin-winrt compiler plugin could not find kotlinx.cinterop.invoke/${parameterTypes.size}.")
                +builder.irCall(invoke, pluginContext.irBuiltIns.intType).apply {
                    (parameterTypes + pluginContext.irBuiltIns.intType).forEachIndexed { index, type ->
                        typeArguments[index] = type
                    }
                    arguments[0] = builder.irGet(function)
                    arguments[1] = receiver
                    nativeValues.forEachIndexed { index, value ->
                        arguments[index + 2] = value
                    }
                }
            }

        private fun nativeCInteropCarrier(
            symbols: NativeCInteropSymbols,
            builder: DeclarationIrBuilder,
            argumentKind: UnitCallAbiArgumentKind,
            value: IrExpression,
        ): IrExpression =
            when (argumentKind) {
                UnitCallAbiArgumentKind.Struct1 ->
                    builder.irCall(platformAbiReadInt8).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = value
                    }
                UnitCallAbiArgumentKind.Struct2 ->
                    builder.irCall(platformAbiReadInt16).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = value
                    }
                UnitCallAbiArgumentKind.Struct4 ->
                    builder.irCall(platformAbiReadInt32).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = value
                    }
                UnitCallAbiArgumentKind.Struct8 ->
                    builder.irCall(platformAbiReadInt64).apply {
                        arguments[0] = builder.irGetObject(platformAbi)
                        arguments[1] = value
                    }
                UnitCallAbiArgumentKind.StructPointer -> symbols.rawAddressToOpaquePointer(builder, value)
                else -> symbols.nativeCarrier(builder, argumentKind, value)
            }

        private fun referencePointer(
            builder: DeclarationIrBuilder,
            reference: IrExpression,
        ): IrExpression =
            builder.irCall(comObjectReferencePointerGetter).apply {
                arguments[0] = reference
            }

        private fun IrExpression.stringConstantValue(): String? =
            (this as? IrConst)?.value as? String

        private fun IrCall.varargValues(expectedCount: Int, varargIndex: Int = 4): List<IrExpression>? {
            val varargArgument = arguments.getOrNull(varargIndex)
            if (expectedCount == 0 && varargArgument == null) {
                return emptyList()
            }
            val vararg = varargArgument as? IrVararg ?: return null
            val values = vararg.elements.map { element -> element as? IrExpression ?: return null }
            return values.takeIf { it.size == expectedCount }
        }

        private enum class UnitCallAbiArgumentKind {
            RawAddress,
            RawComPtr,
            Byte,
            Int16,
            Int32,
            UInt32,
            Int64,
            UInt64,
            Float,
            Double,
            Boolean,
            String,
            Struct1,
            Struct2,
            Struct4,
            Struct8,
            StructPointer,
            Object,
        }

        private val UnitCallAbiArgumentKind.isStruct: Boolean
            get() = when (this) {
                UnitCallAbiArgumentKind.Struct1,
                UnitCallAbiArgumentKind.Struct2,
                UnitCallAbiArgumentKind.Struct4,
                UnitCallAbiArgumentKind.Struct8,
                UnitCallAbiArgumentKind.StructPointer -> true
                else -> false
            }

        private enum class NoArgumentGetterReturnKind {
            String,
            Boolean,
            Int8,
            UInt8,
            Int16,
            UInt16,
            Int32,
            UInt32,
            Int64,
            UInt64,
            Float,
            Double,
            RawAddress,
        }

        private enum class ProjectedObjectGetterKind {
            RuntimeClass,
            Interface,
        }

        private object UnitCallAbiShape {
            fun parse(value: String): List<UnitCallAbiArgumentKind>? {
                if (value.isBlank()) {
                    return emptyList()
                }
                return value.split(',').map { token ->
                    when (token) {
                        "RawAddress" -> UnitCallAbiArgumentKind.RawAddress
                        "RawComPtr" -> UnitCallAbiArgumentKind.RawComPtr
                        "Byte" -> UnitCallAbiArgumentKind.Byte
                        "Int16" -> UnitCallAbiArgumentKind.Int16
                        "Int32" -> UnitCallAbiArgumentKind.Int32
                        "UInt32" -> UnitCallAbiArgumentKind.UInt32
                        "Int64" -> UnitCallAbiArgumentKind.Int64
                        "UInt64" -> UnitCallAbiArgumentKind.UInt64
                        "Float" -> UnitCallAbiArgumentKind.Float
                        "Double" -> UnitCallAbiArgumentKind.Double
                        "Boolean" -> UnitCallAbiArgumentKind.Boolean
                        "String" -> UnitCallAbiArgumentKind.String
                        "Struct" -> UnitCallAbiArgumentKind.StructPointer
                        "Object" -> UnitCallAbiArgumentKind.Object
                        else -> parseStructLayoutToken(token) ?: return null
                    }
                }
            }

            fun varargValueCount(argumentKinds: List<UnitCallAbiArgumentKind>): Int =
                argumentKinds.sumOf { kind ->
                    when (kind) {
                        UnitCallAbiArgumentKind.Struct1,
                        UnitCallAbiArgumentKind.Struct2,
                        UnitCallAbiArgumentKind.Struct4,
                        UnitCallAbiArgumentKind.Struct8,
                        UnitCallAbiArgumentKind.StructPointer -> 2
                        else -> 1
                    }
                }

            fun appendToken(shape: String, token: String): String =
                if (shape.isBlank()) token else "$shape,$token"

            private fun parseStructLayoutToken(token: String): UnitCallAbiArgumentKind? {
                val match = STRUCT_LAYOUT_TOKEN.matchEntire(token) ?: return null
                return when (match.groupValues[1].toLongOrNull()) {
                    1L -> UnitCallAbiArgumentKind.Struct1
                    2L -> UnitCallAbiArgumentKind.Struct2
                    4L -> UnitCallAbiArgumentKind.Struct4
                    8L -> UnitCallAbiArgumentKind.Struct8
                    null -> null
                    else -> UnitCallAbiArgumentKind.StructPointer
                }
            }

            private val STRUCT_LAYOUT_TOKEN = Regex("""Struct(\d+)_\d+""")
        }

        private fun projectedObjectAbi(
            builder: DeclarationIrBuilder,
            value: IrExpression,
        ): IrExpression =
            builder.irCall(platformAbiFromRawComPtr).apply {
                arguments[0] = builder.irGetObject(platformAbi)
                arguments[1] = builder.irCall(comObjectReferencePointerGetter).apply {
                    arguments[0] = builder.irCall(iWinRTObjectNativeObjectGetter).apply {
                        arguments[0] = value
                    }
                }
            }

        companion object {
            fun create(pluginContext: IrPluginContext, fromFile: IrFile?): WinRTProjectionIntrinsicIrLowerings? {
                val hString = pluginContext.findClassSymbol(WINRT_HSTRING_CLASS_ID, fromFile)
                    ?: return null
                val hStringCompanion = hString.owner.declarations
                    .filterIsInstance<IrClass>()
                    .singleOrNull { it.name.asString() == "Companion" }
                    ?.symbol
                    ?: return null
                val hStringCreateReference = hStringCompanion.functionNamed("createReference") ?: return null
                val hStringFromHandle = hStringCompanion.functionNamed("fromHandle") ?: return null
                val hStringToKString = hString.functionNamed("toKString") ?: return null
                val hStringClose = hString.functionNamed("close") ?: return null
                val referencedHString = pluginContext.findClassSymbol(WINRT_REFERENCED_HSTRING_CLASS_ID, fromFile)
                    ?: return null
                val referencedHStringHandleGetter = referencedHString.propertyGetter("handle") ?: return null
                val referencedHStringClose = referencedHString.functionNamed("close") ?: return null
                val iWinRTObject = pluginContext.findClassSymbol(WINRT_IWINRT_OBJECT_CLASS_ID, fromFile)
                    ?: return null
                val iWinRTObjectNativeObjectGetter = iWinRTObject.propertyGetter("nativeObject") ?: return null
                val comObjectReference = pluginContext.findClassSymbol(WINRT_COM_OBJECT_REFERENCE_CLASS_ID, fromFile)
                    ?: return null
                val comObjectReferencePointerGetter = comObjectReference.propertyGetter("pointer") ?: return null
                val rawComPtr = pluginContext.findClassSymbol(WINRT_RAW_COM_PTR_CLASS_ID, fromFile)
                    ?: return null
                val rawComPtrValueGetter = rawComPtr.propertyGetter("value") ?: return null
                val rawAddress = pluginContext.findClassSymbol(WINRT_RAW_ADDRESS_CLASS_ID, fromFile)
                    ?: return null
                val rawAddressValueGetter = rawAddress.propertyGetter("value") ?: return null
                val platformAbi = pluginContext.findClassSymbol(WINRT_PLATFORM_ABI_CLASS_ID, fromFile)
                    ?: return null
                val nativeScope = pluginContext.findClassSymbol(WINRT_NATIVE_SCOPE_CLASS_ID, fromFile)
                    ?: return null
                val platformAbiConfinedScope = platformAbi.functionNamed("confinedScope") ?: return null
                val platformAbiFromRawComPtr = platformAbi.functionNamed("fromRawComPtr") ?: return null
                val platformAbiAllocatePointerSlot = platformAbi.functionNamed("allocatePointerSlot") ?: return null
                val platformAbiAllocateInt8Slot = platformAbi.functionNamed("allocateInt8Slot") ?: return null
                val platformAbiAllocateInt32Slot = platformAbi.functionNamed("allocateInt32Slot") ?: return null
                val platformAbiAllocateInt64Slot = platformAbi.functionNamed("allocateInt64Slot") ?: return null
                val platformAbiAllocateDoubleSlot = platformAbi.functionNamed("allocateDoubleSlot") ?: return null
                val platformAbiAllocateBytes = platformAbi.functionNamedWithRegularParameterCount("allocateBytes", 2) ?: return null
                val platformAbiReadPointer = platformAbi.functionNamed("readPointer") ?: return null
                val platformAbiReadPointerAt = platformAbi.functionNamed("readPointerAt") ?: return null
                val platformAbiReadInt8 = platformAbi.functionNamed("readInt8") ?: return null
                val platformAbiReadInt16 = platformAbi.functionNamed("readInt16") ?: return null
                val platformAbiReadInt32 = platformAbi.functionNamed("readInt32") ?: return null
                val platformAbiReadInt64 = platformAbi.functionNamed("readInt64") ?: return null
                val platformAbiReadFloat = platformAbi.functionNamed("readFloat") ?: return null
                val platformAbiReadDouble = platformAbi.functionNamed("readDouble") ?: return null
                val platformAbiIsNullRawAddress =
                    platformAbi.functionNamedWithValueParameterTypes("isNull", WINRT_RAW_ADDRESS_FQ_NAME) ?: return null
                val platformAbiToRawComPtr =
                    platformAbi.functionNamedWithValueParameterTypes("toRawComPtr", WINRT_RAW_ADDRESS_FQ_NAME) ?: return null
                val nativeScopeClose = nativeScope.functionNamed("close") ?: return null
                val nativeStructAdapter = pluginContext.findClassSymbol(WINRT_NATIVE_STRUCT_ADAPTER_CLASS_ID, fromFile)
                    ?: return null
                val nativeStructLayout = pluginContext.findClassSymbol(WINRT_NATIVE_STRUCT_LAYOUT_CLASS_ID, fromFile)
                    ?: return null
                val nativeStructAdapterLayoutGetter = nativeStructAdapter.propertyGetter("layout") ?: return null
                val nativeStructAdapterRead = nativeStructAdapter.functionNamed("read") ?: return null
                val nativeStructAdapterWrite = nativeStructAdapter.functionNamed("write") ?: return null
                val nativeStructAdapterDisposeAbi = nativeStructAdapter.functionNamed("disposeAbi") ?: return null
                val nativeStructLayoutSizeBytesGetter = nativeStructLayout.propertyGetter("sizeBytes") ?: return null
                val marshaler = pluginContext.findClassSymbol(WINRT_MARSHALER_CLASS_ID, fromFile)
                    ?: return null
                val marshalerFromAbiArray = marshaler.functionNamed("fromAbiArray") ?: return null
                val marshalerDisposeAbiArray = marshaler.functionNamed("disposeAbiArray") ?: return null
                val winRTObjectMarshaller = pluginContext.findClassSymbol(WINRT_OBJECT_MARSHALLER_CLASS_ID, fromFile)
                    ?: return null
                val winRTObjectMarshallerFromAbi = winRTObjectMarshaller.functionNamed("fromAbi") ?: return null
                val emptyList = pluginContext.findFunctionSymbols(
                    CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
                    fromFile,
                ).singleOrNull() ?: return null
                val iUnknownReference = pluginContext.findClassSymbol(WINRT_IUNKNOWN_REFERENCE_CLASS_ID, fromFile)
                    ?: return null
                val iUnknownReferenceConstructor =
                    iUnknownReference.constructorWithRegularParameterCount(5) ?: return null
                val comObjectReferenceAsInspectable = comObjectReference.functionNamed("asInspectable") ?: return null
                val comObjectReferenceClose = comObjectReference.functionNamed("close") ?: return null
                val function1 = pluginContext.findClassSymbol(KOTLIN_FUNCTION1_CLASS_ID, fromFile)
                    ?: return null
                val function1Invoke = function1.functionNamed("invoke") ?: return null
                val kotlinError = pluginContext.findFunctionSymbols(
                    CallableId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("error")),
                    fromFile,
                ).singleOrNull() ?: return null
                val hResult = pluginContext.findClassSymbol(WINRT_HRESULT_CLASS_ID, fromFile)
                    ?: return null
                val hResultConstructor = hResult.owner.declarations
                    .filterIsInstance<IrConstructor>()
                    .singleOrNull()
                    ?.symbol
                    ?: return null
                val hResultRequireSuccess = hResult.functionNamed("requireSuccess") ?: return null
                val jvmFfmSymbols = JvmFfmSymbols.create(
                    pluginContext = pluginContext,
                    fromFile = fromFile,
                    rawComPtrValueGetter = rawComPtrValueGetter,
                    rawAddressValueGetter = rawAddressValueGetter,
                )
                val nativeCInteropSymbols = NativeCInteropSymbols.create(
                    pluginContext = pluginContext,
                    fromFile = fromFile,
                    rawComPtrValueGetter = rawComPtrValueGetter,
                    rawAddressValueGetter = rawAddressValueGetter,
                )
                val ubyteConstructor = pluginContext.findClassSymbol(KOTLIN_UBYTE_CLASS_ID, fromFile)
                    ?.singleValueConstructor()
                val ushortConstructor = pluginContext.findClassSymbol(KOTLIN_USHORT_CLASS_ID, fromFile)
                    ?.singleValueConstructor()
                val uintConstructor = pluginContext.findClassSymbol(KOTLIN_UINT_CLASS_ID, fromFile)
                    ?.singleValueConstructor()
                val ulongConstructor = pluginContext.findClassSymbol(KOTLIN_ULONG_CLASS_ID, fromFile)
                    ?.singleValueConstructor()
                return WinRTProjectionIntrinsicIrLowerings(
                    hStringCompanion = hStringCompanion,
                    hStringCreateReference = hStringCreateReference,
                    hStringFromHandle = hStringFromHandle,
                    hStringToKString = hStringToKString,
                    hStringClose = hStringClose,
                    referencedHStringHandleGetter = referencedHStringHandleGetter,
                    referencedHStringClose = referencedHStringClose,
                    iWinRTObjectNativeObjectGetter = iWinRTObjectNativeObjectGetter,
                    comObjectReferencePointerGetter = comObjectReferencePointerGetter,
                    rawComPtrValueGetter = rawComPtrValueGetter,
                    rawAddressValueGetter = rawAddressValueGetter,
                    platformAbi = platformAbi,
                    platformAbiConfinedScope = platformAbiConfinedScope,
                    platformAbiFromRawComPtr = platformAbiFromRawComPtr,
                    platformAbiAllocatePointerSlot = platformAbiAllocatePointerSlot,
                    platformAbiAllocateInt8Slot = platformAbiAllocateInt8Slot,
                    platformAbiAllocateInt32Slot = platformAbiAllocateInt32Slot,
                    platformAbiAllocateInt64Slot = platformAbiAllocateInt64Slot,
                    platformAbiAllocateDoubleSlot = platformAbiAllocateDoubleSlot,
                    platformAbiAllocateBytes = platformAbiAllocateBytes,
                    platformAbiReadPointer = platformAbiReadPointer,
                    platformAbiReadPointerAt = platformAbiReadPointerAt,
                    platformAbiReadInt8 = platformAbiReadInt8,
                    platformAbiReadInt16 = platformAbiReadInt16,
                    platformAbiReadInt32 = platformAbiReadInt32,
                    platformAbiReadInt64 = platformAbiReadInt64,
                    platformAbiReadFloat = platformAbiReadFloat,
                    platformAbiReadDouble = platformAbiReadDouble,
                    platformAbiIsNullRawAddress = platformAbiIsNullRawAddress,
                    platformAbiToRawComPtr = platformAbiToRawComPtr,
                    nativeScopeClose = nativeScopeClose,
                    nativeStructAdapterLayoutGetter = nativeStructAdapterLayoutGetter,
                    nativeStructAdapterRead = nativeStructAdapterRead,
                    nativeStructAdapterWrite = nativeStructAdapterWrite,
                    nativeStructAdapterDisposeAbi = nativeStructAdapterDisposeAbi,
                    nativeStructLayoutSizeBytesGetter = nativeStructLayoutSizeBytesGetter,
                    marshalerFromAbiArray = marshalerFromAbiArray,
                    marshalerDisposeAbiArray = marshalerDisposeAbiArray,
                    emptyList = emptyList,
                    winRTObjectMarshaller = winRTObjectMarshaller,
                    winRTObjectMarshallerFromAbi = winRTObjectMarshallerFromAbi,
                    iUnknownReferenceConstructor = iUnknownReferenceConstructor,
                    comObjectReferenceAsInspectable = comObjectReferenceAsInspectable,
                    comObjectReferenceClose = comObjectReferenceClose,
                    function1Invoke = function1Invoke,
                    kotlinError = kotlinError,
                    hResultConstructor = hResultConstructor,
                    hResultRequireSuccess = hResultRequireSuccess,
                    ubyteConstructor = ubyteConstructor,
                    ushortConstructor = ushortConstructor,
                    uintConstructor = uintConstructor,
                    ulongConstructor = ulongConstructor,
                    jvmFfmSymbols = jvmFfmSymbols,
                    nativeCInteropSymbols = nativeCInteropSymbols,
                )
            }

        }

        private class NativeCInteropSymbols private constructor(
            private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
            private val rawAddressValueGetter: IrSimpleFunctionSymbol,
            val cPointer: IrClassSymbol,
            val cFunction: IrClassSymbol,
            val cOpaque: IrClassSymbol,
            val toCPointer: IrSimpleFunctionSymbol,
            private val invokesByArity: Map<Int, IrSimpleFunctionSymbol>,
        ) {
            fun canLower(argumentKinds: List<UnitCallAbiArgumentKind>): Boolean =
                invokeForArity(argumentKinds.size + 1) != null

            fun invokeForArity(arity: Int): IrSimpleFunctionSymbol? = invokesByArity[arity]

            fun opaquePointerType(): IrSimpleType = cPointer.typeWith(cOpaque.owner.defaultType)

            fun opaquePointerNullableType(): IrType = opaquePointerType().makeNullable()

            fun cFunctionPointerType(
                pluginContext: IrPluginContext,
                parameterTypes: List<IrType>,
            ): IrSimpleType {
                val functionClass = pluginContext.findClassSymbol(
                    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function${parameterTypes.size}")),
                ) ?: error("kotlin-winrt compiler plugin requires kotlin.Function${parameterTypes.size} to lower mingw cinterop calls.")
                val functionType = functionClass.typeWith(parameterTypes + pluginContext.irBuiltIns.intType)
                val cFunctionType = cFunction.typeWith(functionType)
                return cPointer.typeWith(cFunctionType)
            }

            fun toOpaquePointer(
                builder: DeclarationIrBuilder,
                value: IrExpression,
            ): IrExpression =
                builder.irCall(toCPointer, opaquePointerNullableType()).apply {
                    typeArguments[0] = cOpaque.owner.defaultType
                    arguments[0] = value
                }

            fun rawComPtrToOpaquePointer(
                builder: DeclarationIrBuilder,
                pointer: IrExpression,
            ): IrExpression =
                toOpaquePointer(
                    builder,
                    builder.irCall(rawComPtrValueGetter).apply {
                        arguments[0] = pointer
                    },
                )

            fun rawAddressToOpaquePointer(
                builder: DeclarationIrBuilder,
                address: IrExpression,
            ): IrExpression =
                toOpaquePointer(
                    builder,
                    builder.irCall(rawAddressValueGetter).apply {
                        arguments[0] = address
                    },
                )

            fun functionPointer(
                builder: DeclarationIrBuilder,
                pluginContext: IrPluginContext,
                address: IrExpression,
                parameterTypes: List<IrType>,
            ): IrExpression {
                val pointerType = cFunctionPointerType(pluginContext, parameterTypes)
                val cFunctionType = pointerType.arguments.single().typeOrNull
                    ?: error("kotlin-winrt compiler plugin could not build mingw CFunction pointer type.")
                return builder.irAs(
                    builder.irCall(toCPointer, pointerType.makeNullable()).apply {
                        typeArguments[0] = cFunctionType
                        arguments[0] = builder.irCall(rawAddressValueGetter).apply {
                            arguments[0] = address
                        }
                    },
                    pointerType,
                )
            }

            fun nativeCarrier(
                builder: DeclarationIrBuilder,
                kind: UnitCallAbiArgumentKind,
                value: IrExpression,
            ): IrExpression =
                when (kind) {
                    UnitCallAbiArgumentKind.RawComPtr -> rawComPtrToOpaquePointer(builder, value)
                    UnitCallAbiArgumentKind.RawAddress,
                    UnitCallAbiArgumentKind.StructPointer,
                    UnitCallAbiArgumentKind.String,
                    UnitCallAbiArgumentKind.Object -> rawAddressToOpaquePointer(builder, value)
                    UnitCallAbiArgumentKind.Byte,
                    UnitCallAbiArgumentKind.Int16,
                    UnitCallAbiArgumentKind.Int32,
                    UnitCallAbiArgumentKind.UInt32,
                    UnitCallAbiArgumentKind.Int64,
                    UnitCallAbiArgumentKind.UInt64,
                    UnitCallAbiArgumentKind.Float,
                    UnitCallAbiArgumentKind.Double,
                    UnitCallAbiArgumentKind.Boolean,
                    UnitCallAbiArgumentKind.Struct1,
                    UnitCallAbiArgumentKind.Struct2,
                    UnitCallAbiArgumentKind.Struct4,
                    UnitCallAbiArgumentKind.Struct8 -> value
                }

            companion object {
                fun create(
                    pluginContext: IrPluginContext,
                    fromFile: IrFile?,
                    rawComPtrValueGetter: IrSimpleFunctionSymbol,
                    rawAddressValueGetter: IrSimpleFunctionSymbol,
                ): NativeCInteropSymbols? {
                    fun missing(): Nothing? = null
                    val cPointer = pluginContext.findClassSymbol(KOTLINX_CINTEROP_CPOINTER_CLASS_ID, fromFile)
                        ?: return missing()
                    val cFunction = pluginContext.findClassSymbol(KOTLINX_CINTEROP_CFUNCTION_CLASS_ID, fromFile)
                        ?: return missing()
                    val cOpaque = pluginContext.findClassSymbol(KOTLINX_CINTEROP_COPAQUE_CLASS_ID, fromFile)
                        ?: return missing()
                    val toCPointer = pluginContext.findFunctionSymbols(
                        CallableId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("toCPointer")),
                        fromFile,
                    ).firstOrNull() ?: return missing()
                    val invokesByArity = pluginContext.findFunctionSymbols(
                        CallableId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("invoke")),
                        fromFile,
                    ).mapNotNull { symbol ->
                        val arity = symbol.owner.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular }
                        arity to symbol
                    }.toMap()
                    if (invokesByArity.isEmpty()) {
                        return missing()
                    }
                    return NativeCInteropSymbols(
                        rawComPtrValueGetter = rawComPtrValueGetter,
                        rawAddressValueGetter = rawAddressValueGetter,
                        cPointer = cPointer,
                        cFunction = cFunction,
                        cOpaque = cOpaque,
                        toCPointer = toCPointer,
                        invokesByArity = invokesByArity,
                    )
                }
            }
        }

        private class JvmFfmSymbols private constructor(
            private val memorySegmentType: org.jetbrains.kotlin.ir.types.IrType,
            private val memorySegmentOfAddress: IrSimpleFunctionSymbol,
            private val memorySegmentReinterpret: IrSimpleFunctionSymbol,
            private val memorySegmentGetAddress: IrSimpleFunctionSymbol,
            private val memorySegmentGetAtIndexAddress: IrSimpleFunctionSymbol,
            val methodHandleInvoke: IrSimpleFunctionSymbol,
            private val valueLayoutAddress: JvmStaticLayoutValue,
            private val intToLong: IrSimpleFunctionSymbol,
            private val uintToInt: IrSimpleFunctionSymbol?,
            private val ulongToLong: IrSimpleFunctionSymbol?,
            private val rawComPtrValueGetter: IrSimpleFunctionSymbol,
            private val rawAddressValueGetter: IrSimpleFunctionSymbol,
            private val winRTJvmFfmDowncallHandles: IrClassSymbol,
            private val winRTJvmFfmDowncallHandlesHResult: IrSimpleFunctionSymbol,
        ) {
            fun segmentFromRawComPtr(
                builder: DeclarationIrBuilder,
                pointer: IrExpression,
            ): IrExpression =
                builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = builder.irCall(memorySegmentOfAddress).apply {
                        arguments[0] = builder.irCall(rawComPtrValueGetter).apply {
                            arguments[0] = pointer
                        }
                    }
                    arguments[1] = builder.irLong(Long.MAX_VALUE)
                }

            fun segmentFromRawAddress(
                builder: DeclarationIrBuilder,
                address: IrExpression,
            ): IrExpression =
                builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = builder.irCall(memorySegmentOfAddress).apply {
                        arguments[0] = builder.irCall(rawAddressValueGetter).apply {
                            arguments[0] = address
                        }
                    }
                    arguments[1] = builder.irLong(Long.MAX_VALUE)
                }

            private fun List<UnitCallAbiArgumentKind>.jvmFfmAbiShape(): String =
                joinToString(",") { kind -> kind.jvmFfmAbiToken() }

            private fun UnitCallAbiArgumentKind.jvmFfmAbiToken(): String =
                when (this) {
                    UnitCallAbiArgumentKind.RawAddress -> "RawAddress"
                    UnitCallAbiArgumentKind.RawComPtr -> "RawComPtr"
                    UnitCallAbiArgumentKind.Byte -> "Byte"
                    UnitCallAbiArgumentKind.Int16 -> "Int16"
                    UnitCallAbiArgumentKind.Int32 -> "Int32"
                    UnitCallAbiArgumentKind.UInt32 -> "UInt32"
                    UnitCallAbiArgumentKind.Int64 -> "Int64"
                    UnitCallAbiArgumentKind.UInt64 -> "UInt64"
                    UnitCallAbiArgumentKind.Float -> "Float"
                    UnitCallAbiArgumentKind.Double -> "Double"
                    UnitCallAbiArgumentKind.Boolean -> "Boolean"
                    UnitCallAbiArgumentKind.String -> "String"
                    UnitCallAbiArgumentKind.Struct1,
                    UnitCallAbiArgumentKind.Struct2,
                    UnitCallAbiArgumentKind.Struct4,
                    UnitCallAbiArgumentKind.Struct8,
                    UnitCallAbiArgumentKind.StructPointer -> "Struct"
                    UnitCallAbiArgumentKind.Object -> "Object"
                }

            fun downcallHandle(
                builder: DeclarationIrBuilder,
                argumentKinds: List<UnitCallAbiArgumentKind>,
            ): IrExpression =
                downcallHandle(builder, argumentKinds.jvmFfmAbiShape())

            fun downcallHandle(
                builder: DeclarationIrBuilder,
                abiShape: String,
            ): IrExpression =
                builder.irCall(winRTJvmFfmDowncallHandlesHResult).apply {
                    arguments[0] = builder.irGetObject(winRTJvmFfmDowncallHandles)
                    arguments[1] = builder.irString(abiShape)
                }

            fun canLower(argumentKinds: List<UnitCallAbiArgumentKind>): Boolean =
                argumentKinds.all { kind ->
                    when (kind) {
                        UnitCallAbiArgumentKind.UInt32 -> uintToInt != null
                        UnitCallAbiArgumentKind.UInt64 -> ulongToLong != null
                        else -> true
                    }
                }

            fun vtableEntry(
                builder: DeclarationIrBuilder,
                instanceSegment: IrExpression,
                slot: IrExpression,
            ): IrExpression {
                val objectMemory = builder.irCall(memorySegmentReinterpret).apply {
                    arguments[0] = instanceSegment
                    arguments[1] = builder.irLong(8L)
                }
                val vtable = builder.irCall(memorySegmentGetAddress).apply {
                    arguments[0] = objectMemory
                    arguments[1] = valueLayoutAddress.get(builder)
                    arguments[2] = builder.irLong(0L)
                }
                return builder.irCall(memorySegmentGetAtIndexAddress).apply {
                    arguments[0] = builder.irCall(memorySegmentReinterpret).apply {
                        arguments[0] = vtable
                        arguments[1] = builder.irLong(Long.MAX_VALUE)
                    }
                    arguments[1] = valueLayoutAddress.get(builder)
                    arguments[2] = builder.irCall(intToLong).apply {
                        arguments[0] = slot
                    }
                }
            }

            fun jvmCarrier(
                builder: DeclarationIrBuilder,
                kind: UnitCallAbiArgumentKind,
                value: IrExpression,
            ): IrExpression =
                when (kind) {
                    UnitCallAbiArgumentKind.Byte,
                    UnitCallAbiArgumentKind.Int16,
                    UnitCallAbiArgumentKind.Int32,
                    UnitCallAbiArgumentKind.Int64,
                    UnitCallAbiArgumentKind.Float,
                    UnitCallAbiArgumentKind.Double,
                    UnitCallAbiArgumentKind.Boolean -> value
                    UnitCallAbiArgumentKind.UInt32 -> builder.irCall(requireNotNull(uintToInt)).apply {
                        arguments[0] = value
                    }
                    UnitCallAbiArgumentKind.UInt64 -> builder.irCall(requireNotNull(ulongToLong)).apply {
                        arguments[0] = value
                    }
                    UnitCallAbiArgumentKind.RawComPtr -> segmentFromRawComPtr(builder, value)
                    UnitCallAbiArgumentKind.RawAddress,
                    UnitCallAbiArgumentKind.Struct1,
                    UnitCallAbiArgumentKind.Struct2,
                    UnitCallAbiArgumentKind.Struct4,
                    UnitCallAbiArgumentKind.Struct8,
                    UnitCallAbiArgumentKind.StructPointer,
                    UnitCallAbiArgumentKind.String,
                    UnitCallAbiArgumentKind.Object -> segmentFromRawAddress(builder, value)
                }

            fun invokeHResultDowncall(
                builder: DeclarationIrBuilder,
                pluginContext: IrPluginContext,
                handle: IrExpression,
                arguments: List<IrExpression>,
            ): IrExpression {
                val original = methodHandleInvoke.owner
                val receiverParameters = original.parameters.filter { parameter ->
                    parameter.kind != IrParameterKind.Regular
                }
                val instantiated = pluginContext.irFactory.buildFun {
                    updateFrom(original)
                    name = original.name
                    origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
                    returnType = pluginContext.irBuiltIns.intType
                }.apply {
                    parent = original.parent
                    parameters = receiverParameters + arguments.mapIndexed { index, argument ->
                        pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                            name = Name.identifier("\$$index")
                            type = argument.type
                            origin = JvmLoweredDeclarationOrigin.POLYMORPHIC_SIGNATURE_INSTANTIATION
                            kind = IrParameterKind.Regular
                        }, this)
                    }
                }
                return builder.irCall(instantiated.symbol, pluginContext.irBuiltIns.intType).apply {
                    this.arguments[0] = handle
                    arguments.forEachIndexed { index, argument ->
                        this.arguments[index + 1] = argument
                    }
                }
            }

            private class JvmStaticLayoutValue(
                private val field: IrField?,
                private val getter: IrSimpleFunctionSymbol?,
            ) {
                fun get(builder: DeclarationIrBuilder): IrExpression =
                    field?.let { builder.irGetField(null, it) }
                        ?: builder.irCall(requireNotNull(getter))
            }

            companion object {
                fun create(
                    pluginContext: IrPluginContext,
                    fromFile: IrFile?,
                    rawComPtrValueGetter: IrSimpleFunctionSymbol,
                    rawAddressValueGetter: IrSimpleFunctionSymbol,
                ): JvmFfmSymbols? {
                    fun missing(): Nothing? = null
                    val memorySegment = pluginContext.findClassSymbol(JAVA_MEMORY_SEGMENT_CLASS_ID, fromFile) ?: return missing()
                    val methodHandle = pluginContext.findClassSymbol(JAVA_METHOD_HANDLE_CLASS_ID, fromFile) ?: return missing()
                    val valueLayout = pluginContext.findClassSymbol(JAVA_VALUE_LAYOUT_CLASS_ID, fromFile) ?: return missing()
                    val addressLayout = pluginContext.findClassSymbol(JAVA_ADDRESS_LAYOUT_CLASS_ID, fromFile) ?: return missing()
                    val winRTJvmFfmDowncallHandles =
                        pluginContext.findClassSymbol(WINRT_JVM_FFM_DOWNCALL_HANDLES_CLASS_ID, fromFile) ?: return missing()
                    val winRTJvmFfmDowncallHandlesHResult =
                        winRTJvmFfmDowncallHandles.functionNamed("hResult") ?: return missing()
                    val uint = pluginContext.findClassSymbol(KOTLIN_UINT_CLASS_ID, fromFile)
                    val ulong = pluginContext.findClassSymbol(KOTLIN_ULONG_CLASS_ID, fromFile)
                    fun staticLayoutValue(classId: ClassId, owner: IrClassSymbol, name: String): JvmStaticLayoutValue? {
                        val property = owner.owner.declarations
                            .filterIsInstance<IrProperty>()
                            .singleOrNull { it.name.asString() == name }
                        val field = owner.fieldNamed(name) ?: property?.backingField
                        val getter = property?.getter?.symbol
                            ?: pluginContext.findPropertySymbols(CallableId(classId, Name.identifier(name)), fromFile)
                                .singleOrNull()
                                ?.owner
                                ?.getter
                                ?.symbol
                        return if (field != null || getter != null) {
                            JvmStaticLayoutValue(field, getter)
                        } else {
                            null
                        }
                    }
                    fun valueLayout(name: String): JvmStaticLayoutValue? =
                        staticLayoutValue(JAVA_VALUE_LAYOUT_CLASS_ID, valueLayout, name)
                            ?: staticLayoutValue(JAVA_ADDRESS_LAYOUT_CLASS_ID, addressLayout, name)
                    return JvmFfmSymbols(
                        memorySegmentType = memorySegment.owner.defaultType,
                        memorySegmentOfAddress = memorySegment.functionNamedWithValueParameterTypes("ofAddress", KOTLIN_LONG_FQ_NAME)
                            ?: return missing(),
                        memorySegmentReinterpret = memorySegment.functionNamedWithValueParameterTypes("reinterpret", KOTLIN_LONG_FQ_NAME)
                            ?: return missing(),
                        memorySegmentGetAddress = memorySegment.functionNamedWithValueParameterTypes(
                            "get",
                            JAVA_ADDRESS_LAYOUT_FQ_NAME,
                            KOTLIN_LONG_FQ_NAME,
                        ) ?: return missing(),
                        memorySegmentGetAtIndexAddress = memorySegment.functionNamedWithValueParameterTypes(
                            "getAtIndex",
                            JAVA_ADDRESS_LAYOUT_FQ_NAME,
                            KOTLIN_LONG_FQ_NAME,
                        ) ?: return missing(),
                        methodHandleInvoke = methodHandle.functionNamed("invoke") ?: return missing(),
                        valueLayoutAddress = valueLayout("ADDRESS")
                            ?: return missing(),
                        intToLong = pluginContext.irBuiltIns.intClass.owner.declarations
                            .filterIsInstance<IrSimpleFunction>()
                            .singleOrNull { function -> function.name.asString() == "toLong" }
                            ?.symbol
                            ?: return missing(),
                        uintToInt = uint?.functionNamedWithRegularParameterCount("toInt", 0),
                        ulongToLong = ulong?.functionNamedWithRegularParameterCount("toLong", 0),
                        rawComPtrValueGetter = rawComPtrValueGetter,
                        rawAddressValueGetter = rawAddressValueGetter,
                        winRTJvmFfmDowncallHandles = winRTJvmFfmDowncallHandles,
                        winRTJvmFfmDowncallHandlesHResult = winRTJvmFfmDowncallHandlesHResult,
                    )
                }
            }
        }
    }

    private fun readCompilerSupportManifest(): List<KotlinWinRTCompilerSupportManifestEntry> {
        return readCompilerSupportManifestIfConfigured(compilerSupportManifestPath)
    }

    private fun writeCompilerSupportClasses(
        entries: List<KotlinWinRTCompilerSupportManifestEntry>,
        projectionRegistrarEntries: List<KotlinWinRTProjectionRegistrarEntry>,
        projectionSupportOwnerIdentity: String,
    ) {
        val outputDirectory = compilerSupportClassOutputDirectoryPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        Files.deleteIfExists(outputDirectory.resolve(STALE_EVENT_PROJECTION_REGISTRY_CLASS_PATH))
        writeCompilerSupportManifestClass(entries, outputDirectory)
        writeProjectionSupportInitializerClass(
            entries = projectionRegistrarEntries,
            outputDirectory = outputDirectory,
            ownerIdentity = projectionSupportOwnerIdentity,
        )
    }

    private fun readProjectionRegistrarEntries(
        manifestEntries: List<KotlinWinRTCompilerSupportManifestEntry>,
    ): List<KotlinWinRTProjectionRegistrarEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "projection-registrar",
            description = "projection registrar input",
            read = ::readProjectionRegistrarEntries,
        )
    }

    private fun readGenericTypeInstantiationEntries(
        manifestEntries: List<KotlinWinRTCompilerSupportManifestEntry>,
    ): List<KotlinWinRTGenericTypeInstantiationEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "generic-type-instantiation",
            description = "generic type instantiation input",
            read = ::readGenericTypeInstantiationEntries,
        )
    }

    private fun readGenericAbiRegistryEntries(
        manifestEntries: List<KotlinWinRTCompilerSupportManifestEntry>,
    ): List<KotlinWinRTGenericAbiRegistryEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "generic-abi-registry",
            description = "generic ABI registry input",
            read = ::readGenericAbiRegistryEntries,
        )
    }

    private fun readAuthoringTypeDetailsRegistrarEntries(
        manifestEntries: List<KotlinWinRTCompilerSupportManifestEntry>,
    ): List<KotlinWinRTAuthoringTypeDetailsRegistrarEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        return readCompilerSupportInputEntries(
            manifestPath = manifestPath,
            manifestEntries = manifestEntries,
            kind = "authoring-type-details-registrar",
            description = "authoring type-details registrar input",
            read = ::readAuthoringTypeDetailsRegistrarEntries,
        )
    }

    private fun writeProjectionTypeIndex(
        classContexts: List<AuthoredIrClassContext>,
        winRTTypes: Map<String, IndexedWinRTType>,
    ) {
        val outputPath = typeIndexOutputPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        val records = classContexts
            .mapNotNull { context -> context.klass.fqNameWhenAvailable?.asString() }
            .mapNotNull { sourceTypeName -> projectionTypeIndexRecordForSourceType(sourceTypeName, winRTTypes) }
            .distinctBy(KotlinWinRTProjectionTypeIndexRecord::sourceTypeName)
            .sortedBy(KotlinWinRTProjectionTypeIndexRecord::sourceTypeName)
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            records.joinToString(separator = "\n", postfix = if (records.isEmpty()) "" else "\n") { it.render() },
        )
    }

    private fun writeAuthoredCandidates(
        classContexts: List<AuthoredIrClassContext>,
        winRTTypes: Map<String, IndexedWinRTType>,
    ) {
        writeAuthoredCandidates(authoredCandidates(classContexts, winRTTypes))
    }

    private fun authoredCandidates(
        classContexts: List<AuthoredIrClassContext>,
        winRTTypes: Map<String, IndexedWinRTType>,
        sourceSubtypedNames: Set<String> = sourceSubtypedNames(classContexts),
    ): List<KotlinWinRTAuthoredTypeCandidate> =
        classContexts
            .asSequence()
            .filter(::isEffectivelyAuthorable)
            .mapNotNull { context -> authoredTypeFor(context.klass, winRTTypes, isEffectivelyPublic(context), sourceSubtypedNames) }
            .distinctBy(KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
            .sortedBy(KotlinWinRTAuthoredTypeCandidate::sourceTypeName)
            .toList()

    private fun writeAuthoredCandidates(
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
    ) {
        val outputPath = authoredCandidatesOutputPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        KotlinWinRTAuthoringCandidateFile.write(outputPath, candidates)
    }

    private fun writeAuthoredSupportArtifacts(
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
    ) {
        val exportedCandidates = candidates.filter(KotlinWinRTAuthoredTypeCandidate::isPublic)
        authoredMetadataOutputPath
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let { outputPath ->
                KotlinWinRTAuthoringMetadataModel.writeDescriptor(
                    candidates = exportedCandidates,
                    outputFile = outputPath,
                )
            }
        val assemblyName = authoringAssemblyName?.takeIf(String::isNotBlank) ?: return
        authoredWinmdOutputPath
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let { outputPath ->
                KotlinWinRTAuthoringMetadataModel.writeWinmd(
                    assemblyName = assemblyName,
                    candidates = exportedCandidates,
                    outputFile = outputPath,
                )
            }
        authoredHostManifestOutputPath
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
            ?.let { outputPath ->
                KotlinWinRTAuthoringMetadataModel.writeHostManifest(
                    assemblyName = assemblyName,
                    targetArtifactName = authoringTargetArtifactName?.takeIf(String::isNotBlank) ?: "$assemblyName.jar",
                    hostExportsClassName = winRTAuthoringHostExportsClassName(
                        authoringTargetArtifactName?.takeIf(String::isNotBlank) ?: "$assemblyName.jar",
                    ),
                    candidates = exportedCandidates,
                    outputFile = outputPath,
                )
            }
    }

    private fun authoredTypeFor(
        klass: IrClass,
        winRTTypes: Map<String, IndexedWinRTType>,
        isPublic: Boolean = true,
        sourceSubtypedNames: Set<String> = emptySet(),
    ): KotlinWinRTAuthoredTypeCandidate? {
        val sourceTypeName = klass.fqNameWhenAvailable?.asString() ?: return null
        if (sourceTypeName in sourceSubtypedNames && klass.modality != Modality.FINAL) {
            return null
        }
        val projectedMetadataName = projectionPackageToMetadataName(sourceTypeName)
        if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX) ||
            (projectedMetadataName != sourceTypeName && projectedMetadataName in winRTTypes)
        ) {
            return null
        }
        val annotation = authoredRuntimeClassAnnotation(klass, winRTTypes)
        val inheritedWinRTTypes = inheritedWinRTTypes(klass, winRTTypes)
        val resolvedWinRTTypes = annotation.resolvedTypes + inheritedWinRTTypes
        if (resolvedWinRTTypes.isEmpty()) {
            return null
        }
        val packageName = sourceTypeName.substringBeforeLast('.', missingDelimiterValue = "")
        val className = sourceTypeName.substringAfterLast('.')
        val winRTBase = resolvedWinRTTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
        val directInterfaces = resolvedWinRTTypes
            .filter { type -> type.kind == "Interface" }
            .map { type -> type.qualifiedName }
        val overridableInterfaces = (annotation.overridableInterfaceNames + inheritedOverridableInterfaceNames(winRTBase, winRTTypes))
            .distinct()
            .sorted()
        return KotlinWinRTAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            winRTBaseClassName = winRTBase?.qualifiedName,
            winRTInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces,
            isPublic = isPublic,
            activatableFactoryInterfaceName = annotation.activatableFactoryInterfaceName,
            staticFactoryInterfaceNames = annotation.staticFactoryInterfaceNames,
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun sourceSubtypedNames(classContexts: List<AuthoredIrClassContext>): Set<String> {
        val sourceClassNames = classContexts
            .mapNotNullTo(mutableSetOf()) { context -> context.klass.fqNameWhenAvailable?.asString() }
        return classContexts
            .asSequence()
            .flatMap { context -> context.klass.superTypes.asSequence() }
            .mapNotNull { type -> type.classOrNull?.owner?.fqNameWhenAvailable?.asString() }
            .filterTo(mutableSetOf()) { typeName -> typeName in sourceClassNames }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun inheritedWinRTTypes(
        klass: IrClass,
        winRTTypes: Map<String, IndexedWinRTType>,
        visitedSourceTypes: MutableSet<String> = mutableSetOf(),
    ): List<IndexedWinRTType> =
        klass.superTypes.flatMap { type ->
            val superTypeName = type.classFqName?.asString() ?: return@flatMap emptyList()
            winRTTypes[projectionPackageToMetadataName(superTypeName)]?.let { return@flatMap listOf(it) }
            val superClass = type.classOrNull?.owner ?: return@flatMap emptyList()
            val sourceTypeName = superClass.fqNameWhenAvailable?.asString() ?: return@flatMap emptyList()
            if (!visitedSourceTypes.add(sourceTypeName)) {
                return@flatMap emptyList()
            }
            inheritedWinRTTypes(superClass, winRTTypes, visitedSourceTypes)
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun authoredRuntimeClassAnnotation(
        klass: IrClass,
        winRTTypes: Map<String, IndexedWinRTType>,
    ): ResolvedAuthoredRuntimeClassAnnotation {
        val annotation = klass.annotations.firstOrNull { call ->
            call.symbol.owner.parentClassOrNull?.fqNameWhenAvailable?.asString() == WINRT_AUTHORED_RUNTIME_CLASS_ANNOTATION
        } ?: return ResolvedAuthoredRuntimeClassAnnotation.Empty
        val baseClassName = annotation.arguments.getOrNull(0).stringConstantValue()
        val interfaceNames = annotation.arguments.getOrNull(1).stringArrayConstantValue()
        val overridableInterfaceNames = annotation.arguments.getOrNull(2).stringArrayConstantValue()
        val activatableFactoryInterfaceName = annotation.arguments.getOrNull(3).stringConstantValue()
        val staticFactoryInterfaceNames = annotation.arguments.getOrNull(4).stringArrayConstantValue()
        val resolvedBase = baseClassName
            .takeIf(String::isNotBlank)
            ?.let(::projectionPackageToMetadataName)
            ?.let { typeName ->
                requireNotNull(winRTTypes[typeName]) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $baseClassName."
                }.also { type ->
                    require(type.kind == "RuntimeClass") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation baseClassName must reference a WinRT runtime class: $baseClassName."
                    }
                }
            }
        val resolvedInterfaces = interfaceNames
            .map { typeName ->
                val metadataName = projectionPackageToMetadataName(typeName)
                requireNotNull(winRTTypes[metadataName]) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation interfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
        val resolvedOverridableInterfaces = overridableInterfaceNames
            .map { typeName ->
                val metadataName = projectionPackageToMetadataName(typeName)
                requireNotNull(winRTTypes[metadataName]) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation overridableInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRTType::qualifiedName)
        val resolvedActivatableFactoryInterface = activatableFactoryInterfaceName
            .takeIf(String::isNotBlank)
            ?.let { typeName ->
                val metadataName = projectionPackageToMetadataName(typeName)
                requireNotNull(winRTTypes[metadataName]) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation activatableFactoryInterfaceName must reference a WinRT interface: $typeName."
                    }
                }.qualifiedName
            }
        val resolvedStaticFactoryInterfaces = staticFactoryInterfaceNames
            .map { typeName ->
                val metadataName = projectionPackageToMetadataName(typeName)
                requireNotNull(winRTTypes[metadataName]) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation staticFactoryInterfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
            .map(IndexedWinRTType::qualifiedName)
        return ResolvedAuthoredRuntimeClassAnnotation(
            resolvedTypes = listOfNotNull(resolvedBase) + resolvedInterfaces,
            overridableInterfaceNames = resolvedOverridableInterfaces,
            activatableFactoryInterfaceName = resolvedActivatableFactoryInterface,
            staticFactoryInterfaceNames = resolvedStaticFactoryInterfaces.distinct().sorted(),
        )
    }

    private data class ResolvedAuthoredRuntimeClassAnnotation(
        val resolvedTypes: List<IndexedWinRTType>,
        val overridableInterfaceNames: List<String>,
        val activatableFactoryInterfaceName: String?,
        val staticFactoryInterfaceNames: List<String>,
    ) {
        companion object {
            val Empty = ResolvedAuthoredRuntimeClassAnnotation(emptyList(), emptyList(), null, emptyList())
        }
    }

    private fun IrExpression?.stringArrayConstantValue(): List<String> {
        val vararg = this as? IrVararg ?: return emptyList()
        return vararg.elements.mapNotNull { element -> (element as? IrConst)?.value as? String }
    }

    private fun IrExpression?.stringConstantValue(): String =
        (this as? IrConst)?.value as? String ?: ""

    private fun validateAuthoredType(
        klass: IrClass,
        authoredType: KotlinWinRTAuthoredTypeCandidate,
        afterK2: Boolean,
        report: (String) -> Unit,
    ) {
        if (!afterK2) {
            report("kotlin-winrt authoring requires K2 semantic analysis for ${authoredType.sourceTypeName}.")
        }
        if (klass.kind != ClassKind.CLASS) {
            report("WinRT authored type ${authoredType.sourceTypeName} must be a concrete Kotlin class.")
        }
        if (klass.isValue) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be a Kotlin value class.")
        }
        if (authoredType.isPublic && !klass.hasPublicDefaultActivationConstructor()) {
            report(
                "Public WinRT authored type ${authoredType.sourceTypeName} must declare an accessible zero-argument constructor for default activation.",
            )
        }
        if (klass.isInner) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be an inner class.")
        }
        if (klass.parentClassOrNull != null) {
            report(
                "WinRT authored type ${authoredType.sourceTypeName} must be a top-level Kotlin type; " +
                    "nested authored runtime classes are not supported.",
            )
        }
        if (klass.typeParameters.isNotEmpty()) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be generic.")
        }
        if (klass.kind == ClassKind.CLASS && klass.modality != Modality.FINAL) {
            report("WinRT authored class ${authoredType.sourceTypeName} must be final.")
        }
        validateAuthoredConstructors(klass, authoredType, report)
        validateAuthoredMemberTypes(klass, authoredType, report)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun validateAuthoredConstructors(
        klass: IrClass,
        authoredType: KotlinWinRTAuthoredTypeCandidate,
        report: (String) -> Unit,
    ) {
        val publicConstructorArities = mutableSetOf<Int>()
        klass.declarations
            .filterIsInstance<IrConstructor>()
            .filter { constructor -> constructor.visibility == DescriptorVisibilities.PUBLIC }
            .forEach { constructor ->
                val arity = constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular }
                if (!publicConstructorArities.add(arity)) {
                    report(
                        "WinRT authored type ${authoredType.sourceTypeName} must not declare multiple public constructors with $arity parameter(s).",
                    )
                }
            }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun validateAuthoredMemberTypes(
        klass: IrClass,
        authoredType: KotlinWinRTAuthoredTypeCandidate,
        report: (String) -> Unit,
    ) {
        val publicFunctions = klass.declarations.filterIsInstance<IrSimpleFunction>()
            .filter { function ->
                function.visibility == DescriptorVisibilities.PUBLIC &&
                    function.origin != IrDeclarationOrigin.FAKE_OVERRIDE &&
                    function.name.asString() !in authoredMemberValidationSyntheticFunctionNames
            }
        publicFunctions
            .groupBy { function -> function.name.asString() }
            .filterValues { overloads -> overloads.size > 1 }
            .keys
            .forEach { memberName ->
                report(
                    "WinRT authored member ${authoredType.sourceTypeName}.$memberName must not be overloaded until DefaultOverload metadata is supported.",
                )
            }
        publicFunctions
            .forEach { function ->
                if (function.isSuspend) {
                    report(
                        "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} must not be suspend; expose WinRT async interfaces explicitly.",
                    )
                }
                if (function.isOperator) {
                    report(
                        "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} must not overload Kotlin operators.",
                    )
                }
                if (function.typeParameters.isNotEmpty()) {
                    report(
                        "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} must not be generic.",
                    )
                }
                validateAuthoredExposedType(
                    type = function.returnType,
                    authoredType = authoredType,
                    memberName = function.name.asString(),
                    role = "return type",
                    report = report,
                )
                function.parameters
                    .filter { parameter -> parameter.kind == IrParameterKind.Regular }
                    .forEach { parameter ->
                        if (parameter.name.asString() == authoredReturnValueParameterName) {
                            report(
                                "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} parameter '${parameter.name.asString()}' " +
                                    "must not use the generated return-value parameter name.",
                            )
                        }
                        if (parameter.varargElementType != null) {
                            report(
                                "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} parameter '${parameter.name.asString()}' " +
                                    "must not be vararg.",
                            )
                        }
                        if (parameter.defaultValue != null) {
                            report(
                                "WinRT authored member ${authoredType.sourceTypeName}.${function.name.asString()} parameter '${parameter.name.asString()}' " +
                                    "must not declare a Kotlin default value.",
                            )
                        }
                        validateAuthoredExposedType(
                            type = parameter.type,
                            authoredType = authoredType,
                            memberName = function.name.asString(),
                            role = "parameter '${parameter.name.asString()}'",
                            report = report,
                        )
                    }
            }
        klass.declarations.filterIsInstance<IrProperty>()
            .filter { property ->
                property.visibility == DescriptorVisibilities.PUBLIC &&
                    property.origin != IrDeclarationOrigin.FAKE_OVERRIDE
            }
            .forEach { property ->
                val propertyType = property.getter?.returnType ?: property.backingField?.type ?: return@forEach
                validateAuthoredExposedType(
                    type = propertyType,
                    authoredType = authoredType,
                    memberName = property.name.asString(),
                    role = "property type",
                    report = report,
                )
            }
    }

    private fun validateAuthoredExposedType(
        type: IrType,
        authoredType: KotlinWinRTAuthoredTypeCandidate,
        memberName: String,
        role: String,
        report: (String) -> Unit,
    ) {
        val typeName = type.classFqName?.asString()
        if (typeName == "kotlin.Unit" && role != "return type") {
            report(
                "WinRT authored member ${authoredType.sourceTypeName}.$memberName $role must not expose kotlin.Unit; Unit is only valid as a void return.",
            )
        }
        if (typeName in unsupportedAuthoredExposedTypeNames) {
            report(
                "WinRT authored member ${authoredType.sourceTypeName}.$memberName $role must not expose unsupported type $typeName.",
            )
        }
        if (typeName?.startsWith("kotlin.Function") == true) {
            report(
                "WinRT authored member ${authoredType.sourceTypeName}.$memberName $role must not expose Kotlin function type $typeName; " +
                    "use a projected WinRT delegate type instead.",
            )
        }
        (type as? IrSimpleType)?.arguments
            ?.mapNotNull { argument -> argument.typeOrNull }
            ?.forEach { argumentType ->
                validateAuthoredExposedType(
                    type = argumentType,
                    authoredType = authoredType,
                    memberName = memberName,
                    role = "$role generic argument",
                    report = report,
                )
            }
        if (!type.isKotlinArrayType()) {
            return
        }
        val elementType = type.arrayElementType() ?: return
        if (elementType.isKotlinArrayType()) {
            report(
                "WinRT authored member ${authoredType.sourceTypeName}.$memberName $role must not expose jagged arrays; " +
                    "Windows Runtime arrays are one-dimensional.",
            )
        }
    }

    private fun IrType.arrayElementType(): IrType? =
        (this as? IrSimpleType)?.arguments?.singleOrNull()?.typeOrNull

    private fun IrType.isKotlinArrayType(): Boolean =
        classFqName?.asString() in kotlinArrayTypeNames

    private val kotlinArrayTypeNames = setOf(
        "kotlin.Array",
        "kotlin.BooleanArray",
        "kotlin.ByteArray",
        "kotlin.CharArray",
        "kotlin.DoubleArray",
        "kotlin.FloatArray",
        "kotlin.IntArray",
        "kotlin.LongArray",
        "kotlin.ShortArray",
        "kotlin.UByteArray",
        "kotlin.UIntArray",
        "kotlin.ULongArray",
        "kotlin.UShortArray",
    )

    private val unsupportedAuthoredExposedTypeNames = setOf(
        "kotlin.Nothing",
        "kotlin.Throwable",
        "kotlin.Exception",
        "java.lang.Throwable",
        "java.lang.Exception",
    )

    private val authoredMemberValidationSyntheticFunctionNames = setOf(
        "equals",
        "hashCode",
        "toString",
    )

    private val authoredReturnValueParameterName = "__retval"

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun classContextsIn(
        declaration: IrDeclaration,
        containingTypesPublic: Boolean = true,
        containingTypesAuthorable: Boolean = true,
    ): List<AuthoredIrClassContext> =
        when (declaration) {
            is IrClass -> {
                val nestedContainingTypesPublic = containingTypesPublic && declaration.visibility == DescriptorVisibilities.PUBLIC
                val nestedContainingTypesAuthorable = containingTypesAuthorable && isAuthorableVisibility(declaration.visibility)
                listOf(AuthoredIrClassContext(declaration, containingTypesPublic, containingTypesAuthorable)) +
                    declaration.declarations.flatMap { child ->
                        classContextsIn(child, nestedContainingTypesPublic, nestedContainingTypesAuthorable)
                    }
            }
            else -> emptyList()
        }

    private data class AuthoredIrClassContext(
        val klass: IrClass,
        val containingTypesPublic: Boolean,
        val containingTypesAuthorable: Boolean,
    )

    private fun isEffectivelyPublic(context: AuthoredIrClassContext): Boolean =
        context.containingTypesPublic && context.klass.visibility == DescriptorVisibilities.PUBLIC

    private fun isEffectivelyAuthorable(context: AuthoredIrClassContext): Boolean =
        context.containingTypesAuthorable && isAuthorableVisibility(context.klass.visibility)

    private fun isAuthorableVisibility(visibility: org.jetbrains.kotlin.descriptors.DescriptorVisibility): Boolean =
        visibility == DescriptorVisibilities.PUBLIC || visibility == DescriptorVisibilities.INTERNAL

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrClass.hasPublicDefaultActivationConstructor(): Boolean =
        declarations
            .filterIsInstance<IrConstructor>()
            .any { constructor ->
                constructor.visibility == DescriptorVisibilities.PUBLIC &&
                    constructor.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
            }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addProjectionSupportInitializerFunction(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRTProjectionRegistrarEntry>,
        ownerIdentity: String,
    ): IrSimpleFunctionSymbol? {
        if (entries.isEmpty()) {
            return null
        }
        val lookupFile = requireCompilerSupportPrerequisite(
            description = "projection registrar",
            prerequisite = "module file",
            value = moduleFragment.files.firstOrNull(),
        )
        val file = moduleFragment.projectionSupportAnchorFile(ownerIdentity) ?: lookupFile
        val registerGeneratedProjectionTypeIndex = requireCompilerSupportPrerequisite(
            description = "projection registrar",
            prerequisite = "io.github.composefluent.winrt.runtime.registerGeneratedProjectionTypeIndex with 5 regular parameters",
            value = pluginContext.findFunctionSymbols(
                CallableId(
                    FqName("io.github.composefluent.winrt.runtime"),
                    Name.identifier("registerGeneratedProjectionTypeIndex"),
                ),
                lookupFile,
            )
                .map { symbol -> symbol.owner }
                .singleOrNull { function ->
                    function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 5
                }
                ?.symbol,
        )
        val initializerHash = projectionSupportInitializerHash(entries, ownerIdentity)
        val function = pluginContext.irFactory.buildFun {
            name = Name.identifier("kotlinWinRTProjectionSupportInitialize_$initializerHash")
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = file
        }
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        val resolvedEntries = resolveProjectionRegistrarClasses(entries) { className ->
            pluginContext.findClassSymbol(ClassId.topLevel(FqName(className)), file)
        }
        val anchorFiles = moduleFragment.projectionSupportAnchorFiles(ownerIdentity).ifEmpty { listOf(file) }
        val chunkFunctions = resolvedEntries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE).mapIndexed { index, chunk ->
            val chunkFile = anchorFiles[index % anchorFiles.size]
            pluginContext.irFactory.buildFun {
                name = Name.identifier("kotlinWinRTProjectionSupportInitialize_${initializerHash}_${index.toString().padStart(3, '0')}")
                returnType = pluginContext.irBuiltIns.unitType
                visibility = DescriptorVisibilities.INTERNAL
                modality = Modality.FINAL
            }.apply {
                parent = chunkFile
                val chunkBuilder = DeclarationIrBuilder(pluginContext, symbol)
                body = chunkBuilder.irBlockBody {
                    chunk.forEach { (entry, projectedClass) ->
                        +chunkBuilder.irCall(registerGeneratedProjectionTypeIndex).apply {
                            arguments[0] = IrClassReferenceImpl(
                                startOffset = 0,
                                endOffset = 0,
                                type = pluginContext.irBuiltIns.kClassClass.owner.defaultType,
                                symbol = projectedClass,
                                classType = projectedClass.owner.defaultType,
                            )
                            arguments[1] = chunkBuilder.irString(entry.projectedTypeName)
                            arguments[2] = chunkBuilder.irString(entry.kind)
                            arguments[3] = chunkBuilder.irString(entry.baseTypeName)
                            arguments[4] = chunkBuilder.irString(entry.interfaceIid)
                        }
                    }
                }
            }
        }
        chunkFunctions.forEach { chunkFunction ->
            (chunkFunction.parent as? IrFile ?: file).declarations += chunkFunction
        }
        function.body = builder.irBlockBody {
            chunkFunctions.forEach { chunkFunction ->
                +builder.irCall(chunkFunction.symbol)
            }
        }
        file.declarations += function
        return function.symbol
    }

    private fun IrModuleFragment.projectionSupportAnchorFile(ownerIdentity: String): IrFile? {
        projectionSupportAnchorFiles(ownerIdentity).firstOrNull()?.let { return it }
        val anchorFileName = winRTProjectionSupportAnchorFileName(ownerIdentity)
        return files.firstOrNull { file ->
            file.fileEntry.name.replace('\\', '/').substringAfterLast('/') == "$anchorFileName.kt"
        }
    }

    private fun IrModuleFragment.projectionSupportAnchorFiles(ownerIdentity: String): List<IrFile> {
        val anchorFileName = winRTProjectionSupportAnchorFileName(ownerIdentity)
        val ownerAnchors = files.filter { file ->
            val name = file.fileEntry.name.replace('\\', '/').substringAfterLast('/')
            name == "$anchorFileName.kt" ||
                (name.startsWith("${anchorFileName}_") && name.endsWith(".kt"))
        }
        if (ownerAnchors.isNotEmpty()) {
            return ownerAnchors.sortedBy { file -> file.fileEntry.name.replace('\\', '/') }
        }
        return files
            .filter { file ->
                val name = file.fileEntry.name.replace('\\', '/').substringAfterLast('/')
                name == "WinRTProjectionSupportAnchor.kt" ||
                    (name.startsWith("WinRTProjectionSupportAnchor_") && name.endsWith(".kt"))
            }
            .sortedBy { file -> file.fileEntry.name.replace('\\', '/') }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerProjectionSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        initialize: IrSimpleFunctionSymbol?,
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    if (!call.isProjectionSupportEnsureInitializedCall()) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol
                        ?: return call.also {
                            pluginContext.reportUnloweredCompilerPluginIntrinsic(
                                "WinRTProjectionSupportIntrinsic.ensureInitialized",
                            )
                        }
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return initialize?.let { symbol -> builder.irCall(symbol) } ?: builder.irUnit()
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.isProjectionSupportEnsureInitializedCall(): Boolean {
        val function = symbol.owner
        if (function.name.asString() != "ensureInitialized") {
            return false
        }
        val ownerClass = function.parent as? IrClass ?: return false
        return ownerClass.fqNameWhenAvailable?.asString() ==
            "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic"
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun collectGenericTypeInstantiationSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
    ): Set<String> {
        val calls = mutableSetOf<String>()
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    call.genericTypeInstantiationSupportIntrinsicCallName()?.let(calls::add)
                    return call
                }
            },
        )
        return calls
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addGenericTypeInstantiationSupportFunctions(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRTGenericTypeInstantiationEntry>,
        supportClassName: String?,
        ownerIdentity: String?,
        includeInitializeAll: Boolean,
        includeInitializeBySourceType: Boolean,
    ): GenericTypeInstantiationSupportFunctions? {
        if (entries.isEmpty() || (!includeInitializeAll && !includeInitializeBySourceType)) {
            return null
        }
        val supportClassFqName = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "compiler support manifest className",
            value = supportClassName?.takeIf(String::isNotBlank),
        )
        val hostFile = moduleFragment.projectionSupportAnchorFile(ownerIdentity ?: "")
            ?: moduleFragment.files.firstOrNull { irFile ->
                !irFile.fileEntry.name.contains("AuthoringTypeDetailsRegistrar") &&
                    !irFile.fileEntry.name.contains("WinRTEventProjectionHelper")
            }
        val file = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "projection support anchor or non-authoring module file",
            value = hostFile,
        )
        val supportClass = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "class $supportClassFqName",
            value = pluginContext.findClassSymbol(
                ClassId.topLevel(FqName(supportClassFqName)),
                file,
            ),
        )
        val entryClassFqName = genericTypeInstantiationEntryClassName(supportClassFqName)
        val entryClass = requireCompilerSupportPrerequisite(
            description = "generic type instantiation",
            prerequisite = "class $entryClassFqName",
            value = pluginContext.findClassSymbol(
                ClassId.topLevel(FqName(entryClassFqName)),
                file,
            ),
        )
        val entryConstructor = entryClass.owner.declarations
            .filterIsInstance<IrConstructor>()
            .singleOrNull { constructor ->
                constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 9
            }
            ?.symbol
            .let { symbol ->
                requireCompilerSupportPrerequisite(
                    description = "generic type instantiation",
                    prerequisite = "${entryClassFqName.substringAfterLast('.')} constructor with 9 regular parameters",
                    value = symbol,
                )
            }
        val initializeEntry = supportClass.owner.declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { function ->
                function.name.asString() == "initializeEntry" &&
                    function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 1
            }
            ?.symbol
            .let { symbol ->
                requireCompilerSupportPrerequisite(
                    description = "generic type instantiation",
                    prerequisite = "WinRTGenericTypeInstantiations.initializeEntry with 1 regular parameter",
                    value = symbol,
                )
            }
        val listOf = pluginContext.findFunctionSymbols(
            CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("listOf")),
            file,
        ).singleOrNull { function ->
            function.owner.parameters
                .singleOrNull { parameter -> parameter.kind == IrParameterKind.Regular }
                ?.varargElementType != null
        }.let { symbol ->
            requireCompilerSupportPrerequisite(
                description = "generic type instantiation",
                prerequisite = "kotlin.collections.listOf vararg function",
                value = symbol,
            )
        }

        val sortedEntries = entries.sortedWith(
            compareBy(KotlinWinRTGenericTypeInstantiationEntry::sourceType, KotlinWinRTGenericTypeInstantiationEntry::className),
        )
        val entryChunks = sortedEntries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE)
        val anchorFiles = moduleFragment.projectionSupportAnchorFiles(ownerIdentity ?: "").ifEmpty { listOf(file) }
        val initializeAllChunks = if (includeInitializeAll) {
            entryChunks.mapIndexed { index, chunk ->
                val chunkFile = anchorFiles[index % anchorFiles.size]
                pluginContext.irFactory.buildFun {
                    name = Name.identifier("kotlinWinRTGenericTypeInstantiationInitializeAll_${index.toString().padStart(3, '0')}")
                    returnType = pluginContext.irBuiltIns.unitType
                    visibility = DescriptorVisibilities.INTERNAL
                    modality = Modality.FINAL
                }.apply {
                    parent = chunkFile
                    val chunkBuilder = DeclarationIrBuilder(pluginContext, symbol)
                    body = chunkBuilder.irBlockBody {
                        chunk.forEach { entry ->
                            +chunkBuilder.genericTypeInstantiationInitializeEntryCall(
                                pluginContext = pluginContext,
                                supportClass = supportClass,
                                initializeEntry = initializeEntry,
                                entryConstructor = entryConstructor,
                                listOf = listOf,
                                entry = entry,
                            )
                        }
                    }
                }
            }
        } else {
            emptyList()
        }
        val initializeAll = if (includeInitializeAll) {
            pluginContext.irFactory.buildFun {
                name = Name.identifier("kotlinWinRTGenericTypeInstantiationInitializeAll")
                returnType = pluginContext.irBuiltIns.unitType
                visibility = DescriptorVisibilities.INTERNAL
                modality = Modality.FINAL
            }.apply {
                parent = file
            }.also { function ->
                val initializeAllBuilder = DeclarationIrBuilder(pluginContext, function.symbol)
                function.body = initializeAllBuilder.irBlockBody {
                    initializeAllChunks.forEach { chunkFunction ->
                        +initializeAllBuilder.irCall(chunkFunction.symbol)
                    }
                }
            }
        } else {
            null
        }
        val initializeBySourceTypeChunks = if (includeInitializeBySourceType) {
            entryChunks.mapIndexed { index, chunk ->
                val chunkFile = anchorFiles[index % anchorFiles.size]
                pluginContext.irFactory.buildFun {
                    name = Name.identifier("kotlinWinRTGenericTypeInstantiationInitializeBySourceType_${index.toString().padStart(3, '0')}")
                    returnType = pluginContext.irBuiltIns.unitType
                    visibility = DescriptorVisibilities.INTERNAL
                    modality = Modality.FINAL
                }.apply {
                    parent = chunkFile
                    parameters = listOf(
                        pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                            name = Name.identifier("sourceType")
                            type = pluginContext.irBuiltIns.stringType
                            kind = IrParameterKind.Regular
                        }, this),
                    )
                    val chunkBuilder = DeclarationIrBuilder(pluginContext, symbol)
                    val chunkSourceTypeParameter = parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
                    body = chunkBuilder.irBlockBody {
                        chunk.forEach { entry ->
                            +chunkBuilder.irIfThen(
                                type = pluginContext.irBuiltIns.unitType,
                                condition = chunkBuilder.irEquals(
                                    chunkBuilder.irGet(chunkSourceTypeParameter),
                                    chunkBuilder.irString(entry.sourceType),
                                ),
                                thenPart = chunkBuilder.genericTypeInstantiationInitializeEntryCall(
                                    pluginContext = pluginContext,
                                    supportClass = supportClass,
                                    initializeEntry = initializeEntry,
                                    entryConstructor = entryConstructor,
                                    listOf = listOf,
                                    entry = entry,
                                ),
                            )
                        }
                    }
                }
            }
        } else {
            emptyList()
        }

        val initializeBySourceType = if (includeInitializeBySourceType) {
            pluginContext.irFactory.buildFun {
                name = Name.identifier("kotlinWinRTGenericTypeInstantiationInitializeBySourceType")
                returnType = pluginContext.irBuiltIns.unitType
                visibility = DescriptorVisibilities.INTERNAL
                modality = Modality.FINAL
            }.apply {
                parent = file
                parameters = listOf(
                    pluginContext.irFactory.buildValueParameter(IrValueParameterBuilder().apply {
                        name = Name.identifier("sourceType")
                        type = pluginContext.irBuiltIns.stringType
                        kind = IrParameterKind.Regular
                    }, this),
                )
            }.also { function ->
                val initializeBySourceTypeBuilder = DeclarationIrBuilder(pluginContext, function.symbol)
                val sourceTypeParameter = function.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
                function.body = initializeBySourceTypeBuilder.irBlockBody {
                    initializeBySourceTypeChunks.forEach { chunkFunction ->
                        +initializeBySourceTypeBuilder.irCall(chunkFunction.symbol).apply {
                            arguments[0] = initializeBySourceTypeBuilder.irGet(sourceTypeParameter)
                        }
                    }
                }
            }
        } else {
            null
        }
        initializeAllChunks.forEach { chunkFunction ->
            (chunkFunction.parent as? IrFile ?: file).declarations += chunkFunction
        }
        initializeBySourceTypeChunks.forEach { chunkFunction ->
            (chunkFunction.parent as? IrFile ?: file).declarations += chunkFunction
        }
        initializeAll?.let(file.declarations::add)
        initializeBySourceType?.let(file.declarations::add)
        return GenericTypeInstantiationSupportFunctions(
            initializeAll = initializeAll?.symbol,
            initializeBySourceType = initializeBySourceType?.symbol,
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.genericTypeInstantiationInitializeEntryCall(
        pluginContext: IrPluginContext,
        supportClass: IrClassSymbol,
        initializeEntry: IrSimpleFunctionSymbol,
        entryConstructor: IrConstructorSymbol,
        listOf: IrSimpleFunctionSymbol,
        entry: KotlinWinRTGenericTypeInstantiationEntry,
    ): IrExpression =
        irCall(initializeEntry).apply {
            dispatchReceiver = irGetObject(supportClass)
            arguments[1] = irCall(entryConstructor).apply {
                arguments[0] = irString(entry.className)
                arguments[1] = irString(entry.sourceType)
                arguments[2] = irBoolean(entry.isDelegate)
                arguments[3] = irStringList(pluginContext, listOf, entry.rcwFunctions)
                arguments[4] = irStringList(pluginContext, listOf, entry.vtableFunctions)
                arguments[5] = irStringList(pluginContext, listOf, entry.propertyAccessors)
                arguments[6] = irStringList(pluginContext, listOf, entry.genericReturnOnlyRcwFunctions)
                arguments[7] = irStringList(pluginContext, listOf, entry.projectedGenericFallbacks)
                arguments[8] = irStringList(pluginContext, listOf, entry.dependencies)
            }
        }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun DeclarationIrBuilder.irStringList(
        pluginContext: IrPluginContext,
        listOf: IrSimpleFunctionSymbol,
        values: List<String>,
    ): IrExpression {
        val parameter = listOf.owner.parameters.single { parameter -> parameter.kind == IrParameterKind.Regular }
        return irCall(listOf).apply {
            arguments[0] = IrVarargImpl(
                startOffset = startOffset,
                endOffset = endOffset,
                type = parameter.type,
                varargElementType = pluginContext.irBuiltIns.stringType,
                elements = values.map { value -> irString(value) },
            )
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerGenericTypeInstantiationSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        support: GenericTypeInstantiationSupportFunctions?,
    ) {
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    val genericCall = call.genericTypeInstantiationSupportIntrinsicCallName() ?: return call
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol
                        ?: return call.also {
                            pluginContext.reportUnloweredCompilerPluginIntrinsic(
                                "WinRTGenericTypeInstantiationSupportIntrinsic.$genericCall",
                            )
                        }
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return when (genericCall) {
                        "initializeAll" -> support?.initializeAll?.let(builder::irCall) ?: builder.irUnit()
                        "initializeBySourceType" -> support?.initializeBySourceType?.let { initializeBySourceType ->
                            builder.irCall(initializeBySourceType).apply {
                                arguments[0] = call.arguments[1]
                            }
                        } ?: builder.irUnit()
                        else -> call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.genericTypeInstantiationSupportIntrinsicCallName(): String? {
        val function = symbol.owner
        val name = function.name.asString()
        if (name != "initializeAll" && name != "initializeBySourceType") {
            return null
        }
        val ownerClass = function.parent as? IrClass ?: return null
        return name.takeIf {
            ownerClass.fqNameWhenAvailable?.asString() ==
                "io.github.composefluent.winrt.runtime.WinRTGenericTypeInstantiationSupportIntrinsic"
        }
    }

    private data class GenericTypeInstantiationSupportFunctions(
        val initializeAll: IrSimpleFunctionSymbol?,
        val initializeBySourceType: IrSimpleFunctionSymbol?,
    )

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addGenericAbiSupportFunctions(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        supportClassName: String?,
    ): GenericAbiSupportFunctions? {
        if (supportClassName.isNullOrBlank()) {
            return null
        }
        val file = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "module file",
            value = moduleFragment.files.firstOrNull(),
        )
        val supportClass = requireCompilerSupportPrerequisite(
            description = "generic ABI registry",
            prerequisite = "class $supportClassName",
            value = pluginContext.findClassSymbol(ClassId.topLevel(FqName(supportClassName)), file),
        )
        return GenericAbiSupportFunctions(
            supportClass = supportClass,
            delegateNamed = requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "$supportClassName.delegateNamed with 1 regular parameter",
                value = supportClass.functionNamedWithRegularParameterCount("delegateNamed", 1),
            ),
            delegatesForSourceType = requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "$supportClassName.delegatesForSourceType with 1 regular parameter",
                value = supportClass.functionNamedWithRegularParameterCount("delegatesForSourceType", 1),
            ),
            isDerivedGenericInterface = requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "$supportClassName.isDerivedGenericInterface with 1 regular parameter",
                value = supportClass.functionNamedWithRegularParameterCount("isDerivedGenericInterface", 1),
            ),
            registerAbiDelegates = requireCompilerSupportPrerequisite(
                description = "generic ABI registry",
                prerequisite = "$supportClassName.registerAbiDelegates with 1 regular parameter",
                value = supportClass.functionNamedWithRegularParameterCount("registerAbiDelegates", 1),
            ),
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerGenericAbiSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        support: GenericAbiSupportFunctions?,
    ) {
        val lookupFile = moduleFragment.files.firstOrNull()
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    val genericAbiCall = call.genericAbiSupportIntrinsicCallName() ?: return call
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol
                        ?: return call.also {
                            pluginContext.reportUnloweredCompilerPluginIntrinsic(
                                "WinRTGenericAbiSupportIntrinsic.$genericAbiCall",
                            )
                        }
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return when (genericAbiCall) {
                        "delegateNamed" -> support?.let {
                            builder.irCall(it.delegateNamed).apply {
                                dispatchReceiver = builder.irGetObject(it.supportClass)
                                arguments[1] = call.arguments[1]
                            }
                        } ?: builder.irNull()
                        "delegatesForSourceType" -> support?.let {
                            builder.irCall(it.delegatesForSourceType).apply {
                                dispatchReceiver = builder.irGetObject(it.supportClass)
                                arguments[1] = call.arguments[1]
                            }
                        } ?: builder.irCall(
                            pluginContext.findFunctionSymbols(
                                CallableId(KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME, Name.identifier("emptyList")),
                                lookupFile,
                            ).single(),
                        )
                        "isDerivedGenericInterface" -> support?.let {
                            builder.irCall(it.isDerivedGenericInterface).apply {
                                dispatchReceiver = builder.irGetObject(it.supportClass)
                                arguments[1] = call.arguments[1]
                            }
                        } ?: builder.irBoolean(false)
                        "registerAbiDelegates" -> support?.let {
                            builder.irCall(it.registerAbiDelegates).apply {
                                dispatchReceiver = builder.irGetObject(it.supportClass)
                                arguments[1] = call.arguments[1]
                            }
                        } ?: builder.irUnit()
                        else -> call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.genericAbiSupportIntrinsicCallName(): String? {
        val function = symbol.owner
        val name = function.name.asString()
        if (name !in GENERIC_ABI_SUPPORT_INTRINSIC_FUNCTIONS) {
            return null
        }
        val ownerClass = function.parent as? IrClass ?: return null
        return name.takeIf {
            ownerClass.fqNameWhenAvailable?.asString() ==
                "io.github.composefluent.winrt.runtime.WinRTGenericAbiSupportIntrinsic"
        }
    }

    private data class GenericAbiSupportFunctions(
        val supportClass: IrClassSymbol,
        val delegateNamed: IrSimpleFunctionSymbol,
        val delegatesForSourceType: IrSimpleFunctionSymbol,
        val isDerivedGenericInterface: IrSimpleFunctionSymbol,
        val registerAbiDelegates: IrSimpleFunctionSymbol,
    )

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoringSupportIntrinsicCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        manifestEntries: List<KotlinWinRTAuthoringTypeDetailsRegistrarEntry>,
    ) {
        val lookupFile = moduleFragment.files.firstOrNull()
        val registrars = authoringTypeDetailsRegistrarRegisters(pluginContext, lookupFile, manifestEntries)
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    if (!call.isAuthoringSupportEnsureInitializedCall()) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol
                        ?: return call.also {
                            pluginContext.reportUnloweredCompilerPluginIntrinsic(
                                "WinRTAuthoringSupportIntrinsic.ensureInitialized",
                            )
                        }
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    val resolvedRegistrars = requireCompilerSupportPrerequisite(
                        description = "authoring type-details registrar",
                        prerequisite = "WinRTAuthoringTypeDetailsRegistrar.register with no regular parameters",
                        value = registrars.takeIf(List<*>::isNotEmpty),
                    )
                    return builder.irBlock(resultType = call.type) {
                        resolvedRegistrars.forEach { resolvedRegistrar ->
                            +builder.irCall(resolvedRegistrar.register).apply {
                                dispatchReceiver = builder.irGetObject(resolvedRegistrar.registrarClass)
                            }
                        }
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoredTypeConstructors(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        authoredTypeNames: Set<String>,
    ) {
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val registrar = requireCompilerSupportPrerequisite(
            description = "authoring type-details registrar",
            prerequisite = "WinRTAuthoringTypeDetailsRegistrar.register with no regular parameters",
            value = authoringTypeDetailsRegistrarRegister(pluginContext, moduleFragment.files.firstOrNull()),
        )
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitConstructor(declaration: IrConstructor): IrStatement {
                    val constructor = super.visitConstructor(declaration) as IrConstructor
                    val ownerClass = constructor.parent as? IrClass ?: return constructor
                    val ownerTypeName = ownerClass.fqNameWhenAvailable?.asString() ?: return constructor
                    if (ownerTypeName !in authoredTypeNames) {
                        return constructor
                    }
                    val body = constructor.body as? IrBlockBody ?: return constructor
                    val builder = DeclarationIrBuilder(pluginContext, constructor.symbol, constructor.startOffset, constructor.endOffset)
                    body.statements.add(
                        0,
                        builder.irCall(registrar.register).apply {
                            dispatchReceiver = builder.irGetObject(registrar.registrarClass)
                        },
                    )
                    return constructor
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun lowerAuthoredTypeConstructorCalls(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        authoredTypeNames: Set<String>,
    ) {
        if (authoredTypeNames.isEmpty()) {
            return
        }
        val registrar = requireCompilerSupportPrerequisite(
            description = "authoring type-details registrar",
            prerequisite = "WinRTAuthoringTypeDetailsRegistrar.register with no regular parameters",
            value = authoringTypeDetailsRegistrarRegister(pluginContext, moduleFragment.files.firstOrNull()),
        )
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
                    val call = super.visitConstructorCall(expression) as IrConstructorCall
                    val constructedClass = call.symbol.owner.parent as? IrClass ?: return call
                    val constructedTypeName = constructedClass.fqNameWhenAvailable?.asString() ?: return call
                    if (constructedTypeName !in authoredTypeNames) {
                        return call
                    }
                    val builderScope = currentScope?.scope?.scopeOwnerSymbol
                        ?: return call.also {
                            pluginContext.reportUnloweredCompilerPluginIntrinsic(
                                "authored constructor call for $constructedTypeName",
                            )
                        }
                    val builder = DeclarationIrBuilder(pluginContext, builderScope, call.startOffset, call.endOffset)
                    return builder.irBlock(resultType = call.type) {
                        +builder.irCall(registrar.register).apply {
                            dispatchReceiver = builder.irGetObject(registrar.registrarClass)
                        }
                        +call
                    }
                }
            },
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun authoringTypeDetailsRegistrarRegister(
        pluginContext: IrPluginContext,
        fromFile: IrFile?,
    ): AuthoringTypeDetailsRegistrar? {
        val registrarName = authoringTypeDetailsRegistrarName(authoringAssemblyName)
        val registrarClass = pluginContext.findClassSymbol(
            ClassId.topLevel(FqName("io.github.composefluent.winrt.projections.support.$registrarName")),
            fromFile,
        ) ?: return null
        val register = registrarClass
            .owner
            .declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { function ->
                function.name.asString() == "register" &&
                    function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
            }
            ?.symbol
            ?: return null
        return AuthoringTypeDetailsRegistrar(registrarClass, register)
    }

    private fun authoringTypeDetailsRegistrarRegisters(
        pluginContext: IrPluginContext,
        fromFile: IrFile?,
        manifestEntries: List<KotlinWinRTAuthoringTypeDetailsRegistrarEntry>,
    ): List<AuthoringTypeDetailsRegistrar> {
        val currentRegistrarName = authoringTypeDetailsRegistrarName(authoringAssemblyName)
        val classNames = (manifestEntries.map(KotlinWinRTAuthoringTypeDetailsRegistrarEntry::className) +
            "io.github.composefluent.winrt.projections.support.$currentRegistrarName")
            .distinct()
        return classNames.mapNotNull { className ->
            authoringTypeDetailsRegistrarRegister(pluginContext, fromFile, className)
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun authoringTypeDetailsRegistrarRegister(
        pluginContext: IrPluginContext,
        fromFile: IrFile?,
        className: String,
    ): AuthoringTypeDetailsRegistrar? {
        val registrarClass = pluginContext.findClassSymbol(
            ClassId.topLevel(FqName(className)),
            fromFile,
        ) ?: return null
        val register = registrarClass
            .owner
            .declarations
            .filterIsInstance<IrSimpleFunction>()
            .singleOrNull { function ->
                function.name.asString() == "register" &&
                    function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
            }
            ?.symbol
            ?: return null
        return AuthoringTypeDetailsRegistrar(registrarClass, register)
    }

    private data class AuthoringTypeDetailsRegistrar(
        val registrarClass: IrClassSymbol,
        val register: IrSimpleFunctionSymbol,
    )

    private fun IrPluginContext.reportUnloweredCompilerPluginIntrinsic(description: String) {
        reportCompilerPluginMessage(
            CompilerMessageSeverity.ERROR,
            "kotlin-winrt compiler plugin recognized $description but could not lower it from the current IR scope.",
        )
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun IrCall.isAuthoringSupportEnsureInitializedCall(): Boolean {
        val function = symbol.owner
        if (function.name.asString() != "ensureInitialized") {
            return false
        }
        val ownerClass = function.parent as? IrClass ?: return false
        return ownerClass.fqNameWhenAvailable?.asString() ==
            "io.github.composefluent.winrt.runtime.WinRTAuthoringSupportIntrinsic"
    }
}

private val WINRT_PROJECTION_INTRINSIC_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRTProjectionIntrinsic")

private const val KOTLIN_WINRT_GENERATED_SUPPRESS_MARKER = "KOTLIN_WINRT_GENERATED"

@Suppress("DEPRECATION")
private fun IrPluginContext.reportCompilerPluginMessage(
    severity: CompilerMessageSeverity,
    message: String,
) {
    messageCollector.report(severity, message, null)
}

private fun IrPluginContext.findClassSymbol(classId: ClassId, fromFile: IrFile? = null): IrClassSymbol? =
    fromFile
        ?.let { file -> finderForSource(file).findClass(classId) }
        ?: finderForBuiltins().findClass(classId)

private fun IrPluginContext.findFunctionSymbols(
    callableId: CallableId,
    fromFile: IrFile? = null,
): Collection<IrSimpleFunctionSymbol> {
    val sourceSymbols = fromFile
        ?.let { file -> finderForSource(file).findFunctions(callableId) }
        .orEmpty()
    return sourceSymbols.ifEmpty { finderForBuiltins().findFunctions(callableId) }
}

private fun IrPluginContext.findPropertySymbols(
    callableId: CallableId,
    fromFile: IrFile? = null,
) =
    fromFile
        ?.let { file -> finderForSource(file).findProperties(callableId) }
        .orEmpty()
        .ifEmpty { finderForBuiltins().findProperties(callableId) }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamed(name: String): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { it.name.asString() == name }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithValueParameterTypes(
    name: String,
    vararg typeNames: FqName,
): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name &&
                function.parameters
                    .filter { parameter -> parameter.kind == IrParameterKind.Regular }
                    .map { parameter -> parameter.type.classFqName } == typeNames.toList()
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.functionNamedWithRegularParameterCount(
    name: String,
    count: Int,
): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrSimpleFunction>()
        .singleOrNull { function ->
            function.name.asString() == name &&
                function.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == count
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.fieldNamed(name: String): IrField? =
    owner.declarations
        .filterIsInstance<IrField>()
        .singleOrNull { field -> field.name.asString() == name }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.propertyGetter(name: String): IrSimpleFunctionSymbol? =
    owner.declarations
        .filterIsInstance<IrProperty>()
        .singleOrNull { it.name.asString() == name }
        ?.getter
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.singleValueConstructor(): IrConstructorSymbol? =
    owner.declarations
        .filterIsInstance<IrConstructor>()
        .singleOrNull { constructor ->
            constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == 1
        }
        ?.symbol

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrClassSymbol.constructorWithRegularParameterCount(count: Int): IrConstructorSymbol? =
    owner.declarations
        .filterIsInstance<IrConstructor>()
        .singleOrNull { constructor ->
            constructor.parameters.count { parameter -> parameter.kind == IrParameterKind.Regular } == count
        }
        ?.symbol

private val WINRT_RUNTIME_PACKAGE_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime")

private val KOTLIN_PACKAGE_FQ_NAME =
    FqName("kotlin")

private val KOTLIN_COLLECTIONS_PACKAGE_FQ_NAME =
    FqName("kotlin.collections")

private val KOTLIN_BYTE_FQ_NAME =
    FqName("kotlin.Byte")

private val KOTLIN_SHORT_FQ_NAME =
    FqName("kotlin.Short")

private val KOTLIN_INT_FQ_NAME =
    FqName("kotlin.Int")

private val KOTLIN_LONG_FQ_NAME =
    FqName("kotlin.Long")

private val KOTLIN_FLOAT_FQ_NAME =
    FqName("kotlin.Float")

private val KOTLIN_DOUBLE_FQ_NAME =
    FqName("kotlin.Double")

private val KOTLIN_UINT_FQ_NAME =
    FqName("kotlin.UInt")

private val KOTLIN_ULONG_FQ_NAME =
    FqName("kotlin.ULong")

private val KOTLIN_UBYTE_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UByte"))

private val KOTLIN_USHORT_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UShort"))

private val WINRT_RAW_COM_PTR_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.RawComPtr")

private val WINRT_RAW_ADDRESS_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.RawAddress")

private val JAVA_ADDRESS_LAYOUT_FQ_NAME =
    FqName("java.lang.foreign.AddressLayout")

private val KOTLIN_UINT_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("UInt"))

private val KOTLIN_ULONG_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("ULong"))

private val KOTLIN_FUNCTION1_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function1"))

private val KOTLIN_FUNCTION2_CLASS_ID =
    ClassId(KOTLIN_PACKAGE_FQ_NAME, Name.identifier("Function2"))

private val GENERIC_ABI_SUPPORT_INTRINSIC_FUNCTIONS =
    setOf("delegateNamed", "delegatesForSourceType", "isDerivedGenericInterface", "registerAbiDelegates")

private const val GENERIC_ABI_LOOKUP_SHARD_SIZE = 48

private val WINRT_COM_VTABLE_INVOKER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")

private val WINRT_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("HString"))

private val WINRT_REFERENCED_HSTRING_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ReferencedHString"))

private val WINRT_IWINRT_OBJECT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("IWinRTObject"))

private val WINRT_OBJECT_MARSHALLER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("WinRTObjectMarshaller"))

private val WINRT_COM_OBJECT_REFERENCE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("ComObjectReference"))

private val WINRT_IUNKNOWN_REFERENCE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("IUnknownReference"))

private val WINRT_RAW_COM_PTR_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("RawComPtr"))

private val WINRT_RAW_ADDRESS_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("RawAddress"))

private val WINRT_PLATFORM_ABI_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("PlatformAbi"))

private val WINRT_NATIVE_SCOPE_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeScope"))

private val WINRT_NATIVE_STRUCT_ADAPTER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeStructAdapter"))

private val WINRT_NATIVE_STRUCT_LAYOUT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeStructLayout"))

private val WINRT_MARSHALER_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("Marshaler"))

private val WINRT_HRESULT_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("HResult"))

private val WINRT_JVM_FFM_DOWNCALL_HANDLES_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("WinRTJvmFfmDowncallHandles"))

private val JAVA_FOREIGN_PACKAGE_FQ_NAME =
    FqName("java.lang.foreign")

private val JAVA_MEMORY_SEGMENT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("MemorySegment"))

private val JAVA_VALUE_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("ValueLayout"))

private val JAVA_ADDRESS_LAYOUT_CLASS_ID =
    ClassId(JAVA_FOREIGN_PACKAGE_FQ_NAME, Name.identifier("AddressLayout"))

private val JAVA_METHOD_HANDLE_CLASS_ID =
    ClassId(FqName("java.lang.invoke"), Name.identifier("MethodHandle"))

private val KOTLINX_CINTEROP_PACKAGE_FQ_NAME =
    FqName("kotlinx.cinterop")

private val KOTLINX_CINTEROP_CPOINTER_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("CPointer"))

private val KOTLINX_CINTEROP_CFUNCTION_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("CFunction"))

private val KOTLINX_CINTEROP_COPAQUE_CLASS_ID =
    ClassId(KOTLINX_CINTEROP_PACKAGE_FQ_NAME, Name.identifier("COpaque"))

private val WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS = listOf(
    "callUnit",
    "callBoolean",
    "callScalar",
    "getString",
    "getBoolean",
    "getNoExceptionBoolean",
    "getInt32",
    "getUInt32",
    "getInt64",
    "getUInt64",
    "getFloat",
    "getDouble",
    "getStruct",
    "callStruct",
    "getArray",
    "setStruct",
    "setString",
    "setBoolean",
    "setInt32",
    "setUInt32",
    "setInt64",
    "setUInt64",
    "setFloat",
    "setDouble",
    "getProjectedRuntimeClass",
    "getNullableProjectedRuntimeClass",
    "getProjectedInterface",
    "getNullableProjectedInterface",
    "staticGetArray",
    "staticGetArrayWithProjectedObject",
    "staticCallProjectedRuntimeClassWithString",
    "staticCallProjectedInterfaceWithString",
    "callProjectedRuntimeClass",
    "callProjectedInterface",
    "callObject",
)

private val WINRT_PROJECTION_INTRINSIC_FUNCTIONS = WINRT_PROJECTION_INTRINSIC_DIRECT_FUNCTIONS

private val RUNTIME_OWNED_PROJECTION_INTRINSICS =
    setOf(
        "getString",
        "getBoolean",
        "getNoExceptionBoolean",
        "getInt32",
        "getUInt32",
        "getInt64",
        "getUInt64",
        "getFloat",
        "getDouble",
        "setString",
        "setInt32",
        "setUInt32",
        "setInt64",
        "setUInt64",
    )

internal fun isProjectionIntrinsicFunction(
    name: String,
    ownerFqName: String?,
): Boolean =
    name in WINRT_PROJECTION_INTRINSIC_FUNCTIONS &&
        ownerFqName == WINRT_PROJECTION_INTRINSIC_FQ_NAME.asString()

internal fun generatedSourceRootFromMetadataIndex(metadataIndexPath: String?): String? {
    val indexPath = metadataIndexPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return null
    val parent = indexPath.toAbsolutePath().normalize().parent ?: return null
    if (parent.fileName?.toString() != "kotlin-winrt-authoring") {
        return null
    }
    return parent.parent?.toString()?.normalizedCompilerPathPrefix()
}

internal fun isGeneratedSourceFile(fileName: String, generatedSourceRoot: String?): Boolean {
    val root = generatedSourceRoot ?: return false
    val normalizedFileName = fileName.normalizedCompilerPathPrefix()
    if (normalizedFileName == root || normalizedFileName.startsWith("$root/")) {
        return true
    }
    val siblingGeneratedRoots = buildList {
        if (root.endsWith("/kotlin-winrt")) {
            add("$root-authoring")
        }
        if ("/kotlin-winrt/" in root) {
            add(root.replace("/kotlin-winrt/", "/kotlin-winrt-authoring/"))
        }
        val generatedDirectories = listOf(
            "/generated/kotlin-winrt/src/main/kotlin",
            "/generated/kotlin-winrt/src/commonmain/kotlin",
        )
        generatedDirectories.firstOrNull(root::endsWith)?.let { generatedDirectory ->
            add(root.removeSuffix(generatedDirectory) + "/generated/kotlin-winrt-compiler-authoring")
        }
    }
    return siblingGeneratedRoots.any { sibling ->
        normalizedFileName == sibling || normalizedFileName.startsWith("$sibling/")
    }
}

private fun String.normalizedCompilerPathPrefix(): String =
    replace('\\', '/')
        .trimEnd('/')
        .lowercase()

data class KotlinWinRTCompilerSupportManifestEntry(
    val kind: String,
    val className: String,
    val sourceFile: String,
    val entries: Int,
    val owner: String = "",
)

fun readCompilerSupportManifest(path: Path): List<KotlinWinRTCompilerSupportManifestEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "compiler support manifest",
        expectedHeader = setOf(
            COMPILER_SUPPORT_MANIFEST_HEADER,
            COMPILER_SUPPORT_MANIFEST_HEADER_WITH_OWNER,
        ),
        parse = ::parseCompilerSupportManifestLine,
    )
    val duplicate = entries
        .groupBy { entry -> listOf(entry.kind, entry.className, entry.sourceFile, entry.owner) }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate compiler support manifest entry for kind ${duplicate!![0]}, class ${duplicate[1]}, source file ${duplicate[2]}, and owner ${duplicate[3]} in $path."
    }
    return entries
}

fun readCompilerSupportManifestIfConfigured(path: String?): List<KotlinWinRTCompilerSupportManifestEntry> {
    val manifestPath = path?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
    require(Files.isRegularFile(manifestPath)) {
        "kotlin-winrt compiler plugin requires compiler support manifest $manifestPath to exist when compilerSupportManifest is configured."
    }
    return readCompilerSupportManifest(manifestPath)
}

private fun parseCompilerSupportManifestLine(line: String): KotlinWinRTCompilerSupportManifestEntry? {
    val parts = line.split('\t')
    if (parts.size !in 4..5) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
        return null
    }
    if (parts[0] !in COMPILER_SUPPORT_MANIFEST_KINDS) {
        return null
    }
    val expected = COMPILER_SUPPORT_MANIFEST_ENTRY_BY_KIND[parts[0]] ?: return null
    if (expected.className != null && parts[1] != expected.className) {
        return null
    }
    if (parts[2] != expected.sourceFile) {
        return null
    }
    val entries = parts[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    return KotlinWinRTCompilerSupportManifestEntry(
        kind = parts[0],
        className = parts[1],
        sourceFile = parts[2],
        entries = entries,
        owner = parts.getOrNull(4).orEmpty(),
    )
}

private fun winRTAuthoringHostExportsClassName(ownerIdentity: String): String {
    val suffix = ownerIdentity.toKotlinSupportIdentifierSuffix()
    return if (suffix.isBlank()) {
        "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports"
    } else {
        "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports_$suffix"
    }
}

private fun winRTProjectionSupportAnchorFileName(ownerIdentity: String): String {
    val suffix = ownerIdentity.toKotlinSupportIdentifierSuffix()
    return if (suffix.isBlank()) {
        "WinRTProjectionSupportAnchor"
    } else {
        "WinRTProjectionSupportAnchor_$suffix"
    }
}

private fun String.toKotlinSupportIdentifierSuffix(): String =
    buildString {
        this@toKotlinSupportIdentifierSuffix.trim().forEach { char ->
            append(if (char.isLetterOrDigit()) char else '_')
        }
    }.trim('_')
        .replace(Regex("_+"), "_")
        .let { suffix ->
            if (suffix.firstOrNull()?.isDigit() == true) "_$suffix" else suffix
        }

private val COMPILER_SUPPORT_MANIFEST_KINDS: Set<String> =
    setOf(
        "projection-registrar",
        "generic-type-instantiation",
        "generic-abi-registry",
        "xaml-component-resource",
        "authoring-type-details-registrar",
    )

private const val COMPILER_SUPPORT_MANIFEST_HEADER: String =
    "kind\tclassName\tsourceFile\tentries"

private const val COMPILER_SUPPORT_MANIFEST_HEADER_WITH_OWNER: String =
    "kind\tclassName\tsourceFile\tentries\towner"

private val COMPILER_SUPPORT_MANIFEST_ENTRY_BY_KIND: Map<String, CompilerSupportManifestExpectedEntry> =
    mapOf(
        "projection-registrar" to CompilerSupportManifestExpectedEntry(
            className = "io.github.composefluent.winrt.runtime.WinRTProjectionSupportIntrinsic",
            sourceFile = "projection-registrar.tsv",
        ),
        "generic-type-instantiation" to CompilerSupportManifestExpectedEntry(
            className = null,
            sourceFile = "generic-instantiations.tsv",
        ),
        "generic-abi-registry" to CompilerSupportManifestExpectedEntry(
            className = null,
            sourceFile = "generic-abi-registry.tsv",
        ),
        "xaml-component-resource" to CompilerSupportManifestExpectedEntry(
            className = "io.github.composefluent.winrt.projections.support.WinUiXamlComponentResources",
            sourceFile = "xaml-component-resources.tsv",
        ),
        "authoring-type-details-registrar" to CompilerSupportManifestExpectedEntry(
            className = null,
            sourceFile = "authoring-type-details-registrars.tsv",
        ),
    )

private data class CompilerSupportManifestExpectedEntry(
    val className: String?,
    val sourceFile: String,
)

private const val COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME_PREFIX: String =
    "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest_"

private const val PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX: String =
    "io/github/composefluent/winrt/projections/support/WinRTProjectionSupport_"

private const val PROJECTION_SUPPORT_INITIALIZER_CLASS_NAME_PREFIX: String =
    "WinRTProjectionSupport_"

private const val STALE_EVENT_PROJECTION_REGISTRY_CLASS_PATH: String =
    "io/github/composefluent/winrt/projections/support/WinRTEventProjectionRegistry.class"

private const val PROJECTION_REGISTRAR_CHUNK_SIZE: Int = 128

private const val GENERIC_ABI_REGISTRY_LIST_SEPARATOR: String = "\u001F"

fun writeCompilerSupportManifestClass(
    entries: List<KotlinWinRTCompilerSupportManifestEntry>,
    outputDirectory: Path,
): String? {
    if (entries.isEmpty()) {
        deleteStaleCompilerSupportManifestClasses(outputDirectory, currentInternalName = null)
        return null
    }
    val internalName = compilerSupportManifestInternalName(entries)
    deleteStaleCompilerSupportManifestClasses(outputDirectory, internalName)
    val classWriter = ClassWriter(0)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.addIntConstantField("ENTRY_COUNT", entries.size)
    entries
        .groupBy(KotlinWinRTCompilerSupportManifestEntry::kind)
        .toSortedMap()
        .forEach { (kind, kindEntries) ->
            classWriter.addIntConstantField("${compilerSupportFieldPrefix(kind)}_ENTRIES", kindEntries.sumOf { it.entries })
        }
    classWriter.addDefaultConstructor()
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$internalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
    return internalName
}

private fun compilerSupportManifestInternalName(
    entries: List<KotlinWinRTCompilerSupportManifestEntry>,
): String =
    COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME_PREFIX +
        MessageDigest.getInstance("SHA-256")
            .digest(
                entries
                    .sortedWith(
                        compareBy(
                            KotlinWinRTCompilerSupportManifestEntry::owner,
                            KotlinWinRTCompilerSupportManifestEntry::kind,
                            KotlinWinRTCompilerSupportManifestEntry::className,
                            KotlinWinRTCompilerSupportManifestEntry::sourceFile,
                            KotlinWinRTCompilerSupportManifestEntry::entries,
                        ),
                    )
                    .joinToString(separator = "\n") { entry ->
                        listOf(
                            entry.owner,
                            entry.kind,
                            entry.className,
                            entry.sourceFile,
                            entry.entries.toString(),
                        ).joinToString("\t")
                    }
                    .toByteArray(StandardCharsets.UTF_8),
            )
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
            .take(16)

private fun deleteStaleCompilerSupportManifestClasses(
    outputDirectory: Path,
    currentInternalName: String?,
) {
    val supportDirectory = outputDirectory.resolve(COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME_PREFIX).parent ?: return
    if (!Files.isDirectory(supportDirectory)) {
        return
    }
    val currentFileName = currentInternalName?.substringAfterLast('/')?.let { "$it.class" }
    Files.list(supportDirectory).use { stream ->
        stream
            .filter(Files::isRegularFile)
            .filter { path -> path.fileName.toString().startsWith("WinRTCompilerSupportManifest") }
            .filter { path -> path.fileName.toString().endsWith(".class") }
            .filter { path -> currentFileName == null || path.fileName.toString() != currentFileName }
            .forEach(Files::deleteIfExists)
    }
}

data class KotlinWinRTProjectionRegistrarEntry(
    val kotlinClassName: String,
    val projectedTypeName: String,
    val kind: String,
    val baseTypeName: String,
    val metadataClassName: String,
    val interfaceIid: String,
)

data class KotlinWinRTAuthoringTypeDetailsRegistrarEntry(
    val className: String,
)

fun <T : Any> resolveProjectionRegistrarClasses(
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    resolve: (String) -> T?,
): List<Pair<KotlinWinRTProjectionRegistrarEntry, T>> =
    entries
        .sortedWith(compareBy(KotlinWinRTProjectionRegistrarEntry::kotlinClassName, KotlinWinRTProjectionRegistrarEntry::projectedTypeName))
        .map { entry ->
            val projectedClass = resolve(entry.kotlinClassName)
            require(projectedClass != null) {
                "kotlin-winrt compiler plugin requires projection registrar input for ${entry.projectedTypeName} " +
                    "to reference resolvable Kotlin class ${entry.kotlinClassName}."
            }
            entry to projectedClass
        }

internal fun genericTypeInstantiationEntryClassName(supportClassName: String): String {
    val packageName = supportClassName.substringBeforeLast('.', missingDelimiterValue = "")
    val supportSimpleName = supportClassName.substringAfterLast('.')
    val ownerSuffix = supportSimpleName.removePrefix("WinRTGenericTypeInstantiations")
    val entrySimpleName = "GenericTypeInstantiationEntry$ownerSuffix"
    return if (packageName.isEmpty()) entrySimpleName else "$packageName.$entrySimpleName"
}

fun <T : Any> requireCompilerSupportPrerequisite(
    description: String,
    prerequisite: String,
    value: T?,
): T {
    require(value != null) {
        "kotlin-winrt compiler plugin requires $description support input to resolve $prerequisite."
    }
    return value
}

fun readProjectionRegistrarEntries(path: Path): List<KotlinWinRTProjectionRegistrarEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "projection registrar input",
        expectedHeader = PROJECTION_REGISTRAR_HEADER,
        parse = ::parseProjectionRegistrarLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.kotlinClassName to entry.projectedTypeName }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate projection registrar input for Kotlin class ${duplicate!!.first} and projected type ${duplicate.second} in $path."
    }
    return entries
}

private const val PROJECTION_REGISTRAR_HEADER: String =
    "kotlinClassName\tprojectedTypeName\tkind\tbaseTypeName\tmetadataClassName\tinterfaceIid"

private fun parseProjectionRegistrarLine(line: String): KotlinWinRTProjectionRegistrarEntry? {
    val parts = line.split('\t')
    if (parts.size != 6) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
        return null
    }
    if (parts[2] !in PROJECTION_REGISTRAR_KINDS) {
        return null
    }
    return KotlinWinRTProjectionRegistrarEntry(
        kotlinClassName = parts[0],
        projectedTypeName = parts[1],
        kind = parts[2],
        baseTypeName = parts[3],
        metadataClassName = parts[4],
        interfaceIid = parts[5],
    )
}

private val PROJECTION_REGISTRAR_KINDS: Set<String> =
    setOf("Interface", "RuntimeClass", "Enum", "Struct", "Delegate")

fun readAuthoringTypeDetailsRegistrarEntries(path: Path): List<KotlinWinRTAuthoringTypeDetailsRegistrarEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "authoring type-details registrar input",
        expectedHeader = AUTHORING_TYPE_DETAILS_REGISTRAR_HEADER,
        parse = ::parseAuthoringTypeDetailsRegistrarLine,
    )
    val duplicate = entries
        .groupBy(KotlinWinRTAuthoringTypeDetailsRegistrarEntry::className)
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate authoring type-details registrar input for class $duplicate in $path."
    }
    return entries
}

private const val AUTHORING_TYPE_DETAILS_REGISTRAR_HEADER: String =
    "className"

private fun parseAuthoringTypeDetailsRegistrarLine(line: String): KotlinWinRTAuthoringTypeDetailsRegistrarEntry? {
    val parts = line.split('\t')
    if (parts.size != 1 || parts[0].isBlank()) {
        return null
    }
    return KotlinWinRTAuthoringTypeDetailsRegistrarEntry(parts[0])
}

fun writeProjectionSupportInitializerClass(
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    outputDirectory: Path,
    ownerIdentity: String = "",
): String? {
    if (entries.isEmpty()) {
        deleteStaleProjectionSupportInitializerClasses(outputDirectory, currentInternalName = null)
        return null
    }
    val internalName = projectionSupportInitializerInternalName(entries, ownerIdentity)
    deleteStaleProjectionSupportInitializerClasses(outputDirectory, internalName)
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        "initialized",
        "Z",
        null,
        null,
    ).visitEnd()
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE)
    val classInitializer = classWriter.visitMethod(
        Opcodes.ACC_STATIC,
        "<clinit>",
        "()V",
        null,
        null,
    )
    classInitializer.visitCode()
    classInitializer.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        internalName,
        "initialize",
        "()V",
        false,
    )
    classInitializer.visitInsn(Opcodes.RETURN)
    classInitializer.visitMaxs(0, 0)
    classInitializer.visitEnd()
    val initialize = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "initialize",
        "()V",
        null,
        null,
    )
    initialize.visitCode()
    val alreadyInitialized = org.jetbrains.org.objectweb.asm.Label()
    initialize.visitFieldInsn(Opcodes.GETSTATIC, internalName, "initialized", "Z")
    initialize.visitJumpInsn(Opcodes.IFNE, alreadyInitialized)
    initialize.visitInsn(Opcodes.ICONST_1)
    initialize.visitFieldInsn(Opcodes.PUTSTATIC, internalName, "initialized", "Z")
    chunks.indices.forEach { index ->
        initialize.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            projectionRegistrarChunkInternalName(internalName, index),
            "register",
            "()V",
            false,
        )
    }
    initialize.visitLabel(alreadyInitialized)
    initialize.visitInsn(Opcodes.RETURN)
    initialize.visitMaxs(0, 0)
    initialize.visitEnd()

    classWriter.visitEnd()

    val target = outputDirectory.resolve("$internalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
    chunks.forEachIndexed { index, chunk ->
        writeProjectionRegistrarChunkClass(
            internalName = projectionRegistrarChunkInternalName(internalName, index),
            entries = chunk,
            outputDirectory = outputDirectory,
        )
    }
    return internalName
}

fun deleteStaleProjectionSupportInitializerClasses(
    outputDirectory: Path,
    currentInternalName: String?,
) {
    val supportDirectory = outputDirectory.resolve(PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX)
        .parent
        ?: return
    if (!Files.isDirectory(supportDirectory)) {
        return
    }
    val currentRelativePath = currentInternalName?.let { "$it.class" }
    Files.list(supportDirectory).use { stream ->
        stream
            .filter(Files::isRegularFile)
            .filter { path -> path.fileName.toString().startsWith(PROJECTION_SUPPORT_INITIALIZER_CLASS_NAME_PREFIX) }
            .filter { path -> path.fileName.toString().endsWith(".class") }
            .filter { path ->
                currentRelativePath == null ||
                    outputDirectory.relativize(path).toString().replace('\\', '/') != currentRelativePath
            }
            .forEach(Files::deleteIfExists)
    }
}

fun projectionSupportInitializerInternalName(
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    ownerIdentity: String = "",
): String {
    val digest = projectionSupportInitializerHash(entries, ownerIdentity)
    return "$PROJECTION_SUPPORT_INITIALIZER_INTERNAL_NAME_PREFIX$digest"
}

fun projectionSupportInitializerHash(
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    ownerIdentity: String = "",
): String =
    MessageDigest.getInstance("SHA-256")
        .digest(
            buildString {
                append("owner\t")
                append(ownerIdentity)
                append('\n')
                entries
                    .sortedWith(compareBy(KotlinWinRTProjectionRegistrarEntry::kotlinClassName, KotlinWinRTProjectionRegistrarEntry::projectedTypeName))
                    .joinTo(this, separator = "\n") { entry ->
                        listOf(
                            entry.kotlinClassName,
                            entry.projectedTypeName,
                            entry.kind,
                            entry.baseTypeName,
                            entry.metadataClassName,
                            entry.interfaceIid,
                        ).joinToString("\t")
                    }
            }
                .toByteArray(StandardCharsets.UTF_8),
        )
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(16)

private fun projectionRegistrarChunkInternalName(
    initializerInternalName: String,
    index: Int,
): String =
    "${initializerInternalName}_Chunk${index.toString().padStart(3, '0')}"

private fun writeProjectionRegistrarChunkClass(
    internalName: String,
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    outputDirectory: Path,
) {
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        internalName,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.addDefaultConstructor()
    classWriter.addProjectionRegistrarChunk("register", entries, Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC)
    classWriter.visitEnd()
    val target = outputDirectory.resolve("$internalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addProjectionRegistrarChunk(
    name: String,
    entries: List<KotlinWinRTProjectionRegistrarEntry>,
    access: Int = Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
) {
    val method = visitMethod(
        access,
        name,
        "()V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        if (entry.metadataClassName.isNotBlank()) {
            val metadataInternalName = entry.metadataClassName.toMetadataInternalName()
            method.visitFieldInsn(
                Opcodes.GETSTATIC,
                metadataInternalName,
                "INSTANCE",
                "L$metadataInternalName;",
            )
            method.visitInsn(Opcodes.POP)
        }
        method.visitLdcInsn(Type.getObjectType(entry.kotlinClassName.toInternalName()))
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "kotlin/jvm/internal/Reflection",
            "getOrCreateKotlinClass",
            "(Ljava/lang/Class;)Lkotlin/reflect/KClass;",
            false,
        )
        method.visitLdcInsn(entry.projectedTypeName)
        method.visitLdcInsn(entry.kind)
        method.visitLdcInsn(entry.baseTypeName)
        method.visitLdcInsn(entry.interfaceIid)
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/github/composefluent/winrt/runtime/CompilerGeneratedProjectionTypeIndexesKt",
            "registerGeneratedProjectionTypeIndex",
            "(Lkotlin/reflect/KClass;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun String.toInternalName(): String =
    replace('.', '/')

private fun String.toMetadataInternalName(): String =
    removeSuffix(".Metadata").toInternalName() + "\$Metadata"

data class KotlinWinRTGenericTypeInstantiationEntry(
    val className: String,
    val sourceType: String,
    val isDelegate: Boolean,
    val rcwFunctions: List<String>,
    val vtableFunctions: List<String>,
    val propertyAccessors: List<String>,
    val genericReturnOnlyRcwFunctions: List<String>,
    val projectedGenericFallbacks: List<String>,
    val dependencies: List<String>,
)

fun readGenericTypeInstantiationEntries(path: Path): List<KotlinWinRTGenericTypeInstantiationEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "generic type instantiation input",
        expectedHeader = GENERIC_TYPE_INSTANTIATION_HEADER,
        parse = ::parseGenericTypeInstantiationLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.sourceType to entry.className }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate generic type instantiation input for source type ${duplicate!!.first} and class ${duplicate.second} in $path."
    }
    return entries
}

private const val GENERIC_TYPE_INSTANTIATION_HEADER: String =
    "className\tsourceType\tisDelegate\trcwFunctions\tvtableFunctions\tpropertyAccessors\tgenericReturnOnlyRcwFunctions\tprojectedGenericFallbacks\tdependencies"

private fun parseGenericTypeInstantiationLine(line: String): KotlinWinRTGenericTypeInstantiationEntry? {
    val parts = line.split('\t')
    if (parts.size != 9) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    val rcwFunctions = parts[3].splitListFieldOrNull() ?: return null
    val vtableFunctions = parts[4].splitListFieldOrNull() ?: return null
    val propertyAccessors = parts[5].splitListFieldOrNull() ?: return null
    val genericReturnOnlyRcwFunctions = parts[6].splitListFieldOrNull() ?: return null
    val projectedGenericFallbacks = parts[7].splitListFieldOrNull() ?: return null
    val dependencies = parts[8].splitListFieldOrNull() ?: return null
    return KotlinWinRTGenericTypeInstantiationEntry(
        className = parts[0],
        sourceType = parts[1],
        isDelegate = parts[2].toBooleanStrictOrNull() ?: return null,
        rcwFunctions = rcwFunctions,
        vtableFunctions = vtableFunctions,
        propertyAccessors = propertyAccessors,
        genericReturnOnlyRcwFunctions = genericReturnOnlyRcwFunctions,
        projectedGenericFallbacks = projectedGenericFallbacks,
        dependencies = dependencies,
    )
}

private fun String.splitListFieldOrNull(): List<String>? =
    splitSupportListFieldOrNull(',')

data class KotlinWinRTGenericAbiRegistryEntry(
    val kind: String,
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)

fun readGenericAbiRegistryEntries(path: Path): List<KotlinWinRTGenericAbiRegistryEntry> {
    val entries = readRequiredTsvRows(
        path = path,
        description = "generic ABI registry input",
        expectedHeader = GENERIC_ABI_REGISTRY_HEADER,
        parse = ::parseGenericAbiRegistryLine,
    )
    val duplicate = entries
        .groupBy { entry -> entry.duplicateKey() }
        .entries
        .firstOrNull { (_, values) -> values.size > 1 }
        ?.key
    require(duplicate == null) {
        "kotlin-winrt compiler plugin found duplicate generic ABI registry input for ${duplicate!!} in $path."
    }
    return entries
}

private const val GENERIC_ABI_REGISTRY_HEADER: String =
    "kind\tname\tsourceGenericType\toperation\tdeclaration\tabiParameterTypes\ttypeArrayShape"

private fun KotlinWinRTGenericAbiRegistryEntry.duplicateKey(): String =
    when (kind) {
        "derived-interface" -> "$kind:$name"
        "delegate" -> "$kind:$name:$sourceGenericType"
        else -> "$kind:$name"
    }

fun <T> readCompilerSupportInputEntries(
    manifestPath: Path,
    manifestEntries: List<KotlinWinRTCompilerSupportManifestEntry>,
    kind: String,
    description: String,
    read: (Path) -> List<T>,
): List<T> {
    val manifestDirectory = manifestPath.parent ?: Path.of("")
    return manifestEntries
        .asSequence()
        .filter { it.kind == kind }
        .groupBy(KotlinWinRTCompilerSupportManifestEntry::sourceFile)
        .asSequence()
        .flatMap { (sourceFile, entriesForSource) ->
            val sourcePath = manifestDirectory.resolve(sourceFile)
            require(Files.isRegularFile(sourcePath)) {
                "kotlin-winrt compiler plugin requires $description file $sourcePath declared by $manifestPath to exist."
            }
            val expectedEntries = entriesForSource.maxOf(KotlinWinRTCompilerSupportManifestEntry::entries)
            val entries = read(sourcePath)
            require(entries.size == expectedEntries) {
                "kotlin-winrt compiler plugin expected $expectedEntries $description entries in $sourcePath declared by $manifestPath, but found ${entries.size}."
            }
            entries.asSequence()
        }
        .toList()
}

private fun <T> readRequiredTsvRows(
    path: Path,
    description: String,
    expectedHeader: String,
    parse: (String) -> T?,
): List<T> =
    readRequiredTsvRows(
        path = path,
        description = description,
        expectedHeader = setOf(expectedHeader),
        parse = parse,
    )

private fun <T> readRequiredTsvRows(
    path: Path,
    description: String,
    expectedHeader: Set<String>,
    parse: (String) -> T?,
): List<T> {
    val lines = Files.readAllLines(path)
    val actualHeader = lines.firstOrNull()
    require(actualHeader != null && actualHeader in expectedHeader) {
        "kotlin-winrt compiler plugin expected $description header ${expectedHeader.joinToString(prefix = "'", postfix = "'", separator = "' or '")} in $path."
    }
    return lines
        .asSequence()
        .drop(1)
        .mapIndexedNotNull { index, line ->
            if (line.isBlank()) {
                null
            } else {
                parse(line)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt compiler plugin could not parse $description row ${index + 2} in $path.",
                    )
            }
        }
        .toList()
}

private fun parseGenericAbiRegistryLine(line: String): KotlinWinRTGenericAbiRegistryEntry? {
    val parts = line.split('\t')
    if (parts.size != 7) {
        return null
    }
    if (parts[0].isBlank() || parts[1].isBlank()) {
        return null
    }
    val abiParameterTypes = parts[5].splitGenericAbiRegistryListFieldOrNull() ?: return null
    val typeArrayShape = parts[6].splitGenericAbiRegistryListFieldOrNull() ?: return null
    when (parts[0]) {
        "derived-interface" -> Unit
        "delegate" -> {
            if (parts[2].isBlank() || parts[3].isBlank() || parts[4].isBlank()) {
                return null
            }
            if (abiParameterTypes.isEmpty() || typeArrayShape.isEmpty()) {
                return null
            }
        }
        else -> return null
    }
    return KotlinWinRTGenericAbiRegistryEntry(
        kind = parts[0],
        name = parts[1],
        sourceGenericType = parts[2],
        operation = parts[3],
        declaration = parts[4],
        abiParameterTypes = abiParameterTypes,
        typeArrayShape = typeArrayShape,
    )
}

private fun String.splitGenericAbiRegistryListFieldOrNull(): List<String>? =
    splitSupportListFieldOrNull(GENERIC_ABI_REGISTRY_LIST_SEPARATOR)

private fun String.splitSupportListFieldOrNull(separator: String): List<String>? {
    if (isEmpty()) {
        return emptyList()
    }
    val parts = split(separator)
    if (parts.any(String::isBlank)) {
        return null
    }
    return parts
}

private fun String.splitSupportListFieldOrNull(separator: Char): List<String>? =
    splitSupportListFieldOrNull(separator.toString())

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRTTypeHandle(
    projectedTypeName: String,
    interfaceId: String,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/WinRTTypeHandle")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(projectedTypeName)
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/Guid")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(interfaceId)
    visitMethodInsn(Opcodes.INVOKESPECIAL, "io/github/composefluent/winrt/runtime/Guid", "<init>", "(Ljava/lang/String;)V", false)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/WinRTTypeHandle",
        "<init>",
        "(Ljava/lang/String;Lio/github/composefluent/winrt/runtime/Guid;)V",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addStringList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        visitLdcInsn(value)
        visitInsn(Opcodes.AASTORE)
    }
    visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "kotlin/collections/CollectionsKt",
        "listOf",
        "([Ljava/lang/Object;)Ljava/util/List;",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.pushInt(value: Int) {
    when (value) {
        0 -> visitInsn(Opcodes.ICONST_0)
        1 -> visitInsn(Opcodes.ICONST_1)
        2 -> visitInsn(Opcodes.ICONST_2)
        3 -> visitInsn(Opcodes.ICONST_3)
        4 -> visitInsn(Opcodes.ICONST_4)
        5 -> visitInsn(Opcodes.ICONST_5)
        in Byte.MIN_VALUE..Byte.MAX_VALUE -> visitIntInsn(Opcodes.BIPUSH, value)
        in Short.MIN_VALUE..Short.MAX_VALUE -> visitIntInsn(Opcodes.SIPUSH, value)
        else -> visitLdcInsn(value)
    }
}

private fun compilerSupportFieldPrefix(kind: String): String =
    kind.uppercase()
        .map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")

private fun ClassWriter.addIntConstantField(name: String, value: Int) {
    visitField(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
        name,
        "I",
        null,
        value,
    ).visitEnd()
}

private fun ClassWriter.addDefaultConstructor() {
    val method = visitMethod(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null)
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(1, 1)
    method.visitEnd()
}

internal data class SelectedProjectionIntrinsicAbiSymbols(
    val useJvmFfm: Boolean,
    val useNativeCInterop: Boolean,
)

internal fun selectProjectionIntrinsicAbiSymbols(
    hasJvmFfmSymbols: Boolean,
    hasNativeCInteropSymbols: Boolean,
): SelectedProjectionIntrinsicAbiSymbols =
    if (hasNativeCInteropSymbols) {
        SelectedProjectionIntrinsicAbiSymbols(useJvmFfm = false, useNativeCInterop = true)
    } else {
        SelectedProjectionIntrinsicAbiSymbols(useJvmFfm = hasJvmFfmSymbols, useNativeCInterop = false)
    }
