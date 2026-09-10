package io.github.composefluent.winrt.compiler

import io.github.composefluent.winrt.compiler.callsites.lowering.lowerWinRTProjectionCallSites
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
import io.github.composefluent.winrt.compiler.authoring.resolveIndexedWinRTTypeByProjectedName
import io.github.composefluent.winrt.compiler.authoring.authoringTypeDetailsRegistrarName
import io.github.composefluent.winrt.metadata.WinRTFundamentalType
import io.github.composefluent.winrt.metadata.guidSignatureFragment
import io.github.composefluent.winrt.metadata.toKotlinProjectionTypeName
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
import org.jetbrains.kotlin.ir.builders.declarations.buildField
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
import org.jetbrains.kotlin.ir.builders.irSetField
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
            optionName = "projectionSupportOwnerArtifactName",
            valueDescription = "<file>",
            description = "Artifact identity used to address the external compiled projection support initializer.",
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
        CliOption(
            optionName = "projectionSupportMode",
            valueDescription = "<embedded|external>",
            description = "Whether imported projection support is emitted in this compilation or supplied by a cached projection artifact.",
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
        } else if (option.optionName == "projectionSupportOwnerArtifactName") {
            configuration.put(PROJECTION_SUPPORT_OWNER_ARTIFACT_NAME_KEY, value)
        } else if (option.optionName == "compilerSupportManifest") {
            configuration.put(COMPILER_SUPPORT_MANIFEST_KEY, value)
        } else if (option.optionName == "compilerSupportClassOutputDirectory") {
            configuration.put(COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY, value)
        } else if (option.optionName == "projectionSupportMode") {
            configuration.put(PROJECTION_SUPPORT_MODE_KEY, value)
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
        val PROJECTION_SUPPORT_OWNER_ARTIFACT_NAME_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt projection support owner artifact name")
        val COMPILER_SUPPORT_MANIFEST_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support manifest")
        val COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support class output directory")
        val PROJECTION_SUPPORT_MODE_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt projection support mode")
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
                projectionSupportOwnerArtifactName = configuration.get(KotlinWinRTCommandLineProcessor.PROJECTION_SUPPORT_OWNER_ARTIFACT_NAME_KEY),
                compilerSupportManifestPath = configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
                compilerSupportClassOutputDirectoryPath = configuration.get(KotlinWinRTCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
                projectionSupportMode = configuration.get(KotlinWinRTCommandLineProcessor.PROJECTION_SUPPORT_MODE_KEY),
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
    private val projectionSupportOwnerArtifactName: String?,
    private val compilerSupportManifestPath: String?,
    private val compilerSupportClassOutputDirectoryPath: String?,
    private val projectionSupportMode: String?,
) : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val compilerSupportEntries = readCompilerSupportManifest()
        val projectionRegistrarEntries = readProjectionRegistrarEntries(compilerSupportEntries)
        lowerWinRTProjectionCallSites(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            guidSignaturesByKotlinClass = guidSignaturesForLowering(projectionRegistrarEntries),
        )
        val projectionSupportOwnerIdentity = projectionSupportOwnerArtifactName
            ?.takeIf(String::isNotBlank)
            ?: authoringTargetArtifactName
            ?.takeIf(String::isNotBlank)
            ?: compilerSupportEntries
                .filter { entry -> entry.kind == "projection-registrar" }
                .maxByOrNull(KotlinWinRTCompilerSupportManifestEntry::entries)
                ?.owner
                .orEmpty()
        val authoringRegistrarEntries = readAuthoringTypeDetailsRegistrarEntries(compilerSupportEntries)
        val emitProjectionSupport = when (projectionSupportMode?.lowercase()) {
            null, "", "embedded" -> true
            "external" -> false
            else -> error("Unsupported kotlin-winrt projectionSupportMode '$projectionSupportMode'.")
        }
        if (emitProjectionSupport) {
            writeCompilerSupportClasses(compilerSupportEntries, projectionRegistrarEntries, projectionSupportOwnerIdentity)
        } else {
            clearEmbeddedProjectionSupport(
                compilerSupportClassOutputDirectoryPath
                    ?.takeIf(String::isNotBlank)
                    ?.let(Path::of),
            )
        }
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
        val projectionSupportInitialize = if (emitProjectionSupport) {
            addProjectionSupportInitializerFunction(
                moduleFragment = moduleFragment,
                pluginContext = pluginContext,
                entries = projectionRegistrarEntries,
                ownerIdentity = projectionSupportOwnerIdentity,
            )
        } else {
            addExternalProjectionSupportInitializerFunction(
                moduleFragment = moduleFragment,
                pluginContext = pluginContext,
                entries = projectionRegistrarEntries,
                ownerIdentity = projectionSupportOwnerIdentity,
            )
        }
        lowerProjectionSupportIntrinsicCalls(
            moduleFragment = moduleFragment,
            pluginContext = pluginContext,
            initialize = projectionSupportInitialize,
        )
        lowerAuthoringSupportIntrinsicCalls(moduleFragment, pluginContext, authoringRegistrarEntries)
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
            else -> runtimeClassNames.firstOrNull { runtimeClassName ->
                runtimeClassName.equals(projectionPackageToMetadataName(targetName), ignoreCase = true)
            }
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

    private fun clearEmbeddedProjectionSupport(outputDirectory: Path?) {
        if (outputDirectory == null) {
            return
        }
        deleteStaleCompilerSupportManifestClasses(outputDirectory, currentInternalName = null)
        deleteStaleProjectionSupportInitializerClasses(outputDirectory, currentInternalName = null)
        Files.deleteIfExists(outputDirectory.resolve(STALE_EVENT_PROJECTION_REGISTRY_CLASS_PATH))
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

    private fun guidSignaturesForLowering(
        entries: List<KotlinWinRTProjectionRegistrarEntry>,
    ): Map<String, String> = buildMap {
        WinRTFundamentalType.entries.forEach { type ->
            put("kotlin.${type.toKotlinProjectionTypeName()}", type.guidSignatureFragment())
        }
        put("kotlin.Any", "cinterface(IInspectable)")
        put("io.github.composefluent.winrt.runtime.Guid", "g16")
        entries.forEach { entry ->
            if (entry.guidSignature.isNotBlank()) {
                val previous = put(entry.kotlinClassName, entry.guidSignature)
                require(previous == null || previous == entry.guidSignature) {
                    "Conflicting WinRT GUID signatures for ${entry.kotlinClassName}: '$previous' and '${entry.guidSignature}'."
                }
            }
        }
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
        writeStringIfChanged(
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
            resolveIndexedWinRTTypeByProjectedName(superTypeName, winRTTypes)?.let { return@flatMap listOf(it) }
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
            ?.let { typeName ->
                requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $baseClassName."
                }.also { type ->
                    require(type.kind == "RuntimeClass") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation baseClassName must reference a WinRT runtime class: $baseClassName."
                    }
                }
            }
        val resolvedInterfaces = interfaceNames
            .map { typeName ->
                requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation interfaceNames must reference WinRT interfaces: $typeName."
                    }
                }
            }
        val resolvedOverridableInterfaces = overridableInterfaceNames
            .map { typeName ->
                requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
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
                requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
                    "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation references unknown WinRT metadata type $typeName."
                }.also { type ->
                    require(type.kind == "Interface") {
                        "WinRT authored type ${klass.fqNameWhenAvailable?.asString()} annotation activatableFactoryInterfaceName must reference a WinRT interface: $typeName."
                    }
                }.qualifiedName
            }
        val resolvedStaticFactoryInterfaces = staticFactoryInterfaceNames
            .map { typeName ->
                requireNotNull(resolveIndexedWinRTTypeByProjectedName(typeName, winRTTypes)) {
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
        val initializedField = pluginContext.irFactory.buildField {
            name = Name.identifier("kotlinWinRTProjectionSupportInitialized_$initializerHash")
            type = pluginContext.irBuiltIns.booleanType
            visibility = DescriptorVisibilities.PRIVATE
            isFinal = false
            isStatic = true
        }.apply {
            parent = file
            val initializerBuilder = DeclarationIrBuilder(pluginContext, symbol)
            initializer = pluginContext.irFactory.createExpressionBody(
                startOffset,
                endOffset,
                initializerBuilder.irBoolean(false),
            )
        }
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
        }.map { (entry, projectedClass) ->
            val metadataClass = entry.metadataClassName
                .takeIf(String::isNotBlank)
                ?.let { metadataClassName ->
                    requireCompilerSupportPrerequisite(
                        description = "projection registrar",
                        prerequisite = "metadata object $metadataClassName for ${entry.projectedTypeName}",
                        value = projectedClass.owner.declarations
                            .filterIsInstance<IrClass>()
                            .singleOrNull { nested ->
                                nested.fqNameWhenAvailable?.asString() == metadataClassName
                            }
                            ?.symbol,
                    )
                }
            Triple(entry, projectedClass, metadataClass)
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
                    chunk.forEach { (entry, projectedClass, metadataClass) ->
                        metadataClass?.let { symbol ->
                            +chunkBuilder.irGetObject(symbol)
                        }
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
        file.declarations += initializedField
        function.body = builder.irBlockBody {
            // Metadata.register() calls the support intrinsic, so mark the initializer before
            // touching companion objects to make the generated registration pass reentrant.
            +builder.irIfThen(
                type = pluginContext.irBuiltIns.unitType,
                condition = builder.irEquals(
                    builder.irGetField(null, initializedField),
                    builder.irBoolean(false),
                ),
                thenPart = builder.irBlock {
                    +builder.irSetField(null, initializedField, builder.irBoolean(true))
                    chunkFunctions.forEach { chunkFunction ->
                        +builder.irCall(chunkFunction.symbol)
                    }
                },
            )
        }
        file.declarations += function
        return function.symbol
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun addExternalProjectionSupportInitializerFunction(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
        entries: List<KotlinWinRTProjectionRegistrarEntry>,
        ownerIdentity: String,
    ): IrSimpleFunctionSymbol? {
        if (entries.isEmpty()) {
            return null
        }
        val lookupFile = requireCompilerSupportPrerequisite(
            description = "external projection registrar",
            prerequisite = "module file",
            value = moduleFragment.files.firstOrNull(),
        )
        val externalInitializerClassName = projectionSupportInitializerInternalName(entries, ownerIdentity)
            .replace('/', '.')
        val externalInitializerClass = requireCompilerSupportPrerequisite(
            description = "external projection registrar",
            prerequisite = "compiled initializer class $externalInitializerClassName",
            value = pluginContext.findClassSymbol(
                ClassId.topLevel(FqName(externalInitializerClassName)),
                lookupFile,
            ),
        )
        val externalInitializer = requireCompilerSupportPrerequisite(
            description = "external projection registrar",
            prerequisite = "static initialize() method on $externalInitializerClassName",
            value = externalInitializerClass.owner.declarations
                .filterIsInstance<IrSimpleFunction>()
                .singleOrNull { function ->
                    function.name.asString() == "initialize" &&
                        function.parameters.none { parameter -> parameter.kind == IrParameterKind.Regular }
                }
                ?.symbol,
        )
        val functionName = Name.identifier(
            "kotlinWinRTProjectionSupportInitialize_${projectionSupportInitializerHash(entries, ownerIdentity)}",
        )
        val function = pluginContext.irFactory.buildFun {
            name = functionName
            returnType = pluginContext.irBuiltIns.unitType
            visibility = DescriptorVisibilities.INTERNAL
            modality = Modality.FINAL
        }.apply {
            parent = lookupFile
        }
        val builder = DeclarationIrBuilder(pluginContext, function.symbol)
        function.body = builder.irBlockBody {
            +builder.irCall(externalInitializer)
        }
        lookupFile.declarations += function
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
                    // Keep one constructor-owned fallback for runtime-only and external-language
                    // construction. A separate constructor-call wrapper would execute the same
                    // once-only registrar read twice and cannot cover reflective/runtime entry
                    // points; generated composable constructors already lower the module support
                    // marker before their value-dependent base-factory call.
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

private val WINRT_COM_VTABLE_INVOKER_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.ComVtableInvoker")

private val WINRT_NATIVE_HSTRING_REFERENCE_FRAME_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeHStringReferenceFrame"))

private val WINRT_NATIVE_SCALAR_SCRATCH_FRAME_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeScalarScratchFrame"))

private val WINRT_NATIVE_STRUCT_SCRATCH_FRAME_CLASS_ID =
    ClassId(WINRT_RUNTIME_PACKAGE_FQ_NAME, Name.identifier("NativeStructScratchFrame"))

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

private fun parseCompilerSupportManifestLine(
    header: String,
    line: String,
): KotlinWinRTCompilerSupportManifestEntry? {
    val parts = line.split('\t')
    val expectedColumns = if (header == COMPILER_SUPPORT_MANIFEST_HEADER) 4 else 5
    if (parts.size != expectedColumns) {
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

private fun writeBytesIfChanged(target: Path, bytes: ByteArray) {
    if (Files.isRegularFile(target) && Files.readAllBytes(target).contentEquals(bytes)) {
        return
    }
    Files.createDirectories(target.parent)
    Files.write(target, bytes)
}

private fun writeStringIfChanged(target: Path, contents: String) {
    writeBytesIfChanged(target, contents.toByteArray(StandardCharsets.UTF_8))
}

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
        writeBytesIfChanged(target, classWriter.toByteArray())
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
    val guidSignature: String = "",
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
        expectedHeader = PROJECTION_REGISTRAR_HEADERS,
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

private const val LEGACY_PROJECTION_REGISTRAR_HEADER: String =
    "kotlinClassName\tprojectedTypeName\tkind\tbaseTypeName\tmetadataClassName\tinterfaceIid"

private const val PROJECTION_REGISTRAR_HEADER: String =
    "$LEGACY_PROJECTION_REGISTRAR_HEADER\tguidSignature"

private val PROJECTION_REGISTRAR_HEADERS: Set<String> =
    setOf(LEGACY_PROJECTION_REGISTRAR_HEADER, PROJECTION_REGISTRAR_HEADER)

private fun parseProjectionRegistrarLine(
    header: String,
    line: String,
): KotlinWinRTProjectionRegistrarEntry? {
    val parts = line.split('\t')
    val expectedColumns = if (header == LEGACY_PROJECTION_REGISTRAR_HEADER) 6 else 7
    if (parts.size != expectedColumns) {
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
        guidSignature = parts.getOrNull(6).orEmpty(),
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
        writeBytesIfChanged(target, classWriter.toByteArray())
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
                            entry.guidSignature,
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
    writeBytesIfChanged(target, classWriter.toByteArray())
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

private fun String.splitListFieldOrNull(): List<String>? =
    splitSupportListFieldOrNull(',')

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
        parse = { _, line -> parse(line) },
    )

private fun <T> readRequiredTsvRows(
    path: Path,
    description: String,
    expectedHeader: Set<String>,
    parse: (String, String) -> T?,
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
                parse(actualHeader, line)
                    ?: throw IllegalArgumentException(
                        "kotlin-winrt compiler plugin could not parse $description row ${index + 2} in $path.",
                    )
            }
        }
        .toList()
}

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
