package io.github.composefluent.winrt.metadata

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

data class WinRTAuthoredRuntimeClassDescriptor(
    val runtimeClassName: String,
    val baseRuntimeClassName: String? = null,
    val interfaceNames: List<String>,
    val overridableInterfaceNames: List<String> = emptyList(),
    val isActivatable: Boolean = true,
    val isSealed: Boolean = true,
    val activatableFactoryInterfaceName: String? = null,
    val staticFactoryInterfaceNames: List<String> = emptyList(),
) {
    init {
        require(runtimeClassName.isNotBlank()) { "Authored runtime class name must not be blank." }
        require(interfaceNames.isNotEmpty()) { "Authored runtime class must expose at least one interface." }
    }
}

object WinRTAuthoringMetadata {
    fun mergeAuthoredRuntimeClasses(
        model: WinRTMetadataModel,
        runtimeClasses: List<WinRTAuthoredRuntimeClassDescriptor>,
    ): WinRTMetadataModel {
        val authoredTypes = runtimeClasses.map(::runtimeClassDefinition)
        if (authoredTypes.isEmpty()) {
            return model
        }
        val authoredNamespaces = authoredTypes
            .groupBy(WinRTTypeDefinition::namespace)
            .map { (namespace, types) -> WinRTNamespace(namespace, types) }
        return WinRTMetadataModel(model.namespaces + authoredNamespaces).normalized()
    }

    private fun runtimeClassDefinition(descriptor: WinRTAuthoredRuntimeClassDescriptor): WinRTTypeDefinition {
        val namespace = descriptor.runtimeClassName.substringBeforeLast('.', missingDelimiterValue = "")
        val typeName = descriptor.runtimeClassName.substringAfterLast('.')
        val defaultInterfaceName = descriptor.interfaceNames.first()
        val overridableInterfaces = descriptor.overridableInterfaceNames.toSet()
        return WinRTTypeDefinition(
            namespace = namespace,
            name = typeName,
            kind = WinRTTypeKind.RuntimeClass,
            baseTypeName = descriptor.baseRuntimeClassName ?: "System.Object",
            isSealedType = descriptor.isSealed,
            defaultInterfaceName = defaultInterfaceName,
            implementedInterfaces = descriptor.interfaceNames.map { interfaceName ->
                WinRTInterfaceImplementationDefinition(
                    interfaceName = interfaceName,
                    isDefault = interfaceName == defaultInterfaceName,
                    isOverridable = interfaceName in overridableInterfaces,
                )
            },
            activation = WinRTActivationShape(
                isActivatable = descriptor.isActivatable,
                activatableFactoryInterfaceName = descriptor.activatableFactoryInterfaceName,
                staticInterfaceNames = descriptor.staticFactoryInterfaceNames,
            ),
        )
    }
}

object WinRTAuthoredMetadataDescriptorWriter {
    fun write(
        runtimeClasses: List<WinRTAuthoredRuntimeClassDescriptor>,
        outputFile: Path,
    ) {
        Files.createDirectories(outputFile.parent)
        outputFile.writeText(
            runtimeClasses
                .sortedBy(WinRTAuthoredRuntimeClassDescriptor::runtimeClassName)
                .joinToString(separator = "\n", postfix = "\n") { descriptor ->
                    listOf(
                        descriptor.runtimeClassName,
                        descriptor.baseRuntimeClassName.orEmpty(),
                        descriptor.interfaceNames.joinToString(";"),
                        descriptor.overridableInterfaceNames.joinToString(";"),
                        descriptor.isActivatable.toString(),
                        descriptor.isSealed.toString(),
                        descriptor.activatableFactoryInterfaceName.orEmpty(),
                        descriptor.staticFactoryInterfaceNames.joinToString(";"),
                    ).joinToString("\t")
                },
        )
    }
}
