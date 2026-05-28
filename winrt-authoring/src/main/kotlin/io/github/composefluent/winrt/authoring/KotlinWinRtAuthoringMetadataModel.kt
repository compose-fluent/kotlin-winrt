package io.github.composefluent.winrt.authoring

import io.github.composefluent.winrt.authoring.KotlinWinRtAuthoredTypeCandidate
import io.github.composefluent.winrt.metadata.WinRtAuthoredMetadataDescriptorWriter
import io.github.composefluent.winrt.metadata.WinRtAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRtAuthoringMetadata
import io.github.composefluent.winrt.metadata.WinRtMetadataModel
import io.github.composefluent.winrt.metadata.WinRtPortableExecutableMetadataWriter
import java.nio.file.Files
import java.nio.file.Path

object KotlinWinRtAuthoringMetadataModel {
    fun mergeAuthoredRuntimeClasses(
        model: WinRtMetadataModel,
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
    ): WinRtMetadataModel {
        val authoredTypes = candidates
            .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRtInterfaceNames.isNotEmpty() }
            .map(::runtimeClassDescriptor)
        return WinRtAuthoringMetadata.mergeAuthoredRuntimeClasses(model, authoredTypes)
    }

    fun writeDescriptor(
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        outputFile: Path,
    ) {
        WinRtAuthoredMetadataDescriptorWriter.write(
            runtimeClasses = runtimeClassDescriptors(candidates),
            outputFile = outputFile,
        )
    }

    fun writeWinmd(
        assemblyName: String,
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        outputFile: Path,
    ) {
        WinRtPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = assemblyName,
            runtimeClasses = runtimeClassDescriptors(candidates),
            outputFile = outputFile,
        )
    }

    fun writeHostManifest(
        assemblyName: String,
        targetArtifactName: String = "$assemblyName.jar",
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
        outputFile: Path,
    ) {
        val runtimeClassNames = runtimeClassDescriptors(candidates).map { it.runtimeClassName }.sorted()
        Files.createDirectories(outputFile.parent)
        Files.writeString(
            outputFile,
            buildString {
                appendLine("{")
                appendLine("  \"schemaVersion\": 1,")
                appendLine("  \"model\": \"jvm-authoring-host\",")
                appendLine("  \"assemblyName\": ${assemblyName.toJsonString()},")
                appendLine("  \"hostExportsClass\": \"io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports\",")
                appendLine("  \"targetArtifact\": ${targetArtifactName.toJsonString()},")
                appendLine("  \"activatableClasses\": ${runtimeClassNames.toJsonArray()},")
                appendLine("  \"activatableClassTargets\": ${runtimeClassNames.associateWith { targetArtifactName }.toJsonObject()}")
                appendLine("}")
            },
        )
    }

    private fun runtimeClassDescriptors(candidates: List<KotlinWinRtAuthoredTypeCandidate>): List<WinRtAuthoredRuntimeClassDescriptor> =
        candidates
            .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRtInterfaceNames.isNotEmpty() }
            .map(::runtimeClassDescriptor)

    private fun runtimeClassDescriptor(candidate: KotlinWinRtAuthoredTypeCandidate): WinRtAuthoredRuntimeClassDescriptor =
        WinRtAuthoredRuntimeClassDescriptor(
            runtimeClassName = candidate.sourceTypeName,
            baseRuntimeClassName = candidate.winRtBaseClassName,
            interfaceNames = candidate.winRtInterfaceNames,
            overridableInterfaceNames = candidate.overridableInterfaceNames,
            isActivatable = candidate.isPublic,
        )

    private fun Map<String, String>.toJsonObject(): String =
        entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
            "${key.toJsonString()}: ${value.toJsonString()}"
        }

    private fun List<String>.toJsonArray(): String =
        joinToString(prefix = "[", postfix = "]") { it.toJsonString() }

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
