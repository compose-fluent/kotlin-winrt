package io.github.composefluent.winrt.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
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
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.org.objectweb.asm.ClassWriter
import org.jetbrains.org.objectweb.asm.Opcodes
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
        if (metadataIndexPath.isNullOrBlank()) {
            return
        }
        val compilerSupportEntries = readCompilerSupportManifest()
        writeCompilerSupportManifestClass(compilerSupportEntries)
        val winRtTypes = readAuthoringMetadataIndex(Path.of(metadataIndexPath))
        if (winRtTypes.isEmpty()) {
            return
        }
        val messageCollector = pluginContext.messageCollector
        val classContexts = moduleFragment.files
            .flatMap { file -> file.declarations.flatMap(::classContextsIn) }
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

    private fun readCompilerSupportManifest(): List<KotlinWinRtCompilerSupportManifestEntry> {
        val manifestPath = compilerSupportManifestPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return emptyList()
        if (!Files.isRegularFile(manifestPath)) {
            return emptyList()
        }
        return readCompilerSupportManifest(manifestPath)
    }

    private fun writeCompilerSupportManifestClass(entries: List<KotlinWinRtCompilerSupportManifestEntry>) {
        val outputDirectory = compilerSupportClassOutputDirectoryPath?.takeIf(String::isNotBlank)?.let(Path::of) ?: return
        if (entries.isEmpty()) {
            return
        }
        writeCompilerSupportManifestClass(entries, outputDirectory)
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
    val parts = line.split('\t')
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
