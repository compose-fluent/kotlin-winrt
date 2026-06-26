package io.github.composefluent.winrt.compiler.authoring

import io.github.composefluent.winrt.metadata.WinRTAuthoredMetadataDescriptorWriter
import io.github.composefluent.winrt.metadata.WinRTAuthoredRuntimeClassDescriptor
import io.github.composefluent.winrt.metadata.WinRTAuthoringMetadata
import io.github.composefluent.winrt.metadata.WinRTMetadataModel
import io.github.composefluent.winrt.metadata.WinRTPortableExecutableMetadataWriter
import java.nio.file.Files
import java.nio.file.Path

object KotlinWinRTAuthoringMetadataModel {
    fun mergeAuthoredRuntimeClasses(
        model: WinRTMetadataModel,
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
    ): WinRTMetadataModel {
        val authoredTypes = candidates
            .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRTInterfaceNames.isNotEmpty() }
            .map(::runtimeClassDescriptor)
        return WinRTAuthoringMetadata.mergeAuthoredRuntimeClasses(model, authoredTypes)
    }

    fun writeDescriptor(
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
        outputFile: Path,
    ) {
        WinRTAuthoredMetadataDescriptorWriter.write(
            runtimeClasses = runtimeClassDescriptors(candidates),
            outputFile = outputFile,
        )
    }

    fun writeWinmd(
        assemblyName: String,
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
        outputFile: Path,
    ) {
        WinRTPortableExecutableMetadataWriter.writeAuthoredWinmd(
            assemblyName = assemblyName,
            runtimeClasses = runtimeClassDescriptors(candidates),
            outputFile = outputFile,
        )
    }

    fun writeHostManifest(
        assemblyName: String,
        targetArtifactName: String = "$assemblyName.jar",
        hostExportsClassName: String = "io.github.composefluent.winrt.projections.support.WinRTAuthoringHostExports",
        candidates: List<KotlinWinRTAuthoredTypeCandidate>,
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
                appendLine("  \"hostExportsClass\": ${hostExportsClassName.toJsonString()},")
                appendLine("  \"targetArtifact\": ${targetArtifactName.toJsonString()},")
                appendLine("  \"activatableClasses\": ${runtimeClassNames.toJsonArray()},")
                appendLine("  \"activatableClassTargets\": ${runtimeClassNames.associateWith { targetArtifactName }.toJsonObject()}")
                appendLine("}")
            },
        )
    }

    private fun runtimeClassDescriptors(candidates: List<KotlinWinRTAuthoredTypeCandidate>): List<WinRTAuthoredRuntimeClassDescriptor> =
        candidates
            .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRTInterfaceNames.isNotEmpty() }
            .map(::runtimeClassDescriptor)

    private fun runtimeClassDescriptor(candidate: KotlinWinRTAuthoredTypeCandidate): WinRTAuthoredRuntimeClassDescriptor =
        WinRTAuthoredRuntimeClassDescriptor(
            runtimeClassName = candidate.sourceTypeName,
            baseRuntimeClassName = candidate.winRTBaseClassName,
            interfaceNames = candidate.winRTInterfaceNames,
            overridableInterfaceNames = candidate.overridableInterfaceNames,
            isActivatable = candidate.isPublic,
            activatableFactoryInterfaceName = candidate.activatableFactoryInterfaceName,
            staticFactoryInterfaceNames = candidate.staticFactoryInterfaceNames,
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
