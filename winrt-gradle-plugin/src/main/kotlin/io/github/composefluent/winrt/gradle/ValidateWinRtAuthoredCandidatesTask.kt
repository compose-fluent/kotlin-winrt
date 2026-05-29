package io.github.composefluent.winrt.gradle

import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoringCandidateFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class ValidateWinRtAuthoredCandidatesTask : DefaultTask() {
    @get:Internal
    abstract val scannerCandidates: RegularFileProperty

    @get:Internal
    abstract val compilerCandidates: RegularFileProperty

    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val candidateFiles: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun validate() {
        validateAuthoredCandidateHandoff(
            scannerCandidates = scannerCandidates.orNull?.asFile,
            compilerCandidates = compilerCandidates.orNull?.asFile,
        )
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText("ok\n")
        }
    }
}

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
    throw GradleException(
        "kotlin-winrt authored candidate handoff mismatch between source scanner and compiler IR output. " +
            "Only scanner candidates: ${(scannerTypes - compilerTypes).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Only compiler candidates: ${(compilerTypes - scannerTypes).sorted().joinToString().ifBlank { "<none>" }}. " +
            "Regenerate authored metadata from sources visible before generateWinRtProjections or fix scanner/IR parity.",
    )
}

private fun File?.readCandidates(): List<KotlinWinRtAuthoredTypeCandidate> =
    this
        ?.takeIf(File::isFile)
        ?.toPath()
        ?.let(KotlinWinRtAuthoringCandidateFile::read)
        .orEmpty()
        .sortedBy(KotlinWinRtAuthoredTypeCandidate::sourceTypeName)
