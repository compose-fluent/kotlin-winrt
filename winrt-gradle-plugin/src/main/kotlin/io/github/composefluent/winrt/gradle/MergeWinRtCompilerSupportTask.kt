package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

@CacheableTask
abstract class MergeWinRtCompilerSupportTask : DefaultTask() {
    init {
        emitXamlComponentResourceSources.convention(false)
    }

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val localCompilerSupportManifest: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dependencyIdentityFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val emitXamlComponentResourceSources: Property<Boolean>

    @TaskAction
    fun merge() {
        val outputRoot = outputDirectory.get().asFile.toPath()
        cleanDirectory(outputRoot)
        Files.createDirectories(outputRoot)
        val manifests = buildList {
            localCompilerSupportManifest.orNull?.asFile?.takeIf(File::isFile)?.let(::add)
            dependencyIdentityFiles.files
                .flatMap(::readCompilerSupportManifests)
                .map(::File)
                .filter(File::isFile)
                .forEach(::add)
        }.distinctBy { it.absolutePath }
        val sourceRows = linkedMapOf<CompilerSupportSourceKey, MutableList<String>>()
        val sourceFileRows = linkedMapOf<String, MutableList<String>>()
        manifests.forEach { manifest ->
            val manifestRoot = manifest.toPath().parent ?: return@forEach
            readCompilerSupportRows(manifest).forEach { row ->
                row.rejectRetiredRuntimeDiscoveryKind(manifest)
                val source = manifestRoot.resolve(row.sourceFile)
                if (!Files.isRegularFile(source)) {
                    return@forEach
                }
                val lines = Files.readAllLines(source)
                if (lines.isEmpty()) {
                    return@forEach
                }
                val key = CompilerSupportSourceKey(row.kind, row.className, source.name)
                val targetRows = sourceRows.getOrPut(key) { mutableListOf(lines.first()) }
                targetRows += lines.asSequence().drop(1).filter(String::isNotBlank)
                val mergedRows = sourceFileRows.getOrPut(source.name) { mutableListOf(lines.first()) }
                mergedRows += lines.asSequence().drop(1).filter(String::isNotBlank)
            }
        }
        val sourceFileEntryCounts = sourceFileRows.mapValues { (_, lines) ->
            lines.drop(1).distinct().size
        }
        val manifestRows = mutableListOf("kind\tclassName\tsourceFile\tentries")
        sourceRows.toSortedMap(compareBy<CompilerSupportSourceKey> { it.kind }.thenBy { it.className }.thenBy { it.sourceFile })
            .forEach { (key, lines) ->
                manifestRows += listOf(
                    key.kind,
                    key.className,
                    key.sourceFile,
                    sourceFileEntryCounts.getValue(key.sourceFile).toString(),
                ).joinToString("\t")
            }
        sourceFileRows.toSortedMap()
            .forEach { (sourceFile, lines) ->
                val distinctLines = lines.take(1) + lines.drop(1).distinct()
                Files.writeString(outputRoot.resolve(sourceFile), distinctLines.joinToString(separator = "\n", postfix = "\n"))
            }
        Files.writeString(outputRoot.resolve("compiler-support.tsv"), manifestRows.joinToString(separator = "\n", postfix = "\n"))
        if (emitXamlComponentResourceSources.get()) {
            writeWinUiXamlComponentResourcesSource(outputRoot, sourceRows)
        }
    }

    private fun cleanDirectory(path: Path) {
        if (!Files.exists(path)) {
            return
        }
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }
    }
}

private fun writeWinUiXamlComponentResourcesSource(
    outputRoot: Path,
    sourceRows: Map<CompilerSupportSourceKey, List<String>>,
) {
    val runtimeClassNames = sourceRows
        .filterKeys { key -> key.kind == "xaml-component-resource" && key.sourceFile == "xaml-component-resources.tsv" }
        .values
        .asSequence()
        .flatMap { lines -> lines.asSequence().drop(1) }
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
        .toList()
    if (runtimeClassNames.isEmpty()) {
        return
    }
    val target = outputRoot.resolve("io/github/composefluent/winrt/projections/support/WinUiXamlComponentResources.kt")
    Files.createDirectories(target.parent)
    Files.writeString(
        target,
        buildString {
            appendLine("// Deterministic merged WinUI component XAML resource bootstrap.")
            appendLine("package io.github.composefluent.winrt.projections.support")
            appendLine()
            appendLine("import io.github.composefluent.winrt.runtime.ActivationFactory")
            appendLine("import microsoft.ui.xaml.ResourceDictionary")
            appendLine()
            appendLine("public object WinUiXamlComponentResources {")
            appendLine("    public fun installInto(mergedDictionaries: MutableList<ResourceDictionary>) {")
            runtimeClassNames.forEach { runtimeClassName ->
                append("        mergedDictionaries.add(ResourceDictionary.Metadata.wrap(ActivationFactory.activateInstance(")
                append(runtimeClassName.kotlinStringLiteral())
                appendLine(")))")
            }
            appendLine("    }")
            appendLine("}")
        },
    )
}

private fun String.kotlinStringLiteral(): String =
    buildString {
        append('"')
        this@kotlinStringLiteral.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }

private data class CompilerSupportManifestRow(
    val kind: String,
    val className: String,
    val sourceFile: String,
)

private data class CompilerSupportSourceKey(
    val kind: String,
    val className: String,
    val sourceFile: String,
)

private val RETIRED_RUNTIME_DISCOVERY_COMPILER_SUPPORT_KINDS = setOf(
    "event-source",
    "event-source-mapping",
    "interface-native",
    "interface-native-projection",
    "interface-native-projections",
)

private fun CompilerSupportManifestRow.rejectRetiredRuntimeDiscoveryKind(manifest: File) {
    if (kind !in RETIRED_RUNTIME_DISCOVERY_COMPILER_SUPPORT_KINDS) {
        return
    }
    throw GradleException(
        "Compiler support manifest ${manifest.absolutePath} contains retired runtime-discovery support kind '$kind'. " +
            "Regenerate WinRT projections with the current generator/compiler plugin so support is emitted as " +
            "compile-time marker facts instead of classpath resource or fixed-registry runtime discovery.",
    )
}

private fun readCompilerSupportRows(manifest: File): List<CompilerSupportManifestRow> =
    manifest.readLines()
        .also { lines ->
            if (lines.firstOrNull() != COMPILER_SUPPORT_MANIFEST_HEADER) {
                throw GradleException(
                    "Compiler support manifest ${manifest.absolutePath} has unexpected header.",
                )
            }
        }
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapIndexed { index, line ->
            val parts = line.split('\t')
            if (parts.size != 4 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                throw GradleException(
                    "Compiler support manifest ${manifest.absolutePath} has malformed row ${index + 2}.",
                )
            }
            CompilerSupportManifestRow(parts[0], parts[1], parts[2])
        }
        .toList()

private const val COMPILER_SUPPORT_MANIFEST_HEADER: String =
    "kind\tclassName\tsourceFile\tentries"
