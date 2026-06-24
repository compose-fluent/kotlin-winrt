package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.compiler.authoring.readAuthoringMetadataIndex
import io.github.composefluent.winrt.compiler.authoring.renderAuthoringMetadataIndexRow
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
import java.util.Base64
import kotlin.io.path.name

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

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val typeShapeDescriptorFiles: ConfigurableFileCollection

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
    abstract val runtimeAssetFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoredMetadataFiles: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val authoringMetadataIndexFiles: ConfigurableFileCollection

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
                appendLine("  \"projectionShapeVersion\": $CURRENT_PROJECTION_SHAPE_VERSION,")
                appendLine("  \"metadataInputs\": ${portablePublishedMetadataInputs(metadataInputs.get()).toJsonArray()},")
                appendLine("  \"includeNamespaces\": ${includeNamespaces.get().toJsonArray()},")
                appendLine("  \"includeTypes\": ${includeTypes.get().toJsonArray()},")
                appendLine("  \"projectedTypes\": ${readProjectedTypeNames(projectionRegistrarFiles.files, typeShapeDescriptorFiles.files).toJsonArray()},")
                appendLine("  \"excludeNamespaces\": ${excludeNamespaces.get().toJsonArray()},")
                appendLine("  \"excludeTypes\": ${excludeTypes.get().toJsonArray()},")
                appendLine("  \"additionExcludeNamespaces\": ${additionExcludeNamespaces.get().toJsonArray()},")
                appendLine("  \"windowsSdk\": {")
                appendLine("    \"version\": ${windowsSdkVersion.orNull.toJsonStringOrNull()},")
                appendLine("    \"includeExtensions\": ${includeWindowsSdkExtensions.get()}")
                appendLine("  },")
                appendLine("  \"nugetPackages\": ${nugetPackages.get().toJsonArray()},")
                appendLine("  \"runtimeAssetRecords\": ${runtimeAssetRecordsToJsonArray(readRuntimeAssetRecords(runtimeAssetFiles.files))},")
                appendLine("  \"authoredMetadataRecords\": ${authoredMetadataRecordsToJsonArray(readAuthoredMetadataRecords(authoredMetadataFiles.files))},")
                appendLine("  \"authoringMetadataIndexRows\": ${readAuthoringMetadataIndexRows(authoringMetadataIndexFiles.files).toJsonArray()},")
                appendLine("  \"authoredHostManifestRecords\": ${authoredHostManifestRecordsToJsonArray(readAuthoredHostManifestRecords(authoredHostManifestFiles.files))},")
                appendLine("  \"authoredTargetArtifactRecords\": ${authoredTargetArtifactRecordsToJsonArray(readAuthoredTargetArtifactRecords(authoredTargetArtifactFiles.files))},")
                appendLine("  \"compilerSupportFileRecords\": ${compilerSupportFileRecordsToJsonArray(readCompilerSupportFileRecords(compilerSupportManifestFiles.files))}")
                appendLine("}")
            },
        )
    }
}

internal fun readProjectedTypeNames(
    projectionRegistrarFiles: Iterable<File>,
    typeShapeDescriptorFiles: Iterable<File> = emptyList(),
): List<String> =
    (
        projectionRegistrarFiles
        .filter(File::isFile)
        .flatMap(::readProjectionRegistrarProjectedTypeNames) +
            typeShapeDescriptorFiles
                .filter(File::isFile)
                .flatMap(::readTypeShapeDescriptorProjectedTypeNames)
        )
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

private val typeShapeDescriptorHeader = listOf(
    "projectedTypeName",
    "key",
    "value",
)

private fun readTypeShapeDescriptorProjectedTypeNames(file: File): List<String> {
    val lines = file.readLines()
    val header = lines.firstOrNull()?.split('\t')
        ?: throw GradleException("Type shape descriptor '${file.absolutePath}' is missing a header.")
    if (header != typeShapeDescriptorHeader) {
        throw GradleException(
            "Type shape descriptor '${file.absolutePath}' has malformed header '${lines.first()}'.",
        )
    }
    return lines.drop(1).mapIndexedNotNull { index, line ->
        if (line.isBlank()) {
            return@mapIndexedNotNull null
        }
        val rowNumber = index + 2
        val parts = splitProjectionRegistrarRow(line)
        if (parts.size != typeShapeDescriptorHeader.size || parts.take(2).any(String::isBlank)) {
            throw GradleException(
                "Type shape descriptor '${file.absolutePath}' has malformed row $rowNumber.",
            )
        }
        parts[0]
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

internal fun readAuthoringMetadataIndexRows(identityFile: File): List<String> {
    val content = identityFile.takeIf { it.isFile }?.readText().orEmpty()
    return readIdentityJsonStringArray(content, "authoringMetadataIndexRows")
}

private fun readAuthoringMetadataIndexRows(authoringMetadataIndexFiles: Iterable<File>): List<String> =
    authoringMetadataIndexFiles
        .filter(File::isFile)
        .flatMap { file -> readAuthoringMetadataIndex(file.toPath()).values }
        .distinctBy { type -> type.qualifiedName }
        .sortedBy { type -> type.qualifiedName }
        .map(::renderAuthoringMetadataIndexRow)

private fun readIdentityJsonStringArray(content: String, propertyName: String): List<String> {
    val match = Regex(""""${Regex.escape(propertyName)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeIdentityJsonString() }
        .toList()
}

private fun String.decodeIdentityJsonString(): String =
    buildString {
        var index = 0
        while (index < this@decodeIdentityJsonString.length) {
            val char = this@decodeIdentityJsonString[index++]
            if (char != '\\' || index >= this@decodeIdentityJsonString.length) {
                append(char)
                continue
            }
            when (val escaped = this@decodeIdentityJsonString[index++]) {
                '\\' -> append('\\')
                '"' -> append('"')
                'b' -> append('\b')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> {
                    append('\\')
                    append(escaped)
                }
            }
        }
    }

internal fun List<String>.toJsonArray(): String =
    joinToString(prefix = "[", postfix = "]") { it.toJsonString() }

private fun portablePublishedMetadataInputs(inputs: List<String>): List<String> =
    inputs
        .filter(::isPortablePublishedMetadataInput)
        .distinct()

private fun isPortablePublishedMetadataInput(input: String): Boolean {
    if (input.equals("local", ignoreCase = true) ||
        input.equals("sdk", ignoreCase = true) ||
        input.equals("sdk+", ignoreCase = true)
    ) {
        return true
    }
    if (Regex("""\d+(?:\.\d+){1,3}\+?""").matches(input)) {
        return true
    }
    if (input.startsWith("nuget:", ignoreCase = true)) {
        val spec = input.substringAfter(':')
        return spec.lastIndexOf('@').let { separator -> separator > 0 && separator < spec.lastIndex }
    }
    return false
}

private fun authoredHostManifestRecordsToJsonArray(records: List<AuthoredHostManifestRecord>): String =
    records.joinToString(prefix = "[", postfix = "]") { it.toJsonObject() }

private fun runtimeAssetRecordsToJsonArray(records: List<RuntimeAssetRecord>): String =
    records.joinToString(prefix = "[", postfix = "]") { it.toJsonObject() }

internal fun authoredMetadataRecordsToJsonArray(records: List<AuthoredMetadataRecord>): String =
    records.joinToString(prefix = "[", postfix = "]") { it.toJsonObject() }

private fun compilerSupportFileRecordsToJsonArray(records: List<CompilerSupportFileRecord>): String =
    records.joinToString(prefix = "[", postfix = "]") { it.toJsonObject() }

internal fun authoredTargetArtifactRecordsToJsonArray(records: List<AuthoredTargetArtifactRecord>): String =
    records.joinToString(prefix = "[", postfix = "]") { it.toJsonObject() }

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

internal data class AuthoredMetadataRecord(
    val fileName: String,
    val contentBase64: String,
)

internal data class RuntimeAssetRecord(
    val fileName: String,
    val contentBase64: String,
)

internal fun readRuntimeAssetRecords(files: Iterable<File>): List<RuntimeAssetRecord> =
    files
        .filter(File::isFile)
        .map { file ->
            RuntimeAssetRecord(
                fileName = file.toPath().name,
                contentBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }
        .distinctBy { record -> record.fileName.lowercase() }
        .sortedBy { record -> record.fileName.lowercase() }

internal fun readAuthoredMetadataRecords(files: Iterable<File>): List<AuthoredMetadataRecord> =
    files
        .filter(File::isFile)
        .map { file ->
            AuthoredMetadataRecord(
                fileName = file.toPath().name,
                contentBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }
        .distinctBy { record -> record.fileName.lowercase() }
        .sortedBy { record -> record.fileName.lowercase() }

internal data class AuthoredTargetArtifactRecord(
    val fileName: String,
    val contentBase64: String,
)

internal fun readAuthoredTargetArtifactRecords(files: Iterable<File>): List<AuthoredTargetArtifactRecord> =
    files
        .filter(File::isFile)
        .map { file ->
            AuthoredTargetArtifactRecord(
                fileName = file.toPath().name,
                contentBase64 = Base64.getEncoder().encodeToString(file.readBytes()),
            )
        }
        .distinctBy { record -> record.fileName.lowercase() }
        .sortedBy { record -> record.fileName.lowercase() }

internal data class CompilerSupportFileRecord(
    val group: String,
    val fileName: String,
    val content: String,
)

internal fun readCompilerSupportFileRecords(files: Iterable<File>): List<CompilerSupportFileRecord> =
    files
        .filter(File::isFile)
        .sortedBy { file -> file.absolutePath.lowercase() }
        .flatMapIndexed { index, file -> readCompilerSupportFileRecordsFromManifest(file, "compiler-support-$index") }
        .distinctBy { record -> "${record.group.lowercase()}/${record.fileName.lowercase()}" }
        .sortedWith(compareBy<CompilerSupportFileRecord> { it.group.lowercase() }.thenBy { it.fileName.lowercase() })

private fun readCompilerSupportFileRecordsFromManifest(manifest: File, group: String): List<CompilerSupportFileRecord> {
    val manifestPath = manifest.toPath()
    val manifestRoot = manifestPath.parent ?: return emptyList()
    val manifestContent = manifest.readText()
    val records = mutableListOf(
        CompilerSupportFileRecord(group = group, fileName = manifestPath.name, content = manifestContent),
    )
    manifestContent
        .lineSequence()
        .drop(1)
        .filter(String::isNotBlank)
        .mapNotNull { row -> row.split('\t').getOrNull(2) }
        .filter(String::isNotBlank)
        .distinct()
        .map { sourceFile -> manifestRoot.resolve(sourceFile) }
        .filter { source -> Files.isRegularFile(source) }
        .forEach { source ->
            records += CompilerSupportFileRecord(group = group, fileName = source.name, content = Files.readString(source))
        }
    return records
}

private fun AuthoredMetadataRecord.toJsonObject(): String =
    buildString {
        append("{")
        append("\"fileName\":")
        append(fileName.toJsonString())
        append(",\"contentBase64\":")
        append(contentBase64.toJsonString())
        append("}")
    }

private fun RuntimeAssetRecord.toJsonObject(): String =
    buildString {
        append("{")
        append("\"fileName\":")
        append(fileName.toJsonString())
        append(",\"contentBase64\":")
        append(contentBase64.toJsonString())
        append("}")
    }

private fun AuthoredTargetArtifactRecord.toJsonObject(): String =
    buildString {
        append("{")
        append("\"fileName\":")
        append(fileName.toJsonString())
        append(",\"contentBase64\":")
        append(contentBase64.toJsonString())
        append("}")
    }

private fun CompilerSupportFileRecord.toJsonObject(): String =
    buildString {
        append("{")
        append("\"group\":")
        append(group.toJsonString())
        append(",\"fileName\":")
        append(fileName.toJsonString())
        append(",\"content\":")
        append(content.toJsonString())
        append("}")
    }

internal data class AuthoredHostManifestRecord(
    val assemblyName: String,
    val hostExportsClass: String?,
    val targetArtifact: String,
    val activatableClasses: List<String>,
    val activatableClassTargets: Map<String, String>,
)

internal fun readAuthoredHostManifestRecords(files: Iterable<File>): List<AuthoredHostManifestRecord> =
    files
        .filter(File::isFile)
        .mapNotNull { file -> readAuthoredHostManifestRecord(file.readText()) }
        .distinctBy(AuthoredHostManifestRecord::recordIdentity)
        .sortedWith(
            compareBy<AuthoredHostManifestRecord> { it.assemblyName.lowercase() }
                .thenBy { it.targetArtifact.lowercase() }
                .thenBy { it.hostExportsClass.orEmpty() },
        )

internal fun readAuthoredHostManifestRecord(content: String): AuthoredHostManifestRecord? {
    val assemblyName = readIdentityJsonStringField(content, "assemblyName")?.takeIf(String::isNotBlank)
        ?: return null
    val activatableClasses = readIdentityJsonStringArrayField(content, "activatableClasses")
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
    val activatableClassTargets = readIdentityJsonStringMapField(content, "activatableClassTargets")
        .filterKeys(String::isNotBlank)
        .toSortedMap()
    if (activatableClasses.isEmpty() && activatableClassTargets.isEmpty()) {
        return null
    }
    return AuthoredHostManifestRecord(
        assemblyName = assemblyName,
        hostExportsClass = readIdentityJsonStringField(content, "hostExportsClass")?.takeIf(String::isNotBlank),
        targetArtifact = readIdentityJsonStringField(content, "targetArtifact").orEmpty(),
        activatableClasses = activatableClasses,
        activatableClassTargets = activatableClassTargets,
    )
}

private fun AuthoredHostManifestRecord.toJsonObject(): String =
    buildString {
        append("{")
        append("\"assemblyName\":")
        append(assemblyName.toJsonString())
        hostExportsClass?.let { value ->
            append(",\"hostExportsClass\":")
            append(value.toJsonString())
        }
        append(",\"targetArtifact\":")
        append(targetArtifact.toJsonString())
        append(",\"activatableClasses\":")
        append(activatableClasses.toJsonArray())
        append(",\"activatableClassTargets\":")
        append(activatableClassTargets.toJsonObject())
        append("}")
    }

private fun AuthoredHostManifestRecord.recordIdentity(): String =
    listOf(assemblyName.lowercase(), targetArtifact.lowercase(), hostExportsClass.orEmpty()).joinToString("\u0000")

private fun Map<String, String>.toJsonObject(): String =
    entries
        .sortedBy { it.key }
        .joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${key.toJsonString()}:${value.toJsonString()}"
        }

private fun readIdentityJsonStringField(content: String, name: String): String? =
    Regex(""""${Regex.escape(name)}"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .find(content)
        ?.groupValues
        ?.get(1)
        ?.decodeIdentityJsonString()

private fun readIdentityJsonStringArrayField(content: String, name: String): List<String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyList()
    return Regex(""""((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .map { it.groupValues[1].decodeIdentityJsonString() }
        .toList()
}

private fun readIdentityJsonStringMapField(content: String, name: String): Map<String, String> {
    val match = Regex(""""${Regex.escape(name)}"\s*:\s*\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)
        .find(content) ?: return emptyMap()
    return Regex(""""((?:\\.|[^"\\])*)"\s*:\s*"((?:\\.|[^"\\])*)"""")
        .findAll(match.groupValues[1])
        .associate { matchResult ->
            matchResult.groupValues[1].decodeIdentityJsonString() to
                matchResult.groupValues[2].decodeIdentityJsonString()
        }
}
