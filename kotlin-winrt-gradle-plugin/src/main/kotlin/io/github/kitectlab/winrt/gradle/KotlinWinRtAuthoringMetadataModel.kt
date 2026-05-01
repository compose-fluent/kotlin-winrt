package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtAuthoredMetadataDescriptorWriter
import io.github.kitectlab.winrt.metadata.WinRtAuthoredRuntimeClassDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAuthoringMetadata
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtPortableExecutableMetadataWriter
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
        )
}
