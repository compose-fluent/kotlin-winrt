package io.github.kitectlab.winrt.gradle

import io.github.kitectlab.winrt.metadata.WinRtActivationShape
import io.github.kitectlab.winrt.metadata.WinRtInterfaceImplementationDefinition
import io.github.kitectlab.winrt.metadata.WinRtMetadataModel
import io.github.kitectlab.winrt.metadata.WinRtNamespace
import io.github.kitectlab.winrt.metadata.WinRtTypeDefinition
import io.github.kitectlab.winrt.metadata.WinRtTypeKind

object KotlinWinRtAuthoringMetadataModel {
    fun mergeAuthoredRuntimeClasses(
        model: WinRtMetadataModel,
        candidates: List<KotlinWinRtAuthoredTypeCandidate>,
    ): WinRtMetadataModel {
        val authoredTypes = candidates
            .filter { candidate -> candidate.sourceTypeName.isNotBlank() && candidate.winRtInterfaceNames.isNotEmpty() }
            .map(::runtimeClassDefinition)
        if (authoredTypes.isEmpty()) {
            return model
        }
        val authoredNamespaces = authoredTypes
            .groupBy(WinRtTypeDefinition::namespace)
            .map { (namespace, types) -> WinRtNamespace(namespace, types) }
        return WinRtMetadataModel(model.namespaces + authoredNamespaces).normalized()
    }

    private fun runtimeClassDefinition(candidate: KotlinWinRtAuthoredTypeCandidate): WinRtTypeDefinition {
        val namespace = candidate.sourceTypeName.substringBeforeLast('.', missingDelimiterValue = "")
        val typeName = candidate.sourceTypeName.substringAfterLast('.')
        val defaultInterfaceName = candidate.winRtInterfaceNames.first()
        val overridableInterfaces = candidate.overridableInterfaceNames.toSet()
        return WinRtTypeDefinition(
            namespace = namespace,
            name = typeName,
            kind = WinRtTypeKind.RuntimeClass,
            baseTypeName = candidate.winRtBaseClassName ?: "System.Object",
            isSealedType = true,
            defaultInterfaceName = defaultInterfaceName,
            implementedInterfaces = candidate.winRtInterfaceNames.map { interfaceName ->
                WinRtInterfaceImplementationDefinition(
                    interfaceName = interfaceName,
                    isDefault = interfaceName == defaultInterfaceName,
                    isOverridable = interfaceName in overridableInterfaces,
                )
            },
            activation = WinRtActivationShape(isActivatable = true),
        )
    }
}
