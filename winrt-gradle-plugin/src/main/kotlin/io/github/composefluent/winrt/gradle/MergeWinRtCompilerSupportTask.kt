package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
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

abstract class MergeWinRtCompilerSupportTask : DefaultTask() {
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
        manifests.forEach { manifest ->
            val manifestRoot = manifest.toPath().parent ?: return@forEach
            readCompilerSupportRows(manifest).forEach { row ->
                if (row.kind !in MERGED_COMPILER_SUPPORT_KINDS) {
                    return@forEach
                }
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
            }
        }
        if (sourceRows.isEmpty()) {
            return
        }
        val manifestRows = mutableListOf("kind\tclassName\tsourceFile\tentries")
        sourceRows.toSortedMap(compareBy<CompilerSupportSourceKey> { it.kind }.thenBy { it.className }.thenBy { it.sourceFile })
            .forEach { (key, lines) ->
                val distinctLines = lines.take(1) + lines.drop(1).distinct()
                Files.writeString(outputRoot.resolve(key.sourceFile), distinctLines.joinToString(separator = "\n", postfix = "\n"))
                manifestRows += listOf(
                    key.kind,
                    key.className,
                    key.sourceFile,
                    (distinctLines.size - 1).toString(),
                ).joinToString("\t")
            }
        Files.writeString(outputRoot.resolve("compiler-support.tsv"), manifestRows.joinToString(separator = "\n", postfix = "\n"))
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

private val MERGED_COMPILER_SUPPORT_KINDS = setOf(
    "projection-registrar",
    "event-source",
    "generic-type-instantiation",
    "generic-abi-registry",
)

private fun readCompilerSupportRows(manifest: File): List<CompilerSupportManifestRow> =
    manifest.readLines()
        .asSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) {
                null
            } else {
                CompilerSupportManifestRow(parts[0], parts[1], parts[2])
            }
        }
        .toList()
