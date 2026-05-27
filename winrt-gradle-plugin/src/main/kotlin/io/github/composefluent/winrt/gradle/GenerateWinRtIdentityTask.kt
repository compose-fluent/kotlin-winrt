package io.github.composefluent.winrt.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files

@CacheableTask
abstract class GenerateWinRtIdentityTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val metadataInputs: ListProperty<String>

    @get:Input
    abstract val includeNamespaces: ListProperty<String>

    @get:Input
    abstract val includeTypes: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val projectionRegistrarFiles: ConfigurableFileCollection

    @get:Input
    abstract val excludeNamespaces: ListProperty<String>

    @get:Input
    abstract val excludeTypes: ListProperty<String>

    @get:Input
    abstract val additionExcludeNamespaces: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val windowsSdkVersion: Property<String>

    @get:Input
    abstract val includeWindowsSdkExtensions: Property<Boolean>

    @get:Input
    abstract val nugetPackages: ListProperty<String>

    @get:Input
    abstract val runtimeAssets: ListProperty<String>

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredMetadataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredHostManifestFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredTargetArtifactFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerSupportManifestFiles: ConfigurableFileCollection

    @TaskAction
    fun generate() {
        val target = outputFile.get().asFile.toPath()
        Files.createDirectories(target.parent)
        Files.writeString(
            target,
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"model\": \"library\",")
                appendLine("  \"metadataInputs\": ${metadataInputs.get().toJsonArray()},")
                appendLine("  \"includeNamespaces\": ${includeNamespaces.get().toJsonArray()},")
                appendLine("  \"includeTypes\": ${includeTypes.get().toJsonArray()},")
                appendLine("  \"projectedTypes\": ${readProjectedTypeNames(projectionRegistrarFiles.files).toJsonArray()},")
                appendLine("  \"excludeNamespaces\": ${excludeNamespaces.get().toJsonArray()},")
                appendLine("  \"excludeTypes\": ${excludeTypes.get().toJsonArray()},")
                appendLine("  \"additionExcludeNamespaces\": ${additionExcludeNamespaces.get().toJsonArray()},")
                appendLine("  \"windowsSdk\": {")
                appendLine("    \"version\": ${windowsSdkVersion.orNull.toJsonStringOrNull()},")
                appendLine("    \"includeExtensions\": ${includeWindowsSdkExtensions.get()}")
                appendLine("  },")
                appendLine("  \"nugetPackages\": ${nugetPackages.get().toJsonArray()},")
                appendLine("  \"runtimeAssets\": ${runtimeAssets.get().toJsonArray()},")
                appendLine("  \"authoredMetadata\": ${authoredMetadataFiles.files.map { it.absolutePath }.sorted().toJsonArray()},")
                appendLine("  \"authoredHostManifests\": ${authoredHostManifestFiles.files.map { it.absolutePath }.sorted().toJsonArray()},")
                appendLine("  \"authoredTargetArtifacts\": ${authoredTargetArtifactFiles.files.map { it.absolutePath }.sorted().toJsonArray()},")
                appendLine("  \"compilerSupportManifests\": ${compilerSupportManifestFiles.files.map { it.absolutePath }.sorted().toJsonArray()}")
                appendLine("}")
            },
        )
    }
}

internal fun readProjectedTypeNames(projectionRegistrarFiles: Iterable<File>): List<String> =
    projectionRegistrarFiles
        .filter(File::isFile)
        .flatMap(::readProjectionRegistrarProjectedTypeNames)
        .distinct()
        .sorted()

private val projectionRegistrarHeader = listOf(
    "kotlinClassName",
    "projectedTypeName",
    "kind",
    "baseTypeName",
    "metadataClassName",
)

private fun readProjectionRegistrarProjectedTypeNames(file: File): List<String> {
    val lines = file.readLines()
    val header = lines.firstOrNull()?.split('\t')
        ?: throw GradleException("Projection registrar '${file.absolutePath}' is missing a header.")
    if (header != projectionRegistrarHeader) {
        throw GradleException(
            "Projection registrar '${file.absolutePath}' has malformed header '${lines.first()}'.",
        )
    }
    return lines.drop(1).mapIndexedNotNull { index, line ->
        if (line.isBlank()) {
            return@mapIndexedNotNull null
        }
        val rowNumber = index + 2
        val parts = splitProjectionRegistrarRow(line)
        if (parts.size != projectionRegistrarHeader.size || parts.take(3).any(String::isBlank)) {
            throw GradleException(
                "Projection registrar '${file.absolutePath}' has malformed row $rowNumber.",
            )
        }
        parts[1]
    }
}

private fun splitProjectionRegistrarRow(line: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    line.forEachIndexed { index, char ->
        if (char == '\t') {
            parts += line.substring(start, index)
            start = index + 1
        }
    }
    parts += line.substring(start)
    return parts
}

internal fun readCompilerSupportManifests(identityFile: File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    val match = Regex(""""compilerSupportManifests"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeIdentityJsonString() }
        .toList()
}

private fun String.decodeIdentityJsonString(): String =
    replace("\\\"", "\"").replace("\\\\", "\\")

internal fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { it.toJsonString() }

internal fun String?.toJsonStringOrNull(): String =
    this?.toJsonString() ?: "null"

internal fun String.toJsonString(): String =
    buildString {
        append('"')
        this@toJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
