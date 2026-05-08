package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
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
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGetObject
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.org.objectweb.asm.Type
import java.nio.file.Files
import java.nio.file.Path

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCommandLineProcessor : CommandLineProcessor {
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
        val COMPILER_SUPPORT_MANIFEST_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support manifest")
        val COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY: CompilerConfigurationKey<String> =
            CompilerConfigurationKey("kotlin-winrt compiler support class output directory")
    }
}

@OptIn(ExperimentalCompilerApi::class)
class KotlinWinRtCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = KotlinWinRtCommandLineProcessor.PLUGIN_ID
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(
            KotlinWinRtIrGenerationExtension(
                metadataIndexPath = configuration.get(KotlinWinRtCommandLineProcessor.METADATA_INDEX_KEY),
                typeIndexOutputPath = configuration.get(KotlinWinRtCommandLineProcessor.TYPE_INDEX_OUTPUT_KEY),
                compilerSupportManifestPath = configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_MANIFEST_KEY),
                compilerSupportClassOutputDirectoryPath = configuration.get(KotlinWinRtCommandLineProcessor.COMPILER_SUPPORT_CLASS_OUTPUT_DIRECTORY_KEY),
            ),
        )
    }
}

class KotlinWinRtIrGenerationExtension(
    private val metadataIndexPath: String?,
    private val typeIndexOutputPath: String?,
    private val compilerSupportManifestPath: String?,
    private val compilerSupportClassOutputDirectoryPath: String?,
) : IrGenerationExtension {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun generate(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val compilerSupportEntries = readCompilerSupportManifest()
        writeCompilerSupportClasses(compilerSupportEntries)
        lowerProjectionIntrinsics(moduleFragment, pluginContext)
        if (metadataIndexPath.isNullOrBlank()) {
            return
        }
        val winRtTypes = readAuthoringMetadataIndex(Path.of(metadataIndexPath))
        if (winRtTypes.isEmpty()) {
            return
        }
        val messageCollector = pluginContext.messageCollector
        val generatedSourceRoot = generatedSourceRootFromMetadataIndex(metadataIndexPath)
        val classContexts = moduleFragment.files
            .asSequence()
            .filterNot { file -> isGeneratedSourceFile(file.fileEntry.name, generatedSourceRoot) }
            .flatMap { file -> file.declarations.asSequence().flatMap { declaration -> classContextsIn(declaration).asSequence() } }
            .toList()
        writeProjectionTypeIndex(classContexts, winRtTypes)
        classContexts.forEach { context ->
            val klass = context.klass
            if (klass.visibility != DescriptorVisibilities.PUBLIC || !context.containingTypesPublic) {
                return@forEach
            }
            val authoredType = authoredTypeFor(klass, winRtTypes) ?: return@forEach
            validateAuthoredType(klass, authoredType, pluginContext.afterK2) { message ->
                messageCollector.report(CompilerMessageSeverity.ERROR, message, null)
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    private fun lowerProjectionIntrinsics(
        moduleFragment: IrModuleFragment,
        pluginContext: IrPluginContext,
    ) {
        val intrinsicClassId = ClassId.topLevel(WINRT_PROJECTION_INTRINSIC_FQ_NAME)
        val helperReceivers = WINRT_PROJECTION_INTRINSIC_HELPERS.values
            .distinct()
            .mapNotNull { helperFqName ->
                val classId = ClassId.topLevel(helperFqName)
                pluginContext.referenceClass(classId)?.let { helperFqName to it }
            }
            .toMap()
        val intrinsicFunctions = WINRT_PROJECTION_INTRINSIC_FUNCTIONS.associateWith { functionName ->
            pluginContext.referenceFunctions(CallableId(intrinsicClassId, Name.identifier(functionName)))
                .singleOrNull()
        }
            .filterValues { symbol -> symbol != null }
        val helperFunctions = WINRT_PROJECTION_INTRINSIC_HELPERS
            .mapNotNull { (functionName, helperFqName) ->
                val classId = ClassId.topLevel(helperFqName)
                val symbol = pluginContext.referenceFunctions(CallableId(classId, Name.identifier(functionName)))
                    .singleOrNull()
                    ?: return@mapNotNull null
                functionName to symbol
            }
            .toMap()
        if (intrinsicFunctions.isEmpty() || helperFunctions.isEmpty()) {
            return
        }
        moduleFragment.transformChildrenVoid(
            object : IrElementTransformerVoidWithContext() {
                override fun visitCall(expression: IrCall): IrExpression {
                    val call = super.visitCall(expression) as IrCall
                    val intrinsicName = intrinsicFunctions.entries
                        .firstOrNull { (_, symbol) -> symbol == call.symbol }
                        ?.key
                        ?: return call
                    val helperSymbol = helperFunctions[intrinsicName] ?: return call
                    val helperReceiver = helperReceivers[WINRT_PROJECTION_INTRINSIC_HELPERS[intrinsicName]] ?: return call
                    val scope = currentScope?.scope?.scopeOwnerSymbol ?: return call
                    val builder = DeclarationIrBuilder(pluginContext, scope, call.startOffset, call.endOffset)
                    return builder.irCall(helperSymbol)
                        .apply {
                            arguments[0] = builder.irGetObject(helperReceiver)
                            for (index in 1 until call.arguments.size) {
                                arguments[index] = call.arguments[index]
                            }
                        }
                }
            },
        )
    }

    private fun readCompilerSupportManifest(): List<KotlinWinRtCompilerSupportManifestEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        if (!Files.isRegularFile(manifestPath)) {
            return emptyList()
        }
        return readCompilerSupportManifest(manifestPath)
    }

    private fun writeCompilerSupportClasses(entries: List<KotlinWinRtCompilerSupportManifestEntry>) {
        val outputDirectory = compilerSupportClassOutputDirectoryPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        if (entries.isEmpty()) {
            return
        }
        writeCompilerSupportManifestClass(entries, outputDirectory)
        writeProjectionRegistrarClass(
            entries = readProjectionRegistrarEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeEventProjectionRegistryClass(
            entries = readEventProjectionEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeGenericTypeInstantiationRegistryClass(
            entries = readGenericTypeInstantiationEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeGenericAbiRegistryArtifactClass(
            entries = readGenericAbiRegistryEntries(entries),
            outputDirectory = outputDirectory,
        )
        writeInterfaceNativeProjectionRegistryClass(
            entries = readInterfaceNativeProjectionEntries(entries),
            outputDirectory = outputDirectory,
        )
    }

    private fun readProjectionRegistrarEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtProjectionRegistrarEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "projection-registrar" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readProjectionRegistrarEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readEventProjectionEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtEventProjectionEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "event-source" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readEventProjectionEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readGenericTypeInstantiationEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericTypeInstantiationEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "generic-type-instantiation" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readGenericTypeInstantiationEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readGenericAbiRegistryEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtGenericAbiRegistryEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "generic-abi-registry" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readGenericAbiRegistryEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun readInterfaceNativeProjectionEntries(
        manifestEntries: List<KotlinWinRtCompilerSupportManifestEntry>,
    ): List<KotlinWinRtInterfaceNativeProjectionEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        val manifestDirectory = manifestPath.parent ?: return emptyList()
        return manifestEntries
            .asSequence()
            .filter { it.kind == "interface-native-projection" }
            .flatMap { entry ->
                val sourcePath = manifestDirectory.resolve(entry.sourceFile)
                if (Files.isRegularFile(sourcePath)) {
                    readInterfaceNativeProjectionEntries(sourcePath).asSequence()
                } else {
                    emptySequence()
                }
            }
            .toList()
    }

    private fun writeProjectionTypeIndex(
        classContexts: List<AuthoredIrClassContext>,
        winRtTypes: Map<String, IndexedWinRtType>,
    ) {
        val outputPath = typeIndexOutputPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        val records = classContexts
            .mapNotNull { context -> context.klass.fqNameWhenAvailable?.asString() }
            .mapNotNull { sourceTypeName -> projectionTypeIndexRecordForSourceType(sourceTypeName, winRtTypes) }
            .distinctBy(KotlinWinRtProjectionTypeIndexRecord::sourceTypeName)
            .sortedBy(KotlinWinRtProjectionTypeIndexRecord::sourceTypeName)
        outputPath.parent?.let(Files::createDirectories)
        Files.writeString(
            outputPath,
            records.joinToString(separator = "\n", postfix = if (records.isEmpty()) "" else "\n") { it.render() },
        )
    }

    private fun authoredTypeFor(
        klass: IrClass,
        winRtTypes: Map<String, IndexedWinRtType>,
    ): KotlinWinRtAuthoredTypeCandidate? {
        val sourceTypeName = klass.fqNameWhenAvailable?.asString() ?: return null
        if (sourceTypeName.startsWith(PROJECTION_PACKAGE_PREFIX) ||
            projectionPackageToMetadataName(sourceTypeName) in winRtTypes
        ) {
            return null
        }
        val resolvedWinRtTypes = klass.superTypes
            .mapNotNull { type -> type.classFqName?.asString() }
            .map(::projectionPackageToMetadataName)
            .mapNotNull(winRtTypes::get)
        if (resolvedWinRtTypes.isEmpty()) {
            return null
        }
        val packageName = sourceTypeName.substringBeforeLast('.', missingDelimiterValue = "")
        val className = sourceTypeName.substringAfterLast('.')
        val winRtBase = resolvedWinRtTypes.firstOrNull { type -> type.kind == "RuntimeClass" }
        val directInterfaces = resolvedWinRtTypes
            .filter { type -> type.kind == "Interface" }
            .map { type -> type.qualifiedName }
        val overridableInterfaces = winRtBase?.overridableInterfaces.orEmpty()
        return KotlinWinRtAuthoredTypeCandidate(
            packageName = packageName,
            className = className,
            sourceTypeName = sourceTypeName,
            winRtBaseClassName = winRtBase?.qualifiedName,
            winRtInterfaceNames = (directInterfaces + overridableInterfaces).distinct().sorted(),
            overridableInterfaceNames = overridableInterfaces.distinct().sorted(),
        )
    }

    private fun validateAuthoredType(
        klass: IrClass,
        authoredType: KotlinWinRtAuthoredTypeCandidate,
        afterK2: Boolean,
        report: (String) -> Unit,
    ) {
        if (!afterK2) {
            report("kotlin-winrt authoring requires K2 semantic analysis for ${authoredType.sourceTypeName}.")
        }
        if (klass.isInner) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be an inner class.")
        }
        if (klass.typeParameters.isNotEmpty()) {
            report("WinRT authored type ${authoredType.sourceTypeName} must not be generic.")
        }
        if (klass.kind == ClassKind.CLASS && klass.modality != Modality.FINAL) {
            report("WinRT authored class ${authoredType.sourceTypeName} must be final.")
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun classContextsIn(
        declaration: IrDeclaration,
        containingTypesPublic: Boolean = true,
    ): List<AuthoredIrClassContext> =
        when (declaration) {
            is IrClass -> {
                val nestedContainingTypesPublic = containingTypesPublic && declaration.visibility == DescriptorVisibilities.PUBLIC
                listOf(AuthoredIrClassContext(declaration, containingTypesPublic)) +
                    declaration.declarations.flatMap { child -> classContextsIn(child, nestedContainingTypesPublic) }
            }
            else -> emptyList()
        }

    private data class AuthoredIrClassContext(
        val klass: IrClass,
        val containingTypesPublic: Boolean,
    )
}

private val WINRT_PROJECTION_INTRINSIC_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRtProjectionIntrinsic")

private val WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRtInstanceProjectionInterop")

private val WINRT_STATIC_PROJECTION_INTEROP_FQ_NAME =
    FqName("io.github.composefluent.winrt.runtime.WinRtStaticProjectionInterop")

private val WINRT_PROJECTION_INTRINSIC_HELPERS = linkedMapOf(
    "staticGetArray" to WINRT_STATIC_PROJECTION_INTEROP_FQ_NAME,
    "staticGetArrayWithProjectedObject" to WINRT_STATIC_PROJECTION_INTEROP_FQ_NAME,
    "staticCallProjectedRuntimeClassWithString" to WINRT_STATIC_PROJECTION_INTEROP_FQ_NAME,
    "staticCallProjectedInterfaceWithString" to WINRT_STATIC_PROJECTION_INTEROP_FQ_NAME,
    "invokeUnit" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getString" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getBoolean" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getInt32" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getUInt32" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getInt64" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getUInt64" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getFloat" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getDouble" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getStruct" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "getArray" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setStruct" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setString" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setBoolean" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setInt32" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setUInt32" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setInt64" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setUInt64" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setFloat" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "setDouble" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "callUnitWithFloatAndString" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "callUnitWithStringAndFloat" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
    "callUnitWithFloatStringAndProjectedObject" to WINRT_INSTANCE_PROJECTION_INTEROP_FQ_NAME,
)

private val WINRT_PROJECTION_INTRINSIC_FUNCTIONS = WINRT_PROJECTION_INTRINSIC_HELPERS.keys.toList()

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
    return normalizedFileName == root || normalizedFileName.startsWith("$root/")
}

private fun String.normalizedCompilerPathPrefix(): String =
    replace('\\', '/')
        .trimEnd('/')
        .lowercase()

data class KotlinWinRtCompilerSupportManifestEntry(
    val kind: String,
    val className: String,
    val sourceFile: String,
    val entries: Int,
)

fun readCompilerSupportManifest(path: Path): List<KotlinWinRtCompilerSupportManifestEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseCompilerSupportManifestLine)
        .toList()

private fun parseCompilerSupportManifestLine(line: String): KotlinWinRtCompilerSupportManifestEntry? {
    val parts = line.split('\t', limit = 4)
    if (parts.size < 4) {
        return null
    }
    val entries = parts[3].toIntOrNull() ?: return null
    return KotlinWinRtCompilerSupportManifestEntry(
        kind = parts[0],
        className = parts[1],
        sourceFile = parts[2],
        entries = entries,
    )
}

private const val COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTCompilerSupportManifest"

private const val PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTProjectionRegistrar"

private const val EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTEventProjectionRegistry"

private const val GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiationRegistry"

private const val GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTGenericAbiRegistryArtifact"

private const val INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME: String =
    "io/github/composefluent/winrt/projections/support/WinRTInterfaceProjectionRegistry"

private const val PROJECTION_REGISTRAR_CHUNK_SIZE: Int = 128

private const val EVENT_PROJECTION_REGISTRY_CHUNK_SIZE: Int = 96

private const val GENERIC_TYPE_INSTANTIATION_REGISTRY_CHUNK_SIZE: Int = 96

private const val INTERFACE_NATIVE_PROJECTION_REGISTRY_CHUNK_SIZE: Int = 96

private const val GENERIC_ABI_REGISTRY_LIST_SEPARATOR: String = "\u001F"

fun writeCompilerSupportManifestClass(
    entries: List<KotlinWinRtCompilerSupportManifestEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(0)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("compiler-support.tsv", null)
    classWriter.addIntConstantField("ENTRY_COUNT", entries.size)
    entries
        .groupBy(KotlinWinRtCompilerSupportManifestEntry::kind)
        .toSortedMap()
        .forEach { (kind, kindEntries) ->
            classWriter.addIntConstantField("${compilerSupportFieldPrefix(kind)}_ENTRIES", kindEntries.sumOf { it.entries })
        }
    classWriter.addDefaultConstructor()
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$COMPILER_SUPPORT_MANIFEST_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

data class KotlinWinRtProjectionRegistrarEntry(
    val kotlinClassName: String,
    val projectedTypeName: String,
    val kind: String,
    val baseTypeName: String,
    val metadataClassName: String,
)

fun readProjectionRegistrarEntries(path: Path): List<KotlinWinRtProjectionRegistrarEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseProjectionRegistrarLine)
        .toList()

private fun parseProjectionRegistrarLine(line: String): KotlinWinRtProjectionRegistrarEntry? {
    val parts = line.split('\t', limit = 5)
    if (parts.size < 5) {
        return null
    }
    return KotlinWinRtProjectionRegistrarEntry(
        kotlinClassName = parts[0],
        projectedTypeName = parts[1],
        kind = parts[2],
        baseTypeName = parts[3],
        metadataClassName = parts[4],
    )
}

fun writeProjectionRegistrarClass(
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("projection-registrar.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(PROJECTION_REGISTRAR_CHUNK_SIZE)
    val register = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "register",
        "()V",
        null,
        null,
    )
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME,
            projectionRegistrarChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()

    chunks.forEachIndexed { index, chunk ->
        classWriter.addProjectionRegistrarChunk(projectionRegistrarChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$PROJECTION_REGISTRAR_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun projectionRegistrarChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addProjectionRegistrarChunk(
    name: String,
    entries: List<KotlinWinRtProjectionRegistrarEntry>,
) {
    val method = visitMethod(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
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
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            "io/github/composefluent/winrt/runtime/CompilerGeneratedProjectionTypeIndexesKt",
            "registerGeneratedProjectionTypeIndex",
            "(Lkotlin/reflect/KClass;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
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

data class KotlinWinRtEventProjectionEntry(
    val eventType: String,
    val ownerType: String,
    val sourceClass: String,
    val abiEventType: String,
    val genericArguments: List<String>,
    val usesSharedEventHandlerSource: Boolean,
    val interfaceId: String,
    val parameterKinds: List<String>,
    val returnKind: String,
    val parameterTypeNames: List<String>,
)

fun readEventProjectionEntries(path: Path): List<KotlinWinRtEventProjectionEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseEventProjectionLine)
        .toList()

private fun parseEventProjectionLine(line: String): KotlinWinRtEventProjectionEntry? {
    val parts = line.split('\t', limit = 10)
    if (parts.size < 6) {
        return null
    }
    return KotlinWinRtEventProjectionEntry(
        eventType = parts[0],
        ownerType = parts[1],
        sourceClass = parts[2],
        abiEventType = parts[3],
        genericArguments = parts[4].split(',').filter(String::isNotBlank),
        usesSharedEventHandlerSource = parts[5].toBooleanStrictOrNull() ?: false,
        interfaceId = parts.getOrNull(6).orEmpty(),
        parameterKinds = parts.getOrNull(7).orEmpty().split(',').filter(String::isNotBlank),
        returnKind = parts.getOrNull(8).orEmpty().ifBlank { "UNIT" },
        parameterTypeNames = parts.getOrNull(9).orEmpty().split(',').filter(String::isNotBlank),
    )
}

fun writeEventProjectionRegistryClass(
    entries: List<KotlinWinRtEventProjectionEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("event-sources.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(EVENT_PROJECTION_REGISTRY_CHUNK_SIZE)
    val register = classWriter.visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "register",
        "()V",
        null,
        null,
    )
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
            eventProjectionRegistryChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()
    chunks.forEachIndexed { index, chunk ->
        classWriter.addEventProjectionRegistryChunk(eventProjectionRegistryChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$EVENT_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun eventProjectionRegistryChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addEventProjectionRegistryChunk(
    name: String,
    entries: List<KotlinWinRtEventProjectionEntry>,
) {
    val method = visitMethod(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC,
        name,
        "()V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "INSTANCE",
            "Lio/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations;",
        )
        method.visitLdcInsn(entry.eventType)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeBySourceType",
            "(Ljava/lang/String;)V",
            false,
        )
        method.addEventSourceDescriptor(entry, withFactory = false)
        method.visitVarInsn(Opcodes.ASTORE, 0)
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime;",
        )
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/WinRtGeneratedEventSourceRuntime",
            "createEventSourceFactory",
            "(Lio/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor;)Lkotlin/jvm/functions/Function2;",
            false,
        )
        method.visitVarInsn(Opcodes.ASTORE, 1)
        method.addEventSourceDescriptor(entry, withFactory = true)
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/WinRtEventSourceRuntime",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/WinRtEventSourceRuntime;",
        )
        method.visitInsn(Opcodes.SWAP)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/WinRtEventSourceRuntime",
            "registerEventSource",
            "(Lio/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addEventSourceDescriptor(
    entry: KotlinWinRtEventProjectionEntry,
    withFactory: Boolean,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.eventType)
    visitLdcInsn(entry.ownerType)
    visitLdcInsn(entry.sourceClass)
    visitLdcInsn(entry.abiEventType)
    addStringList(entry.genericArguments)
    visitInsn(if (entry.usesSharedEventHandlerSource) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    addNullableGuid(entry.interfaceId)
    addWinRtDelegateValueKindList(entry.parameterKinds)
    addWinRtDelegateValueKind(entry.returnKind)
    addStringList(entry.parameterTypeNames)
    if (withFactory) {
        visitVarInsn(Opcodes.ALOAD, 1)
    } else {
        visitInsn(Opcodes.ACONST_NULL)
    }
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/WinRtEventSourceDescriptor",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;ZLio/github/composefluent/winrt/runtime/Guid;Ljava/util/List;Lio/github/composefluent/winrt/runtime/WinRtDelegateValueKind;Ljava/util/List;Lkotlin/jvm/functions/Function2;)V",
        false,
    )
}

data class KotlinWinRtGenericTypeInstantiationEntry(
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

fun readGenericTypeInstantiationEntries(path: Path): List<KotlinWinRtGenericTypeInstantiationEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseGenericTypeInstantiationLine)
        .toList()

private fun parseGenericTypeInstantiationLine(line: String): KotlinWinRtGenericTypeInstantiationEntry? {
    val parts = line.split('\t', limit = 9)
    if (parts.size < 9) {
        return null
    }
    return KotlinWinRtGenericTypeInstantiationEntry(
        className = parts[0],
        sourceType = parts[1],
        isDelegate = parts[2].toBooleanStrictOrNull() ?: false,
        rcwFunctions = parts[3].splitListField(),
        vtableFunctions = parts[4].splitListField(),
        propertyAccessors = parts[5].splitListField(),
        genericReturnOnlyRcwFunctions = parts[6].splitListField(),
        projectedGenericFallbacks = parts[7].splitListField(),
        dependencies = parts[8].splitListField(),
    )
}

private fun String.splitListField(): List<String> =
    split(',').filter(String::isNotBlank)

fun writeGenericTypeInstantiationRegistryClass(
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("generic-instantiations.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(GENERIC_TYPE_INSTANTIATION_REGISTRY_CHUNK_SIZE)
    classWriter.addGenericRegistryDispatcher("initializeAll", "()V", chunks.indices) { method, index ->
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
            genericRegistryChunkName(index),
            "()V",
            false,
        )
    }
    classWriter.addGenericRegistryDispatcher("initializeBySourceType", "(Ljava/lang/String;)V", chunks.indices) { method, index ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME,
            genericRegistrySourceChunkName(index),
            "(Ljava/lang/String;)V",
            false,
        )
    }
    chunks.forEachIndexed { index, chunk ->
        classWriter.addGenericRegistryChunk(genericRegistryChunkName(index), chunk)
        classWriter.addGenericRegistrySourceChunk(genericRegistrySourceChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$GENERIC_TYPE_INSTANTIATION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addGenericRegistryDispatcher(
    name: String,
    descriptor: String,
    indices: IntRange,
    callChunk: (org.jetbrains.org.objectweb.asm.MethodVisitor, Int) -> Unit,
) {
    val method = visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, name, descriptor, null, null)
    method.visitCode()
    indices.forEach { index -> callChunk(method, index) }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun genericRegistryChunkName(index: Int): String =
    "initializeAllChunk${index.toString().padStart(3, '0')}"

private fun genericRegistrySourceChunkName(index: Int): String =
    "initializeBySourceTypeChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addGenericRegistryChunk(
    name: String,
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "()V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.addGenericInstantiationEntry(entry)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeEntry",
            "(Lio/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry;)V",
            false,
        )
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericRegistrySourceChunk(
    name: String,
    entries: List<KotlinWinRtGenericTypeInstantiationEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "(Ljava/lang/String;)V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitLdcInsn(entry.sourceType)
        method.visitMethodInsn(Opcodes.INVOKESTATIC, "kotlin/jvm/internal/Intrinsics", "areEqual", "(Ljava/lang/Object;Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.addGenericInstantiationEntry(entry)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
            "initializeEntry",
            "(Lio/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry;)V",
            false,
        )
        method.visitInsn(Opcodes.RETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addGenericInstantiationEntry(entry: KotlinWinRtGenericTypeInstantiationEntry) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        "io/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations",
        "INSTANCE",
        "Lio/github/composefluent/winrt/projections/support/WinRTGenericTypeInstantiations;",
    )
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.className)
    visitLdcInsn(entry.sourceType)
    visitInsn(if (entry.isDelegate) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    addStringList(entry.rcwFunctions)
    addStringList(entry.vtableFunctions)
    addStringList(entry.propertyAccessors)
    addStringList(entry.genericReturnOnlyRcwFunctions)
    addStringList(entry.projectedGenericFallbacks)
    addStringList(entry.dependencies)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/projections/support/GenericTypeInstantiationEntry",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;ZLjava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;Ljava/util/List;)V",
        false,
    )
}

data class KotlinWinRtGenericAbiRegistryEntry(
    val kind: String,
    val name: String,
    val sourceGenericType: String,
    val operation: String,
    val declaration: String,
    val abiParameterTypes: List<String>,
    val typeArrayShape: List<String>,
)

fun readGenericAbiRegistryEntries(path: Path): List<KotlinWinRtGenericAbiRegistryEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseGenericAbiRegistryLine)
        .toList()

private fun parseGenericAbiRegistryLine(line: String): KotlinWinRtGenericAbiRegistryEntry? {
    val parts = line.split('\t', limit = 7)
    if (parts.size < 7) {
        return null
    }
    return KotlinWinRtGenericAbiRegistryEntry(
        kind = parts[0],
        name = parts[1],
        sourceGenericType = parts[2],
        operation = parts[3],
        declaration = parts[4],
        abiParameterTypes = parts[5].splitGenericAbiRegistryListField(),
        typeArrayShape = parts[6].splitGenericAbiRegistryListField(),
    )
}

private fun String.splitGenericAbiRegistryListField(): List<String> =
    split(GENERIC_ABI_REGISTRY_LIST_SEPARATOR).filter(String::isNotBlank)

fun writeGenericAbiRegistryArtifactClass(
    entries: List<KotlinWinRtGenericAbiRegistryEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("generic-abi-registry.tsv", null)
    classWriter.addDefaultConstructor()
    val delegates = entries.filter { it.kind == "delegate" }
    val derivedInterfaces = entries.filter { it.kind == "derived-interface" }.map { it.name }
    classWriter.addGenericAbiDelegateNamedMethod(delegates)
    classWriter.addGenericAbiDelegatesForSourceTypeMethod(delegates)
    classWriter.addGenericAbiIsDerivedGenericInterfaceMethod(derivedInterfaces)
    classWriter.addGenericAbiRegisterDelegatesMethod(delegates)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$GENERIC_ABI_REGISTRY_ARTIFACT_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addGenericAbiDelegateNamedMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "delegateNamed",
        "(Ljava/lang/String;)Lio/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry;",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitLdcInsn(entry.name)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.addGenericAbiDelegateEntry(entry)
        method.visitInsn(Opcodes.ARETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.ACONST_NULL)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiDelegatesForSourceTypeMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "delegatesForSourceType",
        "(Ljava/lang/String;)Ljava/util/List;",
        null,
        null,
    )
    method.visitCode()
    method.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList")
    method.visitInsn(Opcodes.DUP)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false)
    method.visitVarInsn(Opcodes.ASTORE, 1)
    entries.forEach { entry ->
        method.visitLdcInsn(entry.sourceGenericType)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.visitVarInsn(Opcodes.ALOAD, 1)
        method.addGenericAbiDelegateEntry(entry)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false)
        method.visitInsn(Opcodes.POP)
        method.visitLabel(next)
    }
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiIsDerivedGenericInterfaceMethod(typeNames: List<String>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "isDerivedGenericInterface",
        "(Ljava/lang/String;)Z",
        null,
        null,
    )
    method.visitCode()
    typeNames.forEach { typeName ->
        method.visitLdcInsn(typeName)
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "equals", "(Ljava/lang/Object;)Z", false)
        val next = org.jetbrains.org.objectweb.asm.Label()
        method.visitJumpInsn(Opcodes.IFEQ, next)
        method.visitInsn(Opcodes.ICONST_1)
        method.visitInsn(Opcodes.IRETURN)
        method.visitLabel(next)
    }
    method.visitInsn(Opcodes.ICONST_0)
    method.visitInsn(Opcodes.IRETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addGenericAbiRegisterDelegatesMethod(entries: List<KotlinWinRtGenericAbiRegistryEntry>) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
        "registerAbiDelegates",
        "(Lkotlin/jvm/functions/Function2;)V",
        null,
        null,
    )
    method.visitCode()
    entries.forEach { entry ->
        method.visitVarInsn(Opcodes.ALOAD, 0)
        method.addStringList(entry.typeArrayShape)
        method.visitLdcInsn(entry.name)
        method.visitMethodInsn(
            Opcodes.INVOKEINTERFACE,
            "kotlin/jvm/functions/Function2",
            "invoke",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
            true,
        )
        method.visitInsn(Opcodes.POP)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addGenericAbiDelegateEntry(
    entry: KotlinWinRtGenericAbiRegistryEntry,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(entry.name)
    visitLdcInsn(entry.sourceGenericType)
    visitLdcInsn(entry.operation)
    visitLdcInsn(entry.declaration)
    addStringList(entry.abiParameterTypes)
    addStringList(entry.typeArrayShape)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/projections/support/GenericAbiDelegateEntry",
        "<init>",
        "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V",
        false,
    )
}

data class KotlinWinRtInterfaceNativeProjectionEntry(
    val projectedTypeName: String,
    val kotlinInterfaceClassName: String,
    val implementationClassName: String,
    val interfaceId: String,
    val memberCount: Int,
    val members: List<KotlinWinRtInterfaceNativeProjectionMemberEntry> = emptyList(),
)

data class KotlinWinRtInterfaceNativeProjectionMemberEntry(
    val kind: String,
    val jvmName: String,
    val slot: Int,
    val returnKind: String,
    val parameterKinds: List<String>,
    val suppressHResultCheck: Boolean,
    val eventTypeName: String = "",
    val ownerTypeName: String = "",
)

fun readInterfaceNativeProjectionEntries(path: Path): List<KotlinWinRtInterfaceNativeProjectionEntry> =
    Files.readAllLines(path)
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull(::parseInterfaceNativeProjectionLine)
        .toList()

private fun parseInterfaceNativeProjectionLine(line: String): KotlinWinRtInterfaceNativeProjectionEntry? {
    val parts = line.split('\t', limit = 6)
    if (parts.size < 5) {
        return null
    }
    val memberCount = parts[4].toIntOrNull() ?: return null
    val members = parts.getOrElse(5) { "" }
        .split(';')
        .filter(String::isNotBlank)
        .mapNotNull(::parseInterfaceNativeProjectionMember)
    return KotlinWinRtInterfaceNativeProjectionEntry(
        projectedTypeName = parts[0],
        kotlinInterfaceClassName = parts[1],
        implementationClassName = parts[2],
        interfaceId = parts[3],
        memberCount = memberCount,
        members = members,
    )
}

private fun parseInterfaceNativeProjectionMember(value: String): KotlinWinRtInterfaceNativeProjectionMemberEntry? {
    val parts = value.split('|', limit = 8)
    if (parts.size < 6) {
        return null
    }
    return KotlinWinRtInterfaceNativeProjectionMemberEntry(
        kind = parts[0],
        jvmName = parts[1],
        slot = parts[2].toIntOrNull() ?: return null,
        returnKind = parts[3],
        parameterKinds = parts[4].split(',').filter(String::isNotBlank),
        suppressHResultCheck = parts[5].toBooleanStrictOrNull() ?: false,
        eventTypeName = parts.getOrElse(6) { "" },
        ownerTypeName = parts.getOrElse(7) { "" },
    )
}

fun writeInterfaceNativeProjectionRegistryClass(
    entries: List<KotlinWinRtInterfaceNativeProjectionEntry>,
    outputDirectory: Path,
) {
    if (entries.isEmpty()) {
        return
    }
    entries.forEach { entry ->
        if (entry.memberCount == 0) {
            writeInterfaceNativeProjectionImplementationClass(entry, outputDirectory)
        }
        writeInterfaceNativeProjectionFactoryClass(entry, outputDirectory)
    }

    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
        null,
        "java/lang/Object",
        null,
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.addDefaultConstructor()
    val chunks = entries.chunked(INTERFACE_NATIVE_PROJECTION_REGISTRY_CHUNK_SIZE)
    val register = classWriter.visitMethod(Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC, "register", "()V", null, null)
    register.visitCode()
    chunks.indices.forEach { index ->
        register.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME,
            interfaceNativeProjectionRegistryChunkName(index),
            "()V",
            false,
        )
    }
    register.visitInsn(Opcodes.RETURN)
    register.visitMaxs(0, 0)
    register.visitEnd()
    chunks.forEachIndexed { index, chunk ->
        classWriter.addInterfaceNativeProjectionRegistryChunk(interfaceNativeProjectionRegistryChunkName(index), chunk)
    }
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$INTERFACE_NATIVE_PROJECTION_REGISTRY_CLASS_INTERNAL_NAME.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun interfaceNativeProjectionRegistryChunkName(index: Int): String =
    "registerChunk${index.toString().padStart(3, '0')}"

private fun ClassWriter.addInterfaceNativeProjectionRegistryChunk(
    name: String,
    entries: List<KotlinWinRtInterfaceNativeProjectionEntry>,
) {
    val method = visitMethod(Opcodes.ACC_PRIVATE or Opcodes.ACC_STATIC, name, "()V", null, null)
    method.visitCode()
    entries.forEach { entry ->
        method.visitFieldInsn(
            Opcodes.GETSTATIC,
            "io/github/composefluent/winrt/runtime/ComWrappersSupport",
            "INSTANCE",
            "Lio/github/composefluent/winrt/runtime/ComWrappersSupport;",
        )
        method.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
        val factoryInternalName = interfaceNativeProjectionFactoryInternalName(entry)
        method.visitTypeInsn(Opcodes.NEW, factoryInternalName)
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, factoryInternalName, "<init>", "()V", false)
        method.visitMethodInsn(
            Opcodes.INVOKEVIRTUAL,
            "io/github/composefluent/winrt/runtime/ComWrappersSupport",
            "registerInterfaceProjectionFactory",
            "(Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;Lkotlin/jvm/functions/Function1;)Z",
            false,
        )
        method.visitInsn(Opcodes.POP)
    }
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun writeInterfaceNativeProjectionImplementationClass(
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
    outputDirectory: Path,
) {
    val implementationInternalName = entry.implementationClassName.toInternalName()
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        implementationInternalName,
        null,
        "java/lang/Object",
        arrayOf(entry.kotlinInterfaceClassName.toInternalName(), "io/github/composefluent/winrt/runtime/IWinRTObject"),
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.visitField(
        Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
        null,
        null,
    ).visitEnd()
    classWriter.addInterfaceNativeProjectionConstructor(implementationInternalName)
    classWriter.addInterfaceNativeProjectionNativeObjectGetter(implementationInternalName)
    classWriter.addInterfaceNativeProjectionPrimaryTypeHandleGetter(entry)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$implementationInternalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addInterfaceNativeProjectionConstructor(ownerInternalName: String) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "<init>",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)V",
        null,
        null,
    )
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitVarInsn(Opcodes.ALOAD, 1)
    method.visitFieldInsn(
        Opcodes.PUTFIELD,
        ownerInternalName,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
    )
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionNativeObjectGetter(ownerInternalName: String) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "getNativeObject",
        "()Lio/github/composefluent/winrt/runtime/ComObjectReference;",
        null,
        null,
    )
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitFieldInsn(
        Opcodes.GETFIELD,
        ownerInternalName,
        "nativeObject",
        "Lio/github/composefluent/winrt/runtime/ComObjectReference;",
    )
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionPrimaryTypeHandleGetter(entry: KotlinWinRtInterfaceNativeProjectionEntry) {
    val method = visitMethod(
        Opcodes.ACC_PUBLIC,
        "getPrimaryTypeHandle",
        "()Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;",
        null,
        null,
    )
    method.visitCode()
    method.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
    method.visitInsn(Opcodes.ARETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun writeInterfaceNativeProjectionFactoryClass(
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
    outputDirectory: Path,
) {
    val factoryInternalName = interfaceNativeProjectionFactoryInternalName(entry)
    val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
    classWriter.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC or Opcodes.ACC_FINAL or Opcodes.ACC_SUPER,
        factoryInternalName,
        null,
        "java/lang/Object",
        arrayOf("kotlin/jvm/functions/Function1"),
    )
    classWriter.visitSource("interface-native-projections.tsv", null)
    classWriter.addPublicDefaultConstructor()
    classWriter.addInterfaceNativeProjectionFactoryInvoke(factoryInternalName, entry)
    classWriter.visitEnd()

    val target = outputDirectory.resolve("$factoryInternalName.class")
    Files.createDirectories(target.parent)
    Files.write(target, classWriter.toByteArray())
}

private fun ClassWriter.addPublicDefaultConstructor() {
    val method = visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
    method.visitCode()
    method.visitVarInsn(Opcodes.ALOAD, 0)
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
    method.visitInsn(Opcodes.RETURN)
    method.visitMaxs(0, 0)
    method.visitEnd()
}

private fun ClassWriter.addInterfaceNativeProjectionFactoryInvoke(
    ownerInternalName: String,
    entry: KotlinWinRtInterfaceNativeProjectionEntry,
) {
    val implementationInternalName = entry.implementationClassName.toInternalName()
    val typedInvoke = visitMethod(
        Opcodes.ACC_PUBLIC,
        "invoke",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)Ljava/lang/Object;",
        null,
        null,
    )
    typedInvoke.visitCode()
    typedInvoke.visitLdcInsn(Type.getObjectType(entry.kotlinInterfaceClassName.toInternalName()))
    typedInvoke.addWinRtTypeHandle(entry.projectedTypeName, entry.interfaceId)
    typedInvoke.visitVarInsn(Opcodes.ALOAD, 1)
    typedInvoke.addInterfaceNativeProjectionMemberList(entry.members)
    typedInvoke.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "io/github/composefluent/winrt/runtime/WinRtGeneratedInterfaceProjectionRuntime",
        "create",
        "(Ljava/lang/Class;Lio/github/composefluent/winrt/runtime/WinRtTypeHandle;Lio/github/composefluent/winrt/runtime/IUnknownReference;Ljava/util/List;)Ljava/lang/Object;",
        false,
    )
    typedInvoke.visitInsn(Opcodes.ARETURN)
    typedInvoke.visitMaxs(0, 0)
    typedInvoke.visitEnd()

    val bridge = visitMethod(
        Opcodes.ACC_PUBLIC or Opcodes.ACC_BRIDGE or Opcodes.ACC_SYNTHETIC,
        "invoke",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        null,
        null,
    )
    bridge.visitCode()
    bridge.visitVarInsn(Opcodes.ALOAD, 0)
    bridge.visitVarInsn(Opcodes.ALOAD, 1)
    bridge.visitTypeInsn(Opcodes.CHECKCAST, "io/github/composefluent/winrt/runtime/IUnknownReference")
    bridge.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        ownerInternalName,
        "invoke",
        "(Lio/github/composefluent/winrt/runtime/IUnknownReference;)Ljava/lang/Object;",
        false,
    )
    bridge.visitInsn(Opcodes.ARETURN)
    bridge.visitMaxs(0, 0)
    bridge.visitEnd()
}

private fun interfaceNativeProjectionFactoryInternalName(entry: KotlinWinRtInterfaceNativeProjectionEntry): String =
    "${entry.implementationClassName}\$Factory".toInternalName()

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addInterfaceNativeProjectionMemberList(
    members: List<KotlinWinRtInterfaceNativeProjectionMemberEntry>,
) {
    pushInt(members.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    members.forEachIndexed { index, member ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addInterfaceNativeProjectionMember(member)
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

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addInterfaceNativeProjectionMember(
    member: KotlinWinRtInterfaceNativeProjectionMemberEntry,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberDescriptor")
    visitInsn(Opcodes.DUP)
    addEnumValue(
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberKind",
        member.kind,
    )
    visitLdcInsn(member.jvmName)
    pushInt(member.slot)
    addEnumValue(
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind",
        member.returnKind,
    )
    addValueKindList(member.parameterKinds)
    visitInsn(if (member.suppressHResultCheck) Opcodes.ICONST_1 else Opcodes.ICONST_0)
    visitLdcInsn(member.eventTypeName)
    visitLdcInsn(member.ownerTypeName)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberDescriptor",
        "<init>",
        "(Lio/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionMemberKind;Ljava/lang/String;ILio/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind;Ljava/util/List;ZLjava/lang/String;Ljava/lang/String;)V",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addValueKindList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addEnumValue("io/github/composefluent/winrt/runtime/GeneratedInterfaceProjectionValueKind", value)
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

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addEnumValue(
    enumInternalName: String,
    constantName: String,
) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        enumInternalName,
        constantName,
        "L$enumInternalName;",
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtTypeHandle(
    projectedTypeName: String,
    interfaceId: String,
) {
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/WinRtTypeHandle")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(projectedTypeName)
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/Guid")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(interfaceId)
    visitMethodInsn(Opcodes.INVOKESPECIAL, "io/github/composefluent/winrt/runtime/Guid", "<init>", "(Ljava/lang/String;)V", false)
    visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "io/github/composefluent/winrt/runtime/WinRtTypeHandle",
        "<init>",
        "(Ljava/lang/String;Lio/github/composefluent/winrt/runtime/Guid;)V",
        false,
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addNullableGuid(value: String) {
    if (value.isBlank()) {
        visitInsn(Opcodes.ACONST_NULL)
        return
    }
    visitTypeInsn(Opcodes.NEW, "io/github/composefluent/winrt/runtime/Guid")
    visitInsn(Opcodes.DUP)
    visitLdcInsn(value)
    visitMethodInsn(Opcodes.INVOKESPECIAL, "io/github/composefluent/winrt/runtime/Guid", "<init>", "(Ljava/lang/String;)V", false)
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtDelegateValueKind(value: String) {
    visitFieldInsn(
        Opcodes.GETSTATIC,
        "io/github/composefluent/winrt/runtime/WinRtDelegateValueKind",
        value.ifBlank { "UNIT" },
        "Lio/github/composefluent/winrt/runtime/WinRtDelegateValueKind;",
    )
}

private fun org.jetbrains.org.objectweb.asm.MethodVisitor.addWinRtDelegateValueKindList(values: List<String>) {
    pushInt(values.size)
    visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object")
    values.forEachIndexed { index, value ->
        visitInsn(Opcodes.DUP)
        pushInt(index)
        addWinRtDelegateValueKind(value)
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
