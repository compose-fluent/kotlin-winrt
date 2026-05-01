package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtAuthoredMetadataDescriptorWriter
import io.github.kitectlab.winrt.metadata.WinRtAuthoredRuntimeClassDescriptor
import io.github.kitectlab.winrt.metadata.WinRtAuthoringMetadata
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
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
            runtimeClasses = candidates
                .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRtInterfaceNames.isNotEmpty() }
                .map(::runtimeClassDescriptor),
            outputFile = outputFile,
        )
    }

    private fun runtimeClassDescriptor(candidate: KotlinWinRtAuthoredTypeCandidate): WinRtAuthoredRuntimeClassDescriptor =
        WinRtAuthoredRuntimeClassDescriptor(
            runtimeClassName = candidate.sourceTypeName,
            baseRuntimeClassName = candidate.winRtBaseClassName,
            interfaceNames = candidate.winRtInterfaceNames,
            overridableInterfaceNames = candidate.overridableInterfaceNames,
        )
}
