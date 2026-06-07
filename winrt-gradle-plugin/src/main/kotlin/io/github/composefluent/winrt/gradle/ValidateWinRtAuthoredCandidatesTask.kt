package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringCandidateFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

@CacheableTask
abstract class ValidateWinRtAuthoredCandidatesTask : DefaultTask() {
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannerCandidates: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerCandidates: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannerAuthoredMetadata: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerAuthoredMetadata: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannerAuthoredWinmd: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerAuthoredWinmd: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannerAuthoredHostManifest: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerAuthoredHostManifest: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val scannerAuthoringTypeDetails: ConfigurableFileCollection

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compilerAuthoringTypeDetails: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun validate() {
        validateAuthoredCandidateHandoff(
            scannerCandidates = scannerCandidates.singleCandidateFileOrNull(),
            compilerCandidates = compilerCandidates.singleCandidateFileOrNull(),
        )
        validateAuthoredArtifactHandoff(
            description = "authored metadata descriptor",
            scannerArtifact = scannerAuthoredMetadata.singleCandidateFileOrNull(),
            compilerArtifact = compilerAuthoredMetadata.singleCandidateFileOrNull(),
        )
        validateAuthoredArtifactHandoff(
            description = "authored WinMD",
            scannerArtifact = scannerAuthoredWinmd.singleCandidateFileOrNull(),
            compilerArtifact = compilerAuthoredWinmd.singleCandidateFileOrNull(),
        )
        validateAuthoredArtifactHandoff(
            description = "authored host manifest",
            scannerArtifact = scannerAuthoredHostManifest.singleCandidateFileOrNull(),
            compilerArtifact = compilerAuthoredHostManifest.singleCandidateFileOrNull(),
        )
        validateAuthoredDirectoryHandoff(
            description = "authored TypeDetails",
            scannerDirectory = scannerAuthoringTypeDetails.singleDirectoryOrNull(),
            compilerDirectory = compilerAuthoringTypeDetails.singleDirectoryOrNull(),
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok\n")
        }
    }
}

private fun ConfigurableFileCollection.singleCandidateFileOrNull(): File? =
    files.singleOrNull()?.takeIf(File::isFile)

private fun ConfigurableFileCollection.singleDirectoryOrNull(): File? =
    files.singleOrNull()?.takeIf(File::isDirectory)

internal fun validateAuthoredCandidateHandoff(
    scannerCandidates: File?,
    compilerCandidates: File?,
) {
    val scanner = scannerCandidates.readCandidates()
    val compiler = compilerCandidates.readCandidates()
    if (scanner == compiler) {
        return
    }
    val scannerTypes = scanner.map(KotlinWinRtAuthoredTypeCandidate::sourceTypeName).toSet()
    val compilerTypes = compiler.map(KotlinWinRtAuthoredTypeCandidate::sourceTypeName).toSet()
    val scannerByType = scanner.associateBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
    val compilerByType = compiler.associateBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
    val changedTypes = (scannerTypes intersect compilerTypes)
        .filter { typeName -> scannerByType[typeName] != compilerByType[typeName] }
        .sorted()
    throw GradleException(
        "kotlin-winrt authored candidate handoff mismatch between source scanner and compiler IR output. " +
            "Only scanner candidates: ${(scannerTypes - compilerTypes).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Only compiler candidates: ${(compilerTypes - scannerTypes).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Changed candidates: ${changedTypes.joinToString().ifBlank { "<none>" }}. " +
            "Regenerate authored metadata from sources visible before generateWinRtProjections or fix scanner/IR parity.",
    )
}

internal fun validateAuthoredArtifactHandoff(
    description: String,
    scannerArtifact: File?,
    compilerArtifact: File?,
) {
    val scannerBytes = scannerArtifact?.takeIf(File::isFile)?.readBytes()
    val compilerBytes = compilerArtifact?.takeIf(File::isFile)?.readBytes()
    if (scannerBytes == null && compilerBytes == null) {
        return
    }
    if (scannerBytes != null && compilerBytes != null && scannerBytes.contentEquals(compilerBytes)) {
        return
    }
    throw GradleException(
        "kotlin-winrt $description handoff mismatch between source scanner and compiler IR output. " +
            "Scanner artifact: ${scannerArtifact?.absolutePath ?: "<missing>"}. " +
            "Compiler artifact: ${compilerArtifact?.absolutePath ?: "<missing>"}. " +
            "Regenerate authored support artifacts from compiler-visible symbols or fix scanner/IR parity.",
    )
}

internal fun validateAuthoredDirectoryHandoff(
    description: String,
    scannerDirectory: File?,
    compilerDirectory: File?,
) {
    val scannerFiles = scannerDirectory?.takeIf(File::isDirectory)?.toPath()?.relativeRegularFiles().orEmpty()
    val compilerFiles = compilerDirectory?.takeIf(File::isDirectory)?.toPath()?.relativeRegularFiles().orEmpty()
    if (scannerFiles.isEmpty() && compilerFiles.isEmpty()) {
        return
    }
    val scannerByPath = scannerFiles.associateWith { relativePath ->
        Files.readAllBytes(scannerDirectory!!.toPath().resolve(relativePath))
    }
    val compilerByPath = compilerFiles.associateWith { relativePath ->
        Files.readAllBytes(compilerDirectory!!.toPath().resolve(relativePath))
    }
    if (scannerByPath.keys == compilerByPath.keys &&
        scannerByPath.all { (relativePath, scannerBytes) ->
            compilerByPath[relativePath]?.contentEquals(scannerBytes) == true
        }
    ) {
        return
    }
    val scannerPaths = scannerByPath.keys
    val compilerPaths = compilerByPath.keys
    val changedPaths = (scannerPaths intersect compilerPaths)
        .filterNot { relativePath ->
            compilerByPath[relativePath]?.contentEquals(scannerByPath[relativePath]) == true ||
                (description == "authored TypeDetails" &&
                    scannerByPath[relativePath]?.isWhitespaceEquivalentTo(compilerByPath[relativePath]) == true)
        }
        .sorted()
    if (scannerPaths == compilerPaths && changedPaths.isEmpty()) {
        return
    }
    throw GradleException(
        "kotlin-winrt $description handoff mismatch between source scanner and compiler IR output. " +
            "Only scanner files: ${(scannerPaths - compilerPaths).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Only compiler files: ${(compilerPaths - scannerPaths).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Changed files: ${changedPaths.joinToString().ifBlank { "<none>" }}. " +
            "Regenerate authored TypeDetails from compiler-visible symbols or fix scanner/IR parity.",
    )
}

private fun Path.relativeRegularFiles(): List<String> =
    Files.walk(this).use { stream ->
        stream.asSequence()
            .filter(Files::isRegularFile)
            .map { file -> relativize(file).toString().replace(File.separatorChar, '/') }
            .sorted()
            .toList()
    }

private fun ByteArray.isWhitespaceEquivalentTo(other: ByteArray?): Boolean =
    other != null && decodeToString().withoutKotlinWhitespace() == other.decodeToString().withoutKotlinWhitespace()

private fun String.withoutKotlinWhitespace(): String =
    filterNot(Char::isWhitespace)

private fun File?.readCandidates(): List<KotlinWinRtAuthoredTypeCandidate> =
    this
        ?.takeIf(File::isFile)
        ?.toPath()
        ?.let(KotlinWinRtAuthoringCandidateFile::read)
        .orEmpty()
        .sortedBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
